package com.eventverse.app.infrastructure.builder

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import com.eventverse.app.domain.builder.ModuleEditReply
import com.eventverse.app.domain.builder.ModuleEditRequest
import com.eventverse.app.domain.builder.ModuleEditor
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ProposalLimits
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.infrastructure.EnvLoader
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import com.eventverse.app.infrastructure.discovery.extractJsonObject
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Penafsir sunting isian per modul (Fase C, model kecil): kalimat pengguna (atau jawabannya atas follow-up) →
 * [ProposalEdit]. Satu panggilan tanpa alat. Keluarannya **usulan**: use case menerapkannya dan validator menolak yang
 * salah; percobaan ulang membawa galat berpath lewat [ModuleEditRequest.feedback].
 */
class KoogModuleEditor(
    private val executor: PromptExecutor,
    private val model: LLModel = DiscoveryAgents.defaultModel,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : ModuleEditor {

    val agentRef: String = "koog/${model.id}/module-edit-v1"

    override suspend fun edit(request: ModuleEditRequest): Result<ModuleEditReply> = runCatching {
        val agent = AIAgent(
            promptExecutor = executor, llmModel = model, strategy = singleRunStrategy(),
            systemPrompt = SYSTEM, temperature = TEMPERATURE, maxIterations = MAX_ITERATIONS
        )
        parse(withTimeout(timeoutMillis) { agent.use { it.run(userMessage(request)) } })
    }

    internal fun userMessage(r: ModuleEditRequest): String = buildString {
        val e = r.proposal.entity
        appendLine("Modul: ${r.moduleName}. Layar: ${r.proposal.title} (${r.proposal.widget.code}).")
        appendLine("Isian saat ini (entitas '${e?.label ?: "-"}'):")
        e?.fields?.forEach { f ->
            appendLine("- key=${f.key}; label=${f.label}; type=${f.type.name}; required=${f.required}" +
                if (f.options.isNotEmpty()) "; options=${f.options.joinToString("|")}" else "")
        } ?: appendLine("(layar ini tidak punya isian)")
        e?.statusField?.let { appendLine("Field status (jangan dibuang/diganti tipenya): $it") }
        if (r.answered.isNotEmpty()) {
            appendLine()
            appendLine("Pengguna menjawab pertanyaan berikut:")
            r.answered.forEach { (q, a) -> appendLine("Pertanyaan: $q\nJawaban: $a") }
        }
        appendLine()
        appendLine("Permintaan pengguna: \"\"\"${r.message}\"\"\"")
        r.feedback?.let {
            appendLine()
            appendLine("Usulan sebelumnya DITOLAK validator: $it")
            appendLine("Perbaiki dan balas JSON lengkap.")
        }
    }

    internal fun parse(raw: String): ModuleEditReply {
        val root = JsonParser.parseObject(extractJsonObject(raw))
        val reply = (root["reply"] as? JsonValue.Str)?.value?.trim().orEmpty()
        val items = (root["edits"] as? JsonValue.Arr)?.items ?: error("Jawaban tidak memuat 'edits'")
        require(items.size <= MAX_EDITS) { "Terlalu banyak sunting sekaligus (${items.size}); maksimum $MAX_EDITS" }
        return ModuleEditReply(reply, items.mapIndexed { i, it -> editOf(it as? JsonValue.Obj ?: error("edits[$i] bukan objek"), i) })
    }

    private fun editOf(o: JsonValue.Obj, i: Int): ProposalEdit = when (val op = (o["op"] as? JsonValue.Str)?.value) {
        "add" -> ProposalEdit.AddField(fieldOf(o["field"] as? JsonValue.Obj ?: error("edits[$i].field wajib")))
        "remove" -> ProposalEdit.RemoveField((o["key"] as? JsonValue.Str)?.value ?: error("edits[$i].key wajib"))
        "replace" -> (o["key"] as? JsonValue.Str)?.value?.let { ProposalEdit.ReplaceField(it, fieldOf(o["field"] as? JsonValue.Obj ?: error("edits[$i].field wajib"))) }
            ?: error("edits[$i].key wajib")
        else -> error("edits[$i].op '$op' tidak dikenal (add, remove, replace)")
    }

    private fun fieldOf(o: JsonValue.Obj): FieldProposal {
        val type = (o["type"] as? JsonValue.Str)?.value?.uppercase()?.let { t -> FieldType.entries.firstOrNull { it.name == t } }
            ?: error("field.type wajib salah satu ${FieldType.entries.joinToString { it.name }}")
        val options = (o["options"] as? JsonValue.Arr)?.items?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty().take(ProposalLimits.OPTIONS)
        return FieldProposal(
            key = (o["key"] as? JsonValue.Str)?.value ?: error("field.key wajib"),
            label = (o["label"] as? JsonValue.Str)?.value ?: error("field.label wajib"),
            type = type, required = (o["required"] as? JsonValue.Bool)?.value ?: false, options = options
        )
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 30_000L
        const val MAX_EDITS = 6
        private const val TEMPERATURE = 0.1
        private const val MAX_ITERATIONS = 4

        val SYSTEM: String = """
            Kamu menyunting isian (field) satu layar modul berdasarkan permintaan pemilik usaha. Kamu hanya boleh menambah, membuang, atau
            mengganti field — tidak menyentuh hal lain.

            ATURAN:
            - key: huruf kecil, angka, garis bawah, diawali huruf, maksimum 41 karakter (mis. tanggal_kirim). label: nama tampil bahasa Indonesia.
            - type salah satu: TEXT, NUMBER, DATE, ENUM, BOOL. ENUM wajib punya options (maksimum ${ProposalLimits.OPTIONS}); tipe lain tanpa options.
            - Maksimum $MAX_EDITS sunting per jawaban. Jangan membuang atau mengganti tipe field status. Jangan menambah field yang sudah ada.
            - Hanya lakukan yang diminta atau yang jelas tersirat dari jawaban pengguna. Jawaban "sudah cukup" atau permintaan di luar isian
              berarti tidak ada sunting: balas edits kosong dan jelaskan singkat di reply.
            - "reply": satu-dua kalimat bahasa awam yang merangkum apa yang kamu ubah (atau mengapa tidak ada perubahan).

            BALASAN: satu objek JSON saja:
            {"reply":"...","edits":[{"op":"add","field":{"key":"...","label":"...","type":"TEXT","required":false,"options":[]}},
            {"op":"remove","key":"..."},{"op":"replace","key":"...","field":{"key":"<sama>","label":"...","type":"ENUM","required":true,"options":["a","b"]}}]}
        """.trimIndent()
    }
}

/** Saklar penyunting modul: `BUILDER_MODULE_EDITOR=koog` + `DEEPSEEK_API_KEY` → LLM (model kecil); selain itu `null`. */
object BuilderModuleEditors {
    fun fromEnv(): ModuleEditor? = from(
        configured = EnvLoader.get("BUILDER_MODULE_EDITOR").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = listOf("BUILDER_MODULE_EDITOR_MODEL", "INTERVIEW_AGENT_MODEL").firstNotNullOfOrNull { EnvLoader.get(it).takeIf(String::isNotBlank) },
        timeoutMillis = EnvLoader.get("BUILDER_MODULE_EDITOR_TIMEOUT_MS").toLongOrNull()?.takeIf { it > 0 } ?: KoogModuleEditor.DEFAULT_TIMEOUT_MILLIS
    )

    fun from(configured: String?, apiKey: String?, modelId: String? = null, timeoutMillis: Long = KoogModuleEditor.DEFAULT_TIMEOUT_MILLIS): ModuleEditor? {
        if (configured?.lowercase() != DiscoveryAgents.KOOG) return null
        if (apiKey.isNullOrBlank()) {
            logger.warn("BUILDER_MODULE_EDITOR=koog tetapi DEEPSEEK_API_KEY kosong — utas modul memakai agent penyusun draf")
            return null
        }
        val model = DiscoveryAgents.resolveModel(modelId)
        logger.info("Penyunting modul Builder aktif: koog (model {}), timeout {} ms", model.id, timeoutMillis)
        return KoogModuleEditor(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)), model, timeoutMillis)
    }

    private val logger = LoggerFactory.getLogger(BuilderModuleEditors::class.java)
}
