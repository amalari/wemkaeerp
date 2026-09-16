package com.eventverse.app.domain.costing.usecases

import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.costing.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Hasil pembacaan satu berkas Excel HPP lama, sebelum divalidasi menjadi entity.
 *
 * Semua field longgar (nullable, String bebas) karena inilah batas antara dunia luar yang
 * berantakan dan domain yang ketat. Validasi terjadi sekali, di [ImportHistoricalCostingUseCase],
 * sehingga parser mana pun — AI, regex, atau isian manual — masuk lewat pintu yang sama.
 */
data class ParsedHistoricalCosting(
    val sourceFileName: String,
    val styleName: String?,
    val clientName: String? = null,
    val categoryText: String? = null,
    val knitType: String? = null,
    val yarnType: String? = null,
    val gauge: Int? = null,
    val netWeightGrams: Double? = null,
    val knittingMinutes: Int? = null,
    val buttonCount: Int? = null,
    val hppPerUnitMinor: Long? = null,
    val sellingPricePerUnitMinor: Long? = null,
    val mockupImageUrl: String? = null,
    val features: Map<String, String> = emptyMap(),
    val costBreakdown: List<BenchmarkCostLine> = emptyList()
)

/** Ringkasan satu batch impor — dipakai CLI untuk log dan UI untuk kartu hasil. */
data class HistoricalImportReport(
    val imported: List<CostingProductBenchmark> = emptyList(),
    val skipped: List<SkippedImport> = emptyList()
) {
    val importedCount: Int get() = imported.size
    val skippedCount: Int get() = skipped.size

    data class SkippedImport(val sourceFileName: String, val reason: String)
}

/**
 * Memasukkan arsip HPP lama ke Knowledge Base tenant.
 *
 * ## Mengapa satu file gagal tidak menggagalkan batch?
 * Impor 100 file adalah operasi manusia yang dijalankan sekali. Kalau file ke-73 punya sel
 * gramasi kosong dan seluruh batch di-rollback, operator harus mengulang dari nol tanpa tahu
 * file mana yang salah. Kegagalan per file dikumpulkan di [HistoricalImportReport.skipped],
 * dan yang lolos tetap tersimpan lewat satu [CostingBenchmarkRepository.saveBatch].
 *
 * ## Idempoten berdasar nama berkas
 * Menjalankan ulang script pada folder yang sama tidak menggandakan arsip: berkas yang namanya
 * sudah tercatat dilewati. Ini penting karena impor besar hampir selalu dijalankan dua kali —
 * sekali gagal di tengah, sekali lagi setelah diperbaiki.
 */
class ImportHistoricalCostingUseCase(
    private val benchmarkRepository: CostingBenchmarkRepository,
    private val idGenerator: () -> String,
    private val clock: Clock = Clock.System
) {

    suspend operator fun invoke(
        tenantId: TenantId,
        records: List<ParsedHistoricalCosting>,
        skipAlreadyImported: Boolean = true
    ): Result<HistoricalImportReport> = runCatching {
        val now = clock.now()
        val alreadyImported = if (skipAlreadyImported) {
            benchmarkRepository.findImportedFileNames(tenantId)
        } else {
            emptySet()
        }

        val imported = mutableListOf<CostingProductBenchmark>()
        val skipped = mutableListOf<HistoricalImportReport.SkippedImport>()

        records.forEach { record ->
            if (record.sourceFileName in alreadyImported) {
                skipped += HistoricalImportReport.SkippedImport(
                    record.sourceFileName,
                    "Sudah pernah diimpor sebelumnya"
                )
                return@forEach
            }
            runCatching { record.toBenchmark(tenantId, now) }
                .onSuccess { imported += it }
                .onFailure { error ->
                    skipped += HistoricalImportReport.SkippedImport(
                        record.sourceFileName,
                        error.message ?: "Baris tidak dapat dibaca"
                    )
                }
        }

        if (imported.isNotEmpty()) {
            benchmarkRepository.saveBatch(imported)
        }

        HistoricalImportReport(imported = imported, skipped = skipped)
    }

    private fun ParsedHistoricalCosting.toBenchmark(
        tenantId: TenantId,
        now: Instant
    ): CostingProductBenchmark {
        val style = styleName?.takeIf { it.isNotBlank() }
            ?: error("Nama artikel tidak ditemukan di berkas")
        val weight = netWeightGrams
            ?: error("Berat bersih (gramasi) tidak ditemukan — tanpa ini artikel tak berguna sebagai acuan")
        val hpp = hppPerUnitMinor
            ?: error("HPP per pcs tidak ditemukan di berkas")

        return CostingProductBenchmark(
            id = BenchmarkId(idGenerator()),
            tenantId = tenantId,
            styleName = style,
            clientName = clientName.orEmpty(),
            // Kategori dibaca dari teks kategori bila ada; kalau tidak, dari nama artikelnya sendiri,
            // karena arsip lama sering menulis "CARDIGAN PARINARA" sebagai satu-satunya petunjuk.
            category = KnitCategory.fromFreeText(categoryText)
                .takeIf { it != KnitCategory.OTHER }
                ?: KnitCategory.fromFreeText(style),
            structure = KnitStructure(
                knitType = knitType.orEmpty(),
                yarnType = yarnType.orEmpty(),
                gauge = gauge?.takeIf { it in 1..21 }
            ),
            metrics = PhysicalMetrics(
                netWeightGrams = weight,
                knittingMinutes = knittingMinutes?.takeIf { it >= 0 },
                buttonCount = buttonCount?.coerceAtLeast(0) ?: 0
            ),
            pricing = BenchmarkPricing(
                hppPerUnit = Money(hpp),
                sellingPricePerUnit = sellingPricePerUnitMinor?.let { Money(it) }
            ),
            mockupImageUrl = mockupImageUrl?.takeIf { it.isNotBlank() },
            features = features,
            costBreakdown = costBreakdown,
            sourceFileName = sourceFileName,
            createdAt = now,
            updatedAt = now
        )
    }
}
