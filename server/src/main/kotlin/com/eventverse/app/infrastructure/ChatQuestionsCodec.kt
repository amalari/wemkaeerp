package com.eventverse.app.infrastructure

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Pertanyaan follow-up pesan chat ⇄ JSON (kolom `questions`, V93). Satu parser; dokumen rusak = galat, bukan kosong. */
object ChatQuestionsCodec {

    fun encode(questions: List<Clarification>): JsonValue.Arr = jsonArrayOf(questions.map { c ->
        val base = jsonObjectOf("id" to jsonOf(c.id), "question" to jsonOf(c.question))
        c.answer?.let { JsonValue.Obj(base.entries + ("answer" to jsonOf(it))) } ?: base
    })

    fun decode(raw: String): List<Clarification> {
        val arr = JsonParser.parse(raw) as? JsonValue.Arr ?: error("questions bukan array: $raw")
        return arr.items.mapIndexed { i, item ->
            val o = item as? JsonValue.Obj ?: error("questions[$i] bukan objek")
            Clarification(
                id = (o["id"] as? JsonValue.Str)?.value ?: error("questions[$i].id wajib"),
                question = (o["question"] as? JsonValue.Str)?.value ?: error("questions[$i].question wajib"),
                answer = (o["answer"] as? JsonValue.Str)?.value
            )
        }
    }
}
