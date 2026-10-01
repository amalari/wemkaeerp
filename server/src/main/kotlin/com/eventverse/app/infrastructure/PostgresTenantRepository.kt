package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.tables.TenantsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

/**
 * PostgreSQL implementation of TenantRepository utilizing JetBrains Exposed and HikariCP.
 */
class PostgresTenantRepository : TenantRepository {

    override suspend fun findById(id: TenantId): Tenant? = DatabaseFactory.dbQuery {
        TenantsTable.selectAll()
            .where { TenantsTable.id eq id.value }
            .map { toTenant(it) }
            .singleOrNull()
    }

    override suspend fun findBySlug(slug: TenantSlug): Tenant? = DatabaseFactory.dbQuery {
        TenantsTable.selectAll()
            .where { TenantsTable.slug eq slug.value }
            .map { toTenant(it) }
            .singleOrNull()
    }

    override suspend fun save(tenant: Tenant): Result<Tenant> = runCatching {
        DatabaseFactory.dbQuery {
            val exists = TenantsTable.selectAll()
                .where { TenantsTable.id eq tenant.id.value }
                .count() > 0

            if (exists) {
                TenantsTable.update({ TenantsTable.id eq tenant.id.value }) {
                    it[slug] = tenant.slug.value
                    it[name] = tenant.name.value
                    it[status] = tenant.status.name
                    it[tier] = tenant.tier.name
                    it[activeMachineCount] = tenant.activeMachineCount
                    it[businessPreset] = tenant.businessPreset.code.value
                    it[industryTemplate] = tenant.industryTemplate.name
                    it[domainPack] = tenant.domainPack.value
                    it[domainPackVersion] = tenant.domainPackVersion
                    // NULL tidak ditulis: biarkan DEFAULT DB (V88) berlaku untuk tenant baru.
                    tenant.trialEndsAt?.let { endsAt -> it[trialEndsAt] = endsAt }
                }
            } else {
                TenantsTable.insert {
                    it[id] = tenant.id.value
                    it[slug] = tenant.slug.value
                    it[name] = tenant.name.value
                    it[status] = tenant.status.name
                    it[tier] = tenant.tier.name
                    it[activeMachineCount] = tenant.activeMachineCount
                    it[businessPreset] = tenant.businessPreset.code.value
                    it[industryTemplate] = tenant.industryTemplate.name
                    it[domainPack] = tenant.domainPack.value
                    it[domainPackVersion] = tenant.domainPackVersion
                    // NULL = biarkan nilai lama (tenant legacy tanpa jam tidak dinolkan ulang).
                    tenant.trialEndsAt?.let { endsAt -> it[trialEndsAt] = endsAt }
                }
            }
            tenant
        }
    }

    override suspend fun existsBySlug(slug: TenantSlug): Boolean = DatabaseFactory.dbQuery {
        TenantsTable.selectAll()
            .where { TenantsTable.slug eq slug.value }
            .count() > 0
    }

    override suspend fun findAll(): List<Tenant> = DatabaseFactory.dbQuery {
        TenantsTable.selectAll().map { toTenant(it) }
    }

    private fun toTenant(row: ResultRow): Tenant = Tenant(
        id = TenantId(row[TenantsTable.id]),
        slug = TenantSlug(row[TenantsTable.slug]),
        name = TenantName(row[TenantsTable.name]),
        status = TenantStatus.valueOf(row[TenantsTable.status]),
        tier = SubscriptionTier.valueOf(row[TenantsTable.tier]),
        activeMachineCount = row[TenantsTable.activeMachineCount],
        businessPreset = GarmentBlueprints.parse(row[TenantsTable.businessPreset]),
        // Nilai asing jatuh ke rajut: satu-satunya kerangka sebelum kolom ini ada (V74).
        industryTemplate = IndustryTemplateCode.parseOrNull(row[TenantsTable.industryTemplate]) ?: IndustryTemplateCode.KNIT_SWEATER,
        // Kode tak dikenal tetap dibaca apa adanya; plugin tenant menolaknya 409 (B7 FR-4), bukan jatuh ke garment.
        domainPack = DomainPackCode(row[TenantsTable.domainPack]),
        domainPackVersion = row[TenantsTable.domainPackVersion],
        trialEndsAt = row[TenantsTable.trialEndsAt]
    )
}
