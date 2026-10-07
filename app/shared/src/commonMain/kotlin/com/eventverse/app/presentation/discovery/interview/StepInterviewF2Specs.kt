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
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah F2: Spesifikasi Area Kerja Kunci.
 * Menetapkan siapa yang mencatat, siapa yang melihat, dan kapan pekerjaan dianggap selesai.
 */
@Composable
fun StepInterviewF2Spek(
    state: InterviewSessionState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newAreaName by remember { mutableStateOf("") }
    var whoFillsText by remember { mutableStateOf("") }
    var whatRecordedText by remember { mutableStateOf("") }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Spesifikasi Area Kerja Kunci",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Tentukan tanggung jawab pencatatan data pada tiap area operasional kunci.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs, bottom = ClaySpacing.Sm)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                if (state.specs.isEmpty()) {
                    Text(
                        text = "Belum ada spesifikasi area kerja khusus. Anda dapat menambahkan di bawah atau langsung lanjut ke divisi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    state.specs.forEach { spec ->
                        ClayCard(
                            modifier = Modifier.fillMaxWidth(),
                            outlineColor = WeMadeColors.Outline
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Area: ${spec.areaKey.value}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                ClayButton(
                                    text = "Hapus",
                                    onClick = { state.removeSpec(spec.areaKey) },
                                    style = ClayButtonStyle.Danger
                                )
                            }
                            val details = listOfNotNull(
                                spec.whoFills?.let { "Pengisi: $it" },
                                spec.whatRecorded?.let { "Mencatat: $it" },
                                spec.whoSees?.let { "Pelihat: $it" },
                                spec.doneWhen?.let { "Selesai: $it" }
                            )
                            if (details.isNotEmpty()) {
                                Text(
                                    text = details.joinToString(" | "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    modifier = Modifier.padding(top = ClaySpacing.Xs)
                                )
                            }
                        }
                    }
                }

                // Form Tambah Spec Ringkas
                Text(
                    text = "Tambah Spesifikasi Area Baru:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = ClaySpacing.Sm)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    OutlinedTextField(
                        value = newAreaName,
                        onValueChange = { newAreaName = it },
                        placeholder = { Text("Nama Area (mis. Gudang)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    OutlinedTextField(
                        value = whoFillsText,
                        onValueChange = { whoFillsText = it },
                        placeholder = { Text("Siapa Pengisi (mis. Staf Gudang)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = whatRecordedText,
                        onValueChange = { whatRecordedText = it },
                        placeholder = { Text("Apa yang dicatat (mis. Surat Jalan Masuk)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    ClayButton(
                        text = "Tambah Area",
                        onClick = {
                            if (newAreaName.isNotBlank()) {
                                val key = state.toSlug(newAreaName, "area")
                                val ref = BasisRef(Basis.JAWABAN, answerId = "turn_${state.turnNumber}")
                                state.addOrUpdateSpec(
                                    RequirementSpec(
                                        areaKey = RoleKey(key),
                                        whoFills = whoFillsText.ifBlank { null },
                                        whatRecorded = whatRecordedText.ifBlank { null },
                                        basisRef = ref
                                    )
                                )
                                newAreaName = ""
                                whoFillsText = ""
                                whatRecordedText = ""
                            }
                        },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }

        // Navigasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ClayButton(
                text = "Kembali (F1)",
                onClick = onBack,
                style = ClayButtonStyle.Ghost
            )
            ClayButton(
                text = "Lanjut ke Konfirmasi Divisi (G1)",
                onClick = onNext,
                style = ClayButtonStyle.Primary
            )
        }
    }
}
