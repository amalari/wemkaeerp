package com.eventverse.app.presentation.orgchart.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun OrgNodeCard(
    node: OrgNode,
    isHighlighted: Boolean = false,
    badgeLabel: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val deptColor = node.department?.let { Color(it.colorHex) } ?: WeMadeColors.Primary

    val levelTint = when (node.level) {
        HierarchyLevel.EXECUTIVE -> WeMadeColors.Primary
        HierarchyLevel.HEAD_OF_DEPARTMENT -> WeMadeColors.Warning
        HierarchyLevel.TEAM_LEAD -> WeMadeColors.Success
        HierarchyLevel.STAFF_OPERATOR -> WeMadeColors.OnSurfaceMuted
    }

    ClayCard(
        modifier = modifier.width(236.dp),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = if (isHighlighted) deptColor else WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = if (isHighlighted) ClayOffset.Rest else ClayOffset.Small,
        borderWidth = if (isHighlighted) 3.5.dp else ClayBorder.Thick,
        selected = false,
        contentPadding = PaddingValues(ClaySpacing.Lg),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional Badge Header (e.g. "POSISI BARU" or "ATASAN ANDA")
            if (badgeLabel != null || isHighlighted) {
                ClayTag(
                    text = badgeLabel ?: "POSISI SEDANG DIEDIT",
                    tint = if (isHighlighted) deptColor else WeMadeColors.OnSurfaceMuted,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(ClaySpacing.Sm))
            }

            // Avatar & Level Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clayFlat(
                            shape = CircleShape,
                            background = deptColor.copy(alpha = 0.15f),
                            outline = deptColor,
                            borderWidth = ClayBorder.Medium
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.avatarInitial,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = deptColor
                    )
                }

                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = node.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = node.roleTitle,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))
            HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            // Footer info: Department & Level indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTag(
                    text = node.department?.shortName ?: "DIREKSI",
                    tint = deptColor,
                    fontSize = 10.sp
                )

                ClayTag(
                    text = node.level.shortLabel.uppercase(),
                    tint = levelTint,
                    fontSize = 10.sp
                )
            }
        }
    }
}
