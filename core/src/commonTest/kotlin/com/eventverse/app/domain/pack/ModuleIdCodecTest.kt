package com.eventverse.app.domain.pack

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

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** B6c: satu parser kunci modul — semantik lama per format dipertahankan, nilai tak dikenal kini dilaporkan. */
class ModuleIdCodecTest {

    private val reported = mutableListOf<Pair<String, String>>()

    init { ModuleIdCodec.unknownSink = ModuleIdCodec.UnknownSink { loc, raw -> reported += loc to raw } }

    @AfterTest fun reset() { ModuleIdCodec.unknownSink = ModuleIdCodec.UnknownSink { _, _ -> } }

    @Test
    fun storedName_isExact_likeValueOf_andEveryModuleRoundTrips() {
        BusinessModules.entries.forEach { assertEquals(it, ModuleIdCodec.fromStoredName(ModuleIdCodec.storedName(it), "t")) }
        assertEquals(GarmentModules.QUALITY_CONTROL, ModuleIdCodec.fromStoredName("QUALITY_CONTROL", "t"))
        assertNull(ModuleIdCodec.fromStoredName("quality_control", "custom_roles"), "NAME peka huruf besar, seperti valueOf")
        assertEquals(listOf("custom_roles" to "quality_control"), reported)
    }

    @Test
    fun code_isCaseInsensitive_likeFromCode_andUnknownIsReportedWithLocation() {
        assertEquals(GarmentModules.CRM_SALES, ModuleIdCodec.fromCode("crm_sales", "t"))
        assertEquals(GarmentModules.CRM_SALES, ModuleIdCodec.fromCode("CRM_Sales", "t"))
        assertNull(ModuleIdCodec.fromCode("grading", "catalog"))
        assertEquals(listOf("catalog" to "grading"), reported)
    }

    @Test
    fun nullIsNotReported_andStandardLookupNeverReports() {
        assertNull(ModuleIdCodec.fromStoredName(null, "x"))
        assertNull(ModuleIdCodec.standardOrNull("sablon_bordir_custom"), "plugin kustom: wajar tidak dikenal")
        assertEquals(emptyList(), reported)
    }
}
