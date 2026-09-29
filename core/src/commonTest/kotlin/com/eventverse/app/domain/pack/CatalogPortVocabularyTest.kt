package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import kotlin.test.Test
import kotlin.test.assertTrue

/** Setiap port di katalog modul wajib ada di port wiring pack (dulu `PortDataTypeRegistryTest`). */
class CatalogPortVocabularyTest {

    @Test
    fun allCatalogHandoffs_mustBeWiredInPack() {
        val pack = GarmentDomainPack.pack
        OperationalModuleCatalog.all.flatMap { it.upstreamPrerequisites + it.downstreamHandoffs + it.referenceInputs }
            .toSet()
            .forEach { label -> assertTrue(pack.isWired(label), "Port '$label' belum terdaftar di GarmentPortTypes.wired") }
    }
}
