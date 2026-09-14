package com.eventverse.app.domain.techpack

import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.contracts.BomLine
import com.eventverse.app.domain.contracts.LaborOperation
import com.eventverse.app.domain.contracts.SizeYieldFactor
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant

data class TechPack(
    val id: TechPackId,
    val tenantId: TenantId,
    val styleCode: StyleCode,
    val styleName: String,
    val clientName: String = "",
    val status: TechPackStatus = TechPackStatus.DRAFT,
    val version: Int = 1,
    val sourceSampleSpecId: String? = null,
    val sourceSpkNumber: String = "",
    val bomLines: List<BomLine> = emptyList(),
    val laborOperations: List<LaborOperation> = emptyList(),
    val sizeYieldFactors: List<SizeYieldFactor> = emptyList(),
    val customAttributes: CustomAttributes = CustomAttributes.EMPTY,
    val notes: String = "",
    val createdByUserId: String? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val releasedAt: Instant? = null,
    val archivedAt: Instant? = null
) {
    init {
        require(styleName.isNotBlank()) { "Nama style tidak boleh kosong" }
        require(version >= 1) { "Versi harus minimal 1" }
        require(bomLines.distinctBy { it.lineId }.size == bomLines.size) { "Duplikasi lineId pada baris BOM" }
        require(sizeYieldFactors.distinctBy { it.sizeLabel }.size == sizeYieldFactors.size) { "Duplikasi sizeLabel pada faktor ukuran" }
        require(laborOperations.distinctBy { it.operationId }.size == laborOperations.size) { "Duplikasi operationId pada operasi kerja" }
        require(status != TechPackStatus.RELEASED || releasedAt != null) { "Tech Pack yang dirilis wajib memiliki tanggal rilis" }
    }

    val isEditable: Boolean get() = status.isEditable && archivedAt == null
    val isReleased: Boolean get() = status == TechPackStatus.RELEASED
    val isArchived: Boolean get() = archivedAt != null

    val unresolvedLines: List<BomLine> get() = bomLines.filterNot { it.material.isResolved }

    val blockingUnresolvedLines: List<BomLine>
        get() = unresolvedLines.filter { it.ownership.hasFinancialAssetValue }

    val totalSamMinutes: Ratio
        get() {
            var total = Ratio.ZERO
            for (op in laborOperations) {
                total += op.samMinutes
            }
            return total
        }

    val totalOrderedQuantity: Long
        get() = sizeYieldFactors.sumOf { it.orderedQuantity }

    fun grossRequirementFor(line: BomLine): Quantity {
        if (sizeYieldFactors.isEmpty() || sizeYieldFactors.all { it.orderedQuantity == 0L }) {
            val totalQty = totalOrderedQuantity.takeIf { it > 0L } ?: 1L
            return line.grossFor(totalQty)
        }

        var totalMicros = 0L
        for (factor in sizeYieldFactors) {
            if (factor.orderedQuantity <= 0L) continue
            val netScaled = line.netQuantityPerGarment * factor.scale
            val grossPerPcs = netScaled + (netScaled * line.wasteAllowance)
            val grossForSize = grossPerPcs * factor.orderedQuantity
            totalMicros += grossForSize.convertTo(line.netQuantityPerGarment.uom).micros
        }
        return Quantity(totalMicros, line.netQuantityPerGarment.uom)
    }

    fun upsertBomLine(line: BomLine, now: Instant): Result<TechPack> {
        if (!isEditable) return Result.failure(IllegalStateException("Tech Pack versi $version berstatus ${status.displayName} tidak dapat diubah"))
        val existingIndex = bomLines.indexOfFirst { it.lineId == line.lineId }
        val updatedLines = if (existingIndex >= 0) {
            bomLines.toMutableList().apply { set(existingIndex, line) }
        } else {
            bomLines + line
        }
        return Result.success(copy(bomLines = updatedLines, updatedAt = now))
    }

    fun removeBomLine(lineId: String, now: Instant): Result<TechPack> {
        if (!isEditable) return Result.failure(IllegalStateException("Tech Pack versi $version berstatus ${status.displayName} tidak dapat diubah"))
        return Result.success(copy(bomLines = bomLines.filterNot { it.lineId == lineId }, updatedAt = now))
    }

    fun upsertLaborOperation(op: LaborOperation, now: Instant): Result<TechPack> {
        if (!isEditable) return Result.failure(IllegalStateException("Tech Pack versi $version berstatus ${status.displayName} tidak dapat diubah"))
        val existingIndex = laborOperations.indexOfFirst { it.operationId == op.operationId }
        val updatedOps = if (existingIndex >= 0) {
            laborOperations.toMutableList().apply { set(existingIndex, op) }
        } else {
            laborOperations + op
        }
        return Result.success(copy(laborOperations = updatedOps, updatedAt = now))
    }

    fun removeLaborOperation(operationId: String, now: Instant): Result<TechPack> {
        if (!isEditable) return Result.failure(IllegalStateException("Tech Pack versi $version berstatus ${status.displayName} tidak dapat diubah"))
        return Result.success(copy(laborOperations = laborOperations.filterNot { it.operationId == operationId }, updatedAt = now))
    }

    fun setSizeYieldFactors(factors: List<SizeYieldFactor>, now: Instant): Result<TechPack> {
        if (!isEditable) return Result.failure(IllegalStateException("Tech Pack versi $version berstatus ${status.displayName} tidak dapat diubah"))
        if (factors.any { it.scale.numerator <= 0L }) {
            return Result.failure(IllegalArgumentException("Skala yield ukuran harus lebih besar dari 0"))
        }
        return Result.success(copy(sizeYieldFactors = factors, updatedAt = now))
    }

    fun release(now: Instant): Result<TechPack> {
        if (status != TechPackStatus.DRAFT) return Result.failure(IllegalStateException("Hanya Tech Pack berstatus DRAFT yang dapat dirilis (status saat ini: ${status.displayName})"))
        if (bomLines.isEmpty()) return Result.failure(IllegalStateException("BOM kosong: minimal harus ada satu baris bahan"))
        if (bomLines.any { it.netQuantityPerGarment.isZero }) {
            val emptyLines = bomLines.filter { it.netQuantityPerGarment.isZero }.joinToString { it.material.displayLabel }
            return Result.failure(IllegalStateException("Baris BOM dengan kuantitas 0: $emptyLines"))
        }
        if (blockingUnresolvedLines.isNotEmpty()) {
            val unmapped = blockingUnresolvedLines.joinToString { it.material.displayLabel }
            return Result.failure(IllegalStateException("Bahan belum dipetakan ke katalog Master Data: $unmapped"))
        }
        return Result.success(copy(status = TechPackStatus.RELEASED, releasedAt = now, updatedAt = now))
    }

    fun reviseAs(newId: TechPackId, now: Instant): TechPack {
        require(status == TechPackStatus.RELEASED) { "Hanya Tech Pack yang sudah RELEASED yang dapat direvisi" }
        return copy(
            id = newId,
            version = version + 1,
            status = TechPackStatus.DRAFT,
            releasedAt = null,
            createdAt = now,
            updatedAt = now
        )
    }

    fun archive(now: Instant): TechPack = copy(archivedAt = now, updatedAt = now)
    fun restore(now: Instant): TechPack = copy(archivedAt = null, updatedAt = now)
}
