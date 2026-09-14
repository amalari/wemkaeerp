package com.eventverse.app.domain.sampling.usecases

import com.eventverse.app.domain.sampling.*
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

data class CreateSamplingOrderCommand(
    val tenantId: TenantId,
    val clientName: String,
    val styleName: String,
    val sizeMode: SizeMode = SizeMode.ALL_SIZE,
    val deadlineProgram: LocalDate? = null,
    val deadlineFinishing: LocalDate? = null,
    val deadlineDelivery: LocalDate? = null,
    val leadId: String? = null,
    val notes: String = "",
    val useFactoryAllSizePreset: Boolean = true
)

class CreateSamplingOrderUseCase(
    private val repository: SamplingOrderRepository
) {
    suspend operator fun invoke(command: CreateSamplingOrderCommand): Result<SamplingOrder> = runCatching {
        require(command.clientName.isNotBlank()) { "Nama klien tidak boleh kosong" }
        require(command.styleName.isNotBlank()) { "Nama style / artikel tidak boleh kosong" }

        val now = Clock.System.now()
        val nextSpk = repository.nextSpkNumber(command.tenantId)
        val id = SamplingOrderId("smp_${now.toEpochMilliseconds()}_${nextSpk.value.filter { it.isLetterOrDigit() }}")

        val finishedSizes = if (command.useFactoryAllSizePreset) {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED)
        } else {
            listOf(SizeMeasurement(sizeLabel = if (command.sizeMode == SizeMode.ALL_SIZE) "ALL SIZE" else "M"))
        }

        val rawSizes = if (command.useFactoryAllSizePreset) {
            listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT)
        } else {
            listOf(SizeMeasurement(sizeLabel = if (command.sizeMode == SizeMode.ALL_SIZE) "ALL SIZE" else "M"))
        }

        val order = SamplingOrder(
            id = id,
            tenantId = command.tenantId,
            spkNumber = nextSpk,
            clientName = command.clientName.trim(),
            styleName = command.styleName.trim(),
            status = SamplingStatus.DRAFT,
            sizeMode = command.sizeMode,
            deadlineProgram = command.deadlineProgram,
            deadlineFinishing = command.deadlineFinishing,
            deadlineDelivery = command.deadlineDelivery,
            leadId = command.leadId,
            notes = command.notes.trim(),
            finishedSizeCharts = finishedSizes,
            rawKnitSizeCharts = rawSizes,
            createdAt = now,
            updatedAt = now
        )

        repository.save(order)
    }
}
