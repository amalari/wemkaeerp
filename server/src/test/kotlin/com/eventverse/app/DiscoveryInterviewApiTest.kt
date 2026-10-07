package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.RoleHint
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import com.eventverse.app.infrastructure.DatabaseFactory
import com.eventverse.app.infrastructure.InMemoryAuditLogRepository
import com.eventverse.app.infrastructure.InMemoryDepartmentRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDemandRepository
import com.eventverse.app.infrastructure.InMemoryDiscoveryDraftRepository
import com.eventverse.app.infrastructure.InMemoryDomainPackRepository
import com.eventverse.app.infrastructure.InMemoryEmployeeRepository
import com.eventverse.app.infrastructure.InMemoryModuleAssignmentRepository
import com.eventverse.app.infrastructure.InMemoryRoleRepository
import com.eventverse.app.infrastructure.InMemoryTenantEntitlementRepository
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import com.eventverse.app.infrastructure.InMemoryTenantRepository
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * B4 (PLAN-iv-B): route wawancara fail-closed + ringkasan membawa `interview`/`nextQuestion`/`origin`, dan draf
 * lama tak berubah. Pack klinik uji diberi kamus peran supaya tebakan deterministik muncul tanpa LLM.
 */
class DiscoveryInterviewApiTest {

    private val ownerSlug = "klinik-uji"
    private val otherSlug = "garment-uji"
    private val narrative = "Kami klinik dengan antrean pasien per poli dan tagihan kasir. Perawat memeriksa pasien lalu kasir menagih."

    private fun tenants() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            listOf("ten-klinik-uji" to ownerSlug, "ten-garment-uji" to otherSlug).forEach { (id, slug) ->
                repo.save(Tenant(TenantId(id), TenantSlug(slug), TenantName(slug), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = GarmentDomainPack.CODE))
            }
        }
    }

    private fun io.ktor.server.application.Application.app(drafts: InMemoryDiscoveryDraftRepository) = module(
        tenantRepository = tenants(), pipelineRepository = InMemoryTenantPipelineRepository(),
        entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
        moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
        employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
        domainPackRepository = InMemoryDomainPackRepository(), discoveryDraftRepository = drafts,
        discoveryDemandRepository = InMemoryDiscoveryDemandRepository()
    )

    /** Membuat draf klinik lewat API lalu memberi pack-nya kamus peran (perawat, kasir → dua modul operasional pertama). */
    private suspend fun HttpClient.createDraft(drafts: InMemoryDiscoveryDraftRepository, id: String): List<String> {
        val created = post("/api/discovery/drafts") {
            asTenant(ownerSlug); contentType(ContentType.Application.Json)
            setBody("""{"id":"$id","narrative":"$narrative","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val stored = requireNotNull(drafts.findById(DiscoveryDraftId(id)))
        val ops = stored.draft.pack.modules.filter { it.slot != null }.take(2)
        assertEquals(2, ops.size, "pack klinik agent deterministik harus punya >= 2 modul operasional")
        val hinted = stored.draft.pack.copy(roleHints = listOf(
            RoleHint("perawat", "Perawat", ops[0].id), RoleHint("kasir", "Kasir", ops[1].id)
        ))
        drafts.save(stored.copy(draft = stored.draft.copy(pack = hinted)))
        return ops.map { it.id.value }
    }

    private suspend fun HttpClient.interview(id: String, body: String, slug: String? = ownerSlug): HttpResponse =
        post("/api/discovery/drafts/$id/interview") {
            slug?.let { asTenant(it) }; contentType(ContentType.Application.Json); setBody(body)
        }

    private fun obj(r: String) = JsonParser.parseObject(r)

    @Test
    fun `tanpa login 401, bukan pemilik 403 termasuk superadmin, draf hantu 404, aksi asing 400`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        client.createDraft(drafts, "iv-1")

        assertEquals(HttpStatusCode.Unauthorized, client.interview("iv-1", """{"action":"start"}""", slug = null).status)
        assertEquals(HttpStatusCode.Forbidden, client.interview("iv-1", """{"action":"start"}""", slug = otherSlug).status)
        assertEquals(HttpStatusCode.Forbidden, client.post("/api/discovery/drafts/iv-1/interview") {
            asSuperadminActingAs(ownerSlug); contentType(ContentType.Application.Json); setBody("""{"action":"start"}""")
        }.status)
        assertEquals(HttpStatusCode.NotFound, client.interview("hantu", """{"action":"start"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.interview("iv-1", """{"action":"terbang"}""").status)
        assertEquals(HttpStatusCode.BadRequest, client.interview("iv-1", "bukan json").status)
        // Penolakan tidak menyentuh draf.
        assertNull(requireNotNull(drafts.findById(DiscoveryDraftId("iv-1"))).draft.interview)
    }

    @Test
    fun `draf lama tanpa wawancara tidak memuat kunci baru dan ringkasannya tak berubah`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        client.createDraft(drafts, "iv-2")

        val before = client.get("/api/discovery/drafts/iv-2") { asTenant(ownerSlug) }.bodyAsText()
        client.interview("iv-2", """{"action":"answer","questionId":"x","outcome":"confirmed"}""")   // ditolak: belum dimulai
        client.interview("iv-2", """{"action":"start"}""", slug = otherSlug)                        // ditolak: 403
        val after = client.get("/api/discovery/drafts/iv-2") { asTenant(ownerSlug) }.bodyAsText()

        assertEquals(before, after)
        val keys = obj(after).entries.keys
        assertTrue("interview" !in keys && "nextQuestion" !in keys, "kunci wawancara bocor ke draf lama: $keys")
        assertTrue(obj(after).array("modules").filterIsInstance<JsonValue.Obj>().none { it.has("origin") })
    }

    @Test
    fun `mulai lalu jawab tiap giliran sampai selesai, ringkasan membawa pertanyaan dan asal modul`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        val ops = client.createDraft(drafts, "iv-3")

        val started = obj(client.interview("iv-3", """{"action":"start"}""").bodyAsText())
        val session = assertNotNull(started.obj("interview"))
        assertEquals(2, session.array("links").size)
        val q1 = assertNotNull(started.obj("nextQuestion"))
        assertEquals("g1_divisi", q1.string("step"))
        assertEquals(2, q1.array("guesses").size)
        // Modul tertaut menerima asal (NEW untuk modul pack baru); idempoten: mulai ulang tak menimpa.
        val withOrigin = started.array("modules").filterIsInstance<JsonValue.Obj>().filter { it.has("origin") }
        assertEquals(ops.toSet(), withOrigin.map { it.string("id") }.toSet())
        assertTrue(withOrigin.all { it.string("origin") == ModuleOrigin.NEW.code })
        assertEquals(started.obj("interview"), obj(client.interview("iv-3", """{"action":"start"}""").bodyAsText()).obj("interview"))

        // Jawaban basi ditolak 400 dan tak mengubah apa pun.
        assertEquals(HttpStatusCode.BadRequest, client.interview("iv-3", """{"action":"answer","questionId":"g3_modul_t9","outcome":"confirmed"}""").status)

        var question = q1
        var turns = 0
        while (true) {
            val id = question.string("id")!!
            val reply = obj(client.interview("iv-3", """{"action":"answer","questionId":"$id","outcome":"confirmed"}""").bodyAsText())
            turns++
            question = reply.obj("nextQuestion") ?: break
            assertTrue(turns <= 8, "melebihi batas giliran")
        }
        val done = obj(client.get("/api/discovery/drafts/iv-3") { asTenant(ownerSlug) }.bodyAsText())
        assertEquals("done", done.obj("interview")!!.string("step"))
        assertNull(done.obj("nextQuestion").takeIf { it != null && it != JsonValue.Null })
        assertTrue(done.obj("interview")!!.array("links").filterIsInstance<JsonValue.Obj>().all { it.string("confirmed") == "confirmed" })
        assertEquals(turns, done.obj("interview")!!.array("answers").size)
    }

    @Test
    fun `terima semua menutup wawancara dengan SKIPPED, dan draf terkunci 409`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        client.createDraft(drafts, "iv-4")

        val all = obj(client.interview("iv-4", """{"action":"accept_all"}""").bodyAsText())
        assertEquals("done", all.obj("interview")!!.string("step"))
        assertTrue(all.obj("interview")!!.array("links").filterIsInstance<JsonValue.Obj>().all { it.string("confirmed") == "skipped" })

        assertEquals(HttpStatusCode.OK, client.post("/api/discovery/drafts/iv-4/lock") { asTenant(ownerSlug) }.status)
        assertEquals(HttpStatusCode.Conflict, client.interview("iv-4", """{"action":"accept_all"}""").status)
    }

    @Test
    fun `sesi suntingan klien yang melanggar aturan ditolak 400 berpath dan tak tersimpan`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        client.createDraft(drafts, "iv-5")
        val started = obj(client.interview("iv-5", """{"action":"start"}""").bodyAsText())
        val qid = started.obj("nextQuestion")!!.string("id")!!

        val bad = JsonValue.Obj(started.obj("interview")!!.entries + ("links" to JsonValue.Arr(listOf(JsonValue.Obj(mapOf(
            "roleKey" to JsonValue.Str("perawat"), "moduleId" to JsonValue.Str("klinik_hantu"), "origin" to JsonValue.Str("new"),
            "features" to JsonValue.Arr(emptyList()), "confirmed" to JsonValue.Str("changed")
        ))))))
        val rejected = client.interview("iv-5", """{"action":"answer","questionId":"$qid","outcome":"changed","session":${bad.encode()}}""")
        assertEquals(HttpStatusCode.BadRequest, rejected.status)
        assertTrue(rejected.bodyAsText().contains("$.interview.links[0].moduleId"), rejected.bodyAsText())
        assertEquals(started.obj("interview"), obj(client.get("/api/discovery/drafts/iv-5") { asTenant(ownerSlug) }.bodyAsText()).obj("interview"))

        // Kunci enum tak dikenal di sesi suntingan: ditolak codec, bukan jatuh ke bawaan.
        val unknown = JsonValue.Obj(started.obj("interview")!!.entries + ("step" to JsonValue.Str("g9")))
        val r2 = client.interview("iv-5", """{"action":"answer","questionId":"$qid","outcome":"changed","session":${unknown.encode()}}""")
        assertEquals(HttpStatusCode.BadRequest, r2.status)
        assertTrue(r2.bodyAsText().contains("$.session.step"))
    }

    @Test
    fun `mode konsultan membuka F0 dan ringkasan membawa basis per modul dan profil lewat jawaban`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        val ops = client.createDraft(drafts, "iv-6")

        val started = obj(client.interview("iv-6", """{"action":"start","mode":"konsultan"}""").bodyAsText())
        assertEquals("f0_bisnis", started.obj("nextQuestion")!!.string("step"))
        val withBasis = started.array("modules").filterIsInstance<JsonValue.Obj>().filter { it.has("basis") }
        assertEquals(ops.toSet(), withBasis.map { it.string("id") }.toSet())
        assertTrue(withBasis.all { it.obj("basis")!!.string("basis") == "narasi" && narrative.contains(it.obj("basis")!!.string("quote")!!) })
        assertEquals(2, started.obj("interview")!!.int("version"))

        val afterF0 = obj(client.interview("iv-6", """{"action":"answer","questionId":"f0_bisnis_t1","outcome":"confirmed","text":"Klinik umum 40 pasien sehari"}""").bodyAsText())
        assertEquals("Klinik umum 40 pasien sehari", afterF0.obj("interview")!!.obj("profile")!!.string("summary"))
        assertEquals("f1_tujuan", afterF0.obj("nextQuestion")!!.string("step"))
    }

    @Test
    fun `ringkasan memuat modul bersama rujukan dengan label pack, dan draf tanpa rujukan tak berubah`() = testApplication {
        DatabaseFactory.init()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(drafts) }
        client.createDraft(drafts, "iv-7")
        val before = client.get("/api/discovery/drafts/iv-7") { asTenant(ownerSlug) }.bodyAsText()
        assertTrue(!obj(before).has("sharedModules"), "draf tanpa rujukan tak boleh memuat kunci baru")

        val stored = requireNotNull(drafts.findById(DiscoveryDraftId("iv-7")))
        val slot = GarmentDomainPack.pack.slot(com.eventverse.app.domain.pack.GarmentSlots.COSTING_HPP)!!
        val ports = stored.draft.pack.portTypes.toList()
        val ref = com.eventverse.app.domain.pack.ModuleReference(
            com.eventverse.app.domain.pack.GarmentModules.COSTING_HPP, "Perhitungan Biaya",
            mapOf(ports[0] to slot.defaultInput, ports[1] to slot.defaultOutput)
        )
        drafts.save(stored.copy(draft = stored.draft.copy(pack = stored.draft.pack.copy(moduleReferences = listOf(ref)))))

        val after = obj(client.get("/api/discovery/drafts/iv-7") { asTenant(ownerSlug) }.bodyAsText())
        val shared = after.array("sharedModules").filterIsInstance<JsonValue.Obj>().single()
        assertEquals("costing_hpp", shared.string("id"))
        assertEquals("Perhitungan Biaya", shared.string("displayName"))
        assertEquals(ports[0].value, shared.string("slotInput"), "port dalam kosakata pack, bukan port platform")
        assertEquals(2, shared.obj("portMapping")!!.entries.size)
        // Modul rujukan belum masuk modul pack (RBAC/kanvas menunggu persetujuan).
        assertTrue(after.array("modules").filterIsInstance<JsonValue.Obj>().none { it.string("id") == "costing_hpp" })
    }
}
