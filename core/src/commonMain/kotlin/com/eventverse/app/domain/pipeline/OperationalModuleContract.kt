package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.tenant.TenantId

/**
 * Semantics of stock ownership for raw materials and finished garments.
 * Crucial differentiator between FOB, CMT, and Brand D2C operations.
 */
enum class StockOwnershipSemantics(
    val code: String,
    val displayName: String,
    val hasFinancialAssetValue: Boolean,
    val requiresWasteReconciliation: Boolean
) {
    OWNED_RAW_MATERIAL(
        code = "owned_raw_material",
        displayName = "Bahan Baku Milik Pabrik (Aset Finansial)",
        hasFinancialAssetValue = true,
        requiresWasteReconciliation = false
    ),
    CONSIGNED_CLIENT_MATERIAL(
        code = "consigned_client_material",
        displayName = "Kain Titipan Klien / Konsinyasi (Bukan Aset)",
        hasFinancialAssetValue = false,
        requiresWasteReconciliation = true
    ),
    INTERNAL_FINISHED_GOODS(
        code = "internal_finished_goods",
        displayName = "Stok Baju Jadi Milik Brand (Katalog SKU)",
        hasFinancialAssetValue = true,
        requiresWasteReconciliation = false
    ),
    NON_STOCK_SERVICE(
        code = "non_stock_service",
        displayName = "Non-Fisik / Eksekusi Jasa Murni",
        hasFinancialAssetValue = false,
        requiresWasteReconciliation = false
    );
}

/**
 * How costing and billing are evaluated for this operational module.
 */
enum class CostingBehavior(
    val code: String,
    val displayName: String,
    val includesFabricMaterialCost: Boolean
) {
    FULL_PACKAGE_COGS(
        code = "full_package_cogs",
        displayName = "HPP Penuh (Bahan Kain + Aksesoris + Jasa + Margin)",
        includesFabricMaterialCost = true
    ),
    SERVICE_FEE_ONLY(
        code = "service_fee_only",
        displayName = "Ongkos Jasa Makloon Saja (Tarif per Pcs/Lusin)",
        includesFabricMaterialCost = false
    ),
    RETAIL_VALUATION_WITH_FEES(
        code = "retail_valuation_with_fees",
        displayName = "Valuasi Stok Retail + Biaya Marketplace & Packing B2C",
        includesFabricMaterialCost = true
    ),
    INDIRECT_OVERHEAD(
        code = "indirect_overhead",
        displayName = "Biaya Overhead & Pemeliharaan Mesin",
        includesFabricMaterialCost = false
    );
}

/**
 * Policy defining whether a module must be executed or bypassed under a specific business model.
 */
enum class ModuleExecutionPolicy {
    MANDATORY,
    BYPASSED,
    CONDITIONAL_REWORK;
}



/**
 * Universal Contract for any Operational Module in WeMade ERP.
 * Supports both standard presets and dynamic puzzling / custom tenant workflows.
 */
interface OperationalModuleSpecification {
    val module: BusinessModule

    /**
     * Non-null by construction: only operational modules have a specification at all, and every
     * operational module fills exactly one capability slot. A governance module reaching here
     * would be a wiring mistake, and failing loudly beats silently synthesizing a station.
     */
    val archetype: ModuleArchetype
        get() = requireNotNull(GarmentSlots.forModule(module)) {
            "Modul '${module.code}' bertipe ${module.kind} sehingga tidak mengisi slot kapabilitas " +
                "mana pun; hanya modul operasional yang boleh punya OperationalModuleSpecification."
        }

    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    /** Port masuk: tipe data yang diterima (terdaftar di `DomainPack.wiredPortTypes`). */
    val upstreamPrerequisites: List<String>
    /** Port keluar: tipe data yang dipancarkan. Kanvas menyambung A→B bila keluar A ∩ masuk B. */
    val downstreamHandoffs: List<String>

    /**
     * Port masuk yang berlaku untuk [parameters] modul ini di Blueprint tenant (TRD-PLAT-001 FR-4) —
     * override bila perilaku bisnis mengubah kebutuhan data.
     */
    fun inputsFor(parameters: Map<String, String>): List<String> = upstreamPrerequisites

    fun outputsFor(parameters: Map<String, String>): List<String> = downstreamHandoffs

    /**
     * Masukan **rujukan**: data yang dibaca tapi tidak mengalir sebagai barang (QC membaca tech pack
     * sebagai acuan). Tersambung bila pemasoknya aktif, tetapi tidak diteruskan melewati modul bypass.
     */
    val referenceInputs: List<String> get() = emptyList()

    /**
     * Who carries the cost when this module detects a defect. Null for modules that do not
     * make a quality judgement.
     */
    val defectLiability: DefectLiability? get() = null
}

/**
 * Descriptor for dynamic, pluggable modules created by developers or tenants.
 * Allows custom calculation rules (e.g. custom HPP formula) within a known archetype.
 */
data class DynamicModuleDescriptor(
    val moduleId: String,
    val archetype: ModuleArchetype,
    val name: String,
    val description: String,
    val acceptedInputDataTypes: Set<String>,
    val producedOutputDataType: String,
    val isCustomTenantPlugin: Boolean = false,
    val customConfigSchemaJson: String? = null
) {
    init {
        require(moduleId.isNotBlank()) { "DynamicModuleDescriptor.moduleId cannot be blank" }
        require(name.isNotBlank()) { "DynamicModuleDescriptor.name cannot be blank" }
    }

    /**
     * Materialises this descriptor as a node that can be inserted into a tenant's
     * pipeline graph and persisted. This is what turns the descriptor from a type
     * declaration into a usable extension point.
     */
    fun toPipelineNode(
        nodeId: String = "node-$moduleId",
        stepOrderIndex: Int = 0,
        isBypassed: Boolean = false,
        formulaParameters: Map<String, String> = emptyMap()
    ): CustomPipelineNode = CustomPipelineNode(
        nodeId = nodeId,
        moduleId = moduleId,
        customDisplayName = name,
        archetype = archetype,
        isBypassed = isBypassed,
        stepOrderIndex = stepOrderIndex,
        customFormulaParameters = formulaParameters,
        isCustomPlugin = isCustomTenantPlugin,
        configSchemaJson = customConfigSchemaJson
    )

    companion object {
        /** Reconstructs a descriptor from a persisted custom node. */
        fun fromPipelineNode(node: CustomPipelineNode): DynamicModuleDescriptor =
            DynamicModuleDescriptor(
                moduleId = node.moduleId,
                archetype = node.archetype,
                name = node.customDisplayName,
                description = node.archetype.displayName,
                acceptedInputDataTypes = setOf(node.archetype.defaultExpectedInputType),
                producedOutputDataType = node.archetype.defaultProducedOutputType,
                isCustomTenantPlugin = node.isCustomPlugin,
                customConfigSchemaJson = node.configSchemaJson
            )
    }
}
