package com.eventverse.app.presentation.orgchart.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.orgchart.HierarchyLevel
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun OrgNodeCard(
    node: OrgNode,
    isHighlighted: Boolean = false,
    badgeLabel: String? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val deptColor = Color(node.department.colorHex)

    val cardBorder = if (isHighlighted) {
        BorderStroke(2.dp, WeMadeColors.Accent)
    } else {
        BorderStroke(1.dp, WeMadeColors.Border)
    }

    val cardBg = if (isHighlighted) {
        WeMadeColors.AccentLight
    } else {
        WeMadeColors.Surface
    }

    Card(
        modifier = modifier
            .width(220.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = cardBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isHighlighted) 4.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Optional Badge Header (e.g. "POSISI BARU" or "ATASAN ANDA")
            if (badgeLabel != null || isHighlighted) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isHighlighted) WeMadeColors.Accent else Color(0xFFE2E8F0))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = badgeLabel ?: "POSISI SEDANG DIEDIT",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighlighted) Color.White else Color(0xFF475569),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // Avatar & Level Tag
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(deptColor.copy(alpha = 0.15f))
                        .border(1.5.dp, deptColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = node.avatarInitial,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = deptColor
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
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

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = WeMadeColors.Border.copy(alpha = 0.6f), thickness = 0.5.dp)
            Spacer(modifier = Modifier.height(6.dp))

            // Footer info: Department & Level indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(deptColor.copy(alpha = 0.1f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = node.department.shortName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = deptColor
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            when (node.level) {
                                HierarchyLevel.EXECUTIVE -> Color(0xFF6366F1).copy(alpha = 0.12f)
                                HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFF59E0B).copy(alpha = 0.12f)
                                HierarchyLevel.STAFF_OPERATOR -> Color(0xFF64748B).copy(alpha = 0.12f)
                            }
                        )
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = node.level.shortLabel.uppercase(),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (node.level) {
                            HierarchyLevel.EXECUTIVE -> Color(0xFF4338CA)
                            HierarchyLevel.HEAD_OF_DEPARTMENT -> Color(0xFFB45309)
                            HierarchyLevel.STAFF_OPERATOR -> Color(0xFF475569)
                        }
                    )
                }
            }
        }
    }
}
