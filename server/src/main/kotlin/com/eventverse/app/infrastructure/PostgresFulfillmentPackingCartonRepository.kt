package com.eventverse.app.infrastructure

import com.eventverse.app.domain.fulfillment.carton.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

/**
 * Repository Postgres untuk packing karton.
 *
 * Mengimplementasikan [CartonRepository] dengan RLS per tenant.
 * Idempoten: save menangani INSERT dan UPDATE.
 */
class PostgresFulfillmentPackingCartonRepository : CartonRepository {

    // -------------------------------------------------------------------------
    // CartonRepository implementation
    // -------------------------------------------------------------------------

    override suspend fun findById(tenantId: TenantId, id: CartonId): PackingCarton? =
        DatabaseFactory.dbQuery(tenantId) {
            CartonsTable.selectAll()
                .where { CartonsTable.id eq id.value }
                .singleOrNull()
                ?.toPackingCarton()
        }

    override suspend fun findAll(tenantId: TenantId): List<PackingCarton> =
        DatabaseFactory.dbQuery(tenantId) {
            CartonsTable.selectAll()
                .where { CartonsTable.tenantId eq tenantId.value }
                .orderBy(CartonsTable.createdAt, SortOrder.DESC)
                .map { it.toPackingCarton() }
        }

    override suspend fun findByStatus(tenantId: TenantId, status: CartonStatus): List<PackingCarton> =
        DatabaseFactory.dbQuery(tenantId) {
            CartonsTable.selectAll()
                .where { 
                    (CartonsTable.tenantId eq tenantId.value) and 
                    (CartonsTable.status eq status.name) 
                }
                .orderBy(CartonsTable.updatedAt, SortOrder.DESC)
                .map { it.toPackingCarton() }
        }

    override suspend fun findByNumber(tenantId: TenantId, number: CartonNumber): PackingCarton? =
        DatabaseFactory.dbQuery(tenantId) {
            CartonsTable.selectAll()
                .where { 
                    (CartonsTable.tenantId eq tenantId.value) and 
                    (CartonsTable.cartonNumber eq number.value) 
                }
                .singleOrNull()
                ?.toPackingCarton()
        }

    override suspend fun findOpenCartons(tenantId: TenantId): List<PackingCarton> =
        findByStatus(tenantId, CartonStatus.OPEN)

    override suspend fun save(carton: PackingCarton): PackingCarton =
        DatabaseFactory.dbQuery(carton.tenantId) {
            val existing = CartonsTable.selectAll()
                .where { CartonsTable.id eq carton.id.value }
                .singleOrNull()

            if (existing == null) {
                CartonsTable.insert {
                    it[id] = carton.id.value
                    it[tenantId] = carton.tenantId.value
                    it[productionRef] = carton.productionRef
                    it[cartonNumber] = carton.cartonNumber.value
                    it[itemBreakdown] = carton.itemBreakdown.toString()
                    it[quantityPcs] = carton.quantityPcs
                    it[grossWeightKg] = carton.grossWeightKg.toString()
                    it[unitTicketIds] = carton.unitTicketIds.toString()
                    it[sealNumber] = carton.sealNumber?.value
                    it[status] = carton.status.name
                    it[createdAt] = carton.createdAt.toString()
                    it[updatedAt] = carton.updatedAt.toString()
                    it[notes] = carton.notes
                }
            } else {
                CartonsTable.update({ CartonsTable.id eq carton.id.value }) {
                    it[productionRef] = carton.productionRef
                    it[cartonNumber] = carton.cartonNumber.value
                    it[itemBreakdown] = carton.itemBreakdown.toString()
                    it[quantityPcs] = carton.quantityPcs
                    it[grossWeightKg] = carton.grossWeightKg.toString()
                    it[unitTicketIds] = carton.unitTicketIds.toString()
                    it[sealNumber] = carton.sealNumber?.value
                    it[status] = carton.status.name
                    it[updatedAt] = carton.updatedAt.toString()
                    it[notes] = carton.notes
                }
            }
            carton
        }

    override suspend fun recordEvent(
        tenantId: TenantId,
        cartonId: CartonId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: Instant
    ) {
        DatabaseFactory.dbQuery(tenantId) {
            CartonEventsTable.insert {
                it[CartonEventsTable.id] = java.util.UUID.randomUUID().toString()
                it[CartonEventsTable.tenantId] = tenantId.value
                it[CartonEventsTable.cartonId] = cartonId.value
                it[CartonEventsTable.eventType] = eventType
                it[CartonEventsTable.actor] = actor
                it[CartonEventsTable.detail] = detail
                it[CartonEventsTable.occurredAt] = occurredAt.toString()
            }
        }
    }

    override suspend fun getPackingQueue(tenantId: TenantId): List<PackingQueueItem> =
        DatabaseFactory.dbQuery(tenantId) {
            CartonsTable.selectAll()
                .where { CartonsTable.tenantId eq tenantId.value }
                .orderBy(CartonsTable.updatedAt, SortOrder.DESC)
                .limit(100)
                .map { row ->
                    PackingQueueItem(
                        unitTicketId = row[CartonsTable.id],
                        productionRef = row[CartonsTable.productionRef],
                        sizeLabel = row[CartonsTable.itemBreakdown].let {
                            try {
                                it.removeSurrounding("{", "}")
                                    .split(",")
                                    .firstOrNull()
                                    ?.split("=")
                                    ?.getOrNull(0)
                                    ?.removeSurrounding("\"")
                                    ?: ""
                            } catch (e: Exception) {
                                ""
                            }
                        },
                        colorway = "",
                        receivedAt = Instant.parse(row[CartonsTable.createdAt])
                    )
                }
        }

    override suspend fun getNextCartonSequence(tenantId: TenantId, year: Int): Int =
        DatabaseFactory.dbQuery(tenantId) {
            val prefix = "CTN-$year-"
            val maxRow = CartonsTable.select(CartonsTable.cartonNumber)
                .where { CartonsTable.tenantId eq tenantId.value }
                .orderBy(CartonsTable.cartonNumber, SortOrder.DESC)
                .limit(1)
                .singleOrNull()

            val maxNumber = maxRow?.get(CartonsTable.cartonNumber)
                ?.takeIf { it.startsWith(prefix) }
                ?.substringAfter(prefix)
                ?.toIntOrNull() ?: 0

            maxNumber + 1
        }

    // -------------------------------------------------------------------------
    // Extension functions for mapping
    // -------------------------------------------------------------------------

    private fun ResultRow.toPackingCarton(): PackingCarton {
        val status = CartonStatus.fromString(this[CartonsTable.status]) ?: CartonStatus.OPEN
        return PackingCarton(
            id = CartonId(this[CartonsTable.id]),
            tenantId = TenantId(this[CartonsTable.tenantId]),
            productionRef = this[CartonsTable.productionRef],
            cartonNumber = CartonNumber(this[CartonsTable.cartonNumber]),
            itemBreakdown = parseJsonMap(this[CartonsTable.itemBreakdown]),
            quantityPcs = this[CartonsTable.quantityPcs],
            grossWeightKg = this[CartonsTable.grossWeightKg].toDoubleOrNull() ?: 0.0,
            unitTicketIds = parseJsonList(this[CartonsTable.unitTicketIds]),
            sealNumber = this[CartonsTable.sealNumber]?.let { SealNumber(it) },
            status = status,
            createdAt = Instant.parse(this[CartonsTable.createdAt]),
            updatedAt = Instant.parse(this[CartonsTable.updatedAt]),
            notes = this[CartonsTable.notes]
        )
    }

    private fun parseJsonMap(json: String): Map<String, Int> = try {
        json.removeSurrounding("{", "}")
            .split(",")
            .filter { it.isNotBlank() }
            .associate {
                val parts = it.split("=")
                val key = parts[0].removeSurrounding("\"").trim()
                val value = parts.getOrNull(1)?.toIntOrNull() ?: 0
                key to value
            }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun parseJsonList(json: String): List<String> = try {
        json.removeSurrounding("[", "]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }

    // -------------------------------------------------------------------------
    // Table definitions
    // -------------------------------------------------------------------------

    object CartonsTable : Table("fulfillment.cartons") {
        val id = varchar("id", 64)
        val tenantId = varchar("tenant_id", 64)
        val productionRef = varchar("production_ref", 64)
        val cartonNumber = varchar("carton_number", 40)
        val itemBreakdown = text("item_breakdown")
        val quantityPcs = integer("quantity_pcs")
        val grossWeightKg = varchar("gross_weight_kg", 20)
        val unitTicketIds = text("unit_ticket_ids")
        val sealNumber = varchar("seal_number", 40).nullable()
        val status = varchar("status", 20)
        val createdAt = varchar("created_at", 64)
        val updatedAt = varchar("updated_at", 64)
        val notes = text("notes")

        override val primaryKey = PrimaryKey(id)
    }

    object CartonEventsTable : Table("fulfillment.carton_events") {
        val id = varchar("id", 64)
        val tenantId = varchar("tenant_id", 64)
        val cartonId = varchar("carton_id", 64)
        val eventType = varchar("event_type", 40)
        val actor = varchar("actor", 120)
        val detail = text("detail")
        val occurredAt = varchar("occurred_at", 64)

        override val primaryKey = PrimaryKey(id)
    }
}
