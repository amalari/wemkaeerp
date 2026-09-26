package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.MaterialRef
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.techpack.usecases.PreviewBomMaterialCostUseCase
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BomCostPreviewTest {

    private val tenantId = TenantId("tenant-test")
    private val now = Instant.parse("2026-09-14T10:00:00Z")

    private class FakeTechPackRepository(var techPack: TechPack) : TechPackRepository {
        override suspend fun findById(tenantId: TenantId, id: TechPackId): TechPack? = techPack
        override suspend fun findVersions(tenantId: TenantId, styleCode: StyleCode): List<TechPack> = listOf(techPack)
        override suspend fun findLatestReleased(tenantId: TenantId, styleCode: StyleCode): TechPack? = techPack
        override suspend fun findBySourceSample(tenantId: TenantId, sourceSampleSpecId: String): TechPack? = null
        override suspend fun search(tenantId: TenantId, query: TechPackQuery): TechPackPage = TechPackPage(listOf(techPack), 1, 1, 20)
        override suspend fun save(techPack: TechPack): TechPack = techPack.also { this.techPack = it }
        override suspend fun saveRevision(oldVersion: TechPack, newVersion: TechPack): TechPack = newVersion.also { this.techPack = it }
        override suspend fun archive(tenantId: TenantId, id: TechPackId): Boolean = true
        override suspend fun reserveNextStyleCode(tenantId: TenantId, prefix: String): StyleCode = StyleCode("STY-001")
    }

    private class FakeMaterialRepository(val materials: List<MaterialItem>) : MaterialItemRepository {
        var findAllByIdsCalls = 0
        override suspend fun findById(tenantId: TenantId, id: MaterialId): MaterialItem? = materials.firstOrNull { it.id == id }
        override suspend fun findByCode(tenantId: TenantId, code: MaterialCode): MaterialItem? = null
        override suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>): List<MaterialItem> {
            findAllByIdsCalls++
            return materials.filter { it.id in ids }
        }
        override suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage = MaterialCatalogPage(materials, materials.size.toLong(), 1, 20)
        override suspend fun matchByFreeText(tenantId: TenantId, freeText: String, category: MaterialCategory?): List<MaterialItem> = emptyList()
        override suspend fun save(material: MaterialItem): MaterialItem = material
        override suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory): MaterialCode = MaterialCode("MAT-001")
        override suspend fun archive(tenantId: TenantId, id: MaterialId): Boolean = true
    }

    private class FakePriceRepository(val prices: Map<MaterialId, MaterialPrice>) : MaterialPriceRepository {
        var effectivePricesAtCalls = 0
        override suspend fun historyFor(tenantId: TenantId, materialId: MaterialId): MaterialPriceHistory = MaterialPriceHistory(materialId)
        override suspend fun effectivePriceAt(tenantId: TenantId, materialId: MaterialId, at: Instant, source: PriceSource): MaterialPrice? = prices[materialId]
        override suspend fun effectivePricesAt(tenantId: TenantId, materialIds: Collection<MaterialId>, at: Instant, source: PriceSource): Map<MaterialId, MaterialPrice> {
            effectivePricesAtCalls++
            return prices.filterKeys { it in materialIds }
        }
        override suspend fun append(price: MaterialPrice): MaterialPrice = price
        override suspend fun policyFor(tenantId: TenantId): TenantPricePolicy = TenantPricePolicy(tenantId)
        override suspend fun savePolicy(policy: TenantPricePolicy): TenantPricePolicy = policy
    }

    @Test
    fun `preview calculates cost with anti-N+1 batching and handles consignment zero`() {
        val matYarnId = MaterialId("mat-yarn-1")
        val matTrimId = MaterialId("mat-trim-1")

        val yarnItem = MaterialItem(
            id = matYarnId,
            tenantId = tenantId,
            code = MaterialCode("MAT-YARN-01"),
            name = "Cotton Combed 30s",
            category = MaterialCategory.YARN,
            baseUom = UnitOfMeasure.KILOGRAM,
            createdAt = now,
            updatedAt = now
        )

        val trimItem = MaterialItem(
            id = matTrimId,
            tenantId = tenantId,
            code = MaterialCode("MAT-TRIM-01"),
            name = "Kancing Titipan Buyer",
            category = MaterialCategory.TRIM,
            baseUom = UnitOfMeasure.PIECE,
            defaultOwnership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
            createdAt = now,
            updatedAt = now
        )

        val yarnPrice = MaterialPrice(
            id = MaterialPriceId("price-1"),
            tenantId = tenantId,
            materialId = matYarnId,
            unitPrice = UnitPrice(
                amount = Money.idr(145_000L), // Rp 145.000 / kg
                per = Quantity.of(1.0, UnitOfMeasure.KILOGRAM)
            ),
            source = PriceSource.STANDARD,
            effectiveFrom = now,
            recordedAt = now
        )

        val trimPrice = MaterialPrice(
            id = MaterialPriceId("price-2"),
            tenantId = tenantId,
            materialId = matTrimId,
            unitPrice = UnitPrice(
                amount = Money.idr(500L), // Standard acuan Rp 500 / pcs
                per = Quantity.of(1.0, UnitOfMeasure.PIECE)
            ),
            source = PriceSource.STANDARD,
            effectiveFrom = now,
            recordedAt = now
        )

        val lineYarn = BomLine(
            lineId = "line-1",
            material = MaterialRef.resolved(matYarnId, yarnItem.code, yarnItem.name),
            category = MaterialCategory.YARN,
            netQuantityPerGarment = Quantity.of(200.0, UnitOfMeasure.GRAM), // 200g = 0.2kg
            wasteAllowance = Ratio.ZERO,
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL
        )

        val lineTrim = BomLine(
            lineId = "line-2",
            material = MaterialRef.resolved(matTrimId, trimItem.code, trimItem.name),
            category = MaterialCategory.TRIM,
            netQuantityPerGarment = Quantity.of(6.0, UnitOfMeasure.PIECE), // 6 pcs
            wasteAllowance = Ratio.ZERO,
            ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL
        )

        val techPack = TechPack(
            id = TechPackId("tp-1"),
            tenantId = tenantId,
            styleCode = StyleCode("STY-001"),
            styleName = "Polo Shirt",
            bomLines = listOf(lineYarn, lineTrim),
            createdAt = now,
            updatedAt = now
        )

        val fakeTpRepo = FakeTechPackRepository(techPack)
        val fakeMatRepo = FakeMaterialRepository(listOf(yarnItem, trimItem))
        val fakePriceRepo = FakePriceRepository(mapOf(matYarnId to yarnPrice, matTrimId to trimPrice))

        val useCase = PreviewBomMaterialCostUseCase(fakeTpRepo, fakeMatRepo, fakePriceRepo)

        kotlinx.coroutines.test.runTest {
            val result = useCase(tenantId, techPack.id, orderQuantity = 100L, at = now)
            val preview = result.getOrThrow()

            // Verify Anti-N+1: batch called exactly 1 time each
            assertEquals(1, fakeMatRepo.findAllByIdsCalls)
            assertEquals(1, fakePriceRepo.effectivePricesAtCalls)

            // Yarn cost: 0.2kg * Rp 145.000 = Rp 29.000 per garment. For 100 garments = Rp 2.900.000
            val yarnLineCost = preview.lines.first { it.lineId == "line-1" }
            assertEquals(Money.idr(29_000L), yarnLineCost.costPerGarment)
            assertEquals(Money.idr(2_900_000L), yarnLineCost.costTotal)

            // Consigned trim cost: Rp 0 in materialCostPerGarment, but notional value recorded
            val trimLineCost = preview.lines.first { it.lineId == "line-2" }
            assertEquals(Money.zero(), trimLineCost.costPerGarment)

            // Preview total only bills factory owned material
            assertEquals(Money.idr(29_000L), preview.materialCostPerGarment)
            assertEquals(Money.idr(2_900_000L), preview.materialCostTotal)

            // Consigned notional value: 6 pcs * 100 * Rp 500 = Rp 300.000
            assertEquals(Money.idr(300_000L), preview.consignedNotionalValue)
            assertTrue(preview.isComplete)
        }
    }
}
