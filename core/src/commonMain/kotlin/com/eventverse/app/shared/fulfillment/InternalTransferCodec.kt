package com.eventverse.app.shared.fulfillment

import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.InternalTransfer
import com.eventverse.app.domain.fulfillment.TransferId
import com.eventverse.app.domain.fulfillment.TransferLeg
import com.eventverse.app.domain.fulfillment.TransferStatus
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.TraceCode
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Instant

/**
 * Codec transfer karung. Cermin domain, bukan sebaliknya — kalau bentuk JSON dan domain
 * berselisih, yang salah adalah codec-nya.
 */
object InternalTransferCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encode(transfer: InternalTransfer): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(transfer.id.value),
        "tenantId" to jsonOf(transfer.tenantId.value),
        "sackCode" to jsonOf(transfer.sackCode.value),
        "humanCode" to jsonOf(transfer.humanCode),
        "workOrderKind" to (transfer.workOrder?.let { jsonOf(it.kind.name) } ?: JsonValue.Null),
        "workOrderId" to (transfer.workOrder?.let { jsonOf(it.id) } ?: JsonValue.Null),
        "sizeLabel" to jsonOf(transfer.sizeLabel),
        "colorway" to jsonOf(transfer.colorway),
        "declaredPcs" to jsonOf(transfer.declaredPcs),
        "leg" to jsonOf(transfer.leg.name),
        "legLabel" to jsonOf(transfer.leg.displayName),
        "status" to jsonOf(transfer.status.name),
        "statusLabel" to jsonOf(transfer.status.displayName),
        "dispatchWeightKg" to jsonOf(transfer.dispatchWeightKg.value),
        "dispatchScalePhotoKey" to jsonOf(transfer.dispatchScalePhotoKey),
        "requestedBy" to jsonOf(transfer.requestedBy),
        "requestedAt" to jsonOf(transfer.requestedAt.toString()),
        "approvedBy" to (transfer.approvedBy?.let(::jsonOf) ?: JsonValue.Null),
        "approvedAt" to (transfer.approvedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "approvalSignatureKey" to (transfer.approvalSignatureKey?.let(::jsonOf) ?: JsonValue.Null),
        "rejectedBy" to (transfer.rejectedBy?.let(::jsonOf) ?: JsonValue.Null),
        "rejectReason" to (transfer.rejectReason?.let(::jsonOf) ?: JsonValue.Null),
        "handover" to (transfer.handover?.let(::encodeProof) ?: JsonValue.Null),
        "receivedWeightKg" to (transfer.receivedWeightKg?.let { jsonOf(it.value) } ?: JsonValue.Null),
        "receivedPcs" to (transfer.receivedPcs?.let(::jsonOf) ?: JsonValue.Null),
        "receivedAt" to (transfer.receivedAt?.let { jsonOf(it.toString()) } ?: JsonValue.Null),
        "notes" to jsonOf(transfer.notes)
    )

    fun decode(obj: JsonValue.Obj): InternalTransfer {
        val requestedAt = DateTimeCodec.parseInstantOrFallback(obj.string("requestedAt"), EPOCH)
        return InternalTransfer(
            id = TransferId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            sackCode = TraceCode(obj.string("sackCode") ?: ""),
            workOrder = decodeWorkOrder(obj),
            sizeLabel = obj.string("sizeLabel") ?: "",
            colorway = obj.string("colorway") ?: "",
            declaredPcs = obj.int("declaredPcs") ?: 0,
            leg = enumOrNull<TransferLeg>(obj.string("leg")) ?: TransferLeg.QC_RAJUT_TO_FINISHING,
            status = enumOrNull<TransferStatus>(obj.string("status")) ?: TransferStatus.MENUNGGU_ACC,
            dispatchWeightKg = WeightKg(obj.double("dispatchWeightKg") ?: 0.0),
            dispatchScalePhotoKey = obj.string("dispatchScalePhotoKey") ?: "",
            requestedBy = obj.string("requestedBy") ?: "",
            requestedAt = requestedAt,
            approvedBy = obj.string("approvedBy"),
            approvedAt = obj.string("approvedAt")?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            approvalSignatureKey = obj.string("approvalSignatureKey"),
            rejectedBy = obj.string("rejectedBy"),
            rejectReason = obj.string("rejectReason"),
            handover = obj.obj("handover")?.let(::decodeProof),
            receivedWeightKg = obj.double("receivedWeightKg")?.let { WeightKg(it) },
            receivedPcs = obj.int("receivedPcs"),
            receivedAt = obj.string("receivedAt")?.takeIf { it.isNotBlank() }?.let(Instant::parse),
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), requestedAt),
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), requestedAt),
            notes = obj.string("notes") ?: ""
        )
    }

    private fun encodeProof(proof: HandoverProof): JsonValue.Obj = when (proof) {
        is HandoverProof.ReceiverHandover -> jsonObjectOf(
            "type" to jsonOf("RECEIVER"),
            "receiverName" to jsonOf(proof.receiverName),
            "signatureKey" to jsonOf(proof.signatureKey),
            "evidencePhotoKey" to jsonOf(proof.evidencePhotoKey)
        )
        is HandoverProof.CourierShipment -> jsonObjectOf(
            "type" to jsonOf("COURIER"),
            "carrier" to jsonOf(proof.carrier),
            "trackingNumber" to jsonOf(proof.trackingNumber),
            "chargeableWeightKg" to jsonOf(proof.chargeableWeightKg.value),
            "evidencePhotoKey" to jsonOf(proof.evidencePhotoKey)
        )
    }

    private fun decodeProof(obj: JsonValue.Obj): HandoverProof? = when (obj.string("type")) {
        "RECEIVER" -> HandoverProof.ReceiverHandover(
            receiverName = obj.string("receiverName") ?: "",
            signatureKey = obj.string("signatureKey") ?: "",
            evidencePhotoKey = obj.string("evidencePhotoKey") ?: ""
        )
        "COURIER" -> obj.double("chargeableWeightKg")?.let { weight ->
            HandoverProof.CourierShipment(
                carrier = obj.string("carrier") ?: "",
                trackingNumber = obj.string("trackingNumber") ?: "",
                chargeableWeightKg = WeightKg(weight),
                evidencePhotoKey = obj.string("evidencePhotoKey") ?: ""
            )
        }
        else -> null
    }

    private fun decodeWorkOrder(obj: JsonValue.Obj): TraceWorkOrderRef? {
        val kind = enumOrNull<TraceWorkOrderKind>(obj.string("workOrderKind")) ?: return null
        val id = obj.string("workOrderId")?.takeIf { it.isNotBlank() } ?: return null
        return TraceWorkOrderRef(kind, id)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { candidate -> enumValues<T>().firstOrNull { it.name == candidate } }
}
