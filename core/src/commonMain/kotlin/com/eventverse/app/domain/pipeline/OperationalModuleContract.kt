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

    /** Macro stage a node of this archetype belongs to on the factory canvas. */
    val defaultStage: PipelineStage
        get() = when (this) {
            ORDER_INGESTION -> PipelineStage.COMMERCIAL
            PRODUCT_ENGINEERING, COSTING_HPP -> PipelineStage.ENGINEERING
            RAW_MATERIAL -> PipelineStage.SUPPLY_CHAIN
            CUTTING, SEWING, FINISHING, CUSTOM_EXTENSION -> PipelineStage.MANUFACTURING
            QUALITY_CONTROL, FULFILLMENT -> PipelineStage.ASSURANCE_DELIVERY
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
            BusinessModule.MASTER_DATA,
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
    val upstreamPrerequisites: List<String>
    val downstreamHandoffs: List<String>

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
    /** Nodes in execution order, as the tenant arranged them. */
    val orderedNodes: List<CustomPipelineNode>
        get() = nodes.sortedBy { it.stepOrderIndex }

    val activeNodes: List<CustomPipelineNode> get() = nodes.filterNot { it.isBypassed }

    val bypassedNodes: List<CustomPipelineNode> get() = nodes.filter { it.isBypassed }

    val customPluginNodes: List<CustomPipelineNode> get() = nodes.filter { it.isCustomPlugin }

    /**
     * True when the graph carries no operational node. A tenant row can legitimately
     * exist in this state (e.g. seeded before its topology was written), and callers
     * must treat it as "needs provisioning" rather than as a valid empty workflow.
     */
    val isEmpty: Boolean get() = nodes.isEmpty()

    fun findNode(nodeId: String): CustomPipelineNode? = nodes.firstOrNull { it.nodeId == nodeId }

    /** Renames one module for this tenant only, leaving every other tenant untouched. */
    fun renameNode(nodeId: String, newDisplayName: String): CustomTenantPipeline {
        require(newDisplayName.isNotBlank()) { "Module display name cannot be blank" }
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(nodes = nodes.map { if (it.nodeId == nodeId) it.rename(newDisplayName) else it })
    }

    /** Switches a module on or off without severing the surrounding wiring. */
    fun setNodeBypassed(nodeId: String, isBypassed: Boolean): CustomTenantPipeline {
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(nodes = nodes.map { if (it.nodeId == nodeId) it.withBypassed(isBypassed) else it })
    }

    /** Overrides tenant-specific calculation parameters (sewing tariff, secret margin, …). */
    fun updateNodeFormulaParameters(
        nodeId: String,
        parameters: Map<String, String>
    ): CustomTenantPipeline {
        requireNotNull(findNode(nodeId)) { "Node not found in pipeline: $nodeId" }
        return copy(
            nodes = nodes.map {
                if (it.nodeId == nodeId) it.copy(customFormulaParameters = parameters) else it
            }
        )
    }

    /** Appends a module (standard or custom plugin) at the end of the flow. */
    fun addNode(node: CustomPipelineNode): CustomTenantPipeline {
        require(findNode(node.nodeId) == null) { "Duplicate node ID: ${node.nodeId}" }
        val nextIndex = (nodes.maxOfOrNull { it.stepOrderIndex } ?: 0) + 1
        return copy(nodes = nodes + node.copy(stepOrderIndex = nextIndex))
    }

    /** Removes a module and every edge that referenced it, keeping the graph consistent. */
    fun removeNode(nodeId: String): CustomTenantPipeline = copy(
        nodes = nodes.filterNot { it.nodeId == nodeId },
        edges = edges.filterNot { it.fromNodeId == nodeId || it.toNodeId == nodeId }
    )

    fun connect(edge: CustomPipelineEdge): CustomTenantPipeline {
        requireNotNull(findNode(edge.fromNodeId)) { "Unknown source node: ${edge.fromNodeId}" }
        requireNotNull(findNode(edge.toNodeId)) { "Unknown target node: ${edge.toNodeId}" }
        require(edges.none { it.edgeId == edge.edgeId }) { "Duplicate edge ID: ${edge.edgeId}" }
        return copy(edges = edges + edge)
    }

    fun rename(newName: String): CustomTenantPipeline {
        require(newName.isNotBlank()) { "Pipeline name cannot be blank" }
        return copy(pipelineName = newName)
    }

    companion object {
        fun fromPreset(tenantId: TenantId, preset: GarmentBusinessPreset): CustomTenantPipeline {
            val snapshot = PipelinePresetFactory.createSnapshot(preset)
            val customNodes = snapshot.nodes.map { node ->
                CustomPipelineNode(
                    nodeId = node.id,
                    moduleId = node.module.code,
                    customDisplayName = node.title,
                    // forModuleCode, bukan forModule: ia total dan jatuh ke CUSTOM_EXTENSION.
                    // Node preset selalu operasional, jadi hasilnya identik — yang berubah hanya
                    // bahwa penambahan modul non-operasional tidak lagi memaksa perubahan di sini.
                    archetype = ModuleArchetype.forModuleCode(node.module.code),
                    isBypassed = node.isBypassed,
                    stepOrderIndex = node.stepNumber,
                    customFormulaParameters = emptyMap()
                )
            }

            val nodesByModuleCode = snapshot.nodes.associateBy { it.module.code }
            val customEdges = snapshot.nodes.flatMap { sourceNode ->
                sourceNode.downstreamModuleCodes.mapNotNull { targetCode ->
                    nodesByModuleCode[targetCode]?.let { targetNode ->
                        CustomPipelineEdge(
                            edgeId = "edge-${sourceNode.id}-to-${targetNode.id}",
                            fromNodeId = sourceNode.id,
                            toNodeId = targetNode.id,
                            expectedDataType = "StandardHandoffPayload"
                        )
                    }
                }
            }

            // Rework/defect feedback routes are part of the tenant topology, not decoration:
            // persist them so a restored graph still knows where rejects flow back to.
            val feedbackEdges = snapshot.nodes.flatMap { sourceNode ->
                sourceNode.feedbackRoutes.mapNotNull { route ->
                    nodesByModuleCode[route.targetModuleCode]?.let { targetNode ->
                        CustomPipelineEdge(
                            edgeId = "rework-${sourceNode.id}-to-${targetNode.id}",
                            fromNodeId = sourceNode.id,
                            toNodeId = targetNode.id,
                            expectedDataType = "DefectReworkPayload",
                            isFeedbackReworkLoop = true
                        )
                    }
                }
            }

            return CustomTenantPipeline(
                tenantId = tenantId,
                pipelineName = "Alur Operasional ${preset.shortBadge}",
                baseStarterPreset = preset,
                nodes = customNodes,
                edges = (customEdges + feedbackEdges).distinctBy { it.edgeId }
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
    val customFormulaParameters: Map<String, String> = emptyMap(),
    /** True when this node comes from a tenant/third-party plugin rather than a built-in module. */
    val isCustomPlugin: Boolean = false,
    /** Opaque JSON config schema for a custom plugin; passed through untouched. */
    val configSchemaJson: String? = null
) {
    init {
        require(nodeId.isNotBlank()) { "CustomPipelineNode.nodeId cannot be blank" }
        require(moduleId.isNotBlank()) { "CustomPipelineNode.moduleId cannot be blank" }
    }

    /** The built-in module this node maps to, or null when it is a custom plugin. */
    val standardModule: BusinessModule?
        get() = BusinessModule.entries.firstOrNull { it.code == moduleId }

    fun rename(newDisplayName: String): CustomPipelineNode {
        require(newDisplayName.isNotBlank()) { "Module display name cannot be blank" }
        return copy(customDisplayName = newDisplayName)
    }

    fun withBypassed(isBypassed: Boolean): CustomPipelineNode = copy(isBypassed = isBypassed)

    fun formulaParameter(key: String): String? = customFormulaParameters[key]
}

data class CustomPipelineEdge(
    val edgeId: String,
    val fromNodeId: String,
    val toNodeId: String,
    val expectedDataType: String,
    val isFeedbackReworkLoop: Boolean = false
)
