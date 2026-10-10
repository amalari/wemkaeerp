package com.eventverse.app.presentation.costing.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.costing.CostingProductBenchmark
import com.eventverse.app.presentation.costing.CostingUiEvent
import com.eventverse.app.presentation.costing.CostingUiState
import com.eventverse.app.presentation.costing.formatRupiah
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Katalog arsip produk rajut yang pernah diproduksi — Knowledge Base yang menjadi acuan
 * [AiQuickEstimatorPane].
 *
 * Kolom yang ditampilkan dipilih berdasar apa yang dipakai estimator: gramasi netto dan menit
 * mesin. Keduanya tampil sejajar dengan HPP supaya operator bisa melihat sendiri artikel mana
 * yang angkanya janggal dan perlu diperbaiki di sumbernya.
 */
@Composable
fun HistoricalBenchmarksPane(
    state: CostingUiState,
    onEvent: (CostingUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
            ) {
                Text(
                    text = "Knowledge Base Produk Historis",
                    fontWeight = FontWeight.Black,
                    fontSize = 16.sp,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${state.benchmarks.size} artikel tersimpan sebagai acuan estimasi",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            Spacer(Modifier.width(ClaySpacing.Md))
            if (state.canWrite) {
                ClayButton(
                    text = if (state.isImportingWorkbook) "Mengimpor..." else "Import File Excel",
                    onClick = { onEvent(CostingUiEvent.PickAndImportWorkbook) },
                    style = ClayButtonStyle.Secondary,
                    enabled = !state.isImportingWorkbook
                )
            }
        }

        ClayTextField(
            value = state.benchmarkSearchQuery,
            onValueChange = { onEvent(CostingUiEvent.SetBenchmarkSearchQuery(it)) },
            placeholder = "Cari nama artikel, klien, atau jenis benang...",
            leadingIcon = { IconSearch() },
            modifier = Modifier.fillMaxWidth()
        )

        when {
            state.isLoadingBenchmarks -> CenteredNotice("Memuat arsip produk...")

            state.benchmarks.isEmpty() -> EmptyArchiveNotice()

            state.filteredBenchmarks.isEmpty() -> CenteredNotice(
                "Tidak ada artikel yang cocok dengan \"${state.benchmarkSearchQuery}\""
            )

            else -> LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                items(state.filteredBenchmarks) { benchmark ->
                    BenchmarkRowCard(benchmark)
                }
            }
        }
    }
}

@Composable
private fun BenchmarkRowCard(benchmark: CostingProductBenchmark) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
            ) {
                Text(
                    text = benchmark.styleName,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(
                        benchmark.clientName.takeIf { it.isNotBlank() },
                        benchmark.structure.yarnType.takeIf { it.isNotBlank() },
                        benchmark.structure.gauge?.let { "${it}GG" }
                    ).joinToString(" · ").ifBlank { "Detail teknis tidak tercatat" },
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(ClaySpacing.Md))
            ClayBadge(text = benchmark.category.displayName, tint = WeMadeColors.Primary)
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            MetricChip("Gramasi", "${benchmark.metrics.netWeightGrams.toInt()} g", WeMadeColors.Teal)
            benchmark.metrics.knittingMinutes?.let {
                MetricChip("Menit rajut", "$it mnt", WeMadeColors.Info)
            }
            if (benchmark.metrics.buttonCount > 0) {
                MetricChip("Kancing", "${benchmark.metrics.buttonCount} pcs", WeMadeColors.Accent)
            }
            MetricChip("HPP", benchmark.pricing.hppPerUnit.formatRupiah(), WeMadeColors.Primary)
            benchmark.pricing.sellingPricePerUnit?.let {
                MetricChip("Harga jual", it.formatRupiah(), WeMadeColors.Success)
            }
        }

        if (benchmark.costBreakdown.isNotEmpty()) {
            Spacer(Modifier.height(ClaySpacing.Md))
            Text(
                text = "Rincian: " + benchmark.costBreakdown.joinToString(" · ") {
                    "${it.label} ${it.amountPerUnit.formatRupiah()}"
                },
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (benchmark.sourceFileName.isNotBlank()) {
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(
                text = "Sumber: ${benchmark.sourceFileName}",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceDisabled,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MetricChip(label: String, value: String, tint: androidx.compose.ui.graphics.Color) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
        Text(label, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Black, color = tint)
    }
}

@Composable
private fun CenteredNotice(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)
    }
}

/**
 * Keadaan kosong menyebut perintah impor massalnya secara harfiah.
 *
 * Arsip 100 berkas tidak diimpor satu per satu lewat tombol; jalur sebenarnya adalah task
 * Gradle. Menyembunyikan perintah itu di dokumentasi berarti operator menghabiskan sore
 * mengklik tombol seratus kali.
 */
@Composable
private fun EmptyArchiveNotice() {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.SurfaceMuted,
        contentPadding = PaddingValues(ClaySpacing.Xl)
    ) {
        Text(
            text = "Arsip masih kosong",
            fontWeight = FontWeight.Black,
            fontSize = 14.sp,
            color = WeMadeColors.OnSurface
        )
        Spacer(Modifier.height(ClaySpacing.Sm))
        Text(
            text = "Estimator tetap bisa dipakai, tapi memakai gramasi baku dan rentang yang lebih " +
                "lebar. Impor berkas HPP lama untuk membuat estimasinya jauh lebih akurat.",
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Lg))
        Text(
            text = "Impor massal (ratusan berkas sekaligus):",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.SurfaceDark,
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(ClaySpacing.Md)
        ) {
            Text(
                text = "./gradlew :server:importHistoricalCosting \\\n" +
                    "    --args=\"--dir=data/excel-hpp --tenant=<tenantId>\"",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceInverse
            )
        }
    }
}
