package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Pane Tagihan (FR-M2-5, sisi tenant): **baca saja**.
 *
 * Pane ini sengaja tidak punya tombol "bayar" maupun "ubah status": penerbitan dan konfirmasi
 * pembayaran adalah wewenang platform (manual pada MVP), dan menaruh tombol yang akan ditolak server
 * hanya mengajarkan pengguna bahwa tombol boleh tidak berfungsi. Yang dibutuhkan tenant adalah
 * melihat angka yang **sudah terkunci** di dokumennya.
 */
private data class InvoiceRow(
    val number: String,
    val period: String,
    val status: String,
    val totalIdr: Long,
    val lineCount: Int
)

private fun parseInvoices(raw: JsonValue): List<InvoiceRow> =
    ((raw as? JsonValue.Obj)?.get("invoices") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>()
        ?.map { invoice ->
            InvoiceRow(
                number = invoice.string("number").orEmpty(),
                period = invoice.string("period").orEmpty(),
                status = invoice.string("status").orEmpty(),
                totalIdr = (invoice.get("totalIdr") as? JsonValue.Num)?.asLong ?: 0L,
                lineCount = (invoice.get("lines") as? JsonValue.Arr)?.items?.size ?: 0
            )
        }
        .orEmpty()

/** Rupiah tanpa desimal & tanpa locale-dependent API (kompilasi 5 target). */
private fun formatIdr(amount: Long): String {
    val digits = amount.toString()
    val grouped = digits.reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}

@Composable
fun BuilderBillingPane(modifier: Modifier = Modifier) {
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    var rows by remember { mutableStateOf(emptyList<InvoiceRow>()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.invoices()
            .onSuccess { rows = parseInvoices(it) }
            .onFailure { e -> error = e.message }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = "Tagihan Langganan",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        error?.let {
            Text(
                text = it,
                color = WeMadeColors.Error,
                style = typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rows.isEmpty() && error == null) {
            Text(
                text = "Belum ada tagihan. Invoice diterbitkan platform setiap awal periode.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Column biasa, bukan LazyColumn: shell sudah membungkus pane dengan `verticalScroll`,
        // dan daftar bersarang yang `weight(1f)` di dalam induk ber-tinggi tak terbatas akan
        // memberi tinggi nol — kartunya ada di pohon tapi tak pernah terlihat.
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            rows.forEach { invoice ->
                ClayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = invoice.number,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = invoice.status,
                            tint = when (invoice.status) {
                                "PAID" -> WeMadeColors.Success
                                "ISSUED" -> WeMadeColors.Primary
                                "VOID" -> WeMadeColors.Error
                                else -> WeMadeColors.OnSurfaceMuted
                            }
                        )
                        Text(
                            text = "${invoice.lineCount} modul · ${formatIdr(invoice.totalIdr)}",
                            style = typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
