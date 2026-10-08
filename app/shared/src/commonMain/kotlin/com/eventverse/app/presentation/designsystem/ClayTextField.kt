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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    /**
     * Tinggi minimum untuk isian multi-baris, diabaikan bila [singleLine] true.
     *
     * Tanpa ini, isian teks panjang (isi elemen teks pada template faktur) hanya setinggi satu baris
     * dan pengguna harus menggulir di dalam kotak satu baris untuk membaca ulang apa yang sudah
     * ditulisnya.
     */
    minLines: Int = 1,
    /** Batas tinggi isian multi-baris (komposer chat tumbuh sampai sini lalu menggulir di dalam). */
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    /** Opsional: peminta fokus programatik untuk field di dalam popup (mis. dropdown searchable). */
    focusRequester: FocusRequester? = null
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
                    background = if (enabled && !readOnly) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                    outline = when {
                        isError -> WeMadeColors.Error
                        isFocused && !readOnly -> focusColor
                        else -> WeMadeColors.Outline
                    },
                    offset = if ((isFocused || isError) && !readOnly) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if ((isFocused || isError) && !readOnly) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = when {
                        isError -> WeMadeColors.Error
                        isFocused && !readOnly -> focusColor
                        else -> WeMadeColors.Outline
                    },
                    innerShade = false
                )
                .padding(horizontal = ClaySpacing.Lg, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                // Isian multi-baris dibaca dari atas ke bawah; memusatkannya secara vertikal membuat
                // baris pertama melompat-lompat setiap kali pengguna menambah baris baru.
                verticalAlignment = if (singleLine) Alignment.CenterVertically else Alignment.Top,
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
                            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
                            .onFocusChanged { isFocused = it.isFocused },
                        singleLine = singleLine,
                        // BasicTextField melempar pengecualian bila minLines > 1 pada mode satu baris.
                        minLines = if (singleLine) 1 else minLines.coerceAtLeast(1),
                        maxLines = if (singleLine) 1 else maxLines.coerceAtLeast(minLines.coerceAtLeast(1)),
                        enabled = enabled,
                        readOnly = readOnly,
                        textStyle = LocalTextStyle.current.copy(
                            color = if (enabled && !readOnly) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        cursorBrush = if (readOnly) SolidColor(Color.Transparent) else SolidColor(focusColor),
                        keyboardOptions = keyboardOptions,
                        keyboardActions = keyboardActions
                    )
                }

                if (trailingIcon != null) {
                    trailingIcon()
                }
            }
        }

        if (isError && errorMessage != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = errorMessage,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }
}

/**
 * Varian area teks multi-baris khas Claymorphism (TRD-PLAT-003, Irisan 2 Track C).
 * Nyaman untuk field teks panjang ([com.eventverse.app.domain.prototype.FieldType.LONG_TEXT]
 * dan [com.eventverse.app.domain.customfield.FieldType.LongText]).
 */
@Composable
fun ClayTextArea(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    minLines: Int = 3,
    maxLines: Int = Int.MAX_VALUE,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    focusRequester: FocusRequester? = null
) {
    ClayTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        singleLine = false,
        minLines = minLines,
        maxLines = maxLines,
        enabled = enabled,
        readOnly = readOnly,
        isError = isError,
        errorMessage = errorMessage,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        focusRequester = focusRequester
    )
}

