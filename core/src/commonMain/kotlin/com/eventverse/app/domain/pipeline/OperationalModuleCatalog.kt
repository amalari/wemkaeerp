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

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.pack.ModuleIdCodec

import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.pack.GarmentBlueprintParams

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


    /** Order intake: leads, negotiation, purchase orders. */
    object CrmSalesModule : OperationalModuleSpecification {
        override val module = GarmentModules.CRM_SALES
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = emptyList<String>()
        override val downstreamHandoffs = listOf("ProductionOrderDraft")
    }

    /** Prototype sampling and pattern approval before mass production. */
    object SamplingOrderModule : OperationalModuleSpecification {
        override val module = GarmentModules.SAMPLING_ORDER
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
        override val module = GarmentModules.INVENTORY
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("MaterialRequisition")
        override val downstreamHandoffs = listOf("VerifiedMaterialStock")
    }

    /**
     * Technical specification and bill of materials. Di CMT tech pack dibawa buyer, jadi modul ini
     * di-bypass di preset makloon (paritas dengan PipelinePresetFactory).
     */
    object TechPackBomModule : OperationalModuleSpecification {
        override val module = GarmentModules.TECH_PACK_BOM
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("ApprovedSampleSpecification")
        override val downstreamHandoffs = listOf("TechPackAndYieldData", "MaterialRequisition")
    }

    /** Cost of goods calculation — the module whose rules differ most by business model. */
    object CostingHppModule : OperationalModuleSpecification {
        override val module = GarmentModules.COSTING_HPP
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.FULL_PACKAGE_COGS
        override val upstreamPrerequisites = listOf("TechPackAndYieldData", "VerifiedMaterialStock")
        override val downstreamHandoffs = listOf("CostingCalculationResult")

        /**
         * Nilai stok kain hanya masuk HPP pada paket penuh. Makloon (jasa) dan D2C (valuasi retail)
         * tidak menjumlahkan stok ke HPP — module-integration-rules Kontrak 4.
         */
        override fun inputsFor(parameters: Map<String, String>) =
            if (behaviorOf(parameters) == CostingBehavior.FULL_PACKAGE_COGS) upstreamPrerequisites
            else upstreamPrerequisites - "VerifiedMaterialStock"

        /** Parser ketat parameter Blueprint `costingBehavior`; kosong → perilaku bawaan spec. */
        private fun behaviorOf(parameters: Map<String, String>): CostingBehavior =
            parameters[GarmentBlueprintParams.COSTING_BEHAVIOR]
                ?.let { v -> requireNotNull(CostingBehavior.entries.firstOrNull { it.name == v }) { "costingBehavior tak dikenal: $v" } }
                ?: costingBehavior
    }

    /** Machine scheduling, cutting orders, mass production work orders. */
    object ProductionMrpModule : OperationalModuleSpecification {
        override val module = GarmentModules.PRODUCTION_MRP
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = listOf("CostingCalculationResult", "VerifiedMaterialStock")
        override val downstreamHandoffs = listOf("CutPiecesBundle")
    }

    /** Sewing line execution and daily operator output. */
    object OperatorExecModule : OperationalModuleSpecification {
        override val module = GarmentModules.OPERATOR_EXEC
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.SERVICE_FEE_ONLY
        override val upstreamPrerequisites = listOf("CutPiecesBundle")
        override val downstreamHandoffs = listOf("AssembledGarmentBundle")
    }

    /** Inspection and grading — the module that attributes defect liability. */
    object QualityControlModule : OperationalModuleSpecification {
        override val module = GarmentModules.QUALITY_CONTROL
        override val stockOwnership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val upstreamPrerequisites = listOf("AssembledGarmentBundle")
        override val referenceInputs = listOf("TechPackAndYieldData")
        override val downstreamHandoffs = listOf("InspectedAndGradedUnit")
        override val defectLiability = DefectLiability.FACTORY_WORKMANSHIP
    }

    /** Packing, delivery notes, dispatch. */
    object FulfillmentModule : OperationalModuleSpecification {
        override val module = GarmentModules.FULFILLMENT
        override val stockOwnership = StockOwnershipSemantics.INTERNAL_FINISHED_GOODS
        override val costingBehavior = CostingBehavior.RETAIL_VALUATION_WITH_FEES
        override val upstreamPrerequisites = listOf("InspectedAndGradedUnit")
        override val downstreamHandoffs = listOf("DispatchedShipmentManifest")
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
        ModuleIdCodec.standardOrNull(moduleCode)?.let { byModule[it] }

    /** Modules recommended as the starting set for a business model. */
    fun recommendedFor(blueprint: Blueprint): List<OperationalModuleSpecification> =
        all.filter { blueprint.isActive(it.module.code) }

    /** Modules that fill the same capability slot and can therefore be swapped. */
    fun interchangeableWith(archetype: ModuleArchetype): List<OperationalModuleSpecification> =
        all.filter { it.archetype == archetype }
}
