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
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah F0: Profil Usaha & Model Bisnis.
 * Mengonfirmasi jenis usaha dan ringkasan alur operasional utama.
 */
@Composable
fun StepInterviewF0Bisnis(
    state: InterviewSessionState,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var summaryText by remember(state.profile) {
        mutableStateOf(state.profile?.summary ?: state.narrative.ifBlank { "Usaha Operasional Terpadu" })
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Ringkasan Profil Usaha Anda",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Sistem merangkum model operasional Anda dari cerita awal. Sesuaikan deskripsi di bawah agar sistem memahami konteks industri Anda.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs, bottom = ClaySpacing.Sm)
            )

            OutlinedTextField(
                value = summaryText,
                onValueChange = {
                    summaryText = it
                    state.updateProfileSummary(it)
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = WeMadeColors.Primary,
                    unfocusedBorderColor = WeMadeColors.Outline
                )
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ClayButton(
                text = "Lanjut ke Sasaran & Kendala (F1)",
                onClick = {
                    state.updateProfileSummary(summaryText)
                    onNext()
                },
                style = ClayButtonStyle.Primary
            )
        }
    }
}

/**
 * Langkah F1: Sasaran Operasional & Titik Sakit (Pain Points).
 * Menentukan apa yang ingin dicapai dan masalah yang sering terjadi di lapangan.
 */
@Composable
fun StepInterviewF1Tujuan(
    state: InterviewSessionState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newGoalText by remember { mutableStateOf("") }
    var newPainText by remember { mutableStateOf("") }
    val goals = state.profile?.goals.orEmpty()
    val painPoints = state.profile?.painPoints.orEmpty()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Tujuan Operasional
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "1. Sasaran & Tujuan Operasional",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Primary
            )
            Text(
                text = "Apa target utama yang ingin dicapai bisnis Anda dengan sistem ERP ini?",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs, bottom = ClaySpacing.Sm)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                if (goals.isEmpty()) {
                    Text(
                        text = "Belum ada sasaran operasional tercatat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    goals.forEach { goal ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "- $goal", style = MaterialTheme.typography.bodyMedium)
                            ClayButton(
                                text = "Hapus",
                                onClick = { state.removeGoal(goal) },
                                style = ClayButtonStyle.Danger
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newGoalText,
                        onValueChange = { newGoalText = it },
                        placeholder = { Text("Contoh: Stok bahan tercatat otomatis real-time") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Primary,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    ClayButton(
                        text = "Tambah",
                        onClick = {
                            if (newGoalText.isNotBlank()) {
                                state.addGoal(newGoalText)
                                newGoalText = ""
                            }
                        },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }

        // Kendala Saat Ini (Titik Sakit)
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "2. Kendala Operasional Saat Ini",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Warning
            )
            Text(
                text = "Apa kendala atau kebocoran yang sering terjadi di lapangan saat ini?",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs, bottom = ClaySpacing.Sm)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                if (painPoints.isEmpty()) {
                    Text(
                        text = "Belum ada kendala lapangan tercatat.",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    painPoints.forEach { point ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "- $point", style = MaterialTheme.typography.bodyMedium)
                            ClayButton(
                                text = "Hapus",
                                onClick = { state.removePainPoint(point) },
                                style = ClayButtonStyle.Danger
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newPainText,
                        onValueChange = { newPainText = it },
                        placeholder = { Text("Contoh: Selisih stok fisik dan pencatatan manual") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = WeMadeColors.Warning,
                            unfocusedBorderColor = WeMadeColors.Outline
                        )
                    )
                    ClayButton(
                        text = "Tambah",
                        onClick = {
                            if (newPainText.isNotBlank()) {
                                state.addPainPoint(newPainText)
                                newPainText = ""
                            }
                        },
                        style = ClayButtonStyle.Secondary
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
                text = "Kembali (F0)",
                onClick = onBack,
                style = ClayButtonStyle.Ghost
            )
            ClayButton(
                text = "Lanjut ke Spesifikasi Area (F2)",
                onClick = onNext,
                style = ClayButtonStyle.Primary
            )
        }
    }
}
