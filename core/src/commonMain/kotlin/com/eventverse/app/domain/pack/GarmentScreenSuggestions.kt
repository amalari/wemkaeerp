package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.prototype.CardElement
import com.eventverse.app.domain.prototype.CardStyle
import com.eventverse.app.domain.prototype.ColumnMeta
import com.eventverse.app.domain.prototype.CountSpec
import com.eventverse.app.domain.prototype.DashboardHints
import com.eventverse.app.domain.prototype.FieldHint
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.FormConfig
import com.eventverse.app.domain.prototype.FormHints
import com.eventverse.app.domain.prototype.KanbanHints
import com.eventverse.app.domain.prototype.TableHints

/** Melekatkan alasan pilihan layar (data pack) tanpa menambah argumen posisi ke tiap konstruktor. */
private fun ScreenSuggestion.because(reason: String): ScreenSuggestion = copy(rationale = reason)

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
 *
 * B3 (butir jalur B): dua layar terpenting membawa petunjuk kaya — CRM "Daftar PO" bertipe
 * ([TableHints.fields], `inlineCreate`, `editableFields`) dan papan SPK sampling berkartu bertipe
 * ([KanbanHints.card]/[KanbanHints.columnMeta]/[KanbanHints.detailForm]). Garment tetap **mode
 * legacy** (baris berkunci "Kolom", field dari baris) — deklarasi `fields`/`groupField` milik mode
 * server (B2.1) dan sengaja tidak dipakai di sini.
 */
object GarmentScreenSuggestions {
    val all: List<ScreenSuggestion> = listOf(
        ScreenSuggestion(
            GarmentModules.CRM_SALES, "Daftar PO & Prospek", WidgetKind.TABLE,
            GarmentExportSeed.crmRows(),
            // B3: No. PO & Pembeli wajib, sisanya teks; status ENUM otomatis dari kolom status.
            tableHints = TableHints(
                "Status", listOf("Prospek", "Sampling", "Produksi", "Siap kirim"),
                fields = listOf(
                    FieldHint("No. PO", FieldType.TEXT, required = true),
                    FieldHint("Pembeli", FieldType.TEXT, required = true),
                    FieldHint("Produk", FieldType.TEXT),
                    FieldHint("Target Kirim", FieldType.TEXT)
                ),
                inlineCreate = true,
                editableFields = listOf("Pembeli", "Produk", "Target Kirim")
            )
        ).because("Dipilih karena PO dan prospek dibandingkan berderet — pembeli, produk, dan target kirim — lalu statusnya diubah langsung di baris."),
        ScreenSuggestion(
            GarmentModules.SAMPLING_ORDER, "Papan SPK Sampling", WidgetKind.KANBAN,
            GarmentExportSeed.samplingRows(),
            KanbanHints(
                columns = listOf("Baru", "Dikerjakan", "Selesai"),
                transitions = mapOf("Baru" to setOf("Dikerjakan"), "Dikerjakan" to setOf("Baru", "Selesai"), "Selesai" to setOf("Dikerjakan")),
                groupLabel = "Status SPK sampling",
                // B3: kartu bertipe — nomor jadi judul; lencana jenis; angka/tanggal/tanda sesuai data.
                card = listOf(
                    CardElement("Nomor", CardStyle.TITLE),
                    CardElement("Artikel", CardStyle.TEXT),
                    CardElement("Jenis", CardStyle.BADGE),
                    CardElement("Pembeli", CardStyle.TEXT),
                    CardElement("Jumlah", CardStyle.NUMBER),
                    CardElement("Due", CardStyle.DATE),
                    CardElement("Mendesak", CardStyle.FLAG)
                ),
                // Warna kolom = data pack; kolom Dikerjakan dibatasi WIP 3 SPK.
                columnMeta = mapOf(
                    "Baru" to ColumnMeta(tintHex = 0xFF64748B),
                    "Dikerjakan" to ColumnMeta(tintHex = 0xFF2563EB, wipLimit = 3),
                    "Selesai" to ColumnMeta(tintHex = 0xFF16A34A)
                ),
                detailForm = FormConfig(listOf("Nomor", "Artikel", "Jenis", "Pembeli", "Jumlah", "Due", "Mendesak"), "Simpan SPK")
            )
        ).because("Dipilih karena SPK sampling berpindah tahap dari baru, dikerjakan, sampai selesai, dan antrean kerja terlihat jelas di papan."),
        ScreenSuggestion(
            GarmentModules.TECH_PACK_BOM, "Spesifikasi BOM & Tech Pack", WidgetKind.TABLE,
            GarmentExportSeed.bomRows(),
            tableHints = TableHints("Status", listOf("Draft", "Final"))
        ).because("Dipilih karena BOM adalah daftar bahan per artikel yang dibaca berderet dan ditandai draft atau final."),
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
        ).because("Dipilih karena pemilik butuh angka ringkas HPP, margin, dan order aktif tanpa membuka daftar."),
        ScreenSuggestion(
            GarmentModules.INVENTORY, "Stok Kain & Bahan Baku", WidgetKind.TABLE,
            GarmentExportSeed.inventoryRows(),
            // B4: "Kepemilikan" bertipe ENUM di tabel juga — formulir pelengkapnya sudah ENUM pada entitas yang sama,
            // dan lintas-layar entitas yang sama wajib berdefinisi konsisten (semua nilai seed ada di pilihan).
            tableHints = TableHints(
                "Status", listOf("Tersedia", "Menipis", "Konsinyasi"),
                fields = listOf(FieldHint("Kepemilikan", FieldType.ENUM, options = listOf("Milik pabrik", "Titipan buyer")))
            ),
            // Form pelengkap tabel: Bahan, Stok, dan Kepemilikan wajib diisi (butir B2) supaya
            // baris baru langsung lolos aturan stok — kepemilikan menentukan semantik nilai (Kontrak 3).
            formHints = FormHints(
                fields = listOf("Bahan", "Stok", "Kepemilikan"),
                required = listOf("Bahan", "Stok", "Kepemilikan"),
                options = mapOf("Kepemilikan" to listOf("Milik pabrik", "Titipan buyer")),
                submitLabel = "Catat bahan"
            )
        ).because("Dipilih karena stok kain dicek berderet per bahan, dengan penanda milik pabrik atau titipan buyer."),
        ScreenSuggestion(
            GarmentModules.PRODUCTION_MRP, "Jadwal Potong & SPK Massal", WidgetKind.KANBAN,
            GarmentExportSeed.mrpRows(),
            KanbanHints(
                columns = listOf("Antre Potong", "Berjalan", "Selesai"),
                transitions = mapOf("Antre Potong" to setOf("Berjalan"), "Berjalan" to setOf("Antre Potong", "Selesai")),
                groupLabel = "Tahap produksi"
            )
        ).because("Dipilih karena SPK massal bergerak dari antre potong, berjalan, sampai selesai dan perlu terlihat sebagai papan."),
        ScreenSuggestion(
            GarmentModules.OPERATOR_EXEC, "Kanban Lini Jahit", WidgetKind.KANBAN,
            GarmentExportSeed.linimRows(),
            KanbanHints(columns = listOf("Lini 2", "Lini 3", "Lini 4"), groupLabel = "Lini jahit")
        ).because("Dipilih karena pekerjaan dibagi per lini jahit dan kepala produksi perlu melihat beban tiap lini."),
        ScreenSuggestion(
            GarmentModules.QUALITY_CONTROL, "Checklist Inspeksi QC", WidgetKind.CHECKLIST,
            GarmentExportSeed.qcRows()
        ).because("Dipilih karena inspeksi mutu adalah daftar butir periksa yang dicentang satu per satu."),
        ScreenSuggestion(
            GarmentModules.FULFILLMENT, "Surat Jalan & Packing List", WidgetKind.PRINT,
            listOf(GarmentExportSeed.suratJalanRow())
        ).because("Dipilih karena surat jalan dan packing list diserahkan ke pembeli dalam bentuk cetak.")
    )
}