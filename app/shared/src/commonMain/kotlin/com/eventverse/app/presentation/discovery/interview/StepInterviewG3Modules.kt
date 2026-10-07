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
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.DiscoveryModuleUi
import com.eventverse.app.presentation.discovery.displayName
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah G3: Modul & Fitur per Peran.
 * Menampilkan kaitan peran dengan modul, lencana asal (Pakai Ulang / Kembangkan / Baru),
 * keyakinan tebakan, pemindahan modul, dan pengelolaan fitur.
 */
@Composable
fun StepInterviewG3Modules(
    state: InterviewSessionState,
    availableModules: List<DiscoveryModuleUi>,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newFeatureText by remember { mutableStateOf("") }
    var activeFeatureTargetKey by remember { mutableStateOf<String?>(null) }
    var movingTargetKey by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        if (state.links.isEmpty()) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Belum ada kaitan peran ke modul.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        } else {
            state.links.forEach { link ->
                val role = state.roles.firstOrNull { it.roleKey == link.roleKey }
                val targetKey = "${link.roleKey.value}:${link.moduleId.value}"
                val isAddingFeature = activeFeatureTargetKey == targetKey
                val isMovingModule = movingTargetKey == targetKey

                val originColor = when (link.origin) {
                    ModuleOrigin.REUSE_PLATFORM -> WeMadeColors.Primary
                    ModuleOrigin.REUSE_PACK -> WeMadeColors.Success
                    ModuleOrigin.EXTEND -> WeMadeColors.Warning
                    ModuleOrigin.NEW -> WeMadeColors.Accent
                }

                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = "Peran: ${role?.label ?: link.roleKey.value}",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Modul: ${link.moduleId.value}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClayBadge(
                                text = link.origin.displayName,
                                tint = originColor
                            )
                            link.confidence?.let { conf ->
                                ClayBadge(
                                    text = "Yakin $conf%",
                                    tint = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }

                    // Pemindahan Modul
                    if (isMovingModule) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = ClaySpacing.Sm),
                            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            Text(
                                text = "Pilih modul tujuan pengganti:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                            ) {
                                availableModules.take(4).forEach { mod ->
                                    ClayButton(
                                        text = mod.displayName.ifBlank { mod.id },
                                        onClick = {
                                            state.changeModuleForRole(
                                                link.roleKey,
                                                link.moduleId,
                                                ModuleId(mod.id),
                                                mod.origin ?: ModuleOrigin.REUSE_PACK
                                            )
                                            movingTargetKey = null
                                        },
                                        style = if (mod.id == link.moduleId.value) ClayButtonStyle.Primary else ClayButtonStyle.Secondary
                                    )
                                }
                                ClayButton(
                                    text = "Batal",
                                    onClick = { movingTargetKey = null },
                                    style = ClayButtonStyle.Ghost
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(top = ClaySpacing.Sm),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            ClayButton(
                                text = "Pindahkan ke Modul Lain",
                                onClick = { movingTargetKey = targetKey },
                                style = ClayButtonStyle.Secondary
                            )
                        }
                    }

                    // Fitur-fitur Modul
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ClaySpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Fitur Modul:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )

                        if (link.features.isEmpty()) {
                            Text(
                                text = "Belum ada fitur khusus.",
                                style = MaterialTheme.typography.bodySmall,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                            ) {
                                link.features.forEach { feat ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                                    ) {
                                        ClayBadge(
                                            text = feat,
                                            tint = WeMadeColors.Primary
                                        )
                                        ClayButton(
                                            text = "x",
                                            onClick = { state.removeFeatureFromLink(link.roleKey, link.moduleId, feat) },
                                            style = ClayButtonStyle.Ghost
                                        )
                                    }
                                }
                            }
                        }

                        if (isAddingFeature) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = ClaySpacing.Xs),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = newFeatureText,
                                    onValueChange = { newFeatureText = it },
                                    placeholder = { Text("Nama fitur baru") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = WeMadeColors.Primary,
                                        unfocusedBorderColor = WeMadeColors.Outline
                                    )
                                )
                                ClayButton(
                                    text = "Simpan",
                                    onClick = {
                                        state.addFeatureToLink(link.roleKey, link.moduleId, newFeatureText)
                                        newFeatureText = ""
                                        activeFeatureTargetKey = null
                                    },
                                    enabled = newFeatureText.isNotBlank(),
                                    style = ClayButtonStyle.Primary
                                )
                                ClayButton(
                                    text = "Batal",
                                    onClick = { activeFeatureTargetKey = null },
                                    style = ClayButtonStyle.Ghost
                                )
                            }
                        } else {
                            ClayButton(
                                text = "+ Tambah Fitur",
                                onClick = { activeFeatureTargetKey = targetKey },
                                style = ClayButtonStyle.Ghost,
                                modifier = Modifier.padding(top = ClaySpacing.Xs)
                            )
                        }
                    }
                }
            }
        }

        // Aksi Navigasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ClayButton(
                text = "Kembali ke Peran (G2)",
                onClick = onBack,
                style = ClayButtonStyle.Secondary
            )
            ClayButton(
                text = "Lanjut ke Sambungan (G4)",
                onClick = onNext,
                style = ClayButtonStyle.Accent
            )
        }
    }
}
