package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Dialog pemilih tanggal berbasis Claymorphism yang seragam di seluruh 5 target KMP.
 */
@Composable
fun ClayDatePickerDialog(
    initialDate: LocalDate?,
    title: String = "Pilih Tanggal",
    onDismiss: () -> Unit,
    onSelectDate: (String) -> Unit
) {
    val today = remember {
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    }

    var viewingYear by remember { mutableStateOf(initialDate?.year ?: today.year) }
    var viewingMonth by remember { mutableStateOf(initialDate?.monthNumber ?: today.monthNumber) }
    var selectedDate by remember { mutableStateOf(initialDate) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 360.dp),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = selectedDate?.let { formatIsoDate(it) } ?: "Belum dipilih",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedDate != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                        )
                    }

                    ClayIconButton(
                        onClick = onDismiss,
                        size = 28.dp
                    ) {
                        IconClose(
                            modifier = Modifier.size(14.dp),
                            color = WeMadeColors.OnSurface
                        )
                    }
                }

                // Navigasi Bulan & Tahun
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
                        ClayIconButton(
                            onClick = { viewingYear -= 1 },
                            size = 28.dp,
                            shape = ClayShapes.Chip
                        ) {
                            Text(
                                text = "«",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }
                        ClayIconButton(
                            onClick = {
                                if (viewingMonth == 1) {
                                    viewingMonth = 12
                                    viewingYear -= 1
                                } else {
                                    viewingMonth -= 1
                                }
                            },
                            size = 28.dp,
                            shape = ClayShapes.Chip
                        ) {
                            IconArrowBack(
                                modifier = Modifier.size(12.dp),
                                color = WeMadeColors.OnSurface
                            )
                        }
                    }

                    Text(
                        text = "${monthNameIndonesian(viewingMonth)} $viewingYear",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
                        ClayIconButton(
                            onClick = {
                                if (viewingMonth == 12) {
                                    viewingMonth = 1
                                    viewingYear += 1
                                } else {
                                    viewingMonth += 1
                                }
                            },
                            size = 28.dp,
                            shape = ClayShapes.Chip
                        ) {
                            IconArrowForward(
                                modifier = Modifier.size(12.dp),
                                color = WeMadeColors.OnSurface
                            )
                        }
                        ClayIconButton(
                            onClick = { viewingYear += 1 },
                            size = 28.dp,
                            shape = ClayShapes.Chip
                        ) {
                            Text(
                                text = "»",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                // Hari dalam seminggu
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DayOfWeekNamesId.forEach { dayName ->
                        Text(
                            text = dayName,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }

                // Grid Tanggal
                val daysCount = daysInMonth(viewingYear, viewingMonth)
                val firstDayOfWeek = LocalDate(viewingYear, viewingMonth, 1).dayOfWeek
                val startOffset = dayOfWeekOffset(firstDayOfWeek)
                val totalCells = startOffset + daysCount
                val rowCount = (totalCells + 6) / 7

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    for (row in 0 until rowCount) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (col in 0 until 7) {
                                val cellIndex = row * 7 + col
                                if (cellIndex < startOffset || cellIndex >= totalCells) {
                                    Spacer(modifier = Modifier.weight(1f))
                                } else {
                                    val dayNum = cellIndex - startOffset + 1
                                    val cellDate = LocalDate(viewingYear, viewingMonth, dayNum)
                                    val isSelected = selectedDate == cellDate
                                    val isToday = cellDate == today

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .aspectRatio(1f)
                                            .padding(1.dp)
                                            .claySurface(
                                                shape = ClayShapes.Chip,
                                                background = when {
                                                    isSelected -> WeMadeColors.Primary
                                                    isToday -> WeMadeColors.PrimaryContainer
                                                    else -> WeMadeColors.Surface
                                                },
                                                outline = when {
                                                    isSelected -> WeMadeColors.Outline
                                                    isToday -> WeMadeColors.Primary
                                                    else -> WeMadeColors.Border
                                                },
                                                offset = if (isSelected) ClayOffset.Small else ClayOffset.Flat,
                                                borderWidth = if (isSelected || isToday) ClayBorder.Medium else ClayBorder.Hairline,
                                                shadowColor = if (isSelected) WeMadeColors.Outline else Color.Transparent,
                                                innerShade = false
                                            )
                                            .pointerHoverIcon(PointerIcon.Hand)
                                            .clickable { selectedDate = cellDate },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = dayNum.toString(),
                                            fontSize = 12.sp,
                                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                                            color = when {
                                                isSelected -> WeMadeColors.Surface
                                                isToday -> WeMadeColors.Primary
                                                else -> WeMadeColors.OnSurface
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Tombol Aksi di Bawah
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Kosongkan",
                        style = ClayButtonStyle.Ghost,
                        onClick = { onSelectDate("") }
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        ClayButton(
                            text = "Hari Ini",
                            style = ClayButtonStyle.Secondary,
                            onClick = {
                                selectedDate = today
                                viewingYear = today.year
                                viewingMonth = today.monthNumber
                            }
                        )
                        ClayButton(
                            text = "Pilih",
                            style = ClayButtonStyle.Primary,
                            enabled = selectedDate != null,
                            onClick = {
                                selectedDate?.let { onSelectDate(formatIsoDate(it)) }
                            }
                        )
                    }
                }
            }
        }
    }
}

internal fun parseIsoDateOrNull(value: String): LocalDate? =
    if (value.isBlank()) null else runCatching { LocalDate.parse(value.trim()) }.getOrNull()

internal fun isValidIsoDate(value: String): Boolean =
    value.isBlank() || runCatching { LocalDate.parse(value.trim()) }.isSuccess

internal fun formatIsoDate(date: LocalDate): String = date.toString()

internal fun daysInMonth(year: Int, monthNumber: Int): Int = when (monthNumber) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 30
}

internal fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

internal fun dayOfWeekOffset(dayOfWeek: DayOfWeek): Int = when (dayOfWeek) {
    DayOfWeek.MONDAY -> 0
    DayOfWeek.TUESDAY -> 1
    DayOfWeek.WEDNESDAY -> 2
    DayOfWeek.THURSDAY -> 3
    DayOfWeek.FRIDAY -> 4
    DayOfWeek.SATURDAY -> 5
    DayOfWeek.SUNDAY -> 6
}

internal fun monthNameIndonesian(monthNumber: Int): String = when (monthNumber) {
    1 -> "Januari"
    2 -> "Februari"
    3 -> "Maret"
    4 -> "April"
    5 -> "Mei"
    6 -> "Juni"
    7 -> "Juli"
    8 -> "Agustus"
    9 -> "September"
    10 -> "Oktober"
    11 -> "November"
    12 -> "Desember"
    else -> ""
}

private val DayOfWeekNamesId = listOf("Sen", "Sel", "Rab", "Kam", "Jum", "Sab", "Min")
