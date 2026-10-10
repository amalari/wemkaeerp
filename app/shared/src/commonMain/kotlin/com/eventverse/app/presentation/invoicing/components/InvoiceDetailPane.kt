package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiEvent
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun InvoiceDetailPane(
    invoice: Invoice,
    payments: List<InvoicePayment>,
    onEvent: (InvoiceUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalPaidMinor = payments.sumOf { it.amount.minorUnits }
    val totalPaid = Money(totalPaidMinor, invoice.currency)
    val remainingMinor = (invoice.total.minorUnits - totalPaidMinor).coerceAtLeast(0L)
    val remainingMoney = Money(remainingMinor, invoice.currency)

    ClayCard(
        modifier = modifier.fillMaxSize(),
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Header Row: Number + Status + Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = invoice.number.value,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = kindLabel(invoice.kind),
                            tint = kindColor(invoice.kind),
                            fontSize = 11.sp
                        )
                        ClayBadge(
                            text = statusLabel(invoice.status),
                            tint = statusColor(invoice.status),
                            fontSize = 11.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Text(
                        text = "Dibuat oleh: ${invoice.createdBy} · ${invoice.createdAt.toString().substringBefore('T')}",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                ClayButton(
                    text = "Tutup",
                    onClick = { onEvent(InvoiceUiEvent.SelectInvoice(null)) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 11.sp
                )
            }

            HorizontalDivider(color = WeMadeColors.Border)

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // PDF Download / Print
                ClayButton(
                    text = "Cetak / Unduh PDF",
                    onClick = { onEvent(InvoiceUiEvent.OpenPdfPreview(invoice)) },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 12.sp
                )

                // If Draft: Issue button
                if (invoice.status == InvoiceStatus.DRAFT) {
                    ClayButton(
                        text = "Terbitkan Faktur",
                        onClick = { onEvent(InvoiceUiEvent.IssueInvoice(invoice.id)) },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp
                    )
                }

                // If Issued or Partially Paid: Record payment
                if (invoice.status == InvoiceStatus.ISSUED || invoice.status == InvoiceStatus.PARTIALLY_PAID) {
                    ClayButton(
                        text = "Catat Pembayaran",
                        onClick = { onEvent(InvoiceUiEvent.OpenRecordPaymentDialog(invoice)) },
                        style = ClayButtonStyle.Success,
                        fontSize = 12.sp
                    )

                    // If Down Payment: Create Settlement button
                    if (invoice.kind == InvoiceKind.DOWN_PAYMENT) {
                        ClayButton(
                            text = "Buat Faktur Pelunasan",
                            onClick = { onEvent(InvoiceUiEvent.CreateSettlement(invoice.id)) },
                            style = ClayButtonStyle.Accent,
                            fontSize = 12.sp
                        )
                    }

                    // Void button
                    ClayButton(
                        text = "Batalkan (Void)",
                        onClick = { onEvent(InvoiceUiEvent.OpenVoidDialog(invoice)) },
                        style = ClayButtonStyle.Danger,
                        fontSize = 12.sp
                    )
                }
            }

            HorizontalDivider(color = WeMadeColors.Border)

            // Bill To Information
            SectionTitle("Kepada (Bill To)")
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.SurfaceMuted,
                borderWidth = ClayBorder.Hairline,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                Text(
                    text = invoice.billTo.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                if (invoice.billTo.contactPerson.isNotBlank()) {
                    Text(
                        text = "Kontak: ${invoice.billTo.contactPerson}",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurface
                    )
                }
                if (invoice.billTo.phone.isNotBlank() || invoice.billTo.email.isNotBlank()) {
                    Text(
                        text = "Telp/Email: ${invoice.billTo.phone} · ${invoice.billTo.email}",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                if (invoice.billTo.address.isNotBlank()) {
                    Text(
                        text = "Alamat: ${invoice.billTo.address}",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                if (invoice.billTo.taxId.isNotBlank()) {
                    Text(
                        text = "NPWP: ${invoice.billTo.taxId}",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            // Dates
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Tanggal Terbit", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    Text(text = invoice.issueDate.toString(), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "Jatuh Tempo", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = invoice.dueDate?.toString() ?: "Sesuai Perjanjian",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Items Table
            SectionTitle("Rincian Tagihan (${invoice.lines.size} Item)")
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                invoice.lines.forEach { line ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.Surface,
                                outline = WeMadeColors.Border,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Sm),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = line.description,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                            Text(
                                text = "${line.quantity.formatted()} @ ${line.unitPrice.formatted()}" +
                                    if (line.discount.isPositive) " (Diskon ${line.discount.asPercentageString(1)})" else "",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                        Text(
                            text = line.amount.formatted(),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }
            }

            // Financial Breakdown
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.SurfaceMuted,
                borderWidth = ClayBorder.Medium,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                FinancialRow("Subtotal", invoice.subtotal.formatted())
                if (invoice.globalDiscount.isPositive) {
                    FinancialRow("Diskon Global (${invoice.globalDiscount.asPercentageString(1)})", "-")
                }
                if (invoice.taxRatio.isPositive) {
                    FinancialRow("PPN (${invoice.taxRatio.asPercentageString(1)})", invoice.taxAmount.formatted())
                }
                HorizontalDivider(color = WeMadeColors.Border, modifier = Modifier.padding(vertical = ClaySpacing.Xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Total Tagihan",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = invoice.total.formatted(),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.Primary
                    )
                }
            }

            // Payment Summary & History
            SectionTitle("Status Pembayaran")
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = if (remainingMinor == 0L) WeMadeColors.SuccessBg else WeMadeColors.Surface,
                borderWidth = ClayBorder.Medium,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                FinancialRow("Total Terbayar", totalPaid.formatted())
                FinancialRow(
                    "Sisa Tagihan",
                    remainingMoney.formatted(),
                    valueColor = if (remainingMinor == 0L) WeMadeColors.Success else WeMadeColors.Error
                )

                if (payments.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Riwayat Transaksi:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    payments.forEach { pay ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "${pay.paidAt.toString().substringBefore('T')} · ${pay.method}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (pay.reference.isNotBlank()) {
                                    Text(
                                        text = "Ref: ${pay.reference}",
                                        fontSize = 10.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            }
                            Text(
                                text = pay.amount.formatted(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Success
                            )
                        }
                    }
                }
            }

            // Notes & Terms
            if (invoice.notes.isNotBlank()) {
                SectionTitle("Catatan")
                Text(text = invoice.notes, fontSize = 12.sp, color = WeMadeColors.OnSurface)
            }

            if (invoice.terms.isNotBlank()) {
                SectionTitle("Syarat & Ketentuan")
                Text(text = invoice.terms, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        fontSize = 12.sp,
        fontWeight = FontWeight.Black,
        color = WeMadeColors.OnSurface
    )
}

@Composable
private fun FinancialRow(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = WeMadeColors.OnSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(text = value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}
