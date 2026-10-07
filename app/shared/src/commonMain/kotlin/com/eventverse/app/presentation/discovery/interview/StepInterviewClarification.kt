package com.eventverse.app.presentation.discovery.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pertanyaan klarifikasi perencana alur penuh: hal pokok yang tak bisa disimpulkan dari cerita ditanyakan dulu,
 * baru alur & modul disusun. Tombol kirim aktif bila semua pertanyaan terjawab; **buta terhadap domain** selain
 * daftar [Clarification] — jawaban dikirim ke server lewat [onSubmit].
 */
@Composable
fun StepInterviewClarification(
    questions: List<Clarification>,
    busy: Boolean,
    onSubmit: (Map<String, String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val pending = questions.filter { it.answer.isNullOrBlank() }
    val answers = remember(pending.map { it.id }) { mutableStateMapOf<String, String>() }
    val complete = pending.all { !answers[it.id].isNullOrBlank() }

    ClayCard(modifier = modifier.fillMaxWidth(), outlineColor = WeMadeColors.Primary) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Text(
                text = "Sebelum menyusun alur, ada yang ingin saya pastikan",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Ceritanya belum cukup jelas di bagian berikut. Jawab singkat saja; setelah itu alur dan modul disusun dari awal sampai akhir.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            pending.forEach { q ->
                Text(text = q.question, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = answers[q.id].orEmpty(),
                    onValueChange = { answers[q.id] = it },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    enabled = !busy,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Outline
                    )
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                ClayButton(
                    text = if (busy) "Menyusun alur..." else "Kirim jawaban & susun alur",
                    onClick = { onSubmit(pending.associate { it.id to answers[it.id].orEmpty().trim() }) },
                    style = ClayButtonStyle.Primary,
                    enabled = complete && !busy
                )
            }
        }
    }
}
