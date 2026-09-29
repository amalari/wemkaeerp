package com.eventverse.app.domain.contracts

/**
 * Base sealed interface for typed payloads passed between operational module ports in WeMade ERP.
 */
sealed interface ModulePortPayload {
    val portDataType: String
}

data class PortAdapterDescriptor(
    val from: String,
    val to: String,
    val isLossy: Boolean,
    val explanation: String
)
