package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Paritas judul menu garment (TRD-I18N-003 Track A). Tabel `id` di bawah = `AppNavScreen.title` layar utama
 * modul pada saat seam dibekukan; mengubah salah satunya adalah perubahan judul menu `id` (dilarang).
 */
class GarmentMenuLabelParityTest {

    private val garment = GarmentDomainPack.pack

    private val navTitles = mapOf(
        GarmentModules.ORG_CHART to "Bagan Organisasi",
        GarmentModules.DYNAMIC_RBAC to "Hak Akses (RBAC)",
        GarmentModules.FACTORY_FLOW to "Alur Pabrik (Pipeline)",
        GarmentModules.CRM_SALES to "Penjualan & Pelanggan",
        GarmentModules.SAMPLING_ORDER to "Order Sampling",
        GarmentModules.INVENTORY to "Gudang & Bahan Baku",
        GarmentModules.TECH_PACK_BOM to "Tech Pack & BOM",
        GarmentModules.COSTING_HPP to "Kalkulasi HPP",
        GarmentModules.PRODUCTION_MRP to "Jadwal Produksi (MRP)",
        GarmentModules.OPERATOR_EXEC to "Lantai Produksi",
        GarmentModules.QUALITY_CONTROL to "Quality Control",
        GarmentModules.FULFILLMENT to "Packing & Pengiriman",
        GarmentModules.MASTER_DATA to "Master Data Bahan & Harga",
        GarmentModules.VENDOR_CONTACTS to "Kontak Vendor",
        GarmentModules.INVOICING to "Invoice & Penagihan"
    )

    @Test
    fun menuLabelId_samaDenganJudulMenuSebelumnya_untukSemuaModul() {
        assertEquals(GarmentModules.modules.map { it.id }.toSet(), navTitles.keys)
        navTitles.forEach { (id, title) ->
            assertEquals(title, garment.module(id)?.menuLabelText?.textOrNull(LanguageCode.ID), "menuLabel id $id")
        }
    }

    @Test
    fun menuLabelEn_lengkapUntukSemuaModul_danGarmentTetapMengklaimEn() {
        garment.modules.forEach { m ->
            assertTrue(m.menuLabelText?.has(LanguageCode.EN) == true, "menuLabel en ${m.id.value}")
        }
        assertEquals(emptyList(), garment.missingTranslations())
        assertTrue(LanguageCode.EN in garment.languages.languages)
    }

    @Test
    fun packGarmentPenuh_roundTripLewatCodec() {
        assertEquals(garment, DomainPackCodec.decode(DomainPackCodec.encodeToString(garment)))
    }
}
