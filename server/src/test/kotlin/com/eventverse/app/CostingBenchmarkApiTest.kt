package com.eventverse.app

import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.*
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.common.UnitPrice
import com.eventverse.app.domain.masterdata.*
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.infrastructure.InMemoryTenantPipelineRepository
import kotlin.time.Duration.Companion.days
import com.eventverse.app.shared.costing.QuickEstimateCodec
import com.eventverse.app.shared.json.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import org.junit.Test
import kotlin.test.*

/**
 * Endpoint Knowledge Base historis dan estimator cepat CS.
 *
 * Tenant kedua ikut diisi arsip untuk memastikan estimator tidak pernah meminjam acuan dari
 * pabrik lain — kebocoran itu berarti satu tenant bisa menyimpulkan gramasi dan HPP pesaingnya
 * dari rentang harga yang dikembalikan.
 */
class CostingBenchmarkApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")
    private val otherTenantId = TenantId("ten-other-002")
    private val now = Clock.System.now()

    private fun tenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    private fun benchmark(
        id: String,
        style: String,
        owner: TenantId = tenantId,
        grams: Double = 494.0,
        hppRupiah: Long = 145_000
    ) = CostingProductBenchmark(
        id = BenchmarkId(id),
        tenantId = owner,
        styleName = style,
        clientName = "PT Sinar Busana",
        category = KnitCategory.CARDIGAN,
        structure = KnitStructure(knitType = "Jacquard", yarnType = "Acrylic 2/32", gauge = 7),
        metrics = PhysicalMetrics(netWeightGrams = grams, knittingMinutes = 97, buttonCount = 7),
        pricing = BenchmarkPricing(hppPerUnit = Money(hppRupiah * 100)),
        sourceFileName = "$id.xlsx",
        createdAt = now,
        updatedAt = now
    )

    private fun benchmarkRepoWith(vararg items: CostingProductBenchmark) =
        InMemoryCostingBenchmarkRepository().also { repo ->
            runBlocking { items.forEach { repo.save(it) } }
        }

    /*
     * Repositori master data ikut disuntik walau test ini tidak menguji harga material:
     * /estimate-quick merakit tarifnya dari katalog benang & trim, jadi tanpa suntikan ini
     * rute jatuh ke repositori Postgres default dan menggantung menunggu koneksi yang tidak
     * pernah ada.
     */
    private fun ApplicationTestBuilder.installModule(benchmarkRepo: CostingBenchmarkRepository) {
        application {
            module(
                tenantRepository = tenantRepo(),
                // Pipeline repo wajib disuntik: /estimate-quick membaca koefisien rumus tenant dari
                // node COSTING_HPP, jadi tanpa ini rute jatuh ke PostgresTenantPipelineRepository
                // dan menggantung menunggu koneksi yang tidak ada.
                pipelineRepository = InMemoryTenantPipelineRepository(),
                materialItemRepository = InMemoryMaterialItemRepository(),
                materialPriceRepository = InMemoryMaterialPriceRepository(),
                costingSheetRepository = InMemoryCostingSheetRepository(),
                costingRateCardRepository = InMemoryCostingRateCardRepository(),
                costingBenchmarkRepository = benchmarkRepo
            )
        }
    }

    private fun token() = TestAuth.tenantToken(tenantSlug = tenantSlug, role = Role.TENANT_ADMIN)

    @Test
    fun listBenchmarks_returnsOnlyCurrentTenantArchive() = testApplication {
        installModule(
            benchmarkRepoWith(
                benchmark("bmk-1", "Cardigan Parinara"),
                benchmark("bmk-2", "Cardigan Milik Pabrik Lain", owner = otherTenantId)
            )
        )

        val response = client.get("/api/tenant/costing/benchmarks") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val items = (JsonParser.parse(response.bodyAsText()) as JsonValue.Arr)
            .items.filterIsInstance<JsonValue.Obj>()
        assertEquals(1, items.size)
        assertEquals("Cardigan Parinara", items.single().string("styleName"))
    }

    @Test
    fun estimateQuick_withArchive_returnsRangeAnchoredOnHistoricalWeight() = testApplication {
        installModule(
            benchmarkRepoWith(
                benchmark("bmk-1", "Cardigan Parinara", grams = 494.0),
                benchmark("bmk-2", "Cardigan Alcarina", grams = 506.0),
                benchmark("bmk-3", "Cardigan Basic", grams = 470.0)
            )
        )

        val response = client.post("/api/tenant/costing/estimate-quick") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                jsonObjectOf(
                    "orderQuantity" to jsonOf(100L),
                    "materialCharacter" to jsonOf(MaterialCharacter.HANGAT_AKRILIK.name),
                    "thickness" to jsonOf(KnitThickness.SEDANG.name),
                    "silhouette" to jsonOf(GarmentSilhouette.CARDIGAN_BUKAAN.name),
                    "trims" to jsonObjectOf("buttonCount" to jsonOf(7))
                ).encode()
            )
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val result = QuickEstimateCodec.decodeResult(JsonParser.parse(response.bodyAsText()) as JsonValue.Obj)

        assertTrue(result.estimatedWeightGrams in 460.0..520.0, "Gramasi: ${result.estimatedWeightGrams}")
        assertTrue(result.hppLow.minorUnits < result.hppHigh.minorUnits)
        assertTrue(result.suggestedPriceLow.minorUnits > result.hppLow.minorUnits)
        assertEquals(3, result.matches.size)
    }

    @Test
    fun estimateQuick_doesNotBorrowBenchmarksFromAnotherTenant() = testApplication {
        installModule(benchmarkRepoWith(benchmark("bmk-other", "Cardigan Rahasia", owner = otherTenantId)))

        val response = client.post("/api/tenant/costing/estimate-quick") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(
                jsonObjectOf(
                    "orderQuantity" to jsonOf(100L),
                    "silhouette" to jsonOf(GarmentSilhouette.CARDIGAN_BUKAAN.name)
                ).encode()
            )
        }

        // Tanpa arsip milik tenant ini dan tanpa rate card, estimasi memang tidak bisa dihitung —
        // dan itu jawaban yang benar. Yang tidak boleh terjadi: 200 OK berisi angka pinjaman.
        assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
        assertFalse(response.bodyAsText().contains("Rahasia"))
    }

    @Test
    fun estimateQuick_withoutOrderQuantity_isRejected() = testApplication {
        installModule(benchmarkRepoWith(benchmark("bmk-1", "Cardigan Parinara")))

        val response = client.post("/api/tenant/costing/estimate-quick") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            contentType(ContentType.Application.Json)
            setBody(jsonObjectOf("silhouette" to jsonOf(GarmentSilhouette.CARDIGAN_BUKAAN.name)).encode())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun importBenchmarks_rejectsNonXlsxFile() = testApplication {
        installModule(benchmarkRepoWith())

        val response = client.post("/api/tenant/costing/benchmarks/import?fileName=arsip.pdf") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody(ByteArray(16) { 1 })
        }

        assertEquals(HttpStatusCode.UnsupportedMediaType, response.status)
    }

    @Test
    fun importBenchmarks_rejectsCorruptWorkbook() = testApplication {
        installModule(benchmarkRepoWith())

        val response = client.post("/api/tenant/costing/benchmarks/import?fileName=arsip.xlsx") {
            header(HttpHeaders.Authorization, "Bearer ${token()}")
            setBody("ini jelas bukan berkas Excel".encodeToByteArray())
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertTrue(response.bodyAsText().contains("tidak dapat dibaca", ignoreCase = true))
    }
}

/**
 * Koefisien rumus estimator per tenant, lewat `customFormulaParameters` node `COSTING_HPP`.
 *
 * Yang dibuktikan di sini dan tidak bisa dibuktikan unit test domain: setelan itu benar-benar
 * sampai dari konfigurasi pipeline tenant ke angka yang dikembalikan HTTP.
 */
class CostingEstimatorTuningApiTest {

    private val tenantSlug = "wemade-demo"
    private val tenantId = TenantId("ten-demo-001")
    private val now = Clock.System.now()

    private fun tenantRepo(): InMemoryTenantRepository {
        val repo = InMemoryTenantRepository()
        runBlocking {
            repo.save(
                Tenant(
                    id = tenantId,
                    slug = TenantSlug(tenantSlug),
                    name = TenantName("PT WeMade Demo"),
                    status = TenantStatus.ACTIVE,
                    tier = SubscriptionTier.PRO
                )
            )
        }
        return repo
    }

    /** Arsip identik untuk kedua skenario, supaya satu-satunya variabel adalah setelan tenant. */
    private fun archiveRepo() = InMemoryCostingBenchmarkRepository().also { repo ->
        runBlocking {
            listOf(494.0 to "a", 506.0 to "b", 470.0 to "c").forEach { (grams, id) ->
                repo.save(
                    CostingProductBenchmark(
                        id = BenchmarkId("bmk-$id"),
                        tenantId = tenantId,
                        styleName = "Cardigan $id",
                        category = KnitCategory.CARDIGAN,
                        structure = KnitStructure(yarnType = "Acrylic 2/32", gauge = 7),
                        metrics = PhysicalMetrics(netWeightGrams = grams, knittingMinutes = 97),
                        pricing = BenchmarkPricing(hppPerUnit = Money(145_000_00)),
                        sourceFileName = "HPP-$id.xlsx",
                        createdAt = now,
                        updatedAt = now
                    )
                )
            }
        }
    }

    /** Menyetel `customFormulaParameters` pada node HPP milik tenant, seperti admin pabrik. */
    private fun pipelineRepoWith(params: Map<String, String>) =
        InMemoryTenantPipelineRepository().also { repo ->
            runBlocking {
                val pipeline = repo.findByTenantId(tenantId) ?: return@runBlocking
                repo.save(
                    pipeline.copy(
                        nodes = pipeline.nodes.map { node ->
                            if (node.moduleId == QuickEstimateTuningCodec.COSTING_MODULE_CODE) {
                                node.copy(customFormulaParameters = params)
                            } else {
                                node
                            }
                        }
                    )
                )
            }
        }

    /**
     * Katalog benang + harganya, supaya estimasi menempuh jalur rincian biaya (bottom-up).
     * Tanpa ini estimator jatuh ke penskalaan HPP historis, yang tidak punya baris "Benang"
     * dan karenanya tidak bisa menunjukkan efek setelan susut benang sama sekali.
     */
    private fun seededMaterials(): Pair<InMemoryMaterialItemRepository, InMemoryMaterialPriceRepository> {
        val items = InMemoryMaterialItemRepository()
        val prices = InMemoryMaterialPriceRepository()
        runBlocking {
            val yarn = items.save(
                MaterialItem(
                    id = MaterialId("mat-yarn-acrylic"),
                    tenantId = tenantId,
                    code = MaterialCode("YRN-0001"),
                    name = "Benang Acrylic 2/32",
                    category = MaterialCategory.YARN,
                    baseUom = UnitOfMeasure.KILOGRAM,
                    createdAt = now,
                    updatedAt = now
                )
            )
            prices.append(
                MaterialPrice(
                    id = MaterialPriceId("prc-yarn-acrylic"),
                    tenantId = tenantId,
                    materialId = yarn.id,
                    unitPrice = UnitPrice(Money(160_000_00), Quantity.kilograms(1.0)),
                    effectiveFrom = now.minus(30.days),
                    recordedAt = now.minus(30.days)
                )
            )
        }
        return items to prices
    }

    /** Rate card tenant: tarif rajut per menit + overhead, sumber tarif non-material. */
    private fun seededRateCard() = InMemoryCostingRateCardRepository().also { repo ->
        runBlocking {
            repo.save(
                CostingRateCard(
                    id = CostingRateCardId("rc-demo"),
                    tenantId = tenantId,
                    behavior = CostingBehavior.FULL_PACKAGE_COGS,
                    effectiveFrom = now.minus(30.days),
                    laborRatePerSamMinute = Money(500_00),
                    overheadPerUnit = Money(6_000_00),
                    createdAt = now.minus(30.days),
                    updatedAt = now.minus(30.days)
                )
            )
        }
    }

    private fun ApplicationTestBuilder.installModule(
        benchmarkRepo: CostingBenchmarkRepository,
        pipelineRepo: com.eventverse.app.domain.pipeline.TenantPipelineRepository
    ) {
        val (materialItems, materialPrices) = seededMaterials()
        application {
            module(
                tenantRepository = tenantRepo(),
                pipelineRepository = pipelineRepo,
                materialItemRepository = materialItems,
                materialPriceRepository = materialPrices,
                costingSheetRepository = InMemoryCostingSheetRepository(),
                costingRateCardRepository = seededRateCard(),
                costingBenchmarkRepository = benchmarkRepo
            )
        }
    }

    private fun estimateBody() = jsonObjectOf(
        "orderQuantity" to jsonOf(30L),
        "materialCharacter" to jsonOf(MaterialCharacter.HANGAT_AKRILIK.name),
        "thickness" to jsonOf(KnitThickness.SEDANG.name),
        "silhouette" to jsonOf(GarmentSilhouette.CARDIGAN_BUKAAN.name)
    ).encode()

    private suspend fun ApplicationTestBuilder.estimate(): QuickQuotationEstimateResult {
        val token = TestAuth.tenantToken(tenantSlug = tenantSlug, role = Role.TENANT_ADMIN)
        val response = client.post("/api/tenant/costing/estimate-quick") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(estimateBody())
        }
        assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
        return QuickEstimateCodec.decodeResult(JsonParser.parse(response.bodyAsText()) as JsonValue.Obj)
    }

    @Test
    fun tenantWithoutAnySetting_stillGetsABottomUpEstimateOnSystemDefaults() = testApplication {
        installModule(archiveRepo(), pipelineRepoWith(emptyMap()))

        val default = estimate()

        assertEquals(EstimateBasis.BOTTOM_UP_WITH_BENCHMARK, default.basis)
        assertEquals(EstimateConfidence.HIGH, default.confidence)
        // Default sistem: susut 8% dan penalti run kecil aktif pada order 30 pcs.
        assertTrue(default.breakdown.any { it.explanation.contains("8% susut") }, default.breakdown.toString())
        assertTrue(default.breakdown.any { it.explanation.contains("tier run kecil") })
    }

    @Test
    fun tenantWithLowWasteAndNoSmallRunPenalty_getsACheaperEstimate() = testApplication {
        installModule(
            archiveRepo(),
            pipelineRepoWith(
                mapOf(
                    QuickEstimateTuningCodec.KEY_YARN_WASTE_PERCENT to "2",
                    QuickEstimateTuningCodec.KEY_SMALL_RUN_TIERS to "1:1.0"
                )
            )
        )

        val tuned = estimate()

        // Susut 2% (bukan 8%) dan tanpa penalti run kecil pada order 30 pcs.
        assertTrue(tuned.breakdown.any { it.explanation.contains("2% susut") }, tuned.breakdown.toString())
        assertTrue(
            tuned.breakdown.none { it.explanation.contains("tier run kecil") },
            "Penalti run kecil masih dipakai: ${tuned.breakdown}"
        )
        assertTrue(tuned.warnings.none { it.contains("run kecil") })
    }

    @Test
    fun tenantWithHeavyHandknitBaseline_usesItsOwnFallbackWhenArchiveIsEmpty() = testApplication {
        installModule(
            InMemoryCostingBenchmarkRepository(),
            pipelineRepoWith(mapOf(QuickEstimateTuningCodec.KEY_FALLBACK_GRAMS to "CARDIGAN:900"))
        )

        val result = estimate()

        assertEquals(900.0, result.estimatedWeightGrams)
        assertEquals(EstimateConfidence.LOW, result.confidence)
    }

    @Test
    fun typoInTenantSettings_surfacesAsAWarningInsteadOfFailingTheQuote() = testApplication {
        installModule(
            archiveRepo(),
            pipelineRepoWith(mapOf(QuickEstimateTuningCodec.KEY_YARN_WASTE_PERCENT to "delapan"))
        )

        val result = estimate()

        assertTrue(result.hppMid.minorUnits > 0L, "Estimasi tetap harus keluar")
        assertTrue(
            result.warnings.any { it.contains("estimatorYarnWastePercent") },
            "Peringatan setelan tidak sampai ke CS: ${result.warnings}"
        )
    }
}
