package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.extractSizeColumns
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.domain.sampling.isSizeColumnActive
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
                        modifier = Modifier.width(140.dp)
                    )

                    // Kolom-kolom ukuran (dapat diubah nama & dihapus jika > 1)
                    for (col in columns) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(64.dp)
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
                                    decorationBox = { innerTextField ->
                                        if (col.isBlank()) {
                                            Text(
                                                text = "Size",
                                                fontSize = 11.sp,
                                                color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center
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
                                    .width(140.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
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
                                    decorationBox = { innerTextField ->
                                        if (row.pomName.isEmpty()) {
                                            Text(
                                                text = "cth: Lingkar Pinggang, Dada",
                                                fontSize = 10.sp,
                                                color = WeMadeColors.OnSurfaceMuted.copy(alpha = 0.5f)
                                            )
                                        }
                                        innerTextField()
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Input nilai per ukuran (cm)
                            for (col in columns) {
                                val currentVal = row.values[col] ?: ""

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .width(64.dp)
                                        .clayFlat(
                                            shape = ClayShapes.Pill,
                                            background = WeMadeColors.SurfaceMuted,
                                            outline = WeMadeColors.Border,
                                            borderWidth = ClayBorder.Hairline
                                        )
                                        .padding(horizontal = 4.dp, vertical = 5.dp),
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
                    text = "• Qty aktif jika POM terisi",
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
                            modifier = Modifier.width(140.dp)
                        )
                        for (col in activeColumns) {
                            Text(
                                text = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurfaceMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(64.dp)
                            )
                        }
                        Text(
                            text = "Total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp)
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
                                .width(140.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Jumlah (pcs)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Cell per ukuran aktif
                        for (col in activeColumns) {
                            val currentVal = qtyRow.values[col] ?: ""

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(64.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
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
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Total Cell
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(64.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$totalQty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WeMadeColors.OnSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}
