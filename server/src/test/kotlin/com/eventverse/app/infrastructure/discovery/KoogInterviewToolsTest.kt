package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.InterviewEvalPacks
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
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
