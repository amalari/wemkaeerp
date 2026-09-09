package com.eventverse.app.presentation.pipeline.components

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
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun NodeInspectorDrawer(
    node: PipelineNode?,
    isPresentationMode: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (node == null) return

    val scrollState = rememberScrollState()

    Card(
        modifier = modifier
            .fillMaxHeight()
            .width(420.dp),
        shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresentationMode) Color(0xFF0F172A) else WeMadeColors.Surface
        ),
        border = BorderStroke(
            1.dp,
            if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
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
                            fontSize = 10.sp,
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

                IconButton(onClick = onClose) {
                    Text(
                        text = "✕",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPresentationMode) Color.White else WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            // Health Status Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(node.healthStatus.bgTintHex))
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
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurface
                    )
                }
            }

            // Description
            Text(
                text = node.description,
                fontSize = 13.sp,
                color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface,
                lineHeight = 18.sp
            )

            HorizontalDivider(
                color = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
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

            HorizontalDivider(
                color = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
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
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (isPresentationMode) Color(0xFF1E293B)
                            else Color(0xFFF8FAFC)
                        )
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Divisi Pemilik:",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = node.assignedDepartment,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(node.deptColorHex)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (node.module.isGlobalOnly) WeMadeColors.SuccessBg
                                else WeMadeColors.PrimaryContainer
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = if (node.module.isGlobalOnly) "Seluruh Pabrik (Global)" else "Hirarkis (Multi-Scope)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (node.module.isGlobalOnly) WeMadeColors.Success else WeMadeColors.Primary
                        )
                    }
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFF8FAFC))
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

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(badgeColor.copy(alpha = 0.15f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor
                )
            }
        }

        Text(
            text = content,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPresentationMode) Color(0xFF93C5FD) else WeMadeColors.Primary
        )

        Text(
            text = explanation,
            fontSize = 11.sp,
            color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted,
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
            .clip(RoundedCornerShape(8.dp))
            .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFF1F5F9))
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
                fontSize = 11.sp,
                color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
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
            fontSize = 10.sp,
            color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurfaceMuted
        )
    }
}
