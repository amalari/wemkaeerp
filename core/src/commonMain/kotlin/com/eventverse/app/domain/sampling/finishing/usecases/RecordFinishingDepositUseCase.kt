package com.eventverse.app.domain.sampling.finishing.usecases

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingOrderRepository
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.finishing.FinishingDeposit
import com.eventverse.app.domain.sampling.finishing.FinishingDepositRepository
import com.eventverse.app.domain.sampling.finishing.FinishingProgress
import com.eventverse.app.domain.sampling.finishing.progressAgainst
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

data class RecordFinishingDepositCommand(
    val tenantId: TenantId,
    val samplingOrderId: SamplingOrderId,
    val depositDate: LocalDate,
    val qtyPcs: Int,
    val weightKg: Double = 0.0,
    val scalePhotoKey: String? = null,
    val garmentPhotoKey: String? = null,
    val operatorName: String = "",
    val notes: String = ""
)

/** Hasil setoran: dokumennya sendiri plus akumulasi terbaru, supaya UI tak perlu menghitung ulang. */
data class FinishingDepositResult(
    val deposit: FinishingDeposit,
    val progress: FinishingProgress
)

/**
 * Mencatat satu setoran bertahap tim finishing internal.
 *
 * Targetnya dibaca dari matriks ukuran SPK, bukan dari angka yang dikirim klien — kalau target
 * ikut dikirim dari layar, setiap operator bisa menetapkan targetnya sendiri dan "sisa 0" berhenti
 * berarti apa pun.
 */
class RecordFinishingDepositUseCase(
    private val depositRepository: FinishingDepositRepository,
    private val samplingOrderRepository: SamplingOrderRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(command: RecordFinishingDepositCommand): Result<FinishingDepositResult> = runCatching {
        require(command.qtyPcs > 0) { "Jumlah setoran minimal 1 pcs" }
        require(command.weightKg >= 0.0) { "Berat timbangan tidak boleh negatif" }

        val order = samplingOrderRepository.findById(command.samplingOrderId)
            ?: error("SPK sampling tidak ditemukan: ${command.samplingOrderId.value}")
        require(order.tenantId == command.tenantId) { "SPK sampling bukan milik pabrik ini" }

        val existing = depositRepository.findBySamplingOrder(command.tenantId, command.samplingOrderId)
        val target = finishingTargetPcs(order)
        val before = existing.progressAgainst(target)
        require(before.remainingPcs > 0) {
            "Tugas finishing SPK ${order.spkNumber.value} sudah tuntas (${before.depositedPcs}/$target pcs)"
        }

        val now = clock.now()
        val deposit = FinishingDeposit(
            id = depositRepository.nextId(command.tenantId),
            tenantId = command.tenantId,
            samplingOrderId = command.samplingOrderId,
            depositDate = command.depositDate,
            qtyPcs = command.qtyPcs,
            weightKg = command.weightKg,
            scalePhotoKey = command.scalePhotoKey?.trim()?.takeIf { it.isNotEmpty() },
            garmentPhotoKey = command.garmentPhotoKey?.trim()?.takeIf { it.isNotEmpty() },
            operatorName = command.operatorName.trim(),
            notes = command.notes.trim(),
            createdAt = now
        )

        val saved = depositRepository.save(deposit)
        FinishingDepositResult(
            deposit = saved,
            progress = (existing + saved).progressAgainst(target)
        )
    }
}

/**
 * Target pcs finishing satu SPK: akumulasi kolom ukuran yang datanya lengkap di matriks,
 * dengan `sampleQuantity` sebagai cadangan untuk SPK lama yang matriksnya belum terisi.
 */
fun finishingTargetPcs(order: SamplingOrder): Int =
    calculateTotalSampleQuantity(order.sizeMatrix, fallback = order.sampleQuantity)
        .takeIf { it > 0 } ?: order.sampleQuantity
