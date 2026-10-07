package com.eventverse.app.domain.builder

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/** Status sistem Antrian Pembuatan (FR-M2-4) — konsep platform, bukan kosakata vertikal. */
/**
 * [SUPERSEDED] dikelola **sistem**, bukan operator: sebuah deploy ulang pack kustom menggantikan permintaan yang belum selesai
 * dengan permintaan baru (brief versi baru). Tidak bisa diatur manual lewat antrean.
 */
enum class BuildRequestStatus {
    QUEUED, QUOTED, APPROVED, IN_PROGRESS, SHIPPED, REJECTED, SUPERSEDED;

    /** Masih menunggu/sedang dikerjakan — yang digantikan bila tenant men-deploy ulang. */
    val isPending: Boolean get() = this == QUEUED || this == QUOTED || this == APPROVED || this == IN_PROGRESS
}

@JvmInline
value class BuildRequestId(val value: String) {
    init { require(value.isNotBlank()) { "BuildRequestId kosong" } }
}

/**
 * Satu permintaan pembuatan kode modul (FR-M2-4). Dibuat `QUEUED` oleh [DeployTenantUseCase] bila
 * draf memakai pack kustom yang belum diimplementasi platform; dikelola superadmin (MVP tanpa
 * self-service). `quoteId` menaut ke ledger `moduledev` bila sudah dikutip.
 */
/**
 * Brief developer yang **dibekukan** saat permintaan lahir (opsi B, serah-terima): Markdown untuk dibaca dan JSON untuk
 * mesin (portal developer kelak). Tidak pernah ditulis ulang — chat/draf yang berubah sesudahnya tidak mengubah apa yang
 * dijanjikan kepada developer (prinsip "template disalin, dokumen membeku").
 */
data class BriefSnapshot(val markdown: String, val json: String, val takenAt: Instant) {
    init { require(markdown.isNotBlank()) { "BriefSnapshot.markdown kosong" } }
}

data class BuildRequest(
    val id: BuildRequestId,
    val tenantId: TenantId,
    val moduleId: String,
    val reason: String,
    val status: BuildRequestStatus = BuildRequestStatus.QUEUED,
    val quoteId: String? = null,
    val deploymentId: String? = null,
    val createdAt: Instant? = null,
    /** Brief beku saat dibuat; null untuk permintaan lama (sebelum V94) atau bila penyusunan brief gagal. */
    val brief: BriefSnapshot? = null,
    /** Versi brief untuk modul ini (1 = pertama; naik tiap deploy ulang yang menggantikan permintaan sebelumnya). */
    val briefVersion: Int = 1,
    /** Permintaan yang digantikan oleh yang ini (revisi); null untuk permintaan pertama. */
    val supersedes: BuildRequestId? = null,
    /** Permintaan pengganti bila ini [BuildRequestStatus.SUPERSEDED]; null bila digugurkan tanpa pengganti (modul tak lagi aktif). */
    val supersededBy: BuildRequestId? = null
) {
    init { require(briefVersion > 0) { "BuildRequest.briefVersion harus positif" } }
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
