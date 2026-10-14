package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.GlassTokens
import com.eventverse.app.presentation.designsystem.glassSurface
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Varian **GLASS** (`ChatBubbleStyle.GLASS`) — gelembung kaca: isian semi-transparan, rim tipis
 * terang, kilau di tepi atas.
 *
 * Blur dibiarkan mati ([GlassTokens.BlurNone]) supaya teks tetap tajam: `Modifier.blur` lintas
 * target memburamkan isi node itu sendiri, bukan latar di belakangnya (lihat KDoc `glassSurface`).
 * Kesan kaca datang dari translusensi + rim. Balon user diberi rona [WeMadeColors.Primary] tipis
 * agar tetap terbaca sebagai milik pengirim, bukan kaca netral seperti balon agent.
 */
@Composable
internal fun GlassChatBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit
) {
    val isUser = entry.isUser
    Column(
        modifier = Modifier
            .glassSurface(
                shape = if (isUser) ClayShapes.ChatBubbleOutgoing else ClayShapes.ChatBubbleIncoming,
                background = if (isUser) WeMadeColors.Primary.copy(alpha = 0.18f) else WeMadeColors.GlassSurface,
                outline = WeMadeColors.GlassBorder,
                blurRadius = GlassTokens.BlurNone
            )
            .padding(
                if (isUser) PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
                else PaddingValues(ClaySpacing.Md)
            ),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        if (isUser) UserChatText(entry) else AgentChatContent(entry, busy, onApply, onShowResult)
    }
}
