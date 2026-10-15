package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.pack.slotPorts
import com.eventverse.app.shared.json.JsonParser
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti AC C2** (plan IV-C): alat `interview_state` dan dekoder jawaban akhir **sepakat pada dokumen
 * berjembatan** — apa yang ditampilkan alat harus bisa dibaca dekoder produksi tanpa kejutan, dan
 * katalog memberi petunjuk asal yang benar menurut registri.
 */
class KoogInterviewToolsTest {

    private val pack = InterviewEvalPacks.klinikPack
    private val session = InterviewEvalPacks.klinikSession
    private val draft: DiscoveryDraft = InterviewEvalPacks.draftOf(pack, session)

    @Test
    fun `keluaran interview_state terbaca dekoder jawaban akhir - alat dan dekoder sepakat`() {
        val stateJson = KoogInterviewBridge.interviewStateJson(draft)
        val decoded = KoogInterviewBridge.decodeInterviewAnswer(stateJson, draft)
        assertEquals(session, decoded, "Dokumen dari alat wajib dibaca dekoder tanpa perubahan")
    }

    @Test
    fun `draf tanpa sesi menampilkan sesi G1 - model tidak pernah melihat dokumen kosong`() {
        val fresh = InterviewEvalPacks.draftOf(pack, null)
        val state = JsonParser.parseObject(KoogInterviewBridge.interviewStateJson(fresh)).obj("interview")!!
        assertEquals("g1_divisi", (state["step"] as? com.eventverse.app.shared.json.JsonValue.Str)?.value)
    }

    @Test
    fun `useCurrent dijembatani ke sesi berjalan sebelum dekode`() {
        val answer = """{"interview":{"useCurrent":true}}"""
        val decoded = KoogInterviewBridge.decodeInterviewAnswer(answer, draft)
        assertEquals(session, decoded, "useCurrent berarti 'tidak ada yang berubah'")
    }

    @Test
    fun `jawaban tanpa kunci interview ditolak berpath`() {
        val result = runCatching { KoogInterviewBridge.decodeInterviewAnswer("""{"lain":true}""", draft) }
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()!!.message!!.contains("interview"))
    }

    @Test
    fun `katalog menunjuk asal sesuai registri - org_chart reuse_platform modul kustom new`() = runBlocking {
        val catalog = JsonParser.parseObject(interviewCatalogJson(pack))
        val modules = (catalog["modules"] as com.eventverse.app.shared.json.JsonValue.Arr).items
            .map { it as com.eventverse.app.shared.json.JsonValue.Obj }
        val byId = modules.associate { (it["id"] as com.eventverse.app.shared.json.JsonValue.Str).value to it }
        assertEquals("reuse_platform", (byId.getValue("org_chart")["originHint"] as com.eventverse.app.shared.json.JsonValue.Str).value)
        assertEquals(true, (byId.getValue("org_chart")["shipped"] as com.eventverse.app.shared.json.JsonValue.Bool).value)
        assertEquals(false, (byId.getValue("klinik_kasir")["shipped"] as com.eventverse.app.shared.json.JsonValue.Bool).value)
        assertEquals("new", (byId.getValue("klinik_kasir")["originHint"] as com.eventverse.app.shared.json.JsonValue.Str).value)
        val ports = (catalog["wiredPorts"] as com.eventverse.app.shared.json.JsonValue.Arr).items
        assertTrue(ports.isNotEmpty(), "Port wired wajib tercantum untuk tebakan G4")
    }

    /**
     * Temuan 7 (model sungguhan 2026-10-16): validator menuntut portType handoff = keluaran modul asal ATAU masukan modul tujuan,
     * tetapi katalog tidak memuat port per modul sehingga model hanya bisa menebak dari `wiredPorts` global dan gagal 3 putaran.
     */
    @Test
    fun `katalog memuat port masuk dan keluar tiap modul operasional persis seperti yang dipakai validator`() {
        val catalog = JsonParser.parseObject(interviewCatalogJson(pack))
        val modules = (catalog["modules"] as com.eventverse.app.shared.json.JsonValue.Arr).items
            .map { it as com.eventverse.app.shared.json.JsonValue.Obj }
        val byId = modules.associateBy { (it["id"] as com.eventverse.app.shared.json.JsonValue.Str).value }
        pack.modules.forEach { m ->
            val expected = pack.slotPorts(m.id)
            val entry = byId.getValue(m.id.value)
            assertEquals(expected?.first?.value, (entry["input"] as? com.eventverse.app.shared.json.JsonValue.Str)?.value, "input ${m.id.value}")
            assertEquals(expected?.second?.value, (entry["output"] as? com.eventverse.app.shared.json.JsonValue.Str)?.value, "output ${m.id.value}")
        }
        assertTrue(pack.modules.any { pack.slotPorts(it.id) != null }, "prasyarat: ada modul bersuplai port")
    }

    @Test
    fun `setiap handoff sah menurut validator dapat dibangun dari informasi katalog saja`() {
        val catalog = JsonParser.parseObject(interviewCatalogJson(pack))
        val modules = (catalog["modules"] as com.eventverse.app.shared.json.JsonValue.Arr).items
            .map { it as com.eventverse.app.shared.json.JsonValue.Obj }
        val byId = modules.associateBy { (it["id"] as com.eventverse.app.shared.json.JsonValue.Str).value }
        fun port(id: String, key: String) = (byId.getValue(id)[key] as? com.eventverse.app.shared.json.JsonValue.Str)?.value
        assertTrue(session.handoffs.isNotEmpty(), "prasyarat: sesi uji punya handoff")
        session.handoffs.forEach { h ->
            val fromOut = port(h.from.value, "output"); val toIn = port(h.to.value, "input")
            assertTrue(h.portType.value == fromOut || h.portType.value == toIn,
                "handoff ${h.from.value}->${h.to.value} memakai ${h.portType.value}; katalog hanya menyebut output=$fromOut input=$toIn")
        }
    }

    @Test
    fun `registry alat memuat interview_state dan interview_catalog`() {
        val descriptors = interviewToolRegistry { draft }.tools.map { it.descriptor.name }
        assertTrue(InterviewTools.INTERVIEW_STATE in descriptors)
        assertTrue(InterviewTools.INTERVIEW_CATALOG in descriptors)
    }

    @Test
    fun `alat state dieksekusi menghasilkan dokumen yang sama dengan jembatan`() = runBlocking {
        val output = InterviewStateTool { draft }.execute(Unit)
        assertEquals(KoogInterviewBridge.interviewStateJson(draft), output)
        assertEquals(InterviewStep.DONE, KoogInterviewBridge.decodeInterviewAnswer(output, draft).step, "dekoder mengembalikan sesi berjalan utuh")
    }
}
