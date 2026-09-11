package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Input field khas Claymorphism + Neo-Brutalism:
 * - Label mandiri tebal di atas field (tanpa floating notch yang memotong border).
 * - Kontainer tactile solid dengan outline tebal 2dp/3dp dan hard shadow offset.
 * - Efek terangkat (offset membesar) saat field mendapatkan fokus.
 * - Mendukung ikon Canvas leading/trailing.
 */
@Composable
fun ClayTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    focusColor: Color = WeMadeColors.Primary,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default
) {
    var isFocused by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label != null) {
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = if (enabled) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                    outline = if (isFocused) focusColor else WeMadeColors.Outline,
                    offset = if (isFocused) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if (isFocused) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = if (isFocused) focusColor else WeMadeColors.Outline,
                    innerShade = false
                )
                .padding(horizontal = ClaySpacing.Lg, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                if (leadingIcon != null) {
                    leadingIcon()
                }

                Box(modifier = Modifier.weight(1f)) {
                    if (value.isEmpty() && placeholder != null) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.55f),
                            fontWeight = FontWeight.Normal
                        )
                    }
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isFocused = it.isFocused },
                        singleLine = singleLine,
                        enabled = enabled,
                        textStyle = LocalTextStyle.current.copy(
                            color = WeMadeColors.OnSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = SolidColor(focusColor),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }
    }
}
