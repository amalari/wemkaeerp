package com.eventverse.app.presentation.pipeline.components

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.ModuleFeatureRegistry
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun NodeInspectorDrawer(
    node: PipelineNode?,
    isPresentationMode: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Opens per-tenant renaming for this module. Null when the screen is showing preset
     * template data, where there is no persisted node to rename.
     */
    onRenameRequest: (() -> Unit)? = null,
    /** Kerangka tahap tenant — isi level 2 node Sampling. */
    stageFlow: List<StageDefinition> = emptyList(),
    /** Jumlah SPK per tahap dari telemetri nyata; kosong → tahap tanpa angka. */
    stageWip: Map<StageCode, Int> = emptyMap()
) {
    if (node == null) return

    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(420.dp)
            .claySurface(
                shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
                background = if (isPresentationMode) WeMadeColors.SurfaceDark else WeMadeColors.Surface,
                outline = if (isPresentationMode) WeMadeColors.OutlineInverse else WeMadeColors.Outline,
                // Drawer menempel di tepi kanan layar, jadi bayangannya diarahkan ke kiri.
                // Bayangan ke kanan akan jatuh ke luar viewport dan panel kehilangan kedalamannya.
                shadowX = -ClayOffset.Rest,
                shadowY = 0.dp,
                // Inner shade dimatikan: pada panel setinggi layar, gradasi di dasarnya jatuh
                // jauh dari isi dan hanya terbaca sebagai noda.
                innerShade = false
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
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

                    Column {
                        Text(
                            text = "INSPEKSI KONTRAK MODUL",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            letterSpacing = 0.5.sp
                        )
                        Text(
                            text = node.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (onRenameRequest != null && !isPresentationMode) {
                        ClayButton(
                            text = "Ubah Nama",
                            onClick = onRenameRequest,
                            style = ClayButtonStyle.Secondary,
                            fontSize = 12.sp,
                            offset = ClayOffset.Pressed,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    ClayIconButton(
                        onClick = onClose,
                        size = 30.dp,
                        containerColor = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                        else WeMadeColors.SurfaceMuted,
                        outlineColor = if (isPresentationMode) WeMadeColors.OutlineInverse
                        else WeMadeColors.Outline
                    ) {
                        IconClose(
                            modifier = Modifier.size(14.dp),
                            color = if (isPresentationMode) Color.White else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Health Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = Color(node.healthStatus.bgTintHex),
                        outline = Color(node.healthStatus.badgeColorHex).copy(alpha = 0.55f),
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(Color(node.healthStatus.badgeColorHex))
                )
                Column {
                    Text(
                        text = "Status: ${node.healthStatus.label}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(node.healthStatus.badgeColorHex)
                    )
                    Text(
                        text = node.healthMessage,
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurface
                    )
                }
            }

            // Description
            Text(
                text = node.description,
                fontSize = 13.sp,
                color = if (isPresentationMode) WeMadeColors.OnSurfaceInverse else WeMadeColors.OnSurface,
                lineHeight = 18.sp
            )

            HorizontalDivider(
                color = if (isPresentationMode) WeMadeColors.BorderInverse else WeMadeColors.Border
            )

            // Input Contract Section
            SectionBox(
                title = "Kontrak Data Masuk (Input Requirements)",
                badge = "Prasyarat Eksekusi",
                badgeColor = WeMadeColors.Primary,
                content = node.inputContract,
                explanation = "Data atau dokumen fisik yang wajib tersedia di sistem sebelum divisi terkait dapat memulai pengerjaan.",
                isPresentationMode = isPresentationMode,
                icon = { IconInlet(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary) }
            )

            // Output Contract Section
            SectionBox(
                title = "Kontrak Data Keluar (Output Deliverables)",
                badge = "Diteruskan ke Downstream",
                badgeColor = WeMadeColors.Success,
                content = node.outputContract,
                explanation = "Hasil keluaran tervalidasi yang secara otomatis ditransfer dan membuka kunci proses di tahapan berikutnya.",
                isPresentationMode = isPresentationMode,
                icon = { IconOutlet(modifier = Modifier.size(13.dp), color = WeMadeColors.Success) }
            )

            ModuleFeaturesSection(
                features = ModuleFeatureRegistry.forHost(node.module),
                stageFlow = stageFlow,
                stageWip = stageWip,
                stations = WorkStationCatalog.line()
            )

            HorizontalDivider(
                color = if (isPresentationMode) WeMadeColors.BorderInverse else WeMadeColors.Border
            )

            // Assigned Division & RBAC Scope
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconUsers(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Penanggung Jawab & Tata Kelola (RBAC)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                            else WeMadeColors.Background,
                            outline = if (isPresentationMode) WeMadeColors.OutlineInverse
                            else WeMadeColors.Border,
                            borderWidth = ClayBorder.Medium
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Divisi Pemilik:",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = node.assignedDepartment,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(node.deptColorHex)
                        )
                    }

                    ClayTag(
                        text = if (node.module.isGlobalOnly) "Seluruh Pabrik (Global)" else "Hirarkis (Multi-Scope)",
                        tint = if (node.module.isGlobalOnly) WeMadeColors.Success else WeMadeColors.Primary,
                        fontSize = 12.sp
                    )
                }
            }

            // Live Performance Metrics
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconHealth(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Metrik Operasional Real-time",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "WIP Sedang Antre",
                        value = if (node.isBypassed) "0 Pcs" else "${node.wipPieces} Pcs",
                        subtext = if (node.isBottleneck) "Di atas batas normal" else "Terkendali",
                        isPresentationMode = isPresentationMode,
                        icon = {
                            IconWip(
                                modifier = Modifier.size(12.dp),
                                color = if (node.isBottleneck) WeMadeColors.Warning else WeMadeColors.Primary
                            )
                        }
                    )

                    MetricBox(
                        modifier = Modifier.weight(1f),
                        title = "Rata-rata Waktu Siklus",
                        value = if (node.isBypassed) "0.0 Jam" else "${node.cycleTimeHours} Jam",
                        subtext = "Per batch produksi",
                        isPresentationMode = isPresentationMode,
                        icon = {
                            IconClock(
                                modifier = Modifier.size(12.dp),
                                color = WeMadeColors.Purple
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionBox(
    title: String,
    badge: String,
    badgeColor: Color,
    content: String,
    explanation: String,
    isPresentationMode: Boolean,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                else WeMadeColors.Background,
                outline = if (isPresentationMode) WeMadeColors.OutlineInverse else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                if (icon != null) {
                    icon()
                }
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                )
            }

            ClayTag(text = badge, tint = badgeColor, fontSize = 11.sp)
        }

        Text(
            text = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPresentationMode) WeMadeColors.PrimaryInverse else WeMadeColors.Primary
        )

        Text(
            text = explanation,
            fontSize = 12.sp,
            color = if (isPresentationMode) WeMadeColors.OnSurfaceMutedInverse else WeMadeColors.OnSurfaceMuted,
            lineHeight = 15.sp
        )
    }
}

@Composable
private fun MetricBox(
    title: String,
    value: String,
    subtext: String,
    isPresentationMode: Boolean,
    icon: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (isPresentationMode) WeMadeColors.SurfaceDarkElevated
                else WeMadeColors.SurfaceMuted,
                outline = if (isPresentationMode) WeMadeColors.OutlineInverse else WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                icon()
            }
            Text(
                text = title,
                fontSize = 12.sp,
                color = if (isPresentationMode) WeMadeColors.OnSurfaceMutedInverse else WeMadeColors.OnSurfaceMuted
            )
        }
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
        )
        Text(
            text = subtext,
            fontSize = 11.sp,
            color = if (isPresentationMode) WeMadeColors.OnSurfaceInverse else WeMadeColors.OnSurfaceMuted
        )
    }
}
