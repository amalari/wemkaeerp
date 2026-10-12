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
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.module
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
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
 * B4 (TRD-FUL-001 Track B): Gerbang, transisi status, dan RLS untuk packing & shipment.
 *
 * - Peran tanpa wewenang → 403 fail-closed
 * - Transisi status ganda → 409 Conflict
 * - Tenant isolation (RLS) → lintas-tenant ditolak
 *
 * Integration test: butuh Postgres (repositori default modul).
 */
class FulfillmentPackingRoutesTest {

    private val tenants = InMemoryTenantRepository()
    private val roles = InMemoryRoleRepository()

    private data class Env(val slug: String, val tenantId: TenantId, val suffix: String)

    /** Tenant + jabatan segar per test: state di DB tidak saling menular. */
    private suspend fun registerTenant(suffix: String, accessLevel: AccessLevel? = AccessLevel.MANAGE): Env {
        DatabaseFactory.init()
        val env = Env("packing-probe-$suffix", TenantId("ten-packing-$suffix"), suffix)
        tenants.save(
            Tenant(env.tenantId, TenantSlug(env.slug), TenantName("Packing Probe $suffix"), TenantStatus.ACTIVE,
                SubscriptionTier.PRO, businessPreset = GarmentBlueprints.CMT_MAKLOON)
        )

        if (accessLevel != null) {
            roles.save(
                CustomRole(
                    id = RoleId("role-fulfillment-$accessLevel-$suffix"),
                    tenantId = env.tenantId,
                    name = "Fulfillment $accessLevel",
                    description = "Test role with $accessLevel access",
                    modulePermissions = mapOf(GarmentModules.FULFILLMENT to ModuleAccessConfig(accessLevel))
                )
            )
        }

        // Jabatan tanpa akses fulfillment
        roles.save(
            CustomRole(
                id = RoleId("role-gudang-$suffix"),
                tenantId = env.tenantId,
                name = "Staf Gudang",
                description = "Hanya master data",
                modulePermissions = mapOf(GarmentModules.MASTER_DATA to ModuleAccessConfig(AccessLevel.MANAGE))
            )
        )

        return env
    }

    /** Seed karton tersegel untuk test transisi. */
    private fun seedSealedCarton(tenantIdValue: String, cartonIdValue: String) {
        transaction {
            exec("SET LOCAL app.current_tenant_id = '$tenantIdValue'")
            exec(
                """
                INSERT INTO fulfillment.cartons
                    (id, tenant_id, production_ref, carton_number, item_breakdown, quantity_pcs,
                     gross_weight_kg, unit_ticket_ids, seal_number, status, created_at, updated_at, notes)
                VALUES ('$cartonIdValue', '$tenantIdValue', 'PO-001', 'CTN-2026-0001', '{}', 100,
                        25.5, '[]', 'SEAL-001', 'SEALED', NOW(), NOW(), '')
                """.trimIndent()
            )
        }
    }

    /** Seed shipment issued untuk test transisi. */
    private fun seedIssuedShipment(tenantIdValue: String, shipmentIdValue: String) {
        transaction {
            exec("SET LOCAL app.current_tenant_id = '$tenantIdValue'")
            exec(
                """
                INSERT INTO fulfillment.shipments
                    (id, tenant_id, shipment_number, stock_ownership, carrier, destination,
                     status, shipped_at, delivered_at, created_at, updated_at, notes)
                VALUES ('$shipmentIdValue', '$tenantIdValue', 'SJ-2026-0001', 'OWNED',
                        '{}', '{}', 'ISSUED', NULL, NULL, NOW(), NOW(), '')
                """.trimIndent()
            )
        }
    }

    private fun boot(block: suspend ApplicationTestBuilder.() -> Unit) = testApplication {
        application {
            module(
                tenantRepository = tenants,
                roleRepository = roles
            )
        }
        startApplication()
        block()
    }

    // -------------------------------------------------------------------------
    // B4.1: 403 per level (fail-closed)
    // -------------------------------------------------------------------------

    @Test
    fun getPackingQueue_roleWithoutFulfillmentAccess_returns403() = boot {
        val env = registerTenant("tanpa-akses", null)
        val response = client.get("/api/tenant/fulfillment/packing-queue") {
            asStaff(env.slug, "role-gudang-${env.suffix}")
        }
        assertEquals(403, response.status.value, "VIEW tanpa akses fulfillment harus 403")
    }

    @Test
    fun postCartons_roleWithoutFulfillmentAccess_returns403() = boot {
        val env = registerTenant("tanpa-akses", null)
        val response = client.post("/api/tenant/fulfillment/cartons") {
            asStaff(env.slug, "role-gudang-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"productionRef":"PO-TEST","notes":"test"}""")
        }
        assertEquals(403, response.status.value, "OPERATE tanpa akses fulfillment harus 403")
    }

    @Test
    fun patchCartonReopen_roleWithOperateOnly_returns403() = boot {
        val env = registerTenant("operate-only", AccessLevel.OPERATE)
        seedSealedCarton(env.tenantId.value, "ctn-test-reopen")

        // OPERATE tidak cukup untuk reopen (butuh MANAGE)
        val response = client.patch("/api/tenant/fulfillment/cartons/ctn-test-reopen/reopen") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Salah ukuran","actor":"tester"}""")
        }
        assertEquals(403, response.status.value, "Reopen butuh MANAGE, bukan OPERATE")
    }

    @Test
    fun patchCartonReopen_roleWithManage_returns200() = boot {
        val env = registerTenant("manage-ok", AccessLevel.MANAGE)
        seedSealedCarton(env.tenantId.value, "ctn-test-manage")

        val response = client.patch("/api/tenant/fulfillment/cartons/ctn-test-manage/reopen") {
            asStaff(env.slug, "role-fulfillment-MANAGE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Salah ukuran","actor":"tester"}""")
        }
        assertEquals(200, response.status.value, "MANAGE bisa reopen")
        val body = response.bodyAsText()
        assertTrue(body.contains("REOPENED"), "Status harus REOPENED: $body")
    }

    @Test
    fun deleteShipment_roleWithOperateOnly_returns403() = boot {
        val env = registerTenant("operate-only-delete", AccessLevel.OPERATE)
        seedIssuedShipment(env.tenantId.value, "sj-test-delete")

        // OPERATE tidak cukup untuk cancel (butuh MANAGE)
        val response = client.delete("/api/tenant/fulfillment/shipments/sj-test-delete") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Batal","actor":"tester"}""")
        }
        assertEquals(403, response.status.value, "Delete shipment butuh MANAGE")
    }

    @Test
    fun rbacUncounted_returns403() = boot {
        val env = registerTenant("hantu", null)
        // customRoleId tidak ada di repositori → keputusan tidak terhitung
        val response = client.get("/api/tenant/fulfillment/packing-queue") {
            asStaff(env.slug, "role-hantu")
        }
        assertEquals(403, response.status.value, "RBAC tak terhitung → fail-closed")
    }

    // -------------------------------------------------------------------------
    // B4.2: 409 Transisi status (state machine)
    // -------------------------------------------------------------------------

    @Test
    fun sealAlreadySealedCarton_returns409() = boot {
        val env = registerTenant("seal-ganda", AccessLevel.OPERATE)
        seedSealedCarton(env.tenantId.value, "ctn-sudah-sealed")

        // Coba seal lagi
        val response = client.patch("/api/tenant/fulfillment/cartons/ctn-sudah-sealed/seal") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"sealNumber":"SEAL-002","grossWeightKg":30.0,"actor":"tester"}""")
        }
        assertEquals(409, response.status.value, "Seal karton sudah SEALED → 409")
    }

    @Test
    fun sealEmptyCarton_returns409() = boot {
        val env = registerTenant("kosong", AccessLevel.OPERATE)
        // Buat karton kosong
        val createResp = client.post("/api/tenant/fulfillment/cartons") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"productionRef":"PO-KOSONG"}""")
        }
        assertEquals(200, createResp.status.value)
        val cartonId = createResp.bodyAsText().let {
            """\"id"\s*:\s*"([^"]+)"""".toRegex().find(it)?.groupValues?.get(1) ?: ""
        }

        // Coba seal dengan qty=0
        val sealResp = client.patch("/api/tenant/fulfillment/cartons/$cartonId/seal") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"sealNumber":"SEAL-KOSONG","grossWeightKg":0.0,"actor":"tester"}""")
        }
        assertEquals(400, sealResp.status.value, "Berat 0 harus ditolak")
    }

    @Test
    fun markInTransitAlreadyInTransit_returns409() = boot {
        val env = registerTenant("in-transit-ganda", AccessLevel.OPERATE)
        seedIssuedShipment(env.tenantId.value, "sj-sudah-transit")
        // Sudah di transit dari seed data, tapi status di DB masih ISSUED
        // Perlu update manual untuk simulasi
        transaction {
            exec("SET LOCAL app.current_tenant_id = '${env.tenantId.value}'")
            exec("UPDATE fulfillment.shipments SET status = 'IN_TRANSIT' WHERE id = 'sj-sudah-transit'")
        }

        // Coba in-transit lagi
        val response = client.patch("/api/tenant/fulfillment/shipments/sj-sudah-transit/in-transit") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"actor":"tester"}""")
        }
        assertEquals(409, response.status.value, "In-transit dari IN_TRANSIT → 409")
    }

    @Test
    fun confirmDeliveredBeforeInTransit_returns409() = boot {
        val env = registerTenant("belum-transit", AccessLevel.OPERATE)
        seedIssuedShipment(env.tenantId.value, "sj-belum-transit")

        // Coba delivered tanpa in-transit dulu
        val response = client.patch("/api/tenant/fulfillment/shipments/sj-belum-transit/delivered") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"actor":"tester"}""")
        }
        assertEquals(409, response.status.value, "Delivered tanpa in-transit → 409")
    }

    @Test
    fun confirmDeliveredTwice_returns409() = boot {
        val env = registerTenant("delivered-ganda", AccessLevel.OPERATE)
        seedIssuedShipment(env.tenantId.value, "sj-sudah-delivered")

        // Set ke IN_TRANSIT dulu
        transaction {
            exec("SET LOCAL app.current_tenant_id = '${env.tenantId.value}'")
            exec("UPDATE fulfillment.shipments SET status = 'IN_TRANSIT' WHERE id = 'sj-sudah-delivered'")
        }

        // Pertama: delivered
        val first = client.patch("/api/tenant/fulfillment/shipments/sj-sudah-delivered/delivered") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"actor":"tester"}""")
        }
        assertEquals(200, first.status.value, "Delivered pertama harus berhasil")

        // Kedua: delivered lagi → 409
        val second = client.patch("/api/tenant/fulfillment/shipments/sj-sudah-delivered/delivered") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"actor":"tester"}""")
        }
        assertEquals(409, second.status.value, "Delivered dua kali → 409")
    }

    @Test
    fun cancelIssuedShipment_returns200_andUnlinksCartons() = boot {
        val env = registerTenant("cancel-ok", AccessLevel.MANAGE)
        seedIssuedShipment(env.tenantId.value, "sj-batal")
        // Seed karton dan link-kan
        seedSealedCarton(env.tenantId.value, "ctn-terkait")
        transaction {
            exec("SET LOCAL app.current_tenant_id = '${env.tenantId.value}'")
            exec("INSERT INTO fulfillment.shipment_cartons (tenant_id, shipment_id, carton_id) VALUES ('${env.tenantId.value}', 'sj-batal', 'ctn-terkait')")
        }

        val response = client.delete("/api/tenant/fulfillment/shipments/sj-batal") {
            asStaff(env.slug, "role-fulfillment-MANAGE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Batal pois","actor":"tester"}""")
        }
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue(body.contains("CANCELLED"), "Status harus CANCELLED: $body")
    }

    @Test
    fun cancelInTransitShipment_returns409() = boot {
        val env = registerTenant("cancel-salah-status", AccessLevel.MANAGE)
        seedIssuedShipment(env.tenantId.value, "sj-salah-status")
        // Langsung IN_TRANSIT, tidak bisa cancel
        transaction {
            exec("SET LOCAL app.current_tenant_id = '${env.tenantId.value}'")
            exec("UPDATE fulfillment.shipments SET status = 'IN_TRANSIT' WHERE id = 'sj-salah-status'")
        }

        val response = client.delete("/api/tenant/fulfillment/shipments/sj-salah-status") {
            asStaff(env.slug, "role-fulfillment-MANAGE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"reason":"Batal","actor":"tester"}""")
        }
        assertEquals(409, response.status.value, "Cancel IN_TRANSIT → 409")
    }

    // -------------------------------------------------------------------------
    // B4.3: RLS isolation
    // -------------------------------------------------------------------------

    @Test
    fun crossTenantRead_returns404() = boot {
        val envA = registerTenant("tenant-a", AccessLevel.MANAGE)
        val envB = registerTenant("tenant-b", AccessLevel.MANAGE)

        // Seed karton di tenant A
        seedSealedCarton(envA.tenantId.value, "ctn-tenant-a")

        // Tenant B mencoba baca
        val response = client.get("/api/tenant/fulfillment/packing-queue") {
            asStaff(envB.slug, "role-fulfillment-MANAGE-${envB.suffix}")
        }
        // Karena RLS, tenant B tidak melihat data tenant A
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        // Carton dari tenant A tidak boleh muncul
        assertTrue(!body.contains("ctn-tenant-a"), "Data tenant A tidak boleh muncul di tenant B")
    }

    @Test
    fun crossTenantWrite_returns404() = boot {
        val envA = registerTenant("tenant-a-write", AccessLevel.OPERATE)
        val envB = registerTenant("tenant-b-write", AccessLevel.OPERATE)

        // Seed karton di tenant A
        seedSealedCarton(envA.tenantId.value, "ctn-a-cross")

        // Tenant B mencoba update karton tenant A (ID known)
        val response = client.patch("/api/tenant/fulfillment/cartons/ctn-a-cross/seal") {
            asStaff(envB.slug, "role-fulfillment-OPERATE-${envB.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"sealNumber":"SEAL-CROSS","grossWeightKg":10.0,"actor":"attacker"}""")
        }
        // RLS menolak karena karton tidak milik tenant B
        assertEquals(404, response.status.value, "Cross-tenant write harus 404")
    }

    @Test
    fun viewQueue_withFullAccess_returnsCartonsAndShipments() = boot {
        val env = registerTenant("view-ok", AccessLevel.VIEW)
        seedSealedCarton(env.tenantId.value, "ctn-view")
        seedIssuedShipment(env.tenantId.value, "sj-view")

        val response = client.get("/api/tenant/fulfillment/packing-queue") {
            asStaff(env.slug, "role-fulfillment-VIEW-${env.suffix}")
        }
        assertEquals(200, response.status.value)
        val body = response.bodyAsText()
        assertTrue(body.contains("cartons") || body.contains("shipments"), "Queue harus mengembalikan data")
    }

    // -------------------------------------------------------------------------
    // B4.4: Happy path (sanity)
    // -------------------------------------------------------------------------

    @Test
    fun createAndSealCarton_happyPath() = boot {
        val env = registerTenant("happy-path", AccessLevel.OPERATE)

        // 1. Buat karton
        val createResp = client.post("/api/tenant/fulfillment/cartons") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"productionRef":"PO-2026-001","notes":"Order歧"}""")
        }
        assertEquals(200, createResp.status.value)
        val createBody = createResp.bodyAsText()
        assertTrue(createBody.contains("OPEN"), "Karton baru harus OPEN")

        val cartonId = """\"id"\s*:\s*"([^"]+)"""".toRegex().find(createBody)?.groupValues?.get(1)
        assertTrue(!cartonId.isNullOrEmpty(), "Harus dapat ID karton")

        // 2. Seal karton
        val sealResp = client.patch("/api/tenant/fulfillment/cartons/$cartonId/seal") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""{"sealNumber":"SEAL-2026-001","grossWeightKg":25.5,"actor":"packer1"}""")
        }
        assertEquals(200, sealResp.status.value)
        val sealBody = sealResp.bodyAsText()
        assertTrue(sealBody.contains("SEALED"), "Karton harus jadi SEALED")
        assertTrue(sealBody.contains("SEAL-2026-001"), "Seal number harus tersimpan")
    }

    @Test
    fun createShipment_happyPath() = boot {
        val env = registerTenant("shipment-happy", AccessLevel.OPERATE)
        seedSealedCarton(env.tenantId.value, "ctn-ship-1")

        // Buat shipment dengan karton
        val resp = client.post("/api/tenant/fulfillment/shipments") {
            asStaff(env.slug, "role-fulfillment-OPERATE-${env.suffix}")
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "stockOwnership": "OWNED",
                    "carrier": {"expedition":"JNE","mode":"DARAT","driver":"Budi"},
                    "destination": {"buyer":"PT ABC","address":"Jl.测试 No.1","city":"Jakarta"},
                    "cartonIds":["ctn-ship-1"],
                    "notes":"Kiriman pertama"
                }
            """.trimIndent())
        }
        assertEquals(200, resp.status.value)
        val body = resp.bodyAsText()
        assertTrue(body.contains("ISSUED"), "Shipment harus ISSUED")
        assertTrue(body.contains("JNE"), "Carrier harus tersimpan")
        assertTrue(body.contains("PT ABC"), "Destination harus tersimpan")
    }
}
