package com.eventverse.app.infrastructure.crm.prefill

import com.eventverse.app.domain.crm.prefill.DeterministicLeadDraftExtractor
import com.eventverse.app.domain.crm.prefill.DraftFieldKind
import com.eventverse.app.domain.crm.prefill.DraftFieldSpec
import com.eventverse.app.domain.crm.prefill.LeadDraftFields
import com.eventverse.app.domain.crm.prefill.usecases.ExtractLeadDraftUseCase
import com.eventverse.app.infrastructure.discovery.ScriptedPromptExecutor
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-HELP-002 lewat jalur Koog produksi, jawaban LLM dari skrip — tanpa jaringan. */
class KoogLeadDraftExtractorTest {

    private val chat = "Saya Rina, Batik Sekar. Butuh 2 lusin polo bordir. WA 0812 3456 7890"

    @Test
    fun promptCarriesMaskedTextAndTenantFields_neverTheRealNumber() = runBlocking {
        val exec = ScriptedPromptExecutor(listOf("""{"brandName":"Batik Sekar","contactPerson":"Rina","whatsappNumber":"{TELP_1}","estimatedPcs":24,"cf-jenis":"Bordir Komputer"}"""))
        val draft = ExtractLeadDraftUseCase(KoogLeadDraftExtractor(exec))(chat, emptyList()).getOrThrow()

        val prompt = exec.lastPromptText()
        assertFalse("3456" in prompt, "nomor asli tidak boleh ada di prompt")
        assertTrue("{TELP_1}" in prompt && "kunci: brandName" in prompt)
        assertEquals("6281234567890", draft.whatsappNumber?.value)
        assertEquals(24, draft.estimatedPcs)
        assertTrue(draft.agentRef.startsWith("koog/"))
    }

    @Test
    fun keysOutsideTheOfferedFields_areIgnored() = runBlocking {
        val exec = ScriptedPromptExecutor(listOf("""```json
            {"brandName":"X","stage":"QUALIFIED","ownerEmployeeId":"emp-1"}
            ```"""))
        val fields = listOf(DraftFieldSpec(LeadDraftFields.BRAND_NAME, "Brand", DraftFieldKind.TEXT))
        val raw = KoogLeadDraftExtractor(exec).extract("teks", fields).getOrThrow()
        assertEquals(mapOf(LeadDraftFields.BRAND_NAME to "X"), raw, "AI tidak bisa memilih tahap atau pemilik lead (FR-4)")
    }

    @Test
    fun nonJsonAnswer_fallsBackToDeterministicPartialDraft() = runBlocking {
        val exec = ScriptedPromptExecutor(listOf("Maaf, saya tidak bisa membantu."))
        val draft = ExtractLeadDraftUseCase(KoogLeadDraftExtractor(exec))(chat, emptyList()).getOrThrow()
        assertTrue(draft.partial)
        assertEquals(24, draft.estimatedPcs)
    }

    @Test
    fun envSelection_defaultsToDeterministic() {
        assertTrue(LeadDraftAgents.from(null, "k") is DeterministicLeadDraftExtractor)
        assertTrue(LeadDraftAgents.from("koog", null) is DeterministicLeadDraftExtractor)
        assertTrue(LeadDraftAgents.from("koog", "k") is KoogLeadDraftExtractor)
    }
}
