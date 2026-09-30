package com.eventverse.app.infrastructure.discovery

import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DeterministicDiscoveryAgent

/**
 * Pemilihan agent (plan §2 A8, D4): LLM Koog nanti di belakang interface [DiscoveryAgent] yang sama.
 * Kill-switch env sekarang: nilai apa pun selain `koog` — atau `koog` saat dependensinya belum dipasang —
 * jatuh ke jalur deterministik. Server tidak boleh gagal start karena satu kunci API belum diisi.
 */
object DiscoveryAgents {

    fun fromEnv(): DiscoveryAgent = from(System.getenv("DISCOVERY_AGENT"))

    fun from(configured: String?): DiscoveryAgent = when (configured?.lowercase()) {
        // "koog" -> KoogDiscoveryAgent(...) — A8, di branch terpisah (klock/kotlinx-datetime 0.6.2).
        else -> DeterministicDiscoveryAgent()
    }
}
