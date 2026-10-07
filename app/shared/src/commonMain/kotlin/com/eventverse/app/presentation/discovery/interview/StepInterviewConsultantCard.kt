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
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kartu saran konsultan (F0-F2, A7).
 * Menampilkan rekomendasi konsultan dengan alasan singkat dan tombol aksi eksplisit:
 * Terima (SARAN_DITERIMA) / Ubah / Tolak.
 */
@Composable
fun StepInterviewConsultantCard(
    suggestion: ConsultantSuggestion,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    onModify: () -> Unit,
    modifier: Modifier = Modifier
) {
    val outline = when (suggestion.status) {
        ConsultantSuggestionStatus.ACCEPTED -> WeMadeColors.Success
        ConsultantSuggestionStatus.REJECTED -> WeMadeColors.Outline
        ConsultantSuggestionStatus.PENDING -> WeMadeColors.Primary
        ConsultantSuggestionStatus.MODIFIED -> WeMadeColors.Warning
    }

    ClayCard(modifier = modifier.fillMaxWidth(), outlineColor = outline) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = suggestion.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            ClayBadge(
                text = when (suggestion.status) {
                    ConsultantSuggestionStatus.ACCEPTED -> "Diterima"
                    ConsultantSuggestionStatus.REJECTED -> "Ditolak"
                    ConsultantSuggestionStatus.MODIFIED -> "Diubah"
                    ConsultantSuggestionStatus.PENDING -> "Saran Baru"
                },
                tint = outline
            )
        }

        Text(
            text = suggestion.rationale,
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier.padding(vertical = ClaySpacing.Xs)
        )

        Text(
            text = "Dasar: ${suggestion.basisRef}",
            style = MaterialTheme.typography.labelSmall,
            color = WeMadeColors.Primary
        )

        if (suggestion.status == ConsultantSuggestionStatus.PENDING) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayButton(
                    text = "Terima Saran",
                    onClick = onAccept,
                    style = ClayButtonStyle.Success
                )
                ClayButton(
                    text = "Ubah",
                    onClick = onModify,
                    style = ClayButtonStyle.Secondary
                )
                ClayButton(
                    text = "Tolak",
                    onClick = onReject,
                    style = ClayButtonStyle.Ghost
                )
            }
        }
    }
}
