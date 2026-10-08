package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.legacyRouteLabel
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.SackTransferStatus
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Warna status diambil dari palet sinyal produksi — bukan dekoratif: hijau berarti aman,
 * amber berarti menunggu orang, merah berarti ada masalah yang harus dibuka.
 */
internal fun transferStatusTint(status: SackTransferStatus): Color = when (status) {
    SackTransferStatus.MENUNGGU_ACC -> WeMadeColors.Warning
    SackTransferStatus.DIANTAR -> WeMadeColors.Info
    SackTransferStatus.DITERIMA -> WeMadeColors.Success
    SackTransferStatus.DITERIMA_SELISIH -> WeMadeColors.Accent
    SackTransferStatus.DITOLAK -> WeMadeColors.Error
    SackTransferStatus.DIPERIKSA -> WeMadeColors.Warning
}

/**
 * Satu kartu = satu perjalanan karung. Semua aksi kontekstual (ACC, terima, ajukan ulang)
 * hidup inline di kartu ini sesuai status dan wewenang — bukan di layar lain.
 */
@Composable
fun TransferCard(
    transfer: InternalTransfer,
    canApprove: Boolean,
    canWork: Boolean,
    isSubmitting: Boolean,
    onEvent: (FulfillmentUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(modifier = modifier.fillMaxWidth()) {
        TransferHeader(transfer)
        TransferFacts(transfer)
        TransferFootnote(transfer)

        when {
            // Mode ikut menentukan, bukan status saja: perjalanan DIRECT tidak pernah punya
            // meja admin untuk menyetujuinya, dan domain menolak approve() atasnya.
            transfer.handoverMode == HandoverMode.ADMIN_HUB &&
                transfer.status == SackTransferStatus.MENUNGGU_ACC && canApprove ->
                ApproveSection(
                    isSubmitting = isSubmitting,
                    onDecide = { approved, name, signature, reason ->
                        onEvent(
                            if (approved) FulfillmentUiEvent.Approve(transfer.id.value, name, signature)
                            else FulfillmentUiEvent.Reject(transfer.id.value, reason, name)
                        )
                    }
                )

            transfer.status == SackTransferStatus.DIANTAR && canWork ->
                ReceiveSection(
                    declaredPcs = transfer.declaredPcs,
                    isSubmitting = isSubmitting,
                    onReceive = { proofPayload, receivedWeight, receivedPcs, recordedBy ->
                        onEvent(
                            FulfillmentUiEvent.Receive(
                                transferId = transfer.id.value,
                                proof = proofPayload,
                                receivedWeightKg = receivedWeight,
                                receivedPcs = receivedPcs,
                                recordedBy = recordedBy
                            )
                        )
                    },
                    onUploadEvidence = { fileName, mime, bytes, onDone ->
                        onEvent(FulfillmentUiEvent.UploadEvidence(fileName, mime, bytes, onDone))
                    }
                )

            transfer.status == SackTransferStatus.DITOLAK && canWork ->
                ResubmitSection(
                    isSubmitting = isSubmitting,
                    onResubmit = { weight, photoKey, requestedBy ->
                        onEvent(
                            FulfillmentUiEvent.Resubmit(transfer.id.value, weight, photoKey, requestedBy)
                        )
                    },
                    onUploadEvidence = { fileName, mime, bytes, onDone ->
                        onEvent(FulfillmentUiEvent.UploadEvidence(fileName, mime, bytes, onDone))
                    }
                )

            transfer.handoverMode == HandoverMode.ADMIN_HUB &&
                transfer.status == SackTransferStatus.MENUNGGU_ACC -> WaitingNote()
        }
    }
}

@Composable
private fun TransferHeader(transfer: InternalTransfer) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Kode yang boleh menyusut; badge status yang tidak boleh — Kontrak 13.
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = transfer.humanCode,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                // Mode disebut di sini supaya kartu DIRECT tidak terbaca sebagai kartu yang
                // "lupa di-ACC" — dua hal yang kalau tertukar menuntun admin mencari tombol
                // persetujuan yang memang tidak pernah ada.
                text = "${legacyRouteLabel(transfer.route)} · ${transfer.handoverMode.displayName}",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        ClayBadge(text = transfer.status.displayName, tint = transferStatusTint(transfer.status))
    }
}

@Composable
private fun TransferFacts(transfer: InternalTransfer) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        ClayTag(text = "${transfer.declaredPcs} pcs", tint = WeMadeColors.Primary)
        ClayTag(text = "Size ${transfer.sizeLabel}", tint = WeMadeColors.Primary)
        if (transfer.colorway.isNotBlank()) {
            ClayTag(text = transfer.colorway, tint = WeMadeColors.Primary)
        }
        // Tidak ada timbangan pada perjalanan antar langsung — tag-nya hilang, bukan menampilkan
        // "0.00 kg" yang terbaca seperti karung kosong.
        transfer.dispatchWeightKg?.let { ClayTag(text = it.formatted(), tint = WeMadeColors.Info) }
    }
}

@Composable
private fun TransferFootnote(transfer: InternalTransfer) {
    Text(
        text = "Dibawa oleh ${transfer.requestedBy}" +
            (transfer.approvedBy?.let { " · ACC oleh $it" } ?: ""),
        fontSize = 10.sp,
        color = WeMadeColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = ClaySpacing.Xs)
    )

    transfer.rejectReason?.let { reason ->
        Text(
            text = "Ditolak: $reason",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.Error,
            modifier = Modifier.padding(top = ClaySpacing.Xs)
        )
    }

    when (val proof = transfer.handover) {
        is HandoverProof.ReceiverHandover ->
            HandoverNote(
                text = "Diterima oleh ${proof.receiverName}" +
                    (transfer.receivedPcs?.let { " · fisik $it pcs" } ?: ""),
                color = if (transfer.status == SackTransferStatus.DITERIMA_SELISIH) WeMadeColors.Accent
                else WeMadeColors.Success
            )

        is HandoverProof.CourierShipment ->
            HandoverNote(
                text = "Resi ${proof.trackingNumber} (${proof.carrier}) · ${proof.chargeableWeightKg.formatted()}",
                color = if (transfer.status == SackTransferStatus.DITERIMA_SELISIH) WeMadeColors.Accent
                else WeMadeColors.Success
            )

        null -> Unit
    }
}

@Composable
private fun HandoverNote(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 11.sp,
        color = color,
        modifier = Modifier.padding(top = ClaySpacing.Xs)
    )
}

@Composable
private fun WaitingNote() {
    HorizontalDivider(
        modifier = Modifier.padding(top = ClaySpacing.Md),
        color = WeMadeColors.Outline.copy(alpha = 0.4f)
    )
    Text(
        text = "Menunggu keputusan admin produksi.",
        fontSize = 10.sp,
        color = WeMadeColors.OnSurfaceMuted,
        modifier = Modifier.padding(top = ClaySpacing.Xs)
    )
}
