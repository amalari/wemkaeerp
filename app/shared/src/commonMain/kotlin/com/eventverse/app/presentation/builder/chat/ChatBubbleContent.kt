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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Isi gelembung chat yang **tidak bergantung pada permukaan** (surface-agnostic).
 *
 * Ketiga varian — [CardChatBubble] (CARD), [BubbleChatBubble] (BUBBLE), [GlassChatBubble] (GLASS) —
 * hanya berbeda pada pembungkusnya (bentuk, isian, rim). Isi pesan, blok klarifikasi, ringkasan
 * perubahan, dan bilah aksi identik, jadi bagian itu diangkat ke sini sekali saja. Inilah
 * pemisahan shell (permukaan) dari section (isi) yang dipakai repo ini.
 */

/** Ada hasil yang bisa ditinjau/diterapkan: patch menunggu, atau sudah diterapkan dengan ringkasan. */
internal fun hasChatResult(entry: BuilderChatEntry): Boolean =
    entry.hasPendingPatch || (entry.applied && entry.summary.isNotEmpty())

/** Warna rim kartu agent, berbeda per keadaan. State dibedakan lewat **warna**, bukan ketebalan. */
internal fun agentChatOutline(entry: BuilderChatEntry): Color = when {
    entry.hasPendingPatch -> WeMadeColors.Warning
    entry.applied && hasChatResult(entry) -> WeMadeColors.Success
    entry.questions.isNotEmpty() -> WeMadeColors.Accent
    else -> WeMadeColors.Outline
}

/** Balon teks user — satu paragraf, tanpa blok tambahan. */
@Composable
internal fun UserChatText(entry: BuilderChatEntry) {
    val typography = rememberClayTypography()
    Text(
        text = entry.text,
        style = typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = WeMadeColors.OnSurface
    )
}

/**
 * Isi balon chat agent: identitas + status, teks utama, daftar klarifikasi, ringkasan perubahan,
 * dan bilah aksi (Buka Hasil / Terapkan). Murni presentasi — tidak ada logika bisnis.
 */
@Composable
internal fun AgentChatContent(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit
) {
    val typography = rememberClayTypography()
    val hasResult = hasChatResult(entry)
    val hasQuestions = entry.questions.isNotEmpty()

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
                    .background(WeMadeColors.AccentLight, ClayShapes.Inset)
                    .border(ClayBorder.Hairline, WeMadeColors.Accent.copy(alpha = 0.4f), ClayShapes.Inset)
                    .padding(ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
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
                                .padding(top = ClaySpacing.Xxs)
                                .size(14.dp)
                                .background(
                                    if (q.answered) WeMadeColors.SuccessBg else Color.White,
                                    ClayShapes.Marker
                                )
                                .border(
                                    ClayBorder.Hairline,
                                    if (q.answered) WeMadeColors.Success else WeMadeColors.OutlineSoft,
                                    ClayShapes.Marker
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
                    .background(WeMadeColors.InfoBg, ClayShapes.Inset)
                    .border(ClayBorder.Hairline, WeMadeColors.Info.copy(alpha = 0.35f), ClayShapes.Inset)
                    .padding(ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
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
                                .padding(top = ClaySpacing.Sm)
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
                thickness = ClayBorder.Hairline,
                modifier = Modifier.padding(top = ClaySpacing.Xxs)
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
