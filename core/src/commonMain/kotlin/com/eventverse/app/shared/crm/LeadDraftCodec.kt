package com.eventverse.app.shared.crm

import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.domain.crm.prefill.DraftIssue
import com.eventverse.app.domain.crm.prefill.LeadDraft
import com.eventverse.app.domain.customfield.CustomFieldId
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/** Format kabel `POST /api/tenant/crm/leads/draft` dan `/api/tenant/crm/ai-settings` (TRD-HELP-002 §4). */
object LeadDraftCodec {

    fun encodeDraft(d: LeadDraft): JsonValue.Obj = jsonObjectOf(
        "draft" to jsonObjectOf(
            "brandName" to jsonOf(d.brandName),
            "contactPerson" to jsonOf(d.contactPerson),
            "whatsappNumber" to jsonOf(d.whatsappNumber?.value),
            "email" to jsonOf(d.email),
            "productCategory" to jsonOf(d.productCategory),
            "estimatedPcs" to (d.estimatedPcs?.let { jsonOf(it) } ?: JsonValue.Null),
            "customValues" to JsonValue.Obj(d.customValues.mapKeys { it.key.value }),
        ),
        "issues" to jsonArrayOf(d.issues.map { jsonObjectOf("field" to jsonOf(it.field), "message" to jsonOf(it.message)) }),
        "agentRef" to jsonOf(d.agentRef),
        "partial" to jsonOf(d.partial),
    )

    /** Nilai yang tidak sah dilewati (field kosong), tidak ditebak — server sudah memvalidasinya. */
    fun decodeDraft(root: JsonValue.Obj): LeadDraft {
        val d = root.obj("draft") ?: JsonValue.Obj(emptyMap())
        return LeadDraft(
            brandName = d.string("brandName"),
            contactPerson = d.string("contactPerson"),
            whatsappNumber = d.string("whatsappNumber")?.let { runCatching { WhatsappNumber(it) }.getOrNull() },
            email = d.string("email"),
            productCategory = d.string("productCategory"),
            estimatedPcs = d.int("estimatedPcs"),
            customValues = d.obj("customValues")?.entries.orEmpty()
                .mapNotNull { (k, v) -> (v as? JsonValue.Obj)?.let { cell -> runCatching { CustomFieldId(k) }.getOrNull()?.let { it to cell } } }
                .toMap(),
            issues = root.objectArray("issues").map { DraftIssue(it.string("field").orEmpty(), it.string("message").orEmpty()) },
            agentRef = root.string("agentRef").orEmpty(),
            partial = root.boolean("partial") ?: false,
        )
    }

    fun encodeText(text: String): String = jsonObjectOf("text" to jsonOf(text)).encode()

    data class AiSettings(val leadDraftEnabled: Boolean, val canManage: Boolean)

    fun encodeSettings(s: AiSettings): String =
        jsonObjectOf("leadDraftEnabled" to jsonOf(s.leadDraftEnabled), "canManage" to jsonOf(s.canManage)).encode()

    fun decodeSettings(root: JsonValue.Obj) = AiSettings(root.boolean("leadDraftEnabled") ?: false, root.boolean("canManage") ?: false)
}
