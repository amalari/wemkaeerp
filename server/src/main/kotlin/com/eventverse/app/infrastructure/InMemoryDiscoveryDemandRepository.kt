package com.eventverse.app.infrastructure

import com.eventverse.app.domain.discovery.DiscoveryDemand
import com.eventverse.app.domain.discovery.DiscoveryDemandRepository

/** Untuk test API; demand catatan historis — insert saja, tanpa update/hapus. */
class InMemoryDiscoveryDemandRepository : DiscoveryDemandRepository {

    private val rows = mutableListOf<DiscoveryDemand>()

    override suspend fun save(demand: DiscoveryDemand): DiscoveryDemand {
        rows.add(demand)
        return demand
    }

    override suspend fun findAll(): List<DiscoveryDemand> = rows.reversed()

    fun clear() = rows.clear()
}
