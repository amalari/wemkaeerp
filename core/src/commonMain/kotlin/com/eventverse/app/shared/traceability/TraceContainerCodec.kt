package com.eventverse.app.shared.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

/**
 * Codec wadah telusur. Cermin domain, bukan sebaliknya — kalau bentuk JSON dan domain berselisih,
 * yang salah adalah codec-nya.
 */
object TraceContainerCodec {

    private val EPOCH = Instant.fromEpochMilliseconds(0)

    fun encode(container: TraceContainer): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(container.id.value),
        "tenantId" to jsonOf(container.tenantId.value),
        "code" to jsonOf(container.code.value),
        "workOrderKind" to jsonOf(container.workOrder.kind.name),
        "workOrderId" to jsonOf(container.workOrder.id),
        "tier" to jsonOf(container.tier.name),
        "sizeLabel" to jsonOf(container.sizeLabel),
        "colorway" to jsonOf(container.colorway),
        "state" to jsonOf(container.state.name),
        "panelTallies" to jsonArrayOf(container.panelTallies.map(::encodeTally)),
        "declaredPcs" to jsonOf(container.declaredPcs),
        "weightKg" to jsonOf(container.weightKg),
        "operatorName" to jsonOf(container.operatorName),
        "shift" to jsonOf(container.shift.value),
        "recordedAt" to jsonOf(container.recordedAt.toString()),
        "createdAt" to jsonOf(container.createdAt.toString()),
        "updatedAt" to jsonOf(container.updatedAt.toString()),
        "notes" to jsonOf(container.notes)
    )

    fun decode(obj: JsonValue.Obj): TraceContainer {
        val createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), EPOCH)
        return TraceContainer(
            id = TraceContainerId(obj.string("id") ?: ""),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            code = TraceCode(obj.string("code") ?: ""),
            workOrder = TraceWorkOrderRef(
                kind = enumOrNull<TraceWorkOrderKind>(obj.string("workOrderKind"))
                    ?: TraceWorkOrderKind.SAMPLING,
                id = obj.string("workOrderId") ?: ""
            ),
            tier = enumOrNull<TraceTier>(obj.string("tier")) ?: TraceTier.BUNDLE,
            sizeLabel = obj.string("sizeLabel") ?: "",
            colorway = obj.string("colorway") ?: "",
            state = enumOrNull<TraceContainerState>(obj.string("state")) ?: TraceContainerState.OPENED,
            panelTallies = obj.objectArray("panelTallies").mapNotNull(::decodeTally),
            declaredPcs = obj.int("declaredPcs") ?: 0,
            weightKg = obj.double("weightKg") ?: 0.0,
            operatorName = obj.string("operatorName") ?: "",
            shift = ShiftLabel(obj.string("shift") ?: ""),
            recordedAt = DateTimeCodec.parseInstantOrFallback(obj.string("recordedAt"), createdAt),
            createdAt = createdAt,
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), createdAt),
            notes = obj.string("notes") ?: ""
        )
    }

    fun encodeLink(link: TraceContainerLink): JsonValue.Obj = jsonObjectOf(
        "tenantId" to jsonOf(link.tenantId.value),
        "parentId" to jsonOf(link.parentId.value),
        "childId" to jsonOf(link.childId.value),
        "consumedPcs" to jsonOf(link.consumedPcs),
        "linkedAt" to jsonOf(link.linkedAt.toString())
    )

    fun decodeLink(obj: JsonValue.Obj): TraceContainerLink? {
        val parent = obj.string("parentId")?.takeIf { it.isNotBlank() } ?: return null
        val child = obj.string("childId")?.takeIf { it.isNotBlank() } ?: return null
        val pcs = obj.int("consumedPcs") ?: return null
        if (pcs <= 0 || parent == child) return null
        return TraceContainerLink(
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            parentId = TraceContainerId(parent),
            childId = TraceContainerId(child),
            consumedPcs = pcs,
            linkedAt = DateTimeCodec.parseInstantOrFallback(obj.string("linkedAt"), EPOCH)
        )
    }

    private fun encodeTally(tally: PanelTally): JsonValue.Obj = jsonObjectOf(
        "panel" to jsonOf(tally.panel.name),
        "pieces" to jsonOf(tally.pieces)
    )

    private fun decodeTally(obj: JsonValue.Obj): PanelTally? {
        val panel = enumOrNull<GarmentPanel>(obj.string("panel")) ?: return null
        val pieces = obj.int("pieces") ?: return null
        if (pieces < 0) return null
        return PanelTally(panel, pieces)
    }

    private inline fun <reified T : Enum<T>> enumOrNull(raw: String?): T? =
        raw?.let { value -> enumValues<T>().firstOrNull { it.name == value } }
}
