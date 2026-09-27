package com.eventverse.app.presentation.operator

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.pendingRework
import com.eventverse.app.domain.sampling.reworkCount
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu SPK di meja operator. Kartu rework diberi outline merah dan catatan alasannya di atas —
 * operator harus tahu *apa* yang diperbaiki sebelum mengambilnya, bukan setelah.
 */
@Composable
fun OperatorDeskCard(
    order: SamplingOrder,
    statusLine: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    details: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val rework = order.pendingRework
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        outlineColor = if (rework != null) WeMadeColors.Error else WeMadeColors.Outline,
        contentPadding = PaddingValues(ClaySpacing.Lg),
        onClick = onClick
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayFlowRow(spacing = ClaySpacing.Sm) {
                Text(
                    text = order.spkNumber.value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
                ClayTag(text = "${order.sampleQuantity} Pcs", tint = WeMadeColors.Info)
                if (order.reworkCount > 0) {
                    ClayBadge(text = "Rework #${order.reworkCount}", tint = WeMadeColors.Error)
                }
            }
            Text(
                text = "${order.clientName} • ${order.styleName}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (rework != null) {
                Text(
                    text = "Dari ${rework.fromStage.deskLabel}: ${rework.reason.orEmpty()}",
                    fontSize = 12.sp,
                    color = WeMadeColors.Error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = WeMadeColors.ErrorBg,
                            outline = WeMadeColors.Error.copy(alpha = 0.45f),
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(ClaySpacing.Sm)
                )
            }
            if (statusLine != null) {
                Text(text = statusLine, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
            }
            details?.invoke()
            if (actions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}
