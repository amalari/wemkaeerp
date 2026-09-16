package com.eventverse.app.presentation.deal.components

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
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.deal.DealUiEvent
import com.eventverse.app.presentation.deal.DealUiState
import com.eventverse.app.presentation.deal.DealViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog detail Deal: identitas deal + contact, pemilih tahap, daftar purchase order,
 * dan formulir input PO manual + tombol upload berkas (platform picker).
 * Meng-host [DealViewModel]-nya sendiri supaya bisa dibuka dari pane mana pun.
 */
@Composable
fun DealDetailDialog(
    tenantSlug: String,
    dealId: String? = null,
    sourceLeadId: String? = null,
    onDismiss: () -> Unit
) {
    val viewModel = remember(tenantSlug, dealId, sourceLeadId) {
        DealViewModel(tenantSlug = tenantSlug, dealId = dealId, sourceLeadId = sourceLeadId)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.onEvent(DealUiEvent.Load) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.widthIn(max = 520.dp),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            when {
                state.isLoading -> Text(
                    text = "Memuat deal...",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                state.deal == null -> Column {
                    Text(
                        text = "Deal belum tersedia",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = state.statusMessage ?: "Deal tidak ditemukan.",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                else -> DealDetailContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DealDetailContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val deal = state.deal ?: return
    var poNumber by remember { mutableStateOf("") }
    var poDescription by remember { mutableStateOf("") }
    var poQuantity by remember { mutableStateOf("1") }
    var poUnitPrice by remember { mutableStateOf("") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

        // ── Header: judul deal + badge stage + tombol tutup ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deal.title.value,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(ClaySpacing.Md))
                ClayBadge(text = deal.stage.displayName, tint = deal.stage.tint())
            }
            ClayIconButton(onClick = onDismiss) {
                IconClose(Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ── Kartu Contact ───────────────────────────────────────────────────
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = WeMadeColors.SurfaceMuted,
            outlineColor = WeMadeColors.Outline,
            borderWidth = ClayBorder.Hairline,
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            Text(
                text = "Contact",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted
            )
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(
                text = state.contactName ?: "-",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            state.contactPhone?.let { phone ->
                Spacer(Modifier.height(ClaySpacing.Xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconPhone(Modifier.size(12.dp))
                    Spacer(Modifier.width(ClaySpacing.Xs))
                    Text(text = phone, fontSize = 12.sp, color = WeMadeColors.OnSurface)
                }
            }
            state.contactEmail?.let { email ->
                Spacer(Modifier.height(ClaySpacing.Xs))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconMail(Modifier.size(12.dp))
                    Spacer(Modifier.width(ClaySpacing.Xs))
                    Text(text = email, fontSize = 12.sp, color = WeMadeColors.OnSurface)
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ── Pemilih tahap ───────────────────────────────────────────────────
        Text(
            text = "Tahap Deal",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            DealStage.entries.forEach { stage ->
                val isSelected = stage == deal.stage
                ClayTag(
                    text = stage.displayName,
                    tint = if (isSelected) stage.tint() else WeMadeColors.OnSurfaceMuted,
                    modifier = if (isSelected) Modifier else Modifier.clickable {
                        onEvent(DealUiEvent.ChangeStage(stage))
                    }
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ── Daftar PO ───────────────────────────────────────────────────────
        Text(
            text = "Purchase Orders (${state.purchaseOrders.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        if (state.purchaseOrders.isEmpty()) {
            Text(
                text = "Belum ada PO. Tempelkan PO klien lewat upload berkas atau input manual di bawah.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        } else {
            state.purchaseOrders.forEach { po ->
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    outlineColor = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = po.poNumber.value,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(ClaySpacing.Sm))
                            ClayBadge(
                                text = po.origin.displayName,
                                tint = if (po.origin == com.eventverse.app.domain.deal.PoOrigin.UPLOADED) {
                                    WeMadeColors.Info
                                } else {
                                    WeMadeColors.Success
                                }
                            )
                        }
                        Text(
                            text = formatIdr(po.totalValueIdr),
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    if (po.hasFile) {
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        ClayActionSurface(onClick = { onEvent(DealUiEvent.OpenPoDownload(po.id.value)) }) {
                            Text(
                                text = "Unduh berkas PO (${po.fileName ?: "berkas"})",
                                fontSize = 11.sp,
                                color = WeMadeColors.Primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(ClaySpacing.Xs))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ── Form PO manual + tombol upload ──────────────────────────────────
        Text(
            text = "Tempel PO Baru",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poNumber,
            onValueChange = { poNumber = it },
            label = "Nomor PO",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poDescription,
            onValueChange = { poDescription = it },
            label = "Deskripsi item",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTextField(
                value = poQuantity,
                onValueChange = { poQuantity = it },
                label = "Qty",
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = poUnitPrice,
                onValueChange = { input -> poUnitPrice = input.filter { it.isDigit() } },
                label = "Harga satuan (Rp)",
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(ClaySpacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Simpan PO Manual",
                onClick = {
                    val qty = poQuantity.toDoubleOrNull() ?: 1.0
                    val price = poUnitPrice.toLongOrNull() ?: 0L
                    onEvent(
                        DealUiEvent.AttachManualPo(
                            poNumber = poNumber,
                            description = poDescription,
                            quantity = qty,
                            unitPriceIdr = price
                        )
                    )
                    poNumber = ""
                    poDescription = ""
                    poQuantity = "1"
                    poUnitPrice = ""
                },
                style = ClayButtonStyle.Primary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "Upload Berkas PO",
                onClick = { onEvent(DealUiEvent.UploadPoFile) },
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
        }

        state.statusMessage?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Success)
        }
        state.error?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Error)
        }
    }
}

private fun DealStage.tint() = when (this) {
    DealStage.OPEN -> WeMadeColors.Info
    DealStage.PO_RECEIVED -> WeMadeColors.Primary
    DealStage.IN_PRODUCTION -> WeMadeColors.Accent
    DealStage.WON -> WeMadeColors.Success
    DealStage.LOST -> WeMadeColors.Error
}

private fun formatIdr(amount: Long): String =
    "Rp" + amount.toString().reversed().chunked(3).joinToString(".").reversed()
