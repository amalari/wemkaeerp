package com.eventverse.app.presentation.discovery.interview

import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.shared.discovery.InterviewSessionCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Sambungan wawancara ke server (`POST /api/discovery/drafts/{id}/interview`). Klien **tidak menghitung** langkah
 * berikutnya: setiap balasan adalah ringkasan draf dari server (sesi + `nextQuestion`) dan state UI diganti
 * olehnya. `null` pada [InterviewSessionState.remote] = mode lokal (tes dan pratinjau).
 */
interface InterviewRemote {
    suspend fun start(draftId: String, consultant: Boolean = true): Result<DiscoveryDraftUi>

    suspend fun answer(draftId: String, questionId: String, outcome: Confirmation, text: String?, session: InterviewSession): Result<DiscoveryDraftUi>

    suspend fun acceptAll(draftId: String): Result<DiscoveryDraftUi>
}

class ApiInterviewRemote(private val client: DiscoveryApiClient) : InterviewRemote {

    override suspend fun start(draftId: String, consultant: Boolean): Result<DiscoveryDraftUi> = send(draftId, buildMap {
        put("action", jsonOf("start"))
        if (consultant) put("mode", jsonOf("konsultan"))
    })

    override suspend fun answer(draftId: String, questionId: String, outcome: Confirmation, text: String?, session: InterviewSession): Result<DiscoveryDraftUi> =
        send(draftId, buildMap {
            put("action", jsonOf("answer"))
            put("questionId", jsonOf(questionId))
            put("outcome", jsonOf(outcome.code))
            text?.let { put("text", jsonOf(it)) }
            put("session", InterviewSessionCodec.encode(session))
        })

    override suspend fun acceptAll(draftId: String): Result<DiscoveryDraftUi> = send(draftId, mapOf("action" to jsonOf("accept_all")))

    private suspend fun send(draftId: String, fields: Map<String, JsonValue>): Result<DiscoveryDraftUi> =
        client.answerInterview(draftId, JsonValue.Obj(fields))
            .mapCatching { raw -> DiscoveryDraftUi.fromJson(raw as? JsonValue.Obj ?: throw IllegalStateException("Respons wawancara tidak dikenali")) }
}
