package com.eventverse.app

import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.GarmentDomainPack
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
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import io.ktor.client.request.get
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti plan §2 A6**: funnel discovery ber-login, draf milik pemiliknya (403 untuk pengguna lain),
 * dan LOCKED immutable. Agent deterministik (kill-switch A8) sehingga tanpa jaringan & tanpa biaya.
 */
class DiscoveryApiTest {

    private val klinikSlug = "klinik-uji"
    private val garmentSlug = "garment-uji"

    @Test
    fun `tanpa login ditolak 401 dan narasi menghasilkan draf 201`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        application { app(tenants, InMemoryDiscoveryDraftRepository()) }

        assertEquals(
            HttpStatusCode.Unauthorized,
            client.post("/api/discovery/drafts") { contentType(ContentType.Application.Json); setBody("""{"narrative":"klinik"}""") }.status
        )

        val created = client.post("/api/discovery/drafts") {
            asTenant(garmentSlug); contentType(ContentType.Application.Json)
            setBody("""{"narrative":"Kami klinik gigi dengan antrean pasien per poli dan tagihan kasir.","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
    }

    @Test
    fun `draf hanya bisa dibaca dan direvisi pemiliknya dan LOCKED immutable`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        val createBody = """{"id":"draft-uji-1","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}"""
        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(createBody) }.status
        )

        // Prospek lain membaca/merevisi draf itu: 403 (gerbang pemilik — T12).
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/discovery/drafts/draft-uji-1") { asTenant(klinikSlug) }.status)
        assertEquals(HttpStatusCode.Forbidden, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(klinikSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Pemilik boleh membaca & merevisi selama masih DRAFT.
        assertEquals(HttpStatusCode.OK, client.get("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.OK, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Lock → update berikutnya ditolak 409 (Kontrak 5).
        assertEquals(HttpStatusCode.OK, client.post("/api/discovery/drafts/draft-uji-1/lock") { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.Conflict, client.put("/api/discovery/drafts/draft-uji-1") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(currentDocument()) }.status)

        // Superadmin boleh melihat antrean seluruh draf (review platform) — via act-as workspace.
        assertEquals(HttpStatusCode.OK, client.get("/api/discovery/drafts") { asSuperadminActingAs(garmentSlug) }.status)
    }

    @Test
    fun `pratinjau sandbox menampilkan pack draf dan berakhir fail-closed`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"id":"draft-uji-2","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}""") }.status
        )

        // Mulai sesi pratinjau; pack draf terlihat lewat jalur data B7, tanpa scaffold kode.
        val started = client.post("/api/discovery/drafts/draft-uji-2/preview?ttlMinutes=60") { asTenant(garmentSlug) }
        assertEquals(HttpStatusCode.OK, started.status)
        val slug = JsonParser.parseObject(started.bodyAsText()).string("sandboxSlug")!!
        assertEquals("sandbox-klinik", slug)

        val packJson = client.get("/api/tenant/pack") { asSuperadminActingAs(slug) }
        assertEquals(HttpStatusCode.OK, packJson.status)
        assertEquals("klinik", JsonParser.parseObject(packJson.bodyAsText()).string("code"))

        // Registry LOCKED platform tidak tersentuh.
        assertEquals(GarmentDomainPack.pack, DomainPackRegistry.find(GarmentDomainPack.CODE))

        // Sesi diakhiri → pack dilepas; tenant sandbox ditolak fail-closed, tidak jatuh ke garment.
        assertEquals(HttpStatusCode.OK, client.delete("/api/discovery/drafts/draft-uji-2/preview") { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.Conflict, client.get("/api/tenant/pack") { asSuperadminActingAs(slug) }.status)
        assertEquals(GarmentDomainPack.pack, DomainPackRegistry.find(GarmentDomainPack.CODE))
    }

    @Test
    fun `estimasi submit funnel dan handoff superadmin bekerja end-to-end`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"id":"draft-b-1","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}""") }.status
        )

        // B1: estimasi dari draf — pack klinik penuh modul baru tanpa riwayat build → rentang ditahan jujur.
        val priced = client.get("/api/discovery/drafts/draft-b-1/price") { asTenant(garmentSlug) }
        assertEquals(HttpStatusCode.OK, priced.status)
        val priceBody = JsonParser.parseObject(priced.bodyAsText())
        assertEquals("klinik", priceBody.string("packCode"))
        assertTrue(priceBody.string("withheld") == "true" || priced.bodyAsText().contains("\"withheld\":true"))

        // B2: submit sebelum dikunci ditolak; setelah dikunci → 201 dengan id lead.
        assertEquals(
            HttpStatusCode.Conflict,
            client.post("/api/discovery/drafts/draft-b-1/submit") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"companyName":"Klinik Sehat"}""") }.status
        )
        assertEquals(HttpStatusCode.OK, client.post("/api/discovery/drafts/draft-b-1/lock") { asTenant(garmentSlug) }.status)
        val submitted = client.post("/api/discovery/drafts/draft-b-1/submit") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"companyName":"Klinik Sehat"}""") }
        assertEquals(HttpStatusCode.Created, submitted.status)
        assertTrue(JsonParser.parseObject(submitted.bodyAsText()).string("leadId")!!.startsWith("lead-"))

        // B3: handoff hanya superadmin; lalu membuat tenant dengan pack & blueprint draf.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/api/discovery/drafts/draft-b-1/handoff") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"tenantSlug":"klinik-sehat","companyName":"Klinik Sehat"}""") }.status
        )
        val handed = client.post("/api/discovery/drafts/draft-b-1/handoff") { asSuperadminActingAs(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"tenantSlug":"klinik-sehat","companyName":"Klinik Sehat"}""") }
        assertEquals(HttpStatusCode.Created, handed.status)
        assertEquals("klinik", JsonParser.parseObject(handed.bodyAsText()).string("packCode"))
        assertEquals("klinik_starter", JsonParser.parseObject(handed.bodyAsText()).string("blueprintCode"))
        // Tenant baru sungguh terprovisioning dan membaca pack-nya sendiri lewat jalur data B7.
        val pack = client.get("/api/tenant/pack") { asSuperadminActingAs("klinik-sehat") }
        assertEquals(HttpStatusCode.OK, pack.status)
        assertEquals("klinik", JsonParser.parseObject(pack.bodyAsText()).string("code"))
        // Bersihkan registry pack data hasil handoff agar tes lain mulai dari kondisi bawaan.
        DomainPackRegistry.unregister(com.eventverse.app.domain.pack.DomainPackCode("klinik"))
    }

    @Test
    fun `scaffold kandidat pr hanya untuk superadmin dan draf terkunci`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody("""{"id":"draft-b-2","narrative":"Kami klinik dengan jadwal dokter dan tagihan.","industryHint":"klinik"}""") }.status
        )

        // Draf yang belum dikunci ditolak 409 — scaffold hanya untuk draf beku.
        assertEquals(
            HttpStatusCode.Conflict,
            client.post("/api/discovery/drafts/draft-b-2/scaffold") { asSuperadminActingAs(garmentSlug) }.status
        )
        assertEquals(HttpStatusCode.OK, client.post("/api/discovery/drafts/draft-b-2/lock") { asTenant(garmentSlug) }.status)

        // Pemilik draf (bukan superadmin) ditolak.
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/api/discovery/drafts/draft-b-2/scaffold") { asTenant(garmentSlug) }.status
        )

        // Superadmin: kandidat PR berisi migrasi + snippet, tanpa berkas .kt yang langsung aktif.
        val scaffolded = client.post("/api/discovery/drafts/draft-b-2/scaffold") { asSuperadminActingAs(garmentSlug) }
        assertEquals(HttpStatusCode.Created, scaffolded.status)
        val body = scaffolded.bodyAsText()
        assertEquals("klinik", JsonParser.parseObject(body).string("packCode"))
        assertTrue(body.contains("__register_klinik_modules.sql"))
        assertTrue(body.contains("apply_tenant_rls_in"))
        assertTrue(body.contains("ModuleSchemaMap.snippet.kt.txt"))
        assertTrue(body.contains("docs/handoff/klinik/"))
    }

    @Test
    fun `pola studio dibaca berlogin dan ditulis superadmin saja`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        // Tanpa login → 401.
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/discovery/patterns").status)

        // Login biasa boleh membaca (kosong dulu) tapi tidak boleh menulis.
        assertEquals(HttpStatusCode.OK, client.get("/api/discovery/patterns") { asTenant(garmentSlug) }.status)
        assertEquals(
            HttpStatusCode.Forbidden,
            client.post("/api/discovery/patterns") {
                asTenant(garmentSlug); contentType(ContentType.Application.Json)
                setBody("""{"name":"Pola Uji","widget":"TABLE","pattern":{"kolom":["a"]}}""")
            }.status
        )

        // Superadmin menyimpan; widget asing dan pack hantu ditolak fail-closed.
        assertEquals(
            HttpStatusCode.Created,
            client.post("/api/discovery/patterns") {
                asSuperadminActingAs(garmentSlug); contentType(ContentType.Application.Json)
                setBody("""{"id":"pattern-1","name":"Pola Uji","widget":"TABLE","packCode":"garment","pattern":{"kolom":["a","b"]}}""")
            }.status
        )
        assertEquals(
            HttpStatusCode.Conflict,
            client.post("/api/discovery/patterns") {
                asSuperadminActingAs(garmentSlug); contentType(ContentType.Application.Json)
                setBody("""{"name":"Pola Rusak","widget":"SPREADSHEET","pattern":{}}""")
            }.status
        )
        assertEquals(
            HttpStatusCode.Conflict,
            client.post("/api/discovery/patterns") {
                asSuperadminActingAs(garmentSlug); contentType(ContentType.Application.Json)
                setBody("""{"name":"Pola Hantu","widget":"TABLE","packCode":"pack_hantu","pattern":{}}""")
            }.status
        )

        // Pola tersimpan dan terbaca kembali.
        val listed = client.get("/api/discovery/patterns") { asTenant(garmentSlug) }
        assertTrue(listed.bodyAsText().contains("\"Pola Uji\""))
        assertTrue(listed.bodyAsText().contains("\"widget\":\"TABLE\""))
    }

    /**
     * **Fase C**: payload yang benar-benar dikirim `PrototypeStudioScreen` — `pattern.rows` berurutan
     * dan `packCode` null (pola umum, tidak terikat pack). Bentuk ini yang gagal diam-diam kalau salah
     * satu sisi berubah: klien mengira tersimpan, server menyimpan pola tanpa baris.
     *
     * Nama **dan** id tetap dengan sengaja. Repositori pola di sini adalah Postgres pengembang
     * (`module()` belum menyuntik `PrototypePatternRepository`, lihat catatan di plan §4), sementara
     * `ops.prototype_patterns` punya `UNIQUE(name)`: nama baru tiap run akan membuat run kedua ditolak
     * 409 dan meninggalkan satu baris sampah per eksekusi suite.
     */
    @Test
    fun `payload studio dari klien tersimpan utuh dan urut`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        val saved = client.post("/api/discovery/patterns") {
            asSuperadminActingAs(garmentSlug); contentType(ContentType.Application.Json)
            setBody(
                """{"id":"pattern-klien-uji","name":"Pola Uji Klien","widget":"CUSTOM_SCREEN","packCode":null,""" +
                    """"pattern":{"rows":[{"Blok":"Ringkasan","Lebar":"penuh"},{"Blok":"Daftar","Lebar":"separuh"}]}}"""
            )
        }
        assertEquals(HttpStatusCode.Created, saved.status)

        val listed = client.get("/api/discovery/patterns") { asTenant(garmentSlug) }.bodyAsText()
        assertTrue(listed.contains("\"name\":\"Pola Uji Klien\""))
        assertTrue(listed.contains("\"widget\":\"CUSTOM_SCREEN\""))
        // Urutan baris menentukan pasangan blok di pratinjau → urutan harus selamat di JSONB.
        assertTrue(listed.indexOf("Ringkasan") < listed.indexOf("Daftar"), "Urutan baris pola harus utuh")
    }

    @Test
    fun `pdf blueprint memakai tiket pendek dan gerbang pemilik`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        val drafts = InMemoryDiscoveryDraftRepository()
        application { app(tenants, drafts) }

        // Draf prospek (pemiliknya = pengguna token garment-uji).
        val created = client.post("/api/discovery/drafts") {
            asTenant(garmentSlug); contentType(ContentType.Application.Json)
            setBody("""{"id":"draft-pdf-1","narrative":"Klinik gigi dengan antrean pasien per poli.","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)

        val pdfPath = "/api/discovery/drafts/draft-pdf-1/blueprint.pdf"

        // Tanpa sesi dan tanpa tiket: ditolak plugin, bukan menghasilkan PDF.
        assertEquals(HttpStatusCode.Unauthorized, client.get(pdfPath).status)

        // Pemilik menukar sesinya dengan tiket pendek, lalu membuka PDF **tanpa** header Bearer —
        // inilah jalur yang dipakai tab browser.
        val ticketResponse = client.post("/api/discovery/drafts/draft-pdf-1/print-ticket") { asTenant(garmentSlug) }
        assertEquals(HttpStatusCode.OK, ticketResponse.status)
        val ticket = JsonParser.parseObject(ticketResponse.bodyAsText()).string("ticket").orEmpty()
        assertTrue(ticket.isNotBlank(), "Server tidak menerbitkan tiket cetak")

        val pdf = client.get("$pdfPath?ticket=$ticket")
        assertEquals(HttpStatusCode.OK, pdf.status)
        assertEquals(ContentType.Application.Pdf, pdf.contentType()?.withoutParameters())
        assertEquals("private, no-store", pdf.headers[HttpHeaders.CacheControl])
        val bytes = pdf.bodyAsBytes()
        assertTrue(bytes.size > 1000, "PDF blueprint terlalu kecil")
        assertEquals("%PDF", String(bytes.sliceArray(0..3), Charsets.ISO_8859_1))

        // Bearer biasa juga boleh (jalur API), dan superadmin tetap boleh seperti endpoint JSON.
        assertEquals(HttpStatusCode.OK, client.get(pdfPath) { asTenant(garmentSlug) }.status)
        assertEquals(HttpStatusCode.OK, client.get(pdfPath) { asSuperadminActingAs(garmentSlug) }.status)

        // Pengguna lain dan tiket draf lain ditolak: gerbang pemilik + cakupan path tiket.
        assertEquals(HttpStatusCode.Forbidden, client.get(pdfPath) { asTenant(klinikSlug) }.status)
        val otherDraft = client.post("/api/discovery/drafts") {
            asTenant(garmentSlug); contentType(ContentType.Application.Json)
            setBody("""{"id":"draft-pdf-2","narrative":"Klinik kedua dengan kasir.","industryHint":"klinik"}""")
        }
        assertEquals(HttpStatusCode.Created, otherDraft.status)
        val otherTicket = client.post("/api/discovery/drafts/draft-pdf-2/print-ticket") { asTenant(garmentSlug) }
        val foreignTicket = JsonParser.parseObject(otherTicket.bodyAsText()).string("ticket").orEmpty()
        // 401, bukan 403: cakupan path di tiket sudah ditolak plugin sebelum rute ini berjalan —
        // tiket yang bocor dari riwayat browser tidak pernah sampai ke pemeriksaan pemilik draf.
        assertEquals(HttpStatusCode.Unauthorized, client.get("$pdfPath?ticket=$foreignTicket").status)

        // Tiket untuk draf yang tidak ada tidak bisa diterbitkan.
        assertEquals(
            HttpStatusCode.NotFound,
            client.post("/api/discovery/drafts/draft-hantu/print-ticket") { asTenant(garmentSlug) }.status
        )
    }

    @Test
    fun `narasi tercatat di buku demand dan kandidat rule of three terbaca superadmin saja`() = testApplication {
        DatabaseFactory.init()
        val tenants = tenants()
        application { app(tenants, InMemoryDiscoveryDraftRepository()) }

        // Tiga prospek (draf berbeda) memakai istilah "gigi" yang belum punya modul di pack mana pun.
        listOf(
            """{"id":"draft-demand-1","narrative":"Klinik gigi dengan antrean pasien.","industryHint":"klinik"}""",
            """{"id":"draft-demand-2","narrative":"Klinik gigi anak dengan jadwal dokter gigi.","industryHint":"klinik"}""",
            """{"id":"draft-demand-3","narrative":"Klinik gigi lengkap dengan rekam medis.","industryHint":"klinik"}"""
        ).forEach { body ->
            assertEquals(
                HttpStatusCode.Created,
                client.post("/api/discovery/drafts") { asTenant(garmentSlug); contentType(ContentType.Application.Json); setBody(body) }.status
            )
        }

        // Sinyal produk milik platform: pengguna biasa tidak boleh membaca buku demand.
        assertEquals(HttpStatusCode.Forbidden, client.get("/api/discovery/demands") { asTenant(garmentSlug) }.status)

        val response = client.get("/api/discovery/demands") { asSuperadminActingAs(garmentSlug) }
        assertEquals(HttpStatusCode.OK, response.status)
        val obj = JsonParser.parseObject(response.bodyAsText())
        val candidates = obj.array("candidates").filterIsInstance<JsonValue.Obj>()
        val gigi = candidates.singleOrNull { it.string("term") == "gigi" }
        assertTrue(gigi != null, "istilah gigi harus jadi kandidat: $candidates")
        assertEquals(3, gigi.int("demandCount"), "tiga demand berbeda: $candidates")
        // Demand tercatat lengkap: narasi verbatim + apa yang bisa diekspresikan agent.
        val demands = obj.array("demands").filterIsInstance<JsonValue.Obj>()
        assertEquals(3, demands.size)
        assertTrue(demands.all { it.string("narrative")?.contains("gigi") == true })
        assertTrue(demands.first().array("matchedModules").isNotEmpty(), "modul hasil agent tercatat")

        // E1: narasi asli dipulihkan ke ringkasan draf — prospek yang kembali melihat ceritanya.
        val draft = JsonParser.parseObject(
            client.get("/api/discovery/drafts/draft-demand-1") { asTenant(garmentSlug) }.bodyAsText()
        )
        assertEquals("Klinik gigi dengan antrean pasien.", draft.string("narrative"))
    }

    private fun io.ktor.server.application.Application.app(
        tenants: InMemoryTenantRepository,
        drafts: InMemoryDiscoveryDraftRepository,
        demands: InMemoryDiscoveryDemandRepository = InMemoryDiscoveryDemandRepository()
    ) = module(tenantRepository = tenants, pipelineRepository = InMemoryTenantPipelineRepository(),
        entitlementRepository = InMemoryTenantEntitlementRepository(), roleRepository = InMemoryRoleRepository(),
        moduleAssignmentRepository = InMemoryModuleAssignmentRepository(), departmentRepository = InMemoryDepartmentRepository(),
        employeeRepository = InMemoryEmployeeRepository(), auditLogRepository = InMemoryAuditLogRepository(),
        domainPackRepository = InMemoryDomainPackRepository(), discoveryDraftRepository = drafts,
        discoveryDemandRepository = demands)

    /** Dokumen klinik yang sama dengan keluaran agent deterministik — jadi PUT identik selalu sah. */
    private fun currentDocument() = runBlocking {
        DiscoveryDraftCodec.encodeToString(
            DeterministicDiscoveryAgent().draft(
                DiscoveryRequest("Kami klinik dengan jadwal dokter dan tagihan.", industryHint = "klinik")
            ).getOrThrow()
        )
    }

    private fun tenants() = InMemoryTenantRepository().also { repo ->
        runBlocking {
            repo.save(tenant("ten-klinik-uji", klinikSlug))
            repo.save(tenant("ten-garment-uji", garmentSlug))
        }
    }

    private fun tenant(id: String, slug: String) =
        Tenant(TenantId(id), TenantSlug(slug), TenantName(slug), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = GarmentDomainPack.CODE)
}
