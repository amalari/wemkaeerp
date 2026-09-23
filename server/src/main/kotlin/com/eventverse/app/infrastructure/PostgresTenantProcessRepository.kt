package com.eventverse.app.infrastructure

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.process.TenantProcessCatalog
import com.eventverse.app.domain.process.TenantProcessCatalogRepository
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.infrastructure.tables.TenantOptionalProcessesTable
import kotlinx.datetime.Clock
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Implementasi PostgreSQL katalog proses opsional per tenant (tabel V56).
 * `save` bersifat sinkronisasi penuh: baris yang tidak ada lagi di agregat dihapus,
 * baris yang ada di-upsert — karena [TenantProcessCatalog] adalah sumber kebenaran.
 */
class PostgresTenantProcessRepository : TenantProcessCatalogRepository {

    override suspend fun findByTenantId(tenantId: TenantId): TenantProcessCatalog? =
        DatabaseFactory.dbQuery(tenantId) {
            val rows = TenantOptionalProcessesTable.selectAll()
                .where { TenantOptionalProcessesTable.tenantId eq tenantId.value }
                .toList()
            TenantProcessCatalog(
                tenantId = tenantId,
                processes = rows.map(::hydrate)
            )
        }

    override suspend fun save(catalog: TenantProcessCatalog): Result<TenantProcessCatalog> = runCatching {
        DatabaseFactory.dbQuery(catalog.tenantId) {
            val now = Clock.System.now()

            // Hapus baris yang sudah dikeluarkan dari agregat (hasil removeProcess)
            val keepIds = catalog.processes.map { it.processId }.toSet()
            val staleIds = TenantOptionalProcessesTable.selectAll()
                .where { TenantOptionalProcessesTable.tenantId eq catalog.tenantId.value }
                .toList()
                .map { it[TenantOptionalProcessesTable.id] }
                .filterNot { it in keepIds }
            if (staleIds.isNotEmpty()) {
                TenantOptionalProcessesTable.deleteWhere {
                    SqlExpressionBuilder.run { id inList staleIds }
                }
            }

            catalog.processes.forEach { process ->
                val existing = TenantOptionalProcessesTable.selectAll()
                    .where { TenantOptionalProcessesTable.id eq process.processId }
                    .singleOrNull()

                if (existing == null) {
                    TenantOptionalProcessesTable.insert {
                        it[id] = process.processId
                        it[tenantId] = process.tenantId.value
                        it[processCode] = process.code
                        it[displayName] = process.displayName
                        it[archetype] = process.archetype.name
                        it[samplingAnchorAfter] = process.samplingAnchorAfter?.name
                        it[stationAnchorAfter] = process.stationAnchorAfter?.value
                        it[executionMode] = process.executionMode.name
                        it[vendorRef] = process.vendorRef
                        it[piecerateTariffIdr] = process.piecerateTariffIdr
                        it[standardMinutesPerPiece] = process.standardMinutesPerPiece
                        it[createdAt] = now
                        it[updatedAt] = now
                    }
                } else {
                    TenantOptionalProcessesTable.update({ TenantOptionalProcessesTable.id eq process.processId }) {
                        it[displayName] = process.displayName
                        it[archetype] = process.archetype.name
                        it[samplingAnchorAfter] = process.samplingAnchorAfter?.name
                        it[stationAnchorAfter] = process.stationAnchorAfter?.value
                        it[executionMode] = process.executionMode.name
                        it[vendorRef] = process.vendorRef
                        it[piecerateTariffIdr] = process.piecerateTariffIdr
                        it[standardMinutesPerPiece] = process.standardMinutesPerPiece
                        it[updatedAt] = now
                    }
                }
            }
        }
        catalog
    }

    override suspend fun deleteProcess(tenantId: TenantId, processId: String): Result<Unit> = runCatching {
        DatabaseFactory.dbQuery(tenantId) {
            // Verifikasi kepemilikan tenant sebelum hapus (proteksi lintas-tenant)
            val row = TenantOptionalProcessesTable.selectAll()
                .where { TenantOptionalProcessesTable.id eq processId }
                .singleOrNull()
            if (row != null && row[TenantOptionalProcessesTable.tenantId] == tenantId.value) {
                TenantOptionalProcessesTable.deleteWhere {
                    SqlExpressionBuilder.run { id eq processId }
                }
            }
        }
    }

    private fun hydrate(row: ResultRow): TenantOptionalProcess =
        TenantOptionalProcess(
            processId = row[TenantOptionalProcessesTable.id],
            tenantId = TenantId(row[TenantOptionalProcessesTable.tenantId]),
            code = row[TenantOptionalProcessesTable.processCode],
            displayName = row[TenantOptionalProcessesTable.displayName],
            archetype = ModuleArchetype.valueOf(row[TenantOptionalProcessesTable.archetype]),
            samplingAnchorAfter = row[TenantOptionalProcessesTable.samplingAnchorAfter]
                ?.let { SamplingPipelineStage.parseOrNull(it) },
            stationAnchorAfter = row[TenantOptionalProcessesTable.stationAnchorAfter]
                ?.takeIf { it.isNotBlank() }
                ?.let(::WorkStationCode),
            executionMode = runCatching {
                WorkExecutionMode.valueOf(row[TenantOptionalProcessesTable.executionMode])
            }.getOrDefault(WorkExecutionMode.IN_HOUSE),
            vendorRef = row[TenantOptionalProcessesTable.vendorRef],
            piecerateTariffIdr = row[TenantOptionalProcessesTable.piecerateTariffIdr],
            standardMinutesPerPiece = row[TenantOptionalProcessesTable.standardMinutesPerPiece]
        )
}