package com.eventverse.app.infrastructure.discovery

import ai.koog.prompt.executor.clients.deepseek.DeepSeekLLMClient
import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import ai.koog.prompt.executor.llms.MultiLLMPromptExecutor
import ai.koog.prompt.llm.LLModel
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent
import com.eventverse.app.infrastructure.EnvLoader
import org.slf4j.LoggerFactory

/**
 * Pemilihan agent discovery (plan §2 A8, D4) — **kill-switch**:
 *
 * | `DISCOVERY_AGENT` | `DEEPSEEK_API_KEY` | Hasil |
 * |---|---|---|
 * | `koog` | terisi | [KoogDiscoveryAgent] (LLM), opsional ber-fallback deterministik |
 * | `koog` | kosong | deterministik + peringatan (server tidak boleh gagal start) |
 * | lain / kosong | — | deterministik |
 *
 * `DISCOVERY_AGENT_MODEL` memilih id model (default `deepseek-flash`). Id yang tidak ada di katalog
 * klien **diteruskan apa adanya dengan peringatan** — katalog Koog tertinggal dari API, dan menolaknya
 * akan mematikan funnel karena versi pustaka, bukan karena konfigurasi.
 * `DISCOVERY_AGENT_FALLBACK=off` mematikan jalur cadangan supaya kegagalan LLM terlihat sebagai
 * kegagalan, bukan draf kata kunci yang menyamar.
 *
 * Executor HTTP Koog panjang umurnya mengikuti proses (dibuat sekali saat start); pemanggil tidak
 * menutupnya per request.
 */
object DiscoveryAgents {

    const val KOOG = "koog"
    const val FALLBACK_OFF = "off"

    /**
     * Id model default. Diverifikasi langsung ke `GET https://api.deepseek.com/models` (2026-09-30) —
     * API memakai `deepseek-flash`, sedangkan katalog klien Koog menyebut `deepseek-v4-flash`. Karena
     * itu resolusi di bawah **memakai definisi klien** (kemampuan: tools + JSON schema) tetapi **id yang
     * diberikan pemanggil**, bukan id katalog.
     */
    const val DEFAULT_MODEL_ID = "deepseek-flash"

    /** Definisi klien untuk id default: kemampuan tools/JSON schema, panjang konteks, batas keluaran. */
    val defaultModel: LLModel by lazy { resolveModel(DEFAULT_MODEL_ID) }

    /**
     * Katalog klien dipakai bila id-nya ada; kalau tidak, definisi default dipakai dengan **id yang
     * diminta** + peringatan. Katalog Koog pasti tertinggal dari API (terbukti: id default di atas),
     * jadi menolak id yang tidak dikenal akan mematikan funnel hanya karena versi katalog.
     */
    fun resolveModel(modelId: String?): LLModel {
        val requested = modelId?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL_ID
        DeepSeekModels.models.firstOrNull { it.id == requested }?.let { return it }
        if (requested != DEFAULT_MODEL_ID) {
            logger.warn("DISCOVERY_AGENT_MODEL='{}' tidak ada di katalog klien DeepSeek; diteruskan apa adanya", requested)
        }
        return DeepSeekModels.DeepSeekV4Flash.copy(id = requested)
    }

    /**
     * Kill-switch agent dibaca lewat [EnvLoader] supaya `.env` ikut berlaku di `:server:run` dan
     * `:server:test` — sama seperti `DB_APP_USER`. Tanpa itu, menyalakan agent LLM di mesin dev
     * menuntut `export` manual yang mudah terlupa dan sulit dibedakan dari agent yang tidak aktif.
     */
    fun fromEnv(): DiscoveryAgent = from(
        configured = EnvLoader.get("DISCOVERY_AGENT").takeIf { it.isNotBlank() },
        apiKey = EnvLoader.get("DEEPSEEK_API_KEY").takeIf { it.isNotBlank() },
        modelId = EnvLoader.get("DISCOVERY_AGENT_MODEL").takeIf { it.isNotBlank() },
        fallbackEnabled = EnvLoader.get("DISCOVERY_AGENT_FALLBACK").lowercase() != FALLBACK_OFF
    )

    fun from(
        configured: String?,
        apiKey: String?,
        modelId: String? = null,
        fallbackEnabled: Boolean = true
    ): DiscoveryAgent {
        if (configured?.lowercase() != KOOG) return DeterministicDiscoveryAgent()
        if (apiKey.isNullOrBlank()) {
            logger.warn("DISCOVERY_AGENT=koog tetapi DEEPSEEK_API_KEY kosong — memakai agent deterministik")
            return DeterministicDiscoveryAgent()
        }

        val model = resolveModel(modelId)
        logger.info("Agent discovery aktif: {} (model {}), fallback deterministik = {}", KOOG, model.id, fallbackEnabled)

        return KoogDiscoveryAgent(
            executor = MultiLLMPromptExecutor(DeepSeekLLMClient(apiKey)),
            model = model,
            fallback = if (fallbackEnabled) DeterministicDiscoveryAgent() else null
        )
    }

    private val logger = LoggerFactory.getLogger(DiscoveryAgents::class.java)
}
