package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.DomainPackStatus
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.effectiveOf
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.infrastructure.tables.DomainPacksTable
import com.eventverse.app.shared.pack.DomainPackCodec
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Pack data (tabel V75). Baris yang gagal didekode **melempar** (Kontrak 4): menganggapnya tidak ada berarti tenant
 * pemiliknya berjalan tanpa kosakata — atau lebih buruk, draf baru menimpa versi yang sebenarnya ada.
 */
class PostgresDomainPackRepository : DomainPackRepository {

    override suspend fun findEffective(code: DomainPackCode): StoredDomainPack? = effectiveOf(versions(code))

    override suspend fun findAllEffective(): List<StoredDomainPack> = DatabaseFactory.dbQuery {
        DomainPacksTable.selectAll().map(::toStored)
    }.groupBy { it.pack.code }.values.mapNotNull(::effectiveOf)

    override suspend fun findLatest(code: DomainPackCode): StoredDomainPack? = versions(code).maxByOrNull { it.version }

    override suspend fun save(stored: StoredDomainPack): StoredDomainPack {
        val json = DomainPackCodec.encodeToString(stored.pack)
        DatabaseFactory.dbQuery {
            val where = (DomainPacksTable.code eq stored.pack.code.value) and (DomainPacksTable.version eq stored.version)
            val updated = DomainPacksTable.update({ where }) {
                it[status] = stored.status.name
                it[ownerTenantId] = stored.ownerTenantId?.value
                it[definition] = json
            }
            if (updated == 0) {
                DomainPacksTable.insert {
                    it[code] = stored.pack.code.value
                    it[version] = stored.version
                    it[status] = stored.status.name
                    it[ownerTenantId] = stored.ownerTenantId?.value
                    it[definition] = json
                    it[createdAt] = Clock.System.now()
                }
            }
        }
        return stored
    }

    private suspend fun versions(code: DomainPackCode): List<StoredDomainPack> = DatabaseFactory.dbQuery {
        DomainPacksTable.selectAll().where { DomainPacksTable.code eq code.value }.map(::toStored)
    }

    private fun toStored(row: ResultRow) = StoredDomainPack(
        pack = DomainPackCodec.decode(row[DomainPacksTable.definition]),
        version = row[DomainPacksTable.version],
        status = DomainPackStatus.valueOf(row[DomainPacksTable.status]),
        ownerTenantId = row[DomainPacksTable.ownerTenantId]?.let(::TenantId)
    )
}
