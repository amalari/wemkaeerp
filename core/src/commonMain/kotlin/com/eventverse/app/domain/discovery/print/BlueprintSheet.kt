package com.eventverse.app.domain.discovery.print

import com.eventverse.app.domain.printing.TemplateRect
import com.eventverse.app.domain.printing.TextStyleSpec

/**
 * Model lembar blueprint: apa yang dicetak dan di mana — **tanpa** algoritma penataannya.
 *
 * Dipisah dari `BlueprintSheetLayout` supaya masing-masing punya satu nama yang jujur: berkas ini
 * menjawab "bentuk hasilnya apa", berkas itu menjawab "bagaimana menempatkannya". Renderer PDF hanya
 * butuh yang pertama, dan test invariant tata letak hanya butuh yang kedua.
 */

/**
 * Peran satu baris cetak. Ukuran & bobot ada di sini karena keduanya **milik dokumen**, bukan milik
 * renderer: pratinjau layar (kalau kelak ada) harus memakai angka yang sama persis dengan kertas,
 * sama seperti alasan `TraceLabelRegions` hidup di domain.
 *
 * Warna sengaja **tidak** ada di sini — itu keputusan renderer.
 *
 * **Uji Variabilitas** (`tenant-variability-rules` Kontrak 1): enum ini lolos karena peran baris
 * dokumen adalah konsep **platform** — judul, subjudul, heading, isi, footer. Yang berbeda antar
 * tenant/pack adalah **tulisan** di dalam barisnya (kosakata pack), bukan perannya; jumlah peran tidak
 * bertambah setiap kali ada vertikal baru. (`scripts/audit-variability.sh` melaporkan enum ini; ini
 * jawabannya.)
 */
enum class BlueprintLineRole(val sizePt: Int, val isBold: Boolean, val indentMm10: Int) {
    TITLE(17, true, 0),
    SUBTITLE(11, false, 0),
    BADGE(10, true, 0),
    HEADING(12, true, 0),
    BODY(10, false, 0),
    ROW(9, false, 90),
    DETAIL(8, false, 180),
    NOTE(8, false, 0),
    FOOTER(7, false, 0);

    val style: TextStyleSpec get() = TextStyleSpec(fontSizePt = sizePt, isBold = isBold)
}

/** Satu baris teks yang sudah ditempatkan di kertas. */
data class BlueprintLine(
    val text: String,
    val role: BlueprintLineRole,
    val rect: TemplateRect
)

/** Penanda diagonal yang dicetak di **setiap** halaman, termasuk halaman lanjutan. */
data class BlueprintWatermark(
    val text: String,
    val center: TemplateRect,
    val fontSizePt: Int,
    val angleDeg: Float
)

data class BlueprintPage(
    /** 1-based; dipakai footer ("Halaman 2 dari 3"). */
    val index: Int,
    val body: List<BlueprintLine>,
    val footer: List<BlueprintLine>,
    val watermark: BlueprintWatermark
)

data class BlueprintSheet(
    val document: BlueprintPdfDocument,
    val pages: List<BlueprintPage>
)
