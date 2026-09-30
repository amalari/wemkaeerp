package com.eventverse.app.domain.builder

import com.eventverse.app.domain.common.DomainEvent
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/**
 * Agregat deployment WeMake Builder (PLAN-builder-console §4): satu baris riwayat "kunci versi + aktifkan"
 * milik satu tenant. Netral industri — tidak ada kosakata garment di sini.
 *
 * Dua bentuk kelahiran:
 * - [DeploymentStatus.IMPORTED]: snapshot keadaan tenant yang sudah berjalan sebelum Builder ada (migrasi V81). Versi pack
 *   **tidak** diketahui (pack shipped seperti garment bukan baris `domain_packs`), yang dicatat adalah
 *   [appBuild].
 * - hasil deploy (M2, [DeploymentStatus.ACTIVE]): pack data terkunci versi [packVersion] + salinan blueprint
 *   [blueprintRevision].
 */
enum class DeploymentStatus {
    VALIDATING,
    BLOCKED_ON_BUILD,
    ACTIVE,
    SUPERSEDED,
    FAILED,
    ROLLED_BACK,
    IMPORTED
}

@JvmInline
value class DeploymentNumber(val value: Int) {
    init {
        require(value > 0) { "Nomor deployment harus positif: $value" }
    }
}

@JvmInline
value class DeploymentId(val value: String) {
    init {
        require(value.isNotBlank()) { "DeploymentId kosong" }
    }
}

data class Deployment(
    val id: DeploymentId,
    val tenantId: TenantId,
    val number: DeploymentNumber,
    val packCode: DomainPackCode,
    /** Versi pack data terkunci. `null` hanya untuk [IMPORTED] dan [FAILED] sebelum kunci (pack shipped / gagal kunci). */
    val packVersion: Int? = null,
    /** Versi build aplikasi saat snapshot (khusus [IMPORTED]). */
    val appBuild: String? = null,
    /** Revisi blueprint yang diaktifkan; impor mengambil kondisi tenant apa adanya = revisi 1. */
    val blueprintRevision: Int = 1,
    val status: DeploymentStatus,
    val draftId: String? = null,
    val createdAt: Instant? = null,
    val activatedAt: Instant? = null
) {
    init {
        require(blueprintRevision > 0) { "Revisi blueprint harus positif" }
        require(packVersion == null || packVersion > 0) { "Versi pack harus positif" }
        val versionKnown = status !in setOf(DeploymentStatus.IMPORTED, DeploymentStatus.FAILED, DeploymentStatus.VALIDATING)
        require(!versionKnown || packVersion != null) {
            "Deployment #${number.value} berstatus $status wajib membawa versi pack terkunci"
        }
        require(status != DeploymentStatus.IMPORTED || !appBuild.isNullOrBlank()) {
            "Deployment IMPORTED wajib mencatat versi build aplikasi sebagai snapshotnya"
        }
    }

    /** Deployment yang sedang menjalankan tenant; hanya satu pada satu waktu (invarian repository). */
    val isActive: Boolean get() = status == DeploymentStatus.ACTIVE || status == DeploymentStatus.IMPORTED

    fun activate(at: Instant): Deployment = copy(status = DeploymentStatus.ACTIVE, activatedAt = at)

    fun markRolledBack(at: Instant): Deployment = copy(status = DeploymentStatus.ROLLED_BACK, activatedAt = null)

    fun supersede(at: Instant): Deployment = copy(status = DeploymentStatus.SUPERSEDED)
}

data class DeploymentActivated(val tenantId: TenantId, val number: DeploymentNumber, val occurredAt: Instant) : DomainEvent
data class DeploymentRolledBack(val tenantId: TenantId, val number: DeploymentNumber, val occurredAt: Instant) : DomainEvent

/**
 * Penyimpanan deployment milik tenant (schema `builder`, V81, RLS `apply_tenant_rls_in`). Aturan nomor &
 * transisi status milik use case; repository menyimpan baris apa adanya — **kecuali** invarian "tepat satu
 * deployment aktif per tenant", yang ditegakkan di [save] karena hanya di situ baris baru masuk.
 */
interface BuilderDeploymentRepository {

    /** Riwayat tenant, terbaru dulu. Deployment tenant lain tidak pernah keluar lewat sini. */
    suspend fun findByTenant(tenantId: TenantId): List<Deployment>

    /** Deployment yang sedang menjalankan tenant, atau `null` bila belum ada (tenant pra-deploy). */
    suspend fun findActive(tenantId: TenantId): Deployment?

    /** Nomor berikutnya untuk tenant — agregat baris per tenant, bukan counter global. */
    suspend fun nextNumber(tenantId: TenantId): DeploymentNumber

    suspend fun save(deployment: Deployment): Result<Deployment>
}

/** Deployment tidak sah (mis. nomor bentrok dengan riwayat tenant). */
class DeploymentConflictException(message: String) : IllegalStateException(message)
