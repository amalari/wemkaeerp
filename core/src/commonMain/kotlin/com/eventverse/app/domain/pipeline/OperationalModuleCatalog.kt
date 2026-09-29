package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

/**
 * The catalogue of built-in operational modules, each declaring its capability slot, the
 * business models it fits, its stock semantics and how it bills.
 *
 * [OperationalModuleSpecification] previously had no implementations at all, which meant the
 * "lego puzzle" contract was a type declaration rather than working machinery. This is the
 * concrete side of it: the catalogue a superadmin picks from when handing modules to a
 * tenant, and the source of the contract detail a synthesized node needs.
 */
object OperationalModuleCatalog {

    private val ALL_PRESETS = GarmentBusinessPreset.entries.toSet()

    /** Order intake: leads, negotiation, purchase orders. */
    object CrmSalesModule : OperationalModuleSpecification {
        override val module = BusinessModule.CRM_SALES
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = emptyList<String>()
        override val downstreamHandoffs = listOf("ProductionOrderDraft")
    }

    /** Prototype sampling and pattern approval before mass production. */
    object SamplingOrderModule : OperationalModuleSpecification {
        override val module = BusinessModule.SAMPLING_ORDER
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.SERVICE_FEE_ONLY
        override val upstreamPrerequisites = listOf("ProductionOrderDraft")
        override val downstreamHandoffs = listOf("ApprovedSampleSpecification")
    }

    /**
     * Raw material warehousing. Not part of a CMT makloon flow, where the buyer supplies
     * the fabric — the defining difference between CMT and a full package operation.
     */
    object InventoryModule : OperationalModuleSpecification {
        override val module = BusinessModule.INVENTORY
        override val supportedPresets = setOf(
            GarmentBusinessPreset.FOB_FULL_PACKAGE,
            GarmentBusinessPreset.BRAND_D2C
        )
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("MaterialRequisition")
        override val downstreamHandoffs = listOf("VerifiedMaterialStock")

        override fun stockOwnershipFor(preset: GarmentBusinessPreset) = when (preset) {
            GarmentBusinessPreset.CMT_MAKLOON -> StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
            GarmentBusinessPreset.BRAND_D2C -> StockOwnershipSemantics.OWNED_RAW_MATERIAL
            GarmentBusinessPreset.FOB_FULL_PACKAGE -> StockOwnershipSemantics.OWNED_RAW_MATERIAL
        }
    }

    /**
     * Technical specification and bill of materials. Di CMT tech pack dibawa buyer, jadi modul ini
     * di-bypass di preset makloon (paritas dengan PipelinePresetFactory).
     */
    object TechPackBomModule : OperationalModuleSpecification {
        override val module = BusinessModule.TECH_PACK_BOM
        override val supportedPresets = setOf(
            GarmentBusinessPreset.FOB_FULL_PACKAGE,
            GarmentBusinessPreset.BRAND_D2C
        )
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("ApprovedSampleSpecification")
        override val downstreamHandoffs = listOf("TechPackAndYieldData", "MaterialRequisition")
    }

    /** Cost of goods calculation — the module whose rules differ most by business model. */
    object CostingHppModule : OperationalModuleSpecification {
        override val module = BusinessModule.COSTING_HPP
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("TechPackAndYieldData", "VerifiedMaterialStock")
        override val downstreamHandoffs = listOf("CostingCalculationResult")

        override fun costingBehaviorFor(preset: GarmentBusinessPreset) = when (preset) {
            GarmentBusinessPreset.FOB_FULL_PACKAGE -> CostingBehavior.FULL_PACKAGE_COGS
            GarmentBusinessPreset.CMT_MAKLOON -> CostingBehavior.SERVICE_FEE_ONLY
            GarmentBusinessPreset.BRAND_D2C -> CostingBehavior.RETAIL_VALUATION_WITH_FEES
        }

        /**
         * Nilai stok kain hanya masuk HPP pada paket penuh. Makloon (jasa) dan D2C (valuasi retail)
         * tidak menjumlahkan stok ke HPP — module-integration-rules Kontrak 4.
         */
        override fun inputsFor(preset: GarmentBusinessPreset) =
            if (costingBehaviorFor(preset) == CostingBehavior.FULL_PACKAGE_COGS) upstreamPrerequisites
            else upstreamPrerequisites - "VerifiedMaterialStock"
    }

    /** Machine scheduling, cutting orders, mass production work orders. */
    object ProductionMrpModule : OperationalModuleSpecification {
        override val module = BusinessModule.PRODUCTION_MRP
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = listOf("CostingCalculationResult", "VerifiedMaterialStock")
        override val downstreamHandoffs = listOf("CutPiecesBundle")

        override fun stockOwnershipFor(preset: GarmentBusinessPreset) = when (preset) {
            GarmentBusinessPreset.CMT_MAKLOON -> StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
            else -> StockOwnershipSemantics.OWNED_RAW_MATERIAL
        }
    }

    /** Sewing line execution and daily operator output. */
    object OperatorExecModule : OperationalModuleSpecification {
        override val module = BusinessModule.OPERATOR_EXEC
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.SERVICE_FEE_ONLY
        override val upstreamPrerequisites = listOf("CutPiecesBundle")
        override val downstreamHandoffs = listOf("AssembledGarmentBundle")
    }

    /** Inspection and grading — the module that attributes defect liability. */
    object QualityControlModule : OperationalModuleSpecification {
        override val module = BusinessModule.QUALITY_CONTROL
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = listOf("AssembledGarmentBundle")
        override val referenceInputs = listOf("TechPackAndYieldData")
        override val downstreamHandoffs = listOf("InspectedAndGradedUnit")
        override val defectLiability = DefectLiability.FACTORY_WORKMANSHIP

        override fun defectLiabilityFor(preset: GarmentBusinessPreset) = when (preset) {
            // Fabric the buyer supplied is the buyer's risk, not the factory's.
            GarmentBusinessPreset.CMT_MAKLOON -> DefectLiability.CLIENT_SUPPLIED_DEFECT
            GarmentBusinessPreset.FOB_FULL_PACKAGE -> DefectLiability.SUPPLIER_VENDOR_DEFECT
            GarmentBusinessPreset.BRAND_D2C -> DefectLiability.FACTORY_WORKMANSHIP
        }
    }

    /** Packing, delivery notes, dispatch. */
    object FulfillmentModule : OperationalModuleSpecification {
        override val module = BusinessModule.FULFILLMENT
        override val supportedPresets = ALL_PRESETS
        override val stockOwnership = StockOwnershipSemantics.INTERNAL_FINISHED_GOODS
        override val costingBehavior = CostingBehavior.RETAIL_VALUATION_WITH_FEES
        override val upstreamPrerequisites = listOf("InspectedAndGradedUnit")
        override val downstreamHandoffs = listOf("DispatchedShipmentManifest")

        override fun stockOwnershipFor(preset: GarmentBusinessPreset) = when (preset) {
            GarmentBusinessPreset.CMT_MAKLOON -> StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
            else -> StockOwnershipSemantics.INTERNAL_FINISHED_GOODS
        }
    }

    /**
     * Urutan = urutan node di kanvas Factory Flow dan posisi sisipan reconciler. Ikuti arah aliran
     * data (tech pack → gudang), bukan urutan penulisan. Modul baru: sisipkan di posisi alirannya.
     */
    val all: List<OperationalModuleSpecification> = listOf(
        CrmSalesModule,
        SamplingOrderModule,
        TechPackBomModule,
        InventoryModule,
        CostingHppModule,
        ProductionMrpModule,
        OperatorExecModule,
        QualityControlModule,
        FulfillmentModule
    )

    private val byModule: Map<BusinessModule, OperationalModuleSpecification> =
        all.associateBy { it.module }

    fun specificationFor(module: BusinessModule): OperationalModuleSpecification =
        byModule.getValue(module)

    fun specificationForCode(moduleCode: String): OperationalModuleSpecification? =
        BusinessModule.entries.firstOrNull { it.code == moduleCode }?.let { byModule[it] }

    /** Modules recommended as the starting set for a business model. */
    fun recommendedFor(preset: GarmentBusinessPreset): List<OperationalModuleSpecification> =
        all.filter { it.supportedPresets.contains(preset) }

    /** Modules that fill the same capability slot and can therefore be swapped. */
    fun interchangeableWith(archetype: ModuleArchetype): List<OperationalModuleSpecification> =
        all.filter { it.archetype == archetype }
}
