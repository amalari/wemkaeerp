package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import com.eventverse.app.infrastructure.api.StoredTenantSlugProvider
import com.eventverse.app.infrastructure.api.TraceabilityApiClient
import com.eventverse.app.presentation.deal.openInBrowser
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Satu baris tabel rincian pesanan massal.
 *
 * Dipindahkan keluar dari `DealDetailDialog` yang masih jauh di atas hard limit lapisannya; Aturan
 * Ratchet melarang menambah baris ke sana, jadi tambahan apa pun wajib disertai pemindahan keluar.
 */
@Composable
fun SizeBreakdownRow(cells: List<String>, header: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) WeMadeColors.SurfaceMuted else Color.Transparent)
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                fontSize = 12.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Medium,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = when (index) {
                    0 -> Modifier.weight(2f, fill = false)
                    else -> Modifier.weight(1f, fill = false)
                },
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End
            )
        }
    }
}

fun formatQty(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()

/**
 * Cetak lembar kerja rajut dan setumpuk kartu telusur untuk satu SPK.
 *
 * Dicetak **saat SPK terbit**, jauh sebelum bundel pertama ada. Itu bukan kebetulan: bundel terbentuk
 * di akhir shift, dan menaruh printer di lantai rajut itu mahal dan rapuh. Dengan kartu yang sudah
 * ada di tangan, operator cukup mengambil kartu berikutnya, memindainya, lalu mengisi hitungan.
 *
 * PDF dibuka di tab baru alih-alih diunduh ke memori aplikasi: berkasnya menuju printer, bukan
 * menuju layar, dan pratinjau bawaan browser sudah menyediakan dialog cetak yang dikenal semua orang.
 */
@Composable
fun SpkPrintActions(
    samplingOrderId: String,
    modifier: Modifier = Modifier
) {
    if (samplingOrderId.isBlank()) return
    val ref = TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, samplingOrderId)
    val printer = rememberPdfPrintLauncher()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Cetak Dokumen Lantai Produksi",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Text(
            text = "Lembar kerja memuat QR, spek per panel, dan ukuran dari buyer - satu halaman per size.",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Md))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Lembar Kerja Rajut",
                onClick = { printer.open { worksheetPdfUrl(it, ref) } },
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Kartu Bundel",
                onClick = { printer.open { labelsPdfUrl(it, ref, TraceTier.BUNDLE, null) } },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Kartu Karung",
                onClick = { printer.open { labelsPdfUrl(it, ref, TraceTier.SACK, null) } },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Kartu SPK A6",
                onClick = { printer.open { spkCardPdfUrl(it, ref) } },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
        }
        printer.error?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Error)
        }
    }
}

/**
 * Membuka PDF cetak di tab browser.
 *
 * Tab browser tidak membawa header `Authorization`, jadi URL-nya harus ditukar dulu menjadi URL
 * bertiket lewat panggilan ber-Bearer — itu sebabnya membuka PDF kini asinkron dan bisa gagal.
 */
@Stable
class PdfPrintLauncher internal constructor(
    private val scope: CoroutineScope,
    private val client: TraceabilityApiClient
) {
    var error by mutableStateOf<String?>(null)
        private set

    fun open(request: suspend TraceabilityApiClient.(tenantSlug: String) -> Result<String>) {
        scope.launch {
            val tenantSlug = StoredTenantSlugProvider.currentTenantSlug()
                ?: return@launch run { error = "Sesi tidak ditemukan - silakan login ulang." }
            client.request(tenantSlug)
                .onSuccess { url -> error = null; openInBrowser(url) }
                .onFailure { error = "Gagal menyiapkan PDF: ${it.message ?: "kesalahan tidak dikenal"}" }
        }
    }
}

@Composable
fun rememberPdfPrintLauncher(): PdfPrintLauncher {
    val scope = rememberCoroutineScope()
    return remember(scope) { PdfPrintLauncher(scope, TraceabilityApiClient()) }
}
