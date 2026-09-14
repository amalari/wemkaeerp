package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.FeederEntry
import com.eventverse.app.domain.sampling.PatternFormulas
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun FeederSequenceBar(
    feeders: List<FeederEntry>,
    formulas: PatternFormulas,
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
                    text = "INSTRUKSI PANAH / FEEDER BENANG",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "7 Carrier",
                    tint = WeMadeColors.Accent
                )
            }

            Text(
                text = "P: ${formulas.bodyLengthK} K | L: ${formulas.bodyWidthN} N | Rib: ${formulas.ribK} K",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            feeders.forEach { feeder ->
                ClayCard(
                    modifier = Modifier.width(130.dp),
                    shape = ClayShapes.Tile,
                    contentPadding = PaddingValues(ClaySpacing.Sm),
                    containerColor = if (feeder.feederNumber % 2 == 1) WeMadeColors.Surface else WeMadeColors.SurfaceMuted
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ClayBadge(
                            text = "#${feeder.feederNumber}",
                            tint = if (feeder.feederNumber <= 2) WeMadeColors.Primary else WeMadeColors.Accent,
                            fontSize = 10.sp
                        )
                        Text(
                            text = feeder.ply,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))

                    Text(
                        text = feeder.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1
                    )

                    Text(
                        text = "Warna: ${feeder.color}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
