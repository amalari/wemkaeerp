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
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah G2: Konfirmasi Peran per Divisi & Kepala Divisi.
 * Menampilkan peran-peran operasional di tiap divisi dan penunjukan kepala divisi.
 */
@Composable
fun StepInterviewG2Roles(
    state: InterviewSessionState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newRoleLabel by remember { mutableStateOf("") }
    var selectedDivisionForNewRole by remember {
        mutableStateOf(state.divisions.firstOrNull()?.code ?: DivisionCode("divisi"))
    }
    var editingRoleKey by remember { mutableStateOf<RoleKey?>(null) }
    var editRoleLabelText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        state.divisions.forEach { div ->
            val rolesInDiv = state.roles.filter { it.divisionCode == div.code }

            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Divisi: ${div.name}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    ClayBadge(
                        text = "${rolesInDiv.size} Peran",
                        tint = WeMadeColors.Primary
                    )
                }

                if (rolesInDiv.isEmpty()) {
                    Text(
                        text = "Belum ada peran di divisi ini.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = ClaySpacing.Sm)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = ClaySpacing.Sm),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        rolesInDiv.forEach { role ->
                            val isEditing = editingRoleKey == role.roleKey

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (isEditing) {
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = editRoleLabelText,
                                            onValueChange = { editRoleLabelText = it },
                                            singleLine = true,
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = WeMadeColors.Primary,
                                                unfocusedBorderColor = WeMadeColors.Outline
                                            )
                                        )
                                        ClayButton(
                                            text = "Simpan",
                                            onClick = {
                                                state.renameRole(role.roleKey, editRoleLabelText)
                                                editingRoleKey = null
                                            },
                                            style = ClayButtonStyle.Primary
                                        )
                                        ClayButton(
                                            text = "Batal",
                                            onClick = { editingRoleKey = null },
                                            style = ClayButtonStyle.Ghost
                                        )
                                    }
                                } else {
                                    Row(
                                        modifier = Modifier.weight(1f, fill = false),
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = role.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        if (role.isHead) {
                                            ClayBadge(
                                                text = "Kepala Divisi",
                                                tint = WeMadeColors.Accent,
                                                dot = true
                                            )
                                        }
                                        ClayBadge(
                                            text = if (role.source == ItemSource.GUESS) "Tebakan" else "Tambahan",
                                            tint = if (role.source == ItemSource.GUESS) WeMadeColors.Primary else WeMadeColors.Success
                                        )
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                        ClayButton(
                                            text = if (role.isHead) "Copot Kepala" else "Jadikan Kepala",
                                            onClick = { state.toggleRoleHead(role.roleKey) },
                                            style = if (role.isHead) ClayButtonStyle.Secondary else ClayButtonStyle.Ghost
                                        )
                                        ClayButton(
                                            text = "Ganti Nama",
                                            onClick = {
                                                editingRoleKey = role.roleKey
                                                editRoleLabelText = role.label
                                            },
                                            style = ClayButtonStyle.Secondary
                                        )
                                        ClayButton(
                                            text = "Hapus",
                                            onClick = { state.removeRole(role.roleKey) },
                                            style = ClayButtonStyle.Danger
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tambah Peran Baru
        if (state.divisions.isNotEmpty()) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Tambah Peran Baru",
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
                    OutlinedTextField(
                        value = newRoleLabel,
                        onValueChange = { newRoleLabel = it },
                        placeholder = { Text("Contoh: Operator Mesin, Kasir, Supervisor") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    ClayButton(
                        text = "Tambah",
                        onClick = {
                            state.addRole(newRoleLabel, selectedDivisionForNewRole)
                            newRoleLabel = ""
                        },
                        enabled = newRoleLabel.isNotBlank(),
                        style = ClayButtonStyle.Primary
                    )
                }

                // Pilih divisi tujuan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Untuk Divisi:",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    state.divisions.forEach { d ->
                        ClayButton(
                            text = d.name,
                            onClick = { selectedDivisionForNewRole = d.code },
                            style = if (selectedDivisionForNewRole == d.code) ClayButtonStyle.Primary else ClayButtonStyle.Ghost
                        )
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
                text = "Kembali ke Divisi (G1)",
                onClick = onBack,
                style = ClayButtonStyle.Secondary
            )
            ClayButton(
                text = "Lanjut ke Modul & Fitur (G3)",
                onClick = onNext,
                style = ClayButtonStyle.Accent,
                enabled = state.roles.isNotEmpty()
            )
        }
    }
}
