package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

class PortDataTypeRegistryTest {

    private val pendingTypedContracts = setOf(
        "ProspectLead",
        "ProductionOrderDraft",
        "MaterialRequisition",
        "VerifiedMaterialStock",
        "CuttingOrderWithFabric",
        "CutPiecesBundle",
        "AssembledGarmentBundle",
        "FinishedGarmentUnit",
        "InspectedAndGradedUnit",
        "DispatchedShipmentManifest"
    )

    @Test
    fun allCatalogHandoffs_mustBeRegisteredOrInPendingList() {
        val allCatalogHandoffs = OperationalModuleCatalog.all.flatMap {
            it.upstreamPrerequisites + it.downstreamHandoffs
        }.toSet()

        for (label in allCatalogHandoffs) {
            val isAccountedFor = PortDataTypeRegistry.isTyped(label) || label in pendingTypedContracts
            assertTrue(
                isAccountedFor,
                "Port contract label '$label' belum terdaftar di PortDataTypeRegistry maupun di pending list!"
            )
        }
    }
}
