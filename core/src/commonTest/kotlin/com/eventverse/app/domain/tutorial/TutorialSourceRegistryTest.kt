package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.EmptyPackTutorialSource
import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.pack.PackTutorialSource
import com.eventverse.app.domain.pack.TutorialSourceRegistry
import com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack
import com.eventverse.app.domain.pack.tenant.layanan.LayananTutorialSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Test registry tutorial source per pack (FR-3).
 *
 * Verify:
 * - garment pack returns garment source
 * - layanan pack returns layanan source  
 * - unknown pack returns EmptyPackTutorialSource
 * - fallback works correctly
 */
class TutorialSourceRegistryTest {

    private val garmentPack = GarmentDomainPack.pack
    private val unknownPackCode = DomainPackCode("unknown_pack")
    private val unknownPack = DomainPack(
        code = unknownPackCode,
        displayName = "Unknown Pack",
        phases = listOf(com.eventverse.app.domain.pack.PhaseDefinition(
            com.eventverse.app.domain.pack.PhaseCode("TEST"),
            1,
            "Test",
            "Test phase",
            0xFF2563EB
        )),
        slots = emptyList(),
        portTypes = emptySet(),
        wiredPortTypes = emptySet(),
        modules = emptyList()
    )

    @Test
    fun garmentPack_returnsGarmentSource() {
        val source = TutorialSourceRegistry.forPack(garmentPack)
        val tutorials = source.tutorialsFor(garmentPack)

        // Garment pack harus punya tutorial
        assertTrue(tutorials.isNotEmpty(), "Garment pack harus punya tutorial")
        // Verifikasi tutorial garment ada
        assertTrue(tutorials.any { it.id.value.startsWith("crm_") }, "Harus ada tutorial CRM")
    }

    @Test
    fun layananPack_returnsLayananSource() {
        // Trigger init block of LayananTutorialSource to register to registry
        LayananTutorialSource.hashCode()
        val layananPack = LayananPilotPack.pack
        val source = TutorialSourceRegistry.forPack(layananPack)
        val tutorials = source.tutorialsFor(layananPack)

        // Layanan pack harus punya tutorial (FR-4)
        assertTrue(tutorials.isNotEmpty(), "Layanan pack harus punya tutorial")
        assertTrue(tutorials.any { it.id.value == "layanan_change_request_basics" },
            "Harus ada tutorial layanan_change_request_basics")
    }

    @Test
    fun unknownPack_returnsEmptySource() {
        val source = TutorialSourceRegistry.forPack(unknownPack)
        val tutorials = source.tutorialsFor(unknownPack)

        // Unknown pack harus dapat EmptyPackTutorialSource
        assertTrue(source is EmptyPackTutorialSource, "Unknown pack harus dapat EmptyPackTutorialSource")
        assertTrue(tutorials.isEmpty(), "Unknown pack tidak boleh punya tutorial")
    }

    @Test
    fun registeredPackCodes_includesShippedAndTenantPacks() {
        // Trigger init blocks to register sources
        LayananTutorialSource.hashCode()
        val codes = TutorialSourceRegistry.registeredPackCodes()

        // Must include garment (shipped)
        assertTrue(codes.contains(GarmentDomainPack.CODE), "Registry harus include garment")

        // Must include layanan (tenant pack from FR-4)
        val layananCode = com.eventverse.app.domain.pack.tenant.layanan.LayananPilotPack.CODE
        assertTrue(codes.contains(layananCode), "Registry harus include layanan")
    }

    @Test
    fun packTutorialSource_anchorsWorkCorrectly() {
        val garmentSource = TutorialSourceRegistry.forPack(garmentPack)
        val anchors = garmentSource.anchorsFor(garmentPack)

        // Garment harus punya anchors
        assertTrue(anchors.isNotEmpty(), "Garment pack harus punya anchors")
    }

    @Test
    fun emptyPackTutorialSource_hasCorrectBehavior() {
        val empty = EmptyPackTutorialSource

        assertTrue(empty.tutorialsFor(unknownPack).isEmpty(), "Empty harus return kosong untuk tutorials")
        assertTrue(empty.anchorsFor(unknownPack).isEmpty(), "Empty harus return kosong untuk anchors")
    }
}
