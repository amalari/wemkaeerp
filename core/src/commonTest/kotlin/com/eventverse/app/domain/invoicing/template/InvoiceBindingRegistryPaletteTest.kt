package com.eventverse.app.domain.invoicing.template

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Kontrak palet Perpustakaan Elemen.
 *
 * Keputusan produk saat ini: hanya modul CRM yang ditawarkan sebagai isian bebas. Test ini
 * mengunci keputusan itu DAN sekaligus menjaga bahwa token modul lain tetap hidup di registry —
 * kalau seseorang menghapus `issuer.*` dari [InvoiceBindingRegistry.DOCUMENT], PDF template seed
 * akan rusak diam-diam, dan test ketiga di bawah yang seharusnya berbunyi.
 */
class InvoiceBindingRegistryPaletteTest {

    @Test
    fun standaloneModules_whenPaletteRestrictedToCrm_offersOnlyCrmSales() {
        assertEquals(
            listOf(BindingModuleSource.CRM_SALES),
            InvoiceBindingRegistry.standaloneModules()
        )
    }

    @Test
    fun standaloneTokens_whenPaletteRestrictedToCrm_containsOnlyCrmTokens() {
        val tokens = InvoiceBindingRegistry.standaloneTokens

        assertTrue(tokens.isNotEmpty(), "Palet CRM tidak boleh kosong")
        assertTrue(
            tokens.all { it.moduleSource == BindingModuleSource.CRM_SALES },
            "Semua token palet wajib berasal dari modul CRM"
        )
    }

    @Test
    fun documentRegistry_whenPaletteRestricted_stillResolvesIssuerAndInvoicingTokens() {
        // Registry lengkap tidak boleh ikut terpangkas: PDF server & template seed memakai
        // token issuer.* dan invoice.* walau keduanya tidak ditawarkan di palet lagi.
        assertNotNull(InvoiceBindingRegistry.descriptorFor("issuer.companyName"))
        assertNotNull(InvoiceBindingRegistry.descriptorFor("invoice.total"))
        assertNotNull(InvoiceBindingRegistry.descriptorFor("billTo.name"))
    }
}
