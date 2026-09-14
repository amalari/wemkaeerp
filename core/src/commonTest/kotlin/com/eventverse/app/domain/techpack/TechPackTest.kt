package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.*

class TechPackTest {

    private val tenantId = TenantId("tenant-test")
    private val now = Instant.parse("2026-09-14T10:00:00Z")
    private val later = Instant.parse("2026-09-14T11:00:00Z")

    private fun sampleResolvedLine(id: String = "line-1") = BomLine(
        lineId = id,
        material = MaterialRef.resolved(MaterialId("mat-1"), MaterialCode("MAT-COT-01"), "Cotton Combed 30s"),
        category = MaterialCategory.YARN,
        netQuantityPerGarment = Quantity(280_000L, UnitOfMeasure.GRAM),
        wasteAllowance = Ratio.percent(5.0),
        ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
    )

    private fun sampleConsignedLine(id: String = "line-2") = BomLine(
        lineId = id,
        material = MaterialRef.unresolved("Kain Titipan Buyer"),
        category = MaterialCategory.FABRIC,
        netQuantityPerGarment = Quantity(1_500_000L, UnitOfMeasure.METER),
        wasteAllowance = Ratio.percent(3.0),
        ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
    )

    @Test
    fun `release with empty BOM should fail`() {
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos Polos",
            createdAt = now,
            updatedAt = now
        )

        val result = techPack.release(later)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("BOM kosong") == true)
    }

    @Test
    fun `release with zero quantity line should fail`() {
        val zeroLine = sampleResolvedLine().copy(netQuantityPerGarment = Quantity.zero(UnitOfMeasure.GRAM))
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos Polos",
            bomLines = listOf(zeroLine),
            createdAt = now,
            updatedAt = now
        )

        val result = techPack.release(later)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("kuantitas 0") == true)
    }

    @Test
    fun `release with blocking unresolved lines should fail`() {
        val unmappedAssetLine = sampleResolvedLine().copy(
            material = MaterialRef.unresolved("Benang Belum Terpetakan"),
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        )
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos Polos",
            bomLines = listOf(unmappedAssetLine),
            createdAt = now,
            updatedAt = now
        )

        val result = techPack.release(later)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("belum dipetakan") == true)
    }

    @Test
    fun `release with unresolved consigned line should succeed because consigned has no factory asset risk`() {
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "CMT Polo",
            bomLines = listOf(sampleConsignedLine()),
            createdAt = now,
            updatedAt = now
        )

        val result = techPack.release(later)
        assertTrue(result.isSuccess)
        val released = result.getOrThrow()
        assertEquals(TechPackStatus.RELEASED, released.status)
        assertEquals(later, released.releasedAt)
    }

    @Test
    fun `mutate BOM after released should fail`() {
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos Polos",
            status = TechPackStatus.RELEASED,
            bomLines = listOf(sampleResolvedLine()),
            createdAt = now,
            updatedAt = now,
            releasedAt = now
        )

        val addResult = techPack.upsertBomLine(sampleResolvedLine("line-new"), later)
        assertTrue(addResult.isFailure)

        val removeResult = techPack.removeBomLine("line-1", later)
        assertTrue(removeResult.isFailure)
    }

    @Test
    fun `reviseAs creates draft with incremented version and cleared releasedAt`() {
        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Kaos Polos",
            status = TechPackStatus.RELEASED,
            version = 1,
            bomLines = listOf(sampleResolvedLine()),
            createdAt = now,
            updatedAt = now,
            releasedAt = now
        )

        val revised = techPack.reviseAs(TechPackId("tp-1-v2"), later)
        assertEquals(TechPackId("tp-1-v2"), revised.id)
        assertEquals(2, revised.version)
        assertEquals(TechPackStatus.DRAFT, revised.status)
        assertNull(revised.releasedAt)
        assertTrue(revised.isEditable)
    }
}
