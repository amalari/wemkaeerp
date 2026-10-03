package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bentuk dasar bahasa visual clay: sudut membulat besar, jauh di atas radius Material default.
 *
 * Radius dipilih per peran, bukan per ukuran, supaya sebuah kartu node dan sebuah panel modal
 * tidak pernah tertukar bobot visualnya.
 */
object ClayShapes {
    /** Panel besar, modal, drawer. Sepadan dengan `border-radius: 24px` pada `.clay-card`. */
    val Panel = RoundedCornerShape(24.dp)

    /** Kartu entitas — node pipeline, kartu KPI, kartu modul. */
    val Card = RoundedCornerShape(20.dp)

    /** Tombol. Sepadan dengan `border-radius: 16px` pada `.btn-primary`. */
    val Button = RoundedCornerShape(16.dp)

    /** Tile ikon persegi dan kontainer kecil. */
    val Tile = RoundedCornerShape(14.dp)

    /** Chip, segmented control, field input. */
    val Chip = RoundedCornerShape(12.dp)

    /** Badge/pill status yang membulat penuh. */
    val Pill = RoundedCornerShape(percent = 50)

    // ── Bentuk di dalam kanvas kerja (invoice / template designer) ───────────────────────────
    // Sengaja jauh lebih kecil dari kartu UI: kertas A4 dan elemen di atasnya adalah bahan
    // cetak yang sudutnya mendekati siku, bukan permukaan clay yang membulat.

    /** Lembar kertas kerja — kanvas A4/Letter di dalam designer. */
    val Paper = RoundedCornerShape(4.dp)

    /** Elemen di dalam kertas — kotak teks, tabel item, bentuk garis. */
    val Element = RoundedCornerShape(2.dp)
}

/**
 * Jarak hard shadow dari kartunya.
 *
 * Ini menggantikan konsep elevation: pada bahasa neo-brutalist tidak ada blur sama sekali,
 * yang membedakan "dekat" dan "jauh" adalah seberapa besar pergeseran bayangan solidnya.
 */
object ClayOffset {
    /** Posisi diam. Sepadan dengan `box-shadow: 6px 6px 0`. */
    val Rest: Dp = 6.dp

    /** Elemen sekunder — chip, badge, tile kecil. */
    val Small: Dp = 4.dp

    /** Saat ditekan/terpilih: bayangan menyempit, kartu terlihat masuk ke dalam. */
    val Pressed: Dp = 2.dp

    /** Tanpa bayangan — untuk elemen yang menempel rata pada latar. */
    val Flat: Dp = 0.dp
}

/** Ketebalan outline. Outline tebal inilah pembeda utama neo-brutalism dari Material. */
object ClayBorder {
    /** Sepadan dengan `border: 3px solid`. */
    val Thick: Dp = 3.dp

    /** Untuk elemen kecil yang akan terlihat berat jika memakai 3dp. */
    val Medium: Dp = 2.dp

    val Hairline: Dp = 1.dp
}

/** Ritme spasi. Sebelumnya tersebar sebagai angka mentah di ~140 call site. */
object ClaySpacing {
    val Xxs: Dp = 2.dp
    val Xs: Dp = 4.dp
    val Sm: Dp = 6.dp
    val Md: Dp = 8.dp
    val Lg: Dp = 12.dp
    val Xl: Dp = 16.dp
    val Xxl: Dp = 24.dp
}

/**
 * Perenggangan huruf. Hanya dipakai pada label kapital pendek — pada teks isi, perenggangan
 * membuat Nunito yang ber-x-height besar terbaca renggang dan lemah.
 */
object ClayLetterSpacing {
    /** Section header dan label kapital lain, supaya kapitalnya tidak saling menempel. */
    val Label: TextUnit = 0.8.sp
}

/**
 * Breakpoint adaptif window-size, dipakai layar pertama yang benar-benar butuhnya (CRM
 * Leads). Ditaruh di sini, bukan angka telanjang di layar fitur, supaya modul berikutnya
 * yang butuh adaptivitas mewarisi nilai yang sama alih-alih menebak ulang.
 *
 * Adaptivitas window-size tercatat sebagai utang arsitektur ("greenfield", design-system
 * rules §8) sebelum CRM; nilai 840dp dipilih karena itulah titik umum tablet-lanskap/desktop
 * kecil beralih dari satu kolom ke dua kolom di panduan Material adaptive layouts.
 */
object ClayBreakpoints {
    /** Di bawah ini: satu kolom (daftar kartu + bottom sheet). Di atas/sama: master-detail dua panel. */
    val MasterDetail: Dp = 840.dp
}

/** Lebar panel dalam layar master-detail. */
object ClayPaneWidth {
    val List: Dp = 380.dp

    /**
     * Lebar bingkai satu layar pratinjau prototype (mock `/builder/prototype` dan wizard
     * Discovery). Sengaja mendekati lebar layar ponsel: pratinjau yang melebar mengikuti
     * kontainer terlihat seperti dokumen, bukan aplikasi. Beberapa bingkai disusun berjajar
     * lewat `ClayFlowRow`, bukan `fillMaxWidth`.
     */
    val PrototypeDevice: Dp = 360.dp

    /**
     * Lebar satu kolom **papan** di dalam kanvas yang menggulir mendatar (swimlane Factory Flow,
     * peta modul Studio Discovery, papan kanban).
     *
     * Wajib lebar tetap, **bukan** `fillMaxWidth(fraction)`: di dalam `Row` ber-`horizontalScroll`
     * lebar maksimum adalah tak hingga, sehingga `fillMaxWidth` diabaikan dan kolom menyusut ke
     * lebar intrinsiknya — teks lalu pecah satu huruf per baris. Nilainya lebih besar dari lebar
     * teks biasa karena clay memakan ~18dp per kartu (outline 3dp + bayangan 6dp).
     */
    val Board: Dp = 260.dp
}

/**
 * Diserahkan ke `MaterialTheme(shapes = …)`.
 *
 * Ini pengungkit termurah yang kita punya: dengan satu baris, seluruh `AlertDialog`, `FilterChip`,
 * `OutlinedTextField`, dan `DropdownMenu` bawaan M3 ikut memakai radius clay tanpa perlu
 * disentuh satu per satu. Material default-nya 4/8/12/16/28.
 */
val ClayMaterialShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(24.dp)
)
