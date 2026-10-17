package com.eventverse.app.routes

import com.eventverse.app.domain.builder.ChatMessage
import com.eventverse.app.infrastructure.ChatQuestionsCodec
import com.eventverse.app.shared.builder.ModuleBriefCodec
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Pesan chat sebagai JSON; patch usulan disisipkan apa adanya (sudah JSON sah). Bagian BRIEF (TRD-BLDR-001 §4) memakai
 * [ModuleBriefCodec]; untuk pesan non-BRIEF: `features:[]`, `stages:[]`, `briefSource:null`.
 */
internal fun messageJson(m: ChatMessage): JsonValue {
    val patch = m.proposedDraftJson?.let { runCatching { JsonParser.parse(it) }.getOrNull() }
    return jsonObjectOf(
        "id" to jsonOf(m.id.value),
        "role" to jsonOf(m.role.name),
        "text" to jsonOf(m.text),
        "summary" to jsonArrayOf(m.proposedSummary.map(::jsonOf)),
        "proposedDraft" to (patch ?: JsonValue.Null),
        "hasPendingPatch" to jsonOf(m.hasPendingPatch),
        "appliedDraftId" to jsonOf(m.appliedDraftId),
        "createdAt" to jsonOf(m.createdAt?.toString()),
        "moduleId" to jsonOf(m.moduleId),
        "kind" to jsonOf(m.kind.name),
        "questions" to ChatQuestionsCodec.encode(m.questions),
        "features" to ModuleBriefCodec.encodeFeatures(m.features),
        "stages" to ModuleBriefCodec.encodeStages(m.stages),
        "briefSource" to (m.briefSource?.let(ModuleBriefCodec::encodeSource) ?: JsonValue.Null),
        // Nama kunci API mengikuti domain (FR-2): nilai ini fingerprint idempotensi, bukan evidence.
        "inputFingerprint" to jsonOf(m.inputFingerprint)
    )
}
