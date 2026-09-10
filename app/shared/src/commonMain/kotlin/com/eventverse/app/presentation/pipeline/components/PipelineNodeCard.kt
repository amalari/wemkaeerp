package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun PipelineNodeCard(
    node: PipelineNode,
    isSelected: Boolean,
    isPresentationMode: Boolean,
    onClick: () -> Unit,
    onInspectInputs: ((PipelineNode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isBypassed = node.isBypassed
    val isBottleneck = node.isBottleneck
    val cardAlpha = if (isBypassed) 0.55f else 1.0f

    val borderStroke = when {
        isSelected -> BorderStroke(2.dp, WeMadeColors.Primary)
        isBottleneck -> BorderStroke(1.5.dp, Color(node.healthStatus.badgeColorHex))
        isPresentationMode -> BorderStroke(1.dp, Color(0xFF334155))
        else -> BorderStroke(1.dp, WeMadeColors.Border)
    }

    val cardBg = when {
        isPresentationMode && isSelected -> Color(0xFF1E293B)
        isPresentationMode -> Color(0xFF0F172A)
        isSelected -> Color(0xFFF8FAFC)
        isBottleneck -> Color(node.healthStatus.bgTintHex)
        else -> WeMadeColors.Surface
    }

    Card(
        modifier = modifier
            .alpha(cardAlpha)
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Step Number, Stage, Health Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(Color(node.stage.colorHex)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${node.stepNumber}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = node.stage.displayName.substringAfter(". ").uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(node.stage.colorHex),
                        letterSpacing = 0.5.sp
                    )
                }

                // Health Status Pill
                HealthStatusPill(status = node.healthStatus)
            }

            // Title & Module Category
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = node.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                )

                // Assigned Department Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(node.deptColorHex).copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconUsers(modifier = Modifier.size(11.dp), color = Color(node.deptColorHex))
                            Text(
                                text = node.assignedDepartment,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(node.deptColorHex)
                            )
                        }
                    }
                }
            }

            // Brief Description
            Text(
                text = node.description,
                fontSize = 12.sp,
                color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted,
                lineHeight = 16.sp,
                maxLines = 2
            )

            // n8n-Style Interactive Node Input Port Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (isPresentationMode) Color(0xFF1E293B)
                        else Color(0xFFEFF6FF)
                    )
                    .border(
                        1.dp,
                        if (isPresentationMode) Color(0xFF334155) else Color(0xFFBFDBFE),
                        RoundedCornerShape(8.dp)
                    )
                    .clickable {
                        if (onInspectInputs != null) {
                            onInspectInputs(node)
                        } else {
                            onClick()
                        }
                    }
                    .padding(horizontal = 9.dp, vertical = 7.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Port Socket Handle (n8n dot) + "IN" label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconNodePort(
                                modifier = Modifier.size(11.dp),
                                color = WeMadeColors.Primary
                            )
                            Text(
                                text = "IN",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = WeMadeColors.Primary
                            )
                        }

                        // Badges: Automated vs Manual indicators
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (node.automatedInputCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF0284C7).copy(alpha = 0.16f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        IconZap(modifier = Modifier.size(9.dp), color = Color(0xFF0284C7))
                                        Text(
                                            text = "${node.automatedInputCount}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF0284C7)
                                        )
                                    }
                                }
                            }

                            if (node.manualInputCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFFEA580C).copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                                    ) {
                                        IconPerson(modifier = Modifier.size(10.dp), color = Color(0xFFEA580C))
                                        Text(
                                            text = "${node.manualInputCount} Manual",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFEA580C)
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Mapping ↗",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                        }
                    }

                    // Compact preview of input contract
                    Text(
                        text = node.inputContract,
                        fontSize = 10.sp,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface,
                        maxLines = 1
                    )
                }
            }

            // OUT Deliverable Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isPresentationMode) Color(0xFF0F172A)
                        else Color(0xFFF0FDF4)
                    )
                    .border(
                        0.5.dp,
                        if (isPresentationMode) Color(0xFF334155) else Color(0xFFBBF7D0),
                        RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        IconOutlet(modifier = Modifier.size(10.dp), color = WeMadeColors.Success)
                        Text(
                            text = "OUT:",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Success
                        )
                    }
                    Text(
                        text = node.outputContract,
                        fontSize = 10.sp,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface,
                        maxLines = 1
                    )
                }
            }

            // Footer: Live Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // WIP Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isBottleneck) Color(0xFFFEF3C7)
                                else if (isPresentationMode) Color(0xFF334155)
                                else WeMadeColors.PrimaryContainer
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconWip(
                                modifier = Modifier.size(10.dp),
                                color = if (isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                            )
                            Text(
                                text = if (isBypassed) "0 Pcs" else "${node.wipPieces} Pcs WIP",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                            )
                        }
                    }

                    // Cycle Time Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isPresentationMode) Color(0xFF334155)
                                else Color(0xFFF1F5F9)
                            )
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (!isBypassed) {
                                IconClock(
                                    modifier = Modifier.size(10.dp),
                                    color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurfaceMuted
                                )
                            }
                            Text(
                                text = if (isBypassed) "Bypassed" else "${node.cycleTimeHours}h",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    IconSearch(modifier = Modifier.size(11.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Detail",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                }
            }
        }
    }
}

@Composable
fun HealthStatusPill(status: FlowHealthStatus, modifier: Modifier = Modifier) {
    val pillBg = Color(status.bgTintHex)
    val textColor = Color(status.badgeColorHex)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(pillBg)
            .padding(horizontal = 7.dp, vertical = 3.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = status.label,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}
