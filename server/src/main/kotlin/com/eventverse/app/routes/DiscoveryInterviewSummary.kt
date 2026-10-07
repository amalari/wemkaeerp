package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.interview.nextQuestion
import com.eventverse.app.shared.discovery.InterviewSessionCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Menambahkan bagian wawancara ke ringkasan draf (PLAN-iv-B B4): `interview` (sesi), `nextQuestion` (null = selesai),
 * dan `origin` per modul (asal dari tautan wawancara; modul tanpa tautan tak diberi kunci). Draf **tanpa** sesi
 * dikembalikan apa adanya — bukti "draf lama tidak berubah" dikunci `DiscoveryInterviewApiTest`.
 */
internal fun withInterview(base: JsonValue.Obj, stored: StoredDiscoveryDraft): JsonValue.Obj {
    val session = stored.draft.interview ?: return base
    val origins = session.links.groupBy { it.moduleId.value }.mapValues { (_, links) -> links.first().origin.code }
    val modules = base.array("modules").map { m ->
        val obj = m as? JsonValue.Obj ?: return@map m
        val origin = obj.string("id")?.let(origins::get) ?: return@map m
        JsonValue.Obj(obj.entries + ("origin" to jsonOf(origin)))
    }
    val question = session.nextQuestion(stored.draft)
    return JsonValue.Obj(
        base.entries + mapOf(
            "modules" to jsonArrayOf(modules),
            "interview" to InterviewSessionCodec.encode(session),
            "nextQuestion" to (question?.let(InterviewSessionCodec::encodeQuestion) ?: JsonValue.Null)
        )
    )
}
