package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Jarak blur untuk permukaan kaca (glassmorphism, TRD-PLAT-013 §6: 20-40px).
 *
 * Blur adalah satu-satunya "ketebalan" di bahasa kaca — menggantikan [ClayOffset] yang penuh
 * makna di bahasa clay. Sengaja dikumpulkan di sini supaya panel kaca berikutnya mewarisi nilai
 * yang sama alih-alih menebak ulang.
 */
object GlassTokens {
    /** Blur standar untuk panel kaca yang menonjol (dialog, pane). */
    val Blur: Dp = 28.dp

    /** Blur lembut untuk elemen kaca kecil (chip, gelembung chat), agar teks tetap tajam. */
    val BlurSoft: Dp = 20.dp

    /** Tanpa blur — jalur aman untuk permukaan yang memuat teks yang harus tetap tajam. */
    val BlurNone: Dp = 0.dp
}

/**
 * Permukaan kaca (glassmorphism): isian semi-transparan + rim tipis terang + kilau di tepi atas.
 *
 * Ini **pasangan** [claySurface], bukan variannya. Perbedaan yang disengaja dan tidak boleh
 * ditukar:
 *
 * - **Tidak ada hard shadow.** Kaca tidak punya bayangan solid; kedalamannya datang dari
 *   translusensi dan rim terang. Karena itu tidak ada langkah `padding` reservasi bayangan
 *   seperti pada [claySurface], dan [background] **boleh** semi-transparan (kebalikan dari
 *   aturan opaque [ClayCard]).
 * - **Border, bukan outline.** Rim kaca setipis [ClayBorder.Hairline] berwarna terang; memberi
 *   outline gelap [ClayBorder.Thick] akan membuatnya terbaca sebagai clay, bukan kaca.
 *
 * **Catatan blur (asumsi).** `Modifier.blur` di Compose Multiplatform memburamkan **isi node
 * itu sendiri** (termasuk teks/anak di dalamnya), bukan latar di belakangnya — API backdrop
 * filter lintas-target belum tersedia. Karena itu [blurRadius] default [GlassTokens.BlurNone]:
 * teks tetap tajam, dan efek kaca murni datang dari translusensi + rim + kilau. Set [blurRadius]
 * hanya untuk lapisan dekoratif (glow/aurora) yang tidak memuat teks.
 *
 * Urutan modifier penting, sama seperti [claySurface]:
 * 1. blur (opsional, paling luar) → 2. clip(shape) → 3. background → 4. kilau (drawBehind) →
 * 5. border rim paling akhir supaya duduk di atas isian dan konten.
 *
 * @param shape bentuk panel kaca (gunakan token [ClayShapes], mis. [ClayShapes.ChatBubbleIncoming]).
 * @param background isian semi-transparan (mis. [WeMadeColors.GlassSurface]).
 * @param outline rim tipis terang (mis. [WeMadeColors.GlassBorder]).
 * @param blurRadius jarak blur; [GlassTokens.BlurNone] = tanpa blur (aman untuk teks).
 * @param borderWidth ketebalan rim; hemat [ClayBorder.Hairline] agar tetap tipis.
 * @param sheen true = menggambar kilau gradien di tepi atas, sumber kesan "mengkilap".
 */
fun Modifier.glassSurface(
    shape: Shape = ClayShapes.Panel,
    background: Color = WeMadeColors.GlassSurface,
    outline: Color = WeMadeColors.GlassBorder,
    blurRadius: Dp = GlassTokens.BlurNone,
    borderWidth: Dp = ClayBorder.Hairline,
    sheen: Boolean = true
): Modifier {
    val blurred = if (blurRadius > GlassTokens.BlurNone) {
        blur(blurRadius, BlurredEdgeTreatment.Rectangle)
    } else {
        this
    }
    return blurred
        .clip(shape)
        .background(background)
        // Kilau atas: cahaya yang "memantul" di permukaan kaca. Digambar setelah background
        // tapi sebelum konten, sehingga memudar ke transparan dan tidak menutupi teks.
        .drawBehind {
            if (sheen) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to WeMadeColors.GlassSheen.copy(alpha = 0.30f),
                        0.45f to Color.Transparent
                    )
                )
            }
        }
        .border(borderWidth, outline, shape)
}
