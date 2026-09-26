package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiEvent
import com.eventverse.app.presentation.invoicing.InvoiceUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun VoidInvoiceDialog(
    state: InvoiceUiState,
    onEvent: (InvoiceUiEvent) -> Unit
) {
    val invoice = state.selectedInvoice ?: return
    var reason by remember { mutableStateOf("") }

    Dialog(onDismissRequest = { onEvent(InvoiceUiEvent.CloseVoidDialog) }) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight(),
            containerColor = WeMadeColors.Surface,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Batalkan Faktur Tagihan",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = WeMadeColors.Error
                )

                Text(
                    text = "Anda akan membatalkan faktur ${invoice.number.value} untuk ${invoice.billTo.name}. Tindakan ini tidak dapat diurungkan.",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurface
                )

                ClayTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = "Alasan Pembatalan *",
                    placeholder = "mis. Salah spesifikasi pesanan / negosiasi ulang kontrak"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = { onEvent(InvoiceUiEvent.CloseVoidDialog) },
                        style = ClayButtonStyle.Ghost
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Ya, Batalkan Faktur",
                        enabled = reason.isNotBlank(),
                        onClick = { onEvent(InvoiceUiEvent.SubmitVoid(reason)) },
                        style = ClayButtonStyle.Danger
                    )
                }
            }
        }
    }
}
