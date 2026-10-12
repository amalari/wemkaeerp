package com.eventverse.app.presentation.fulfillment.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.shipment.CarrierInfo
import com.eventverse.app.domain.fulfillment.shipment.DestinationInfo
import com.eventverse.app.domain.fulfillment.shipment.DispatchShipment
import com.eventverse.app.domain.fulfillment.shipment.ShipmentId
import com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus
import com.eventverse.app.domain.fulfillment.shipment.StockOwnership
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayDialog
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Layar Daftar Surat Jalan — shipments / pengiriman eksternal (TRD-FUL-001 Track C).
 *
 * Menggunakan Clay design system — tidak ada komponen Material mentah.
 */
@Composable
fun ShipmentListScreen(
    canManage: Boolean = false,
    modifier: Modifier = Modifier,
    tenantSlug: String,
    viewModel: ShipmentListViewModel = remember(tenantSlug) {
        ShipmentListViewModel(tenantSlug = tenantSlug)
    }
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Header
        HeaderSection(
            issuedCount = state.issuedShipments.size,
            inTransitCount = state.inTransitShipments.size,
            deliveredCount = state.deliveredShipments.size,
            onCreateShipment = { viewModel.onEvent(ShipmentListUiEvent.ShowCreateDialog) }
        )

        // Error message
        state.error?.let { error ->
            ErrorCard(message = error, onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissError) })
        }

        // Success message
        state.successMessage?.let { message ->
            SuccessCard(message = message, onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissSuccess) })
        }

        // Loading
        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        }

        // Shipment list
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(state.shipments, key = { it.id.value }) { shipment ->
                ShipmentCard(
                    shipment = shipment,
                    canManage = canManage,
                    onMarkInTransit = {
                        viewModel.onEvent(ShipmentListUiEvent.ShowInTransitDialog(shipment.id))
                    },
                    onConfirmDelivered = {
                        viewModel.onEvent(ShipmentListUiEvent.ShowDeliveredDialog(shipment.id))
                    },
                    onCancel = {
                        viewModel.onEvent(ShipmentListUiEvent.ShowCancelDialog(shipment.id))
                    }
                )
            }
        }
    }

    // Dialog: Create Shipment
    if (state.showCreateDialog) {
        CreateShipmentDialog(
            onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissCreateDialog) },
            onConfirm = { stockOwnership, carrier, destination, notes ->
                viewModel.onEvent(
                    ShipmentListUiEvent.IssueShipment(
                        stockOwnership = stockOwnership,
                        carrier = carrier,
                        destination = destination,
                        cartonIds = emptyList(), // TODO: connect to available cartons
                        notes = notes
                    )
                )
            }
        )
    }

    // Dialog: Action (in-transit / delivered / cancel)
    state.showActionDialog?.let { dialogState ->
        when (dialogState.type) {
            ActionType.MARK_IN_TRANSIT -> ActionDialog(
                title = "Tandai Sedang Diantar",
                message = "Surat Jalan akan ditandai sebagai sedang dalam perjalanan.",
                onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissActionDialog) },
                onConfirm = { actor ->
                    viewModel.onEvent(ShipmentListUiEvent.MarkInTransit(dialogState.shipmentId.value, actor))
                }
            )
            ActionType.CONFIRM_DELIVERED -> ActionDialog(
                title = "Konfirmasi Terkirim",
                message = "Pastikan barang sudah diterima oleh penerima.",
                onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissActionDialog) },
                onConfirm = { actor ->
                    viewModel.onEvent(ShipmentListUiEvent.ConfirmDelivered(dialogState.shipmentId.value, actor))
                }
            )
            ActionType.CANCEL -> CancelDialog(
                onDismiss = { viewModel.onEvent(ShipmentListUiEvent.DismissActionDialog) },
                onConfirm = { reason, actor ->
                    viewModel.onEvent(
                        ShipmentListUiEvent.CancelShipment(
                            dialogState.shipmentId.value,
                            reason,
                            actor
                        )
                    )
                }
            )
        }
    }
}

@Composable
private fun HeaderSection(
    issuedCount: Int,
    inTransitCount: Int,
    deliveredCount: Int,
    onCreateShipment: () -> Unit
) {
    ClayCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    M3Text(
                        text = "SURAT JALAN EKSTERNAL",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    IconTruck(color = WeMadeColors.Primary)
                }
                M3Text(
                    text = "Kelola pengiriman ke buyer/ekspedisi.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayBadge(text = "Diterbitkan: $issuedCount", tint = WeMadeColors.Info)
                ClayBadge(text = "Diantar: $inTransitCount", tint = WeMadeColors.Warning)
                ClayBadge(text = "Terkirim: $deliveredCount", tint = WeMadeColors.Success)
                ClayButton(
                    text = "Buat SJ",
                    style = ClayButtonStyle.Accent,
                    onClick = onCreateShipment
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    ClayCard(
        containerColor = WeMadeColors.ErrorBg,
        outlineColor = WeMadeColors.Error
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3Text(
                text = message,
                fontSize = 11.sp,
                color = WeMadeColors.Error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "Tutup",
                style = ClayButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun SuccessCard(message: String, onDismiss: () -> Unit) {
    ClayCard(
        containerColor = WeMadeColors.SuccessBg,
        outlineColor = WeMadeColors.Success
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3Text(
                text = message,
                fontSize = 11.sp,
                color = WeMadeColors.Success,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "OK",
                style = ClayButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun ShipmentCard(
    shipment: DispatchShipment,
    canManage: Boolean,
    onMarkInTransit: () -> Unit,
    onConfirmDelivered: () -> Unit,
    onCancel: () -> Unit
) {
    val statusTint = when (shipment.status) {
        ShipmentStatus.ISSUED -> WeMadeColors.Info
        ShipmentStatus.IN_TRANSIT -> WeMadeColors.Warning
        ShipmentStatus.DELIVERED -> WeMadeColors.Success
        ShipmentStatus.CANCELLED -> WeMadeColors.Error
    }

    ClayCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    M3Text(
                        text = shipment.shipmentNumber.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    M3Text(
                        text = shipment.stockOwnership.displayName,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                ClayBadge(text = shipment.status.displayName, tint = statusTint)
            }

            // Destination
            if (shipment.destination.buyer.isNotBlank()) {
                Column {
                    M3Text(
                        text = "Penerima: ${shipment.destination.buyer}",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurface
                    )
                    if (shipment.destination.address.isNotBlank()) {
                        M3Text(
                            text = shipment.destination.address,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            // Carrier
            if (shipment.carrier.expedition.isNotBlank()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayBadge(
                        text = shipment.carrier.expedition,
                        tint = WeMadeColors.SurfaceMuted
                    )
                    if (shipment.carrier.trackingNumber.isNotBlank()) {
                        ClayBadge(
                            text = "Resi: ${shipment.carrier.trackingNumber}",
                            tint = WeMadeColors.SurfaceMuted
                        )
                    }
                }
            }

            // Timestamps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                M3Text(
                    text = "Dibuat: ${formatTimestamp(shipment.createdAt)}",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                if (shipment.shippedAt != null) {
                    M3Text(
                        text = "Dikirim: ${formatTimestamp(shipment.shippedAt!!)}",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                if (shipment.deliveredAt != null) {
                    M3Text(
                        text = "Diterima: ${formatTimestamp(shipment.deliveredAt!!)}",
                        fontSize = 10.sp,
                        color = WeMadeColors.Success
                    )
                }
            }

            // Actions
            if (shipment.status == ShipmentStatus.ISSUED) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canManage) {
                        ClayButton(
                            text = "Batal",
                            style = ClayButtonStyle.Secondary,
                            onClick = onCancel
                        )
                        Spacer(Modifier.width(ClaySpacing.Sm))
                    }
                    ClayButton(
                        text = "Tandai Diantar",
                        style = ClayButtonStyle.Primary,
                        onClick = onMarkInTransit
                    )
                }
            } else if (shipment.status == ShipmentStatus.IN_TRANSIT) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(
                        text = "Konfirmasi Terkirim",
                        style = ClayButtonStyle.Accent,
                        onClick = onConfirmDelivered
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateShipmentDialog(
    onDismiss: () -> Unit,
    onConfirm: (stockOwnership: StockOwnership, carrier: CarrierInfo, destination: DestinationInfo, notes: String) -> Unit
) {
    var selectedOwnership by remember { mutableStateOf(StockOwnership.OWNED) }
    var expedition by remember { mutableStateOf("") }
    var driver by remember { mutableStateOf("") }
    var trackingNumber by remember { mutableStateOf("") }
    var buyer by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ClayDialog(
        title = "Terbitkan Surat Jalan Baru",
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Stock Ownership
            M3Text(
                text = "Kepemilikan Stok",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                StockOwnership.entries.forEach { ownership ->
                    ClayButton(
                        text = ownership.displayName,
                        style = if (selectedOwnership == ownership) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        onClick = { selectedOwnership = ownership }
                    )
                }
            }

            // Carrier info
            M3Text(
                text = "Info Ekspedisi",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )
            ClayTextField(
                value = expedition,
                onValueChange = { expedition = it },
                label = "Nama Ekspedisi",
                placeholder = "JNE / POS / WAHA / dll",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = driver,
                onValueChange = { driver = it },
                label = "Nama Driver (opsional)",
                placeholder = "Nama driver",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = trackingNumber,
                onValueChange = { trackingNumber = it },
                label = "Nomor Resi (opsional)",
                placeholder = "Resi pengiriman",
                modifier = Modifier.fillMaxWidth()
            )

            // Destination info
            M3Text(
                text = "Tujuan",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface
            )
            ClayTextField(
                value = buyer,
                onValueChange = { buyer = it },
                label = "Nama Buyer/Penerima",
                placeholder = "PT Maju Jaya / Toko ABC",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = address,
                onValueChange = { address = it },
                label = "Alamat",
                placeholder = "Jl. xxx No. yyy, Kota",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = contact,
                onValueChange = { contact = it },
                label = "Kontak (opsional)",
                placeholder = "0812-xxxx-xxxx",
                modifier = Modifier.fillMaxWidth()
            )

            // Notes
            ClayTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Catatan (opsional)",
                placeholder = "Catatan untuk SJ ini...",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Terbitkan",
                    style = ClayButtonStyle.Primary,
                    enabled = expedition.isNotBlank() && buyer.isNotBlank(),
                    onClick = {
                        val carrier = CarrierInfo(
                            expedition = expedition,
                            driver = driver,
                            trackingNumber = trackingNumber
                        )
                        val dest = DestinationInfo(
                            buyer = buyer,
                            address = address,
                            contact = contact
                        )
                        onConfirm(selectedOwnership, carrier, dest, notes)
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (actor: String) -> Unit
) {
    var actor by remember { mutableStateOf("") }

    ClayDialog(
        title = title,
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            M3Text(
                text = message,
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            ClayTextField(
                value = actor,
                onValueChange = { actor = it },
                label = "Nama Petugas",
                placeholder = "Nama Anda",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Konfirmasi",
                    style = ClayButtonStyle.Primary,
                    enabled = actor.isNotBlank(),
                    onClick = { onConfirm(actor) }
                )
            }
        }
    }
}

@Composable
private fun CancelDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String, actor: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }

    ClayDialog(
        title = "Batal Surat Jalan",
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            M3Text(
                text = "Membatalkan SJ membutuhkan alasan dan wewenang MANAGE.",
                fontSize = 11.sp,
                color = WeMadeColors.Warning
            )
            ClayTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan Pembatalan",
                placeholder = "Mengapa SJ dibatalkan?",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            ClayTextField(
                value = actor,
                onValueChange = { actor = it },
                label = "Nama Manager",
                placeholder = "Nama Anda",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Batalkan SJ",
                    style = ClayButtonStyle.Primary,
                    enabled = reason.isNotBlank() && actor.isNotBlank(),
                    onClick = { onConfirm(reason, actor) }
                )
            }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.dayOfMonth}/${local.monthNumber}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}
