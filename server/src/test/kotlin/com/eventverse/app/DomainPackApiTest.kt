package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.pack.DomainPackCodec
import com.eventverse.app.shared.rbac.AccessDecisionCodec
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti B7** (TRD-PLAT-001-tenant-pack AC): vertikal klinik didefinisikan sebagai **dokumen JSON**, disimpan &
 * dikunci superadmin, ditetapkan ke tenant, lalu berlaku untuk tenant itu — kosakata, menu (`/me/access`), dan gerbang —
 * tanpa satu baris kode yang menyebut klinik. Tenant garment di server yang sama tidak berubah.
 */
class DomainPackApiTest {

    private val klinikSlug = "klinik-uji"
    private val garmentSlug = "garment-uji"

    @AfterTest
    fun cleanup() = DomainPackRegistry.unregister(KLINIK.code)

    @Test
    fun superadmin_savesLocksAndAssignsDataPack_andTenantRunsOnIt() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        application { module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
            entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
            moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
            employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
            domainPackRepository = InMemoryDomainPackRepository()) }

        val json = DomainPackCodec.encodeToString(KLINIK)
        assertEquals(HttpStatusCode.OK, client.put("/api/admin/domain-packs/klinik") { asSuperadmin(); contentType(ContentType.Application.Json); setBody(json) }.status)
        assertEquals(HttpStatusCode.OK, client.post("/api/admin/domain-packs/klinik/lock") { asSuperadmin() }.status)
        val assign = client.put("/api/admin/tenants/$klinikSlug/domain-pack") { asSuperadmin(); setBody("""{"code":"klinik"}""") }
        assertEquals(HttpStatusCode.OK, assign.status, assign.bodyAsText())

        // Kosakata tenant = pack klinik; tenant garment tetap garment.
        val pack = DomainPackCodec.decode(client.get("/api/tenant/pack") { asTenant(klinikSlug) }.bodyAsText())
        assertEquals(KLINIK, pack)
        // A4: istilah & label aksi ikut perjalanan lewat route — bukan hanya ada di kode klien.
        assertEquals("klinik", pack.term(VocabularyKey.WORKPLACE))
        assertEquals("Tambah Kunjungan", pack.actionLabel(ModuleActionCode.ADD))
        assertTrue(pack.actions.none { action -> action.label.contains("SPK") }, pack.actions.map { it.label }.toString())
        assertEquals(GarmentDomainPack.CODE, DomainPackCodec.decode(client.get("/api/tenant/pack") { asTenant(garmentSlug) }.bodyAsText()).code)
        // …dan tenant garment di server yang sama tetap berbicara garment.
        assertEquals("pabrik", GarmentDomainPack.pack.term(VocabularyKey.WORKPLACE))

        // Menu pemilik tenant klinik: tepat modul pack klinik.
        val access = AccessDecisionCodec.decode(JsonParser.parseObject(client.get("/api/tenant/me/access") { asTenant(klinikSlug) }.bodyAsText()))
        assertEquals(listOf("org_chart", "klinik_antrean"), access.keys.map { it.value })
    }

    @Test
    fun nonSuperadmin_isForbidden_andBrokenPack_isRejected_andUnknownTenantPack_is409() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        runBlocking { tenants.save(tenant("ten-hilang", "pack-hilang", DomainPackCode("hilang"))) }
        application { module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
            entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
            moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
            employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
            domainPackRepository = InMemoryDomainPackRepository()) }
        val json = DomainPackCodec.encodeToString(KLINIK)

        // Owner tenant bukan superadmin: tulis platform ditolak (fail-closed).
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/domain-packs/klinik") { asTenant(garmentSlug, Role.TENANT_ADMIN); setBody(json) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/admin/tenants/$garmentSlug/domain-pack") { asTenant(garmentSlug, Role.TENANT_ADMIN); setBody("""{"code":"klinik"}""") }.status)

        // Pack rusak (kind tak dikenal) dan pack yang merebut id garment ditolak saat ditulis.
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/domain-packs/klinik") { asSuperadmin(); setBody(json.replace("\"OPERATIONAL\"", "\"MAGIC\"")) }.status)
        assertEquals(HttpStatusCode.BadRequest, client.put("/api/admin/domain-packs/klinik") { asSuperadmin(); setBody(json.replace("klinik_antrean", "crm_sales")) }.status)

        // Pack tidak dikenal: tenant ditolak, tidak jatuh ke garment.
        assertEquals(HttpStatusCode.Conflict, client.get("/api/tenant/pack") { asTenant("pack-hilang") }.status)
    }

    private fun tenants() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(tenant("ten-klinik-uji", klinikSlug, GarmentDomainPack.CODE))
            repo.save(tenant("ten-garment-uji", garmentSlug, GarmentDomainPack.CODE))
        }
    }

    private fun tenant(id: String, slug: String, pack: DomainPackCode) =
        Tenant(TenantId(id), TenantSlug(slug), TenantName(slug), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = pack)

    private companion object {
        val KLINIK = DomainPack(
            code = DomainPackCode("klinik"),
            displayName = "Klinik & Layanan Kesehatan",
            phases = listOf(PhaseDefinition(PhaseCode("LAYANAN"), 1, "1. Layanan", "Pasien datang", 0xFF2563EB)),
            slots = listOf(SlotDefinition(SlotCode("klinik_layanan"), "Layanan", PhaseCode("LAYANAN"), PortType("Kunjungan"), PortType("Rekam"))),
            portTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
            wiredPortTypes = setOf(PortType("Kunjungan"), PortType("Rekam")),
            sections = listOf(
                GarmentDomainPack.pack.sections.first { it.code.value == "GOVERNANCE" },
                ModuleSection(ModuleSectionCode("LAYANAN"), "Layanan", 2, 0xFF16A34A, 0xFFF0FDF4)
            ),
            modules = listOf(
                requireNotNull(GarmentDomainPack.pack.module(GarmentModules.ORG_CHART)),
                ModuleDefinition(ModuleId("klinik_antrean"), "Antrean Pasien", "Antrean pendaftaran & poli", ModuleSectionCode("LAYANAN"),
                    ModuleKind.OPERATIONAL, "clipboard", ScopeCapability.HIERARCHICAL,
                    setOf(DataScope.OWN_DATA_ONLY, DataScope.ALL_TENANT_DATA), SlotCode("klinik_layanan"))
            ),
            // A4: istilah chrome & label aksi = data pack. Klinik memakai bahasanya sendiri; kata konveksi
            // ("SPK", "pabrik") tidak boleh datang dari sini.
            actions = listOf(
                ModuleAction(ModuleActionCode.ADD, "Tambah Kunjungan"),
                ModuleAction(ModuleActionCode.EDIT, "Ubah Kunjungan"),
                ModuleAction(ModuleActionCode.APPROVE, "Setujui Kunjungan"),
                ModuleAction(ModuleActionCode.DELETE, "Hapus Kunjungan")
            ),
            vocabulary = mapOf(VocabularyKey.WORKPLACE to "klinik", VocabularyKey.DOCUMENT to "Kunjungan")
        )
    }
}
