package com.eventverse.app.domain.costing

import com.eventverse.app.domain.tenant.TenantId

/**
 * Arsip produk historis per tenant — sumber acuan estimasi cepat.
 *
 * Operasinya sengaja berorientasi domain, bukan CRUD generik: [findSimilar] adalah satu-satunya
 * jalur baca yang dipakai estimator, dan bentuk kueri itulah yang menentukan indeks di V36.
 */
interface CostingBenchmarkRepository {
    suspend fun findById(tenantId: TenantId, id: BenchmarkId): CostingProductBenchmark?

    suspend fun listAll(tenantId: TenantId, limit: Int = 500): List<CostingProductBenchmark>

    /**
     * Kandidat pembanding untuk sebuah permintaan estimasi.
     *
     * Implementasi mempersempit di database (kategori + gauge berdekatan), lalu
     * [EstimateCostingFromAiDesignUseCase] yang memberi skor akhir — supaya aturan
     * kemiripan tetap hidup di domain dan bisa diuji tanpa database.
     */
    suspend fun findSimilar(
        tenantId: TenantId,
        category: KnitCategory,
        gauge: Int? = null,
        limit: Int = 25
    ): List<CostingProductBenchmark>

    /**
     * Gramasi netto seluruh artikel tenant pada [category] — bahan gramasi baku miliknya sendiri.
     *
     * ## Kenapa mengembalikan daftar mentah, bukan median
     * Aturan "berapa sampel minimal sebelum median boleh dipercaya" adalah kebijakan bisnis yang
     * bisa disetel per tenant ([QuickEstimateTuning.archiveFallbackMinSamples]). Kalau median
     * dihitung di SQL, aturan itu pindah ke database dan tidak bisa diuji tanpa database.
     */
    suspend fun netWeightSamples(
        tenantId: TenantId,
        category: KnitCategory,
        limit: Int = 200
    ): List<Double>

    suspend fun save(benchmark: CostingProductBenchmark)

    suspend fun saveBatch(benchmarks: List<CostingProductBenchmark>)

    /** Nama berkas Excel yang sudah pernah diimpor — mencegah impor ganda saat script diulang. */
    suspend fun findImportedFileNames(tenantId: TenantId): Set<String>

    suspend fun count(tenantId: TenantId): Int
}
