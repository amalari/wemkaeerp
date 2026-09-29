package com.eventverse.app.domain.contracts

/**
 * Base sealed interface for typed payloads passed between operational module ports in WeMade ERP.
 */
sealed interface ModulePortPayload {
    val portDataType: String
}

/**
 * Registry of known typed port contracts.
 */
object PortDataTypeRegistry {
    const val PRODUCTION_ORDER_DRAFT = "ProductionOrderDraft"
    const val APPROVED_SAMPLE_SPECIFICATION = "ApprovedSampleSpecification"
    const val TECH_PACK_AND_YIELD_DATA = "TechPackAndYieldData"
    const val MATERIAL_REQUISITION = "MaterialRequisition"
    const val VERIFIED_MATERIAL_STOCK = "VerifiedMaterialStock"
    const val COSTING_CALCULATION_RESULT = "CostingCalculationResult"
    const val CUT_PIECES_BUNDLE = "CutPiecesBundle"
    const val ASSEMBLED_GARMENT_BUNDLE = "AssembledGarmentBundle"
    const val INSPECTED_AND_GRADED_UNIT = "InspectedAndGradedUnit"
    const val DISPATCHED_SHIPMENT_MANIFEST = "DispatchedShipmentManifest"
    const val ISSUED_INVOICE_DOCUMENT = "IssuedInvoiceDocument"

    /**
     * Semua tipe port yang boleh dipakai katalog modul. Tipe baru wajib ditambahkan di sini —
     * `ModuleRegistrationConsistencyTest` gagal bila spec memakai tipe yang tak terdaftar.
     */
    val KNOWN_TYPED_LABELS: Set<String> = setOf(
        PRODUCTION_ORDER_DRAFT,
        APPROVED_SAMPLE_SPECIFICATION,
        TECH_PACK_AND_YIELD_DATA,
        MATERIAL_REQUISITION,
        VERIFIED_MATERIAL_STOCK,
        COSTING_CALCULATION_RESULT,
        CUT_PIECES_BUNDLE,
        ASSEMBLED_GARMENT_BUNDLE,
        INSPECTED_AND_GRADED_UNIT,
        DISPATCHED_SHIPMENT_MANIFEST,
        ISSUED_INVOICE_DOCUMENT
    )

    fun isTyped(label: String): Boolean = label in KNOWN_TYPED_LABELS
}

data class PortAdapterDescriptor(
    val from: String,
    val to: String,
    val isLossy: Boolean,
    val explanation: String
)
