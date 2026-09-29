package com.eventverse.app.presentation.workspace

import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.BusinessModule

/**
 * Baris contoh layar generik per modul (B6e — dulu `when` exhaustive atas enum). Modul tanpa entri, termasuk modul
 * pack lain, tidak punya baris contoh: lebih baik kosong daripada contoh yang menyesatkan bila benar-benar terlihat.
 */
internal object ModuleSampleRows {
    private val rows: Map<BusinessModule, List<Pair<String, String>>> = mapOf(
        GarmentModules.CRM_SALES to listOf(
            "PT Sinar Jaya — 1.200 pcs kemeja" to "Prospek",
            "CV Amanah — 500 pcs seragam" to "Nego"
        ),
        GarmentModules.SAMPLING_ORDER to listOf(
            "Sample #SP-1043 — Polo Cotton" to "Jahit",
            "Sample #SP-1044 — Kemeja PDH" to "Review"
        ),
        GarmentModules.MASTER_DATA to listOf(
            "Benang Cotton Combed 30s — YRN-0001" to "Aktif",
            "Kain Fleece Katun 280 gsm — FAB-0002" to "Aktif"
        ),
        GarmentModules.INVENTORY to listOf(
            "Cotton Combed 30s — 420 kg" to "Tersedia",
            "Kain titipan buyer — 180 kg" to "Konsinyasi"
        ),
        GarmentModules.TECH_PACK_BOM to listOf(
            "Tech Pack PDH-2024 rev.3" to "Final",
            "BOM Polo Combed" to "Draft"
        ),
        GarmentModules.PRODUCTION_MRP to listOf(
            "SPK-8891 — Line 2, 3 hari" to "Berjalan",
            "SPK-8892 — Line 4" to "Antre"
        ),
        GarmentModules.OPERATOR_EXEC to listOf(
            "Rian — 320 pcs hari ini" to "Tercatat",
            "Agus — 280 pcs hari ini" to "Tercatat"
        ),
        GarmentModules.QUALITY_CONTROL to listOf(
            "Inspeksi AQL 2.5 — lot 8891" to "Lolos",
            "Temuan jahitan loncat — 12 pcs" to "Rework"
        ),
        GarmentModules.FULFILLMENT to listOf(
            "Surat Jalan SJ-2201 — 40 karton" to "Dikirim",
            "Packing list PO-5512" to "Disiapkan"
        ),
        GarmentModules.INVOICING to listOf(
            "INV/2026/03/0001 — PT Sinar Jaya (DP 50%)" to "Issued",
            "INV/2026/03/0002 — CV Amanah (Sampling)" to "Paid"
        )
    )

    fun rowsFor(module: BusinessModule): List<Pair<String, String>> = rows[module].orEmpty()
}
