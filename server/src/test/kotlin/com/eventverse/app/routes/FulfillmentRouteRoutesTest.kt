package com.eventverse.app.routes

import com.eventverse.app.asStaff
import com.eventverse.app.domain.pack.GarmentBlueprints
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.domain.rbac.RoleId
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * B4 (TRD-FLOW-003): gerbang dan siklus rute serah terima sebagai data di sisi server.
 *
 * - Peran tanpa wewenang → 403; keputusan RBAC tak terhitung (jabatan tidak ditemukan) → 403
 *   fail-closed; jabatan ber-MANAGE lewat matriks → 200 (AC-5).
 * - Kode tak dikenal / duplikat / tidak sah → 400, tidak ada yang dilewati diam-diam (AC-4).
 * - Rute non-garment (`DIGITIZING_TO_HOOPING`) bisa didaftarkan dan disetel DIRECT (AC-3).
 * - Menghapus rute yang pernah dipakai perjalanan → 409; menonaktifkan → 200 (AC-6).
 * - Tenant tanpa baris tersimpan tetap melihat daftar rute warisan (paritas AC-2, jembatan S0–S2).
 *
 * Integration test: butuh Postgres (repositori default modul), sama seperti `RouteGateTest`.
 */
class FulfillmentRouteRoutesTest {

    private val tenants = InMemoryTenantRepository()
    private val roles = InMemoryRoleRepository()

    private data class Env(val slug: String, val tenantId: TenantId, val suffix: String)

    /** Tenant + jabatan segar per test: state `fulfillment_routes` di DB tidak saling menular. */
    private suspend fun registerTenant(suffix: String): Env {
        DatabaseFactory.init()
        val env = Env("routes-probe-$suffix", TenantId("ten-routes-$suffix"), suffix)
        tenants.save(
            Tenant(env.tenantId, TenantSlug(env.slug), TenantName("Routes Probe $suffix"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON)
        )
        roles.save(
            CustomRole(
                id = RoleId("role-fulfillment-manager-$suffix"), tenantId = env.tenantId,
                name = "Admin Produksi", description = "Kelola rute & mode serah terima",
                modulePermissions = mapOf(GarmentModules.FULFILLMENT to ModuleAccessConfig(AccessLevel.MANAGE))
            )
        )
        roles.save(
            CustomRole(
                id = RoleId("role-gudang-$suffix"), tenantId = env.tenantId,
                name = "Staf Gudang", description = "Hanya master data",
                modulePermissions = mapOf(GarmentModules.MASTER_DATA to ModuleAccessConfig(AccessLevel.MANAGE))
            )
        )
        return env
    }

    /** Satu perjalanan karung yang pernah lewat [code] — kandidat penghapusannya harus ditolak. */
    private fun seedUsedRoute(tenantIdValue: String, code: String) {
        val stamp = System.nanoTime()
        transaction {
            // Koneksi default Exposed adalah pool tenant (wemade_app) — RLS aktif, jadi transaksi
            // ini harus menyebut tenant-nya, persis seperti DatabaseFactory.dbQuery(tenantId).
            exec("SET LOCAL app.current_tenant_id = '$tenantIdValue'")
            exec(
                """
                INSERT INTO fulfillment.fulfillment_transfers
                    (id, tenant_id, sack_code, size_label, declared_pcs, leg, handover_mode, status,
                     dispatch_weight_kg, dispatch_scale_photo_key, requested_by, requested_at, created_at, updated_at)
                VALUES ('tr-uji-$stamp', '$tenantIdValue', 'KARUNG-UJI-$stamp', 'XL', 12, '$code',
                        'ADMIN_HUB', 'DITERIMA', 2.5, 'uji/timbang.jpg', 'tester', NOW(), NOW(), NOW())
                """.trimIndent()
            )
        }
    }

    private fun boot(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = roles,
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository(),
                departmentRepository = InMemoryDepartmentRepository(),
                employeeRepository = InMemoryEmployeeRepository()
            )
        }
        startApplication()
        block()
    }

    @Test
    fun putRoutes_roleWithoutFulfillmentAccess_returns403() = boot {
        val env = registerTenant("tanpa-akses")
        val response = client.put("/api/tenant/fulfillment/routes") {
            asStaff(env.slug, "role-gudang-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"routes":[]}""")
        }
        assertEquals(403, response.status.value)
    }

    @Test
    fun putRoutes_rbacUncounted_returns403() = boot {
        val env = registerTenant("hantu")
        val response = client.put("/api/tenant/fulfillment/routes") {
            // customRoleId tidak ada di repositori jabatan — keputusan tidak bisa dihitung → 403.
            asStaff(env.slug, "role-hantu")
            contentType(ContentType.Application.Json)
            setBody("""{"routes":[]}""")
        }
        assertEquals(403, response.status.value)
    }

    @Test
    fun putRoutes_authorizedManager_returns200_andRoutesVisible() = boot {
        val env = registerTenant("manager")
        val response = client.put("/api/tenant/fulfillment/routes") {
            asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"routes":[
                    {"code":"DIGITIZING_TO_HOOPING","label":"Digitizing ke Hooping"},
                    {"code":"QC_RAJUT_TO_FINISHING","label":"QC Rajut ke Finishing"}
                ]}
                """.trimIndent()
            )
        }
        assertEquals(200, response.status.value)
        val saved = response.bodyAsText()
        assertTrue(saved.contains("DIGITIZING_TO_HOOPING"), "rute non-garment harus tersimpan: $saved")

        val listed = client.get("/api/tenant/fulfillment/routes") {
            asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
        }
        assertTrue(listed.bodyAsText().contains("DIGITIZING_TO_HOOPING"), "GET /routes harus memuat salinan tersimpan")
    }

    @Test
    fun putRoutes_duplicateOrInvalidCode_returns400() = boot {
        val env = registerTenant("rusak")
        val caller: suspend io.ktor.client.HttpClient.(String) -> Int = { body ->
            put("/api/tenant/fulfillment/routes") {
                asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status.value
        }
        assertEquals(400, client.caller("""{"routes":[{"code":"A_ONE","label":"A"},{"code":"A_ONE","label":"B"}]}"""), "kode ganda")
        assertEquals(400, client.caller("""{"routes":[{"code":"raJut","label":"huruf kecil"}]}"""), "kode tidak sah")
    }

    @Test
    fun getRoutes_tenantWithoutStoredRows_fallsBackToLegacyTemplate() = boot {
        val env = registerTenant("warisan")
        val response = client.get("/api/tenant/fulfillment/routes") {
            asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
        }
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        // Paritas AC-2 via jembatan S0–S2: sebelum template pack terisi (A1), tenant rajut melihat
        // dua rute enum dengan label yang sama persis seperti sebelum migrasi.
        assertTrue(body.contains("QC_RAJUT_TO_FINISHING") && body.contains("QC Rajut ke Finishing"), body)
        assertTrue(body.contains("FINISHING_TO_QC_FINISHING") && body.contains("Finishing ke QC Finishing"), body)
    }

    @Test
    fun putRoutes_deletingUsedRoute_returns409_deactivatingSucceeds() = boot {
        val env = registerTenant("riwayat")
        seedUsedRoute(env.tenantId.value, "QC_RAJUT_TO_FINISHING")
        val caller: suspend io.ktor.client.HttpClient.(String) -> Int = { body ->
            put("/api/tenant/fulfillment/routes") {
                asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status.value
        }
        assertEquals(200, client.caller("""{"routes":[{"code":"QC_RAJUT_TO_FINISHING","label":"QC Rajut ke Finishing"}]}"""))
        // Menghapus (bukan menonaktifkan) rute yang pernah dipakai perjalanan → 409 (AC-6).
        assertEquals(409, client.caller("""{"routes":[{"code":"DIGITIZING_TO_HOOPING","label":"Digitizing ke Hooping"}]}"""))
        // Menonaktifkan → sah.
        assertEquals(
            200,
            client.caller(
                """
                {"routes":[
                    {"code":"QC_RAJUT_TO_FINISHING","label":"QC Rajut ke Finishing","active":false},
                    {"code":"DIGITIZING_TO_HOOPING","label":"Digitizing ke Hooping"}
                ]}
                """.trimIndent()
            )
        )
    }

    @Test
    fun putRouteSettings_unknownRoute_returns400() = boot {
        val env = registerTenant("mode-asing")
        val response = client.put("/api/tenant/fulfillment/route-settings") {
            asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"routes":[{"route":"TIDAK_ADA","mode":"DIRECT"}]}""")
        }
        assertEquals(400, response.status.value)
    }

    @Test
    fun putRouteSettings_directOnNonGarmentRoute_returns200_andReflected() = boot {
        val env = registerTenant("mode-langsung")
        val caller: suspend io.ktor.client.HttpClient.(String, String) -> Int = { path, body ->
            put(path) {
                asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
                contentType(ContentType.Application.Json)
                setBody(body)
            }.status.value
        }
        assertEquals(
            200,
            client.caller("/api/tenant/fulfillment/routes", """{"routes":[{"code":"DIGITIZING_TO_HOOPING","label":"Digitizing ke Hooping"}]}""")
        )
        assertEquals(
            200,
            client.caller("/api/tenant/fulfillment/route-settings", """{"routes":[{"route":"DIGITIZING_TO_HOOPING","mode":"DIRECT"}]}""")
        )
        val view = client.get("/api/tenant/fulfillment/route-settings") {
            asStaff(env.slug, "role-fulfillment-manager-${env.suffix}")
        }
        val body = view.bodyAsText()
        assertTrue(body.contains("\"route\":\"DIGITIZING_TO_HOOPING\"") && body.contains("\"mode\":\"DIRECT\""), body)
        assertTrue(body.contains("\"isExplicit\":true"), "mode DIRECT rute non-garment harus tersimpan eksplisit: $body")
    }
}
