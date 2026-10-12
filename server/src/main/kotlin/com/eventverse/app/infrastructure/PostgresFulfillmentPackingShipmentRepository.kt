package com.eventverse.app.infrastructure

import com.eventverse.app.domain.fulfillment.shipment.*
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * Repository Postgres untuk pengiriman (shipment).
 *
 * Mengimplementasikan [ShipmentRepository] dengan RLS per tenant.
 * Idempoten: save menangani INSERT dan UPDATE.
 */
class PostgresFulfillmentPackingShipmentRepository : ShipmentRepository {

    // -------------------------------------------------------------------------
    // ShipmentRepository implementation
    // -------------------------------------------------------------------------

    override suspend fun findById(tenantId: TenantId, id: ShipmentId): DispatchShipment? =
        DatabaseFactory.dbQuery(tenantId) {
            val shipmentRow = ShipmentsTable.selectAll()
                .where { ShipmentsTable.id eq id.value }
                .singleOrNull() ?: return@dbQuery null

            val cartonIds = ShipmentCartonsTable.selectAll()
                .where { ShipmentCartonsTable.shipmentId eq id.value }
                .map { it[ShipmentCartonsTable.cartonId] }

            shipmentRow.toDispatchShipment(cartonIds)
        }

    override suspend fun findAll(tenantId: TenantId): List<DispatchShipment> =
        DatabaseFactory.dbQuery(tenantId) {
            ShipmentsTable.selectAll()
                .where { ShipmentsTable.tenantId eq tenantId.value }
                .orderBy(ShipmentsTable.createdAt, SortOrder.DESC)
                .map { row ->
                    val cartonIds = ShipmentCartonsTable.selectAll()
                        .where { ShipmentCartonsTable.shipmentId eq row[ShipmentsTable.id] }
                        .map { it[ShipmentCartonsTable.cartonId] }
                    row.toDispatchShipment(cartonIds)
                }
        }

    override suspend fun findByStatus(tenantId: TenantId, status: ShipmentStatus): List<DispatchShipment> =
        DatabaseFactory.dbQuery(tenantId) {
            ShipmentsTable.selectAll()
                .where { 
                    (ShipmentsTable.tenantId eq tenantId.value) and 
                    (ShipmentsTable.status eq status.name) 
                }
                .orderBy(ShipmentsTable.updatedAt, SortOrder.DESC)
                .map { row ->
                    val cartonIds = ShipmentCartonsTable.selectAll()
                        .where { ShipmentCartonsTable.shipmentId eq row[ShipmentsTable.id] }
                        .map { it[ShipmentCartonsTable.cartonId] }
                    row.toDispatchShipment(cartonIds)
                }
        }

    override suspend fun findByNumber(tenantId: TenantId, number: ShipmentNumber): DispatchShipment? =
        DatabaseFactory.dbQuery(tenantId) {
            val shipmentRow = ShipmentsTable.selectAll()
                .where { 
                    (ShipmentsTable.tenantId eq tenantId.value) and 
                    (ShipmentsTable.shipmentNumber eq number.value) 
                }
                .singleOrNull() ?: return@dbQuery null

            val cartonIds = ShipmentCartonsTable.selectAll()
                .where { ShipmentCartonsTable.shipmentId eq shipmentRow[ShipmentsTable.id] }
                .map { it[ShipmentCartonsTable.cartonId] }

            shipmentRow.toDispatchShipment(cartonIds)
        }

    override suspend fun findActiveShipments(tenantId: TenantId): List<DispatchShipment> =
        DatabaseFactory.dbQuery(tenantId) {
            ShipmentsTable.selectAll()
                .where { 
                    (ShipmentsTable.tenantId eq tenantId.value) and 
                    (ShipmentsTable.status inList listOf(ShipmentStatus.ISSUED.name, ShipmentStatus.IN_TRANSIT.name)) 
                }
                .orderBy(ShipmentsTable.updatedAt, SortOrder.DESC)
                .map { row ->
                    val cartonIds = ShipmentCartonsTable.selectAll()
                        .where { ShipmentCartonsTable.shipmentId eq row[ShipmentsTable.id] }
                        .map { it[ShipmentCartonsTable.cartonId] }
                    row.toDispatchShipment(cartonIds)
                }
        }

    override suspend fun findByCartonId(tenantId: TenantId, cartonId: CartonId): List<DispatchShipment> =
        DatabaseFactory.dbQuery(tenantId) {
            val shipmentIds = ShipmentCartonsTable.selectAll()
                .where { ShipmentCartonsTable.cartonId eq cartonId.value }
                .map { it[ShipmentCartonsTable.shipmentId] }

            if (shipmentIds.isEmpty()) return@dbQuery emptyList()

            ShipmentsTable.selectAll()
                .where { ShipmentsTable.id inList shipmentIds }
                .map { row ->
                    val cartonIds = ShipmentCartonsTable.selectAll()
                        .where { ShipmentCartonsTable.shipmentId eq row[ShipmentsTable.id] }
                        .map { it[ShipmentCartonsTable.cartonId] }
                    row.toDispatchShipment(cartonIds)
                }
        }

    override suspend fun save(shipment: DispatchShipment): DispatchShipment =
        DatabaseFactory.dbQuery(shipment.tenantId) {
            val existing = ShipmentsTable.selectAll()
                .where { ShipmentsTable.id eq shipment.id.value }
                .singleOrNull()

            if (existing == null) {
                ShipmentsTable.insert {
                    it[id] = shipment.id.value
                    it[tenantId] = shipment.tenantId.value
                    it[shipmentNumber] = shipment.shipmentNumber.value
                    it[stockOwnership] = shipment.stockOwnership.name
                    it[carrier] = shipment.carrier.toJson()
                    it[destination] = shipment.destination.toJson()
                    it[status] = shipment.status.name
                    it[shippedAt] = shipment.shippedAt?.toString()
                    it[deliveredAt] = shipment.deliveredAt?.toString()
                    it[createdAt] = shipment.createdAt.toString()
                    it[updatedAt] = shipment.updatedAt.toString()
                    it[notes] = shipment.notes
                }
            } else {
                ShipmentsTable.update({ ShipmentsTable.id eq shipment.id.value }) {
                    it[shipmentNumber] = shipment.shipmentNumber.value
                    it[stockOwnership] = shipment.stockOwnership.name
                    it[carrier] = shipment.carrier.toJson()
                    it[destination] = shipment.destination.toJson()
                    it[status] = shipment.status.name
                    it[shippedAt] = shipment.shippedAt?.toString()
                    it[deliveredAt] = shipment.deliveredAt?.toString()
                    it[updatedAt] = shipment.updatedAt.toString()
                    it[notes] = shipment.notes
                }
            }
            shipment
        }

    override suspend fun recordEvent(
        tenantId: TenantId,
        shipmentId: ShipmentId,
        eventType: String,
        actor: String,
        detail: String,
        occurredAt: Instant
    ) {
        DatabaseFactory.dbQuery(tenantId) {
            ShipmentEventsTable.insert {
                it[ShipmentEventsTable.id] = java.util.UUID.randomUUID().toString()
                it[ShipmentEventsTable.tenantId] = tenantId.value
                it[ShipmentEventsTable.shipmentId] = shipmentId.value
                it[ShipmentEventsTable.eventType] = eventType
                it[ShipmentEventsTable.actor] = actor
                it[ShipmentEventsTable.detail] = detail
                it[ShipmentEventsTable.occurredAt] = occurredAt.toString()
            }
        }
    }

    override suspend fun getNextShipmentSequence(tenantId: TenantId, year: Int): Int =
        DatabaseFactory.dbQuery(tenantId) {
            val prefix = "SJ-$year-"
            val maxRow = ShipmentsTable.select(ShipmentsTable.shipmentNumber)
                .where { ShipmentsTable.tenantId eq tenantId.value }
                .orderBy(ShipmentsTable.shipmentNumber, SortOrder.DESC)
                .limit(1)
                .singleOrNull()

            val maxNumber = maxRow?.get(ShipmentsTable.shipmentNumber)
                ?.takeIf { it.startsWith(prefix) }
                ?.substringAfter(prefix)
                ?.toIntOrNull() ?: 0

            maxNumber + 1
        }

    override suspend fun linkCartons(tenantId: TenantId, shipmentId: ShipmentId, cartonIds: List<CartonId>) {
        DatabaseFactory.dbQuery(tenantId) {
            cartonIds.forEach { cartonId ->
                ShipmentCartonsTable.insert {
                    it[ShipmentCartonsTable.tenantId] = tenantId.value
                    it[ShipmentCartonsTable.shipmentId] = shipmentId.value
                    it[ShipmentCartonsTable.cartonId] = cartonId.value
                }
            }
        }
    }

    override suspend fun unlinkCartons(tenantId: TenantId, shipmentId: ShipmentId, cartonIds: List<CartonId>) {
        DatabaseFactory.dbQuery(tenantId) {
            cartonIds.forEach { cartonId ->
                ShipmentCartonsTable.deleteWhere {
                    (ShipmentCartonsTable.shipmentId eq shipmentId.value) and 
                    (ShipmentCartonsTable.cartonId eq cartonId.value)
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Extension functions for mapping
    // -------------------------------------------------------------------------

    private fun ResultRow.toDispatchShipment(cartonIds: List<String>): DispatchShipment {
        val status = ShipmentStatus.fromString(this[ShipmentsTable.status]) ?: ShipmentStatus.ISSUED
        return DispatchShipment(
            id = ShipmentId(this[ShipmentsTable.id]),
            tenantId = TenantId(this[ShipmentsTable.tenantId]),
            shipmentNumber = ShipmentNumber(this[ShipmentsTable.shipmentNumber]),
            stockOwnership = parseStockOwnership(this[ShipmentsTable.stockOwnership]),
            carrier = parseCarrierInfo(this[ShipmentsTable.carrier]),
            destination = parseDestinationInfo(this[ShipmentsTable.destination]),
            status = status,
            shippedAt = this[ShipmentsTable.shippedAt]?.let { Instant.parse(it) },
            deliveredAt = this[ShipmentsTable.deliveredAt]?.let { Instant.parse(it) },
            createdAt = Instant.parse(this[ShipmentsTable.createdAt]),
            updatedAt = Instant.parse(this[ShipmentsTable.updatedAt]),
            notes = this[ShipmentsTable.notes]
        )
    }

    private fun CarrierInfo.toJson(): String = buildString {
        append("{")
        var first = true
        if (mode.isNotBlank()) { append("\"mode\":\"$mode\""); first = false }
        if (expedition.isNotBlank()) { if (!first) append(","); append("\"expedition\":\"$expedition\""); first = false }
        if (container.isNotBlank()) { if (!first) append(","); append("\"container\":\"$container\""); first = false }
        if (driver.isNotBlank()) { if (!first) append(","); append("\"driver\":\"$driver\""); first = false }
        if (trackingNumber.isNotBlank()) { if (!first) append(","); append("\"trackingNumber\":\"$trackingNumber\"") }
        append("}")
    }

    private fun DestinationInfo.toJson(): String = buildString {
        append("{")
        var first = true
        if (buyer.isNotBlank()) { append("\"buyer\":\"$buyer\""); first = false }
        if (address.isNotBlank()) { if (!first) append(","); append("\"address\":\"$address\""); first = false }
        if (contact.isNotBlank()) { if (!first) append(","); append("\"contact\":\"$contact\""); first = false }
        if (city.isNotBlank()) { if (!first) append(","); append("\"city\":\"$city\"") }
        append("}")
    }

    private fun parseCarrierInfo(json: String): CarrierInfo = try {
        CarrierInfo(
            mode = json.extract("mode"),
            expedition = json.extract("expedition"),
            container = json.extract("container"),
            driver = json.extract("driver"),
            trackingNumber = json.extract("trackingNumber")
        )
    } catch (e: Exception) {
        CarrierInfo()
    }

    private fun parseDestinationInfo(json: String): DestinationInfo = try {
        DestinationInfo(
            buyer = json.extract("buyer"),
            address = json.extract("address"),
            contact = json.extract("contact"),
            city = json.extract("city")
        )
    } catch (e: Exception) {
        DestinationInfo()
    }

    private fun String.extract(key: String): String {
        val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
        return pattern.find(this)?.groupValues?.getOrNull(1) ?: ""
    }

    private fun parseStockOwnership(value: String): StockOwnership = try {
        StockOwnership.valueOf(value)
    } catch (e: Exception) {
        StockOwnership.OWNED
    }

    // -------------------------------------------------------------------------
    // Table definitions
    // -------------------------------------------------------------------------

    object ShipmentsTable : Table("fulfillment.shipments") {
        val id = varchar("id", 64)
        val tenantId = varchar("tenant_id", 64)
        val shipmentNumber = varchar("shipment_number", 40)
        val stockOwnership = varchar("stock_ownership", 30)
        val carrier = text("carrier")
        val destination = text("destination")
        val status = varchar("status", 20)
        val shippedAt = varchar("shipped_at", 64).nullable()
        val deliveredAt = varchar("delivered_at", 64).nullable()
        val createdAt = varchar("created_at", 64)
        val updatedAt = varchar("updated_at", 64)
        val notes = text("notes")

        override val primaryKey = PrimaryKey(id)
    }

    object ShipmentEventsTable : Table("fulfillment.shipment_events") {
        val id = varchar("id", 64)
        val tenantId = varchar("tenant_id", 64)
        val shipmentId = varchar("shipment_id", 64)
        val eventType = varchar("event_type", 40)
        val actor = varchar("actor", 120)
        val detail = text("detail")
        val occurredAt = varchar("occurred_at", 64)

        override val primaryKey = PrimaryKey(id)
    }

    object ShipmentCartonsTable : Table("fulfillment.shipment_cartons") {
        val tenantId = varchar("tenant_id", 64)
        val shipmentId = varchar("shipment_id", 64)
        val cartonId = varchar("carton_id", 64)

        override val primaryKey = PrimaryKey(shipmentId, cartonId)
    }
}
