package com.eventverse.app.presentation.designsystem

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.eventverse.app.shared.resources.Res
import com.eventverse.app.shared.resources.fredoka_bold
import com.eventverse.app.shared.resources.fredoka_medium
import com.eventverse.app.shared.resources.fredoka_semibold
import com.eventverse.app.shared.resources.nunito_bold
import com.eventverse.app.shared.resources.nunito_regular
import com.eventverse.app.shared.resources.nunito_semibold
import org.jetbrains.compose.resources.Font

/**
 * Fredoka — font judul. Bentuk hurufnya membulat dan terminalnya tumpul; inilah yang membuat
 * bahasa clay terasa utuh. Tanpa font membulat, outline tebal saja akan terbaca sebagai
 * neo-brutalism dingin, bukan clay.
 *
 * Hanya tiga bobot yang dibundel (Medium/SemiBold/Bold). Fredoka nyaris tidak pernah dipakai
 * di bawah SemiBold pada desain ini, jadi membundel Light/Regular hanya menambah berat unduhan.
 */
@Composable
fun rememberFredokaFamily(): FontFamily = FontFamily(
    Font(Res.font.fredoka_medium, FontWeight.Medium),
    Font(Res.font.fredoka_semibold, FontWeight.SemiBold),
    Font(Res.font.fredoka_bold, FontWeight.Bold)
)

/** Nunito — font isi. Membulat tapi tetap terbaca rapat pada ukuran kecil di kanvas pipeline. */
@Composable
fun rememberNunitoFamily(): FontFamily = FontFamily(
    Font(Res.font.nunito_regular, FontWeight.Normal),
    Font(Res.font.nunito_semibold, FontWeight.SemiBold),
    Font(Res.font.nunito_bold, FontWeight.Bold)
)

/**
 * Skala tipografi clay.
 *
 * Dua perubahan penting dari versi sebelumnya:
 *
 * 1. **`color` tidak lagi ditanam ke dalam `TextStyle`.** Sebelumnya enam dari tujuh peran
 *    mengunci warnanya di sini, yang membuat `LocalContentColor` tidak berfungsi — teks di atas
 *    kartu gelap tetap keluar berwarna slate. Warna kini diserahkan ke pemanggil.
 * 2. **Seluruh 15 peran Material diisi.** Sebelumnya hanya 7 yang didefinisikan, sehingga
 *    komponen M3 bawaan (`AlertDialog`, `DropdownMenu`, `OutlinedTextField`) jatuh ke default
 *    Roboto dan tidak akan pernah ikut memakai Nunito.
 */
@Composable
fun rememberClayTypography(): Typography {
    val heading = rememberFredokaFamily()
    val body = rememberNunitoFamily()

    return Typography(
        displayLarge = clayStyle(heading, fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold),
        displayMedium = clayStyle(heading, fontSize = 34.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
        displaySmall = clayStyle(heading, fontSize = 30.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold),
        headlineLarge = clayStyle(heading, fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
        headlineMedium = clayStyle(heading, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineSmall = clayStyle(heading, fontSize = 21.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleLarge = clayStyle(heading, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = clayStyle(heading, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
        titleSmall = clayStyle(heading, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = clayStyle(body, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal),
        bodyMedium = clayStyle(body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
        bodySmall = clayStyle(body, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal),
        labelLarge = clayStyle(body, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = clayStyle(body, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
        labelSmall = clayStyle(body, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold)
    )
}

private fun clayStyle(
    family: FontFamily,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    fontWeight: FontWeight
) = TextStyle(
    fontFamily = family,
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = fontWeight
)
