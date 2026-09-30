package com.eventverse.app.domain.builder.print

import com.eventverse.app.domain.printing.TemplateRect
import com.eventverse.app.domain.printing.TextStyleSpec

/**
 * Model lembar tagihan: apa yang dicetak dan di mana — **tanpa** algoritma penataannya.
 *
 * Dipisah dari [SubscriptionInvoiceSheetLayout] supaya masing-masing punya satu nama yang jujur:
 * berkas ini menjawab "bentuk hasilnya apa", berkas itu menjawab "bagaimana menempatkannya".
 */

/** Perataan horizontal satu baris. Tagihan punya kolom angka di kanan; blueprint tidak. */
enum class SubscriptionInvoiceTextAlign { LEFT, RIGHT }

/**
 * Peran satu baris cetak. Ukuran & bobot ada di sini karena keduanya **milik dokumen**, bukan milik
 * renderer: pratinjau layar (kalau kelak ada) harus memakai angka yang sama persis dengan kertas.
 *
 * **Uji Variabilitas** (`tenant-variability-rules` Kontrak 1): enum ini lolos karena peran baris
 * dokumen adalah konsep **platform** — judul, heading, label, angka. Yang berbeda antar tenant adalah
 * **tulisan** di dalam barisnya (nama modul, harga), bukan perannya.
 */
enum class SubscriptionInvoiceLineRole(
    val sizePt: Int,
    val isBold: Boolean,
    val align: SubscriptionInvoiceTextAlign,
    /** `true` untuk baris yang tidak mencetak teks: renderer menggambar garis di posisinya. */
    val isRule: Boolean = false
) {
    TITLE(17, true, SubscriptionInvoiceTextAlign.LEFT),
    SUBTITLE(11, false, SubscriptionInvoiceTextAlign.LEFT),
    HEADING(12, true, SubscriptionInvoiceTextAlign.LEFT),
    BODY(10, false, SubscriptionInvoiceTextAlign.LEFT),

    /** Kepala kolom: "Modul" dan "Harga / bulan". */
    COLUMN_LEFT(9, true, SubscriptionInvoiceTextAlign.LEFT),
    COLUMN_RIGHT(9, true, SubscriptionInvoiceTextAlign.RIGHT),

    /** Isi baris tagihan: nama modul di kiri, angkanya di kanan. */
    LABEL(9, false, SubscriptionInvoiceTextAlign.LEFT),
    AMOUNT(9, false, SubscriptionInvoiceTextAlign.RIGHT),

    TOTAL_LABEL(11, true, SubscriptionInvoiceTextAlign.LEFT),
    TOTAL_AMOUNT(11, true, SubscriptionInvoiceTextAlign.RIGHT),

    NOTE(8, false, SubscriptionInvoiceTextAlign.LEFT),
    FOOTER(7, false, SubscriptionInvoiceTextAlign.LEFT),

    /**
     * Garis pemisah. Warnanya keputusan renderer; lebarnya nol karena yang digambar adalah satu garis,
     * bukan kotak.
     */
    RULE(1, false, SubscriptionInvoiceTextAlign.LEFT, isRule = true);

    val style: TextStyleSpec get() = TextStyleSpec(fontSizePt = sizePt, isBold = isBold)
}

/** Satu baris teks yang sudah ditempatkan di kertas. */
data class SubscriptionInvoiceLine(
    val text: String,
    val role: SubscriptionInvoiceLineRole,
    val rect: TemplateRect
)

/** Penanda diagonal yang dicetak di **setiap** halaman, termasuk halaman lanjutan. */
data class SubscriptionInvoiceWatermark(
    val text: String,
    val center: TemplateRect,
    val fontSizePt: Int,
    val angleDeg: Float
)

data class SubscriptionInvoicePage(
    /** 1-based; dipakai footer ("Halaman 2 dari 3"). */
    val index: Int,
    val body: List<SubscriptionInvoiceLine>,
    val footer: List<SubscriptionInvoiceLine>,
    val watermark: SubscriptionInvoiceWatermark
)

data class SubscriptionInvoiceSheet(
    val document: SubscriptionInvoicePdfDocument,
    val pages: List<SubscriptionInvoicePage>
)
