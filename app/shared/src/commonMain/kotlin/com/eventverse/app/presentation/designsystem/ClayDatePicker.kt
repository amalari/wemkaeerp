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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pemilih tanggal Clay. Buta domain: menerima dan mengembalikan **string** `TTTT-BB-HH` (ISO, mis. `2026-10-08`),
 * atau string kosong bila belum dipilih. Pemanggil yang menafsirkan nilainya, bukan komponen ini.
 *
 * **Tanda tangan ini dibekukan oleh A0 Irisan 1** (`PLAN-field-component-gaps.md` §2): Track B mengisi badan dan
 * perilakunya, Track C dan pemakai lain hanya bergantung pada tanda tangan ini. Mengubahnya = minta Track B,
 * jangan ditambal di pemakai.
 *
 * Kontrak nilai:
 * - [onValueChange] hanya dipanggil dengan string kosong atau tanggal kalender yang sah — bukan teks setengah ketik.
 * - [value] yang bukan tanggal sah ditampilkan apa adanya dan ditandai galat, tidak diam-diam dikosongkan.
 */
@Composable
fun ClayDatePicker(
    value: String,
    onValueChange: (String) -> Unit,
    label: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    val hasFormatError = value.isNotBlank() && parseIsoDateOrNull(value) == null
    val effectiveError = isError || hasFormatError
    var showDialog by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        if (label.isNotBlank()) {
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
                    outline = when {
                        effectiveError -> WeMadeColors.Error
                        showDialog -> WeMadeColors.Primary
                        else -> WeMadeColors.Outline
                    },
                    offset = if (effectiveError || showDialog) ClayOffset.Small else ClayOffset.Pressed,
                    borderWidth = if (effectiveError || showDialog) ClayBorder.Thick else ClayBorder.Medium,
                    shadowColor = when {
                        effectiveError -> WeMadeColors.Error
                        showDialog -> WeMadeColors.Primary
                        else -> WeMadeColors.Outline
                    },
                    innerShade = false
                )
                .then(
                    if (enabled) {
                        Modifier
                            .pointerHoverIcon(PointerIcon.Hand)
                            .clickable { showDialog = true }
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
                if (value.isBlank()) {
                    Text(
                        text = "TTTT-BB-HH",
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.55f),
                        fontWeight = FontWeight.Normal
                    )
                } else {
                    Text(
                        text = value,
                        fontSize = 13.sp,
                        color = if (effectiveError) WeMadeColors.Error else if (enabled) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                        fontWeight = FontWeight.Medium
                    )
                }

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
                        ) {
                            IconClose(
                                modifier = Modifier.size(14.dp),
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
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
                text = "Format tanggal tidak sah (harus TTTT-BB-HH)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.Error,
                modifier = Modifier.padding(start = 2.dp)
            )
        }
    }

    if (showDialog && enabled) {
        ClayDatePickerDialog(
            initialDate = parseIsoDateOrNull(value),
            title = if (label.isNotBlank()) "Pilih $label" else "Pilih Tanggal",
            onDismiss = { showDialog = false },
            onSelectDate = { selectedIso ->
                onValueChange(selectedIso)
                showDialog = false
            }
        )
    }
}
