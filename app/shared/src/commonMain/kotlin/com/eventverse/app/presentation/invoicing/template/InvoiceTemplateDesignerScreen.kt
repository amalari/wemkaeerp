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
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun InvoiceTemplateDesignerScreen(
    tenantSlug: String,
    templateId: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug, templateId) {
        TemplateDesignerViewModel(tenantSlug = tenantSlug, initialTemplateId = templateId)
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
            onEvent = viewModel::onEvent,
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
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .horizontalScroll(rememberScrollState())
                    .verticalScroll(rememberScrollState())
            ) {
                TemplateCanvas(
                    state = state,
                    onEvent = viewModel::onEvent
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
}
