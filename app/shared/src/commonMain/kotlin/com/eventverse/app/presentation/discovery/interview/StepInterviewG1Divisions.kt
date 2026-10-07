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
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah G1: Konfirmasi Divisi Usaha.
 * Pengguna mengonfirmasi divisi hasil tebakan sistem, dapat mengganti nama, menghapus, atau menambah baru.
 */
@Composable
fun StepInterviewG1Divisions(
    state: InterviewSessionState,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newDivisionName by remember { mutableStateOf("") }
    var editingCode by remember { mutableStateOf<DivisionCode?>(null) }
    var editNameText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Daftar Divisi Usaha",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Berikut divisi yang diusulkan untuk operasional Anda:",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(bottom = ClaySpacing.Sm)
            )

            if (state.divisions.isEmpty()) {
                Text(
                    text = "Belum ada divisi. Silakan tambah divisi pertama di bawah.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    state.divisions.forEach { div ->
                        val isEditing = editingCode == div.code
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = ClaySpacing.Xs),
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
                                        value = editNameText,
                                        onValueChange = { editNameText = it },
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = WeMadeColors.Primary,
                                            unfocusedBorderColor = WeMadeColors.Outline
                                        )
                                    )
                                    ClayButton(
                                        text = "Simpan",
                                        onClick = {
                                            state.renameDivision(div.code, editNameText)
                                            editingCode = null
                                        },
                                        style = ClayButtonStyle.Primary
                                    )
                                    ClayButton(
                                        text = "Batal",
                                        onClick = { editingCode = null },
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
                                        text = div.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    ClayBadge(
                                        text = if (div.source == ItemSource.GUESS) "Tebakan Sistem" else "Tambahan Anda",
                                        tint = if (div.source == ItemSource.GUESS) WeMadeColors.Primary else WeMadeColors.Success
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                                    ClayButton(
                                        text = "Ganti Nama",
                                        onClick = {
                                            editingCode = div.code
                                            editNameText = div.name
                                        },
                                        style = ClayButtonStyle.Secondary
                                    )
                                    ClayButton(
                                        text = "Hapus",
                                        onClick = { state.removeDivision(div.code) },
                                        style = ClayButtonStyle.Danger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Tambah Divisi Baru
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Tambah Divisi Baru",
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
                    value = newDivisionName,
                    onValueChange = { newDivisionName = it },
                    placeholder = { Text("Contoh: Gudang Bahan, Keuangan, dsb.") },
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
                        state.addDivision(newDivisionName)
                        newDivisionName = ""
                    },
                    enabled = newDivisionName.isNotBlank(),
                    style = ClayButtonStyle.Primary
                )
            }
        }

        // Aksi Navigasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ClayButton(
                text = "Lanjut ke Peran (G2)",
                onClick = onNext,
                style = ClayButtonStyle.Accent,
                enabled = state.divisions.isNotEmpty()
            )
        }
    }
}
