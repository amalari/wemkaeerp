package com.eventverse.app.presentation.discovery.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.DiscoveryModuleUi
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah G4: Konfirmasi Sambungan / Serah-Terima Antarmodul.
 * Memastikan alur data/dokumen dari satu modul ke modul berikutnya terhubung rapi.
 */
@Composable
fun StepInterviewG4Handoffs(
    state: InterviewSessionState,
    availableModules: List<DiscoveryModuleUi>,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newPortTypeText by remember { mutableStateOf("Data Operasional") }
    var selectedFromModule by remember {
        mutableStateOf(availableModules.firstOrNull()?.id ?: "")
    }
    var selectedToModule by remember {
        mutableStateOf(availableModules.getOrNull(1)?.id ?: availableModules.firstOrNull()?.id ?: "")
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Sambungan Serah-Terima Dokumen & Data",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Bagian ini memastikan alur kerja tidak terputus saat satu bagian menyerahkan pekerjaan ke bagian berikutnya:",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(bottom = ClaySpacing.Sm)
            )

            if (state.handoffs.isEmpty()) {
                Text(
                    text = "Belum ada sambungan serah-terima. Anda dapat menambahkannya di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    state.handoffs.forEach { h ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ClaySpacing.Xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f, fill = false),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${h.from.value} -> ${h.to.value}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                ClayBadge(
                                    text = h.portType.value,
                                    tint = WeMadeColors.Primary
                                )
                            }

                            ClayButton(
                                text = "Hapus",
                                onClick = { state.removeHandoff(h.from, h.to, h.portType) },
                                style = ClayButtonStyle.Danger
                            )
                        }
                    }
                }
            }
        }

        // Tambah Sambungan Baru
        if (availableModules.size >= 2) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tambah Sambungan Serah-Terima",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dari Modul:", style = MaterialTheme.typography.labelSmall)
                        availableModules.take(4).forEach { m ->
                            ClayButton(
                                text = m.displayName.ifBlank { m.id },
                                onClick = { selectedFromModule = m.id },
                                style = if (selectedFromModule == m.id) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                                modifier = Modifier.padding(vertical = ClaySpacing.Xs)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text("Ke Modul:", style = MaterialTheme.typography.labelSmall)
                        availableModules.take(4).forEach { m ->
                            ClayButton(
                                text = m.displayName.ifBlank { m.id },
                                onClick = { selectedToModule = m.id },
                                style = if (selectedToModule == m.id) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                                modifier = Modifier.padding(vertical = ClaySpacing.Xs)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPortTypeText,
                        onValueChange = { newPortTypeText = it },
                        placeholder = { Text("Tipe dokumen/data (contoh: Surat Jalan)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    ClayButton(
                        text = "Sambungkan",
                        onClick = {
                            if (selectedFromModule.isNotBlank() && selectedToModule.isNotBlank() && selectedFromModule != selectedToModule) {
                                state.addHandoff(
                                    ModuleId(selectedFromModule),
                                    ModuleId(selectedToModule),
                                    PortType(newPortTypeText.trim().ifBlank { "Data" })
                                )
                            }
                        },
                        enabled = selectedFromModule.isNotBlank() && selectedToModule.isNotBlank() && selectedFromModule != selectedToModule,
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }

        // Aksi Navigasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ClayButton(
                text = "Kembali ke Modul (G3)",
                onClick = onBack,
                style = ClayButtonStyle.Secondary
            )
            ClayButton(
                text = "Lanjut ke Ringkasan (G5)",
                onClick = onNext,
                style = ClayButtonStyle.Accent
            )
        }
    }
}
