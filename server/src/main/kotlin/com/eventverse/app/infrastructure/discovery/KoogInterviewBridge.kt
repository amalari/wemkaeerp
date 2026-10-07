package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import com.eventverse.app.shared.discovery.DiscoveryDraftDecodeException
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue

/**
 * Jembatan & dekoder tunggal wawancara (plan IV-C2) — **satu tempat** untuk alat `interview_state`,
 * isi keadaan di pesan pengguna, dan jawaban akhir agent, supaya keduanya **sepakat pada dokumen
 * berjembatan** (pelajaran SP: `validate_draft` pernah menolak singkatan `useShipped` yang diterima
 * jalur jawaban akhir).
 *
 * Model hanya menulis dokumen `interview`. Pack dan blueprint **disuntikkan dari draf berjalan**
 * sebelum dekode — server yang tahu pack-nya, model tidak perlu (dan tidak boleh) menulisnya ulang
 * dari ingatan. Singkatan `{"interview":{"useCurrent":true}}` berarti "langkah ini tak ada yang
 * berubah": diganti dokumen sesi berjalan sebelum dekode, bukan dilonggar aturannya.
 */
internal object KoogInterviewBridge {

    /** Dokumen sesi berjalan, **terbungkus** `{"interview":{...}}` — bentuk yang sama persis dengan jawaban akhir. */
    fun interviewStateJson(draft: DiscoveryDraft): String {
        val session = draft.interview ?: InterviewSession(step = interviewStartStep(draft))
        val document = JsonParser.parseObject(DiscoveryDraftCodec.encodeToString(draft.copy(interview = session)))
        val interview = requireNotNull(document.obj("interview")) { "encode sesi wawancara gagal" }
        return JsonValue.Obj(mapOf("interview" to interview)).encode()
    }

    /** Jawaban model → [InterviewSession] langkah itu. Gagal **berpath**, tidak pernah senyap. */
    fun decodeInterviewAnswer(answer: String, draft: DiscoveryDraft): InterviewSession {
        val doc = applyUseCurrentBridge(extractJsonObject(answer), draft)
        val root = JsonParser.parseObject(doc)
        val interview = root.obj("interview")
            ?: throw DiscoveryDraftDecodeException("$.interview", "jawaban tidak memuat dokumen 'interview'")
        val shell = DiscoveryDraftCodec.encodeToString(draft.copy(interview = null))
        val shellJson = JsonParser.parseObject(shell)
        val injected = JsonValue.Obj(
            shellJson.entries + ("interview" to interview)
        )
        return DiscoveryDraftCodec.decode(injected.encode()).interview
            ?: throw DiscoveryDraftDecodeException("$.interview", "dokumen tidak memuat sesi wawancara")
    }

    /** Langkah awal wawancara = langkah sesi berjalan, atau G1 bila draf belum membawa sesi. */
    private fun interviewStartStep(draft: DiscoveryDraft) =
        draft.interview?.step ?: InterviewStep.G1_DIVISI

    /** Ganti singkatan `useCurrent` dengan dokumen sesi berjalan; kode tak dikenal ditolak berpath. */
    private fun applyUseCurrentBridge(json: String, draft: DiscoveryDraft): String {
        val root = runCatching { JsonParser.parseObject(json) }.getOrNull() ?: return json
        val interview = root.obj("interview") ?: return json
        if (interview.entries.keys.singleOrNull() != "useCurrent" || interview["useCurrent"] != JsonValue.Bool(true)) return json
        val current = JsonParser.parseObject(interviewStateJson(draft)).obj("interview")
            ?: throw DiscoveryDraftDecodeException("$.interview.useCurrent", "sesi berjalan tidak terbaca")
        return JsonValue.Obj(root.entries + ("interview" to current)).encode()
    }
}
