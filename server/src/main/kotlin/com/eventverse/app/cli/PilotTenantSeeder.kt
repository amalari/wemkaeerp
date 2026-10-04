package com.eventverse.app.cli

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.blueprint.BlueprintModule
import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.PrototypeScreen
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.LayananPilotPack
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.tenant.SubscriptionTier
import com.eventverse.app.domain.tenant.Tenant
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.tenant.TenantName
import com.eventverse.app.domain.tenant.TenantRepository
import com.eventverse.app.domain.tenant.TenantSlug
import com.eventverse.app.domain.tenant.TenantStatus

/**
 * Menyiapkan tenant pilot `layanan-demo` di **database uji** (Jalur C, butir C4): pack data `layanan`
 * (LOCKED), tenant ber-`domain_pack = 'layanan'`, satu pemilik, dan draf kerja berlayar papan pilot
 * berbinding Api. Langkah dan alasannya: `docs/plannings/discovery-DP-pilot-activation.md` §5.
 *
 * **Idempoten**: dijalankan ulang tidak menggandakan apa pun. Pack hanya naik versi bila isinya berubah
 * (Kontrak 5: versi terkunci tidak disunting, revisi = versi baru). Draf yang sudah ada **tidak ditimpa**
 * (pekerjaan tim di atasnya tak boleh hilang oleh semai ulang).
 *
 * **Penjaga**: [requireScratchDatabase] menolak nama database tanpa `scratch`. Data demo tidak boleh
 * masuk lewat migrasi maupun menyentuh database dev bersama.
 */
class PilotTenantSeeder(
    private val packs: DomainPackRepository,
    private val tenants: TenantRepository,
    private val users: UserRepository,
    private val drafts: DiscoveryDraftRepository
) {
    data class Report(val lines: List<String>)

    suspend fun seed(config: Config = Config()): Report {
        val log = mutableListOf<String>()
        val tenantId = TenantId(config.tenantId)

        val latest = packs.findLatest(LayananPilotPack.CODE)
        when {
            latest == null -> { packs.save(StoredDomainPack(LayananPilotPack.pack, 1, DomainPackStatus.LOCKED, null)); log += "pack layanan: dibuat v1 (LOCKED)" }
            latest.pack != LayananPilotPack.pack -> {
                packs.save(StoredDomainPack(LayananPilotPack.pack, latest.version + 1, DomainPackStatus.LOCKED, null))
                log += "pack layanan: isi berubah, dibuat v${latest.version + 1} (LOCKED)"
            }
            else -> log += "pack layanan: sudah v${latest.version}, tidak berubah"
        }

        if (tenants.findById(tenantId) == null) {
            tenants.save(
                Tenant(tenantId, TenantSlug(config.slug), TenantName(config.tenantName), TenantStatus.ACTIVE, SubscriptionTier.PRO, domainPack = LayananPilotPack.CODE)
            ).getOrThrow()
            log += "tenant ${config.slug}: dibuat"
        } else log += "tenant ${config.slug}: sudah ada"

        val ownerEmail = EmailAddress(config.ownerEmail)
        val owner = users.findByEmail(ownerEmail) ?: users.save(
            User(UserId(config.ownerUserId), tenantId, Username(config.ownerUsername), ownerEmail, Role.TENANT_ADMIN, isActive = true)
        ).getOrThrow().also { log += "pemilik ${config.ownerUsername}: dibuat" }
        if (log.none { it.startsWith("pemilik") }) log += "pemilik ${config.ownerUsername}: sudah ada"

        if (drafts.findByTenant(tenantId) == null) {
            drafts.save(
                StoredDiscoveryDraft(
                    id = DiscoveryDraftId("draft-${config.tenantId}"), ownerUserId = owner.id, tenantId = tenantId,
                    draft = DiscoveryDraft(
                        LayananPilotPack.pack,
                        Blueprint(
                            BlueprintCode("layanan_starter"), LayananPilotPack.CODE, "Starter Layanan", "Pilot", "Melacak permintaan perubahan klien", "Tim layanan",
                            listOf(BlueprintModule(LayananPilotPack.CHANGE_REQUEST.value, active = true))
                        ),
                        listOf(PrototypeScreen("default-${LayananPilotPack.CHANGE_REQUEST.value}", LayananPilotPack.CHANGE_REQUEST, "Papan Permintaan", "KANBAN"))
                    )
                )
            )
            log += "draf kerja: dibuat"
        } else log += "draf kerja: sudah ada, tidak ditimpa"
        return Report(log)
    }

    data class Config(
        val slug: String = "layanan-demo",
        val tenantId: String = "ten-layanan-demo",
        val tenantName: String = "Layanan Demo",
        val ownerUserId: String = "usr-layanan-demo-owner",
        val ownerUsername: String = "owner_layanan",
        val ownerEmail: String = "owner@layanan-demo.test"
    )

    companion object {
        /** Menolak database yang namanya tak berisi `scratch` — satu-satunya pagar antara alat ini dan DB dev. */
        fun requireScratchDatabase(dbName: String?) {
            require(!dbName.isNullOrBlank() && "scratch" in dbName) {
                "Semai tenant pilot hanya boleh ke database uji (nama berisi 'scratch'); DB_NAME='${dbName.orEmpty()}' ditolak"
            }
        }
    }
}
