package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.plus
import kotlin.time.Duration.Companion.minutes
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Plan §2 A7/T13: sesi pratinjau terkait waktu, per kode pack, dan tidak menyentuh pack bawaan platform. */
class DiscoveryPreviewRegistryTest {

    private val now = Instant.fromEpochMilliseconds(1_700_000_000_000)

    private fun klinikPack(): DomainPack {
        val module = ModuleDefinition(
            id = ModuleId("klinik_antrean"), displayName = "Antrean", description = "Antrean pasien",
            section = ModuleSectionCode("UTAMA"), kind = ModuleKind.OPERATIONAL, iconKey = "clipboard",
            scopeCapability = ScopeCapability.HIERARCHICAL,
            supportedScopes = setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA),
            slot = SlotCode("klinik_antrean")
        )
        return DomainPack(
            code = DomainPackCode("klinik"),
            displayName = "Klinik",
            phases = listOf(PhaseDefinition(PhaseCode("OPERASI"), 1, "1. Operasi", "Alur harian", 0xFF2563EB)),
            slots = listOf(SlotDefinition(SlotCode("klinik_antrean"), "Antrean", PhaseCode("OPERASI"), PortType("Permintaan"), PortType("Catatan"))),
            portTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            wiredPortTypes = setOf(PortType("Permintaan"), PortType("Catatan")),
            sections = listOf(ModuleSection(ModuleSectionCode("UTAMA"), "Operasional", 1, 0xFF2563EB, 0xFFEFF6FF)),
            modules = listOf(module)
        )
    }

    @AfterTest
    fun cleanup() {
        (DiscoveryPreviewRegistry.activePackCodes + DomainPackCode("klinik")).forEach { DiscoveryPreviewRegistry.end(it) }
    }

    @Test
    fun `sesi terdaftar di registry data dan hilang setelah end`() {
        val preview = DiscoveryPreviewRegistry.start(klinikPack(), TenantId("ten-sandbox-klinik"), now)

        assertEquals(preview, DiscoveryPreviewRegistry.activeOf(DomainPackCode("klinik"), now))
        assertTrue(DomainPackRegistry.find(DomainPackCode("klinik")) != null)

        assertTrue(DiscoveryPreviewRegistry.end(DomainPackCode("klinik")))
        assertNull(DomainPackRegistry.find(DomainPackCode("klinik")))
        assertNull(DiscoveryPreviewRegistry.activeOf(DomainPackCode("klinik"), now))
    }

    @Test
    fun `pack bawaan platform tidak pernah tersentuh`() {
        val shipped = DomainPackRegistry.find(GarmentDomainPack.CODE)
        DiscoveryPreviewRegistry.start(klinikPack(), TenantId("ten-sandbox-klinik"), now)

        assertSame(GarmentDomainPack.pack, DomainPackRegistry.find(GarmentDomainPack.CODE))
        assertSame(shipped, DomainPackRegistry.find(GarmentDomainPack.CODE))
    }

    @Test
    fun `dua sesi untuk kode pack yang sama ditolak`() {
        DiscoveryPreviewRegistry.start(klinikPack(), TenantId("ten-sandbox-klinik"), now)
        val ex = assertFailsWith<IllegalStateException> {
            DiscoveryPreviewRegistry.start(klinikPack(), TenantId("ten-sandbox-klinik-2"), now)
        }
        assertTrue(ex.message!!.contains("sudah berjalan"))
    }

    @Test
    fun `sesi kedaluwarsa dibersihkan dan pack dilepas`() {
        DiscoveryPreviewRegistry.start(klinikPack(), TenantId("ten-sandbox-klinik"), now, ttlMinutes = 60)

        // Sebelum kedaluwarsa: tidak ada yang dibersihkan, sesi tetap hidup.
        assertEquals(0, DiscoveryPreviewRegistry.purgeExpired(now + 59.minutes).size)
        assertTrue(DomainPackRegistry.find(DomainPackCode("klinik")) != null)

        // Sesudah kedaluwarsa: dilepas.
        val removed = DiscoveryPreviewRegistry.purgeExpired(now + 61.minutes)
        assertEquals(listOf(DomainPackCode("klinik")), removed)
        assertNull(DomainPackRegistry.find(DomainPackCode("klinik")))
    }
}
