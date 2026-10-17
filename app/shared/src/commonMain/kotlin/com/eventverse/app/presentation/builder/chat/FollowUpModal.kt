package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.builder.ScenarioKeys
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Menghasilkan 3 rekomendasi AI kontekstual berdasarkan id dan teks pertanyaan celah modul.
 *
 * Pertanyaan **skenario** (FR-9, id `scn:`) tidak punya rekomendasi bawaan: pertanyaannya bebas (mis. "Apakah sampel harus disetujui
 * pelanggan dulu?") dan opsi `else` di bawah ("Sesuai SOP standar...") bukan jawabannya — jawaban yang dipilih masuk ke narasi untuk
 * proposal berikutnya, jadi opsi yang tak nyambung mengotori data. Daftar kosong = hanya "Lainnya" (teks bebas).
 */
internal fun getRecommendationsForQuestion(questionId: String, questionText: String): List<String> {
    if (ScenarioKeys.isQuestionId(questionId)) return emptyList()
    val qId = questionId.lowercase()
    val qText = questionText.lowercase()
    return when {
        qId.contains("who_fills") || qText.contains("siapa yang mengisi") -> listOf(
            "Operator / Staf divisi terkait",
            "Supervisor / Kepala Divisi",
            "Admin Operasional & PIC Sales"
        )
        qId.contains("what_recorded") || qText.contains("apa saja yang perlu dicatat") -> listOf(
            "Jumlah kuantiti, status, dan target deadline",
            "Spesifikasi teknis, bahan baku, dan nomor SPK",
            "Catatan defect, output harian, dan PIC operator"
        )
        qId.contains("who_sees") || qText.contains("siapa yang boleh melihat") -> listOf(
            "Hanya tim divisi terkait dan Owner/Superadmin",
            "Semua tim lintas divisi dan manajemen pabrik",
            "Divisi terkait dan divisi proses lanjutan"
        )
        qId.contains("done_when") || qText.contains("kapan pekerjaan") -> listOf(
            "Saat output fisik selesai dan lolos inspeksi QC",
            "Saat status dokumen diubah menjadi Selesai",
            "Saat serah-terima ke stasiun kerja berikutnya disetujui"
        )
        qId.contains("required") || qText.contains("wajib") -> listOf(
            "Wajib untuk kalkulasi biaya dan akurasi HPP",
            "Wajib untuk kontrol kualitas dan pelacakan barang",
            "Kebutuhan laporan kepatuhan dan audit manajemen"
        )
        else -> listOf(
            "Sesuai SOP standar operasional pabrik",
            "Dikelola langsung oleh Supervisor divisi",
            "Otomatis dari data modul sebelumnya"
        )
    }
}

/**
 * Formulir klarifikasi bertahap (1 pertanyaan per langkah) di area chat composer:
 * - 3 pilihan rekomendasi AI
 * - Pilihan ke-4 "Other" dengan input box teks
 */
@Composable
fun FollowUpModal(
    questions: List<PendingQuestion>,
    moduleId: String?,
    onSubmit: (List<Pair<String, String>>) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (questions.isEmpty()) return

    val typography = rememberClayTypography()
    var currentIndex by rememberSaveable { mutableStateOf(0) }
    // Map question.id -> selected option index (0..2 for AI recommendations, 3 for Other)
    var selectedOptions by rememberSaveable { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // Map question.id -> custom text for option 3
    var customTexts by rememberSaveable { mutableStateOf<Map<String, String>>(emptyMap()) }

    val safeIndex = currentIndex.coerceIn(0, questions.lastIndex)
    val currentQuestion = questions[safeIndex]
    val recommendations = remember(currentQuestion.id, currentQuestion.text) {
        getRecommendationsForQuestion(currentQuestion.id, currentQuestion.text)
    }
    // Tanpa rekomendasi (pertanyaan skenario) satu-satunya pilihan adalah "Lainnya": aktif sejak awal.
    val currentSelection = selectedOptions[currentQuestion.id] ?: if (recommendations.isEmpty()) 3 else null
    val currentCustomText = customTexts[currentQuestion.id].orEmpty()

    val isCurrentAnswered = when (currentSelection) {
        0, 1, 2 -> true
        3 -> currentCustomText.isNotBlank()
        else -> false
    }

    fun collectAllAnswers(): List<Pair<String, String>> {
        return questions.mapNotNull { q ->
            val recs = getRecommendationsForQuestion(q.id, q.text)
            val opt = selectedOptions[q.id] ?: if (recs.isEmpty()) 3 else null
            when (opt) {
                0, 1, 2 -> q.id to recs[opt]
                3 -> {
                    val custom = customTexts[q.id]?.trim().orEmpty()
                    if (custom.isNotBlank()) q.id to custom else null
                }
                else -> null
            }
        }
    }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        outlineColor = WeMadeColors.Accent,
        offset = ClayOffset.Small
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Md)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            // Header: Judul & Step Indicator (1 pertanyaan per langkah)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = "Pertanyaan Klarifikasi",
                        style = typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Accent
                    )
                    ClayBadge(
                        text = "${safeIndex + 1} / ${questions.size}",
                        tint = WeMadeColors.Accent
                    )
                }
                moduleId?.let {
                    Text(
                        text = it,
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            // Pertanyaan saat ini
            Text(
                text = currentQuestion.text,
                style = typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )

            // Pilihan: 3 Rekomendasi AI + 1 Other
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                recommendations.forEachIndexed { idx, recText ->
                    val isSelected = currentSelection == idx
                    OptionCard(
                        text = recText,
                        badge = "Rekomendasi AI",
                        isSelected = isSelected,
                        onClick = {
                            selectedOptions = selectedOptions + (currentQuestion.id to idx)
                        }
                    )
                }

                // Pilihan ke-4: Other / Lainnya
                val isOtherSelected = currentSelection == 3
                OptionCard(
                    text = "Lainnya (Tulis jawaban sendiri)",
                    badge = null,
                    isSelected = isOtherSelected,
                    onClick = {
                        selectedOptions = selectedOptions + (currentQuestion.id to 3)
                    }
                )

                // Input box teks muncul khusus saat pilihan ke-4 (Other) aktif
                if (isOtherSelected) {
                    ClayTextField(
                        value = currentCustomText,
                        onValueChange = { newText ->
                            customTexts = customTexts + (currentQuestion.id to newText)
                        },
                        placeholder = "Ketik jawaban spesifik Anda di sini...",
                        modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xxs),
                        singleLine = false,
                        maxLines = 3
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xs))

            // Tombol Navigasi: Kembali, Lewati, Lanjut / Kirim Jawaban
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    if (safeIndex > 0) {
                        ClayButton(
                            text = "Kembali",
                            onClick = { currentIndex = safeIndex - 1 },
                            style = ClayButtonStyle.Secondary
                        )
                    }
                    ClayButton(
                        text = "Lewati",
                        onClick = {
                            if (safeIndex < questions.lastIndex) {
                                currentIndex = safeIndex + 1
                            } else {
                                val answers = collectAllAnswers()
                                if (answers.isNotEmpty()) onSubmit(answers) else onSkip()
                            }
                        },
                        style = ClayButtonStyle.Secondary
                    )
                }

                if (safeIndex < questions.lastIndex) {
                    ClayButton(
                        text = "Lanjut",
                        onClick = { currentIndex = safeIndex + 1 },
                        enabled = isCurrentAnswered
                    )
                } else {
                    ClayButton(
                        text = "Kirim Jawaban",
                        onClick = {
                            val answers = collectAllAnswers()
                            onSubmit(answers)
                        },
                        enabled = isCurrentAnswered || collectAllAnswers().isNotEmpty()
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionCard(
    text: String,
    badge: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val typography = rememberClayTypography()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(
                if (isSelected) WeMadeColors.Primary.copy(alpha = 0.08f) else WeMadeColors.Surface,
                ClayShapes.Chip
            )
            .border(
                width = if (isSelected) ClayBorder.Medium else ClayBorder.Hairline,
                color = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
                shape = ClayShapes.Chip
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .background(if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface, CircleShape)
                        .border(
                            width = 2.dp,
                            color = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .background(WeMadeColors.Surface, CircleShape)
                        )
                    }
                }
                Text(
                    text = text,
                    style = typography.bodySmall,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
                )
            }
            if (badge != null && !isSelected) {
                Text(
                    text = badge,
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}
