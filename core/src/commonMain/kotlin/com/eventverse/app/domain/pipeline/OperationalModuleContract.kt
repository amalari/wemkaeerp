package com.eventverse.app.domain.pipeline

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
 * Functional Archetype / Capability Slot in the factory workflow.
 * Modules fulfilling the same archetype are "sepadan" (equivalent slots)
 * and can be interchanged or customized per tenant without breaking adjacent nodes.
 */
enum class ModuleArchetype(
    val code: String,
    val displayName: String,
    val defaultExpectedInputType: String,
    val defaultProducedOutputType: String
) {
    ORDER_INGESTION(
        code = "order_ingestion",
        displayName = "Penerimaan Pesanan / PO / Sales Ingestion",
        defaultExpectedInputType = "CommercialInquiry",
        defaultProducedOutputType = "ProductionOrderDraft"
    ),
    RAW_MATERIAL(
        code = "raw_material",
        displayName = "Bahan Baku & Persediaan Gudang",
        defaultExpectedInputType = "MaterialRequisition",
        defaultProducedOutputType = "VerifiedMaterialStock"
    ),
    PRODUCT_ENGINEERING(
        code = "product_engineering",
        displayName = "Rekayasa Produk: Tech Pack, BOM & Yield",
        defaultExpectedInputType = "ApprovedSampleSpecification",
        defaultProducedOutputType = "TechPackAndYieldData"
    ),
    COSTING_HPP(
        code = "costing_hpp",
        displayName = "Perhitungan Biaya & HPP (Costing Engine)",
        defaultExpectedInputType = "TechPackAndYieldData",
        defaultProducedOutputType = "CostingCalculationResult"
    ),
    CUTTING(
        code = "cutting",
        displayName = "Pemotongan Pola Kain (Spreading & Cutting)",
        defaultExpectedInputType = "CuttingOrderWithFabric",
        defaultProducedOutputType = "CutPiecesBundle"
    ),
    SEWING(
        code = "sewing",
        displayName = "Penjahitan & Perakitan (Sewing Line)",
        defaultExpectedInputType = "CutPiecesBundle",
        defaultProducedOutputType = "AssembledGarmentBundle"
    ),
    FINISHING(
        code = "finishing",
        displayName = "Finishing, Cuci, Setrika & Trimming",
        defaultExpectedInputType = "AssembledGarmentBundle",
        defaultProducedOutputType = "FinishedGarmentUnit"
    ),
    QUALITY_CONTROL(
        code = "quality_control",
        displayName = "Pengawasan Mutu, Grading & Inspeksi",
        defaultExpectedInputType = "FinishedGarmentUnit",
        defaultProducedOutputType = "InspectedAndGradedUnit"
    ),
    FULFILLMENT(
        code = "fulfillment",
        displayName = "Pengemasan, Surat Jalan & Ekspedisi",
        defaultExpectedInputType = "InspectedAndGradedUnit",
        defaultProducedOutputType = "DispatchedShipmentManifest"
    ),
    CUSTOM_EXTENSION(
        code = "custom_extension",
        displayName = "Modul Khusus Tambahan (Custom Plugin / Extension)",
        defaultExpectedInputType = "AnyOperationalPayload",
        defaultProducedOutputType = "AnyOperationalPayload"
    );

    /**
     * Built-in module that best represents this capability slot. Custom plugin nodes borrow
     * it for icon selection and access scoping, since they have no [BusinessModule] of their own.
     */
    val representativeModule: BusinessModule
        get() = when (this) {
            ORDER_INGESTION -> BusinessModule.CRM_SALES
            RAW_MATERIAL -> BusinessModule.INVENTORY
            PRODUCT_ENGINEERING -> BusinessModule.TECH_PACK_BOM
            COSTING_HPP -> BusinessModule.COSTING_HPP
            CUTTING -> BusinessModule.PRODUCTION_MRP
            SEWING -> BusinessModule.OPERATOR_EXEC
            FINISHING -> BusinessModule.OPERATOR_EXEC
            QUALITY_CONTROL -> BusinessModule.QUALITY_CONTROL
            FULFILLMENT -> BusinessModule.FULFILLMENT
            CUSTOM_EXTENSION -> BusinessModule.PRODUCTION_MRP
        }

    companion object {
        fun fromCode(code: String?): ModuleArchetype? =
            entries.firstOrNull { it.code.equals(code, ignoreCase = true) }

        /**
         * Single source of truth mapping a standard [BusinessModule] to the capability
         * slot it fills. Previously duplicated in two places that could drift apart.
         *
         * Returns null for governance modules (`ModuleKind.GOVERNANCE`). That is not a missing
         * case: a capability slot describes a station on the production line, and the org chart,
         * the permission matrix and the flow canvas are not stations — nothing hands work to them
         * and they hand work to nothing. Forcing them into a slot would make them eligible for the
         * pipeline canvas and for `interchangeableWith`, which is exactly what must not happen.
         */
        fun forModule(module: BusinessModule): ModuleArchetype? = when (module) {
            BusinessModule.CRM_SALES -> ORDER_INGESTION
            BusinessModule.SAMPLING_ORDER -> ORDER_INGESTION
            BusinessModule.INVENTORY -> RAW_MATERIAL
            BusinessModule.TECH_PACK_BOM -> PRODUCT_ENGINEERING
            BusinessModule.COSTING_HPP -> COSTING_HPP
            BusinessModule.PRODUCTION_MRP -> CUTTING
            BusinessModule.OPERATOR_EXEC -> SEWING
            BusinessModule.QUALITY_CONTROL -> QUALITY_CONTROL
            BusinessModule.FULFILLMENT -> FULFILLMENT
            BusinessModule.ORG_CHART,
            BusinessModule.DYNAMIC_RBAC,
            BusinessModule.FACTORY_FLOW,
            BusinessModule.MASTER_DATA, BusinessModule.VENDOR_CONTACTS,
            BusinessModule.INVOICING -> null
        }

        /** Resolves the archetype for a persisted module code, standard or custom. */
        fun forModuleCode(moduleCode: String): ModuleArchetype {
            val standard = BusinessModule.entries.firstOrNull { it.code == moduleCode }
            return standard?.let { forModule(it) } ?: CUSTOM_EXTENSION
        }
    }
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
        get() = requireNotNull(ModuleArchetype.forModule(module)) {
            "Modul '${module.code}' bertipe ${module.kind} sehingga tidak mengisi slot kapabilitas " +
                "mana pun; hanya modul operasional yang boleh punya OperationalModuleSpecification."
        }

    /**
     * Presets where this module is recommended as starter default.
     * Presets are NOT hardcoded constraints; tenants may freely add or remove modules.
     */
    val supportedPresets: Set<GarmentBusinessPreset>
    val recommendedStarterPresets: Set<GarmentBusinessPreset> get() = supportedPresets

    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    /** Port masuk: tipe data yang diterima (terdaftar di `DomainPack.wiredPortTypes`). */
    val upstreamPrerequisites: List<String>
    /** Port keluar: tipe data yang dipancarkan. Kanvas menyambung A→B bila keluar A ∩ masuk B. */
    val downstreamHandoffs: List<String>

    /** Port masuk yang berlaku pada [preset] — override bila perilaku bisnis mengubah kebutuhan data. */
    fun inputsFor(preset: GarmentBusinessPreset): List<String> = upstreamPrerequisites

    fun outputsFor(preset: GarmentBusinessPreset): List<String> = downstreamHandoffs

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

    fun getExecutionPolicy(preset: GarmentBusinessPreset): ModuleExecutionPolicy {
        return if (supportedPresets.contains(preset)) {
            ModuleExecutionPolicy.MANDATORY
        } else {
            ModuleExecutionPolicy.BYPASSED
        }
    }

    /**
     * Costing rules can legitimately differ per business model — the same HPP module bills a
     * full package under FOB but only a service fee under CMT makloon. Modules that behave
     * identically everywhere inherit [costingBehavior].
     */
    fun costingBehaviorFor(preset: GarmentBusinessPreset): CostingBehavior = costingBehavior

    /** Likewise, stock semantics differ: owned fabric under FOB, consigned under CMT. */
    fun stockOwnershipFor(preset: GarmentBusinessPreset): StockOwnershipSemantics = stockOwnership

    /** Liability attribution can also depend on who supplied the material. */
    fun defectLiabilityFor(preset: GarmentBusinessPreset): DefectLiability? = defectLiability
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
