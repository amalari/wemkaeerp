package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField

/** Komposer chat di dasar layar: tumbuh sampai 6 baris; Enter kirim, Shift+Enter baris baru. */
@Composable
internal fun ChatComposer(
    value: String,
    onValueChange: (String) -> Unit,
    busy: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Contoh: pabrik kaos FOB dengan tahap sablon"
) {
    val canSend = !busy && value.isNotBlank()
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.Bottom
    ) {
        ClayTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = placeholder,
            singleLine = false,
            minLines = 1,
            maxLines = 6,
            modifier = Modifier.weight(1f).onPreviewKeyEvent { e ->
                if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) {
                    if (canSend) onSend()
                    true
                } else false
            }
        )
        ClayButton(
            text = if (busy) "Mengirim…" else "Kirim",
            enabled = canSend,
            onClick = onSend
        )
    }
}
