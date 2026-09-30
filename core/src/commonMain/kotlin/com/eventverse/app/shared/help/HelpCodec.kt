package com.eventverse.app.shared.help

import com.eventverse.app.domain.help.usecases.HelpResult
import com.eventverse.app.domain.help.usecases.HelpSuggestion
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tutorial.TutorialId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Format kabel `POST /api/tenant/help/ask` (TRD-HELP-001 §4.4). Saran dengan id tak valid dilewati, bukan ditebak. */
object HelpCodec {

    data class AskRequest(val question: String, val currentModule: ModuleId?)

    fun encodeRequest(question: String, currentModule: ModuleId?): JsonValue.Obj = jsonObjectOf(
        "question" to jsonOf(question),
        "currentModule" to jsonOf(currentModule?.value),
    )

    /** `null` bila `question` tidak ada. Modul yang tidak valid diabaikan (pertanyaan tetap dijawab tanpa konteks layar). */
    fun decodeRequest(payload: JsonValue.Obj): AskRequest? {
        val question = payload.string("question") ?: return null
        val module = payload.string("currentModule")?.let { runCatching { ModuleId(it) }.getOrNull() }
        return AskRequest(question, module)
    }

    fun encodeResult(result: HelpResult): JsonValue.Obj = jsonObjectOf(
        "answer" to jsonOf(result.answer),
        "suggestion" to (result.suggestion?.let(::encodeSuggestion) ?: JsonValue.Null),
        "alternatives" to jsonArrayOf(result.alternatives.map(::encodeSuggestion)),
        "agentRef" to jsonOf(result.agentRef),
    )

    fun decodeResult(payload: JsonValue.Obj): HelpResult = HelpResult(
        answer = payload.string("answer").orEmpty(),
        suggestion = payload.obj("suggestion")?.let(::decodeSuggestion),
        alternatives = payload.objectArray("alternatives").mapNotNull(::decodeSuggestion),
        agentRef = payload.string("agentRef").orEmpty(),
    )

    private fun encodeSuggestion(s: HelpSuggestion) = jsonObjectOf(
        "tutorialId" to jsonOf(s.tutorialId.value),
        "title" to jsonOf(s.title),
        "module" to jsonOf(s.moduleId?.value),
        "stepIndex" to jsonOf(s.stepIndex),
    )

    private fun decodeSuggestion(o: JsonValue.Obj): HelpSuggestion? = runCatching {
        HelpSuggestion(
            tutorialId = TutorialId(requireNotNull(o.string("tutorialId"))),
            title = o.string("title").orEmpty(),
            moduleId = o.string("module")?.let(::ModuleId),
            stepIndex = o.int("stepIndex") ?: 0,
        )
    }.getOrNull()
}
