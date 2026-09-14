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
    const val APPROVED_SAMPLE_SPECIFICATION = "ApprovedSampleSpecification"
    const val TECH_PACK_AND_YIELD_DATA = "TechPackAndYieldData"
    const val COSTING_CALCULATION_RESULT = "CostingCalculationResult"
    const val ISSUED_INVOICE_DOCUMENT = "IssuedInvoiceDocument"

    val KNOWN_TYPED_LABELS: Set<String> = setOf(
        APPROVED_SAMPLE_SPECIFICATION,
        TECH_PACK_AND_YIELD_DATA,
        COSTING_CALCULATION_RESULT,
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
