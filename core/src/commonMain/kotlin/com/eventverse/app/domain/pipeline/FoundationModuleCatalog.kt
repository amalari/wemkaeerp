package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.BusinessModule

interface FoundationModuleSpecification {
    val module: BusinessModule
    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    val providedReferenceTypes: List<String>
}

object FoundationModuleCatalog {
    object MasterDataModule : FoundationModuleSpecification {
        override val module = BusinessModule.MASTER_DATA
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("MaterialCatalogSnapshot", "ResolvedMaterialPrice")
    }

    object VendorContactsModule : FoundationModuleSpecification {
        override val module = BusinessModule.VENDOR_CONTACTS
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("VendorContact", "VendorAssignment")
    }

    object InvoicingModule : FoundationModuleSpecification {
        override val module = BusinessModule.INVOICING
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("IssuedInvoiceDocument")
    }

    val all: List<FoundationModuleSpecification> = listOf(MasterDataModule, VendorContactsModule, InvoicingModule)
}
