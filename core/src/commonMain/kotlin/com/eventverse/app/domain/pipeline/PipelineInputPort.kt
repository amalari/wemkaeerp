package com.eventverse.app.domain.pipeline

/**
 * Represents an individual input requirement or data port for a pipeline node,
 * inspired by node-based workflow engines (such as n8n).
 *
 * Inputs are categorized into two types:
 * 1. Automated: Data streamed directly from the output of an upstream module.
 * 2. Manual: Data or physical artifacts entered/uploaded by a human operator in the factory.
 */
data class PipelineInputPort(
    val id: String,
    val name: String,
    val isManual: Boolean,
    val operatorRole: String? = null,
    val inputMethod: String? = null,
    val sourceModuleCode: String? = null,
    val sourceModuleName: String? = null,
    val sourceOutputContract: String? = null,
    val description: String = "",
    val isRequired: Boolean = true
) {
    val isAutomated: Boolean get() = !isManual
}
