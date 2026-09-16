package com.eventverse.app.presentation.invoicing

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.navigation.PlatformNavigation
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.components.*
import com.eventverse.app.presentation.invoicing.template.InvoiceTemplateDesignerScreen
import com.eventverse.app.presentation.invoicing.template.InvoiceTemplateGalleryScreen
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.theme.WeMadeColors

sealed interface InvoicingSubRoute {
    data object Workspace : InvoicingSubRoute
    data object TemplateGallery : InvoicingSubRoute
    data class Designer(val templateId: String?) : InvoicingSubRoute
}

private fun resolveInvoicingSubRoute(path: String): InvoicingSubRoute {
    val templateId = AppNavScreen.extractTemplateId(path)
    val screen = AppNavScreen.fromPath(path)
    return when {
        templateId != null -> InvoicingSubRoute.Designer(templateId)
        screen == AppNavScreen.INVOICING_TEMPLATES -> InvoicingSubRoute.TemplateGallery
        else -> InvoicingSubRoute.Workspace
    }
}

@Composable
fun InvoiceWorkspaceScreen(
    tenantSlug: String,
    access: ModuleAccessConfig = ModuleAccessConfig(),
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug) {
        InvoiceViewModel(tenantSlug = tenantSlug, access = access)
    }
    val state by viewModel.uiState.collectAsState()
    var activePrefill by remember { mutableStateOf<InvoicePrefillData?>(null) }

    val initialPath = remember { PlatformNavigation.getCurrentPath() }
    var subRoute by remember { mutableStateOf(resolveInvoicingSubRoute(initialPath)) }

    LaunchedEffect(Unit) {
        PlatformNavigation.listenToPathChanges { newPath ->
            subRoute = resolveInvoicingSubRoute(newPath)
        }
    }

    LaunchedEffect(tenantSlug) {
        viewModel.onEvent(InvoiceUiEvent.Load)
        if (InvoicePrefillCoordinator.hasPending()) {
            val pending = InvoicePrefillCoordinator.consumePending()
            activePrefill = pending
            if (pending?.openDesignerDirectly == true) {
                subRoute = InvoicingSubRoute.Designer(null)
                PlatformNavigation.pushPath("/invoicing/templates")
            } else {
                viewModel.onEvent(InvoiceUiEvent.OpenCreateInvoiceDialog())
            }
        }
    }

    when (val current = subRoute) {
        is InvoicingSubRoute.Designer -> {
            InvoiceTemplateDesignerScreen(
                tenantSlug = tenantSlug,
                templateId = current.templateId ?: state.editingTemplateId,
                initialPrefill = activePrefill,
                onClose = {
                    activePrefill = null
                    viewModel.onEvent(InvoiceUiEvent.CloseDesigner)
                    subRoute = InvoicingSubRoute.TemplateGallery
                    PlatformNavigation.pushPath("/invoicing/templates")
                    viewModel.onEvent(InvoiceUiEvent.Load)
                },
                onInvoiceCreated = { _ ->
                    viewModel.onEvent(InvoiceUiEvent.Load)
                },
                modifier = modifier
            )
            return
        }
        is InvoicingSubRoute.TemplateGallery -> {
            InvoiceTemplateGalleryScreen(
                tenantSlug = tenantSlug,
                onOpenDesigner = { templateId ->
                    subRoute = InvoicingSubRoute.Designer(templateId)
                    if (templateId != null) {
                        PlatformNavigation.pushPath("/invoicing/templates/$templateId")
                    } else {
                        PlatformNavigation.pushPath("/invoicing/templates")
                    }
                },
                onBackToWorkspace = {
                    subRoute = InvoicingSubRoute.Workspace
                    PlatformNavigation.pushPath("/invoicing")
                    viewModel.onEvent(InvoiceUiEvent.Load)
                },
                modifier = modifier
            )
            return
        }
        InvoicingSubRoute.Workspace -> {
            // Lanjut ke render workspace tabel penagihan utama
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Workspace Header
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
                        text = "Invoice & Penagihan",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = "FINANCE",
                        tint = WeMadeColors.Primary,
                        fontSize = 11.sp
                    )
                }
                Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                Text(
                    text = "Penerbitan faktur tagihan sample, termin DP, dan pelunasan garmen berkanvas A4.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Katalog & Desain Template",
                    onClick = {
                        subRoute = InvoicingSubRoute.TemplateGallery
                        PlatformNavigation.pushPath("/invoicing/templates")
                    },
                    style = ClayButtonStyle.Secondary,
                    fontSize = 12.sp,
                    leading = { IconRuler(Modifier.size(13.dp), color = WeMadeColors.Primary) }
                )

                ClayButton(
                    text = "+ Buat Tagihan Baru",
                    onClick = { viewModel.onEvent(InvoiceUiEvent.OpenCreateInvoiceDialog()) },
                    style = ClayButtonStyle.Primary,
                    fontSize = 12.sp
                )
            }
        }

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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = err, color = WeMadeColors.Error, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ClayIconButton(
                    onClick = { viewModel.onEvent(InvoiceUiEvent.DismissMessage) },
                    size = 28.dp,
                    containerColor = WeMadeColors.Surface
                ) {
                    IconClose(Modifier.size(13.dp), color = WeMadeColors.Error)
                }
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = msg, color = WeMadeColors.Success, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                ClayIconButton(
                    onClick = { viewModel.onEvent(InvoiceUiEvent.DismissMessage) },
                    size = 28.dp,
                    containerColor = WeMadeColors.Surface
                ) {
                    IconClose(Modifier.size(13.dp), color = WeMadeColors.Success)
                }
            }
        }

        // Summary Metric Cards
        InvoiceSummaryCards(state = state)

        // Main Body: Master-Detail Layout
        Row(
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Left Column: Invoices List
            InvoiceTable(
                state = state,
                onEvent = viewModel::onEvent,
                modifier = if (state.selectedInvoice != null) Modifier.weight(0.55f) else Modifier.fillMaxWidth()
            )

            // Right Column: Invoice Detail
            state.selectedInvoice?.let { selected ->
                InvoiceDetailPane(
                    invoice = selected,
                    payments = state.selectedInvoicePayments,
                    onEvent = viewModel::onEvent,
                    modifier = Modifier.weight(0.45f)
                )
            }
        }
    }

    // Modal Dialogs
    if (state.isCreateInvoiceDialogOpen) {
        CreateInvoiceDialog(
            state = state,
            onEvent = { event ->
                if (event is InvoiceUiEvent.CloseCreateInvoiceDialog || event is InvoiceUiEvent.SubmitCreateInvoice) {
                    activePrefill = null
                }
                viewModel.onEvent(event)
            },
            initialPrefill = activePrefill
        )
    }

    if (state.isRecordPaymentDialogOpen) {
        RecordPaymentDialog(
            state = state,
            onEvent = viewModel::onEvent
        )
    }

    if (state.isVoidDialogOpen) {
        VoidInvoiceDialog(
            state = state,
            onEvent = viewModel::onEvent
        )
    }

    if (state.isPdfPreviewOpen) {
        PdfPreviewDialog(
            state = state,
            onEvent = viewModel::onEvent,
            getPdfUrl = { viewModel.getPdfUrl(it) }
        )
    }
}
