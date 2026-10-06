package com.eventverse.app.infrastructure.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Bukti plan §2 A8** untuk kill-switch: tanpa konfigurasi (atau tanpa kunci API) funnel tetap hidup
 * lewat jalur deterministik, sedangkan `DISCOVERY_AGENT=koog` memilih agent LLM. Id model tak dikenal
 * tidak pernah diteruskan ke API.
 */
class DiscoveryAgentsTest {

    @Test
    fun `tanpa konfigurasi atau tanpa kunci dipakai jalur deterministik`() {
        assertEquals("deterministic/keyword-v1", DiscoveryAgents.from(null, "sk-uji").agentRef)
        assertEquals("deterministic/keyword-v1", DiscoveryAgents.from("deterministik", "sk-uji").agentRef)
        assertEquals("deterministic/keyword-v1", DiscoveryAgents.from("off", "sk-uji").agentRef)
        assertEquals("deterministic/keyword-v1", DiscoveryAgents.from("koog", null).agentRef)
        assertEquals("deterministic/keyword-v1", DiscoveryAgents.from("koog", "   ").agentRef)
    }

    @Test
    fun `koog dengan kunci dan model dikenal memilih agent LLM`() {
        val agent = DiscoveryAgents.from(configured = "koog", apiKey = "sk-uji", modelId = "deepseek-v4-pro")

        assertTrue(agent is KoogDiscoveryAgent)
        assertEquals("koog/deepseek-v4-pro/draft-v2", agent.agentRef)
    }

    @Test
    fun `model id tak dikenal diteruskan apa adanya dengan definisi default`() {
        val agent = DiscoveryAgents.from(configured = "koog", apiKey = "sk-uji", modelId = "model-karangan")

        assertEquals("koog/model-karangan/draft-v2", agent.agentRef)
    }

    @Test
    fun `katalog klien dipakai apa adanya bila id cocok`() {
        val known = DiscoveryAgents.resolveModel("deepseek-v4-pro")

        assertEquals("deepseek-v4-pro", known.id)
        assertEquals(DiscoveryAgents.resolveModel(null).provider, known.provider)
    }
}
