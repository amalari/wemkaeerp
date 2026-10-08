package com.eventverse.app.domain.discovery.proposal

/**
 * Satu blok pada kerangka layar `CUSTOM_SCREEN` yang dinyatakan agent ("Keranjang", "Pembayaran").
 *
 * Ini sketsa untuk dinilai prospek, **bukan** komponen interaktif (keputusan D6): blok tidak punya entitas,
 * field, atau aksi. [hint] hanya memberi tahu renderer rupa sketsanya dan menandai blok mana yang kelak
 * dapat menjadi blok sungguhan lewat jalur biasa.
 *
 * [label] adalah teks bebas dari model, jadi wajib lewat batas `ProposalLimits` dan pemeriksaan kemurnian
 * vertikal seperti teks usulan lain (`ProposalSkeletonRules`, `ProposalPurityRules`).
 */
data class SkeletonBlock(
    val label: String,
    val width: SkeletonWidth = SkeletonWidth.FULL,
    val hint: SkeletonHint = SkeletonHint.TABLE
)

/** Lebar blok pada kerangka. Dua nilai saja: kerangka bukan tata letak bebas. */
enum class SkeletonWidth { FULL, HALF }

/**
 * Petunjuk rupa sketsa, daftar **tertutup** (keputusan D5).
 *
 * Uji Variabilitas: ini kosakata milik sistem untuk menggambar sketsa, bukan konsep domain tenant — tidak
 * berbeda per tenant/industri dan tidak untuk diubah admin pabrik. Label blok-lah yang membawa kosakata
 * domain. Nilai tak dikenal **ditolak** oleh codec, tidak jatuh ke nilai bawaan.
 */
enum class SkeletonHint { TABLE, FORM, METRIC_CARDS, ACTIONS }

/**
 * Satu baris sampel untuk renderer statis (kosakata struktural "Blok"/"Lebar"/"Petunjuk", sama dengan sampel
 * generik `WidgetRegistry`). Murni dan deterministik; `when` tanpa `else` supaya nilai baru memaksa pembaruan.
 */
fun SkeletonBlock.toSampleRow(): Map<String, String> = mapOf(
    "Blok" to label,
    "Lebar" to when (width) {
        SkeletonWidth.FULL -> "penuh"
        SkeletonWidth.HALF -> "separuh"
    },
    "Petunjuk" to when (hint) {
        SkeletonHint.TABLE -> "tabel"
        SkeletonHint.FORM -> "formulir"
        SkeletonHint.METRIC_CARDS -> "kartu angka"
        SkeletonHint.ACTIONS -> "aksi"
    }
)
