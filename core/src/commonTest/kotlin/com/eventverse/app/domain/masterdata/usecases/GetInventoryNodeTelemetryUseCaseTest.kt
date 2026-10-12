package com.eventverse.app.domain.masterdata.usecases

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.MaterialCatalogPage
import com.eventverse.app.domain.masterdata.MaterialCatalogQuery
import com.eventverse.app.domain.masterdata.MaterialCategory
import com.eventverse.app.domain.masterdata.MaterialCode
import com.eventverse.app.domain.masterdata.MaterialId
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.masterdata.MaterialItemRepository
import com.eventverse.app.domain.masterdata.MaterialPrice
import com.eventverse.app.domain.masterdata.MaterialPriceHistory
import com.eventverse.app.domain.masterdata.MaterialPriceId
import com.eventverse.app.domain.masterdata.MaterialPriceRepository
import com.eventverse.app.domain.masterdata.PriceSource
import com.eventverse.app.domain.masterdata.TenantPricePolicy
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Telemetri Inventory dihitung dari katalog material nyata (tidak ada ledger stok), jadi `wipPieces`
 * = material aktif dan health dari rasio material aktif tanpa harga acuan.
 */
class GetInventoryNodeTelemetryUseCaseTest {

    private val now = Instant.parse("2026-10-12T00:00:00Z")
    private val tenant = TenantId("ten-inventory")
    private val clock = object : Clock { override fun now() = now }

    private fun material(n: Int, archived: Boolean = false) = MaterialItem(
        id = MaterialId("mat_$n"),
        tenantId = tenant,
        code = MaterialCode("FAB-00$n"),
        name = "Kain $n",
        category = MaterialCategory.FABRIC,
        baseUom = UnitOfMeasure.KILOGRAM,
        createdAt = now,
        updatedAt = now,
        archivedAt = if (archived) now else null
    )

    private fun priceFor(id: MaterialId) = MaterialPrice(
        id = MaterialPriceId("prc_${id.value}"),
        tenantId = tenant,
        materialId = id,
        unitPrice = UnitPrice(Money(50_000L, CurrencyCode.IDR), Quantity(1_000_000L, UnitOfMeasure.KILOGRAM)),
        effectiveFrom = now,
        recordedAt = now
    )

    private inner class FakeMaterialRepository(private val items: List<MaterialItem>) : MaterialItemRepository {
        override suspend fun findById(tenantId: TenantId, id: MaterialId) = items.firstOrNull { it.id == id }
        override suspend fun findByCode(tenantId: TenantId, code: MaterialCode) = items.firstOrNull { it.code == code }
        override suspend fun findAllByIds(tenantId: TenantId, ids: Collection<MaterialId>) =
            items.filter { it.id in ids }
        override suspend fun searchCatalog(tenantId: TenantId, query: MaterialCatalogQuery): MaterialCatalogPage {
            val visible = if (query.includeArchived) items else items.filterNot { it.isArchived }
            return MaterialCatalogPage(visible, visible.size.toLong(), query.page, query.pageSize)
        }
        override suspend fun matchByFreeText(tenantId: TenantId, freeText: String, category: MaterialCategory?) =
            emptyList<MaterialItem>()
        override suspend fun save(material: MaterialItem) = material
        override suspend fun reserveNextCode(tenantId: TenantId, category: MaterialCategory) = MaterialCode("NEW-001")
        override suspend fun archive(tenantId: TenantId, id: MaterialId) = false
    }

    private inner class FakePriceRepository(private val pricedIds: Set<String>) : MaterialPriceRepository {
        override suspend fun historyFor(tenantId: TenantId, materialId: MaterialId) = MaterialPriceHistory(materialId)
        override suspend fun effectivePriceAt(
            tenantId: TenantId,
            materialId: MaterialId,
            at: Instant,
            source: PriceSource
        ) = if (materialId.value in pricedIds) priceFor(materialId) else null
        override suspend fun effectivePricesAt(
            tenantId: TenantId,
            materialIds: Collection<MaterialId>,
            at: Instant,
            source: PriceSource
        ) = materialIds.filter { it.value in pricedIds }.associateWith { priceFor(it) }
        override suspend fun append(price: MaterialPrice) = price
        override suspend fun policyFor(tenantId: TenantId) = TenantPricePolicy(tenantId)
        override suspend fun savePolicy(policy: TenantPricePolicy) = policy
    }

    private fun useCase(items: List<MaterialItem>, pricedIds: Set<String>) =
        GetInventoryNodeTelemetryUseCase(
            FakeMaterialRepository(items),
            FakePriceRepository(pricedIds),
            clock
        )

    @Test
    fun read_oneUnpricedOfThree_shouldReportWipAndBottleneck() = runTest {
        val items = listOf(material(1), material(2), material(3))

        val t = useCase(items, pricedIds = setOf("mat_1", "mat_2")).invoke(tenant).getOrThrow()

        assertEquals(GarmentModules.INVENTORY, t.module)
        assertEquals(3, t.wipPieces)
        assertEquals(FlowHealthStatus.BOTTLENECK, t.healthStatus)
    }

    @Test
    fun read_allPriced_shouldBeHealthy() = runTest {
        val items = listOf(material(1), material(2))

        val t = useCase(items, pricedIds = setOf("mat_1", "mat_2")).invoke(tenant).getOrThrow()

        assertEquals(2, t.wipPieces)
        assertEquals(FlowHealthStatus.HEALTHY, t.healthStatus)
    }

    @Test
    fun read_archivedMaterials_shouldNotCount() = runTest {
        val items = listOf(material(1), material(2, archived = true))

        val t = useCase(items, pricedIds = emptySet()).invoke(tenant).getOrThrow()

        assertEquals(1, t.wipPieces)
    }

    @Test
    fun read_emptyCatalog_shouldBeHealthyWithZeroWip() = runTest {
        val t = useCase(emptyList(), pricedIds = emptySet()).invoke(tenant).getOrThrow()

        assertEquals(0, t.wipPieces)
        assertEquals(FlowHealthStatus.HEALTHY, t.healthStatus)
    }
}
