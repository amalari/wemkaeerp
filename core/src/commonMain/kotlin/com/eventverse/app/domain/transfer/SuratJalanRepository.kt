package com.eventverse.app.domain.transfer

/**
 * Repository interface untuk agregat [SuratJalanManifest].
 */
interface SuratJalanRepository {
    suspend fun findById(id: SuratJalanId): SuratJalanManifest?
    suspend fun findByNumber(tenantId: String, sjNumber: SuratJalanNumber): SuratJalanManifest?
    suspend fun findByTenant(tenantId: String, transferType: TransferType? = null): List<SuratJalanManifest>
    suspend fun findBySubject(tenantId: String, subjectId: String): List<SuratJalanManifest>
    suspend fun save(manifest: SuratJalanManifest)
}
