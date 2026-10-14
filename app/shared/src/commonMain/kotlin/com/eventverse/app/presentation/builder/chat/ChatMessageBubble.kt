package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Gelembung chat Builder (Gaya Card Studio):
 * - Pesan user: rata kanan dengan lebar proporsional (85%), outline primer biru.
 * - Pesan agent: rata kiri dengan lebar (92%), dipisahkan jelas berdasarkan tipe:
 *   1. Chat Hasil / Usulan Patch: box preview summary dengan tombol aksi Hasil & Terapkan.
 *   2. Chat Follow-Up / Pertanyaan: kartu checklist klarifikasi terstruktur.
 *   3. Chat Balasan Umum: balon teks bersih.
 */
@Composable
internal fun ChatMessageBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val isUser = entry.isUser
    val hasResult = entry.hasPendingPatch || (entry.applied && entry.summary.isNotEmpty())
    val hasQuestions = entry.questions.isNotEmpty()

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.94f)
        ) {
            if (isUser) {
                // ── Balon Chat User ───────────────────────────────────────────
                ClayCard(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 6.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    containerColor = WeMadeColors.PrimaryContainer,
                    outlineColor = WeMadeColors.Primary,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = ClaySpacing.Md,
                        vertical = ClaySpacing.Sm
                    )
                ) {
                    Text(
                        text = entry.text,
                        style = typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = WeMadeColors.OnSurface
                    )
                }
            } else {
                // ── Balon Chat Agent (Studio Card) ────────────────────────────
                val cardOutline = when {
                    entry.hasPendingPatch -> WeMadeColors.Warning
                    entry.applied && hasResult -> WeMadeColors.Success
                    hasQuestions -> WeMadeColors.Accent
                    else -> WeMadeColors.Outline
                }

                ClayCard(
                    shape = RoundedCornerShape(topStart = 6.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp),
                    containerColor = WeMadeColors.Surface,
                    outlineColor = cardOutline,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Md)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                        // Header Identitas Agent & Tag Status
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClayBadge(text = "Agent", tint = WeMadeColors.Success)
                            when {
                                entry.hasPendingPatch -> ClayBadge(text = "Hasil Siap Ditinjau", tint = WeMadeColors.Warning)
                                entry.applied && hasResult -> ClayBadge(text = "Diterapkan", tint = WeMadeColors.Success)
                                hasQuestions -> ClayBadge(text = "Perlu Klarifikasi", tint = WeMadeColors.Accent)
                            }
                        }

                        // Isi Pesan Utama Agent
                        Text(
                            text = entry.text,
                            style = typography.bodyMedium,
                            color = WeMadeColors.OnSurface
                        )

                        // ── Kategori: Chat Follow-up / Pertanyaan Klarifikasi ──
                        if (hasQuestions) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WeMadeColors.AccentLight, RoundedCornerShape(8.dp))
                                    .border(1.dp, WeMadeColors.Accent.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .padding(ClaySpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Daftar Klarifikasi:",
                                    style = typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.AccentDark
                                )
                                entry.questions.forEach { q ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 2.dp)
                                                .size(14.dp)
                                                .background(
                                                    if (q.answered) WeMadeColors.SuccessBg else Color.White,
                                                    RoundedCornerShape(3.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (q.answered) WeMadeColors.Success else WeMadeColors.OutlineSoft,
                                                    RoundedCornerShape(3.dp)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (q.answered) {
                                                IconCheck(
                                                    modifier = Modifier.size(10.dp),
                                                    color = WeMadeColors.Success
                                                )
                                            }
                                        }
                                        Text(
                                            text = q.text,
                                            style = typography.bodySmall,
                                            color = if (q.answered) WeMadeColors.OnSurfaceMuted else WeMadeColors.OnSurface
                                        )
                                    }
                                }
                            }
                        }

                        // ── Kategori: Chat Hasil / Usulan Patch Modul ──────────
                        if (entry.summary.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(WeMadeColors.InfoBg, RoundedCornerShape(8.dp))
                                    .border(1.dp, WeMadeColors.Info.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                                    .padding(ClaySpacing.Sm),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    IconLayers(Modifier.size(12.dp), color = WeMadeColors.InfoDark)
                                    Text(
                                        text = if (entry.hasPendingPatch) "Perubahan Pada Draf:" else "Komponen Modul:",
                                        style = typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.InfoDark
                                    )
                                }
                                entry.summary.forEach { line ->
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .padding(top = 6.dp)
                                                .size(5.dp)
                                                .background(WeMadeColors.InfoDark, CircleShape)
                                        )
                                        Text(
                                            text = line,
                                            style = typography.bodySmall,
                                            color = WeMadeColors.OnSurface
                                        )
                                    }
                                }
                            }
                        }

                        // Bilah Aksi Interaktif (Hasil & Terapkan)
                        if (hasResult || entry.hasPendingPatch) {
                            HorizontalDivider(
                                color = WeMadeColors.Border,
                                thickness = 1.dp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                ClayButton(
                                    text = "Buka Hasil",
                                    onClick = onShowResult,
                                    style = if (entry.hasPendingPatch) ClayButtonStyle.Secondary else ClayButtonStyle.Primary,
                                    leading = { IconLayers(Modifier.size(14.dp)) }
                                )
                                if (entry.hasPendingPatch) {
                                    ClayButton(
                                        text = if (busy) "Memproses..." else "Terapkan",
                                        style = ClayButtonStyle.Primary,
                                        enabled = !busy,
                                        onClick = onApply
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

