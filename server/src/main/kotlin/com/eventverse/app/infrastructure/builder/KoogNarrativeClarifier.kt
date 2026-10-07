package com.eventverse.app.infrastructure.builder

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.singleRunStrategy
import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.executor.model.PromptExecutor
import ai.koog.prompt.llm.LLModel
import ai.koog.utils.io.use
import com.eventverse.app.domain.builder.NarrativeClarifier
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.InterviewLimits
import com.eventverse.app.infrastructure.EnvLoader
import com.eventverse.app.infrastructure.discovery.DiscoveryAgents
import com.eventverse.app.infrastructure.discovery.extractJsonObject
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory

/**
 * Penanya klarifikasi berbasis LLM (Fase B): satu panggilan, tanpa alat. Keluarannya **usulan pertanyaan**; kegagalan
 * (timeout, bukan JSON, pertanyaan tak sah) dilempar ke pemanggil yang menafsirkannya sebagai "tidak bertanya".
 * Aturan keras ditegakkan di kode, bukan dipercayakan ke model: maksimal [InterviewLimits.CLARIFICATIONS] pertanyaan,
 * tiap pertanyaan terisi dan ≤ [InterviewLimits.TEXT] karakter, id unik (dinomori ulang `c1..cN` oleh server).
 */
class KoogNarrativeClarifier(
    private val executor: PromptExecutor,
    private val model: LLModel = DiscoveryAgents.defaultModel,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS
) : NarrativeClarifier {

    val agentRef: String = "koog/${model.id}/builder-clarify-v1"

    override suspend fun clarify(narrative: String, existingModules: List<String>): List<Clarification> {
        val agent = AIAgent(
            promptExecutor = executor,
            llmModel = model,
            strategy = singleRunStrategy(),
            systemPrompt = SYSTEM,
            temperature = TEMPERATURE,
            maxIterations = MAX_ITERATIONS
        )
        val answer = withTimeout(timeoutMillis) { agent.use { it.run(userMessage(narrative, existingModules)) } }
        return parse(answer)
    }

    internal fun parse(raw: String): List<Clarification> {
        val root = JsonParser.parseObject(extractJsonObject(raw))
        val items = (root["questions"] as? JsonValue.Arr)?.items ?: error("Jawaban penanya tidak memuat 'questions'")
        return items.mapIndexedNotNull { i, item ->
            val text = ((item as? JsonValue.Obj)?.get("question") as? JsonValue.Str)?.value?.trim()
            text?.takeIf { it.isNotEmpty() }?.take(InterviewLimits.TEXT)
        }.take(InterviewLimits.CLARIFICATIONS).mapIndexed { i, q -> Clarification("c${i + 1}", q) }
    }

    internal fun userMessage(narrative: String, existingModules: List<String>): String = buildString {
        appendLine("Cerita pemilik usaha (urut waktu):")
        appendLine("\"\"\"")
        appendLine(narrative.trim())
        appendLine("\"\"\"")
        if (existingModules.isNotEmpty()) {
            appendLine()
            appendLine("Draf sistem yang sudah ada memakai modul: ${existingModules.joinToString(", ")}.")
            appendLine("Pesan terakhir kemungkinan revisi atas draf itu.")
        }
        appendLine()
        append("Tentukan apakah ada hal pokok yang tak bisa disimpulkan. Balas JSON sesuai aturan.")
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS = 60_000L
        private const val TEMPERATURE = 0.2
        private const val MAX_ITERATIONS = 4

        val SYSTEM: String = """
            Kamu konsultan yang membaca cerita pemilik usaha untuk merancang sistem operasionalnya (alur kerja dan modul).
            Tugasmu HANYA memutuskan: apakah ada hal pokok yang tidak bisa disimpulkan dari cerita sehingga perlu ditanyakan dulu.

            ATURAN:
            - Tanya hanya bila jawabannya akan mengubah alur atau modul yang dirancang (mis. jenis usaha tidak jelas, urutan kerja
              inti tidak terbaca, siapa pelanggan/pemasok tidak jelas).
            - Maksimal ${InterviewLimits.CLARIFICATIONS} pertanyaan, masing-masing satu kalimat, bahasa awam, tanpa istilah teknis.
            - Jangan menanyakan hal yang sudah dinyatakan di cerita, dan jangan menanyakan preferensi tampilan.
            - Cerita sudah cukup (jenis usaha dan alur kerja intinya terbaca)? Balas daftar kosong. Pesan berupa perintah revisi yang
              jelas atas draf yang sudah ada ("tambah modul pengiriman") juga cukup: balas daftar kosong.
            - Cerita yang sudah memuat "Pertanyaan/Jawaban" berarti kamu sudah bertanya: balas daftar kosong.

            BALASAN: satu objek JSON saja, tanpa teks lain: {"questions":[{"id":"c1","question":"..."}]} atau {"questions":[]}.
        """.trimIndent()
    }
}

/**
 * Saklar penanya klarifikasi (pola `HelpAgents`): `BUILDER_CLARIFIER=koog` + `DEEPSEEK_API_KEY` → LLM; selain itu `null`
 * (chat langsung menyusun draf). Model: `BUILDER_CLARIFIER_MODEL`, lalu `DISCOVERY_AGENT_MODEL_PLAN`, lalu `DISCOVERY_AGENT_MODEL`.
 */
object BuilderClarifiers {
    fun fromEnv(): NarrativeClarifier? = from(
        configured = EnvLoader.get("BUILDER_CLARIFIER").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = listOf("BUILDER_CLARIFIER_MODEL", "DISCOVERY_AGENT_MODEL_PLAN", "DISCOVERY_AGENT_MODEL")
            .firstNotNullOfOrNull { EnvLoader.get(it).takeIf(String::isNotBlank) },
        timeoutMillis = EnvLoader.get("BUILDER_CLARIFIER_TIMEOUT_MS").toLongOrNull()?.takeIf { it > 0 }
            ?: KoogNarrativeClarifier.DEFAULT_TIMEOUT_MILLIS
    )

    fun from(configured: String?, apiKey: String?, modelId: String? = null, timeoutMillis: Long = KoogNarrativeClarifier.DEFAULT_TIMEOUT_MILLIS): NarrativeClarifier? {
        if (configured?.lowercase() != DiscoveryAgents.KOOG) return null
        if (apiKey.isNullOrBlank()) {
            logger.warn("BUILDER_CLARIFIER=koog tetapi DEEPSEEK_API_KEY kosong — chat langsung menyusun draf")
            return null
        }
        val model = DiscoveryAgents.resolveModel(modelId)
        logger.info("Penanya klarifikasi Builder aktif: koog (model {}), timeout {} ms", model.id, timeoutMillis)
        return KoogNarrativeClarifier(MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)), model, timeoutMillis)
    }

    private val logger = LoggerFactory.getLogger(BuilderClarifiers::class.java)
}
