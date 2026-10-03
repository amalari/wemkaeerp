package com.eventverse.app.domain.pack

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Usulan layar prototype bawaan pack konveksi (mock `/builder/prototype`): satu layar per modul
 * operasional, widget-nya mengikuti watak kerjanya (PO → formulir, SPK & lini jahit → papan kanban,
 * BOM & stok kain → tabel, HPP → dasbor, inspeksi → checklist, surat jalan → cetak). Ini **data
 * pack** sejajar [GarmentPortTypes.labels] — pack vertikal lain kelak mengusulkan layarnya sendiri;
 * tidak ada satu baris pun kosakata garment yang bocor ke mesin renderer.
 *
 * v2: setiap usulan membawa [ScreenSuggestion.sampleRows] — isi layar yang akan dilihat user garment
 * (nama buyer, nomor PO, jumlah pcs, tanggal), sehingga pratinjau terbaca seperti aplikasi jadi,
 * bukan kerangka "contoh 1". Bentuk baris per widget dikontrakkan di KDoc [ScreenSuggestion].
 */
object GarmentScreenSuggestions {
    val all: List<ScreenSuggestion> = listOf(
        ScreenSuggestion(
            GarmentModules.CRM_SALES, "Daftar PO & Prospek", WidgetKind.FORM,
            listOf(
                mapOf(
                    "Nama Pembeli" to "PT Sinar Jaya Garment",
                    "No. PO" to "PO-2026-0312",
                    "Produk & Jumlah" to "1.200 pcs kemeja PDH",
                    "Target Kirim" to "28 Maret 2026",
                    "Catatan" to "Prospek — masih nego harga jahit",
                    "Simpan" to "Simpan Pelanggan & Prospek Sales"
                )
            )
        ),
        ScreenSuggestion(
            GarmentModules.SAMPLING_ORDER, "Papan SPK Sampling", WidgetKind.KANBAN,
            listOf(
                mapOf("Kolom" to "Baru", "Kartu" to "SP-1051 · Kemeja PDH", "Detail" to "PT Sinar Jaya · 3 pcs sampel"),
                mapOf("Kolom" to "Dikerjakan", "Kartu" to "SP-1048 · Polo Combed", "Detail" to "Jahit sample — Nia · due 20 Mar"),
                mapOf("Kolom" to "Selesai", "Kartu" to "SP-1043 · Seragam CV Amanah", "Detail" to "Disetujui buyer — naik produksi")
            )
        ),
        ScreenSuggestion(
            GarmentModules.TECH_PACK_BOM, "Spesifikasi BOM & Tech Pack", WidgetKind.TABLE,
            listOf(
                mapOf("Komponen" to "Kain Cotton Combed 30s", "Spesifikasi" to "Navy · 280 gsm", "Pemakaian" to "1,8 yd/pcs", "Status" to "Final"),
                mapOf("Komponen" to "Benang Polyester 120", "Spesifikasi" to "Putih · 5.000 yd/koni", "Pemakaian" to "0,05 koni/pcs", "Status" to "Final"),
                mapOf("Komponen" to "Kancing mutiara 4 lubang", "Spesifikasi" to "12 mm", "Pemakaian" to "11 pcs/baju", "Status" to "Draft"),
                mapOf("Komponen" to "Label woven brand", "Spesifikasi" to "PDH-2024 rev.3", "Pemakaian" to "1 pcs/baju", "Status" to "Final")
            )
        ),
        ScreenSuggestion(
            GarmentModules.COSTING_HPP, "Dasbor HPP & Biaya", WidgetKind.DASHBOARD,
            listOf(
                mapOf("HPP rata-rata" to "Rp 38.500 / pcs"),
                mapOf("Margin target" to "22%"),
                mapOf("Order aktif" to "12 PO"),
                mapOf("Biaya terbesar" to "Kain — 54% dari HPP")
            )
        ),
        ScreenSuggestion(
            GarmentModules.INVENTORY, "Stok Kain & Bahan Baku", WidgetKind.TABLE,
            listOf(
                mapOf("Bahan" to "Cotton Combed 30s", "Stok" to "420 kg", "Kepemilikan" to "Milik pabrik", "Status" to "Tersedia"),
                mapOf("Bahan" to "Kain Fleece Katun 280 gsm", "Stok" to "180 kg", "Kepemilikan" to "Titipan buyer", "Status" to "Konsinyasi"),
                mapOf("Bahan" to "Benang Polyester 120", "Stok" to "96 koni", "Kepemilikan" to "Milik pabrik", "Status" to "Tersedia"),
                mapOf("Bahan" to "Kancing mutiara 12 mm", "Stok" to "5.000 pcs", "Kepemilikan" to "Milik pabrik", "Status" to "Menipis")
            )
        ),
        ScreenSuggestion(
            GarmentModules.PRODUCTION_MRP, "Jadwal Potong & SPK Massal", WidgetKind.KANBAN,
            listOf(
                mapOf("Kolom" to "Antre Potong", "Kartu" to "PO-2026-0312 · Kemeja PDH", "Detail" to "1.200 pcs · Meja potong 3"),
                mapOf("Kolom" to "Berjalan", "Kartu" to "PO-2026-0298 · Jahit", "Detail" to "Lini 2 · selesai 3 hari lagi"),
                mapOf("Kolom" to "Selesai", "Kartu" to "PO-2026-0285 · Obras", "Detail" to "Lini 4 · 2.400 pcs")
            )
        ),
        ScreenSuggestion(
            GarmentModules.OPERATOR_EXEC, "Kanban Lini Jahit", WidgetKind.KANBAN,
            listOf(
                mapOf("Kolom" to "Lini 2", "Kartu" to "Rian — jahit kerah", "Detail" to "320 pcs hari ini"),
                mapOf("Kolom" to "Lini 2", "Kartu" to "Sinta — jahit badan", "Detail" to "280 pcs hari ini"),
                mapOf("Kolom" to "Lini 4", "Kartu" to "Agus — pasang lengan", "Detail" to "255 pcs hari ini")
            )
        ),
        ScreenSuggestion(
            GarmentModules.QUALITY_CONTROL, "Checklist Inspeksi QC", WidgetKind.CHECKLIST,
            listOf(
                mapOf("Butir" to "Jahitan lurus, tidak ada loncat", "Selesai" to "ya"),
                mapOf("Butir" to "Kancing & lubang lengkap (11 pcs)", "Selesai" to "ya"),
                mapOf("Butir" to "Permukaan bersih, tanpa noda minyak", "Selesai" to "tidak"),
                mapOf("Butir" to "Ukuran dalam toleransi ±0,5 cm", "Selesai" to "ya")
            )
        ),
        ScreenSuggestion(
            GarmentModules.FULFILLMENT, "Surat Jalan & Packing List", WidgetKind.PRINT,
            listOf(
                mapOf(
                    "Dokumen" to "Surat Jalan SJ-2201",
                    "Nomor" to "0001/SJ/III/2026",
                    "Penerima" to "PT Sinar Jaya Garment",
                    "Isi" to "40 karton — 4.800 pcs kemeja PDH",
                    "Ekspedisi" to "Truk rental — berangkat 14.00"
                )
            )
        )
    )
}