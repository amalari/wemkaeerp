package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.PipelineInputPort
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Enterprise n8n-style Node Input Inspector Modal Dialog.
 *
 * Provides granular operational clarity by decomposing a module's inputs into:
 * 1. Automated Streams: Inputs piped directly from upstream modules without human delay.
 * 2. Manual Operator Inputs: Human checkpoints requiring staff intervention (signified by person icon).
 * 3. End-to-end mapping from upstream output contracts to this node's inputs.
 */
@Composable
fun NodeInputInspectorModal(
    node: PipelineNode,
    isPresentationMode: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    val surfaceBg = if (isPresentationMode) Color(0xFF0F172A) else Color.White
    val cardBg = if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFF8FAFC)
    val textPrimary = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
    val textMuted = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
    val borderColor = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border

    // Semi-transparent backdrop overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.65f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.88f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {} // Consume click so backdrop doesn't close dialog
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = surfaceBg),
            border = BorderStroke(1.5.dp, if (isPresentationMode) Color(0xFF475569) else WeMadeColors.Primary.copy(alpha = 0.4f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                // Modal Header: Step, Module Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(node.stage.colorHex)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${node.stepNumber}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "INSPEKSI KONTRAK INPUT & MAPPING (NODE)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary,
                                    letterSpacing = 0.6.sp
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(node.deptColorHex).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = node.assignedDepartment,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(node.deptColorHex)
                                    )
                                }
                            }

                            Text(
                                text = node.title,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = textPrimary
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(cardBg)
                    ) {
                        IconClose(
                            modifier = Modifier.size(14.dp),
                            color = textMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Visual n8n Flow Bridge Canvas Banner
                    NodeFlowVisualBanner(
                        node = node,
                        cardBg = cardBg,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textMuted = textMuted,
                        isPresentationMode = isPresentationMode
                    )

                    // Quick Stat Badges Ribbon
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Total Inputs Stat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(cardBg)
                                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column {
                                Text("Total Input Masuk", fontSize = 11.sp, color = textMuted)
                                Text(
                                    text = "${node.inputs.size} Prasyarat",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textPrimary
                                )
                            }
                        }

                        // Automated Stream Stat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0284C7).copy(alpha = if (isPresentationMode) 0.2f else 0.08f))
                                .border(1.dp, Color(0xFF0284C7).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconZap(modifier = Modifier.size(16.dp), color = Color(0xFF0284C7))
                                Column {
                                    Text("Aliran Otomatis", fontSize = 11.sp, color = Color(0xFF0284C7))
                                    Text(
                                        text = "${node.automatedInputCount} Dari Hulu",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPresentationMode) Color.White else Color(0xFF0369A1)
                                    )
                                }
                            }
                        }

                        // Manual Operator Stat
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEA580C).copy(alpha = if (isPresentationMode) 0.2f else 0.08f))
                                .border(1.dp, Color(0xFFEA580C).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                IconPerson(modifier = Modifier.size(16.dp), color = Color(0xFFEA580C))
                                Column {
                                    Text("Input Manual (Operator)", fontSize = 11.sp, color = Color(0xFFEA580C))
                                    Text(
                                        text = "${node.manualInputCount} Oleh Manusia",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isPresentationMode) Color.White else Color(0xFFC2410C)
                                    )
                                }
                            }
                        }
                    }

                    // Section 1: Automated Inputs Piped from Upstream
                    if (node.automatedInputs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconZap(modifier = Modifier.size(14.dp), color = Color(0xFF0284C7))
                                Text(
                                    text = "1. Alur Input Otomatis (Piped dari Output Modul Hulu)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${node.automatedInputs.size} Terkoneksi",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF0284C7)
                                    )
                                }
                            }

                            Text(
                                text = "Data ini mengalir secara otomatis melalui sistem begitu modul hulu menyelesaikan eksekusinya tanpa membutuhkan intervensi manual staf.",
                                fontSize = 11.sp,
                                color = textMuted
                            )

                            node.automatedInputs.forEach { inputPort ->
                                AutomatedInputCard(
                                    port = inputPort,
                                    cardBg = cardBg,
                                    borderColor = borderColor,
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isPresentationMode = isPresentationMode
                                )
                            }
                        }
                    }

                    // Section 2: Manual Operator Inputs
                    if (node.manualInputs.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                IconPerson(modifier = Modifier.size(15.dp), color = Color(0xFFEA580C))
                                Text(
                                    text = "2. Input Manual oleh Operator (Intervensi Manusia)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPresentationMode) Color.White else WeMadeColors.OnSurface
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFFEA580C).copy(alpha = 0.15f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "${node.manualInputs.size} Perlu Diisi",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEA580C)
                                    )
                                }
                            }

                            Text(
                                text = "Prasyarat operasional yang wajib dientri, diunggah, atau diverifikasi secara manual oleh staf fisik pabrik sebelum modul dapat beroperasi.",
                                fontSize = 11.sp,
                                color = textMuted
                            )

                            node.manualInputs.forEach { inputPort ->
                                ManualInputCard(
                                    port = inputPort,
                                    cardBg = cardBg,
                                    borderColor = borderColor,
                                    textPrimary = textPrimary,
                                    textMuted = textMuted,
                                    isPresentationMode = isPresentationMode
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Footer: Close Button & Info
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(cardBg)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconNodePort(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                        Text(
                            text = "Kontrak data tervalidasi sesuai standar arsitektur WeMade ERP.",
                            fontSize = 11.sp,
                            color = textMuted
                        )
                    }

                    Button(
                        onClick = onClose,
                        colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Tutup Dialog", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}

/**
 * Miniature visual node canvas banner depicting an n8n connection pipeline.
 */
@Composable
private fun NodeFlowVisualBanner(
    node: PipelineNode,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textMuted: Color,
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPresentationMode) Color(0xFF090E1A) else Color(0xFFF1F5F9))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconNodePort(modifier = Modifier.size(12.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "DIAGRAM ALUR PIPELINE STREAM (N8N STYLE)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Live Graph Connector",
                    fontSize = 10.sp,
                    color = textMuted
                )
            }

            // Connection Visual Nodes Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left Source: Upstream Node (if any) or External Inception
                val upstreamName = node.automatedInputs.firstOrNull()?.sourceModuleName ?: "Klien Eksternal / Inception"
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(cardBg)
                        .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "MODUL HULU (OUTPUT)",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = textMuted
                        )
                        Text(
                            text = upstreamName,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = textPrimary,
                            maxLines = 1
                        )
                        Text(
                            text = "Data Stream Out",
                            fontSize = 10.sp,
                            color = WeMadeColors.Success
                        )
                    }
                }

                // Middle: Cable Connection & Flow Indicator
                Row(
                    modifier = Modifier
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.Success)
                    )
                    IconArrowRightFlow(
                        modifier = Modifier.size(28.dp),
                        color = WeMadeColors.Primary
                    )
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.Primary)
                    )
                }

                // Right Target: This Current Node
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(WeMadeColors.Primary.copy(alpha = if (isPresentationMode) 0.25f else 0.12f))
                        .border(1.5.dp, WeMadeColors.Primary, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(WeMadeColors.Primary)
                            )
                            Text(
                                text = "NODE TARGET (INPUT)",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                        }
                        Text(
                            text = node.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isPresentationMode) Color.White else WeMadeColors.Primary,
                            maxLines = 1
                        )
                        Text(
                            text = "Menunggu Kontrak Input",
                            fontSize = 10.sp,
                            color = textMuted
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card rendering an individual automated input piped from an upstream module.
 */
@Composable
private fun AutomatedInputCard(
    port: PipelineInputPort,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textMuted: Color,
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconZap(modifier = Modifier.size(13.dp), color = Color(0xFF0284C7))
                    Text(
                        text = port.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF0284C7).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Otomatis (Zero Latency)",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0284C7)
                    )
                }
            }

            if (port.description.isNotBlank()) {
                Text(
                    text = port.description,
                    fontSize = 11.sp,
                    color = textMuted
                )
            }

            // Mapping Details Box: Source Module -> Output Field
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPresentationMode) Color(0xFF090E1A) else Color.White)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "MAPPING SUMBER DATA (UPSTREAM):",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Modul Asal:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted
                        )
                        Text(
                            text = port.sourceModuleName ?: "Modul Hulu",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Output Field:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted
                        )
                        Text(
                            text = port.sourceOutputContract ?: "Data Output Terverifikasi",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.Success
                        )
                    }
                }
            }
        }
    }
}

/**
 * Card rendering an individual manual input entered by a human operator in the factory.
 */
@Composable
private fun ManualInputCard(
    port: PipelineInputPort,
    cardBg: Color,
    borderColor: Color,
    textPrimary: Color,
    textMuted: Color,
    isPresentationMode: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = BorderStroke(1.dp, Color(0xFFEA580C).copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IconPerson(modifier = Modifier.size(14.dp), color = Color(0xFFEA580C))
                    Text(
                        text = port.name,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFFEA580C).copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "Input Operator",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFEA580C)
                    )
                }
            }

            if (port.description.isNotBlank()) {
                Text(
                    text = port.description,
                    fontSize = 11.sp,
                    color = textMuted
                )
            }

            // Operator Details Box: Role + Input Method
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPresentationMode) Color(0xFF090E1A) else Color.White)
                    .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "TATA KELOLA ENTRI MANUAL:",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = textMuted,
                        letterSpacing = 0.5.sp
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Penanggung Jawab:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFEA580C).copy(alpha = 0.12f))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = port.operatorRole ?: "Staf Terkait",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFEA580C)
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Metode Input:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = textMuted
                        )
                        Text(
                            text = port.inputMethod ?: "Form Input Digital",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary
                        )
                    }
                }
            }
        }
    }
}
