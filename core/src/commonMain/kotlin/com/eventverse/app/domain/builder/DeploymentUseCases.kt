package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.DiscoveryDraftValidator
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantStatus
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

class DraftNotFoundException(message: String) : IllegalStateException(message)
class DraftInvalidException(message: String) : IllegalArgumentException(message)

/**
 * Deploy tenant (FR-M2-1, plan §5): kunci versi pack + aktifkan. Append-only — deployment lama tidak
 * pernah ditimpa, hanya `SUPERSEDED`/`ROLLED_BACK`.
 *
 * Aturan "modul butuh kode" (v1, discovery-M2 §2): draf dengan pack yang **bukan pack shipped** →
 * seluruh modul aktifnya butuh kode → `BuildRequest` QUEUED per modul aktif + deployment
 * `BLOCKED_ON_BUILD`. Pack shipped → langsung ACTIVE (paritas garment, konsisten V81).
 * Draf **dikunci** saat deploy (Kontrak 5 — dokumen membeku); revisi berikutnya = draf baru lewat chat.
 */
class DeployTenantUseCase(
    private val drafts: DiscoveryDraftRepository,
    private val deployments: BuilderDeploymentRepository,
    private val buildRequests: BuilderBuildRequestRepository,
    private val tenants: TenantRepository,
    private val clock: Clock = Clock.System,
    /** Pembeku brief per permintaan (opsi B); null = tanpa brief. Kegagalannya tidak pernah menggagalkan deploy. */
    private val briefs: BuildRequestBriefs? = null
) {
    suspend operator fun invoke(tenantId: TenantId): Result<Deployment> = runCatching {
        val stored = drafts.findByTenant(tenantId)
            ?: throw DraftNotFoundException("Tenant belum punya draf kerja — bangun lewat chat dulu")
        require(stored.status != DiscoveryDraftStatus.LOCKED) {
            "Draf sudah terkunci oleh deployment sebelumnya — revisi lewat chat lalu deploy lagi"
        }
        val issues = DiscoveryDraftValidator.validate(stored.draft)
        if (issues.isNotEmpty()) {
            throw DraftInvalidException("Draf tidak sah: " + issues.joinToString("; ") { "${it.path}: ${it.message}" })
        }

        val needsCode = DomainPackRegistry.shipped.none { it.code == stored.draft.pack.code }
        val number = deployments.nextNumber(tenantId)
        val id = DeploymentId("dep-${tenantId.value}-${number.value}")
        // Versi pack dihitung SEBELUM cabang: deployment BLOCKED_ON_BUILD pun wajib membawa versi terkunci (invarian domain
        // dan CHECK SQL V81). Dulu hanya dihitung di jalur ACTIVE, sehingga deploy pack kustom selalu melempar.
        val nextVersion = deployments.findByTenant(tenantId)
            .maxOfOrNull { it.packVersion ?: 0 }?.plus(1) ?: 1

        if (needsCode) {
            val blocked = Deployment(
                id = id,
                tenantId = tenantId,
                number = number,
                packCode = stored.draft.pack.code,
                packVersion = nextVersion,
                blueprintRevision = 1,
                status = DeploymentStatus.BLOCKED_ON_BUILD,
                draftId = stored.id.value
            )
            deployments.save(blocked).getOrThrow()
            stored.draft.blueprint.activeModuleCodes.sorted().forEach { moduleId ->
                val brief = briefs?.let { runCatching { it(tenantId, stored.draft, moduleId) }.getOrNull() }
                buildRequests.save(
                    BuildRequest(
                        id = BuildRequestId("br-${tenantId.value}-${number.value}-$moduleId"),
                        tenantId = tenantId,
                        moduleId = moduleId,
                        reason = "Pack kustom '${stored.draft.pack.code.value}' belum diimplementasi platform",
                        deploymentId = id.value,
                        brief = brief
                    )
                )
            }
            return Result.success(blocked)
        }

        // Tepat satu aktif: nonaktifkan dulu (SUPERSEDED), baru aktifkan yang baru.
        // Snapshot IMPORTED pra-Builder tidak punya versi; saat digantikan ia mewarisi nomor versi
        // yang baru dikunci (keadaan yang sama, kini resmi terkunci) supaya invarian
        // "SUPERSEDED wajib membawa versi" tetap terpenuhi di domain maupun SQL.
        deployments.findActive(tenantId)?.let {
            deployments.save(
                it.copy(status = DeploymentStatus.SUPERSEDED, packVersion = it.packVersion ?: nextVersion)
            ).getOrThrow()
        }

        val activated = deployments.save(
            Deployment(
                id = id,
                tenantId = tenantId,
                number = number,
                packCode = stored.draft.pack.code,
                packVersion = nextVersion,
                blueprintRevision = 1,
                status = DeploymentStatus.ACTIVE,
                draftId = stored.id.value
            ).activate(clock.now())
        ).getOrThrow()

        // Kunci draf + pin versi pack di tenant (kolom V81 diisi di sini, M2).
        drafts.save(stored.copy(status = DiscoveryDraftStatus.LOCKED, lockedAt = clock.now()))
        tenants.findById(tenantId)?.let { tenant ->
            // Go-live = MULAI jam trial (V88/V89) — BUKAN jadi ACTIVE. Builder gratis tanpa
            // batas; trial aplikasi berjalan setelah app jadi, dan konversi ke ACTIVE terjadi
            // saat pembayaran dikonfirmasi (activate()). Deploy ulang tidak mengatur ulang jam.
            val promoted = tenant.copy(
                trialEndsAt = tenant.trialEndsAt ?: (clock.now() + Tenant.DEFAULT_TRIAL_DAYS.days),
                domainPackVersion = nextVersion
            )
            if (promoted != tenant) tenants.save(promoted)
        }
        activated
    }
}

class DataGateException(message: String) : IllegalStateException(message)

/**
 * Rollback (FR-M2-3): pin tenant kembali ke versi sebelumnya, **append-only** — deployment aktif
 * menjadi `ROLLED_BACK` dan baris versi sebelumnya diaktifkan ulang, bukan diubah isinya.
 *
 * Gerbang data (v1, jujur — discovery-M2 §5): bila tenant punya data operasional dan rollback
 * menurunkan `blueprintRevision` (struktur berubah), rollback **ditolak** dengan [DataGateException]
 * kecuali `force = true` — aksi eksplisit "Arsipkan modul", wajib ter-audit di pemanggil.
 */
class RollbackDeploymentUseCase(
    private val deployments: BuilderDeploymentRepository,
    private val probe: TenantOperationalDataProbe,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, force: Boolean = false): Result<Deployment> = runCatching {
        val active = deployments.findActive(tenantId)
            ?: throw IllegalStateException("Tenant tidak punya deployment aktif")
        val previous = deployments.findByTenant(tenantId)
            .filter { it.number.value < active.number.value }
            .filter { it.packVersion != null }
            .maxByOrNull { it.number.value }
            ?: throw IllegalStateException("Tidak ada versi sebelumnya untuk di-rollback")

        val structureChanges = previous.blueprintRevision != active.blueprintRevision
        if (structureChanges && !force && probe.hasOperationalData(tenantId)) {
            throw DataGateException(
                "Rollback menurunkan blueprint (rev ${active.blueprintRevision} → ${previous.blueprintRevision}) " +
                    "tetapi tenant sudah punya data operasional. Gunakan aksi eksplisit 'Arsipkan modul' (force)."
            )
        }

        deployments.save(active.markRolledBack(clock.now())).getOrThrow()
        deployments.save(previous.activate(clock.now())).getOrThrow()
    }
}

