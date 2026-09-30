package com.eventverse.app.domain.crm.prefill

import com.eventverse.app.domain.crm.prefill.usecases.ExtractLeadDraftUseCase
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.customfield.SelectOptionId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * TRD-HELP-002 FR-1..FR-6 dengan skema tenant **bordir** (field kustom non-garment). Ekstraktor dipalsukan:
 * yang diuji adalah urutan samarkan → ekstrak → kembalikan → validasi, bukan mutu LLM.
 */
class ExtractLeadDraftUseCaseTest {

    private class Scripted(var reply: Result<Map<String, String>>) : LeadDraftExtractor {
        override val agentRef = "scripted"
        var seenText: String? = null
        var seenFields: List<DraftFieldSpec> = emptyList()
        override suspend fun extract(maskedText: String, fields: List<DraftFieldSpec>): Result<Map<String, String>> {
            seenText = maskedText; seenFields = fields
            return reply
        }
    }

    private val chat = "Kak saya Rina dari Batik Sekar, mau bordir komputer logo 3 warna di 2 lusin polo. WA 0812 3456 7890, rina@sekar.id"

    @Test
    fun extractorSeesMaskedTextOnly_andValuesAreRestored() = runTest {
        val ex = Scripted(Result.success(mapOf(
            LeadDraftFields.BRAND_NAME to "Batik Sekar", LeadDraftFields.CONTACT_PERSON to "Rina",
            LeadDraftFields.WHATSAPP to "{TELP_1}", LeadDraftFields.EMAIL to "{EMAIL_1}",
            LeadDraftFields.ESTIMATED_PCS to "24", LeadDraftFields.PRODUCT_CATEGORY to "Polo bordir",
            BordirLeadFields.jenisBordir.id.value to "bordir komputer", BordirLeadFields.jumlahWarna.id.value to "3",
        )))
        val draft = ExtractLeadDraftUseCase(ex)(chat, BordirLeadFields.all).getOrThrow()

        val sent = requireNotNull(ex.seenText)
        assertFalse("3456" in sent || "sekar.id" in sent, "nomor/email asli tidak boleh sampai ke ekstraktor: $sent")
        assertEquals("6281234567890", draft.whatsappNumber?.value)
        assertEquals("rina@sekar.id", draft.email)
        assertEquals(24, draft.estimatedPcs)
        assertEquals(CustomAttributes.selectCell(SelectOptionId("opt_komputer")), draft.customValues[BordirLeadFields.jenisBordir.id])
        assertEquals(CustomAttributes.numberCell("3"), draft.customValues[BordirLeadFields.jumlahWarna.id])
        assertEquals("scripted", draft.agentRef)
        assertFalse(draft.partial)
    }

    @Test
    fun fieldSpecs_comeFromTenantSchema_supportedAndActiveOnly() = runTest {
        val ex = Scripted(Result.success(emptyMap()))
        ExtractLeadDraftUseCase(ex)(chat, BordirLeadFields.all).getOrThrow()
        val keys = ex.seenFields.map { it.key }
        assertTrue(BordirLeadFields.jenisBordir.id.value in keys && BordirLeadFields.catatan.id.value in keys)
        assertFalse(BordirLeadFields.tanggal.id.value in keys, "DATE ditunda di pilot (K4)")
        assertFalse(BordirLeadFields.lama.id.value in keys, "kolom arsip tidak ditawarkan")
        assertEquals(listOf("Bordir Komputer", "Bordir Manual"), ex.seenFields.first { it.key == BordirLeadFields.jenisBordir.id.value }.options)
    }

    @Test
    fun invalidValues_areDropped_withIssues_neverForced() = runTest {
        val ex = Scripted(Result.success(mapOf(
            LeadDraftFields.WHATSAPP to "12345", LeadDraftFields.EMAIL to "bukan-email",
            LeadDraftFields.ESTIMATED_PCS to "banyak", BordirLeadFields.jenisBordir.id.value to "Bordir Laser",
            BordirLeadFields.jumlahWarna.id.value to "2.5", "cf-tidak-ada" to "x", BordirLeadFields.tanggal.id.value to "2026-10-01",
        )))
        val draft = ExtractLeadDraftUseCase(ex)(chat, BordirLeadFields.all).getOrThrow()
        assertNull(draft.whatsappNumber); assertNull(draft.email); assertNull(draft.estimatedPcs)
        assertTrue(draft.customValues.isEmpty(), "opsi di luar daftar, pecahan di kolom bulat, kolom asing & DATE: semua dibuang")
        assertEquals(setOf(LeadDraftFields.WHATSAPP, LeadDraftFields.EMAIL, LeadDraftFields.ESTIMATED_PCS,
            BordirLeadFields.jenisBordir.id.value, BordirLeadFields.jumlahWarna.id.value), draft.issues.map { it.field }.toSet())
    }

    @Test
    fun inventedPlaceholder_isDiscarded() = runTest {
        val ex = Scripted(Result.success(mapOf(LeadDraftFields.WHATSAPP to "{TELP_9}")))
        val draft = ExtractLeadDraftUseCase(ex)(chat, emptyList()).getOrThrow()
        assertNull(draft.whatsappNumber)
        assertTrue(draft.issues.isEmpty())
    }

    @Test
    fun extractorFailure_fallsBackToDeterministic_partial() = runTest {
        val draft = ExtractLeadDraftUseCase(Scripted(Result.failure(IllegalStateException("LLM mati"))))(chat, BordirLeadFields.all).getOrThrow()
        assertTrue(draft.partial)
        assertEquals("deterministic/lead-draft-v1", draft.agentRef)
        assertEquals("6281234567890", draft.whatsappNumber?.value)
        assertEquals("rina@sekar.id", draft.email)
        assertEquals(24, draft.estimatedPcs, "2 lusin = 24")
        assertNull(draft.brandName, "deterministik tidak menebak nama")
    }

    @Test
    fun emptyOrTooLongText_isRejected() = runTest {
        val uc = ExtractLeadDraftUseCase(Scripted(Result.success(emptyMap())))
        assertTrue(uc("  ", emptyList()).isFailure)
        assertTrue(uc("a".repeat(4001), emptyList()).isFailure)
    }
}
