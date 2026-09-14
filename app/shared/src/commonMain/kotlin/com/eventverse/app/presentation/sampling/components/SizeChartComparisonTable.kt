package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SizeMeasurement
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun SizeChartComparisonTable(
    finishedSizes: List<SizeMeasurement>,
    rawKnitSizes: List<SizeMeasurement>,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PERBANDINGAN UKURAN GANDA",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "Ukuran Jadi vs Mesin Mentah",
                    tint = WeMadeColors.Primary
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
        ) {
            ClayCard(
                modifier = Modifier.widthIn(min = 600.dp),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(0.dp)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(WeMadeColors.Primary.copy(alpha = 0.08f))
                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TITIK UKUR",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        modifier = Modifier.weight(2.0f)
                    )
                    Text(
                        text = "UKURAN JADI (ACC)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WeMadeColors.Accent,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "UKURAN RAJUT MENTAH",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        modifier = Modifier.weight(1.5f)
                    )
                    Text(
                        text = "SELISIH SUSUT",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.weight(1.2f)
                    )
                }

                val fin = finishedSizes.firstOrNull() ?: SizeMeasurement()
                val raw = rawKnitSizes.firstOrNull() ?: SizeMeasurement()

                val rows = listOf(
                    Triple("Panjang Badan (P Badan)", fin.bodyLength, raw.bodyLength),
                    Triple("Lebar Badan (L Badan)", fin.bodyWidth, raw.bodyWidth),
                    Triple("Panjang Tangan (P Tangan)", fin.sleeveLength, raw.sleeveLength),
                    Triple("Arm Hole Badan", fin.armHole, raw.armHole),
                    Triple("Turun Kerah", fin.neckDrop, raw.neckDrop),
                    Triple("Bukaan Kerah", fin.neckWidth, raw.neckWidth),
                    Triple("Lebar Bahu", fin.shoulderWidth, raw.shoulderWidth),
                    Triple("Tinggi Kerah", fin.collarHeight, raw.collarHeight),
                    Triple("Rib Badan & Tangan", fin.ribHeight, raw.ribHeight),
                    Triple("Lebar Plaket", fin.placketWidth, raw.placketWidth),
                    Triple("Bukaan Tangan", fin.sleeveOpening, raw.sleeveOpening)
                )

                rows.forEachIndexed { index, (label, finVal, rawVal) ->
                    val diff = finVal - rawVal
                    val isEven = index % 2 == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isEven) WeMadeColors.Surface else WeMadeColors.SurfaceMuted.copy(alpha = 0.35f))
                            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurface,
                            modifier = Modifier.weight(2.0f)
                        )
                        Text(
                            text = if (finVal > 0.0) "${finVal.toString().removeSuffix(".0")} cm" else "-",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Accent,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = if (rawVal > 0.0) "${rawVal.toString().removeSuffix(".0")} cm" else "-",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.Primary,
                            modifier = Modifier.weight(1.5f)
                        )
                        Text(
                            text = if (finVal > 0.0 && rawVal > 0.0) {
                                val sign = if (diff > 0) "+${diff.toString().removeSuffix(".0")}" else diff.toString().removeSuffix(".0")
                                "$sign cm"
                            } else "-",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = if (diff > 0) WeMadeColors.Error else WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.weight(1.2f)
                        )
                    }
                }
            }
        }
    }
}
