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
 * Attribution of responsibility when quality defects occur.
 */
enum class DefectLiability(
    val code: String,
    val displayName: String,
    val description: String
) {
    FACTORY_WORKMANSHIP(
        code = "factory_workmanship",
        displayName = "Tanggung Jawab Pabrik (Jahitan / Pemotongan)",
        description = "Cacat pengerjaan operator pabrik; pabrik menanggung biaya pengerjaan ulang (rework)."
    ),
    CLIENT_SUPPLIED_DEFECT(
        code = "client_supplied_defect",
        displayName = "Cacat Bahan Bawaan Buyer (Makloon CMT)",
        description = "Cacat tenun/serat kain yang dibawa klien; bukan kelalaian pabrik, dikomunikasikan ke buyer."
    ),
    SUPPLIER_VENDOR_DEFECT(
        code = "supplier_vendor_defect",
        displayName = "Cacat Pabrik Kain Rekanan (FOB)",
        description = "Kain susut/belang dari supplier pabrik; klaim retur / debit note ke penjual kain."
    );
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
}

/**
 * Universal Contract for any Operational Module in WeMade ERP.
 * Supports both standard presets and dynamic puzzling / custom tenant workflows.
 */
interface OperationalModuleSpecification {
    val module: BusinessModule
    val archetype: ModuleArchetype get() = when (module) {
        BusinessModule.CRM_SALES -> ModuleArchetype.ORDER_INGESTION
        BusinessModule.SAMPLING_ORDER -> ModuleArchetype.ORDER_INGESTION
        BusinessModule.INVENTORY -> ModuleArchetype.RAW_MATERIAL
        BusinessModule.TECH_PACK_BOM -> ModuleArchetype.COSTING_HPP
        BusinessModule.COSTING_HPP -> ModuleArchetype.COSTING_HPP
        BusinessModule.PRODUCTION_MRP -> ModuleArchetype.CUTTING
        BusinessModule.OPERATOR_EXEC -> ModuleArchetype.SEWING
        BusinessModule.QUALITY_CONTROL -> ModuleArchetype.QUALITY_CONTROL
        BusinessModule.FULFILLMENT -> ModuleArchetype.FULFILLMENT
    }

    /**
     * Presets where this module is recommended as starter default.
     * Presets are NOT hardcoded constraints; tenants may freely add or remove modules.
     */
    val supportedPresets: Set<GarmentBusinessPreset>
    val recommendedStarterPresets: Set<GarmentBusinessPreset> get() = supportedPresets

    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    val upstreamPrerequisites: List<String>
    val downstreamHandoffs: List<String>

    fun getExecutionPolicy(preset: GarmentBusinessPreset): ModuleExecutionPolicy {
        return if (supportedPresets.contains(preset)) {
            ModuleExecutionPolicy.MANDATORY
        } else {
            ModuleExecutionPolicy.BYPASSED
        }
    }
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
)

/**
 * Dynamic tenant pipeline graph representing a custom, "puzzled" workflow.
 * Tenants can design, rewire, add, or swap module nodes without modifying codebase enums.
 */
data class CustomTenantPipeline(
    val tenantId: TenantId,
    val pipelineName: String,
    val baseStarterPreset: GarmentBusinessPreset? = null,
    val nodes: List<CustomPipelineNode>,
    val edges: List<CustomPipelineEdge>
) {
    companion object {
        fun fromPreset(tenantId: TenantId, preset: GarmentBusinessPreset): CustomTenantPipeline {
            val snapshot = PipelinePresetFactory.createSnapshot(preset)
            val customNodes = snapshot.nodes.map { node ->
                CustomPipelineNode(
                    nodeId = node.id,
                    moduleId = node.module.code,
                    customDisplayName = node.title,
                    archetype = when (node.module) {
                        BusinessModule.CRM_SALES -> ModuleArchetype.ORDER_INGESTION
                        BusinessModule.SAMPLING_ORDER -> ModuleArchetype.ORDER_INGESTION
                        BusinessModule.INVENTORY -> ModuleArchetype.RAW_MATERIAL
                        BusinessModule.TECH_PACK_BOM -> ModuleArchetype.COSTING_HPP
                        BusinessModule.COSTING_HPP -> ModuleArchetype.COSTING_HPP
                        BusinessModule.PRODUCTION_MRP -> ModuleArchetype.CUTTING
                        BusinessModule.OPERATOR_EXEC -> ModuleArchetype.SEWING
                        BusinessModule.QUALITY_CONTROL -> ModuleArchetype.QUALITY_CONTROL
                        BusinessModule.FULFILLMENT -> ModuleArchetype.FULFILLMENT
                    },
                    isBypassed = node.isBypassed,
                    stepOrderIndex = node.stepNumber,
                    customFormulaParameters = emptyMap()
                )
            }

            val customEdges = snapshot.nodes.flatMap { sourceNode ->
                sourceNode.downstreamModuleCodes.mapNotNull { targetCode ->
                    val targetNode = snapshot.nodes.firstOrNull { it.module.code == targetCode }
                    if (targetNode != null) {
                        CustomPipelineEdge(
                            edgeId = "edge-${sourceNode.id}-to-${targetNode.id}",
                            fromNodeId = sourceNode.id,
                            toNodeId = targetNode.id,
                            expectedDataType = "StandardHandoffPayload"
                        )
                    } else null
                }
            }

            return CustomTenantPipeline(
                tenantId = tenantId,
                pipelineName = "Alur Operasional Tenant",
                baseStarterPreset = preset,
                nodes = customNodes,
                edges = customEdges
            )
        }
    }
}

data class CustomPipelineNode(
    val nodeId: String,
    val moduleId: String,
    val customDisplayName: String,
    val archetype: ModuleArchetype,
    val isBypassed: Boolean = false,
    val stepOrderIndex: Int = 0,
    val customFormulaParameters: Map<String, String> = emptyMap()
)

data class CustomPipelineEdge(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val expectedDataType: String,
    val isFeedbackReworkLoop: Boolean = false
)
