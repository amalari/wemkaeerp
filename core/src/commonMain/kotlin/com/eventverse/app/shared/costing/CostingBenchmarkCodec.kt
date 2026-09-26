package com.eventverse.app.shared.costing

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.common.DateTimeCodec
import com.eventverse.app.shared.common.MeasureCodec
import com.eventverse.app.shared.json.*
import kotlinx.datetime.Clock

/** Bentuk wire arsip produk historis — dipakai route, Ktor client, dan kolom JSONB sekaligus. */
object CostingBenchmarkCodec {

    fun encode(benchmark: CostingProductBenchmark): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(benchmark.id.value),
        "tenantId" to jsonOf(benchmark.tenantId.value),
        "styleName" to jsonOf(benchmark.styleName),
        "clientName" to jsonOf(benchmark.clientName),
        "category" to jsonOf(benchmark.category.name),
        "knitType" to jsonOf(benchmark.structure.knitType),
        "yarnType" to jsonOf(benchmark.structure.yarnType),
        "gauge" to (benchmark.structure.gauge?.let { jsonOf(it) } ?: JsonValue.Null),
        "netWeightGrams" to jsonOf(benchmark.metrics.netWeightGrams),
        "knittingMinutes" to (benchmark.metrics.knittingMinutes?.let { jsonOf(it) } ?: JsonValue.Null),
        "buttonCount" to jsonOf(benchmark.metrics.buttonCount),
        "hppPerUnit" to MeasureCodec.encodeMoney(benchmark.pricing.hppPerUnit),
        "sellingPricePerUnit" to (
            benchmark.pricing.sellingPricePerUnit?.let { MeasureCodec.encodeMoney(it) } ?: JsonValue.Null
            ),
        "mockupImageUrl" to jsonOf(benchmark.mockupImageUrl),
        "features" to jsonStringMapOf(benchmark.features),
        "costBreakdown" to jsonArrayOf(
            benchmark.costBreakdown.map { line ->
                jsonObjectOf(
                    "label" to jsonOf(line.label),
                    "amountPerUnit" to MeasureCodec.encodeMoney(line.amountPerUnit)
                )
            }
        ),
        "sourceSheetId" to jsonOf(benchmark.sourceSheetId?.value),
        "sourceFileName" to jsonOf(benchmark.sourceFileName),
        "createdAt" to jsonOf(benchmark.createdAt.toString()),
        "updatedAt" to jsonOf(benchmark.updatedAt.toString())
    )

    fun decode(obj: JsonValue.Obj): CostingProductBenchmark {
        val now = Clock.System.now()
        return CostingProductBenchmark(
            id = BenchmarkId(obj.string("id") ?: error("Benchmark tanpa id")),
            tenantId = TenantId(obj.string("tenantId") ?: ""),
            styleName = obj.string("styleName") ?: error("Benchmark tanpa nama artikel"),
            clientName = obj.string("clientName") ?: "",
            category = obj.string("category")
                ?.let { name -> KnitCategory.entries.firstOrNull { it.name == name } }
                ?: KnitCategory.OTHER,
            structure = KnitStructure(
                knitType = obj.string("knitType") ?: "",
                yarnType = obj.string("yarnType") ?: "",
                gauge = obj.int("gauge")
            ),
            metrics = PhysicalMetrics(
                netWeightGrams = obj.double("netWeightGrams") ?: error("Benchmark tanpa gramasi"),
                knittingMinutes = obj.int("knittingMinutes"),
                buttonCount = obj.int("buttonCount") ?: 0
            ),
            pricing = BenchmarkPricing(
                hppPerUnit = MeasureCodec.decodeMoney(obj.obj("hppPerUnit")),
                sellingPricePerUnit = obj.obj("sellingPricePerUnit")?.let { MeasureCodec.decodeMoney(it) }
            ),
            mockupImageUrl = obj.string("mockupImageUrl"),
            features = obj.stringMap("features"),
            costBreakdown = obj.objectArray("costBreakdown").map { line ->
                BenchmarkCostLine(
                    label = line.string("label") ?: "",
                    amountPerUnit = MeasureCodec.decodeMoney(line.obj("amountPerUnit"))
                )
            },
            sourceSheetId = obj.string("sourceSheetId")?.takeIf { it.isNotBlank() }?.let { CostingSheetId(it) },
            sourceFileName = obj.string("sourceFileName") ?: "",
            createdAt = DateTimeCodec.parseInstantOrFallback(obj.string("createdAt"), now),
            updatedAt = DateTimeCodec.parseInstantOrFallback(obj.string("updatedAt"), now)
        )
    }
}

/** Bentuk wire permintaan & hasil estimasi cepat CS. */
object QuickEstimateCodec {

    fun decodeInput(obj: JsonValue.Obj): QuickEstimateInput = QuickEstimateInput(
        orderQuantity = obj.long("orderQuantity") ?: error("Field 'orderQuantity' wajib diisi"),
        materialCharacter = obj.string("materialCharacter")
            ?.let { name -> MaterialCharacter.entries.firstOrNull { it.name == name } }
            ?: MaterialCharacter.HANGAT_AKRILIK,
        thickness = obj.string("thickness")
            ?.let { name -> KnitThickness.entries.firstOrNull { it.name == name } }
            ?: KnitThickness.SEDANG,
        silhouette = obj.string("silhouette")
            ?.let { name -> GarmentSilhouette.entries.firstOrNull { it.name == name } }
            ?: GarmentSilhouette.PULLOVER_TERTUTUP,
        trims = obj.obj("trims")?.let { trims ->
            TrimSpec(
                buttonCount = trims.int("buttonCount") ?: 0,
                hasWovenLabel = trims.boolean("hasWovenLabel") ?: true,
                hasHangtag = trims.boolean("hasHangtag") ?: false
            )
        } ?: TrimSpec(),
        notes = obj.string("notes") ?: ""
    )

    fun encodeInput(input: QuickEstimateInput): JsonValue.Obj = jsonObjectOf(
        "orderQuantity" to jsonOf(input.orderQuantity),
        "materialCharacter" to jsonOf(input.materialCharacter.name),
        "thickness" to jsonOf(input.thickness.name),
        "silhouette" to jsonOf(input.silhouette.name),
        "trims" to jsonObjectOf(
            "buttonCount" to jsonOf(input.trims.buttonCount),
            "hasWovenLabel" to jsonOf(input.trims.hasWovenLabel),
            "hasHangtag" to jsonOf(input.trims.hasHangtag)
        ),
        "notes" to jsonOf(input.notes)
    )

    fun encodeResult(result: QuickQuotationEstimateResult): JsonValue.Obj = jsonObjectOf(
        "input" to encodeInput(result.input),
        "basis" to jsonOf(result.basis.name),
        "confidence" to jsonOf(result.confidence.name),
        "estimatedWeightGrams" to jsonOf(result.estimatedWeightGrams),
        "estimatedKnittingMinutes" to jsonOf(result.estimatedKnittingMinutes),
        "hppLow" to MeasureCodec.encodeMoney(result.hppLow),
        "hppHigh" to MeasureCodec.encodeMoney(result.hppHigh),
        "suggestedPriceLow" to MeasureCodec.encodeMoney(result.suggestedPriceLow),
        "suggestedPriceHigh" to MeasureCodec.encodeMoney(result.suggestedPriceHigh),
        "appliedMargin" to MeasureCodec.encodeRatio(result.appliedMargin),
        "breakdown" to jsonArrayOf(
            result.breakdown.map { line ->
                jsonObjectOf(
                    "label" to jsonOf(line.label),
                    "amountPerUnit" to MeasureCodec.encodeMoney(line.amountPerUnit),
                    "explanation" to jsonOf(line.explanation)
                )
            }
        ),
        "matches" to jsonArrayOf(
            result.matches.map { match ->
                jsonObjectOf(
                    "similarity" to jsonOf(match.similarity),
                    "benchmark" to CostingBenchmarkCodec.encode(match.benchmark)
                )
            }
        ),
        "visionHints" to (result.visionHints?.let { encodeVisionHints(it) } ?: JsonValue.Null),
        "warnings" to jsonArrayOf(result.warnings.map { jsonOf(it) })
    )

    fun decodeResult(obj: JsonValue.Obj): QuickQuotationEstimateResult = QuickQuotationEstimateResult(
        input = decodeInput(obj.obj("input") ?: error("Hasil estimasi tanpa input")),
        basis = obj.string("basis")
            ?.let { name -> EstimateBasis.entries.firstOrNull { it.name == name } }
            ?: EstimateBasis.INSUFFICIENT_DATA,
        confidence = obj.string("confidence")
            ?.let { name -> EstimateConfidence.entries.firstOrNull { it.name == name } }
            ?: EstimateConfidence.LOW,
        estimatedWeightGrams = obj.double("estimatedWeightGrams") ?: 0.0,
        estimatedKnittingMinutes = obj.int("estimatedKnittingMinutes") ?: 0,
        hppLow = MeasureCodec.decodeMoney(obj.obj("hppLow")),
        hppHigh = MeasureCodec.decodeMoney(obj.obj("hppHigh")),
        suggestedPriceLow = MeasureCodec.decodeMoney(obj.obj("suggestedPriceLow")),
        suggestedPriceHigh = MeasureCodec.decodeMoney(obj.obj("suggestedPriceHigh")),
        appliedMargin = MeasureCodec.decodeRatio(obj.obj("appliedMargin")),
        breakdown = obj.objectArray("breakdown").map { line ->
            EstimateLine(
                label = line.string("label") ?: "",
                amountPerUnit = MeasureCodec.decodeMoney(line.obj("amountPerUnit")),
                explanation = line.string("explanation") ?: ""
            )
        },
        matches = obj.objectArray("matches").mapNotNull { match ->
            match.obj("benchmark")?.let {
                BenchmarkMatch(
                    benchmark = CostingBenchmarkCodec.decode(it),
                    similarity = match.double("similarity") ?: 0.0
                )
            }
        },
        visionHints = obj.obj("visionHints")?.let(::decodeVisionHints),
        warnings = obj.stringArray("warnings")
    )

    fun encodeVisionHints(hints: DesignVisionHints): JsonValue.Obj = jsonObjectOf(
        "detectedCategory" to jsonOf(hints.detectedCategory?.name),
        "detectedButtonCount" to (hints.detectedButtonCount?.let { jsonOf(it) } ?: JsonValue.Null),
        "detectedFeatures" to jsonArrayOf(hints.detectedFeatures.map { jsonOf(it) }),
        "summary" to jsonOf(hints.summary),
        "confidence" to jsonOf(hints.confidence)
    )

    fun decodeVisionHints(obj: JsonValue.Obj): DesignVisionHints = DesignVisionHints(
        detectedCategory = obj.string("detectedCategory")
            ?.let { name -> KnitCategory.entries.firstOrNull { it.name == name } },
        detectedButtonCount = obj.int("detectedButtonCount"),
        detectedFeatures = obj.stringArray("detectedFeatures"),
        summary = obj.string("summary") ?: "",
        confidence = obj.double("confidence") ?: 0.0
    )
}
