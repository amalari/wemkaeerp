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
import com.eventverse.app.shared.techpack.TechPackCodec
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class TechPackCodecTest {

    private val tenantId = TenantId("tenant-test")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    @Test
    fun `roundtrip encoding and decoding of TechPack and Page`() {
        val line = BomLine(
            lineId = "line-1",
            material = MaterialRef.resolved(MaterialId("mat-1"), MaterialCode("MAT-01"), "Cotton Combed"),
            category = MaterialCategory.YARN,
            netQuantityPerGarment = Quantity(250_000L, UnitOfMeasure.GRAM),
            wasteAllowance = Ratio.percent(4.5),
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            notes = "Catatan baris bahan"
        )

        val op = LaborOperation(
            operationId = "op-1",
            name = "Jahit Kerah",
            samMinutes = Ratio(15L, 10L),
            workstation = "Sewing Line 1",
            isSubcontracted = false
        )

        val size = SizeYieldFactor(
            sizeLabel = "XL",
            scale = Ratio(12L, 10L),
            orderedQuantity = 500L
        )

        val original = TechPack(
            id = TechPackId("tp-test-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-KMP-01"),
            styleName = "Kemeja Casual",
            clientName = "Client XYZ",
            status = TechPackStatus.RELEASED,
            version = 2,
            sourceSampleSpecId = "sample-123",
            sourceSpkNumber = "SPK-001",
            bomLines = listOf(line),
            laborOperations = listOf(op),
            sizeYieldFactors = listOf(size),
            notes = "Catatan tech pack",
            createdByUserId = "user-1",
            createdAt = now,
            updatedAt = now,
            releasedAt = now
        )

        val encoded = TechPackCodec.encode(original)
        val decoded = TechPackCodec.decode(encoded)

        assertEquals(original.id, decoded.id)
        assertEquals(original.tenantId, decoded.tenantId)
        assertEquals(original.styleCode, decoded.styleCode)
        assertEquals(original.styleName, decoded.styleName)
        assertEquals(original.status, decoded.status)
        assertEquals(original.version, decoded.version)
        assertEquals(original.sourceSampleSpecId, decoded.sourceSampleSpecId)
        assertEquals(original.bomLines.size, decoded.bomLines.size)
        assertEquals(original.bomLines.first().material.displayLabel, decoded.bomLines.first().material.displayLabel)
        assertEquals(original.laborOperations.size, decoded.laborOperations.size)
        assertEquals(original.sizeYieldFactors.size, decoded.sizeYieldFactors.size)

        // Test Page
        val page = TechPackPage(listOf(original), 1, 1, 20)
        val pageEncoded = TechPackCodec.encodePage(page)
        val pageDecoded = TechPackCodec.decodePage(pageEncoded)
        assertEquals(1, pageDecoded.items.size)
        assertEquals(1L, pageDecoded.totalCount)
    }
}
