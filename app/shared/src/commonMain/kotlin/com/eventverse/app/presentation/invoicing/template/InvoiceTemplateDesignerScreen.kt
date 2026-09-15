package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.InvoiceId
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.invoicing.components.InvoicePdfPreviewModal
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun InvoiceTemplateDesignerScreen(
    tenantSlug: String,
    templateId: String?,
    initialPrefill: InvoicePrefillData? = null,
    onClose: () -> Unit,
    onInvoiceCreated: (InvoiceId) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug, templateId, initialPrefill) {
        TemplateDesignerViewModel(
            tenantSlug = tenantSlug,
            initialTemplateId = templateId,
            initialPrefill = initialPrefill
        )
    }
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Toolbar
        DesignerToolbar(
            state = state,
            onEvent = { event ->
                if (event is TemplateDesignerUiEvent.SaveAndCreateInvoice) {
                    viewModel.onEvent(
                        TemplateDesignerUiEvent.SaveAndCreateInvoice { createdId ->
                            onInvoiceCreated(createdId)
                        }
                    )
                } else {
                    viewModel.onEvent(event)
                }
            },
            onClose = onClose
        )

        // Notification Banner
        state.error?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Kesalahan: $err", color = WeMadeColors.Error, fontSize = 12.sp)
                ClayButton(
                    text = "Tutup",
                    onClick = { viewModel.onEvent(TemplateDesignerUiEvent.DismissMessage) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 11.sp
                )
            }
        }

        state.successMessage?.let { msg ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SuccessBg,
                        outline = WeMadeColors.Success,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(ClaySpacing.Md),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = msg, color = WeMadeColors.Success, fontSize = 12.sp)
                ClayButton(
                    text = "Tutup",
                    onClick = { viewModel.onEvent(TemplateDesignerUiEvent.DismissMessage) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 11.sp
                )
            }
        }

        // Main Layout: Canvas (Scrollable) + Inspector Pane
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Center Canvas Area
            //
            // State scroll diangkat ke sini karena kanvas memakainya juga untuk pan: tarikan di
            // area kosong memanggil `dispatchRawDelta` pada state yang sama dengan yang dipakai
            // roda mouse, sehingga kedua jalur tidak pernah bertengkar soal posisi.
            val horizontalScroll = rememberScrollState()
            val verticalScroll = rememberScrollState()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(horizontalScroll)
                    .verticalScroll(verticalScroll)
            ) {
                TemplateCanvas(
                    state = state,
                    onEvent = viewModel::onEvent,
                    horizontalScroll = horizontalScroll,
                    verticalScroll = verticalScroll
                )
            }

            // Right Property Inspector (Width ~340dp)
            DesignerPropertyInspector(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = Modifier.width(340.dp)
            )
        }
    }

    // High-Fidelity PDF Preview Dialog
    if (state.isPdfPreviewOpen && state.createdInvoiceId != null) {
        InvoicePdfPreviewModal(
            invoice = state.previewInvoice,
            pdfUrl = viewModel.getPdfUrl(state.createdInvoiceId!!),
            onClose = { viewModel.onEvent(TemplateDesignerUiEvent.ClosePdfPreview) }
        )
    }
}
