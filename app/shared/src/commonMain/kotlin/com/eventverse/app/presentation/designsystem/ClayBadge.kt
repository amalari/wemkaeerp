package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Badge/pill tunggal yang menggantikan tiga implementasi nyaris identik yang sebelumnya berdiri
 * sendiri-sendiri: `HealthStatusPill` (PipelineNodeCard), `Badge` (TenantModulePanel), dan
 * `ModuleCategoryBadge` (ModuleMatrixCard).
 *
 * Warna diturunkan dari satu [tint]: isian adalah tint yang sangat diredam, outline adalah tint
 * pekat. Jadi badge status produksi (hijau/amber/merah) tetap terbaca sebagai sinyal dan tidak
 * ikut hanyut menjadi dekorasi.
 *
 * @param dot titik padat di depan label — dipakai untuk status kesehatan node.
 * @param containerColor isian khusus; default-nya diturunkan dari [tint].
 */
@Composable
fun ClayBadge(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    dot: Boolean = false,
    fontSize: TextUnit = 11.sp,
    containerColor: Color = tint.copy(alpha = 0.14f),
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = containerColor,
                outline = tint.copy(alpha = 0.55f),
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (dot) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(tint, CircleShape)
            )
        }
        leading?.invoke()
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 1,
            softWrap = false
        )
    }
}

/**
 * Varian persegi bersudut untuk label yang panjang atau berdempetan dengan teks, di mana bentuk
 * pill akan makan terlalu banyak lebar horizontal.
 */
@Composable
fun ClayTag(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 10.sp,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = tint.copy(alpha = 0.12f),
                outline = tint.copy(alpha = 0.45f),
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        leading?.invoke()
        Text(
            text = text,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}
