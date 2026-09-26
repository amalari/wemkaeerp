package com.eventverse.app.infrastructure

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.infrastructure.tables.*
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.update

class PostgresTraceContainerRepository : TraceContainerRepository {

    override suspend fun findByCode(tenantId: TenantId, code: TraceCode): TraceContainer? =
        DatabaseFactory.dbQuery(tenantId) {
            TraceContainersTable.selectAll()
                .where { (TraceContainersTable.tenantId eq tenantId.value) and (TraceContainersTable.code eq code.value) }
                .singleOrNull()
                ?.let { hydrate(it, talliesFor(listOf(it[TraceContainersTable.id]))) }
        }

    override suspend fun findByWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef): List<TraceContainer> =
        DatabaseFactory.dbQuery(tenantId) {
            val rows = TraceContainersTable.selectAll()
                .where {
                    (TraceContainersTable.tenantId eq tenantId.value) and
                        (TraceContainersTable.workOrderKind eq ref.kind.name) and
                        (TraceContainersTable.workOrderId eq ref.id)
                }
                .toList()
            val tallies = talliesFor(rows.map { it[TraceContainersTable.id] })
            rows.map { hydrate(it, tallies) }
        }

    override suspend fun findByIds(tenantId: TenantId, ids: List<TraceContainerId>): List<TraceContainer> {
        if (ids.isEmpty()) return emptyList()
        return DatabaseFactory.dbQuery(tenantId) {
            val raw = ids.map { it.value }
            val rows = TraceContainersTable.selectAll()
                .where { (TraceContainersTable.tenantId eq tenantId.value) and (TraceContainersTable.id inList raw) }
                .toList()
            val tallies = talliesFor(rows.map { it[TraceContainersTable.id] })
            rows.map { hydrate(it, tallies) }
        }
    }

    /**
     * Sisipkan-kalau-belum-ada, lalu selalu baca yang berlaku.
     *
     * `ON CONFLICT DO NOTHING` di atas `UNIQUE(tenant_id, code)` inilah yang membuat scan ganda aman.
     * Memeriksa dulu lalu menyisipkan tidak cukup: dua tab yang dibuka operator yang sama bisa lolos
     * pemeriksaan bersamaan, dan yang kedua akan meledak dengan galat basis data alih-alih memberi
     * jawaban yang sama.
     */
    override suspend fun openIfAbsent(container: TraceContainer): TraceContainer =
        DatabaseFactory.dbQuery(container.tenantId) {
            TraceContainersTable.insertIgnore {
                it[id] = container.id.value
                it.applyFrom(container)
                it[createdAt] = container.createdAt
            }
            container.panelTallies.forEach { tally ->
                TraceContainerPanelTalliesTable.insertIgnore {
                    it[containerId] = container.id.value
                    it[tenantId] = container.tenantId.value
                    it[panel] = tally.panel.name
                    it[pieces] = tally.pieces
                }
            }

            TraceContainersTable.selectAll()
                .where {
                    (TraceContainersTable.tenantId eq container.tenantId.value) and
                        (TraceContainersTable.code eq container.code.value)
                }
                .single()
                .let { hydrate(it, talliesFor(listOf(it[TraceContainersTable.id]))) }
        }

    override suspend fun save(container: TraceContainer): TraceContainer =
        DatabaseFactory.dbQuery(container.tenantId) {
            writeContainer(container)
            container
        }

    override suspend fun linksForWorkOrder(tenantId: TenantId, ref: TraceWorkOrderRef): List<TraceContainerLink> =
        DatabaseFactory.dbQuery(tenantId) {
            val containerIds = TraceContainersTable.selectAll()
                .where {
                    (TraceContainersTable.tenantId eq tenantId.value) and
                        (TraceContainersTable.workOrderKind eq ref.kind.name) and
                        (TraceContainersTable.workOrderId eq ref.id)
                }
                .map { it[TraceContainersTable.id] }
            if (containerIds.isEmpty()) return@dbQuery emptyList()

            TraceContainerLinksTable.selectAll()
                .where {
                    (TraceContainerLinksTable.tenantId eq tenantId.value) and
                        (TraceContainerLinksTable.childId inList containerIds)
                }
                .map(::hydrateLink)
        }

    override suspend fun linksForParent(tenantId: TenantId, parentId: TraceContainerId): List<TraceContainerLink> =
        DatabaseFactory.dbQuery(tenantId) {
            TraceContainerLinksTable.selectAll()
                .where {
                    (TraceContainerLinksTable.tenantId eq tenantId.value) and
                        (TraceContainerLinksTable.parentId eq parentId.value)
                }
                .map(::hydrateLink)
        }

    /** Silsilah dan status bundel ditulis bersama — silsilah tanpa status adalah kebohongan setengah jadi. */
    override suspend fun consumeIntoSack(
        sack: TraceContainer,
        links: List<TraceContainerLink>,
        bundles: List<TraceContainer>
    ): Unit = DatabaseFactory.dbQuery(sack.tenantId) {
        writeContainer(sack)
        bundles.forEach { writeContainer(it) }
        links.forEach { link ->
            TraceContainerLinksTable.insert {
                it[parentId] = link.parentId.value
                it[childId] = link.childId.value
                it[tenantId] = link.tenantId.value
                it[consumedPcs] = link.consumedPcs
                it[linkedAt] = link.linkedAt
            }
        }
    }

    /**
     * Ordinal SPK, dibuat sekali lalu stabil selamanya.
     *
     * `MAX(ordinal) + 1` di dalam satu pernyataan INSERT, bukan `COUNT(*) + 1` yang dibaca lebih dulu
     * seperti `nextSpkNumber`: menghitung baris lalu menyisipkan di pernyataan terpisah memberi celah
     * bagi dua permintaan bersamaan untuk memperoleh angka yang sama, dan `COUNT` juga menyusut saat
     * ada baris terhapus sehingga ordinal bisa terpakai ulang — dan kode telusur lama menunjuk SPK
     * yang salah.
     */
    override suspend fun ensureWorkOrderOrdinal(tenantId: TenantId, ref: TraceWorkOrderRef): Int =
        DatabaseFactory.dbQuery(tenantId) {
            existingOrdinal(tenantId, ref)?.let { return@dbQuery it }

            val column = if (ref.kind == TraceWorkOrderKind.SAMPLING) "sampling_order_id" else "bulk_work_order_id"
            // `id` kolomnya VARCHAR(64); id SPK sendiri sudah bisa 64 karakter, jadi awalannya harus
            // pendek dan ekornya dipangkas dari depan — bagian belakang id-lah yang membedakannya.
            val rowId = "tw${ref.kind.symbol}_${ref.id}".takeLast(64).esc()
            val sql = """
                INSERT INTO trace_work_orders (id, tenant_id, ordinal, work_order_kind, $column, created_at)
                SELECT '$rowId', '${tenantId.value}',
                       COALESCE(MAX(ordinal), 0) + 1, '${ref.kind.name}', '${ref.id.esc()}', NOW()
                FROM trace_work_orders WHERE tenant_id = '${tenantId.value}'
                ON CONFLICT DO NOTHING
            """.trimIndent()
            TransactionManager.current().exec(sql)

            existingOrdinal(tenantId, ref) ?: error("Gagal mengalokasikan ordinal SPK untuk ${ref.id}")
        }

    override suspend fun findWorkOrderByOrdinal(
        tenantId: TenantId,
        ordinal: Int,
        kind: TraceWorkOrderKind
    ): TraceWorkOrderRef? = DatabaseFactory.dbQuery(tenantId) {
        TraceWorkOrdersTable.selectAll()
            .where {
                (TraceWorkOrdersTable.tenantId eq tenantId.value) and
                    (TraceWorkOrdersTable.ordinal eq ordinal) and
                    (TraceWorkOrdersTable.workOrderKind eq kind.name)
            }
            .singleOrNull()
            ?.let { row ->
                val id = if (kind == TraceWorkOrderKind.SAMPLING) {
                    row[TraceWorkOrdersTable.samplingOrderId]
                } else {
                    row[TraceWorkOrdersTable.bulkWorkOrderId]
                }
                id?.takeIf { it.isNotBlank() }?.let { TraceWorkOrderRef(kind, it) }
            }
    }

    /**
     * Ordinal tenant — angka yang ikut terkode di setiap kartu supaya kartu milik pabrik lain ditolak.
     *
     * `trace_tenant_ordinals` adalah **satu-satunya** tabel telusur yang sengaja TIDAK memakai Row
     * Level Security, dan itu bukan kelalaian: `MAX(ordinal) + 1` harus melihat baris seluruh tenant
     * untuk menghasilkan angka yang unik secara global. Dipasangi RLS, setiap tenant hanya akan
     * melihat barisnya sendiri, `MAX` selalu mengembalikan nol, dan semua tenant memperoleh ordinal 1
     * — yang artinya kode kartu antar pabrik mulai bertabrakan dan justru saling resolve.
     *
     * Yang bocor kalau tabel ini terbaca lintas tenant hanyalah pencacah; tidak ada data produksi
     * di dalamnya, dan pembacaannya tetap disaring `tenant_id`.
     */
    override suspend fun tenantOrdinal(tenantId: TenantId): Int = DatabaseFactory.dbQuery(tenantId) {
        readTenantOrdinal(tenantId)?.let { return@dbQuery it }

        val sql = """
            INSERT INTO trace_tenant_ordinals (tenant_id, ordinal, created_at)
            SELECT '${tenantId.value}', COALESCE(MAX(ordinal), 0) + 1, NOW() FROM trace_tenant_ordinals
            ON CONFLICT (tenant_id) DO NOTHING
        """.trimIndent()
        TransactionManager.current().exec(sql)

        readTenantOrdinal(tenantId) ?: error("Gagal mengalokasikan ordinal tenant")
    }

    // ── Bantuan ─────────────────────────────────────────────────────────────────────────────

    private fun readTenantOrdinal(tenantId: TenantId): Int? =
        TraceTenantOrdinalsTable.selectAll()
            .where { TraceTenantOrdinalsTable.tenantId eq tenantId.value }
            .singleOrNull()
            ?.get(TraceTenantOrdinalsTable.ordinal)

    private fun existingOrdinal(tenantId: TenantId, ref: TraceWorkOrderRef): Int? =
        TraceWorkOrdersTable.selectAll()
            .where {
                val base = (TraceWorkOrdersTable.tenantId eq tenantId.value) and
                    (TraceWorkOrdersTable.workOrderKind eq ref.kind.name)
                if (ref.kind == TraceWorkOrderKind.SAMPLING) {
                    base and (TraceWorkOrdersTable.samplingOrderId eq ref.id)
                } else {
                    base and (TraceWorkOrdersTable.bulkWorkOrderId eq ref.id)
                }
            }
            .singleOrNull()
            ?.get(TraceWorkOrdersTable.ordinal)

    private fun writeContainer(container: TraceContainer) {
        val exists = TraceContainersTable.selectAll()
            .where { TraceContainersTable.id eq container.id.value }
            .empty()
            .not()

        if (exists) {
            TraceContainersTable.update({ TraceContainersTable.id eq container.id.value }) { it.applyFrom(container) }
        } else {
            TraceContainersTable.insert {
                it[id] = container.id.value
                it.applyFrom(container)
                it[createdAt] = container.createdAt
            }
        }

        TraceContainerPanelTalliesTable.deleteWhere {
            TraceContainerPanelTalliesTable.containerId eq container.id.value
        }
        container.panelTallies.forEach { tally ->
            TraceContainerPanelTalliesTable.insert {
                it[containerId] = container.id.value
                it[tenantId] = container.tenantId.value
                it[panel] = tally.panel.name
                it[pieces] = tally.pieces
            }
        }
    }

    private fun <T> T.applyFrom(container: TraceContainer) where T : org.jetbrains.exposed.sql.statements.UpdateBuilder<*> {
        this[TraceContainersTable.tenantId] = container.tenantId.value
        this[TraceContainersTable.code] = container.code.value
        this[TraceContainersTable.workOrderKind] = container.workOrder.kind.name
        this[TraceContainersTable.workOrderId] = container.workOrder.id
        this[TraceContainersTable.tier] = container.tier.name
        this[TraceContainersTable.sizeLabel] = container.sizeLabel
        this[TraceContainersTable.colorway] = container.colorway
        this[TraceContainersTable.state] = container.state.name
        this[TraceContainersTable.declaredPcs] = container.declaredPcs
        this[TraceContainersTable.weightKg] = container.weightKg
        this[TraceContainersTable.operatorName] = container.operatorName
        this[TraceContainersTable.shiftLabel] = container.shift.value
        this[TraceContainersTable.recordedAt] = container.recordedAt
        this[TraceContainersTable.updatedAt] = container.updatedAt
        this[TraceContainersTable.notes] = container.notes
    }

    private fun talliesFor(containerIds: List<String>): Map<String, List<PanelTally>> {
        if (containerIds.isEmpty()) return emptyMap()
        return TraceContainerPanelTalliesTable.selectAll()
            .where { TraceContainerPanelTalliesTable.containerId inList containerIds }
            .mapNotNull { row ->
                val panel = GarmentPanel.entries
                    .firstOrNull { it.name == row[TraceContainerPanelTalliesTable.panel] }
                    ?: return@mapNotNull null
                row[TraceContainerPanelTalliesTable.containerId] to
                    PanelTally(panel, row[TraceContainerPanelTalliesTable.pieces])
            }
            .groupBy({ it.first }, { it.second })
    }

    private fun hydrate(row: ResultRow, tallies: Map<String, List<PanelTally>>): TraceContainer {
        val id = row[TraceContainersTable.id]
        return TraceContainer(
            id = TraceContainerId(id),
            tenantId = TenantId(row[TraceContainersTable.tenantId]),
            code = TraceCode(row[TraceContainersTable.code]),
            workOrder = TraceWorkOrderRef(
                kind = TraceWorkOrderKind.entries
                    .firstOrNull { it.name == row[TraceContainersTable.workOrderKind] }
                    ?: TraceWorkOrderKind.SAMPLING,
                id = row[TraceContainersTable.workOrderId]
            ),
            tier = TraceTier.entries.firstOrNull { it.name == row[TraceContainersTable.tier] } ?: TraceTier.BUNDLE,
            sizeLabel = row[TraceContainersTable.sizeLabel],
            colorway = row[TraceContainersTable.colorway],
            state = TraceContainerState.entries
                .firstOrNull { it.name == row[TraceContainersTable.state] }
                ?: TraceContainerState.OPENED,
            panelTallies = tallies[id].orEmpty(),
            declaredPcs = row[TraceContainersTable.declaredPcs],
            weightKg = row[TraceContainersTable.weightKg],
            operatorName = row[TraceContainersTable.operatorName],
            shift = ShiftLabel(row[TraceContainersTable.shiftLabel]),
            recordedAt = row[TraceContainersTable.recordedAt],
            createdAt = row[TraceContainersTable.createdAt],
            updatedAt = row[TraceContainersTable.updatedAt],
            notes = row[TraceContainersTable.notes]
        )
    }

    private fun hydrateLink(row: ResultRow) = TraceContainerLink(
        tenantId = TenantId(row[TraceContainerLinksTable.tenantId]),
        parentId = TraceContainerId(row[TraceContainerLinksTable.parentId]),
        childId = TraceContainerId(row[TraceContainerLinksTable.childId]),
        consumedPcs = row[TraceContainerLinksTable.consumedPcs],
        linkedAt = row[TraceContainerLinksTable.linkedAt]
    )

    /** Kode dan label datang dari kartu dan jari operator; keduanya masuk ke SQL literal. */
    private fun String.esc(): String = replace("'", "''")
}
