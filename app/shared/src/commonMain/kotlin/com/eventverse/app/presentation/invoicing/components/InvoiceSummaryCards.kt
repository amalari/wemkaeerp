package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiState
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun InvoiceSummaryCards(
    state: InvoiceUiState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // 1. Total Piutang Aktif
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Total Piutang Berjalan",
            value = state.totalReceivable.formatted(),
            subtitle = "Menunggu pembayaran",
            tint = WeMadeColors.Primary
        )

        // 2. Total Terbayar (Lunas)
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Total Pembayaran Diterima",
            value = state.totalPaid.formatted(),
            subtitle = "Faktur telah lunas",
            tint = WeMadeColors.Success
        )

        // 3. Tagihan Berjalan
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Tagihan Aktif",
            value = "${state.totalActiveCount} Faktur",
            subtitle = "Terbit & berjalan",
            tint = WeMadeColors.Accent
        )

        // 4. Draft
        SummaryMetricCard(
            modifier = Modifier.weight(1f),
            label = "Draf Tagihan",
            value = "${state.totalDraftCount} Draf",
            subtitle = "Belum diterbitkan",
            tint = WeMadeColors.Purple
        )
    }
}

@Composable
private fun SummaryMetricCard(
    label: String,
    value: String,
    subtitle: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted
            )
            ClayBadge(
                text = "·",
                tint = tint,
                fontSize = 10.sp
            )
        }
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
        Text(
            text = value,
            fontSize = 18.sp,
            fontWeight = FontWeight.Black,
            color = WeMadeColors.OnSurface
        )
        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
        Text(
            text = subtitle,
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}
