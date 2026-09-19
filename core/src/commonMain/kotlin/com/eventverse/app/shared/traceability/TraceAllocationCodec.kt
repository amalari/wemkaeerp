package com.eventverse.app.shared.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.shared.json.*

/** Codec potret SPK dan rencana pra-cetak — payload yang dikirim ke klien untuk layar cetak. */
object TraceAllocationCodec {

    fun encodeSnapshot(snapshot: TraceWorkOrderSnapshot): JsonValue.Obj = jsonObjectOf(
        "workOrderKind" to jsonOf(snapshot.ref.kind.name),
        "workOrderId" to jsonOf(snapshot.ref.id),
        "tenantId" to jsonOf(snapshot.tenantId.value),
        "tenantOrdinal" to jsonOf(snapshot.tenantOrdinal),
        "ordinal" to jsonOf(snapshot.ordinal),
        "spkNumber" to jsonOf(snapshot.spkNumber),
        "styleName" to jsonOf(snapshot.styleName),
        "clientName" to jsonOf(snapshot.clientName),
        "sizes" to jsonArrayOf(
            snapshot.sizes.map {
                jsonObjectOf("sizeLabel" to jsonOf(it.sizeLabel), "orderedPcs" to jsonOf(it.orderedPcs))
            }
        ),
        "panelRequirements" to jsonArrayOf(
            snapshot.panelRequirements.map {
                jsonObjectOf(
                    "panel" to jsonOf(it.panel.name),
                    "piecesPerGarment" to jsonOf(it.piecesPerGarment)
                )
            }
        ),
        "colorways" to jsonArrayOf(snapshot.colorways.map { jsonOf(it) })
    )

    fun decodeSnapshot(obj: JsonValue.Obj): TraceWorkOrderSnapshot = TraceWorkOrderSnapshot(
        ref = TraceWorkOrderRef(
            kind = enumOrNull<TraceWorkOrderKind>(obj.string("workOrderKind")) ?: TraceWorkOrderKind.SAMPLING,
            id = obj.string("workOrderId") ?: ""
        ),
        tenantId = TenantId(obj.string("tenantId") ?: ""),
        tenantOrdinal = obj.int("tenantOrdinal") ?: 0,
        ordinal = obj.int("ordinal") ?: 0,
        spkNumber = obj.string("spkNumber") ?: "",
        styleName = obj.string("styleName") ?: "",
        clientName = obj.string("clientName") ?: "",
        sizes = obj.objectArray("sizes").mapNotNull { row ->
            val label = row.string("sizeLabel")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TraceSizeLine(label, (row.int("orderedPcs") ?: 0).coerceAtLeast(0))
        },
        panelRequirements = obj.objectArray("panelRequirements").mapNotNull { row ->
            val panel = enumOrNull<GarmentPanel>(row.string("panel")) ?: return@mapNotNull null
            val per = row.int("piecesPerGarment") ?: return@mapNotNull null
            if (per <= 0) null else PanelRequirement(panel, per)
        },
        colorways = obj.stringArray("colorways")
    )

    fun encodePlan(plan: TraceAllocationPlan): JsonValue.Obj = jsonObjectOf(
        "snapshot" to encodeSnapshot(plan.snapshot),
        "setsPerBundle" to jsonOf(plan.setsPerBundle),
        "pcsPerSack" to jsonOf(plan.pcsPerSack),
        "sheetCountEstimate" to jsonOf(plan.sheetCountEstimate),
        "labels" to jsonArrayOf(
            plan.labels.map {
                jsonObjectOf(
                    "code" to jsonOf(it.code.value),
                    "humanCode" to jsonOf(it.humanCode),
                    "caption" to jsonOf(it.captionFor(plan.snapshot.spkNumber)),
                    "tier" to jsonOf(it.tier.name),
                    "sizeLabel" to jsonOf(it.sizeLabel),
                    "sizeIndex" to jsonOf(it.sizeIndex),
                    "sequence" to jsonOf(it.sequence),
                    "targetCapacity" to jsonOf(it.targetCapacity)
                )
            }
        )
    )

    fun encodeReconciliation(result: TraceReconciliation): JsonValue.Obj = jsonObjectOf(
        "workOrderKind" to jsonOf(result.workOrder.kind.name),
        "workOrderId" to jsonOf(result.workOrder.id),
        "spkNumber" to jsonOf(result.spkNumber),
        "totalOrderedPcs" to jsonOf(result.totalOrderedPcs),
        "totalSackPcs" to jsonOf(result.totalSackPcs),
        "totalShrinkagePcs" to jsonOf(result.totalShrinkagePcs),
        "wipPieces" to jsonOf(result.wipPieces),
        "perSize" to jsonArrayOf(
            result.perSize.map { row ->
                jsonObjectOf(
                    "sizeLabel" to jsonOf(row.sizeLabel),
                    "orderedPcs" to jsonOf(row.orderedPcs),
                    "bundledSets" to jsonOf(row.bundledSets),
                    "consumedSets" to jsonOf(row.consumedSets),
                    "sackPcs" to jsonOf(row.sackPcs),
                    "pendingSets" to jsonOf(row.pendingSets),
                    "shrinkagePcs" to jsonOf(row.shrinkagePcs),
                    "leftoverPanels" to jsonArrayOf(
                        row.leftoverPanels.map {
                            jsonObjectOf(
                                "panel" to jsonOf(it.panel.name),
                                "panelLabel" to jsonOf(it.panel.displayName),
                                "pieces" to jsonOf(it.pieces)
                            )
                        }
                    )
                )
            }
        )
    )

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
