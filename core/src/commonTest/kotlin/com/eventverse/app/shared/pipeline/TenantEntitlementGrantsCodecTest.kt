package com.eventverse.app.shared.pipeline

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

import com.eventverse.app.domain.pipeline.TenantEntitlementGrants
import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TenantEntitlementGrantsCodecTest {

    @Test
    fun encodeThenDecode_withFullDefaultCatalogue_shouldPreserveNullGrantedModules() {
        val grants = TenantEntitlementGrants(
            grantedModules = null,
            grantedCustomModuleIds = setOf("sablon_bordir_custom")
        )

        val decoded = TenantEntitlementGrantsCodec.decode(
            TenantEntitlementGrantsCodec.encode(grants).encode()
        )

        assertNull(decoded.grantedModules, "null harus tetap berarti 'semua modul bawaan sesuai paket'")
        assertEquals(setOf("sablon_bordir_custom"), decoded.grantedCustomModuleIds)
    }

    @Test
    fun encodeThenDecode_withNarrowedModuleSet_shouldRoundTrip() {
        val grants = TenantEntitlementGrants(
            grantedModules = setOf(GarmentModules.CRM_SALES, GarmentModules.OPERATOR_EXEC),
            grantedCustomModuleIds = emptySet()
        )

        val decoded = TenantEntitlementGrantsCodec.decode(
            TenantEntitlementGrantsCodec.encode(grants).encode()
        )

        assertEquals(grants.grantedModules, decoded.grantedModules)
        assertTrue(decoded.grantedCustomModuleIds.isEmpty())
    }

    @Test
    fun decode_withEmptyModuleArray_shouldMeanNoModulesGranted() {
        // Distinct from `null`: an explicit empty array means the plan grants nothing,
        // which must not collapse into "everything" during decode.
        val decoded = TenantEntitlementGrantsCodec.decode(
            """{"grantedModules":[],"grantedCustomModuleIds":[]}"""
        )

        assertEquals(emptySet(), decoded.grantedModules)
    }

    @Test
    fun decode_withMissingKeys_shouldDefaultToFullCatalogueAndNoCustomModules() {
        val decoded = TenantEntitlementGrantsCodec.decode("{}")

        assertNull(decoded.grantedModules)
        assertTrue(decoded.grantedCustomModuleIds.isEmpty())
    }

    @Test
    fun decode_shouldIgnoreUnknownModuleNames() {
        val decoded = TenantEntitlementGrantsCodec.decode(
            """{"grantedModules":["CRM_SALES","NOT_A_REAL_MODULE"],"grantedCustomModuleIds":[]}"""
        )

        assertEquals(setOf(GarmentModules.CRM_SALES), decoded.grantedModules)
    }
}
