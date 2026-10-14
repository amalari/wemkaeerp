package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Varian **BUBBLE** (`ChatBubbleStyle.BUBBLE`) — gelembung sederhana: rata, tanpa hard shadow,
 * hanya rim tipis. Dipakai lewat `Modifier.clayFlat` supaya tetap sekeluarga token bentuk dengan
 * clay, tanpa memakai `Card` Material mentah maupun `Modifier.shadow()`.
 */
@Composable
internal fun BubbleChatBubble(
    entry: BuilderChatEntry,
    busy: Boolean,
    onApply: () -> Unit,
    onShowResult: () -> Unit
) {
    val isUser = entry.isUser
    Column(
        modifier = Modifier
            .clayFlat(
                shape = if (isUser) ClayShapes.ChatBubbleOutgoing else ClayShapes.ChatBubbleIncoming,
                background = if (isUser) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                outline = if (isUser) WeMadeColors.Primary else agentChatOutline(entry),
                borderWidth = ClayBorder.Hairline
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
