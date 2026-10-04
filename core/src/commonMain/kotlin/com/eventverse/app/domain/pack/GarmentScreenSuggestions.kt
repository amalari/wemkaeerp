package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.TableHints

/**
 * Usulan layar prototype bawaan pack konveksi (mock `/builder/prototype`): satu layar per modul
 * operasional, widget-nya mengikuti watak kerjanya (PO → tabel daftar, SPK & lini jahit → papan
 * kanban, BOM & stok kain → tabel, HPP → dasbor, inspeksi → checklist, surat jalan → cetak). Ini
 * **data pack** sejajar [GarmentPortTypes.labels] — pack vertikal lain kelak mengusulkan layarnya sendiri;
 * tidak ada satu baris pun kosakata garment yang bocor ke mesin renderer.
 *
 * v2: setiap usulan membawa [ScreenSuggestion.sampleRows] — isi layar yang akan dilihat user garment
 * (nama buyer, nomor PO, jumlah pcs, tanggal), sehingga pratinjau terbaca seperti aplikasi jadi,
 * bukan kerangka "contoh 1". Bentuk baris per widget dikontrakkan di KDoc [ScreenSuggestion].
 */
object GarmentScreenSuggestions {
    val all: List<ScreenSuggestion> = listOf(
        ScreenSuggestion(
            GarmentModules.CRM_SALES, "Daftar PO & Prospek", WidgetKind.TABLE,
            GarmentExportSeed.crmRows(),
            tableHints = TableHints("Status", listOf("Prospek", "Sampling", "Produksi", "Siap kirim"))
        ),
        ScreenSuggestion(
            GarmentModules.SAMPLING_ORDER, "Papan SPK Sampling", WidgetKind.KANBAN,
            GarmentExportSeed.samplingRows(),
            KanbanHints(
                columns = listOf("Baru", "Dikerjakan", "Selesai"),
                transitions = mapOf("Baru" to setOf("Dikerjakan"), "Dikerjakan" to setOf("Baru", "Selesai"), "Selesai" to setOf("Dikerjakan")),
                groupLabel = "Status SPK sampling"
            )
        ),
        ScreenSuggestion(
            GarmentModules.TECH_PACK_BOM, "Spesifikasi BOM & Tech Pack", WidgetKind.TABLE,
            GarmentExportSeed.bomRows(),
            tableHints = TableHints("Status", listOf("Draft", "Final"))
        ),
        ScreenSuggestion(
            GarmentModules.COSTING_HPP, "Dasbor HPP & Biaya", WidgetKind.DASHBOARD,
            listOf(
                mapOf("HPP rata-rata" to "Rp 98.400 / pcs"),
                mapOf("Margin target" to "25%"),
                mapOf("Order aktif" to "2 PO"),
                mapOf("SPK sampling berjalan" to "4 SPK"),
                mapOf("Nilai order produksi (FOB)" to GarmentExportSeed.activeValueText()),
                mapOf("Biaya terbesar" to "Kain — 58% dari HPP")
            ),
            dashboardHints = DashboardHints(
                mapOf(
                    // Angka ikut berubah saat kartu di papan sumber dipindah (TRD-PLAT-003).
                    "Order aktif" to CountSpec(GarmentModules.PRODUCTION_MRP.value, "Kolom", notEquals = "Selesai", suffix = " PO"),
                    "SPK sampling berjalan" to CountSpec(GarmentModules.SAMPLING_ORDER.value, "Kolom", notEquals = "Selesai", suffix = " SPK")
                )
            )
        ),
        ScreenSuggestion(
            GarmentModules.INVENTORY, "Stok Kain & Bahan Baku", WidgetKind.TABLE,
            GarmentExportSeed.inventoryRows(),
            tableHints = TableHints("Status", listOf("Tersedia", "Menipis", "Konsinyasi"))
        ),
        ScreenSuggestion(
            GarmentModules.PRODUCTION_MRP, "Jadwal Potong & SPK Massal", WidgetKind.KANBAN,
            GarmentExportSeed.mrpRows(),
            KanbanHints(
                columns = listOf("Antre Potong", "Berjalan", "Selesai"),
                transitions = mapOf("Antre Potong" to setOf("Berjalan"), "Berjalan" to setOf("Antre Potong", "Selesai")),
                groupLabel = "Tahap produksi"
            )
        ),
        ScreenSuggestion(
            GarmentModules.OPERATOR_EXEC, "Kanban Lini Jahit", WidgetKind.KANBAN,
            GarmentExportSeed.linimRows(),
            KanbanHints(columns = listOf("Lini 2", "Lini 3", "Lini 4"), groupLabel = "Lini jahit")
        ),
        ScreenSuggestion(
            GarmentModules.QUALITY_CONTROL, "Checklist Inspeksi QC", WidgetKind.CHECKLIST,
            GarmentExportSeed.qcRows()
        ),
        ScreenSuggestion(
            GarmentModules.FULFILLMENT, "Surat Jalan & Packing List", WidgetKind.PRINT,
            listOf(GarmentExportSeed.suratJalanRow())
        )
    )
}