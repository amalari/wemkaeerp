package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.brief.BriefContext
import com.eventverse.app.domain.discovery.brief.BriefDecision
import com.eventverse.app.domain.discovery.brief.BriefOpenQuestion
import com.eventverse.app.domain.discovery.brief.BriefQa
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.RequirementsBrief
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Konteks chat untuk brief developer: cerita, tanya-jawab, keputusan terapan, dan yang belum jelas. */
class BriefContextBuilderTest {

    private val conv = BuilderConversationId("c")
    private val tenant = TenantId("ten-uji")
    private var n = 0

    private fun user(text: String, module: String? = null) =
        ChatMessage(ChatMessageId("m${n++}"), conv, tenant, ChatRole.USER, text, moduleId = module)

    private fun agent(text: String, module: String? = null, applied: Boolean = false, summary: List<String> = emptyList()) =
        ChatMessage(ChatMessageId("m${n++}"), conv, tenant, ChatRole.AGENT, text, moduleId = module,
            appliedDraftId = if (applied) "d1" else null, proposedSummary = summary)

    private fun question(module: String?, vararg qs: Clarification) =
        ChatMessage(ChatMessageId("m${n++}"), conv, tenant, ChatRole.AGENT, "tanya", moduleId = module, kind = ChatMessageKind.QUESTION, questions = qs.toList())

    private val history = listOf(
        user("Kami klinik gigi, pasien mendaftar lalu antre per poli."),
        question(null, Clarification("c1", "Satu antrean atau per poli?", "Satu antrean lalu dibagi")),
        user("Satu antrean lalu dibagi"),                                                  // hanya berfungsi sebagai jawaban
        agent("usulan", applied = true, summary = listOf("Modul aktif baru: klinik_antrean")),
        agent("usulan dibuang", applied = false, summary = listOf("Modul aktif baru: klinik_hantu")),
        question("klinik_poli", Clarification("gap:few_fields:s1", "Apa lagi yang dicatat?"), Clarification("gap:no_required:s1", "Mana yang wajib?", "nama")),
        user("Tambah nomor rekam medis", module = "klinik_poli"),
        agent("patch modul", module = "klinik_poli", applied = true, summary = listOf("Tambah isian: Nomor Rekam Medis (text)")),
        question("klinik_kasir", Clarification("gap:generic:s9", "Masih standar, ada yang disesuaikan?"))
    )

    @Test
    fun `tanpa riwayat tidak ada konteks, brief tetap seperti sebelumnya`() {
        assertNull(briefContextOf(emptyList(), setOf("klinik_poli")))
    }

    @Test
    fun `cerita tanpa balasan jawaban, tanya-jawab hanya yang terjawab, keputusan hanya yang diterapkan, belum jelas hanya yang menunggu`() {
        val c = requireNotNull(briefContextOf(history, setOf("klinik_poli", "klinik_kasir")))
        assertEquals("Kami klinik gigi, pasien mendaftar lalu antre per poli.", c.narrative, "balasan yang jadi jawaban dan pesan modul tidak ikut cerita")
        assertEquals(
            listOf(BriefQa(null, "Satu antrean atau per poli?", "Satu antrean lalu dibagi"), BriefQa("klinik_poli", "Mana yang wajib?", "nama")),
            c.answered
        )
        assertEquals(
            listOf(
                BriefDecision(null, null, listOf("Modul aktif baru: klinik_antrean")),
                BriefDecision("klinik_poli", null, listOf("Tambah isian: Nomor Rekam Medis (text)"))
            ),
            c.decisions, "usulan yang tidak diterapkan tidak boleh tampil sebagai keputusan"
        )
        assertEquals(
            listOf(BriefOpenQuestion("klinik_poli", "Apa lagi yang dicatat?"), BriefOpenQuestion("klinik_kasir", "Masih standar, ada yang disesuaikan?")),
            c.open
        )
    }

    @Test
    fun `modul yang tidak masuk brief dikecualikan, pesan tingkat alur tetap ikut`() {
        val c = requireNotNull(briefContextOf(history, setOf("klinik_kasir")))
        assertTrue(c.answered.none { it.moduleId == "klinik_poli" })
        assertTrue(c.decisions.none { it.moduleId == "klinik_poli" })
        assertEquals(listOf("klinik_kasir"), c.open.map { it.moduleId })
        assertEquals(1, c.decisions.size, "keputusan tingkat alur (moduleId null) tetap ada")
    }

    @Test
    fun `cerita panjang dipotong dengan tanda`() {
        val c = requireNotNull(briefContextOf(listOf(user("a".repeat(100))), emptySet(), narrativeLimit = 40))
        assertEquals("a".repeat(40) + "...", c.narrative)
    }

    // ---- renderer ----

    private val bare = RequirementsBrief("klinik", emptyList(), emptyList(), emptyList())

    @Test
    fun `tanpa konteks Markdown tidak punya bagian konteks, dengan konteks muncul bagian dan label modul`() {
        val without = BriefRenderer.markdown(bare)
        assertFalse(without.contains("Konteks & keputusan") || without.contains("Belum jelas"), "brief lama tidak berubah")

        val ctx = BriefContext(
            narrative = "Kami klinik gigi.\nPasien antre.",
            answered = listOf(BriefQa("klinik_poli", "Mana yang wajib?", "nama")),
            decisions = listOf(BriefDecision(null, "2026-10-08T01:00:00Z", listOf("Modul aktif baru: klinik_antrean", "Modul aktif baru: klinik_stok"))),
            open = listOf(BriefOpenQuestion("klinik_kasir", "Apa lagi?"))
        )
        val md = BriefRenderer.markdown(bare.copy(context = ctx))
        assertTrue(md.contains("## Konteks & keputusan"))
        assertTrue(md.contains("> Kami klinik gigi.\n> Pasien antre."), "cerita sebagai kutipan, per baris")
        assertTrue(md.contains("- [klinik_poli] Mana yang wajib? — nama"))
        assertTrue(md.contains("- 2026-10-08T01:00:00Z — [Seluruh alur] Modul aktif baru: klinik_antrean; Modul aktif baru: klinik_stok"))
        assertTrue(md.contains("## Belum jelas\n- [klinik_kasir] Apa lagi?"))
        assertEquals(md, BriefRenderer.markdown(bare.copy(context = ctx)), "deterministik: masukan sama, keluaran sama")
    }

    @Test
    fun `tidak ada pertanyaan tertunda dinyatakan eksplisit`() {
        val md = BriefRenderer.markdown(bare.copy(context = BriefContext("cerita", emptyList(), emptyList(), emptyList())))
        assertTrue(md.contains("## Belum jelas\n_Tidak ada pertanyaan yang tertunda._"))
    }
}
