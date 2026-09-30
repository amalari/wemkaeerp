package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryPreviewRegistry
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Pratinjau tanpa kode (plan §2 A7, T13): draf prospek didaftarkan sementara dan sebuah **tenant sandbox**
 * dibuat menunjuk pack itu — sehingga menu & `/m/{code}` langsung menampilkan modul prospek memakai jalur
 * data B7 yang sudah ada, tanpa scaffold kode. Registry LOCKED platform tidak tersentuh; sesi kedaluwarsa
 * dibersihkan dan tenant sandbox tanpa pack ditolak fail-closed (409) oleh mekanisme B7 FR-4.
 */
class StartDiscoveryPreviewUseCase(
    private val draftRepository: DiscoveryDraftRepository,
    private val tenantRepository: TenantRepository
) {

    data class StartedPreview(
        val sandboxSlug: String,
        val sandboxTenantId: TenantId,
        val packCode: DomainPackCode,
        val expiresAt: Instant
    )

    suspend operator fun invoke(
        draftId: DiscoveryDraftId,
        callerUserId: UserId,
        isPlatformSuperadmin: Boolean,
        now: Instant = Clock.System.now(),
        ttlMinutes: Long = DiscoveryPreviewRegistry.DEFAULT_TTL_MINUTES
    ): Result<StartedPreview> = runCatching {
        val stored = draftRepository.findById(draftId) ?: error("Draf ${draftId.value} tidak ditemukan")
        if (!isPlatformSuperadmin && stored.ownerUserId != callerUserId) {
            throw UpdateDiscoveryDraftUseCase.NotOwnerException("Draf ${draftId.value} bukan milik Anda")
        }
        val pack = stored.draft.pack

        val slug = TenantSlug(sandboxSlug(pack.code.value))
        val sandbox = tenantRepository.findBySlug(slug) ?: createSandboxTenant(pack.code, slug)
        if (sandbox.domainPack != pack.code) {
            throw IllegalStateException("Slug sandbox '${slug.value}' sudah dipakai tenant pack ${sandbox.domainPack.value}")
        }

        val preview = DiscoveryPreviewRegistry.start(pack, sandbox.id, now, ttlMinutes)
        StartedPreview(slug.value, sandbox.id, pack.code, preview.expiresAt)
    }

    /** Idempoten: sesi lama yang masih hidup memakai tenant sandbox yang sama. */
    private suspend fun createSandboxTenant(code: DomainPackCode, slug: TenantSlug): Tenant =
        tenantRepository.save(
            Tenant(
                id = TenantId(sandboxTenantId(code.value)),
                slug = slug,
                name = TenantName("Pratinjau ${code.value}"),
                status = TenantStatus.ACTIVE,
                tier = SubscriptionTier.PRO,
                domainPack = code
            )
        ).getOrThrow()

    private fun sandboxSlug(packCode: String): String {
        val stem = packCode.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("").trim('-')
        return "sandbox-${stem.take(30 - "sandbox-".length)}".trimEnd('-')
    }

    private fun sandboxTenantId(packCode: String): String =
        "ten-sandbox-${packCode.lowercase().map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")}"
            .take(64).trimEnd('-')
}

/** Mengakhiri sesi pratinjau lebih awal (sebelum TTL); pack draf dilepas dari registry. */
class EndDiscoveryPreviewUseCase(private val draftRepository: DiscoveryDraftRepository) {

    class NotOwnerException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(
        draftId: DiscoveryDraftId,
        callerUserId: UserId,
        isPlatformSuperadmin: Boolean
    ): Result<Unit> = runCatching {
        val stored = draftRepository.findById(draftId) ?: error("Draf ${draftId.value} tidak ditemukan")
        if (!isPlatformSuperadmin && stored.ownerUserId != callerUserId) {
            throw NotOwnerException("Draf ${draftId.value} bukan milik Anda")
        }
        DiscoveryPreviewRegistry.end(stored.draft.pack.code)
    }
}
