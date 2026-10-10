package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

private const val DEFAULT_HOUR = 9

/**
 * Pemilih waktu-murni Clay. Buta domain: menerima dan mengembalikan **string** `JJ:MM` 24 jam (mis. `14:30`),
 * atau string kosong bila belum dipilih. Pemanggil yang menafsirkan nilainya, bukan komponen ini.
 *
 * Membuka [ClayTimePickerDialog] yang sudah ada (dipakai ulang, tidak disalin); nilainya selalu sah karena
 * dialog memakai stepper, bukan teks bebas.
 *
 * Kontrak nilai (satu keluarga dengan [ClayDatePicker]):
 * - [onValueChange] hanya dipanggil dengan string kosong atau `JJ:MM` sah — bukan teks setengah ketik.
 * - [value] yang bukan waktu sah ditampilkan apa adanya dan ditandai galat, tidak diam-diam dikosongkan
 *   (tanpa fallback senyap).
 */
@Composable
fun ClayTimePicker(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    val parsed = parseClayTimeOrNull(value)
    val hasFormatError = value.isNotBlank() && parsed == null
    val effectiveError = isError || hasFormatError
    var showDialog by remember { mutableStateOf(false) }
    val accent = when {
        effectiveError -> WeMadeColors.Error
        showDialog -> WeMadeColors.Primary
        else -> WeMadeColors.Outline
    }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
            Text(text = label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Spacer(modifier = Modifier.height(ClaySpacing.Xs))
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .claySurface(
                    shape = ClayShapes.Chip,
                    background = if (enabled) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                    outline = accent,
                    offset = if (effectiveError || showDialog) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if (effectiveError || showDialog) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = accent,
                    innerShade = false
                )
                .then(
                    if (enabled) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { showDialog = true }
                    } else Modifier
                )
                .padding(horizontal = ClaySpacing.Lg, vertical = 10.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (value.isBlank()) "JJ:MM" else value,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    fontWeight = if (value.isBlank()) FontWeight.Normal else FontWeight.Medium,
                    color = when {
                        value.isBlank() -> WeMadeColors.OnSurfaceMuted.copy(alpha = 0.55f)
                        effectiveError -> WeMadeColors.Error
                        enabled -> WeMadeColors.OnSurface
                        else -> WeMadeColors.OnSurfaceMuted
                    }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    if (enabled && value.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .pointerHoverIcon(PointerIcon.Hand)
                                .clickable { onValueChange("") }
                                .padding(2.dp)
                        ) { IconClose(modifier = Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted) }
                    }
                    IconClockClay(
                        modifier = Modifier.size(18.dp),
                        color = if (enabled) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
        if (hasFormatError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Format jam tidak sah (harus JJ:MM)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }

    if (showDialog && enabled) {
        ClayTimePickerDialog(
            initialHour = parsed?.hour ?: DEFAULT_HOUR,
            initialMinute = parsed?.minute ?: 0,
            title = if (label.isNotBlank()) "Pilih jam $label" else "Pilih Jam",
            onDismiss = { showDialog = false },
            onConfirm = { hour, minute ->
                onValueChange(formatClayTime(hour, minute))
                showDialog = false
            }
        )
    }
}

/**
 * Ikon jam digambar langsung: katalog `ClayIcons` belum punya ikon jam dan berada di atas batas ratchet
 * (file-size-rules §2), jadi tidak boleh ditambah di sana. Angkat ke katalog bila pemakaian kedua muncul
 * (aturan tiga kali).
 */
@Composable
private fun IconClockClay(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val strokeWidth = size.minDimension * 0.1f
        val radius = (size.minDimension - strokeWidth) / 2f
        val middle = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            color = color,
            radius = radius,
            center = middle,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawLine(
            color = color,
            start = middle,
            end = middle + Offset(0f, -radius * 0.6f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = middle,
            end = middle + Offset(radius * 0.5f, radius * 0.25f),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}
