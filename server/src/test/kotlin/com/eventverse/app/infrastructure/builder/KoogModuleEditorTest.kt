package com.eventverse.app.infrastructure.builder

import ai.koog.prompt.executor.clients.deepseek.DeepSeekModels
import com.eventverse.app.domain.builder.ModuleEditRequest
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.EntityProposal
import com.eventverse.app.domain.discovery.proposal.FieldProposal
import com.eventverse.app.domain.discovery.proposal.ProposalEdit
import com.eventverse.app.domain.discovery.proposal.ScreenProposal
import com.eventverse.app.domain.discovery.proposal.ViewProposal
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.infrastructure.discovery.ScriptedPromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Penyunting isian LLM tanpa jaringan dan tanpa biaya (`ScriptedPromptExecutor`). */
class KoogModuleEditorTest {

    private val proposal = ScreenProposal(
        "s1", ModuleId("klinik_poli"), "Antrean Poli", WidgetKind.TABLE, "karena uji",
        EntityProposal("pasien", "Pasien", listOf(FieldProposal("nama", "Nama", FieldType.TEXT, true), FieldProposal("keluhan", "Keluhan", FieldType.TEXT))),
        ViewProposal.Table(listOf("nama", "keluhan"))
    )
    private fun request(message: String = "tambah tanggal kirim", feedback: String? = null, answered: List<Pair<String, String>> = emptyList()) =
        ModuleEditRequest("klinik_poli", "Poli", proposal, message, answered, feedback)

    private fun editor(vararg answers: String) = ScriptedPromptExecutor(answers.toList()).let { it to KoogModuleEditor(it, DeepSeekModels.DeepSeekV4Flash) }

    @Test
    fun `tambah, ganti, dan buang dipetakan ke ProposalEdit, tipe tak dikenal ditolak`() = runBlocking {
        val (_, ed) = editor("""{"reply":"Menambah tanggal kirim.","edits":[
            {"op":"add","field":{"key":"tanggal_kirim","label":"Tanggal Kirim","type":"date","required":true}},
            {"op":"replace","key":"keluhan","field":{"key":"keluhan","label":"Keluhan","type":"ENUM","required":false,"options":["ngilu","bengkak"]}},
            {"op":"remove","key":"nama"}]}""")
        val r = ed.edit(request()).getOrThrow()
        assertEquals("Menambah tanggal kirim.", r.reply())
        assertEquals(3, r.edits.size)
        assertEquals(FieldType.DATE, (r.edits[0] as ProposalEdit.AddField).field.type)
        assertEquals(listOf("ngilu", "bengkak"), (r.edits[1] as ProposalEdit.ReplaceField).field.options)
        assertEquals("nama", (r.edits[2] as ProposalEdit.RemoveField).key)

        val (_, bad) = editor("""{"reply":"x","edits":[{"op":"add","field":{"key":"a","label":"A","type":"MONEY"}}]}""")
        assertTrue(bad.edit(request()).isFailure)
    }

    private fun com.eventverse.app.domain.builder.ModuleEditReply.reply() = text

    @Test
    fun `edits kosong sah (tanpa perubahan), op asing, bukan json, dan lebih dari enam sunting menjadi galat`() = runBlocking {
        assertEquals(emptyList(), editor("""{"reply":"Tidak ada yang perlu diubah.","edits":[]}""").second.edit(request("sudah cukup")).getOrThrow().edits)
        assertTrue(editor("""{"reply":"","edits":[{"op":"rename","key":"a"}]}""").second.edit(request()).isFailure)
        assertTrue(editor("bukan json").second.edit(request()).isFailure)
        val many = (1..7).joinToString(",") { """{"op":"remove","key":"k$it"}""" }
        assertTrue(editor("""{"reply":"","edits":[$many]}""").second.edit(request()).isFailure)
    }

    @Test
    fun `prompt memuat isian saat ini, jawaban follow-up, dan galat percobaan ulang`() = runBlocking {
        val (exec, ed) = editor("""{"reply":"","edits":[]}""")
        ed.edit(request("Perlu nomor rekam medis", feedback = "Field 'nama' sudah ada", answered = listOf("Apa lagi yang dicatat?" to "Perlu nomor rekam medis"))).getOrThrow()
        val sent = exec.lastPromptText()
        assertTrue(sent.contains("key=nama; label=Nama; type=TEXT; required=true"))
        assertTrue(sent.contains("Pertanyaan: Apa lagi yang dicatat?\nJawaban: Perlu nomor rekam medis"))
        assertTrue(sent.contains("DITOLAK validator: Field 'nama' sudah ada"))
    }

    @Test
    fun `saklar lingkungan, tanpa kunci atau bukan koog berarti tidak ada penyunting`() {
        assertNull(BuilderModuleEditors.from(configured = null, apiKey = "k"))
        assertNull(BuilderModuleEditors.from(configured = "koog", apiKey = " "))
        assertTrue(BuilderModuleEditors.from(configured = "KOOG", apiKey = "kunci-uji") is KoogModuleEditor)
    }
}
