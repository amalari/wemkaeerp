package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.domain.sampling.isSizeColumnActive
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tabel Alokasi Kuantitas Sampel per Ukuran (Tabel Mandiri).
 * Ditampilkan tepat di bawah Size Chart, dengan kolom Total Pcs di sisi kanan.
 * Kolom aktif mengikuti ukuran yang ada di Size Chart.
 */
@Composable
fun SamplingQuantityTable(
    qtyRow: SizeChartRow,
    fullMatrix: List<SizeChartRow>,
    columns: List<String>,
    totalQty: Int,
    onUpdateQty: (col: String, value: String) -> Unit,
    readOnly: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = if (isError) WeMadeColors.Error else WeMadeColors.Border,
                borderWidth = if (isError) ClayBorder.Thick else ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Text(
                    text = "Alokasi Jumlah Sampel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "· Qty aktif jika POM terisi",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            // Total Badge Netral
            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Total: $totalQty pcs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        val activeColumns = columns.filter { isSizeColumnActive(fullMatrix, it) }

        if (activeColumns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ClaySpacing.Md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada ukuran aktif. Isi nama bagian dan ukuran di Size Chart untuk mengalokasikan sampel.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val scrollState = rememberScrollState()
            val pomColWidth = calculatePomColumnWidth(fullMatrix)
            val sizeColWidths = remember(columns, fullMatrix, qtyRow, readOnly) {
                columns.associateWith { col ->
                    calculateSizeColumnWidth(
                        col = col,
                        pomRows = fullMatrix.filter { !it.isQtyRow },
                        qtyRow = qtyRow,
                        includeDeleteButtonSpace = !readOnly && columns.size > 1
                    )
                }
            }
            val totalColWidth = 84.dp

            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                Column {
                    // Header Kolom Ukuran Aktif
                    Row(
                        modifier = Modifier
                            .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                            .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ukuran",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(pomColWidth)
                        )
                        for (col in activeColumns) {
                            val colWidth = sizeColWidths[col] ?: 64.dp
                            Text(
                                text = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurfaceMuted,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.width(colWidth)
                            )
                        }
                        Text(
                            text = "Total (Auto)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier.width(totalColWidth)
                        )
                    }

                    Spacer(Modifier.height(ClaySpacing.Xs))

                    // Baris Input Qty per Ukuran Aktif
                    Row(
                        modifier = Modifier
                            .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(pomColWidth)
                                .height(28.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Jumlah (pcs)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                softWrap = false
                            )
                        }

                        // Cell per ukuran aktif
                        for (col in activeColumns) {
                            val currentVal = qtyRow.values[col] ?: ""
                            val colWidth = sizeColWidths[col] ?: 64.dp

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(colWidth)
                                    .height(28.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = currentVal,
                                    readOnly = readOnly,
                                    onValueChange = { newVal ->
                                        val filtered = newVal.filter { it.isDigit() }
                                        onUpdateQty(col, filtered)
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = WeMadeColors.OnSurface,
                                        textAlign = TextAlign.Center
                                    ),
                                    singleLine = true,
                                    maxLines = 1,
                                    decorationBox = { innerTextField ->
                                        Box(
                                            modifier = Modifier.fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (currentVal.isEmpty()) {
                                                Text(
                                                    text = "0",
                                                    fontSize = 11.sp,
                                                    color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.45f),
                                                    textAlign = TextAlign.Center,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            innerTextField()
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Total Cell (Kalkulasi Otomatis)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(totalColWidth)
                                .height(28.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.Primary.copy(alpha = 0.08f),
                                    outline = WeMadeColors.Primary.copy(alpha = 0.35f),
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$totalQty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }
        }
    }
}
