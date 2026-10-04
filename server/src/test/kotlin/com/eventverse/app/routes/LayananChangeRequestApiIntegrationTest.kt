package com.eventverse.app.routes

import com.eventverse.app.asTenant
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.LayananPilotPack
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.PostgresTenantRepository
import com.eventverse.app.module
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Jalur sukses modul pilot `layanan_change_request` (PLAN-proto-C, butir C3): CRUD nyata ke Postgres,
 * validasi sama dengan prototype (required, opsi enum, transisi status, tanggal ISO), dan isolasi antar tenant.
 *
 * **Hanya jalan di database uji** — `DB_NAME` wajib berisi `scratch`, kalau tidak test dilewati. Alasannya:
 * `DatabaseFactory.init()` menjalankan Flyway, dan test ini menulis baris; keduanya tidak boleh menyentuh
 * database dev bersama. Jalankan dengan
 * `docker exec wemade-postgres psql -U postgres -c "CREATE DATABASE wemake_pilot_scratch"` lalu
 * `DB_NAME=wemake_pilot_scratch ./gradlew :server:test --tests '*LayananChangeRequestApiIntegrationTest*'`.
 *
 * Catatan: koneksi aplikasi memakai pemilik DB (tanpa `DB_APP_USER`), yang **melewati RLS**. Isolasi tenant di
 * tes ini dibuktikan oleh filter `tenant_id` di repository; RLS sendiri dibuktikan terpisah lewat SQL sebagai
 * role `wemade_app` (lihat docs/teaching/teaching-proto-c-handoff-pilot.md).
 */
class LayananChangeRequestApiIntegrationTest {

    private val enabled = System.getenv("DB_NAME").orEmpty().contains("scratch")
    private val tenants = PostgresTenantRepository()
    private val slugA = "pilot-a"
    private val slugB = "pilot-b"
    private val base = "/api/tenant/modules/layanan_change_request/change_requests"

    @BeforeTest
    fun setup() {
        if (!enabled) return
        DatabaseFactory.init()
        if (DomainPackRegistry.find(LayananPilotPack.CODE) == null) DomainPackRegistry.register(LayananPilotPack.pack)
        runBlocking {
            listOf(slugA to "ten-pilot-a", slugB to "ten-pilot-b").forEach { (slug, id) ->
                if (tenants.findById(TenantId(id)) == null) {
                    tenants.save(Tenant(TenantId(id), TenantSlug(slug), TenantName("Tenant $slug"), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = LayananPilotPack.CODE))
                }
            }
        }
    }

    @AfterTest
    fun cleanup() { if (enabled) DomainPackRegistry.unregister(LayananPilotPack.CODE) }

    private fun ApplicationTestBuilder.install() {
        application {
            module(
                tenantRepository = tenants,
                pipelineRepository = InMemoryTenantPipelineRepository(),
                entitlementRepository = InMemoryTenantEntitlementRepository(),
                roleRepository = InMemoryRoleRepository(),
                moduleAssignmentRepository = InMemoryModuleAssignmentRepository()
            )
        }
    }

    private fun json(values: String) = """{"values":{$values}}"""

    private suspend fun ApplicationTestBuilder.create(slug: String, values: String) =
        client.post(base) { asTenant(slug); contentType(ContentType.Application.Json); setBody(json(values)) }

    @Test
    fun owner_crud_roundTrip_withStateMachineAndValidation() = testApplication {
        if (!enabled) return@testApplication
        install()

        // tambah — kelima tipe field tersimpan dan terbaca kembali
        val created = create(slugA, """"judul":"Tambah kolom Revisi","peminta":"Rina","prioritas":"Tinggi","status":"Baru","perkiraan_jam":"12.5","target_selesai":"2026-11-01","mendesak":"ya"""")
        assertEquals(HttpStatusCode.Created, created.status)
        val row = JsonParser.parseObject(created.bodyAsText())
        val id = row.string("id").orEmpty()
        val values = row.obj("values")!!
        assertEquals("12.5", values.string("perkiraan_jam"))
        assertEquals("2026-11-01", values.string("target_selesai"))
        assertEquals("ya", values.string("mendesak"))

        // validasi sama dengan prototype
        assertEquals(HttpStatusCode.BadRequest, create(slugA, """"peminta":"tanpa judul","status":"Baru"""").status, "required ditegakkan")
        assertEquals(HttpStatusCode.BadRequest, create(slugA, """"judul":"x","status":"Hantu"""").status, "opsi enum")
        assertEquals(HttpStatusCode.BadRequest, create(slugA, """"judul":"x","status":"Baru","target_selesai":"besok"""").status, "tanggal ISO")
        assertEquals(HttpStatusCode.BadRequest, create(slugA, """"judul":"x","status":"Baru","perkiraan_jam":"banyak"""").status, "angka")

        // pindah status: alur sah ok, loncat ditolak (state machine di server)
        val ok = client.put("$base/$id") { asTenant(slugA); contentType(ContentType.Application.Json); setBody(json(""""status":"Ditinjau"""")) }
        assertEquals(HttpStatusCode.OK, ok.status)
        val jump = client.put("$base/$id") { asTenant(slugA); contentType(ContentType.Application.Json); setBody(json(""""status":"Selesai"""")) }
        assertEquals(HttpStatusCode.BadRequest, jump.status)
        assertTrue("tidak boleh pindah" in jump.bodyAsText())

        // baca & daftar
        assertEquals("Ditinjau", JsonParser.parseObject(client.get("$base/$id") { asTenant(slugA) }.bodyAsText()).obj("values")!!.string("status"))
        assertTrue(client.get(base) { asTenant(slugA) }.bodyAsText().contains(id))

        // hapus
        assertEquals(HttpStatusCode.OK, client.delete("$base/$id") { asTenant(slugA) }.status)
        assertEquals(HttpStatusCode.NotFound, client.get("$base/$id") { asTenant(slugA) }.status)
    }

    @Test
    fun tenants_areIsolated_oneCannotReadChangeOrDeleteTheOthersRows() = testApplication {
        if (!enabled) return@testApplication
        install()
        val id = JsonParser.parseObject(create(slugA, """"judul":"Milik A","status":"Baru"""").bodyAsText()).string("id").orEmpty()

        assertEquals(HttpStatusCode.NotFound, client.get("$base/$id") { asTenant(slugB) }.status)
        assertEquals(HttpStatusCode.NotFound, client.put("$base/$id") { asTenant(slugB); contentType(ContentType.Application.Json); setBody(json(""""judul":"dibajak"""")) }.status)
        assertEquals(HttpStatusCode.NotFound, client.delete("$base/$id") { asTenant(slugB) }.status)
        assertTrue(!client.get(base) { asTenant(slugB) }.bodyAsText().contains(id), "daftar B tidak memuat baris A")
        assertEquals("Milik A", JsonParser.parseObject(client.get("$base/$id") { asTenant(slugA) }.bodyAsText()).obj("values")!!.string("judul"), "baris A utuh")
        client.delete("$base/$id") { asTenant(slugA) }
    }
}
