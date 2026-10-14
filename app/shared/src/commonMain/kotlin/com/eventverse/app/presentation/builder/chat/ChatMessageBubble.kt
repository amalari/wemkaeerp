package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.domain.pack.ChatBubbleStyle
import com.eventverse.app.presentation.theme.LocalPackTheme

/**
 * Gelembung chat Builder (Gaya Card Studio) — **shell** yang memilih permukaan sesuai tema pack.
 *
 * Lebar dan perataan tidak bergantung tema (user 85% rata kanan, agent 94% rata kiri); yang
 * berbeda hanya permukaannya, dan itu diserahkan ke section [CardChatBubble] /
 * [BubbleChatBubble] / [GlassChatBubble]:
 * - **CARD** (bawaan): gelembung clay, outline tebal + hard shadow — identik dengan sebelum
 *   refactor tematik.
 * - **BUBBLE**: gelembung rata tanpa bayangan, hanya rim tipis.
 * - **GLASS**: gelembung kaca semi-transparan.
 *
 * Gaya diambil dari [LocalPackTheme] (`ChatBubbleStyle.CARD` secara bawaan), jadi komponen ini
 * otomatis mengikuti tema pack yang sedang aktif tanpa dipanggil satu per satu.
 *
 * Kategori isi agent (ringkasan patch, checklist pertanyaan, balon teks) tetap seperti semula;
 * lihat [AgentChatContent].
 */
@Composable
internal fun ChatMessageBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit,
    modifier: Modifier = Modifier,
    /** Hanya BRIEF: true = tampil penuh (terbaru), false = dilipat (lama). */
    briefExpanded: Boolean = true,
    style: ChatBubbleStyle = LocalPackTheme.current.chatBubbleStyle
) {
    entry.parseError?.let { ChatParseErrorBubble(it, modifier); return }
    if (entry.brief != null) { ChatBriefBubble(entry, briefExpanded, modifier); return }
    val isUser = entry.isUser

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.85f else 0.94f)
        ) {
            when (style) {
                ChatBubbleStyle.CARD -> CardChatBubble(entry, busy, onApply, onShowResult)
                ChatBubbleStyle.BUBBLE -> BubbleChatBubble(entry, busy, onApply, onShowResult)
                ChatBubbleStyle.GLASS -> GlassChatBubble(entry, busy, onApply, onShowResult)
            }
        }
    }
}
