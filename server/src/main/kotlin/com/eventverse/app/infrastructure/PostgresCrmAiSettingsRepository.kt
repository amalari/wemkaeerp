package com.eventverse.app.infrastructure

import com.eventverse.app.domain.crm.prefill.CrmAiSettingsRepository
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.CrmAiSettingsTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/** Opt-in draf lead AI (tabel V84). Satu baris per tenant, di-upsert; tidak ada baris = mati. */
class PostgresCrmAiSettingsRepository : CrmAiSettingsRepository {

    override suspend fun isLeadDraftEnabled(tenantId: TenantId): Boolean = DatabaseFactory.dbQuery(tenantId) {
        CrmAiSettingsTable.selectAll().where { CrmAiSettingsTable.tenantId eq tenantId.value }
            .singleOrNull()?.get(CrmAiSettingsTable.leadDraftEnabled) ?: false
    }

    override suspend fun setLeadDraftEnabled(tenantId: TenantId, enabled: Boolean, updatedByUserId: String?) {
        DatabaseFactory.dbQuery(tenantId) {
            val now = Clock.System.now()
            val updated = CrmAiSettingsTable.update({ CrmAiSettingsTable.tenantId eq tenantId.value }) {
                it[leadDraftEnabled] = enabled
                it[CrmAiSettingsTable.updatedByUserId] = updatedByUserId
                it[updatedAt] = now
            }
            if (updated == 0) CrmAiSettingsTable.insert {
                it[CrmAiSettingsTable.tenantId] = tenantId.value
                it[leadDraftEnabled] = enabled
                it[CrmAiSettingsTable.updatedByUserId] = updatedByUserId
                it[updatedAt] = now
            }
        }
    }
}

/** Padanan in-memory untuk test & mode tanpa DB. */
class InMemoryCrmAiSettingsRepository : CrmAiSettingsRepository {
    private val enabled = mutableMapOf<TenantId, Boolean>()
    override suspend fun isLeadDraftEnabled(tenantId: TenantId): Boolean = enabled[tenantId] ?: false
    override suspend fun setLeadDraftEnabled(tenantId: TenantId, enabled: Boolean, updatedByUserId: String?) {
        this.enabled[tenantId] = enabled
    }
}
