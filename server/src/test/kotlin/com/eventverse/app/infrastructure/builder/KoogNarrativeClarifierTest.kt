package com.eventverse.app.infrastructure.builder

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.infrastructure.discovery.ScriptedPromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Penanya klarifikasi LLM tanpa jaringan dan tanpa biaya (`ScriptedPromptExecutor`). */
class KoogNarrativeClarifierTest {

    private val model = DeepSeekModels.DeepSeekV4Flash
    private fun clarifier(vararg answers: String) =
        ScriptedPromptExecutor(answers.toList()).let { it to KoogNarrativeClarifier(it, model) }

    @Test
    fun `bertanya dibatasi tiga, dinomori ulang server, dan pertanyaan kosong dibuang`() = runBlocking {
        val (_, c) = clarifier("""{"questions":[{"id":"x","question":"Apa jenis usahanya?"},{"id":"x","question":"  "},
            {"question":"Siapa pelanggannya?"},{"id":"z","question":"Bagaimana alurnya?"},{"id":"w","question":"Pertanyaan keempat"}]}""")
        val out = c.clarify("Kami usaha kecil", emptyList())
        assertEquals(listOf("c1", "c2", "c3"), out.map { it.id })
        assertEquals(listOf("Apa jenis usahanya?", "Siapa pelanggannya?", "Bagaimana alurnya?"), out.map { it.question })
    }

    @Test
    fun `daftar kosong berarti cukup jelas dan pembungkus kode json diterima`() = runBlocking {
        val (_, c) = clarifier("```json\n{\"questions\":[]}\n```")
        assertEquals(emptyList(), c.clarify("Kami konveksi, potong lalu jahit lalu kirim", emptyList()))
    }

    @Test
    fun `keluaran bukan json atau tanpa questions dilempar sebagai galat agar pemanggil tidak bertanya`() = runBlocking<Unit> {
        assertFailsWith<Exception> { clarifier("bukan json").second.clarify("x", emptyList()) }
        assertFailsWith<Exception> { clarifier("""{"hasil":[]}""").second.clarify("x", emptyList()) }
    }

    @Test
    fun `prompt memuat cerita dan modul draf yang sudah ada sebagai konteks revisi`() = runBlocking {
        val (exec, c) = clarifier("""{"questions":[]}""")
        c.clarify("Tambah modul pengiriman", listOf("order_ingestion", "quality_control"))
        val sent = exec.lastPromptText()
        assertTrue(sent.contains("Tambah modul pengiriman"))
        assertTrue(sent.contains("order_ingestion, quality_control"))
        assertTrue(sent.contains("balas daftar kosong"), "aturan 'cukup jelas = kosong' ada di prompt sistem")
    }

    @Test
    fun `saklar lingkungan, tanpa kunci atau bukan koog berarti tidak ada penanya`() {
        assertNull(BuilderClarifiers.from(configured = null, apiKey = "k"))
        assertNull(BuilderClarifiers.from(configured = "off", apiKey = "k"))
        assertNull(BuilderClarifiers.from(configured = "koog", apiKey = " "))
        assertTrue(BuilderClarifiers.from(configured = "KOOG", apiKey = "kunci-uji") is KoogNarrativeClarifier)
    }

    @Test
    fun `saklar mengikuti DISCOVERY_AGENT, dan saklar sendiri hanya pengecualian eksplisit`() {
        assertEquals("koog", BuilderClarifiers.resolveSwitch(own = null, inherited = "koog"))
        assertEquals("koog", BuilderClarifiers.resolveSwitch(own = "  ", inherited = "koog"), "kosong = mewarisi")
        assertEquals("off", BuilderClarifiers.resolveSwitch(own = "off", inherited = "koog"), "pengecualian eksplisit menang")
        assertNull(BuilderClarifiers.resolveSwitch(own = null, inherited = null))
        assertNull(BuilderClarifiers.from(configured = BuilderClarifiers.resolveSwitch("off", "koog"), apiKey = "k"), "off mematikan fitur ini saja")
    }
}
