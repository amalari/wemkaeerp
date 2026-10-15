package com.eventverse.app.presentation.builder

import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.builder.audit.BuilderAuditRemote
import com.eventverse.app.presentation.builder.audit.BuilderAuditUiEvent
import com.eventverse.app.presentation.builder.audit.BuilderAuditViewModel
import com.eventverse.app.presentation.builder.audit.SecurityPostureUi
import com.eventverse.app.presentation.builder.audit.decodeAuditEntries
import com.eventverse.app.presentation.builder.audit.parseSecurityPosture
import com.eventverse.app.presentation.builder.audit.visibleEntries
import com.eventverse.app.shared.builder.BuilderAuditDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Pane Audit (TRD-PLAT-014 FR-5-2): ViewModel MVI dengan fake remote — dekode WAJIB lewat codec
 * kontrak Track 0 (tolak nilai tak dikenal), kartu Postur Security hanya dari payload yang ada
 * (PLAN §7.2), dan gagal memuat postur tidak mematikan daftar audit.
 */
class BuilderAuditViewModelTest {

    private fun entry(id: String, action: AuditAction, summary: String) = AuditLogEntry(
        id = id,
        actorUserId = "user-1",
        actorRole = Role.PLATFORM_SUPERADMIN,
        targetTenantId = TenantId("tenant-a"),
        action = action,
        summary = summary,
        occurredAt = Instant.parse("2026-10-14T10:00:00Z"),
    )

    private class FakeRemote(
        var entries: List<AuditLogEntry> = emptyList(),
        var posture: SecurityPostureUi? = null,
        var failAudit: String? = null,
        var failPosture: String? = null,
    ) : BuilderAuditRemote {
        val auditLimits = mutableListOf<Int>()
        override suspend fun fetchAudit(limit: Int): Result<List<AuditLogEntry>> {
            auditLimits += limit
            return failAudit?.let { Result.failure(IllegalStateException(it)) } ?: Result.success(entries)
        }

        override suspend fun fetchPosture(): Result<SecurityPostureUi> =
            failPosture?.let { Result.failure(IllegalStateException(it)) }
                ?: posture?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("posture tidak disiapkan"))
    }

    private fun TestScope.vm(remote: FakeRemote): BuilderAuditViewModel {
        val vm = BuilderAuditViewModel(remote, this)
        vm.onEvent(BuilderAuditUiEvent.Load)
        advanceUntilIdle()
        return vm
    }

    @Test
    fun load_shouldExposeEntriesChronologicallyAndPosture() = runTest(StandardTestDispatcher()) {
        val deployed = entry("a1", AuditAction.BUILDER_DEPLOYMENT_ACTIVATED, "deploy #1 ACTIVE v2")
        val rolledBack = entry("a2", AuditAction.BUILDER_DEPLOYMENT_ROLLED_BACK, "rollback -> pin v1")
        val remote = FakeRemote(
            entries = listOf(deployed, rolledBack),
            posture = SecurityPostureUi("ACTIVE", 2, 8),
        )
        val vm = vm(remote)
        assertEquals(listOf(deployed, rolledBack), vm.uiState.value.entries, "urutan server dipertahankan apa adanya")
        assertEquals(SecurityPostureUi("ACTIVE", 2, 8), vm.uiState.value.posture)
        assertEquals(listOf(50), remote.auditLimits, "default limit 50")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun filter_shouldNarrowByActionKindAndReset() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(
            entries = listOf(
                entry("a1", AuditAction.BUILDER_DEPLOYMENT_ACTIVATED, "deploy #1"),
                entry("a2", AuditAction.BUILDER_INVOICE_ISSUED, "invoice INV-1"),
                entry("a3", AuditAction.BUILDER_DEPLOYMENT_ACTIVATED, "deploy #2"),
            ),
            posture = SecurityPostureUi(null, null, null),
        )
        val vm = vm(remote)
        vm.onEvent(BuilderAuditUiEvent.Filter(AuditAction.BUILDER_DEPLOYMENT_ACTIVATED)); advanceUntilIdle()
        assertEquals(listOf("a1", "a3"), vm.uiState.value.visibleEntries().map { it.id })
        vm.onEvent(BuilderAuditUiEvent.Filter(AuditAction.BUILDER_INVOICE_ISSUED)); advanceUntilIdle()
        assertEquals(listOf("a2"), vm.uiState.value.visibleEntries().map { it.id })
        vm.onEvent(BuilderAuditUiEvent.Filter(null)); advanceUntilIdle()
        assertEquals(3, vm.uiState.value.visibleEntries().size, "tanpa filter = semua")
    }

    @Test
    fun auditFailure_shouldShowHonestErrorAndKeepPosture() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(
            failAudit = "Butuh izin Builder (MANAGE_BUILDER) untuk membuka konsol ini.",
            posture = SecurityPostureUi("ACTIVE", 3, 8),
        )
        val vm = vm(remote)
        assertTrue(vm.uiState.value.entries.isEmpty())
        assertEquals("Butuh izin Builder (MANAGE_BUILDER) untuk membuka konsol ini.", vm.uiState.value.error)
        assertEquals(SecurityPostureUi("ACTIVE", 3, 8), vm.uiState.value.posture, "postur tetap tampil walau daftar gagal")
    }

    @Test
    fun postureFailure_shouldNotBreakAuditList() = runTest(StandardTestDispatcher()) {
        val remote = FakeRemote(
            entries = listOf(entry("a1", AuditAction.BUILDER_DEPLOYMENT_ACTIVATED, "deploy #1")),
            failPosture = "overview gagal",
        )
        val vm = vm(remote)
        assertEquals(1, vm.uiState.value.entries.size)
        assertNull(vm.uiState.value.posture, "postur null → kartu bilang jujur, bukan angka rekayasa")
        assertNull(vm.uiState.value.error)
    }

    @Test
    fun decode_shouldGoThroughFrozenCodec_andRejectUnknownAction() {
        val valid = jsonObjectOf(
            "entries" to jsonArrayOf(
                listOf(
                    jsonObjectOf(
                        "id" to jsonOf("a1"),
                        "actorUserId" to jsonOf("user-1"),
                        "actorRole" to jsonOf("PLATFORM_SUPERADMIN"),
                        "targetTenantId" to jsonOf("tenant-a"),
                        "action" to jsonOf("builder_deployment_activated"),
                        "summary" to jsonOf("deploy #1 ACTIVE v2"),
                        "occurredAt" to jsonOf("2026-10-14T10:00:00Z"),
                    )
                )
            )
        )
        val decoded = decodeAuditEntries(valid)
        assertEquals(1, decoded.size)
        assertEquals(AuditAction.BUILDER_DEPLOYMENT_ACTIVATED, decoded.single().action)

        val unknown = JsonParser.parse(
            """{"entries":[{"id":"a2","actorUserId":"u","actorRole":"TENANT_ADMIN","targetTenantId":"tenant-a","action":"aksi_ngarang","summary":"x","occurredAt":"2026-10-14T10:00:00Z"}]}"""
        )
        try {
            decodeAuditEntries(unknown)
            fail("aksi tak dikenal wajib ditolak codec, bukan diteruskan")
        } catch (e: BuilderAuditDecodeException) {
            assertTrue(e.message!!.contains("aksi_ngarang"), "pesan menyebut aksinya: ${e.message}")
        }
    }

    @Test
    fun posture_shouldDeriveOnlyFromExistingOverviewPayload() {
        // Bentuk payload server hari ini: tenant + activeDeployment + deployments (tanpa modul live).
        val current = JsonParser.parse(
            """{"tenant":{"slug":"tenant-a","domainPack":"garment","domainPackVersion":1},""" +
                """"activeDeployment":{"number":3,"status":"ACTIVE","packVersion":2},"deployments":[]}"""
        )
        val posture = parseSecurityPosture(current)
        assertEquals("ACTIVE", posture.activeDeploymentStatus)
        assertEquals(2, posture.pinnedPackVersion)
        assertNull(posture.liveModuleCount, "payload belum memuat modul live → null, bukan 0 rekayasa")

        // Tanpa deployment aktif → semua null (belum deploy / sudah rollback).
        val empty = JsonParser.parse("""{"tenant":{"slug":"tenant-a"},"activeDeployment":null,"deployments":[]}""")
        val noDeployment = parseSecurityPosture(empty)
        assertNull(noDeployment.activeDeploymentStatus)
        assertNull(noDeployment.pinnedPackVersion)
        assertNull(noDeployment.liveModuleCount)

        // Bila payload kelak memuat daftar modul live, jumlahnya diambil apa adanya.
        val withLive = JsonParser.parse(
            """{"tenant":{},"activeDeployment":{"status":"ACTIVE","packVersion":4},"liveModules":["procurement","sewing"]}"""
        )
        assertEquals(2, parseSecurityPosture(withLive).liveModuleCount)
        assertNotNull(parseSecurityPosture(withLive).pinnedPackVersion)

        // JSON null / bentuk bukan-array → tidak dihitung 0 rekayasa; tetap "belum tersedia".
        val malformed = JsonParser.parse(
            """{"tenant":{},"activeDeployment":{"status":"ACTIVE","packVersion":4},"liveModules":null}"""
        )
        assertNull(parseSecurityPosture(malformed).liveModuleCount)
    }
}
