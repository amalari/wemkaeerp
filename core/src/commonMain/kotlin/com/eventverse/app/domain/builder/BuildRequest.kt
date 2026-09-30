package com.eventverse.app.domain.builder

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/** Status sistem Antrian Pembuatan (FR-M2-4) — konsep platform, bukan kosakata vertikal. */
enum class BuildRequestStatus { QUEUED, QUOTED, APPROVED, IN_PROGRESS, SHIPPED, REJECTED }

@JvmInline
value class BuildRequestId(val value: String) {
    init { require(value.isNotBlank()) { "BuildRequestId kosong" } }
}

/**
 * Satu permintaan pembuatan kode modul (FR-M2-4). Dibuat `QUEUED` oleh [DeployTenantUseCase] bila
 * draf memakai pack kustom yang belum diimplementasi platform; dikelola superadmin (MVP tanpa
 * self-service). `quoteId` menaut ke ledger `moduledev` bila sudah dikutip.
 */
data class BuildRequest(
    val id: BuildRequestId,
    val tenantId: TenantId,
    val moduleId: String,
    val reason: String,
    val status: BuildRequestStatus = BuildRequestStatus.QUEUED,
    val quoteId: String? = null,
    val deploymentId: String? = null,
    val createdAt: Instant? = null
) {
    init { require(moduleId.isNotBlank()) { "BuildRequest.moduleId kosong" } }
    init { require(reason.isNotBlank()) { "BuildRequest.reason kosong" } }
}

/** Penyimpanan build request (tabel `builder.build_requests`, V83, RLS per tenant). */
interface BuilderBuildRequestRepository {
    suspend fun findByTenant(tenantId: TenantId): List<BuildRequest>

    /** Seluruh antrean lintas tenant — khusus konsol superadmin (Antrian Pembuatan tipis). */
    suspend fun findAll(): List<BuildRequest>

    suspend fun save(request: BuildRequest): BuildRequest
}
