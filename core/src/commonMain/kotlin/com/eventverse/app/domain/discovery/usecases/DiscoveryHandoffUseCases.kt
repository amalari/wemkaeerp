package com.eventverse.app.domain.discovery.usecases

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.usecases.AssignTenantDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.LockDomainPackUseCase
import com.eventverse.app.domain.pack.usecases.SaveDomainPackDraftUseCase
import com.eventverse.app.domain.pack.usecases.TenantOperationalDataProbe
import com.eventverse.app.domain.prospect.LeadSource
import com.eventverse.app.domain.prospect.ProspectLeadId
import com.eventverse.app.domain.prospect.ProspectLeadRepository
import com.eventverse.app.domain.prospect.usecases.SubmitProspectLeadUseCase
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
 * CTA "Bangun Sistem Ini" (plan §3 B2): draf yang sudah **LOCKED** didaftarkan ke funnel tim sebagai
 * [ProspectLead][com.eventverse.app.domain.prospect.ProspectLead].
 *
 * Draf wajib terkunci dulu — yang ditawarkan kepada klien harus beku (Kontrak 5). Menautkan id lead pada
 * baris yang terkunci **bukan** revisi dokumen: kolom `document` tidak berubah satu karakter pun.
 */
class SubmitDiscoveryDraftUseCase(
    private val draftRepository: DiscoveryDraftRepository,
    private val leadRepository: ProspectLeadRepository,
    private val submitLead: SubmitProspectLeadUseCase
) {

    data class Submitted(val draftId: DiscoveryDraftId, val leadId: ProspectLeadId)

    suspend operator fun invoke(
        draftId: DiscoveryDraftId,
        callerUserId: UserId,
        isPlatformSuperadmin: Boolean,
        companyName: String,
        contactName: String? = null,
        contactEmail: String? = null,
        contactPhone: String? = null,
        now: Instant = Clock.System.now()
    ): Result<Submitted> = runCatching {
        val stored = draftRepository.findById(draftId) ?: error("Draf ${draftId.value} tidak ditemukan")
        if (!isPlatformSuperadmin && stored.ownerUserId != callerUserId) {
            throw UpdateDiscoveryDraftUseCase.NotOwnerException("Draf ${draftId.value} bukan milik Anda")
        }
        require(stored.status == DiscoveryDraftStatus.LOCKED) {
            "Draf ${draftId.value} masih DRAFT; kunci dulu sebelum membangun sistemnya"
        }
        val draft = stored.draft
        val narrative = buildString {
            append("Discovery ${draft.pack.displayName}. ")
            append(draft.blueprint.description)
            append(" Modul: ").append(draft.blueprint.activeModuleCodes.sorted().joinToString(", ")).append(".")
        }

        val lead = submitLead(
            id = ProspectLeadId("lead-${now.toEpochMilliseconds()}"),
            companyName = companyName,
            narrativeRaw = narrative,
            contactName = contactName,
            contactEmail = contactEmail,
            contactPhone = contactPhone,
            source = LeadSource.LANDING_PAGE,
            submittedAt = now
        ).getOrThrow()

        leadRepository.save(lead.markTranslated())

        draftRepository.save(stored.copy(prospectLeadId = lead.id.value, updatedAt = now))
        Submitted(draftId, lead.id)
    }
}

/**
 * Handoff otomatis (plan §3 B3): buat tenant → simpan & kunci pack (pemilik = tenant) → tetapkan pack →
 * salin blueprint. Modul langsung tampil di `/m/{code}` tenant baru lewat jalur B7 — tanpa scaffold kode
 * (scaffold untuk tim developer adalah B4, kandidat PR dengan review manusia).
 *
 * **Superadmin saja** — ini provisioning tenant produksi, bukan aksi pemilik draf. Pack bawaan (garment)
 * tidak disimpan ulang: langsung ditetapkan. Pack data yang sudah punya versi berbeda di platform ditolak —
 * menimpanya diam-diam berarti mengubah kosakata tenant lain (plan §7).
 */
class HandoffDiscoveryDraftUseCase(
    private val draftRepository: DiscoveryDraftRepository,
    private val tenantRepository: TenantRepository,
    private val domainPackRepository: DomainPackRepository,
    private val probe: TenantOperationalDataProbe
) {

    data class HandoffResult(val tenant: Tenant, val packCode: DomainPackCode, val packVersion: Int?)

    class ForbiddenException(message: String) : IllegalStateException(message)

    suspend operator fun invoke(
        draftId: DiscoveryDraftId,
        isPlatformSuperadmin: Boolean,
        tenantSlug: String,
        companyName: String
    ): Result<HandoffResult> = runCatching {
        if (!isPlatformSuperadmin) throw ForbiddenException("Handoff hanya boleh superadmin platform")
        val stored = draftRepository.findById(draftId) ?: error("Draf ${draftId.value} tidak ditemukan")
        require(stored.status == DiscoveryDraftStatus.LOCKED) {
            "Draf ${draftId.value} masih DRAFT; kunci dulu sebelum handoff"
        }
        val pack = stored.draft.pack

        // Validasi versi pack **sebelum** tenant dibuat — kegagalan 409 tidak boleh meninggalkan tenant yatim.
        val latest = if (DomainPackRegistry.isShipped(pack.code)) null else domainPackRepository.findLatest(pack.code)
        if (latest != null && domainPackRepository.findEffective(pack.code)?.pack != pack) {
            throw IllegalStateException(
                "Pack ${pack.code.value} sudah punya versi lain di platform; handoff draf ini butuh review manual"
            )
        }

        val slug = TenantSlug(tenantSlug.trim().lowercase())
        val tenant = tenantRepository.findBySlug(slug) ?: tenantRepository.save(
            Tenant(
                id = TenantId("ten-${slug.value}".take(64)),
                slug = slug,
                name = TenantName(companyName.trim()),
                status = TenantStatus.ACTIVE,
                tier = SubscriptionTier.PRO
            )
        ).getOrThrow()

        val version: Int? = when {
            latest == null -> { // Pack bawaan (latest null karena shipped) atau pack data pertama.
                if (DomainPackRegistry.isShipped(pack.code)) {
                    null // Pack bawaan dikirim sebagai kode; tidak ada yang disimpan atau dikunci ulang.
                } else {
                    val saved = SaveDomainPackDraftUseCase(domainPackRepository)(pack, ownerTenantId = tenant.id).getOrThrow()
                    LockDomainPackUseCase(domainPackRepository)(pack.code).getOrThrow()
                    saved.version
                }
            }
            // Versi yang sudah ada wajib identik drafnya — reuse, bukan timpa.
            else -> latest.version
        }

        val assigned = AssignTenantDomainPackUseCase(tenantRepository, probe)(tenant.id, pack.code).getOrThrow()
        val withBlueprint = assigned.copy(businessPreset = stored.draft.blueprint)
        val final = if (withBlueprint != assigned) tenantRepository.save(withBlueprint).getOrThrow() else assigned

        HandoffResult(final, pack.code, version)
    }
}
