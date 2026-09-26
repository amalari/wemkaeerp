package com.eventverse.app.domain.masterdata

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.pipeline.StockOwnershipSemantics
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PriceSourceResolverTest {

    private val tenantId = TenantId("ten-demo-001")
    private val materialId = MaterialId("mat-1")
    private val now = Instant.parse("2026-01-01T00:00:00Z")

    private val fakeMaterialRepo = object : MaterialItemRepository {
        override suspend fun findById(tenantId: TenantId, id: MaterialId): MaterialItem? = MaterialItem(
            id = id,
            tenantId = tenantId,
            code = MaterialCode("YRN-001"),
            name = "Benang",
            category = MaterialCategory.YARN,
            baseUom = UnitOfMeasure.KILOGRAM,
            createdAt = now,
            updatedAt = now
        )
        override suspend fun findByCode(tenantId: TenantId, code: MaterialCode): MaterialItem? = null
        override suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>): List<MaterialItem> = emptyList()
        override suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage =
            MaterialCatalogPage(emptyList(), 0, 1, 20)
        override suspend fun matchByFreeText(tenantId: TenantId, freeText: String, category: MaterialCategory?): List<MaterialItem> = emptyList()
        override suspend fun save(material: MaterialItem): MaterialItem = material
        override suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory): MaterialCode = MaterialCode("YRN-001")
        override suspend fun archive(tenantId: TenantId, id: MaterialId): Boolean = true
    }

    @Test
    fun consignedMaterial_mustReturnZeroWithoutConsultingStrategies() = runTest {
        val bombStrategy = object : PriceSourceStrategy {
            override val source: PriceSource = PriceSource.STANDARD
            override suspend fun resolve(query: PriceQuery): ResolvedPrice? {
                error("Strategi tidak boleh dipanggil untuk bahan konsinyasi!")
            }
        }

        val resolver = PriceSourceResolver(
            strategies = mapOf(PriceSource.STANDARD to bombStrategy),
            materialRepository = fakeMaterialRepo
        )

        val query = PriceQuery(
            tenantId = tenantId,
            materialId = materialId,
            at = now,
            ownership = StockOwnershipSemantics.CONSIGNED_CLIENT_MATERIAL,
            targetUom = UnitOfMeasure.KILOGRAM
        )

        val result = resolver.resolve(query, TenantPricePolicy(tenantId))
        assertTrue(result.isSuccess)

        val resolved = result.getOrThrow()
        assertTrue(resolved.unitPrice.amount.isZero)
        assertEquals(PriceSource.CLIENT_SUPPLIED_ZERO, resolved.source)
    }

    @Test
    fun standardPrice_resolvedSuccessfully() = runTest {
        val standardStrategy = object : PriceSourceStrategy {
            override val source: PriceSource = PriceSource.STANDARD
            override suspend fun resolve(query: PriceQuery): ResolvedPrice? = ResolvedPrice(
                materialId = query.materialId,
                unitPrice = UnitPrice(Money.idr(145_000), Quantity.kilograms(1.0)),
                source = PriceSource.STANDARD,
                effectiveFrom = now,
                explanation = "Tarif standar"
            )
        }

        val resolver = PriceSourceResolver(
            strategies = mapOf(PriceSource.STANDARD to standardStrategy),
            materialRepository = fakeMaterialRepo
        )

        val query = PriceQuery(
            tenantId = tenantId,
            materialId = materialId,
            at = now,
            ownership = StockOwnershipSemantics.OWNED_RAW_MATERIAL,
            targetUom = UnitOfMeasure.GRAM
        )

        val result = resolver.resolve(query, TenantPricePolicy(tenantId))
        assertTrue(result.isSuccess)
        val resolved = result.getOrThrow()
        assertEquals(UnitOfMeasure.GRAM, resolved.unitPrice.per.uom)
        // 1 gram = Rp 145 (14_500 minor)
        assertEquals(14_500L, resolved.unitPrice.amount.minorUnits)
    }
}
