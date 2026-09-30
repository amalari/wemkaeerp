package com.eventverse.app.domain.discovery

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.blueprint.Blueprint
import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlin.jvm.JvmInline

/** Siklus hidup draf discovery (plan §2 A5). Enum sah (Uji Variabilitas): milik platform, bukan kosakata vertikal. */
enum class DiscoveryDraftStatus { DRAFT, LOCKED }

@JvmInline
value class DiscoveryDraftId(val value: String) {
    init { require(value.isNotBlank()) { "DiscoveryDraftId kosong" } }
}

/**
 * Deskriptor satu layar prototype (plan §4, Fase C). Fase A hanya **menyimpan** deskriptornya — renderer &
 * `WidgetRegistry` menyusul, dan [widget] saat itu dibatasi ke kosakata tertutup (`FORM`, `TABLE`, `KANBAN`, …).
 * V1 sengaja tidak memvalidasi [widget] supaya Fase C bebas menetapkan kosakatanya tanpa migrasi dokumen lama.
 */
data class PrototypeScreen(
    val screenId: String,
    val moduleId: ModuleId,
    val title: String,
    val widget: String
) {
    init {
        require(screenId.isNotBlank()) { "PrototypeScreen.screenId kosong" }
        require(title.isNotBlank()) { "PrototypeScreen.title kosong" }
        require(widget.isNotBlank()) { "PrototypeScreen.widget kosong" }
    }
}

/**
 * Keluaran tahap discovery (plan §1, T1): **bukan DSL baru**, melainkan bungkus kontrak yang sudah ada —
 * [DomainPack] (kosakata vertikal, B7) + [Blueprint] (alur, B4) + deskriptor layar (Fase C).
 *
 * Keluaran generator AI **tidak tepercaya**: struktur di sini baru dibentuk setelah dokumen JSON lolos
 * `DiscoveryDraftCodec` (dekode ketat) dan `DiscoveryDraftValidator`. Invarian lintas-bagian ditegakkan di sini
 * sebagai lapisan kedua: blueprint hanya boleh menyebut modul milik pack-nya sendiri.
 */
data class DiscoveryDraft(
    val pack: DomainPack,
    val blueprint: Blueprint,
    val screens: List<PrototypeScreen> = emptyList()
) {
    init {
        require(blueprint.pack == pack.code) {
            "Blueprint ${blueprint.code.value} menunjuk pack ${blueprint.pack.value}, bukan ${pack.code.value}"
        }
        val moduleIds = pack.modules.map { it.id.value }.toSet()
        blueprint.modules.forEach { m ->
            require(m.moduleCode in moduleIds) {
                "Blueprint ${blueprint.code.value} menyebut modul '${m.moduleCode}' yang tidak ada di pack ${pack.code.value}"
            }
        }
        screens.forEach { s ->
            require(s.moduleId.value in moduleIds) {
                "Layar ${s.screenId} menunjuk modul '${s.moduleId.value}' yang tidak ada di pack ${pack.code.value}"
            }
        }
    }
}

/**
 * Satu baris `ops.discovery_drafts` (V78): dokumen [draft] beserta metadata pemilik & siklus hidupnya.
 * [ownerUserId] wajib (T12) — funnel ber-login, dan gerbang A6 adalah *pemilik draf atau superadmin*.
 * [LOCKED][status] membeku dokumen (Kontrak 5): revisi berikutnya tidak boleh lewat update di tempat.
 */
data class StoredDiscoveryDraft(
    val id: DiscoveryDraftId,
    val ownerUserId: UserId,
    val draft: DiscoveryDraft,
    val status: DiscoveryDraftStatus = DiscoveryDraftStatus.DRAFT,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val prospectLeadId: String? = null,
    /**
     * Tenant pemilik draf (PLAN-builder-console §4, V81). `null` untuk draf funnel lama yang milik pribadi
     * pemanggil — tidak di-backfill, sejarah kepemilikannya tidak berubah.
     */
    val tenantId: TenantId? = null,
    val createdAt: Instant? = null,
    val updatedAt: Instant? = null,
    val lockedAt: Instant? = null
) {
    init { require(schemaVersion > 0) { "schemaVersion harus positif" } }

    companion object {
        /** Naikkan hanya bila dokumen lama butuh transformasi saat dibaca (pola snapshot beku, V73). */
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

/** Penyimpanan draf discovery. Tabel `ops.*`: platform-global, tanpa RLS tenant (pola prospect_leads). */
interface DiscoveryDraftRepository {
    suspend fun findById(id: DiscoveryDraftId): StoredDiscoveryDraft?

    /** Milik satu pengguna, terbaru dulu — draf siapa pun tidak pernah bocor lewat sini. */
    suspend fun findByOwner(ownerUserId: UserId): List<StoredDiscoveryDraft>

    /** Draf kerja milik tenant (PLAN-builder-console M1): satu tenant, satu working draft. */
    suspend fun findByTenant(tenantId: TenantId): StoredDiscoveryDraft?

    suspend fun findAll(): List<StoredDiscoveryDraft>

    /** Menyimpan baris apa adanya; aturan status & kepemilikan milik use case, bukan repository. */
    suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft
}
