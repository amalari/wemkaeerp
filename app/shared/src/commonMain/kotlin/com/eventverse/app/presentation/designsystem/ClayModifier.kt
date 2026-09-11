package com.eventverse.app.presentation.designsystem

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Permukaan clay: outline tebal + hard shadow + inner bottom shade.
 *
 * Compose tidak punya padanan langsung untuk `box-shadow: 6px 6px 0 <color>`. `Modifier.shadow`
 * selalu menghasilkan bayangan ber-blur yang mengikuti kurva elevation Material, sedangkan yang
 * dibutuhkan di sini justru kebalikannya: bidang solid tanpa blur sama sekali. Jadi bayangannya
 * digambar sendiri sebagai `drawOutline` dari bentuk yang sama, digeser sejauh `offset`.
 *
 * Urutan modifier di bawah ini penting dan tidak boleh ditukar — lihat komentar per langkah.
 *
 * Soal animasi tekan: yang bergerak adalah kartunya, bukan bayangannya. Jarak bayangan relatif
 * terhadap kartu menyempit dari [ClayOffset.Rest] ke [ClayOffset.Pressed], sementara kartu maju
 * sebesar selisihnya. Hasilnya posisi absolut bayangan tetap dan kartu terlihat "masuk" ke
 * dalamnya — persis efek `transform: translate(2px,2px)` + `box-shadow: 4px 4px 0` pada demo.
 *
 * @param offset jarak bayangan saat diam pada kedua sumbu.
 * @param shadowX arah & jarak horizontal bayangan. **Boleh negatif** untuk mengarahkan bayangan
 *   ke kiri — dipakai panel yang menempel di tepi kanan layar, di mana bayangan ke kanan akan
 *   jatuh ke luar layar dan hilang.
 * @param shadowY arah & jarak vertikal bayangan; boleh negatif untuk mengarah ke atas.
 * @param pressed true saat ditekan atau terpilih.
 * @param innerShade gradasi gelap tipis di dasar kartu — sumber kesan "empuk" khas clay.
 */
fun Modifier.claySurface(
    shape: Shape,
    background: Color,
    outline: Color,
    shadowColor: Color = outline,
    offset: Dp = ClayOffset.Rest,
    shadowX: Dp = offset,
    shadowY: Dp = offset,
    pressed: Boolean = false,
    borderWidth: Dp = ClayBorder.Thick,
    innerShade: Boolean = true
): Modifier = composed {
    // Jarak saat ditekan mempertahankan arah (tanda) masing-masing sumbu, hanya mengecil.
    val restingX = shadowX
    val restingY = shadowY
    val pressedX = if (shadowX < 0.dp) -ClayOffset.Pressed else ClayOffset.Pressed
    val pressedY = if (shadowY < 0.dp) -ClayOffset.Pressed else ClayOffset.Pressed

    val gapX by animateDpAsState(
        targetValue = if (pressed) pressedX else restingX,
        animationSpec = tween(durationMillis = 120),
        label = "clayShadowGapX"
    )
    val gapY by animateDpAsState(
        targetValue = if (pressed) pressedY else restingY,
        animationSpec = tween(durationMillis = 120),
        label = "clayShadowGapY"
    )

    this
        // 1. Sisihkan ruang untuk bayangan, di sisi yang sesuai arahnya. Tanpa ini bayangan
        //    menimpa elemen tetangga di dalam Row/Column yang rapat, karena drawBehind tidak
        //    dibatasi oleh bounds elemen.
        .padding(
            start = if (shadowX < 0.dp) -shadowX else 0.dp,
            end = if (shadowX > 0.dp) shadowX else 0.dp,
            top = if (shadowY < 0.dp) -shadowY else 0.dp,
            bottom = if (shadowY > 0.dp) shadowY else 0.dp
        )
        // 2. Geser kartunya saat ditekan. offset(...) hanya memindahkan penempatan, bukan
        //    pengukuran, jadi footprint layout tetap sama dan tidak ada reflow saat animasi.
        .offset(x = shadowX - gapX, y = shadowY - gapY)
        // 3. Hard shadow. Harus sebelum clip() agar tidak ikut terpotong.
        .drawBehind {
            val silhouette = shape.createOutline(size, layoutDirection, this)
            translate(left = gapX.toPx(), top = gapY.toPx()) {
                drawOutline(outline = silhouette, color = shadowColor)
            }
        }
        // 4. Mulai dari sini semuanya dipotong mengikuti bentuk kartu.
        .clip(shape)
        .background(background)
        // 5. Inner bottom shade. Diletakkan setelah background tapi sebelum konten, sehingga
        //    menggelapkan dasar kartu tanpa menutupi teks di atasnya.
        .drawBehind {
            if (innerShade) {
                drawRect(
                    brush = Brush.verticalGradient(
                        0.88f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.10f)
                    )
                )
            }
        }
        // 6. Outline digambar paling akhir supaya duduk di atas background dan konten.
        .border(borderWidth, outline, shape)
}

/**
 * Varian rata tanpa bayangan, untuk elemen yang menempel pada latar (chip, tile ikon, header
 * swimlane). Tetap memakai outline tebal supaya sekeluarga dengan [claySurface].
 */
fun Modifier.clayFlat(
    shape: Shape,
    background: Color,
    outline: Color,
    borderWidth: Dp = ClayBorder.Medium
): Modifier = this
    .clip(shape)
    .background(background)
    .border(borderWidth, outline, shape)
