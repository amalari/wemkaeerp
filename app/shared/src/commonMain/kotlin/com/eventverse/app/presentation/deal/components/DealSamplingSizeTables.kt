package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tabel Matriks Ukuran (Size Chart / POM) bergaya spreadsheet dinamis:
 * - Kolom ukuran dapat ditambah, diubah nama (misal angka 28, 30 atau huruf M, L), dan dihapus.
 * - Baris POM dapat ditambah, dinamai dengan petunjuk placeholder, dan diisi nilainya (cm).
 */
@Composable
fun SamplingSizeChartTable(
    pomRows: List<SizeChartRow>,
    columns: List<String>,
    onUpdateRow: (SizeChartRow) -> Unit,
    onDeleteRow: (String) -> Unit,
    onAddRow: () -> Unit,
    onAddColumn: () -> Unit,
    onRenameColumn: (oldCol: String, newCol: String) -> Unit,
    onDeleteColumn: (String) -> Unit,
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
        // Baris judul tabel & tombol tambah baris
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
                    text = "Size Chart / POM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "• Spesifikasi Pola (cm)",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            if (!readOnly) {
                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    ClayActionSurface(
                        onClick = onAddRow,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            IconPlus(Modifier.size(11.dp), color = WeMadeColors.Primary)
                            Text(
                                text = "Tambah Baris",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        val scrollState = rememberScrollState()
        val pomColWidth = calculatePomColumnWidth(pomRows)
        val sizeColWidths = remember(columns, pomRows, readOnly) {
            columns.associateWith { col ->
                calculateSizeColumnWidth(
                    col = col,
                    pomRows = pomRows,
                    includeDeleteButtonSpace = !readOnly && columns.size > 1
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
            Column {
                // Baris header kolom ukuran
                Row(
                    modifier = Modifier
                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                        .padding(horizontal = ClaySpacing.Sm, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bagian / POM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.width(pomColWidth)
                    )

                    // Kolom-kolom ukuran (dapat diubah nama & dihapus jika > 1)
                    for (col in columns) {
                        val colWidth = sizeColWidths[col] ?: 64.dp
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(colWidth)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 4.dp, vertical = 3.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                BasicTextField(
                                    value = col,
                                    readOnly = readOnly,
                                    onValueChange = { newCol ->
                                        onRenameColumn(col, newCol)
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurface,
                                        textAlign = TextAlign.Center
                                    ),
                                    singleLine = true,
                                    maxLines = 1,
                                    decorationBox = { innerTextField ->
                                        if (col.isBlank()) {
                                            Text(
                                                text = "Size",
                                                fontSize = 11.sp,
                                                color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center,
                                                maxLines = 1,
                                                softWrap = false
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.weight(1f, fill = false)
                                )

                                if (!readOnly && columns.size > 1) {
                                    Spacer(Modifier.width(2.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clickable { onDeleteColumn(col) },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        IconClose(
                                            modifier = Modifier.size(10.dp),
                                            color = WeMadeColors.OnSurfaceMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Tombol Tambah Kolom Ukuran Baru
                    if (!readOnly) {
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.Primary.copy(alpha = 0.12f),
                                    outline = WeMadeColors.Primary,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .clickable { onAddColumn() }
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconPlus(Modifier.size(10.dp), color = WeMadeColors.Primary)
                                Text(
                                    text = "Kolom",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                            }
                        }
                    }

                    Spacer(Modifier.width(24.dp)) // ruang tombol hapus baris
                }

                Spacer(Modifier.height(ClaySpacing.Xs))

                // Baris data POM
                if (pomRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ClaySpacing.Md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada baris ukuran. Klik '+ Tambah Baris'.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    pomRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Input nama POM dengan placeholder samar
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
                                BasicTextField(
                                    value = row.pomName,
                                    readOnly = readOnly,
                                    onValueChange = { newPom ->
                                        onUpdateRow(row.copy(pomName = newPom))
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WeMadeColors.OnSurface
                                    ),
                                    singleLine = true,
                                    maxLines = 1,
                                    decorationBox = { inner ->
                                        if (row.pomName.isEmpty()) {
                                            Text(POM_PLACEHOLDER_TEXT, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f), maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                                        }
                                        inner()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Input nilai per ukuran (cm)
                            for (col in columns) {
                                val currentVal = row.values[col] ?: ""
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
                                            val newValues = LinkedHashMap(row.values)
                                            newValues[col] = newVal
                                            onUpdateRow(row.copy(values = newValues))
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = WeMadeColors.OnSurface,
                                            textAlign = TextAlign.Center
                                        ),
                                        singleLine = true,
                                        maxLines = 1,
                                        decorationBox = { inner ->
                                            if (currentVal.isEmpty()) {
                                                Text("-", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.4f), textAlign = TextAlign.Center, maxLines = 1, softWrap = false)
                                            }
                                            inner()
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Tombol hapus baris POM
                            if (!readOnly) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeleteRow(row.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconClose(
                                        modifier = Modifier.size(14.dp),
                                        color = WeMadeColors.OnSurface
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

internal const val POM_PLACEHOLDER_TEXT = "cth: Dada, Pinggang"

/**
 * Menghitung lebar kolom Bagian / POM secara adaptif (max 1 baris, default 110dp, pas mengikuti teks).
 */
internal fun calculatePomColumnWidth(pomRows: List<SizeChartRow>): Dp {
    val maxRowChars = pomRows
        .filter { !it.isQtyRow && it.pomName.isNotBlank() }
        .maxOfOrNull { it.pomName.length } ?: 0
    val neededWidth = (maxRowChars * 5.8).dp + 22.dp
    return maxOf(110.dp, neededWidth).coerceAtMost(320.dp)
}

/**
 * Menghitung lebar kolom ukuran secara adaptif (max 1 baris, mengikuti teks terpanjang).
 * Memastikan teks ukuran panjang seperti "ALL SIZE" atau nama custom muncul utuh tanpa terpotong.
 */
internal fun calculateSizeColumnWidth(
    col: String,
    pomRows: List<SizeChartRow> = emptyList(),
    qtyRow: SizeChartRow? = null,
    includeDeleteButtonSpace: Boolean = true
): Dp {
    val headerChars = if (col.isBlank()) 4 else col.length
    val maxRowChars = pomRows.maxOfOrNull { (it.values[col] ?: "").length } ?: 0
    val qtyChars = (qtyRow?.values?.get(col) ?: "").length
    val contentChars = maxOf(headerChars, maxRowChars, qtyChars)

    val buttonSpace = if (includeDeleteButtonSpace) 18.dp else 0.dp
    val neededWidth = (contentChars * 6.2).dp + buttonSpace + 16.dp
    return maxOf(64.dp, neededWidth)
}
