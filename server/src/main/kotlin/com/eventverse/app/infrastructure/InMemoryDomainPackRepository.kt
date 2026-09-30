package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.effectiveOf

class InMemoryDomainPackRepository : DomainPackRepository {

    private val rows = mutableMapOf<Pair<DomainPackCode, Int>, StoredDomainPack>()

    override suspend fun findEffective(code: DomainPackCode): StoredDomainPack? = effectiveOf(rows.values.filter { it.pack.code == code })

    override suspend fun findAllEffective(): List<StoredDomainPack> = rows.values.groupBy { it.pack.code }.values.mapNotNull(::effectiveOf)

    override suspend fun findLatest(code: DomainPackCode): StoredDomainPack? =
        rows.values.filter { it.pack.code == code }.maxByOrNull { it.version }

    override suspend fun findVersion(code: DomainPackCode, version: Int): StoredDomainPack? =
        rows[code to version]

    override suspend fun save(stored: StoredDomainPack): StoredDomainPack {
        rows[stored.pack.code to stored.version] = stored
        return stored
    }
}
