package com.eventverse.app.shared.contracts

import com.eventverse.app.domain.contracts.*
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object TechPackAndYieldDataCodec {

    fun encode(data: TechPackAndYieldData): JsonValue.Obj = jsonObjectOf(
        "techPackId" to jsonOf(data.techPackId),
        "tenantId" to jsonOf(data.tenantId.value),
        "sourceSampleSpecId" to jsonOf(data.sourceSampleSpecId),
        "styleCode" to jsonOf(data.styleCode),
        "styleName" to jsonOf(data.styleName),
        "bomLines" to jsonArrayOf(data.bomLines.map(::encodeBomLine)),
        "laborOperations" to jsonArrayOf(data.laborOperations.map(::encodeLaborOperation)),
        "sizeYieldFactors" to jsonArrayOf(data.sizeYieldFactors.map(::encodeSizeYieldFactor)),
        "preparedAt" to jsonOf(data.preparedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): TechPackAndYieldData {
        val techPackId = obj.string("techPackId") ?: ""
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val sourceSampleSpecId = obj.string("sourceSampleSpecId")
        val styleCode = obj.string("styleCode") ?: ""
        val styleName = obj.string("styleName") ?: ""
        val bomLines = obj.objectArray("bomLines").map(::decodeBomLine)
        val laborOps = obj.objectArray("laborOperations").map(::decodeLaborOperation)
        val sizeFactors = obj.objectArray("sizeYieldFactors").map(::decodeSizeYieldFactor)
        val preparedAt = com.eventverse.app.shared.common.DateTimeCodec.parseInstantOrFallback(
            obj.string("preparedAt"),
            kotlinx.datetime.Clock.System.now()
        )

        return TechPackAndYieldData(
            techPackId = techPackId,
            tenantId = tenantId,
            sourceSampleSpecId = sourceSampleSpecId,
            styleCode = styleCode,
            styleName = styleName,
            bomLines = bomLines,
            laborOperations = laborOps,
            sizeYieldFactors = sizeFactors,
            preparedAt = preparedAt
        )
    }

    fun encodeBomLine(line: BomLine): JsonValue.Obj = jsonObjectOf(
        "lineId" to jsonOf(line.lineId),
        "material" to MaterialRefCodec.encode(line.material),
        "category" to jsonOf(line.category.code),
        "netQuantityPerGarment" to MeasureCodec.encodeQuantity(line.netQuantityPerGarment),
        "wasteAllowance" to MeasureCodec.encodeRatio(line.wasteAllowance),
        "ownership" to jsonOf(line.ownership.code),
        "notes" to jsonOf(line.notes)
    )

    fun decodeBomLine(obj: JsonValue.Obj): BomLine = BomLine(
        lineId = obj.string("lineId") ?: "",
        material = obj.obj("material")?.let(MaterialRefCodec::decode) ?: MaterialRef.unresolved(""),
        category = MaterialCategory.fromCode(obj.string("category")) ?: MaterialCategory.YARN,
        netQuantityPerGarment = MeasureCodec.decodeQuantity(obj.obj("netQuantityPerGarment")),
        wasteAllowance = MeasureCodec.decodeRatio(obj.obj("wasteAllowance")),
        ownership = obj.string("ownership")?.let { codeStr ->
            StockOwnershipSemantics.entries.firstOrNull { it.code == codeStr || it.name.equals(codeStr, ignoreCase = true) }
        } ?: StockOwnershipSemantics.OWNED_RAW_MATERIAL,
        notes = obj.string("notes") ?: ""
    )

    fun encodeLaborOperation(op: LaborOperation): JsonValue.Obj = jsonObjectOf(
        "operationId" to jsonOf(op.operationId),
        "name" to jsonOf(op.name),
        "samMinutes" to MeasureCodec.encodeRatio(op.samMinutes),
        "workstation" to jsonOf(op.workstation),
        "isSubcontracted" to jsonOf(op.isSubcontracted)
    )

    fun decodeLaborOperation(obj: JsonValue.Obj): LaborOperation = LaborOperation(
        operationId = obj.string("operationId") ?: "",
        name = obj.string("name") ?: "",
        samMinutes = MeasureCodec.decodeRatio(obj.obj("samMinutes")),
        workstation = obj.string("workstation") ?: "",
        isSubcontracted = obj.boolean("isSubcontracted") ?: false
    )

    fun encodeSizeYieldFactor(f: SizeYieldFactor): JsonValue.Obj = jsonObjectOf(
        "sizeLabel" to jsonOf(f.sizeLabel),
        "scale" to MeasureCodec.encodeRatio(f.scale),
        "orderedQuantity" to jsonOf(f.orderedQuantity)
    )

    fun decodeSizeYieldFactor(obj: JsonValue.Obj): SizeYieldFactor = SizeYieldFactor(
        sizeLabel = obj.string("sizeLabel") ?: "ALL SIZE",
        scale = MeasureCodec.decodeRatio(obj.obj("scale")),
        orderedQuantity = obj.long("orderedQuantity") ?: 0L
    )
}
