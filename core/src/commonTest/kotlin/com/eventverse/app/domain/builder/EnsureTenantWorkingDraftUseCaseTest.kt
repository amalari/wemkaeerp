package com.eventverse.app.domain.builder

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentScreenSuggestions
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.LayananPilotPack
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.pipeline.TenantPipelineRepository
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Bootstrap draf kerja kini mengisi layar prototype dari usulan data pack + backfill draf kosong. */
class EnsureTenantWorkingDraftUseCaseTest {

    private val tenant = TenantId("konveksi-uji")
    private val owner = UserId("owner-1")
    private var now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    private val clock = object : Clock { override fun now(): Instant = now }

    @Test
    fun freshTenant_bootstrapIncludesPackSuggestedScreens_forActiveModules() = runTest {
        val drafts = FakeDrafts()

        val stored = assertNotNull(useCase(drafts)(tenant, GarmentDomainPack.CODE, owner))
        assertTrue(drafts.rows.containsKey(DiscoveryDraftId("draft-${tenant.value}")), "Draf tersimpan dengan id konvensi")

        val screens = stored.draft.screens
        assertEquals(GarmentScreenSuggestions.all.size, screens.size, "FOB aktif penuh → semua usulan layar masuk")
        assertTrue(screens.all { it.screenId.startsWith("default-") }, "Id layar menandai asal data pack")
        val suratJalan = screens.first { it.moduleId.value == "fulfillment" }
        assertEquals("Surat Jalan & Packing List", suratJalan.title)
        assertEquals(WidgetKind.PRINT.code, suratJalan.widget)
    }

    @Test
    fun existingDraft_withScreens_isNeverTouched() = runTest {
        val drafts = FakeDrafts()
        val seeded = StoredDiscoveryDraft(
            id = DiscoveryDraftId("draft-${tenant.value}"),
            ownerUserId = owner,
            draft = DiscoveryDraft(
                pack = GarmentDomainPack.pack,
                blueprint = GarmentBlueprints.FOB_FULL_PACKAGE,
                screens = listOf(PrototypeScreen("agent-1", ModuleId("fulfillment"), "Layar Buatan Agent", "PRINT"))
            ),
            tenantId = tenant,
            createdAt = now,
            updatedAt = now
        )
        drafts.save(seeded)

        val result = assertNotNull(useCase(drafts)(tenant, GarmentDomainPack.CODE, owner))
        assertSame(seeded, result, "Draf yang sudah punya layar tidak boleh disentuh bootstrap")
        assertEquals(1, result.draft.screens.size)
    }

    @Test
    fun existingDraft_withoutScreens_isBackfilledOnce_thenIdempotent() = runTest {
        val drafts = FakeDrafts()
        drafts.save(
            StoredDiscoveryDraft(
                id = DiscoveryDraftId("draft-${tenant.value}"),
                ownerUserId = owner,
                draft = DiscoveryDraft(GarmentDomainPack.pack, GarmentBlueprints.FOB_FULL_PACKAGE, emptyList()),
                tenantId = tenant,
                createdAt = now,
                updatedAt = now
            )
        )

        now = Instant.parse("2026-01-02T00:00:00Z")
        val backfilled = assertNotNull(useCase(drafts)(tenant, GarmentDomainPack.CODE, owner))
        assertEquals(GarmentScreenSuggestions.all.size, backfilled.draft.screens.size)
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), backfilled.updatedAt)

        now = Instant.parse("2026-01-03T00:00:00Z")
        val again = assertNotNull(useCase(drafts)(tenant, GarmentDomainPack.CODE, owner))
        assertSame(backfilled, again, "Backfill idempoten: draf yang sudah berlayar tidak disentuh lagi")
        assertEquals(Instant.parse("2026-01-02T00:00:00Z"), again.updatedAt)
    }

    @Test
    fun existingDraft_withStalePackSnapshot_isBackfilledFromRegistry() = runTest {
        val drafts = FakeDrafts()
        // Draf lama menyimpan snapshot pack dari sebelum field screenSuggestions ada (JSON lama).
        val stalePack = GarmentDomainPack.pack.copy(screenSuggestions = emptyList())
        drafts.save(
            StoredDiscoveryDraft(
                id = DiscoveryDraftId("draft-${tenant.value}"),
                ownerUserId = owner,
                draft = DiscoveryDraft(stalePack, GarmentBlueprints.FOB_FULL_PACKAGE, emptyList()),
                tenantId = tenant,
                createdAt = now,
                updatedAt = now
            )
        )

        val backfilled = assertNotNull(useCase(drafts)(tenant, GarmentDomainPack.CODE, owner))
        assertEquals(GarmentScreenSuggestions.all.size, backfilled.draft.screens.size)
        assertSame(stalePack, backfilled.draft.pack, "Snapshot pack milik draf tidak pernah diganti")
    }

    // ---- pack data tanpa blueprint bawaan garmen (modul pilot `layanan`) ------------------------------

    @AfterTest
    fun unregisterDataPack() { DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    private fun layananDraft(screens: List<PrototypeScreen>) = StoredDiscoveryDraft(
        id = DiscoveryDraftId("draft-${tenant.value}"),
        ownerUserId = owner,
        draft = DiscoveryDraft(
            pack = LayananPilotPack.pack,
            blueprint = Blueprint(
                BlueprintCode("layanan_starter"), LayananPilotPack.CODE, "Starter Layanan", "Pilot", "Draf pilot", "Tim layanan",
                listOf(BlueprintModule(LayananPilotPack.CHANGE_REQUEST.value, active = true))
            ),
            screens = screens
        ),
        tenantId = tenant,
        createdAt = now,
        updatedAt = now
    )

    private val pilotScreen = PrototypeScreen("default-${LayananPilotPack.CHANGE_REQUEST.value}", LayananPilotPack.CHANGE_REQUEST, "Papan Permintaan", "KANBAN")

    @Test
    fun dataPackWithoutShippedBlueprint_existingDraftWithScreens_isReturned() = runTest {
        // Dulu `null`: blueprint dicari di GarmentBlueprints.all SEBELUM draf tersimpan dicek.
        DomainPackRegistry.register(LayananPilotPack.pack)
        val drafts = FakeDrafts()
        val seeded = layananDraft(listOf(pilotScreen))
        drafts.save(seeded)

        val result = assertNotNull(useCase(drafts)(tenant, LayananPilotPack.CODE, owner), "draf tersimpan harus dikembalikan")
        assertSame(seeded, result, "dikembalikan apa adanya, tidak disentuh")
    }

    @Test
    fun dataPackWithoutShippedBlueprint_noDraft_stillReturnsNull_andCreatesNothing() = runTest {
        // Perubahan ini TIDAK membuat draf untuk pack tanpa blueprint bawaan (itu pekerjaan terpisah).
        DomainPackRegistry.register(LayananPilotPack.pack)
        val drafts = FakeDrafts()

        assertNull(useCase(drafts)(tenant, LayananPilotPack.CODE, owner))
        assertTrue(drafts.rows.isEmpty(), "bootstrap tidak boleh menyimpan apa pun untuk pack tanpa blueprint")
    }

    @Test
    fun dataPackWithoutShippedBlueprint_existingDraftWithoutScreens_isNotBackfilled_unchangedBehaviour() = runTest {
        DomainPackRegistry.register(LayananPilotPack.pack)
        val drafts = FakeDrafts()
        drafts.save(layananDraft(emptyList()))

        assertNull(useCase(drafts)(tenant, LayananPilotPack.CODE, owner), "backfill tetap butuh blueprint bawaan")
        assertTrue(drafts.rows.values.single().draft.screens.isEmpty(), "draf kosong tidak dimodifikasi")
    }

    @Test
    fun unknownPack_returnsNull_evenIfADraftIsStored() = runTest {
        // Pack tak terdaftar di proses ini = kosakata tak bisa dipercaya: tetap null (tidak jatuh ke draf).
        val drafts = FakeDrafts()
        drafts.save(layananDraft(listOf(pilotScreen)))

        assertNull(useCase(drafts)(tenant, LayananPilotPack.CODE, owner))
    }

    private fun useCase(drafts: DiscoveryDraftRepository) =
        EnsureTenantWorkingDraftUseCase(drafts, FakePipelines(), clock)

    private class FakeDrafts : DiscoveryDraftRepository {
        val rows = mutableMapOf<DiscoveryDraftId, StoredDiscoveryDraft>()
        override suspend fun findById(id: DiscoveryDraftId): StoredDiscoveryDraft? = rows[id]
        override suspend fun findByOwner(ownerUserId: UserId): List<StoredDiscoveryDraft> =
            rows.values.filter { it.ownerUserId == ownerUserId }
        override suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft? =
            rows.values.firstOrNull { it.tenantId == tenantId }
        override suspend fun findAll(): List<StoredDiscoveryDraft> = rows.values.toList()
        override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
            rows[stored.id] = stored
            return stored
        }
    }

    private class FakePipelines : TenantPipelineRepository {
        override suspend fun findByTenantId(tenantId: TenantId): CustomTenantPipeline? = null
        override suspend fun save(pipeline: CustomTenantPipeline): Result<CustomTenantPipeline> = Result.success(pipeline)
        override suspend fun deleteByTenantId(tenantId: TenantId): Result<Unit> = Result.success(Unit)
    }
}