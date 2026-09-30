package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryAgent
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.discovery.DiscoveryRequest
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import kotlinx.datetime.Clock

/**
 * Keluaran validator sebagai satu pesan; dipakai use case untuk menolak draf rusak dan route untuk memilih
 * status HTTP. Draf yang gagal **tidak pernah** tersimpan (plan §1: yang tidak lolos tidak pernah tersimpan).
 */
internal fun failuresOf(draft: DiscoveryDraft): String? =
    DiscoveryDraftValidator.validate(draft).takeIf { it.isNotEmpty() }
        ?.joinToString("; ") { "${it.path}: ${it.message}" }

/**
 * Narasi → draf tersimpan (plan §2 A6 POST). Agent dijalankan, hasilnya divalidasi, baru disimpan sebagai
 * [DiscoveryDraftStatus.DRAFT] milik [ownerUserId]. Id draf ditentukan pemanggil (route memakai id ber-timestamp)
 * supaya retry yang sama tidak diam-diam membuat dua draf.
 */
class CreateDiscoveryDraftUseCase(
    private val agent: DiscoveryAgent,
    private val repository: DiscoveryDraftRepository
) {
    suspend operator fun invoke(
        request: DiscoveryRequest,
        ownerUserId: UserId,
        draftId: DiscoveryDraftId,
        prospectLeadId: String? = null
    ): Result<StoredDiscoveryDraft> = runCatching {
        val draft = agent.draft(request).getOrThrow()
        failuresOf(draft)?.let { throw IllegalArgumentException("Draf discovery tidak sah: $it") }
        require(repository.findById(draftId) == null) { "Draf ${draftId.value} sudah ada" }
        val now = Clock.System.now()
        repository.save(
            StoredDiscoveryDraft(
                id = draftId,
                ownerUserId = ownerUserId,
                draft = draft,
                status = DiscoveryDraftStatus.DRAFT,
                prospectLeadId = prospectLeadId,
                createdAt = now,
                updatedAt = now
            )
        )
    }
}

/**
 * Revisi draf (plan §2 A6 PUT). Gerbang: **pemilik draf atau superadmin** (T12) dan status harus masih DRAFT.
 * Kepemilikan diperiksa dua lapis — di route (untuk 403) dan di sini (supaya pemanggil non-HTTP tidak bisa
 * melewatinya). LOCKED immutable (Kontrak 5): revisi = draf baru, bukan edit di tempat.
 */
class UpdateDiscoveryDraftUseCase(private val repository: DiscoveryDraftRepository) {

    class NotOwnerException(message: String) : IllegalStateException(message)
    class LockedException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(
        id: DiscoveryDraftId,
        callerUserId: UserId,
        isPlatformSuperadmin: Boolean,
        draft: DiscoveryDraft
    ): Result<StoredDiscoveryDraft> = runCatching {
        val stored = repository.findById(id) ?: error("Draf ${id.value} tidak ditemukan")
        if (!isPlatformSuperadmin && stored.ownerUserId != callerUserId) {
            throw NotOwnerException("Draf ${id.value} bukan milik Anda")
        }
        if (stored.status == DiscoveryDraftStatus.LOCKED) {
            throw LockedException("Draf ${id.value} sudah terkunci dan tidak bisa diubah")
        }
        failuresOf(draft)?.let { throw IllegalArgumentException("Draf discovery tidak sah: $it") }
        repository.save(stored.copy(draft = draft, updatedAt = Clock.System.now()))
    }
}

/** Mengunci draf (plan §2 A6 POST …/lock). Sejak itu dokumen beku; update apa pun ditolak. */
class LockDiscoveryDraftUseCase(private val repository: DiscoveryDraftRepository) {

    class LockedException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(
        id: DiscoveryDraftId,
        callerUserId: UserId,
        isPlatformSuperadmin: Boolean
    ): Result<StoredDiscoveryDraft> = runCatching {
        val stored = repository.findById(id) ?: error("Draf ${id.value} tidak ditemukan")
        if (!isPlatformSuperadmin && stored.ownerUserId != callerUserId) {
            throw LockedException("Draf ${id.value} bukan milik Anda")
        }
        if (stored.status == DiscoveryDraftStatus.LOCKED) {
            throw LockedException("Draf ${id.value} sudah terkunci")
        }
        val now = Clock.System.now()
        repository.save(stored.copy(status = DiscoveryDraftStatus.LOCKED, lockedAt = now, updatedAt = now))
    }
}
