package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySketchBar
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kerangka layar kustom: sketsa **non-interaktif** (keputusan D6) — tanpa klik, aksi, maupun state.
 * Tata letak dihitung [planSketchRows] (fungsi murni); file ini hanya menggambar.
 */
@Composable
fun SkeletonSketch(rows: List<Map<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        planSketchRows(rows).forEach { line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                line.forEach { block ->
                    SketchBlockView(
                        block,
                        if (block.full) Modifier.fillMaxWidth() else Modifier.weight(1f)
                    )
                }
                // Separuh ganjil tetap setengah lebar, bukan melar penuh.
                if (line.size == 1 && !line[0].full) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SketchBlockView(block: SketchBlock, modifier: Modifier) {
    val bar = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.25f)
    val barSoft = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.12f)
    Column(
        modifier = modifier
            .background(WeMadeColors.SurfaceMuted.copy(alpha = 0.14f), ClayShapes.Tile)
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Text(
            text = block.label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        when (block.shape) {
            SketchShape.NEUTRAL -> Unit
            SketchShape.TABLE -> {
                ClaySketchBar(bar, height = ClaySpacing.Md)
                repeat(3) { ClaySketchBar(barSoft) }
            }
            SketchShape.FORM -> repeat(3) { i ->
                ClaySketchBar(bar, widthFraction = 0.35f, height = ClaySpacing.Sm)
                Row(
                    Modifier.fillMaxWidth()
                        .background(WeMadeColors.Surface, ClayShapes.Chip)
                        .padding(ClaySpacing.Md)
                ) { ClaySketchBar(barSoft, widthFraction = if (i == 1) 0.5f else 0.8f) }
            }
            SketchShape.METRIC_CARDS -> Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                repeat(3) {
                    Column(
                        Modifier.weight(1f)
                            .background(WeMadeColors.Surface, ClayShapes.Chip)
                            .padding(ClaySpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        ClaySketchBar(barSoft, widthFraction = 0.6f, height = ClaySpacing.Sm)
                        ClaySketchBar(bar, widthFraction = 0.9f, height = ClaySpacing.Lg)
                    }
                }
            }
            SketchShape.ACTIONS -> Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                repeat(2) {
                    Row(
                        Modifier.weight(1f)
                            .background(bar, ClayShapes.Button)
                            .padding(ClaySpacing.Md)
                    ) { ClaySketchBar(WeMadeColors.Surface, widthFraction = 0.7f, height = ClaySpacing.Sm) }
                }
            }
        }
    }
}
