package com.eventverse.app.domain.crm.prefill

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-HELP-002 Fase 5b: perintah input lead dibedakan dari pertanyaan cara pakai — ketat, bukan menebak. */
class LeadEntryIntentTest {

    @Test
    fun commandsWithData_areLeadEntry() {
        assertTrue(LeadEntryIntent.matches("catat lead PT Maju, kontak Budi 0812 3456 7890, 500 pcs kaos"))
        assertTrue(LeadEntryIntent.matches("Tolong tambahkan prospek baru: Batik Sekar Solo, butuh polo bordir 2 lusin"))
        assertTrue(LeadEntryIntent.matches("input customer rina@batiksekar.id"))
    }

    @Test
    fun howToQuestions_areNotLeadEntry() {
        assertFalse(LeadEntryIntent.matches("gimana cara bikin lead baru?"))
        assertFalse(LeadEntryIntent.matches("cara tambah prospek"))
        assertFalse(LeadEntryIntent.matches("buat lead baru di mana"))
    }

    @Test
    fun commandWithoutDataOrLeadNoun_isNotLeadEntry() {
        assertFalse(LeadEntryIntent.matches("catat lead"), "tidak ada data untuk diisikan")
        assertFalse(LeadEntryIntent.matches("buat HPP baru untuk kaos polo 500 pcs"), "bukan lead")
        assertFalse(LeadEntryIntent.matches("ada buyer baru chat WA 0812 3456 7890"), "tanpa kata perintah = bisa jadi pertanyaan")
    }
}
