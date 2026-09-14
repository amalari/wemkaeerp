package com.eventverse.app.shared.contracts

import com.eventverse.app.domain.contracts.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Instant

object ApprovedSampleSpecificationCodec {

    fun encode(spec: ApprovedSampleSpecification): JsonValue.Obj = jsonObjectOf(
        "sourceOrderId" to jsonOf(spec.sourceOrderId),
        "tenantId" to jsonOf(spec.tenantId.value),
        "spkNumber" to jsonOf(spec.spkNumber),
        "styleName" to jsonOf(spec.styleName),
        "clientName" to jsonOf(spec.clientName),
        "approvedAt" to jsonOf(spec.approvedAt.toString()),
        "sizeMode" to jsonOf(spec.sizeMode),
        "sizeCharts" to jsonArrayOf(spec.sizeCharts.map(::encodeSizeChart)),
        "panelYields" to jsonArrayOf(spec.panelYields.map(::encodePanelYield)),
        "yarns" to jsonArrayOf(spec.yarns.map(MaterialRefCodec::encode)),
        "colorways" to jsonArrayOf(spec.colorways.map(::encodeColorway)),
        "isWashed" to jsonOf(spec.isWashed),
        "additionalProcesses" to jsonArrayOf(spec.additionalProcesses.map(::encodeAdditionalProcess)),
        "legacyEstimatedHpp" to (spec.legacyEstimatedHpp?.let(MeasureCodec::encodeMoney) ?: JsonValue.Null)
    )

    fun decode(obj: JsonValue.Obj): ApprovedSampleSpecification {
        val sourceOrderId = obj.string("sourceOrderId") ?: ""
        val tenantId = TenantId(obj.string("tenantId") ?: "")
        val spkNumber = obj.string("spkNumber") ?: ""
        val styleName = obj.string("styleName") ?: ""
        val clientName = obj.string("clientName") ?: ""
        val approvedAt = obj.string("approvedAt")?.let { runCatching { Instant.parse(it) }.getOrNull() }
            ?: kotlinx.datetime.Clock.System.now()
        val sizeMode = obj.string("sizeMode") ?: "ALL_SIZE"

        val sizeCharts = obj.objectArray("sizeCharts").map(::decodeSizeChart)
        val panelYields = obj.objectArray("panelYields").map(::decodePanelYield)
        val yarns = obj.objectArray("yarns").map(MaterialRefCodec::decode)
        val colorways = obj.objectArray("colorways").map(::decodeColorway)
        val isWashed = obj.boolean("isWashed") ?: false
        val additionalProcesses = obj.objectArray("additionalProcesses").map(::decodeAdditionalProcess)
        val legacyHpp = obj.obj("legacyEstimatedHpp")?.let(MeasureCodec::decodeMoney)

        return ApprovedSampleSpecification(
            sourceOrderId = sourceOrderId,
            tenantId = tenantId,
            spkNumber = spkNumber,
            styleName = styleName,
            clientName = clientName,
            approvedAt = approvedAt,
            sizeMode = sizeMode,
            sizeCharts = sizeCharts,
            panelYields = panelYields,
            yarns = yarns,
            colorways = colorways,
            isWashed = isWashed,
            additionalProcesses = additionalProcesses,
            legacyEstimatedHpp = legacyHpp
        )
    }

    private fun encodePanelYield(yield: PanelYield): JsonValue.Obj = jsonObjectOf(
        "panel" to jsonOf(yield.panel.name),
        "weight" to MeasureCodec.encodeQuantity(yield.weight),
        "knittingMinutes" to jsonOf(yield.knittingMinutes)
    )

    private fun decodePanelYield(obj: JsonValue.Obj): PanelYield {
        val panel = obj.string("panel")?.let { runCatching { GarmentPanel.valueOf(it) }.getOrNull() } ?: GarmentPanel.OTHER
        val weight = MeasureCodec.decodeQuantity(obj.obj("weight"))
        val minutes = obj.long("knittingMinutes") ?: 0L
        return PanelYield(panel, weight, minutes)
    }

    private fun encodeSizeChart(chart: SpecSizeMeasurement): JsonValue.Obj = jsonObjectOf(
        "sizeLabel" to jsonOf(chart.sizeLabel),
        "finishedMeasurements" to JsonValue.Obj(chart.finishedMeasurements.mapValues { jsonOf(it.value) }),
        "rawKnitMeasurements" to JsonValue.Obj(chart.rawKnitMeasurements.mapValues { jsonOf(it.value) })
    )

    private fun decodeSizeChart(obj: JsonValue.Obj): SpecSizeMeasurement {
        val label = obj.string("sizeLabel") ?: "ALL SIZE"
        val finished = obj.obj("finishedMeasurements")?.entries?.mapNotNull { (k, v) ->
            (v as? JsonValue.Num)?.asDouble?.let { k to it }
        }?.toMap() ?: emptyMap()

        val raw = obj.obj("rawKnitMeasurements")?.entries?.mapNotNull { (k, v) ->
            (v as? JsonValue.Num)?.asDouble?.let { k to it }
        }?.toMap() ?: emptyMap()

        return SpecSizeMeasurement(label, finished, raw)
    }

    private fun encodeColorway(cw: ColorwayFeeder): JsonValue.Obj = jsonObjectOf(
        "feederNumber" to jsonOf(cw.feederNumber),
        "role" to jsonOf(cw.role),
        "ply" to jsonOf(cw.ply),
        "colorName" to jsonOf(cw.colorName),
        "yarn" to MaterialRefCodec.encode(cw.yarn)
    )

    private fun decodeColorway(obj: JsonValue.Obj): ColorwayFeeder = ColorwayFeeder(
        feederNumber = obj.int("feederNumber") ?: 1,
        role = obj.string("role") ?: "",
        ply = obj.int("ply") ?: 1,
        colorName = obj.string("colorName") ?: "",
        yarn = obj.obj("yarn")?.let(MaterialRefCodec::decode) ?: MaterialRef.unresolved("")
    )

    private fun encodeAdditionalProcess(ap: AdditionalProcess): JsonValue.Obj = jsonObjectOf(
        "name" to jsonOf(ap.name),
        "material" to (ap.material?.let(MaterialRefCodec::encode) ?: JsonValue.Null),
        "quantityPerGarment" to (ap.quantityPerGarment?.let(MeasureCodec::encodeQuantity) ?: JsonValue.Null)
    )

    private fun decodeAdditionalProcess(obj: JsonValue.Obj): AdditionalProcess = AdditionalProcess(
        name = obj.string("name") ?: "",
        material = obj.obj("material")?.let(MaterialRefCodec::decode),
        quantityPerGarment = obj.obj("quantityPerGarment")?.let(MeasureCodec::decodeQuantity)
    )
}
