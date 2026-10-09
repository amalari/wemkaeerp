package com.eventverse.app.presentation.designsystem

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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

private const val DEFAULT_HOUR = 9

/**
 * Pemilih tanggal + jam Clay. Buta domain: menerima/mengembalikan **string** `TTTT-BB-HH'T'JJ:MM`
 * (mis. `2026-10-08T14:30`, waktu dinding tanpa zona) atau string kosong.
 *
 * Alur dua langkah: kalender [ClayDatePickerDialog] (dipakai ulang, tidak disalin) lalu [ClayTimePickerDialog].
 *
 * Kontrak nilai: [onValueChange] hanya dipanggil dengan string kosong atau nilai sah (jam 00-23, menit 00-59).
 * [value] tak sah ditampilkan apa adanya dengan galat, tidak diam-diam dikosongkan.
 */
@Composable
fun ClayDateTimePicker(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    val parsed = parseIsoDateTimeOrNull(value)
    val hasFormatError = value.isNotEmpty() && parsed == null
    val effectiveError = isError || hasFormatError
    var flow by remember { mutableStateOf(DateTimeFlow()) }
    val active = flow.isActive
    val accent = when {
        effectiveError -> WeMadeColors.Error
        active -> WeMadeColors.Primary
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
                    offset = if (effectiveError || active) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if (effectiveError || active) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = accent,
                    innerShade = false
                )
                .then(
                    if (enabled) {
                        Modifier.pointerHoverIcon(PointerIcon.Hand).clickable { flow = flow.opened() }
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
                    text = if (value.isBlank()) "TTTT-BB-HH JJ:MM" else displayIsoDateTime(value),
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
                    IconCalendarGrid(
                        modifier = Modifier.size(18.dp),
                        color = if (enabled) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
        if (hasFormatError) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Format tanggal-jam tidak sah (harus TTTT-BB-HHTJJ:MM)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }

    if (enabled && flow.step == DateTimeStep.DATE) {
        ClayDatePickerDialog(
            initialDate = flow.calendarInitialDate(parsed),
            title = if (label.isNotBlank()) "Pilih tanggal $label" else "Pilih Tanggal",
            onDismiss = { flow = flow.dismissed() },
            onSelectDate = { iso ->
                val date = parseIsoDateOrNull(iso)
                if (date == null) { // tombol "Kosongkan"
                    onValueChange("")
                    flow = flow.dismissed()
                } else {
                    flow = flow.datePicked(date)
                }
            }
        )
    }
    val chosenDate = flow.pendingDate
    if (enabled && flow.step == DateTimeStep.TIME && chosenDate != null) {
        ClayTimePickerDialog(
            initialHour = parsed?.hour ?: DEFAULT_HOUR,
            initialMinute = parsed?.minute ?: 0,
            title = if (label.isNotBlank()) "Pilih jam $label" else "Pilih Jam",
            onDismiss = { flow = flow.dismissed() },
            onBack = { flow = flow.backedToDate() },
            onConfirm = { hour, minute ->
                onValueChange(formatIsoDateTime(chosenDate, hour, minute))
                flow = flow.dismissed()
            }
        )
    }
}
