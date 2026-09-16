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
    /** Deal CRM pemilik sampling ini — Golden Sample Lock dari deal ke produksi massal. */
    val dealId: String? = null,
    val sampleQuantity: Int = 2,
    val courierTracking: String? = null,
    val samplingFeeIdr: Long = 0L,
    /** Jumlah revisi yang pernah diminta buyer — 0 berarti masih sampel awal (Rev 0). */
    val revisionCount: Int = 0,
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
        revisionCount = revisionCount + 1,
        updatedAt = updatedAt
    )

    fun cancel(notes: String, updatedAt: Instant): SamplingOrder = copy(
        status = SamplingStatus.CANCELLED,
        notes = notes,
        updatedAt = updatedAt
    )

    /**
     * Mengikat order sampling ke deal CRM. Idempotent: re-link ke deal yang sama
     * dibiarkan menghasilkan salinan identik tanpa mengubah updatedAt.
     */
    fun linkToDeal(dealId: String, updatedAt: Instant): SamplingOrder =
        if (this.dealId == dealId) this
        else copy(dealId = dealId, updatedAt = updatedAt)

    fun updateCourierTracking(tracking: String, updatedAt: Instant): SamplingOrder = copy(
        courierTracking = tracking.trim().takeIf { it.isNotEmpty() },
        updatedAt = updatedAt
    )

    /**
     * Menempelkan satu foto mockup desain. Nilai yang disimpan adalah KEY object storage
     * (bukan presigned URL yang kedaluwarsa) — URL segar dibuat saat pembacaan.
     *
     * Idempotent per key dan dibatasi [MAX_MOCKUPS] foto agar satu desain tidak menumpuk
     * puluhan foto yang membuat kartu accordion berat.
     */
    fun attachMockup(storageKey: String, updatedAt: Instant): SamplingOrder {
        val key = storageKey.trim()
        if (key.isEmpty() || key in knitSpec.mockupImageUrls) return this
        val next = (knitSpec.mockupImageUrls + key).takeLast(MAX_MOCKUPS)
        return copy(knitSpec = knitSpec.copy(mockupImageUrls = next), updatedAt = updatedAt)
    }

    /** Foto mockup terbaru — yang ditampilkan besar di kartu accordion desain. */
    val latestMockupKey: String? get() = knitSpec.mockupImageUrls.lastOrNull()

    /** Desain aktif = belum ACC dan belum dibatalkan (drop oleh buyer/admin). */
    val isActiveDesign: Boolean get() = status != SamplingStatus.ACC_APPROVED && status != SamplingStatus.CANCELLED

    companion object {
        /** Batas foto mockup per desain — mockup terbaru yang ditampilkan. */
        const val MAX_MOCKUPS = 6

        fun defaultMilestones(): List<MilestoneProgress> = MilestoneStep.entries.sortedBy { it.defaultOrder }.map {
            MilestoneProgress(step = it, isCompleted = false)
        }
    }
}
