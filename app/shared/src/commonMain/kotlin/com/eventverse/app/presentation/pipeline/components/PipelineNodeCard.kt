package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
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

    // Outline membawa seluruh beban penandaan status: bentuknya sama, warnanya yang berbicara.
    // Ini menggantikan tiga ketebalan border berbeda (1/1.5/2.dp) yang sebelumnya dipakai untuk
    // membedakan state — pada bahasa neo-brutalist ketebalan outline harus konsisten.
    val outlineColor = when {
        isSelected -> WeMadeColors.Primary
        isBottleneck -> Color(node.healthStatus.badgeColorHex)
        isPresentationMode -> WeMadeColors.OutlineInverse
        else -> WeMadeColors.Outline
    }

    val cardBg = when {
        isPresentationMode && isSelected -> WeMadeColors.SurfaceDarkElevated
        isPresentationMode -> WeMadeColors.SurfaceDark
        isBottleneck -> Color(node.healthStatus.bgTintHex)
        else -> WeMadeColors.Surface
    }

    // Node yang di-bypass tidak "mengambang": bayangannya ikut hilang, bukan cuma diredupkan.
    val restOffset = if (isBypassed) ClayOffset.Small else ClayOffset.Rest

    ClayCard(
        modifier = modifier.alpha(cardAlpha),
        shape = ClayShapes.Card,
        containerColor = cardBg,
        outlineColor = outlineColor,
        // Bayangan tetap gelap meski outline berwarna status, supaya kartu bottleneck tidak
        // terlihat melayang lebih tinggi dari tetangganya hanya karena statusnya merah.
        shadowColor = if (isPresentationMode) WeMadeColors.BackgroundDark else WeMadeColors.Outline,
        offset = restOffset,
        // Kartu terpilih menetap di posisi tertekan — seleksi terbaca dari bentuk, bukan warna saja.
        selected = isSelected,
        contentPadding = PaddingValues(16.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header: Step Number, Stage, Health Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    // Label tahap yang panjang (mis. "RANTAI PASOK & BAHAN BAKU") harus menyerah
                    // duluan. Tanpa weight di sini, Row ini merebut lebar lebih dulu dan menyisakan
                    // ruang segelintir piksel untuk pil status, yang lalu memecah labelnya menjadi
                    // satu huruf per baris.
                    modifier = Modifier.weight(1f, fill = false),
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Text(
                        text = node.stage.displayName.substringAfter(". ").uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(node.stage.colorHex),
                        letterSpacing = 0.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Health Status Pill — tidak boleh menyusut; statusnya harus selalu terbaca utuh.
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
                    ClayTag(
                        text = node.assignedDepartment,
                        tint = Color(node.deptColorHex),
                        fontSize = 11.sp,
                        leading = {
                            IconUsers(modifier = Modifier.size(11.dp), color = Color(node.deptColorHex))
                        }
                    )
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
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                        else WeMadeColors.PrimaryContainer,
                        outline = if (isPresentationMode) WeMadeColors.OutlineInverse
                        else WeMadeColors.Primary.copy(alpha = 0.35f),
                        borderWidth = ClayBorder.Medium
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
                                fontSize = 11.sp,
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
                                ClayTag(
                                    text = "${node.automatedInputCount}",
                                    tint = WeMadeColors.Teal,
                                    leading = {
                                        IconZap(modifier = Modifier.size(9.dp), color = WeMadeColors.Teal)
                                    }
                                )
                            }

                            if (node.manualInputCount > 0) {
                                ClayTag(
                                    text = "${node.manualInputCount} Manual",
                                    tint = WeMadeColors.Accent,
                                    leading = {
                                        IconPerson(modifier = Modifier.size(10.dp), color = WeMadeColors.Accent)
                                    }
                                )
                            }

                            Text(
                                text = "Mapping ↗",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                        }
                    }

                    // Compact preview of input contract
                    Text(
                        text = node.inputContract,
                        fontSize = 11.sp,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface,
                        maxLines = 1
                    )
                }
            }

            // OUT Deliverable Box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = if (isPresentationMode) WeMadeColors.SurfaceDark
                        else WeMadeColors.SuccessBg,
                        outline = if (isPresentationMode) WeMadeColors.OutlineInverse
                        else WeMadeColors.Success.copy(alpha = 0.35f),
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 9.dp, vertical = 6.dp)
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
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Success
                        )
                    }
                    Text(
                        text = node.outputContract,
                        fontSize = 11.sp,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface,
                        maxLines = 1
                    )
                }
            }

            // Failure Feedback Loop Branching on QC Nodes (Always visible in unified flow)
            if (node.feedbackRoutes.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isPresentationMode) Color(0xFF3B0D0D) else WeMadeColors.ErrorBg,
                            outline = WeMadeColors.Error.copy(alpha = 0.45f),
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(horizontal = 9.dp, vertical = 7.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconWarning(
                                modifier = Modifier.size(11.dp),
                                color = Color(0xFFE11D48)
                            )
                            Text(
                                text = "KETIKA GAGAL QC (PUTUS MERAH):",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFE11D48)
                            )
                        }

                        node.feedbackRoutes.forEach { route ->
                            val isDefect = route.edgeType == com.eventverse.app.domain.pipeline.PipelineEdgeType.FEEDBACK_DEFECT
                            val routeColor = if (isDefect) Color(0xFFE11D48) else Color(0xFFD97706)
                            val bgChip = if (isPresentationMode) Color(0xFF450A0A) else Color.White

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clayFlat(
                                        shape = ClayShapes.Chip,
                                        background = bgChip,
                                        outline = routeColor.copy(alpha = 0.4f),
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Text(
                                        text = "⤶ ◀- -",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = routeColor
                                    )
                                    Column {
                                        Text(
                                            text = route.triggerReason,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = routeColor
                                        )
                                        Text(
                                            text = route.actionContract,
                                            fontSize = 10.sp,
                                            color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurfaceMuted,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                val inboundBadge = node.activeFeedbackBadge
                if (inboundBadge != null) {
                    // Inbound reception badge for upstream target nodes (e.g. Inventory or Sewing)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (isPresentationMode) Color(0xFF2A1515) else WeMadeColors.ErrorBg,
                                outline = WeMadeColors.Error.copy(alpha = 0.4f),
                                borderWidth = ClayBorder.Medium
                            )
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "📥 ◀╌╌",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                            Text(
                                text = inboundBadge,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFDC2626)
                            )
                        }
                    }
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
                    val wipTint = if (isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                    ClayTag(
                        text = if (isBypassed) "0 Pcs" else "${node.wipPieces} Pcs WIP",
                        tint = wipTint,
                        fontSize = 12.sp,
                        leading = { IconWip(modifier = Modifier.size(10.dp), color = wipTint) }
                    )

                    // Cycle Time Badge
                    Box(
                        modifier = Modifier
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                                else Color(0xFFF1F5F9),
                                outline = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.30f),
                                borderWidth = ClayBorder.Hairline
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
                                fontSize = 12.sp,
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
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                }
            }
        }
    }
}

/**
 * Kini tinggal membungkus [ClayBadge]. Sebelumnya ini salah satu dari tiga implementasi badge
 * yang nyaris identik namun berdiri sendiri-sendiri di tiga package berbeda.
 */
@Composable
fun HealthStatusPill(status: FlowHealthStatus, modifier: Modifier = Modifier) {
    ClayBadge(
        text = status.label,
        tint = Color(status.badgeColorHex),
        containerColor = Color(status.bgTintHex),
        dot = true,
        fontSize = 11.sp,
        modifier = modifier
    )
}
