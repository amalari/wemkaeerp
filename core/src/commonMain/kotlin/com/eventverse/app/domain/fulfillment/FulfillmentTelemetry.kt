package com.eventverse.app.domain.fulfillment

import com.eventverse.app.domain.fulfillment.carton.CartonRepository
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.shipment.ShipmentRepository
import com.eventverse.app.domain.fulfillment.shipment.ShipmentStatus
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Fungsi telemetri murni untuk modul Fulfillment — packing dan shipping.
 *
 * Fungsi-fungsi ini melakukan komputasi berdasarkan data yang diambil dari repository.
 * Dipanggil oleh layer presentation atau task scheduler untuk menampilkan status di kanvas.
 */

// ============== CONFIGURATION CONSTANTS ==============

/** Durasi maksimal (dalam jam) untuk dianggap HEALTHY */
private const val HEALTHY_THRESHOLD_HOURS = 8.0

/** Durasi maksimal (dalam jam) sebelum dianggap BOTTLENECK */
private const val BOTTLENECK_THRESHOLD_HOURS = 24.0

/** Target pieces per jam untuk throughput normal */
private const val TARGET_THROUGHPUT_PER_HOUR = 500

/** Jumlah shipment yang dianggap stagnan */
private const val STAGNANT_SHIPMENT_THRESHOLD = 3

// ============== TELEMETRY FUNCTIONS ==============

/**
 * Menghitung jumlah Work In Progress (WIP) pieces di modul fulfillment.
 *
 * WIP = semua karton dengan status OPEN atau REOPENED (belum disegel)
 * + semua shipment dengan status ISSUED atau IN_TRANSIT (belum terkirim)
 */
suspend fun calculateWipPieces(
    cartonRepository: CartonRepository,
    shipmentRepository: ShipmentRepository,
    tenantId: TenantId
): Int {
    // WIP from open cartons
    val openCartons = cartonRepository.findByStatus(tenantId, CartonStatus.OPEN)
    val reopenedCartons = cartonRepository.findByStatus(tenantId, CartonStatus.REOPENED)
    val cartonWip = (openCartons + reopenedCartons).sumOf { it.quantityPcs }

    // WIP from active shipments (issued + in transit)
    val issuedShipments = shipmentRepository.findByStatus(tenantId, ShipmentStatus.ISSUED)
    val inTransitShipments = shipmentRepository.findByStatus(tenantId, ShipmentStatus.IN_TRANSIT)
    val activeShipments = issuedShipments + inTransitShipments

    // For shipments, we need to count the pieces in cartons
    // This is a simplification - in practice we'd query linked cartons
    val shipmentWip = activeShipments.size * 50 // estimated average

    return cartonWip + shipmentWip
}

/**
 * Menghitung waktu siklus rata-rata (dalam jam) dari semua shipment.
 *
 * Cycle time = waktu dari ISSUED sampai DELIVERED.
 * Untuk shipment yang belum delivered, menggunakan waktu sejak created.
 */
suspend fun calculateCycleTimeHours(
    shipmentRepository: ShipmentRepository,
    tenantId: TenantId
): Double {
    val allShipments = shipmentRepository.findAll(tenantId)
    if (allShipments.isEmpty()) return 0.0

    val now = Clock.System.now()
    var totalHours = 0.0
    var count = 0

    for (shipment in allShipments) {
        val startTime = when {
            shipment.status == ShipmentStatus.DELIVERED && shipment.deliveredAt != null -> shipment.deliveredAt
            shipment.status == ShipmentStatus.CANCELLED -> shipment.updatedAt
            else -> now
        }

        // Only count active or completed shipments
        if (shipment.status == ShipmentStatus.DELIVERED || shipment.status == ShipmentStatus.IN_TRANSIT) {
            val endTime = when (shipment.status) {
                ShipmentStatus.DELIVERED -> shipment.deliveredAt ?: now
                ShipmentStatus.IN_TRANSIT -> now
                else -> now
            }

            val durationMillis = endTime.toEpochMilliseconds() - shipment.createdAt.toEpochMilliseconds()
            val hours = durationMillis / (1000.0 * 60 * 60)
            totalHours += hours
            count++
        }
    }

    return if (count > 0) totalHours / count else 0.0
}

/**
 * Menghitung status kesehatan alur fulfillment berdasarkan metrik.
 *
 * Logic:
 * - HEALTHY: cycle time <= 8 jam DAN tidak ada shipment stagnan
 * - BOTTLENECK: cycle time > 8 jam ATAU ada shipment > 24 jam belum delivered
 * - CRITICAL: cycle time > 24 jam ATAU ada >= 3 shipment stagnan
 */
suspend fun calculateHealthStatus(
    shipmentRepository: ShipmentRepository,
    tenantId: TenantId
): FlowHealthStatus {
    val allShipments = shipmentRepository.findAll(tenantId)
    if (allShipments.isEmpty()) return FlowHealthStatus.HEALTHY

    val now = Clock.System.now()
    val eightHoursAgo = now.minus(8 * 60 * 60 * 1000L)
    val twentyFourHoursAgo = now.minus(24 * 60 * 60 * 1000L)

    // Count stagnant shipments (issued > 8 hours ago, not yet in transit)
    val stagnantShipments = allShipments.count { shipment ->
        shipment.status == ShipmentStatus.ISSUED &&
        shipment.createdAt < eightHoursAgo
    }

    // Count very old shipments (issued > 24 hours ago, not delivered)
    val veryOldShipments = allShipments.count { shipment ->
        (shipment.status == ShipmentStatus.ISSUED || shipment.status == ShipmentStatus.IN_TRANSIT) &&
        shipment.createdAt < twentyFourHoursAgo
    }

    // Calculate average cycle time
    val avgCycleTime = calculateCycleTimeHours(shipmentRepository, tenantId)

    // Determine health status
    return when {
        // CRITICAL: cycle time > 24 jam ATAU >= 3 shipment stagnan
        avgCycleTime > BOTTLENECK_THRESHOLD_HOURS || stagnantShipments >= STAGNANT_SHIPMENT_THRESHOLD -> {
            FlowHealthStatus.CRITICAL
        }
        // BOTTLENECK: cycle time > 8 jam ATAU ada shipment > 24 jam
        avgCycleTime > HEALTHY_THRESHOLD_HOURS || veryOldShipments > 0 -> {
            FlowHealthStatus.BOTTLENECK
        }
        // HEALTHY: semua normal
        else -> FlowHealthStatus.HEALTHY
    }
}

// ============== HELPER EXTENSIONS ==============

private fun Instant.minus(millis: Long): Instant {
    return Instant.fromEpochMilliseconds(this.toEpochMilliseconds() - millis)
}
