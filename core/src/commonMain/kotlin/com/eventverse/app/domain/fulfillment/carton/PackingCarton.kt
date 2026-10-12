package com.eventverse.app.domain.fulfillment.carton

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

data class PackingCarton(
    val id: CartonId,
    val tenantId: TenantId,
    val productionRef: String,
    val cartonNumber: CartonNumber,
    val itemBreakdown: Map<String, Int> = emptyMap(),
    val quantityPcs: Int = 0,
    val grossWeightKg: Double = 0.0,
    val unitTicketIds: List<String> = emptyList(),
    val sealNumber: SealNumber? = null,
    val status: CartonStatus = CartonStatus.OPEN,
    val createdAt: Instant,
    val updatedAt: Instant,
    val notes: String = ""
) {
    init {
        require(productionRef.isNotBlank()) { "Referensi produksi wajib diisi" }

        if (status == CartonStatus.SEALED) {
            requireNotNull(sealNumber) { "Karton berstatus SEALED wajib punya seal number" }
            require(unitTicketIds.isNotEmpty()) { "Karton berstatus SEALED tidak boleh kosong" }
            require(quantityPcs > 0) { "Karton berstatus SEALED wajib punya pcs > 0" }
        }

        if (sealNumber != null) {
            require(status != CartonStatus.OPEN) { "Karton dengan seal number tidak bisa berstatus OPEN" }
        }
    }

    val isSealed: Boolean get() = status == CartonStatus.SEALED
    val isOpen: Boolean get() = status == CartonStatus.OPEN

    fun addItem(ticketId: String, sizeLabel: String, qty: Int): PackingCarton {
        require(status == CartonStatus.OPEN) {
            "Hanya karton berstatus OPEN yang bisa ditambahkan item"
        }
        require(ticketId.isNotBlank()) { "Ticket ID wajib diisi" }
        require(qty > 0) { "Qty wajib lebih dari 0" }

        val newItems = itemBreakdown.toMutableMap()
        newItems[sizeLabel] = (newItems[sizeLabel] ?: 0) + qty

        return copy(
            itemBreakdown = newItems,
            quantityPcs = quantityPcs + qty,
            unitTicketIds = unitTicketIds + ticketId,
            updatedAt = Clock.System.now()
        )
    }

    fun seal(sealNumber: SealNumber, grossWeightKg: Double, actor: String): PackingCarton {
        require(status == CartonStatus.OPEN) {
            "Hanya karton berstatus OPEN yang bisa disegel"
        }
        require(unitTicketIds.isNotEmpty()) {
            "Karton kosong tidak bisa disegel"
        }
        require(grossWeightKg > 0.0) {
            "Berat kotor wajib lebih dari 0"
        }

        return copy(
            sealNumber = sealNumber,
            status = CartonStatus.SEALED,
            grossWeightKg = grossWeightKg,
            updatedAt = Clock.System.now()
        )
    }

    fun reopen(reason: String, actor: String, now: Instant): PackingCarton {
        require(status == CartonStatus.SEALED) {
            "Hanya karton berstatus SEALED yang bisa dibuka kembali"
        }
        require(reason.isNotBlank()) {
            "Alasan reopen wajib diisi untuk audit"
        }
        require(actor.isNotBlank()) {
            "Nama actor wajib dicatat untuk audit"
        }

        return copy(
            status = CartonStatus.REOPENED,
            updatedAt = now
        )
    }

    fun sealAgain(sealNumber: SealNumber, grossWeightKg: Double, actor: String, now: Instant): PackingCarton {
        require(actor.isNotBlank()) { "Nama actor wajib dicatat untuk audit" }
        require(status == CartonStatus.REOPENED) {
            "Hanya karton berstatus REOPENED yang bisa disegel ulang"
        }
        require(grossWeightKg > 0.0) {
            "Berat kotor wajib lebih dari 0"
        }

        return copy(
            sealNumber = sealNumber,
            status = CartonStatus.SEALED,
            grossWeightKg = grossWeightKg,
            updatedAt = now
        )
    }
}
