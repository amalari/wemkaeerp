package com.eventverse.app.domain.contracts

import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

class PortDataTypeRegistryTest {

    @Test
    fun allCatalogHandoffs_mustBeRegistered() {
        val allCatalogHandoffs = OperationalModuleCatalog.all.flatMap {
            it.upstreamPrerequisites + it.downstreamHandoffs
        }.toSet()

        for (label in allCatalogHandoffs) {
            val isAccountedFor = PortDataTypeRegistry.isTyped(label)
            assertTrue(
                isAccountedFor,
                "Port contract label '$label' belum terdaftar di PortDataTypeRegistry!"
            )
        }
    }
}
