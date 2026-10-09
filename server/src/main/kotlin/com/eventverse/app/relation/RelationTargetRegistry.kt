package com.eventverse.app.relation

import com.eventverse.app.domain.crm.CrmLeadRepository
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.discovery.handoff.PrototypeRowRepository
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleReferenceRules
import com.eventverse.app.domain.prototype.PrototypeRow
import com.eventverse.app.domain.prototype.relationTargetFormatError
import com.eventverse.app.domain.rbac.BusinessModules
import com.eventverse.app.domain.rbac.isOperational
import com.eventverse.app.domain.tenant.TenantId

/** Satu opsi rujukan yang dikembalikan route opsi & diverifikasi [RelationTargetResolver]. */
data class RelationOption(val id: String, val label: String)

/**
 * Sumber baris satu modul target untuk tipe field `RELATION` (C7, TRD-FIELD-001). Hidup di
 * **server** — bukan domain — karena merekatkan repositori konkret (baris modul handoff, lead CRM)
 * ke kontrak rujukan. [RelationTargetRegistry] memetakannya per **kode modul** target; modul yang
 * tidak punya sumber = tidak ada opsi (fail-closed, bukan fallback ke modul lain).
 */
interface RelationTargetSource {
    /**
     * Opsi maksimum [limit] baris yang cocok [query] (cocok label atau id) pada tenant [tenantId].
     * [entity] = entitas target yang diminta klien; [reachableOwnerIds] = jangkauan data pemanggil
     * atas modul target (`null` = seluruh tenant) — sumber dengan data kolektif boleh mengabaikannya,
     * sumber hierarkis (CRM) **wajib** memakainya supaya route tidak membocorkan data di luar scope.
     */
    suspend fun options(
        tenantId: TenantId,
        entity: String,
        reachableOwnerIds: Set<OrgNodeId>?,
        query: String,
        limit: Int
    ): List<RelationOption>

    /** `true` bila record [recordId] ada pada tenant yang sama (validasi tulis, FR-2). */
    suspend fun exists(tenantId: TenantId, recordId: String): Boolean
}

/**
 * Registri sumber rujukan per kode modul. Urutan tolakan route memakai ini; [sourceFor] mengembalikan
 * `null` untuk modul tanpa sumber (dan modul governance/foundation tidak pernah ada di sini — bukan target, R1).
 */
class RelationTargetRegistry(private val sources: Map<String, RelationTargetSource>) {

    fun sourceFor(moduleCode: String): RelationTargetSource? = sources[moduleCode]

    companion object {
        /**
         * Sumber bawaan: baris modul handoff ([recordRows], key = kode modul) + lead CRM untuk `crm_sales`.
         * Tidak ada daftar modul tertutup di sini — modul yang tidak punya baris = tidak punya sumber.
         */
        fun default(
            crmLeads: CrmLeadRepository,
            recordRows: Map<String, PrototypeRowRepository>
        ): RelationTargetRegistry {
            val sources = HashMap<String, RelationTargetSource>()
            recordRows.forEach { (code, rows) -> sources[code] = PrototypeRowRelationSource(rows) }
            sources["crm_sales"] = CrmLeadRelationSource(crmLeads)
            return RelationTargetRegistry(sources)
        }
    }
}

/** Sumber prototype: baris modul hasil handoff. Label v1 = nilai teks pertama baris, fallback id (TRD R3). */
class PrototypeRowRelationSource(private val rows: PrototypeRowRepository) : RelationTargetSource {

    /**
     * Baris modul handoff bersifat kolektif pabrik (`GLOBAL_ONLY`) — [entity]/[reachableOwnerIds]
     * diabaikan; pencarian ditekan ke [PrototypeRowRepository.search] agar tidak memuat seluruh tabel.
     */
    override suspend fun options(
        tenantId: TenantId,
        entity: String,
        reachableOwnerIds: Set<OrgNodeId>?,
        query: String,
        limit: Int
    ): List<RelationOption> =
        rows.search(tenantId, query, limit).map { RelationOption(it.id, labelOf(it)) }

    override suspend fun exists(tenantId: TenantId, recordId: String): Boolean =
        rows.find(tenantId, recordId) != null

    private fun labelOf(row: PrototypeRow): String =
        row.values.values.firstOrNull { it.isNotBlank() }?.take(MAX_LABEL_CHARS) ?: row.id

    private companion object {
        const val MAX_LABEL_CHARS = 120
    }
}

/** Sumber CRM: lead pada tenant yang sama. Label = [CrmLead.title] (aturan label kanonik core). */
class CrmLeadRelationSource(private val leads: CrmLeadRepository) : RelationTargetSource {

    /** Hierarkis: jangkauan data pemanggil ([reachableOwnerIds]) **wajib** diteruskan ke SQL. */
    override suspend fun options(
        tenantId: TenantId,
        entity: String,
        reachableOwnerIds: Set<OrgNodeId>?,
        query: String,
        limit: Int
    ): List<RelationOption> =
        leads.searchActive(tenantId, reachableOwnerIds, query, limit)
            .map { RelationOption(it.id.value, it.title.take(MAX_LABEL_CHARS)) }

    override suspend fun exists(tenantId: TenantId, recordId: String): Boolean =
        leads.findById(tenantId, LeadId(recordId)) != null

    private companion object {
        const val MAX_LABEL_CHARS = 120
    }
}

/**
 * Implementasi [RelationTargetResolver] (Track B, TRD-FIELD-001 FR-2): target "ada dan dapat dirujuk".
 *
 * `targetResource` = **kode modul** target. Valid hanya bila modul dikenal proses **dan** dapat diresolusi
 * pack (modul sendiri pack atau modul bersama yang ditawarkan lewat `sharedModules`/R1
 * [ModuleReferenceRules]) **dan** [isOperational] — modul governance/foundation **tidak** bisa jadi target
 * (diakses lewat salinan identik, bukan rujukan). Modul tanpa sumber = `false` (fail-closed, tanpa
 * fallback). Keberadaan record dicek di sumber modul itu pada tenant yang sama.
 */
class RegistryRelationTargetResolver(private val registry: RelationTargetRegistry) : RelationTargetResolver {

    override suspend fun exists(tenantId: TenantId, targetResource: String, targetRecordId: String): Boolean {
        if (targetRecordId.isBlank()) return false
        val module = resolvableTarget(targetResource) ?: return false
        return registry.sourceFor(module.value)?.exists(tenantId, targetRecordId) ?: false
    }

    /** Modul target yang sah dirujuk, atau `null` (fail-closed). */
    private fun resolvableTarget(targetResource: String): ModuleId? {
        // Bentuk `entityId` atau `moduleId:entityId` (Kontrak 4: parser tunggal, tanpa fallback);
        // modul target = bagian sebelum ':' — satu-satunya bagian yang dibutuhkan untuk resolusi.
        if (relationTargetFormatError(targetResource) != null) return null
        val moduleCode = targetResource.substringBefore(':').ifBlank { return null }
        val module = BusinessModules.fromCode(moduleCode) ?: return null
        if (!module.isOperational) return null
        val known = DomainPackRegistry.moduleDefinition(module) != null || ModuleReferenceRules.offered(module) != null
        return module.takeIf { known }
    }
}
