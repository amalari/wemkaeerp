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
