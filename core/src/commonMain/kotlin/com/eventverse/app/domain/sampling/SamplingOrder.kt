package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class SamplingOrder(
    val id: SamplingOrderId,
    val tenantId: TenantId,
    val spkNumber: SpkNumber,
    val clientName: String,
    val styleName: String,
    val status: SamplingStatus = SamplingStatus.DRAFT,
    val sizeMode: SizeMode = SizeMode.ALL_SIZE,
    val deadlineProgram: LocalDate? = null,
    val deadlineFinishing: LocalDate? = null,
    val deadlineDelivery: LocalDate? = null,
    val leadId: String? = null,
    val accNotes: String = "",
    val notes: String = "",
    val knitSpec: KnitSpec = KnitSpec(),
    val finishedSizeCharts: List<SizeMeasurement> = listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED),
    val rawKnitSizeCharts: List<SizeMeasurement> = listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT),
    val machineProgram: MachineProgram = MachineProgram(feederInstructions = FactorySizePresets.DEFAULT_FEEDERS),
    val yieldAndTiming: YieldAndTiming = YieldAndTiming(),
    val milestones: List<MilestoneProgress> = defaultMilestones(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null
) {
    val isAccApproved: Boolean get() = status == SamplingStatus.ACC_APPROVED
    val isArchived: Boolean get() = archivedAt != null

    fun updateTechnicalSpec(
        knitSpec: KnitSpec,
        finishedSizes: List<SizeMeasurement>,
        rawSizes: List<SizeMeasurement>,
        machineProgram: MachineProgram,
        yieldAndTiming: YieldAndTiming,
        updatedAt: Instant
    ): SamplingOrder = copy(
        knitSpec = knitSpec,
        finishedSizeCharts = finishedSizes,
        rawKnitSizeCharts = rawSizes,
        machineProgram = machineProgram,
        yieldAndTiming = yieldAndTiming,
        status = if (status == SamplingStatus.DRAFT) SamplingStatus.IN_PROGRESS else status,
        updatedAt = updatedAt
    )

    fun toggleMilestone(
        step: MilestoneStep,
        isCompleted: Boolean,
        completedAt: LocalDate?,
        milestoneNotes: String? = null,
        updatedAt: Instant
    ): SamplingOrder {
        val updatedList = milestones.map { current ->
            if (current.step == step) {
                current.copy(
                    isCompleted = isCompleted,
                    completedAt = if (isCompleted) (completedAt ?: current.completedAt) else null,
                    notes = milestoneNotes ?: current.notes
                )
            } else {
                current
            }
        }
        return copy(milestones = updatedList, updatedAt = updatedAt)
    }

    fun approveAcc(notes: String, updatedAt: Instant): SamplingOrder = copy(
        status = SamplingStatus.ACC_APPROVED,
        accNotes = notes,
        updatedAt = updatedAt
    )

    fun requestRevision(notes: String, updatedAt: Instant): SamplingOrder = copy(
        status = SamplingStatus.REVISION,
        accNotes = notes,
        updatedAt = updatedAt
    )

    fun cancel(notes: String, updatedAt: Instant): SamplingOrder = copy(
        status = SamplingStatus.CANCELLED,
        notes = notes,
        updatedAt = updatedAt
    )

    companion object {
        fun defaultMilestones(): List<MilestoneProgress> = MilestoneStep.entries.sortedBy { it.defaultOrder }.map {
            MilestoneProgress(step = it, isCompleted = false)
        }
    }
}
