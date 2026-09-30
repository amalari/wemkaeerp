package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftStatus
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DiscoveryDraftsTable
import com.eventverse.app.shared.discovery.DiscoveryDraftCodec
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Draf discovery (tabel V78). Baris yang gagal didekode **melempar** (Kontrak 4) — alasan sama dengan
 * `PostgresDomainPackRepository`: diam-diam menganggapnya tidak ada berarti draf pemiliknya hilang
 * tanpa jejak, atau draf baru menimpa dokumen yang sebenarnya ada.
 */
class PostgresDiscoveryDraftRepository : DiscoveryDraftRepository {

    override suspend fun findById(id: DiscoveryDraftId): StoredDiscoveryDraft? = DatabaseFactory.dbQuery {
        DiscoveryDraftsTable.selectAll().where { DiscoveryDraftsTable.id eq id.value }.firstOrNull()?.let(::toStored)
    }

    override suspend fun findByOwner(ownerUserId: UserId): List<StoredDiscoveryDraft> = DatabaseFactory.dbQuery {
        DiscoveryDraftsTable.selectAll()
            .where { DiscoveryDraftsTable.ownerUserId eq ownerUserId.value }
            .orderBy(DiscoveryDraftsTable.createdAt, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .map(::toStored)
    }

    override suspend fun findAll(): List<StoredDiscoveryDraft> = DatabaseFactory.dbQuery {
        DiscoveryDraftsTable.selectAll().orderBy(DiscoveryDraftsTable.createdAt, order = org.jetbrains.exposed.sql.SortOrder.DESC).map(::toStored)
    }

    override suspend fun save(stored: StoredDiscoveryDraft): StoredDiscoveryDraft {
        val json = DiscoveryDraftCodec.encodeToString(stored.draft)
        DatabaseFactory.dbQuery {
            val updated = DiscoveryDraftsTable.update({ DiscoveryDraftsTable.id eq stored.id.value }) {
                it[status] = stored.status.name
                it[document] = json
                it[schemaVersion] = stored.schemaVersion
                it[tenantId] = stored.tenantId?.value
                it[updatedAt] = stored.updatedAt ?: kotlinx.datetime.Clock.System.now()
                it[lockedAt] = stored.lockedAt
            }
            if (updated == 0) {
                DiscoveryDraftsTable.insert {
                    it[id] = stored.id.value
                    it[ownerUserId] = stored.ownerUserId.value
                    it[prospectLeadId] = stored.prospectLeadId
                    it[status] = stored.status.name
                    it[document] = json
                    it[schemaVersion] = stored.schemaVersion
                    it[tenantId] = stored.tenantId?.value
                    it[createdAt] = stored.createdAt ?: kotlinx.datetime.Clock.System.now()
                    it[updatedAt] = stored.updatedAt ?: kotlinx.datetime.Clock.System.now()
                    it[lockedAt] = stored.lockedAt
                }
            }
        }
        return stored
    }

    private fun toStored(row: ResultRow) = StoredDiscoveryDraft(
        id = DiscoveryDraftId(row[DiscoveryDraftsTable.id]),
        ownerUserId = UserId(row[DiscoveryDraftsTable.ownerUserId]),
        draft = DiscoveryDraftCodec.decode(row[DiscoveryDraftsTable.document]),
        status = DiscoveryDraftStatus.valueOf(row[DiscoveryDraftsTable.status]),
        schemaVersion = row[DiscoveryDraftsTable.schemaVersion],
        prospectLeadId = row[DiscoveryDraftsTable.prospectLeadId],
        tenantId = row[DiscoveryDraftsTable.tenantId]?.let(::TenantId),
        createdAt = row[DiscoveryDraftsTable.createdAt],
        updatedAt = row[DiscoveryDraftsTable.updatedAt],
        lockedAt = row[DiscoveryDraftsTable.lockedAt]
    )
}
