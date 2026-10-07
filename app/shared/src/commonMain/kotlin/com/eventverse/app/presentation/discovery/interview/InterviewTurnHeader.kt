package com.eventverse.app.presentation.discovery.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Header kartu giliran wawancara: menampilkan indikator giliran (n/8),
 * judul yang ramah pemilik usaha, serta tombol jalan pintas ("Terima Semua" dan "Lewati").
 */
@Composable
fun InterviewTurnHeader(
    step: InterviewStep,
    turnNumber: Int,
    onAcceptAll: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stepTitle = when (step) {
        InterviewStep.F0_BISNIS -> "F0. Profil Usaha"
        InterviewStep.F1_TUJUAN -> "F1. Sasaran & Kendala"
        InterviewStep.F2_SPEK -> "F2. Spesifikasi Area Kerja"
        InterviewStep.G1_DIVISI -> "1. Divisi Usaha"
        InterviewStep.G2_PERAN -> "2. Peran & Kepala Divisi"
        InterviewStep.G3_MODUL -> "3. Modul & Fitur Kebutuhan"
        InterviewStep.G4_SAMBUNGAN -> "4. Sambungan Alur Kerja"
        InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> "5. Ringkasan Rancangan"
    }

    val stepDesc = when (step) {
        InterviewStep.F0_BISNIS -> "Ceritakan atau konfirmasi ringkasan jenis usaha dan model operasional Anda."
        InterviewStep.F1_TUJUAN -> "Tentukan apa yang ingin dicapai dan kendala operasional yang ingin diselesaikan."
        InterviewStep.F2_SPEK -> "Tetapkan siapa yang mencatat, siapa yang melihat, dan tolok ukur penyelesaian pekerjaan."
        InterviewStep.G1_DIVISI -> "Sistem menebak bagian atau divisi usaha Anda berdasarkan narasi. Sesuaikan jika ada yang kurang."
        InterviewStep.G2_PERAN -> "Tentukan siapa saja yang bekerja di tiap divisi dan siapa yang memimpin divisi tersebut."
        InterviewStep.G3_MODUL -> "Setiap peran dihubungkan ke modul kerja yang siap pakai atau perlu dirakit."
        InterviewStep.G4_SAMBUNGAN -> "Periksa serah-terima dokumen atau data antarbagian agar alur operasional tersambung rapi."
        InterviewStep.G5_RINGKASAN, InterviewStep.DONE -> "Tinjau seluruh rancangan sistem sebelum melangkah ke draf blueprint."
    }

    // Penyebut mengikuti jalur: fase konsultan (3) + G1–G5 = 8; tanpa fase konsultan G1–G5 saja = 5.
    // Indeks G dihitung dari langkah; nomor giliran yang lebih besar darinya berarti fase konsultan sudah dilewati.
    val gIndex = (step.ordinal - InterviewStep.G1_DIVISI.ordinal + 1).coerceIn(1, 5)
    val maxTurns = if (step.isConsultant || turnNumber > gIndex) 8 else 5

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayBadge(
                    text = "Giliran $turnNumber/$maxTurns",
                    tint = WeMadeColors.Primary,
                    dot = true
                )
                Text(
                    text = stepTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = "Terima Semua Tebakan",
                    onClick = onAcceptAll,
                    style = ClayButtonStyle.Secondary
                )
                ClayButton(
                    text = "Lewati Wawancara",
                    onClick = onSkip,
                    style = ClayButtonStyle.Ghost
                )
            }
        }
        Text(
            text = stepDesc,
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier.padding(top = ClaySpacing.Sm)
        )
    }
}
