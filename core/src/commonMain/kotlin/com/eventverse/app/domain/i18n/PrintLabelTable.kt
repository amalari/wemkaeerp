package com.eventverse.app.domain.i18n

/**
 * Kosakata label cetak (PLAN-i18n Fase 1, Track B4).
 *
 * Bukan UI Compose — ini *chrome* dokumen PDF: judul kolom, judul seksi, awalan baris
 * identitas. Dulu semuanya literal Indonesia di dalam renderer, sehingga dokumen
 * berbahasa Inggris mustahil dicetak. Di sini ia jadi data berbahasa ganda, satu kunci
 * per label, dan renderer menerima [LanguageCode].
 *
 * Aturan:
 *  - **String `id` identik dengan literal lama** (paritas byte-per-byte); ini yang
 *    menjaga tes renderer lama tetap hijau.
 *  - Label yang bergantung angka disusun dari kunci + unit di renderer
 *    ([UNIT_SET], [UNIT_PCS]) supaya tabel ini tetap data, bukan fungsi berparameter.
 *  - Kunci tertutup: renderer memakai `PrintLabelKey`, jadi kompilator menangkap label
 *    yang lupa diterjemahkan.
 */
enum class PrintLabelKey {
    // Kartu SPK
    SPK_REV,
    SPK_POM_TITLE,
    SPK_SAMPLING_TITLE,
    SPK_SAMPLING_EMPTY,
    SPK_COLORWAY,
    SPK_PRINTED,
    SPK_CLIENT,
    SPK_SIZE,
    SPK_QTY,
    SPK_STAGE,
    SPK_DEADLINE,
    SPK_OVERFLOW_TAIL,

    // Kartu telusur
    TRACE_TARGET,
    UNIT_SET,
    UNIT_PCS,

    // Baris tulis tangan di kartu telusur
    WL_OPERATOR,
    WL_SHIFT_DATE,
    WL_QTY_PCS,
    WL_WEIGHT_KG,
    WL_PARENT_BUNDLE,
    WL_DATE,

    // Lembar kerja rajut
    KNIT_TITLE,
    KNIT_QUANTITY,
    KNIT_COLOR,
    KNIT_PANEL_SECTION,
    KNIT_PANEL_COL,
    KNIT_GRAMASI_COL,
    KNIT_MENIT_COL,
    KNIT_PROGRAM_COL,
    KNIT_SUMBER_COL,
    KNIT_TOTAL,
    KNIT_COPIED,
    KNIT_WEIGHED,
    KNIT_FEEDER,
    KNIT_BUYER_SECTION,
    KNIT_POM_COL,
    KNIT_FINISHED_COL,
    KNIT_RAW_COL,
    KNIT_TOLERANCE_COL,
    KNIT_BUYER_EMPTY
}

object PrintLabelTable {

    private val ID: Map<PrintLabelKey, String> = mapOf(
        PrintLabelKey.SPK_REV to "REV",
        PrintLabelKey.SPK_POM_TITLE to "UKURAN JADI — SPEK CLIENT (POM)",
        PrintLabelKey.SPK_SAMPLING_TITLE to "HASIL UKURAN TIM SAMPLING",
        PrintLabelKey.SPK_SAMPLING_EMPTY to "Belum ada hasil ukuran — isi di lembar Program CAM.",
        PrintLabelKey.SPK_COLORWAY to "WARNA:",
        PrintLabelKey.SPK_PRINTED to "Dicetak",
        PrintLabelKey.SPK_CLIENT to "Client",
        PrintLabelKey.SPK_SIZE to "Size",
        PrintLabelKey.SPK_QTY to "JUMLAH",
        PrintLabelKey.SPK_STAGE to "Tahap",
        PrintLabelKey.SPK_DEADLINE to "Deadline",
        PrintLabelKey.SPK_OVERFLOW_TAIL to "ukuran lainnya — lihat lembar kerja rajut",
        PrintLabelKey.TRACE_TARGET to "Target",
        PrintLabelKey.UNIT_SET to "set",
        PrintLabelKey.UNIT_PCS to "pcs",
        PrintLabelKey.WL_OPERATOR to "Operator",
        PrintLabelKey.WL_SHIFT_DATE to "Shift / Tanggal",
        PrintLabelKey.WL_QTY_PCS to "Jumlah (pcs)",
        PrintLabelKey.WL_WEIGHT_KG to "Berat (kg)",
        PrintLabelKey.WL_PARENT_BUNDLE to "Bundel induk",
        PrintLabelKey.WL_DATE to "Tanggal",
        PrintLabelKey.KNIT_TITLE to "LEMBAR KERJA RAJUT — SIZE",
        PrintLabelKey.KNIT_QUANTITY to "Jumlah:",
        PrintLabelKey.KNIT_COLOR to "Warna:",
        PrintLabelKey.KNIT_PANEL_SECTION to "SPEK PER BAGIAN (hasil breakdown panel)",
        PrintLabelKey.KNIT_PANEL_COL to "PANEL",
        PrintLabelKey.KNIT_GRAMASI_COL to "GRAMASI",
        PrintLabelKey.KNIT_MENIT_COL to "MENIT",
        PrintLabelKey.KNIT_PROGRAM_COL to "PROGRAM CAM",
        PrintLabelKey.KNIT_SUMBER_COL to "SUMBER",
        PrintLabelKey.KNIT_TOTAL to "TOTAL",
        PrintLabelKey.KNIT_COPIED to "salinan",
        PrintLabelKey.KNIT_WEIGHED to "ditimbang",
        PrintLabelKey.KNIT_FEEDER to "Instruksi Panah / Feeder:",
        PrintLabelKey.KNIT_BUYER_SECTION to "SPEK DARI BUYER (titik ukur & toleransi)",
        PrintLabelKey.KNIT_POM_COL to "TITIK UKUR",
        PrintLabelKey.KNIT_FINISHED_COL to "JADI (CM)",
        PrintLabelKey.KNIT_RAW_COL to "RAJUT MENTAH",
        PrintLabelKey.KNIT_TOLERANCE_COL to "TOLERANSI",
        PrintLabelKey.KNIT_BUYER_EMPTY to "Buyer belum mengisi tabel ukuran untuk size ini."
    )

    private val EN: Map<PrintLabelKey, String> = mapOf(
        PrintLabelKey.SPK_REV to "REV",
        PrintLabelKey.SPK_POM_TITLE to "FINISHED MEASUREMENTS — CLIENT SPEC (POM)",
        PrintLabelKey.SPK_SAMPLING_TITLE to "SAMPLING TEAM MEASUREMENT RESULTS",
        PrintLabelKey.SPK_SAMPLING_EMPTY to "No measurements yet — fill in the CAM Program sheet.",
        PrintLabelKey.SPK_COLORWAY to "COLOR:",
        PrintLabelKey.SPK_PRINTED to "Printed",
        PrintLabelKey.SPK_CLIENT to "Client",
        PrintLabelKey.SPK_SIZE to "Size",
        PrintLabelKey.SPK_QTY to "QTY",
        PrintLabelKey.SPK_STAGE to "Stage",
        PrintLabelKey.SPK_DEADLINE to "Deadline",
        PrintLabelKey.SPK_OVERFLOW_TAIL to "more measurements — see the knitting worksheet",
        PrintLabelKey.TRACE_TARGET to "Target",
        PrintLabelKey.UNIT_SET to "sets",
        PrintLabelKey.UNIT_PCS to "pcs",
        PrintLabelKey.WL_OPERATOR to "Operator",
        PrintLabelKey.WL_SHIFT_DATE to "Shift / Date",
        PrintLabelKey.WL_QTY_PCS to "Quantity (pcs)",
        PrintLabelKey.WL_WEIGHT_KG to "Weight (kg)",
        PrintLabelKey.WL_PARENT_BUNDLE to "Parent bundle",
        PrintLabelKey.WL_DATE to "Date",
        PrintLabelKey.KNIT_TITLE to "KNITTING WORKSHEET — SIZE",
        PrintLabelKey.KNIT_QUANTITY to "Quantity:",
        PrintLabelKey.KNIT_COLOR to "Color:",
        PrintLabelKey.KNIT_PANEL_SECTION to "SPEC PER PANEL (panel breakdown result)",
        PrintLabelKey.KNIT_PANEL_COL to "PANEL",
        PrintLabelKey.KNIT_GRAMASI_COL to "WEIGHT",
        PrintLabelKey.KNIT_MENIT_COL to "MINUTES",
        PrintLabelKey.KNIT_PROGRAM_COL to "CAM PROGRAM",
        PrintLabelKey.KNIT_SUMBER_COL to "SOURCE",
        PrintLabelKey.KNIT_TOTAL to "TOTAL",
        PrintLabelKey.KNIT_COPIED to "copied",
        PrintLabelKey.KNIT_WEIGHED to "weighed",
        PrintLabelKey.KNIT_FEEDER to "Needle / Feeder instruction:",
        PrintLabelKey.KNIT_BUYER_SECTION to "SPEC FROM BUYER (measurement points & tolerance)",
        PrintLabelKey.KNIT_POM_COL to "MEASUREMENT POINT",
        PrintLabelKey.KNIT_FINISHED_COL to "FINISHED (CM)",
        PrintLabelKey.KNIT_RAW_COL to "RAW KNIT",
        PrintLabelKey.KNIT_TOLERANCE_COL to "TOLERANCE",
        PrintLabelKey.KNIT_BUYER_EMPTY to "Buyer has not filled the size chart for this size."
    )

    /**
     * Label [key] dalam [language]. `en` selalu terisi untuk setiap kunci (dijaga paritas
     * oleh `PrintLabelTableParityTest`); `id` adalah bawaan paritas.
     */
    fun label(key: PrintLabelKey, language: LanguageCode): String {
        val table = if (language == LanguageCode.EN) EN else ID
        return table[key] ?: ID.getValue(key)
    }

    /**
     * Apakah [key] punya padanan bahasa Inggris eksplisit. Dipakai tes paritas yang
     * mengiterasi [PrintLabelKey.entries] — kunci tanpa `en` menggagalkan build, bukan
     * diam-diam memakai `id` (Kontrak 7).
     */
    fun isTranslated(key: PrintLabelKey): Boolean = EN.containsKey(key)
}
