package com.eventverse.app.domain.tenant

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

data class RegisterTenantCommand(
    val id: String,
    val slug: String,
    val name: String,
    val tier: SubscriptionTier = SubscriptionTier.PRO,
    /** Kerangka tahap industri yang di-provision (TRD-FLOW-001); `null`/tak dikenal = rajut. */
    val industryTemplate: IndustryTemplateCode? = null,
    /**
     * Email user owner yang akan mengelola project ini (PLAN-builder-console F3). `null` = pendaftaran
     * tanpa penentuan owner (mode undangan M0–M1); gerbang satu-owner tetap berlaku bila diisi.
     */
    val ownerEmail: String? = null,
    /** Lama trial hari (V88). Default sesuai kebijakan bawaan platform. */
    val trialDays: Long = DEFAULT_TRIAL_DAYS
) {
    init {
        require(trialDays > 0) { "trialDays harus > 0, dapat $trialDays" }
    }
}

/** Lama trial bawaan platform (hari). */
const val DEFAULT_TRIAL_DAYS = 14L

/**
 * Membuat tenant TRIAL baru — "project" dalam WeMake Builder (1 akun = 1 project).
 *
 * Gerbang kepemilikan (F3): satu email hanya boleh menjadi pemilik (`TENANT_ADMIN`) **satu** tenant.
 * Pelanggaran ditolak 409-level (`error`), bukan diabaikan — begitu daftar publik menyala (M2), tanpa
 * gerbang ini satu orang bisa memonopoli slug tanpa batas. Kolaborator tidak terkena: yang dibatasi
 * kepemilikan, bukan keanggotaan. [users] opsional agar pemanggil lama (mode undangan) tak wajib menyediakannya.
 */
class RegisterTenantUseCase(
    private val tenantRepository: TenantRepository,
    private val users: UserRepository? = null,
    /** Jam domain; default sistem. Param terakhir ber-default = pemanggil lama tak tersentuh. */
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: RegisterTenantCommand): Result<Tenant> = runCatching {
        val tenantId = TenantId(command.id)
        val tenantSlug = TenantSlug(command.slug)
        val tenantName = TenantName(command.name)

        if (tenantRepository.existsBySlug(tenantSlug)) {
            error("Subdomain '${tenantSlug.value}' is already taken")
        }

        val existingTenant = tenantRepository.findById(tenantId)
        if (existingTenant != null) {
            error("Tenant with ID '${tenantId.value}' already exists")
        }

        val ownerEmail = command.ownerEmail?.trim()?.takeIf { it.isNotEmpty() }?.let(::EmailAddress)
        if (ownerEmail != null && users != null) {
            val existing = users.findByEmail(ownerEmail)
            if (existing != null && existing.role != Role.PLATFORM_SUPERADMIN) {
                error(
                    "Email '${ownerEmail.value}' already owns project '${existing.tenantId?.value ?: "-"}'; " +
                        "satu akun hanya boleh memiliki satu project (409)"
                )
            }
        }

        val newTenant = Tenant(
            id = tenantId,
            slug = tenantSlug,
            name = tenantName,
            status = TenantStatus.TRIAL,
            tier = command.tier,
            activeMachineCount = 0,
            industryTemplate = command.industryTemplate ?: IndustryTemplateCode.KNIT_SWEATER,
            trialEndsAt = clock.now() + command.trialDays.days
        )

        tenantRepository.save(newTenant).getOrThrow()
    }
}
