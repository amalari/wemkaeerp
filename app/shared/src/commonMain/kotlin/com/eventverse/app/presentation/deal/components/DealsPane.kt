package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Daftar SEMUA deal milik tenant — pintu masuk deal tanpa harus lewat lead satu-satu.
 * Klik kartu membuka [DealDetailDialog] yang sama dengan jalur dari Lead Inspector.
 */
@Composable
fun DealsPane(
    tenantSlug: String,
    modifier: Modifier = Modifier
) {
    val dataSource = remember { DealApiClient() }
    var deals by remember { mutableStateOf<List<Deal>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var openDealId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tenantSlug, refreshKey) {
        isLoading = true
        error = null
        dataSource.getDeals(tenantSlug)
            .onSuccess {
                deals = it
                isLoading = false
            }
            .onFailure {
                error = it.message
                isLoading = false
            }
    }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Deal (${deals.size})",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClayButton(
                text = "Muat Ulang",
                onClick = { refreshKey++ },
                style = ClayButtonStyle.Ghost,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        when {
            isLoading -> Text(
                text = "Memuat deal...",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            error != null -> Text(
                text = error ?: "Terjadi kesalahan.",
                fontSize = 12.sp,
                color = WeMadeColors.Error
            )
            deals.isEmpty() -> Text(
                text = "Belum ada deal. Qualify satu lead di papan Kanban untuk membuat deal pertama.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            else -> LazyColumn(
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(deals, key = { it.id.value }) { deal ->
                    DealDirectoryRow(deal = deal, onClick = { openDealId = deal.id.value })
                }
            }
        }
    }

    openDealId?.let { dealId ->
        DealDetailDialog(
            tenantSlug = tenantSlug,
            dealId = dealId,
            onDismiss = { openDealId = null }
        )
    }
}

@Composable
private fun DealDirectoryRow(deal: Deal, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deal.title.value,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayBadge(text = deal.stage.displayName, tint = deal.stage.tint())
        }
        Spacer(Modifier.height(ClaySpacing.Xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Estimasi: " + (deal.estimatedValue?.let { formatIdr(it.amount) } ?: "-"),
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            deal.expectedCloseDate?.let { date ->
                Text(
                    text = "Target: $date",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

internal fun DealStage.tint() = when (this) {
    DealStage.OPEN -> WeMadeColors.Info
    DealStage.PO_RECEIVED -> WeMadeColors.Primary
    DealStage.IN_PRODUCTION -> WeMadeColors.Accent
    DealStage.WON -> WeMadeColors.Success
    DealStage.LOST -> WeMadeColors.Error
}

internal fun formatIdr(amount: Long): String =
    "Rp" + amount.toString().reversed().chunked(3).joinToString(".").reversed()
