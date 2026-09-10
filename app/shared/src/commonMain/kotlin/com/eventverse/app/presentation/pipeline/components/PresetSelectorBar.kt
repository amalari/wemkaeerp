package com.eventverse.app.presentation.pipeline.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.PipelineSimulationScenario
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun PresetSelectorBar(
    selectedPreset: GarmentBusinessPreset,
    isPresentationMode: Boolean,
    isSimulating: Boolean,
    activeScenario: PipelineSimulationScenario = PipelineSimulationScenario.NORMAL,
    onSelectPreset: (GarmentBusinessPreset) -> Unit,
    onSelectScenario: (PipelineSimulationScenario) -> Unit = {},
    onTogglePresentationMode: () -> Unit,
    onToggleSimulation: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPresentationMode) Color(0xFF0F172A) else WeMadeColors.Surface
        ),
        border = BorderStroke(
            1.dp,
            if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Business Preset Pills + Mode Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Business Preset Pills
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Model Bisnis:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                    )

                    GarmentBusinessPreset.entries.forEach { preset ->
                        val isSelected = preset == selectedPreset
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    when {
                                        isSelected && isPresentationMode -> WeMadeColors.Primary
                                        isSelected -> WeMadeColors.PrimaryContainer
                                        isPresentationMode -> Color(0xFF1E293B)
                                        else -> Color(0xFFF1F5F9)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) WeMadeColors.Primary else Color.Transparent,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onSelectPreset(preset) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.Start,
                                verticalArrangement = Arrangement.spacedBy(1.dp)
                            ) {
                                Text(
                                    text = preset.shortBadge,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = when {
                                        isSelected && isPresentationMode -> Color.White
                                        isSelected -> WeMadeColors.Primary
                                        isPresentationMode -> Color(0xFFCBD5E1)
                                        else -> WeMadeColors.OnSurface
                                    }
                                )
                                Text(
                                    text = preset.exampleCompanyName,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = when {
                                        isSelected && isPresentationMode -> Color(0xFF93C5FD)
                                        isSelected -> WeMadeColors.Primary.copy(alpha = 0.8f)
                                        isPresentationMode -> Color(0xFF64748B)
                                        else -> WeMadeColors.OnSurfaceMuted
                                    }
                                )
                            }
                        }
                    }
                }

                // Right: Action Buttons (Presentation Mode + Live Simulation)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Live Simulation Status Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isPresentationMode) Color(0xFF1E293B) else WeMadeColors.SuccessBg
                            )
                            .clickable { onToggleSimulation() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isSimulating) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted)
                            )
                            Text(
                                text = if (isSimulating) "Live Stream Aktif" else "Monitoring Pause",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSimulating) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }

                    // Client Presentation Mode Toggle
                    Button(
                        onClick = onTogglePresentationMode,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isPresentationMode) WeMadeColors.Purple else Color(0xFF4F46E5)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            IconPresentation(modifier = Modifier.size(15.dp), color = Color.White)
                            Text(
                                text = if (isPresentationMode) "Keluar Mode Presentasi" else "Mode Presentasi Klien",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // Row 2: Unified Monitoring Legend & Flow Characteristics
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isPresentationMode) Color(0xFF1E293B) else Color(0xFFF8FAFC))
                    .border(
                        width = 1.dp,
                        color = if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Left: Flow Line Meaning Indicators
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Jalur Monitoring Terpadu:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurface
                    )

                    // Forward Edge Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(16.dp)
                                .height(3.dp)
                                .background(WeMadeColors.Primary, RoundedCornerShape(2.dp))
                        )
                        Text(
                            text = "──▶ Alur Produksi Normal (Forward)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
                        )
                    }

                    // Backward QC Feedback Edge Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFFDC2626).copy(alpha = 0.14f))
                                .border(1.dp, Color(0xFFFCA5A5), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "⤶ - - - ◀",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFFDC2626)
                            )
                        }
                        Text(
                            text = "Garis Putus Merah: Jika QC Gagal ➔ Balik ke Rantai Pasok / Lantai Jahit",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFDC2626)
                        )
                    }
                }

                // Right: Target Profile Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isPresentationMode) Color(0xFF334155) else WeMadeColors.Border.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Profil: ${selectedPreset.targetClientProfile}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isPresentationMode) Color(0xFFCBD5E1) else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}
