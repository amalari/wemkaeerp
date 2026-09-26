package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

enum class GarmentPanel(val displayName: String) {
    BODY_FRONT("Badan Depan"),
    BODY_BACK("Badan Belakang"),
    SLEEVE_LEFT("Lengan Kiri"),
    SLEEVE_RIGHT("Lengan Kanan"),
    COLLAR_RIB("Rib Leher / Kerah"),
    PLACKET("Placket / Kancing"),
    POCKET("Saku / Kantong"),
    BOTTOM_RIB("Rib Bawah / Pinggang"),
    CUFF_RIB("Rib Manset / Tangan"),
    OTHER("Panel Lainnya");
}

data class PanelYield(
    val panel: GarmentPanel,
    val weight: Quantity,
    val knittingMinutes: Long = 0L
)

data class SpecSizeMeasurement(
    val sizeLabel: String,
    val finishedMeasurements: Map<String, Double> = emptyMap(),
    val rawKnitMeasurements: Map<String, Double> = emptyMap()
)

data class ColorwayFeeder(
    val feederNumber: Int,
    val role: String,
    val ply: Int,
    val colorName: String,
    val yarn: MaterialRef
)

data class AdditionalProcess(
    val name: String,
    val material: MaterialRef? = null,
    val quantityPerGarment: Quantity? = null
)

data class ApprovedSampleSpecification(
    val sourceOrderId: String,
    val tenantId: TenantId,
    val spkNumber: String,
    val styleName: String,
    val clientName: String,
    val approvedAt: Instant,
    val sizeMode: String,
    val sizeCharts: List<SpecSizeMeasurement> = emptyList(),
    val panelYields: List<PanelYield> = emptyList(),
    val yarns: List<MaterialRef> = emptyList(),
    val colorways: List<ColorwayFeeder> = emptyList(),
    val isWashed: Boolean = false,
    val additionalProcesses: List<AdditionalProcess> = emptyList(),
    val legacyEstimatedHpp: Money? = null
) : ModulePortPayload {
    override val portDataType: String = PortDataTypeRegistry.APPROVED_SAMPLE_SPECIFICATION

    val totalPanelWeight: Quantity
        get() {
            var totalMicros = 0L
            for (p in panelYields) {
                totalMicros += p.weight.convertTo(UnitOfMeasure.GRAM).micros
            }
            return Quantity(totalMicros, UnitOfMeasure.GRAM)
        }

    val totalKnittingMinutes: Long
        get() = panelYields.sumOf { it.knittingMinutes }

    val unresolvedMaterials: List<MaterialRef>
        get() = (yarns + colorways.map { it.yarn } + additionalProcesses.mapNotNull { it.material })
            .filterNot { it.isResolved }
            .distinctBy { it.freeText }
}
