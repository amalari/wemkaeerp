package com.eventverse.app.infrastructure

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.discovery.DiscoveryDemand
import com.eventverse.app.domain.discovery.DiscoveryDemandRepository
import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.infrastructure.tables.DiscoveryDemandsTable
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

/**
 * Buku demand (tabel V80). Tulis-saja dari sisi route: `save` hanya insert — demand adalah
 * catatan historis, merevisi narasi prospek berarti memalsukan sinyal produk.
 */
class PostgresDiscoveryDemandRepository : DiscoveryDemandRepository {

    override suspend fun save(demand: DiscoveryDemand): DiscoveryDemand {
        DatabaseFactory.dbQuery {
            DiscoveryDemandsTable.insert {
                it[id] = demand.id
                it[draftId] = demand.draftId.value
                it[ownerUserId] = demand.ownerUserId.value
                it[narrative] = demand.narrative
                it[industryHint] = demand.industryHint
                it[agentRef] = demand.agentRef
                it[matchedModules] = jsonArrayOf(demand.matchedModuleIds.map(::jsonOf)).encode()
                it[unmatchedTerms] = jsonArrayOf(demand.unmatchedTerms.map(::jsonOf)).encode()
                it[createdAt] = demand.createdAt ?: Clock.System.now()
            }
        }
        return demand
    }

    override suspend fun findAll(): List<DiscoveryDemand> = DatabaseFactory.dbQuery {
        DiscoveryDemandsTable.selectAll()
            .orderBy(DiscoveryDemandsTable.createdAt, order = org.jetbrains.exposed.sql.SortOrder.DESC)
            .map(::toDemand)
    }

    private fun toDemand(row: ResultRow) = DiscoveryDemand(
        id = row[DiscoveryDemandsTable.id],
        draftId = DiscoveryDraftId(row[DiscoveryDemandsTable.draftId]),
        ownerUserId = UserId(row[DiscoveryDemandsTable.ownerUserId]),
        narrative = row[DiscoveryDemandsTable.narrative],
        industryHint = row[DiscoveryDemandsTable.industryHint],
        agentRef = row[DiscoveryDemandsTable.agentRef],
        matchedModuleIds = stringList(row[DiscoveryDemandsTable.matchedModules]),
        unmatchedTerms = stringList(row[DiscoveryDemandsTable.unmatchedTerms]),
        createdAt = row[DiscoveryDemandsTable.createdAt]
    )

    /** Kolom JSONB berupa array string; bentuk lain melempar (Kontrak 4 — tanpa fallback senyap). */
    private fun stringList(json: String): List<String> =
        (JsonParser.parse(json) as? JsonValue.Arr)?.items?.filterIsInstance<JsonValue.Str>()?.map { it.value }
            ?: error("Kolom JSONB demand bukan array string: $json")
}
