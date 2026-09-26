package com.eventverse.app.domain.costing

import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.techpack.BomCostPreview
import com.eventverse.app.domain.techpack.TechPack
import kotlinx.datetime.Instant

/**
 * Input untuk setiap [CostingFormulaStrategy].
 *
 * Membawa semua data yang dibutuhkan strategi untuk menghitung HPP tanpa perlu
 * mengakses repository atau clock — menjaga strategi tetap murni, sinkron, dan mudah diuji.
 *
 * @param techPack Tech Pack yang sudah dirilis.
 * @param bomCostPreview Hasil kalkulasi biaya BOM yang sudah disiapkan oleh [PreviewBomMaterialCostUseCase].
 *   Strategi tidak perlu menghitung ulang biaya material — tinggal ambil [BomCostPreview.materialCostPerGarment].
 * @param orderQuantity Kuantitas order yang sedang dihitung HPP-nya.
 * @param samBreakdown SAM breakdown langsung vs subkon, dihitung oleh [SamMinutesCalculator].
 * @param parameters Parameter yang sudah di-resolve dari 4 sumber (default → node → rateCard → override).
 * @param pricingAsOf Titik waktu penentuan harga — semua pencarian harga menggunakan tanggal ini.
 * @param currency Mata uang yang dipakai seluruh kalkulasi.
 */
data class CostingFormulaInput(
    val techPack: TechPack,
    val bomCostPreview: BomCostPreview,
    val orderQuantity: Long,
    val samBreakdown: SamMinutesCalculator.SamMinutesBreakdown,
    val parameters: ResolvedCostingParameters,
    val pricingAsOf: Instant,
    val currency: CurrencyCode = CurrencyCode.IDR
) {
    init {
        require(orderQuantity > 0L) { "Kuantitas order harus lebih besar dari 0" }
    }
}

/**
 * Output dari setiap [CostingFormulaStrategy].
 *
 * @param buckets Daftar bucket biaya yang sudah dihitung oleh strategi.
 * @param marginRatio Rasio margin yang dipakai (untuk mengisi [CostingCalculationResult.marginRatio]).
 * @param consignedMaterialValueHandled Total nilai notional kain konsinyasi (untuk rekonsiliasi).
 * @param roundingResidual Sisa sen dari biaya per-order yang tidak habis dibagi qty.
 * @param warnings Peringatan non-fatal dari proses kalkulasi (mis. BOM line tidak dihargai).
 */
data class CostingFormulaOutput(
    val buckets: List<CostBucket>,
    val marginRatio: Ratio = Ratio.ZERO,
    val consignedMaterialValueHandled: Money = Money.zero(),
    val roundingResidual: Money = Money.zero(),
    val warnings: List<String> = emptyList()
)

/**
 * Interface Strategy Pattern untuk mesin kalkulasi HPP.
 *
 * Setiap implementasi menangani satu [com.eventverse.app.domain.pipeline.CostingBehavior]:
 * - [FullPackageCogsStrategy] → `FULL_PACKAGE_COGS` (FOB)
 * - [ServiceFeeOnlyStrategy] → `SERVICE_FEE_ONLY` (CMT makloon)
 * - [RetailValuationWithFeesStrategy] → `RETAIL_VALUATION_WITH_FEES` (Brand D2C)
 * - [IndirectOverheadStrategy] → `INDIRECT_OVERHEAD`
 *
 * ## Kontrak implementasi
 * 1. **Murni & sinkron**: tidak boleh ada repository, clock, atau I/O di dalam `calculate`.
 *    Data yang dibutuhkan sudah ada di [CostingFormulaInput].
 * 2. **Wrap konstruksi bucket dengan `runCatching`**: `CostBucket.init` melempar exception
 *    jika invarian konsinyasi dilanggar. [CostingStrategyResolver] akan menangkap ini dan
 *    mengubahnya ke `Result.failure` berbahasa domain, tapi strategi juga tidak boleh
 *    membiarkan exception dari konstruktor lolos tanpa diketahui.
 * 3. **Rasio hanya pada nilai per-unit**: jangan kalikan rasio ke total order di dalam strategi.
 *    Kalikan `orderQuantity` paling akhir di resolver, setelah semua bucket terbentuk.
 * 4. **Bucket MARGIN selalu `isBillableToClient = false`**: margin hanya masuk harga jual
 *    lewat `marginRatio`, bukan lewat bucket yang ditagihkan — double-counting.
 */
interface CostingFormulaStrategy {
    val behavior: com.eventverse.app.domain.pipeline.CostingBehavior

    /**
     * Menghitung HPP dari [input] dan menghasilkan [CostingFormulaOutput].
     *
     * Implementasi TIDAK boleh memanggil repository atau clock.
     * Kalau ada data yang kurang, kembalikan `Result.failure` dengan pesan jelas.
     */
    fun calculate(input: CostingFormulaInput): Result<CostingFormulaOutput>
}
