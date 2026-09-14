package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.Invoice
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiEvent
import com.eventverse.app.presentation.invoicing.InvoiceUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun InvoiceTable(
    state: InvoiceUiState,
    onEvent: (InvoiceUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Search & Filter Toolbar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTextField(
                value = state.searchQuery,
                onValueChange = { onEvent(InvoiceUiEvent.Search(it)) },
                placeholder = "Cari nomor faktur atau nama klien…",
                modifier = Modifier.weight(1f)
            )
        }

        // Filter Tabs: Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StatusFilterChip(
                label = "Semua Status",
                isSelected = state.filterStatus == null,
                onClick = { onEvent(InvoiceUiEvent.FilterByStatus(null)) }
            )
            InvoiceStatus.entries.forEach { status ->
                StatusFilterChip(
                    label = statusLabel(status),
                    isSelected = state.filterStatus == status,
                    color = statusColor(status),
                    onClick = { onEvent(InvoiceUiEvent.FilterByStatus(status)) }
                )
            }
        }

        // Filter Tabs: Kind
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Jenis:",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(end = ClaySpacing.Xs)
            )
            KindFilterChip(
                label = "Semua Jenis",
                isSelected = state.filterKind == null,
                onClick = { onEvent(InvoiceUiEvent.FilterByKind(null)) }
            )
            InvoiceKind.entries.forEach { kind ->
                KindFilterChip(
                    label = kindLabel(kind),
                    isSelected = state.filterKind == kind,
                    onClick = { onEvent(InvoiceUiEvent.FilterByKind(kind)) }
                )
            }
        }

        // Invoices List
        if (state.invoices.isEmpty()) {
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.Surface,
                borderWidth = ClayBorder.Hairline,
                contentPadding = PaddingValues(ClaySpacing.Xxl)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Tidak ada tagihan yang cocok dengan filter saat ini.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(state.invoices, key = { it.id.value }) { invoice ->
                    InvoiceListItemCard(
                        invoice = invoice,
                        isSelected = state.selectedInvoice?.id == invoice.id,
                        onClick = { onEvent(InvoiceUiEvent.SelectInvoice(invoice)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun InvoiceListItemCard(
    invoice: Invoice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        containerColor = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Md),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Number & Client
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = invoice.number.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayBadge(
                        text = kindLabel(invoice.kind),
                        tint = kindColor(invoice.kind),
                        fontSize = 10.sp
                    )
                    ClayBadge(
                        text = statusLabel(invoice.status),
                        tint = statusColor(invoice.status),
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                Text(
                    text = invoice.billTo.name + if (invoice.billTo.contactPerson.isNotBlank()) " (${invoice.billTo.contactPerson})" else "",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Terbit: ${invoice.issueDate}" + (invoice.dueDate?.let { " • Tempo: $it" } ?: ""),
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            // Right: Amount
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = invoice.total.formatted(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = WeMadeColors.OnSurface
                )
                if (invoice.lines.isNotEmpty()) {
                    Text(
                        text = "${invoice.lines.size} item",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusFilterChip(
    label: String,
    isSelected: Boolean,
    color: Color = WeMadeColors.Primary,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = if (isSelected) color.copy(alpha = 0.18f) else WeMadeColors.Surface,
                outline = if (isSelected) color else WeMadeColors.OutlineSoft,
                borderWidth = if (isSelected) ClayBorder.Medium else ClayBorder.Hairline
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
            color = if (isSelected) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun KindFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
        )
    }
}

fun statusLabel(status: InvoiceStatus): String = when (status) {
    InvoiceStatus.DRAFT -> "Draf"
    InvoiceStatus.ISSUED -> "Diterbitkan"
    InvoiceStatus.PARTIALLY_PAID -> "Terbayar Sebagian"
    InvoiceStatus.PAID -> "Lunas"
    InvoiceStatus.VOID -> "Dibatalkan"
}

fun statusColor(status: InvoiceStatus): Color = when (status) {
    InvoiceStatus.DRAFT -> WeMadeColors.Purple
    InvoiceStatus.ISSUED -> WeMadeColors.Primary
    InvoiceStatus.PARTIALLY_PAID -> WeMadeColors.Accent
    InvoiceStatus.PAID -> WeMadeColors.Success
    InvoiceStatus.VOID -> WeMadeColors.Error
}

fun kindLabel(kind: InvoiceKind): String = when (kind) {
    InvoiceKind.DOWN_PAYMENT -> "Uang Muka (DP)"
    InvoiceKind.SETTLEMENT -> "Pelunasan"
    InvoiceKind.FULL -> "Tagihan Penuh"
    InvoiceKind.SAMPLE -> "Sample Order"
}

fun kindColor(kind: InvoiceKind): Color = when (kind) {
    InvoiceKind.DOWN_PAYMENT -> WeMadeColors.Accent
    InvoiceKind.SETTLEMENT -> WeMadeColors.Success
    InvoiceKind.FULL -> WeMadeColors.Primary
    InvoiceKind.SAMPLE -> WeMadeColors.Teal
}
