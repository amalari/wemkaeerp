package com.eventverse.app.domain.crm.prefill

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** TRD-HELP-002 K1: nomor & email tidak pernah ada di teks yang dikirim ke LLM, dan kembali utuh setelahnya. */
class PiiMaskerTest {

    private val text = "Halo kak, saya Budi dari PT Maju Jaya. WA 0812-3456-7890 atau +62 813 1111 2222, email budi.s@majujaya.co.id. Nomor 0812-3456-7890 lagi ya"

    @Test
    fun phonesAndEmails_areReplaced_andRestored() {
        val masked = PiiMasker.mask(text)
        assertFalse("3456" in masked.text || "1111" in masked.text || "majujaya" in masked.text, masked.text)
        assertTrue("Budi" in masked.text && "PT Maju Jaya" in masked.text, "nama sengaja tidak disamarkan (keputusan K1)")
        assertEquals(text, masked.unmask(masked.text))
    }

    @Test
    fun sameNumberTwice_getsOnePlaceholder() {
        val masked = PiiMasker.mask(text)
        assertEquals(2, Regex("""\{TELP_1}""").findAll(masked.text).count())
        assertEquals(setOf("{EMAIL_1}", "{TELP_1}", "{TELP_2}"), masked.placeholders)
    }

    @Test
    fun ordinaryNumbers_areNotMistakenForPhones() {
        val masked = PiiMasker.mask("Order 500 pcs, harga 85000, SPK 2026093012")
        assertTrue(masked.placeholders.isEmpty(), masked.text)
    }
}
