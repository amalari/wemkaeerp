package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import org.jetbrains.compose.resources.painterResource

/**
 * `DataFlowPane` (plan §4): hint port informasional — data apa yang masuk dan keluar tiap modul
 * aktif, sesuai definisi slot pack.
 */
@Composable
fun DataFlowPane(
    draft: DiscoveryDraftUi,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        draft.activeModules.forEach { module ->
            val style = resolveSectionStyle(module.section, draft)
            val iconRenderer = resolveModuleIcon(module)
            val clayRes = resolveClayAsset(module)

            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(style.background, ClayShapes.Tile),
                        contentAlignment = Alignment.Center
                    ) {
                        if (clayRes != null && module.active) {
                            Image(
                                painter = painterResource(clayRes),
                                contentDescription = module.displayName,
                                modifier = Modifier.size(34.dp)
                            )
                        } else {
                            iconRenderer(Modifier.size(18.dp), style.color)
                        }
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = module.displayName,
                            style = typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Masuk: ${module.slotInput ?: "—"}  •  Keluar: ${module.slotOutput ?: "—"}",
                            style = typography.bodySmall,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    ClayBadge(
                        text = module.slot ?: module.section,
                        tint = style.color,
                        modifier = Modifier.padding(start = ClaySpacing.Sm)
                    )
                }
            }
        }
    }
}
