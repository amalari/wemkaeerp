package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.UnresolvableBlueprintException
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.tenant.*
import com.eventverse.app.infrastructure.tables.TenantsTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp
import org.slf4j.LoggerFactory

/**
 * PostgreSQL implementation of TenantRepository utilizing JetBrains Exposed and HikariCP.
 */
class PostgresTenantRepository(
    private val packs: DomainPackRepository = PostgresDomainPackRepository()
) : TenantRepository {

    private val logger = LoggerFactory.getLogger(PostgresTenantRepository::class.java)

    override suspend fun findById(id: TenantId): Tenant? =
        loadRows { TenantsTable.selectAll().where { TenantsTable.id eq id.value } }.singleOrNull()?.let { toTenant(it, TenantBlueprintResolver(packs)) }

    override suspend fun findBySlug(slug: TenantSlug): Tenant? =
        loadRows { TenantsTable.selectAll().where { TenantsTable.slug eq slug.value } }.singleOrNull()?.let { toTenant(it, TenantBlueprintResolver(packs)) }

    private suspend fun loadRows(query: () -> Query): List<TenantRow> = DatabaseFactory.dbQuery { query().map(::toRow) }

    override suspend fun save(tenant: Tenant): Result<Tenant> = runCatching {
        // Fail-closed (TRD-PLAT-008 FR-5): kode starter yang tak bisa dibaca ulang tidak boleh ditulis.
        TenantBlueprintResolver(packs).require(tenant.slug.value, tenant.domainPack, tenant.domainPackVersion, tenant.businessPreset.code.value)
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

    /**
     * Isolasi baris (TRD-PLAT-008 K5): tenant yang starter-nya tak ter-resolve dilewati dan dicatat `ERROR` — satu
     * baris rusak tidak boleh menggagalkan daftar tenant platform. Tenant itu tidak diubah menjadi tenant lain.
     */
    override suspend fun findAll(): List<Tenant> {
        val resolver = TenantBlueprintResolver(packs)
        return loadRows { TenantsTable.selectAll() }.mapNotNull { row ->
            try {
                toTenant(row, resolver)
            } catch (e: UnresolvableBlueprintException) {
                logger.error("Tenant dilewati dari findAll(): {}", e.message)
                null
            }
        }
    }

    /** Baris mentah: kode starter belum di-resolve (butuh pack, jadi dikerjakan di luar transaksi baca). */
    private class TenantRow(val row: ResultRow) {
        val slug: String get() = row[TenantsTable.slug]
        val presetCode: String get() = row[TenantsTable.businessPreset]
        val pack: DomainPackCode get() = DomainPackCode(row[TenantsTable.domainPack])
        val packVersion: Int? get() = row[TenantsTable.domainPackVersion]
    }

    private fun toRow(row: ResultRow) = TenantRow(row)

    private suspend fun toTenant(r: TenantRow, resolver: TenantBlueprintResolver): Tenant {
        val blueprint = resolver.require(r.slug, r.pack, r.packVersion, r.presetCode)
        val row = r.row
        return Tenant(
            id = TenantId(row[TenantsTable.id]),
            slug = TenantSlug(row[TenantsTable.slug]),
            name = TenantName(row[TenantsTable.name]),
            status = TenantStatus.valueOf(row[TenantsTable.status]),
            tier = SubscriptionTier.valueOf(row[TenantsTable.tier]),
            activeMachineCount = row[TenantsTable.activeMachineCount],
            businessPreset = blueprint,
            // Nilai asing jatuh ke rajut: satu-satunya kerangka sebelum kolom ini ada (V74).
            industryTemplate = IndustryTemplateCode.parseOrNull(row[TenantsTable.industryTemplate]) ?: IndustryTemplateCode.KNIT_SWEATER,
            // Kode tak dikenal tetap dibaca apa adanya; plugin tenant menolaknya 409 (B7 FR-4), bukan jatuh ke garment.
            domainPack = DomainPackCode(row[TenantsTable.domainPack]),
            domainPackVersion = row[TenantsTable.domainPackVersion],
            trialEndsAt = row[TenantsTable.trialEndsAt]
        )
    }
}
