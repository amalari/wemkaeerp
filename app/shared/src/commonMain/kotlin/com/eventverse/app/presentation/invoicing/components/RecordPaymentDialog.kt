package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiEvent
import com.eventverse.app.presentation.invoicing.InvoiceUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun RecordPaymentDialog(
    state: InvoiceUiState,
    onEvent: (InvoiceUiEvent) -> Unit
) {
    val invoice = state.selectedInvoice ?: return
    val totalPaidMinor = state.selectedInvoicePayments.sumOf { it.amount.minorUnits }
    val remainingMinor = (invoice.total.minorUnits - totalPaidMinor).coerceAtLeast(0L)
    val remainingNominal = remainingMinor / 100L

    var amountStr by remember { mutableStateOf(remainingNominal.toString()) }
    var selectedMethod by remember { mutableStateOf("BANK_TRANSFER") }
    var reference by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    val methods = listOf(
        "BANK_TRANSFER" to "Transfer Bank",
        "CASH" to "Tunai",
        "GIRO" to "Giro / Cek"
    )

    Dialog(onDismissRequest = { onEvent(InvoiceUiEvent.CloseRecordPaymentDialog) }) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            containerColor = WeMadeColors.Surface,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Catat Pembayaran Masuk",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayIconButton(
                        onClick = { onEvent(InvoiceUiEvent.CloseRecordPaymentDialog) },
                        size = 30.dp,
                        containerColor = WeMadeColors.SurfaceMuted
                    ) {
                        IconClose(Modifier.size(14.dp), color = WeMadeColors.OnSurface)
                    }
                }

                // Invoice Summary Box
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = WeMadeColors.SurfaceMuted,
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Md)
                ) {
                    Text(
                        text = "Nomor Faktur: ${invoice.number.value}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Klien: ${invoice.billTo.name}",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Total Tagihan:", fontSize = 12.sp)
                        Text(text = invoice.total.formatted(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Sisa Tagihan:", fontSize = 12.sp)
                        Text(
                            text = Money(remainingMinor, invoice.currency).formatted(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.Error
                        )
                    }
                }

                // Nominal Pembayaran
                ClayTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it },
                    label = "Nominal Pembayaran (Rp) *",
                    placeholder = remainingNominal.toString()
                )

                // Metode Pembayaran Chips
                Text(text = "Metode Pembayaran", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    methods.forEach { (code, label) ->
                        val isSelected = selectedMethod == code
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clayFlat(
                                    shape = ClayShapes.Card,
                                    background = if (isSelected) WeMadeColors.SuccessBg else WeMadeColors.SurfaceMuted,
                                    outline = if (isSelected) WeMadeColors.Success else WeMadeColors.Border,
                                    borderWidth = if (isSelected) ClayBorder.Medium else ClayBorder.Hairline
                                )
                                .clickable { selectedMethod = code }
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) WeMadeColors.Success else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                ClayTextField(
                    value = reference,
                    onValueChange = { reference = it },
                    label = "Nomor Referensi / Bukti Transfer",
                    placeholder = "mis. TRX-BCA-102938"
                )

                ClayTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = "Catatan Pembayaran",
                    placeholder = "Pembayaran via m-Banking"
                )

                // Tombol Aksi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = { onEvent(InvoiceUiEvent.CloseRecordPaymentDialog) },
                        style = ClayButtonStyle.Ghost
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    val amountLong = amountStr.toLongOrNull() ?: 0L
                    ClayButton(
                        text = "Simpan Pembayaran",
                        enabled = amountLong > 0L,
                        onClick = {
                            val money = Money(amountLong * 100L, invoice.currency)
                            onEvent(
                                InvoiceUiEvent.SubmitRecordPayment(
                                    amount = money,
                                    method = selectedMethod,
                                    reference = reference,
                                    note = note
                                )
                            )
                        },
                        style = ClayButtonStyle.Success
                    )
                }
            }
        }
    }
}
