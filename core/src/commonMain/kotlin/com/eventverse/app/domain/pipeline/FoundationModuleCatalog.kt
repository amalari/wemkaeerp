package com.eventverse.app.domain.pipeline

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly

import com.eventverse.app.domain.rbac.category

import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.pack.GarmentModules

import com.eventverse.app.domain.rbac.BusinessModule

interface FoundationModuleSpecification {
    val module: BusinessModule
    val stockOwnership: StockOwnershipSemantics
    val costingBehavior: CostingBehavior
    val providedReferenceTypes: List<String>
}

object FoundationModuleCatalog {
    object MasterDataModule : FoundationModuleSpecification {
        override val module = GarmentModules.MASTER_DATA
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("MaterialCatalogSnapshot", "ResolvedMaterialPrice")
    }

    object VendorContactsModule : FoundationModuleSpecification {
        override val module = GarmentModules.VENDOR_CONTACTS
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("VendorContact", "VendorAssignment")
    }

    object InvoicingModule : FoundationModuleSpecification {
        override val module = GarmentModules.INVOICING
        override val stockOwnership = StockOwnershipSemantics.NON_STOCK_SERVICE
        override val costingBehavior = CostingBehavior.INDIRECT_OVERHEAD
        override val providedReferenceTypes = listOf("IssuedInvoiceDocument")
    }

    val all: List<FoundationModuleSpecification> = listOf(MasterDataModule, VendorContactsModule, InvoicingModule)
}
