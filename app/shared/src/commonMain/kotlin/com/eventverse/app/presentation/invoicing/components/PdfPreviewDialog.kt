package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
fun PdfPreviewDialog(
    state: InvoiceUiState,
    onEvent: (InvoiceUiEvent) -> Unit,
    getPdfUrl: (com.eventverse.app.domain.invoicing.InvoiceId) -> String
) {
    val invoice = state.selectedInvoice ?: return
    val pdfUrl = getPdfUrl(invoice.id)

    Dialog(onDismissRequest = { onEvent(InvoiceUiEvent.ClosePdfPreview) }) {
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
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cetak Dokumen Faktur (PDF)",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayButton(
                        text = "✕",
                        onClick = { onEvent(InvoiceUiEvent.ClosePdfPreview) },
                        style = ClayButtonStyle.Ghost,
                        fontSize = 14.sp
                    )
                }

                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = WeMadeColors.SurfaceMuted,
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Md)
                ) {
                    Text(
                        text = "Faktur ${invoice.number.value}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Klien: ${invoice.billTo.name}",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Text(
                        text = "Total: ${invoice.total.formatted()}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.Primary
                    )
                    Spacer(modifier = Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = "Dokumen PDF di-generate secara presisi menggunakan Apache PDFBox 3.x dan font Nunito/Fredoka berstandar A4 percetakan.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Tutup",
                        onClick = { onEvent(InvoiceUiEvent.ClosePdfPreview) },
                        style = ClayButtonStyle.Ghost
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Buka PDF di Tab Baru",
                        onClick = {
                            com.eventverse.app.infrastructure.navigation.PlatformNavigation.pushPath(pdfUrl)
                        },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }
    }
}
