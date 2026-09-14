package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Clay-styled checkbox. Material `Checkbox`/`Switch` are off-limits (Contract 5) — this is
 * `clayFlat` (no shadow), the same reason a checkbox cell in a dense form should not carry
 * a 6dp hard shadow.
 */
@Composable
fun ClayCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(22.dp)
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (checked) WeMadeColors.Primary else WeMadeColors.Surface,
                outline = if (checked) WeMadeColors.Primary else WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = { onCheckedChange(!checked) }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (checked) {
            IconCheck(modifier = Modifier.size(14.dp), color = WeMadeColors.Surface)
        }
    }
}
