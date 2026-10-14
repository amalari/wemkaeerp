package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Varian **CARD** (bawaan `ChatBubbleStyle.CARD`) — gelembung clay: outline tebal, hard shadow,
 * sudut membulat besar. Perilaku persis sama dengan `ChatMessageBubble` sebelum refactor tematik.
 */
@Composable
internal fun CardChatBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit
) {
    if (entry.isUser) {
        ClayCard(
            shape = ClayShapes.ChatBubbleOutgoing,
            containerColor = WeMadeColors.PrimaryContainer,
            outlineColor = WeMadeColors.Primary,
            contentPadding = PaddingValues(
                horizontal = ClaySpacing.Md,
                vertical = ClaySpacing.Sm
            )
        ) {
            UserChatText(entry)
        }
    } else {
        ClayCard(
            shape = ClayShapes.ChatBubbleIncoming,
            containerColor = WeMadeColors.Surface,
            outlineColor = agentChatOutline(entry),
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            AgentChatContent(entry, busy, onApply, onShowResult)
        }
    }
}
