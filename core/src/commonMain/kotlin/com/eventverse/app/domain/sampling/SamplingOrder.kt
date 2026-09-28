package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate

data class SamplingSnapshot(
    val mockupFrontKey: String? = null,
    val mockupBackKey: String? = null,
    val sizeMatrix: List<SizeChartRow> = emptyList(),
    val sampleQuantity: Int = 1,
    val samplingFeeIdr: Long = 0L,
    val notes: String = "",
    val stageCode: StageCode = SamplingPipelineStage.NEW_INTAKE.toStageCode(),
    val finishingPath: FinishingPath = FinishingPath.INTERNAL,
    val vendorInfo: MakloonVendorInfo = MakloonVendorInfo(),
    val finishingDeposits: List<FinishingDeposit> = emptyList(),
    val qcInspections: List<QcInspectionReport> = emptyList()
) {
    val pipelineStage: SamplingPipelineStage get() = stageCode.requireSamplingStage()
}

data class RevisionFeedback(
    /** Nomor revisi — nomor revisi yang sedang diarsipkan atau diajukan. */
    val revision: Int,
    val notes: String,
    val at: Instant,
    val snapshot: SamplingSnapshot? = null
)

data class SamplingOrder(
    val id: SamplingOrderId,
    val tenantId: TenantId,
    val spkNumber: SpkNumber,
    val clientName: String,
    val styleName: String,
    val status: SamplingStatus = SamplingStatus.DRAFT,
    /** Tahap saat ini pada kerangka tenant — sumber kebenaran (TRD-FLOW-001); lihat [pipelineStage]. */
    val stageCode: StageCode = SamplingPipelineStage.NEW_INTAKE.toStageCode(),
    val finishingPath: FinishingPath = FinishingPath.INTERNAL,
    val vendorInfo: MakloonVendorInfo = MakloonVendorInfo(),
    val sizeMode: SizeMode = SizeMode.ALL_SIZE,
    val sizeLabel: String? = null,
    val parentSamplingOrderId: SamplingOrderId? = null,
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
    /**
     * Riwayat feedback revisi per nomor revisi — supaya admin bisa menelusuri catatan buyer
     * dari revisi ke revisi (Rev 1, Rev 2, …), bukan hanya feedback terakhir yang menimpa
     * [accNotes]. Kosong pada data lama; fallback tampilan tetap membaca [accNotes].
     */
    val revisionHistory: List<RevisionFeedback> = emptyList(),
    val accNotes: String = "",
    val notes: String = "",
    val knitSpec: KnitSpec = KnitSpec(),
    val finishedSizeCharts: List<SizeMeasurement> = listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED),
    val rawKnitSizeCharts: List<SizeMeasurement> = listOf(FactorySizePresets.ALL_SIZE_CARDIGAN_RAW_KNIT),
    val sizeMatrix: List<SizeChartRow> = defaultSamplingSizeMatrix(),
    val machineProgram: MachineProgram = MachineProgram(feederInstructions = FactorySizePresets.DEFAULT_FEEDERS),
    val yieldAndTiming: YieldAndTiming = YieldAndTiming(),
    val finishingDeposits: List<FinishingDeposit> = emptyList(),
    val qcInspections: List<QcInspectionReport> = emptyList(),
    val milestones: List<MilestoneProgress> = defaultMilestones(),
    /**
     * Lembar input dinamis per tahap (CAM, Rajut, dst.) — label & value bebas mengikuti
     * kebutuhan lembar kerja pabrik. Kosong pada data lama; gerbang tahap membacanya.
     */
    val stageInputs: List<StageWorkInput> = emptyList(),
    /** Jejak audit perpindahan tahap: siapa yang memindahkan dan kapan. Diisi server. */
    val stageHistory: List<StageTransitionAudit> = emptyList(),
    /** Operator yang sedang memegang SPK di mejanya; hanya berlaku bila `stage == pipelineStage`. */
    val activeWork: StageWorkClaim? = null,
    /** Alur proses opsional kustom khusus desain ini. `null` = mewarisi alur default pabrik. */
    val customFlowProcesses: List<TenantOptionalProcess>? = null,
    val isCustomFlow: Boolean = false,
    /** Tag fase Cuci/Setrika desain ini. `null` = mewarisi template pabrik (belum dibekukan). */
    val stagePhaseTags: StagePhaseTags? = null,
    /** Kerangka tahap beku SPK ini (TRD-FLOW-001). `null` = belum beku — lihat [stageFrame]. */
    val frozenStageFlow: List<StageDefinition>? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null
) {
    /** Jembatan baca untuk pembaca lama — lihat [requireSamplingStage]. */
    val pipelineStage: SamplingPipelineStage get() = stageCode.requireSamplingStage()

    val isAccApproved: Boolean get() = status == SamplingStatus.ACC_APPROVED
    val isArchived: Boolean get() = archivedAt != null
    val isInDelivery: Boolean get() = pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
        pipelineStage == SamplingPipelineStage.ACC_APPROVED ||
        isAccApproved ||
        !courierTracking.isNullOrBlank() ||
        milestones.any { it.step == MilestoneStep.KIRIM && it.isCompleted }

    val totalFinishedDepositedQty: Int get() = finishingDeposits.sumOf { it.qtyPcs }
    val remainingFinishingQty: Int get() = (sampleQuantity - totalFinishedDepositedQty).coerceAtLeast(0)
    val isFinishingComplete: Boolean get() = remainingFinishingQty == 0
    val latestQcReport: QcInspectionReport? get() = qcInspections.lastOrNull()

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

    fun requestRevision(notes: String, updatedAt: Instant): SamplingOrder {
        val nextRevision = revisionCount + 1
        val currentSnapshot = SamplingSnapshot(
            mockupFrontKey = mockupFrontKey,
            mockupBackKey = mockupBackKey,
            sizeMatrix = sizeMatrix,
            sampleQuantity = sampleQuantity,
            samplingFeeIdr = samplingFeeIdr,
            notes = this.notes,
            stageCode = stageCode,
            finishingPath = finishingPath,
            vendorInfo = vendorInfo,
            finishingDeposits = finishingDeposits,
            qcInspections = qcInspections
        )
        return copy(
            status = SamplingStatus.REVISION,
            stageCode = SamplingPipelineStage.CAM_PROGRAMMING.toStageCode(),
            accNotes = notes,
            revisionCount = nextRevision,
            // Simpan snapshot keadaan saat ini yang diasosiasikan dengan revisionCount sebelum naik
            revisionHistory = revisionHistory + RevisionFeedback(
                revision = revisionCount,
                notes = notes,
                at = updatedAt,
                snapshot = currentSnapshot
            ),
            updatedAt = updatedAt
        )
    }

    /**
     * Simpan lembar input dinamis untuk satu tahap — menggantikan entry tahap yang sama
     * bila sudah ada (satu tahap = satu lembar kerja aktif).
     */
    fun fillStageInput(stage: StageCode, sections: List<StageInputSection>, updatedAt: Instant): SamplingOrder {
        require(sections.isNotEmpty()) { "Lembar input tahap tidak boleh kosong" }
        val entry = StageWorkInput(stageCode = stage, sections = sections)
        val updated = stageInputs.filterNot { it.stageCode == stage } + entry
        return copy(stageInputs = updated, updatedAt = updatedAt)
    }

    /** Section input yang sudah diisi untuk satu tahap (untuk tampilan read-only antar tahap). */
    fun stageInputFor(stage: StageCode): StageWorkInput? = stageInputs.firstOrNull { it.stageCode == stage }

    /** Jembatan enum untuk pemanggil yang belum pindah (TRD-FLOW-001 R3). */
    fun advancePipelineStage(target: SamplingPipelineStage, updatedAt: Instant, actorEmail: String = "", actorRole: String = "") =
        advancePipelineStage(target.toStageCode(), updatedAt, actorEmail, actorRole)

    fun advancePipelineStage(
        target: StageCode,
        updatedAt: Instant,
        actorEmail: String = "",
        actorRole: String = ""
    ): SamplingOrder {
        require(target in samplingRoute) { "Tahap ${stageFrame.firstOrNull { it.code == target }?.displayName ?: target.value} tidak ada di alur sampling desain ini" }
        requireStageGate(target)
        // Transisi apa pun — termasuk NEW_INTAKE -> NEW_INTAKE saat Deals menerbitkan SPK —
        // menandai order sudah diserahkan ke Divisi Sampling, jadi DRAFT berakhir di sini.
        return movedTo(target, StageTransitionAudit(stageCode, target, actorEmail, actorRole, updatedAt))
            .copy(status = if (status == SamplingStatus.DRAFT) SamplingStatus.IN_PROGRESS else status)
    }

    /**
     * Satu-satunya jalan memindahkan tahap yang meninggalkan jejak: tahap berganti, entri audit
     * ditambahkan, dan klaim "sedang dikerjakan" dilepas — pekerjaan di tahap lama sudah selesai.
     */
    internal fun movedTo(target: StageCode, audit: StageTransitionAudit): SamplingOrder = copy(
        stageCode = target,
        activeWork = null,
        stageHistory = stageHistory + audit.copy(
            workStartedAt = audit.workStartedAt ?: currentWork?.startedAt,
            operatorName = audit.operatorName ?: currentWork?.operatorName
        ),
        updatedAt = audit.at
    )

    fun assignMakloonVendor(info: MakloonVendorInfo, updatedAt: Instant, actorEmail: String = ""): SamplingOrder {
        val updatedInfo = info.copy(status = VendorFollowUpStatus.WITH_VENDOR)
        return copy(finishingPath = FinishingPath.MAKLOON_VENDOR, vendorInfo = updatedInfo)
            .movedTo(
                assemblyStage,
                StageTransitionAudit(stageCode, assemblyStage, actorEmail, "MAKLOON", updatedAt, operatorName = updatedInfo.vendorName.ifBlank { null })
            )
    }

    // Sampel yang pulang dari vendor makloon mendarat di tahap penyelesaian akhir paling
    // awal pada rutenya, bukan di QC: yang kembali adalah barang yang baru selesai dirakit.
    fun recordVendorReturn(returnedAt: LocalDate, updatedAt: Instant, actorEmail: String = ""): SamplingOrder {
        val landing = afterAssembly
        return copy(vendorInfo = vendorInfo.copy(returnedAt = returnedAt, status = VendorFollowUpStatus.RETURNED))
            .movedTo(landing, vendorAudit(landing, actorEmail, updatedAt))
    }

    /** Tangan pertama sesudah perakitan — Cuci, atau Setrika/QC bila Cuci dilompati rute ini. */
    private val afterAssembly: StageCode
        get() = samplingRoute.nextAfter(assemblyStage) ?: finalQcStage

    private fun vendorAudit(target: StageCode, actorEmail: String, at: Instant) =
        StageTransitionAudit(stageCode, target, actorEmail, "MAKLOON", at, operatorName = vendorInfo.vendorName.ifBlank { null })

    fun updateTenselity(entries: List<TenselityEntry>, updatedAt: Instant): SamplingOrder =
        copy(
            machineProgram = machineProgram.copy(tenselityEntries = entries),
            updatedAt = updatedAt
        )

    fun updateActualGramasiAndTiming(
        weights: PanelWeightGrams,
        minutes: PanelKnittingMinutes,
        updatedAt: Instant
    ): SamplingOrder =
        copy(
            yieldAndTiming = yieldAndTiming.copy(panelWeights = weights, panelMinutes = minutes),
            updatedAt = updatedAt
        )

    fun addFinishingDeposit(deposit: FinishingDeposit, updatedAt: Instant): SamplingOrder {
        val updatedDeposits = finishingDeposits + deposit
        val newFinishedQty = updatedDeposits.sumOf { it.qtyPcs }
        // Setoran ini adalah setoran hasil PERAKITAN (linking, obras, pasang aksesori). Ketika
        // seluruh pcs tersetor, yang selesai adalah perakitannya — barangnya lalu berpindah ke
        // tangan pertama lantai penyelesaian akhir pada rute desain ini (pencuci, atau penyetrika
        // bila tag Sampling pada Cuci di-×).
        //
        // Sengaja TIDAK melompat ke QC. Melompat berarti menyatakan sampel sudah dicuci dan
        // disetrika padahal tidak ada satu pun catatan yang mengatakan begitu — kesalahan yang
        // sama persis dengan memetakan baris lama ke QC saat migrasi. Dua tahap di antaranya
        // dimajukan oleh orang yang benar-benar mengerjakannya, lewat tombol di meja finishing.
        val newStage = if (newFinishedQty >= sampleQuantity && stageCode == assemblyStage) {
            afterAssembly
        } else {
            stageCode
        }
        val withDeposit = copy(finishingDeposits = updatedDeposits, updatedAt = updatedAt)
        if (newStage == stageCode) return withDeposit
        return withDeposit.movedTo(newStage, StageTransitionAudit(stageCode, newStage, deposit.operatorName, "OPERATOR", updatedAt))
    }

    fun completeQcInspection(report: QcInspectionReport, updatedAt: Instant): SamplingOrder {
        val updatedInspections = qcInspections + report
        // Hanya QC finishing yang boleh mendorong SPK ke pengiriman. QC rajut memeriksa panel
        // yang bahkan belum dirakit — meloloskannya tidak berarti bajunya siap jalan.
        val newStage = if (report.kind == QcInspectionKind.FINISHING &&
            report.qcResult == QcInspectionResult.PASSED &&
            stageCode == finalQcStage
        ) {
            // QC yang lolos memindahkan barang ke meja pengemasan, bukan langsung ke pengiriman:
            // sejak pengemasan jadi tahapnya sendiri, melompatinya berarti menyatakan sampel sudah
            // dilipat, di-hangtag, dan masuk polybag padahal belum ada yang mengerjakannya.
            packingStage
        } else {
            stageCode
        }
        val withReport = copy(qcInspections = updatedInspections, updatedAt = updatedAt)
        if (newStage == stageCode) return withReport
        return withReport.movedTo(newStage, StageTransitionAudit(stageCode, newStage, report.inspectorName, "QC", updatedAt))
    }

    /** Snapshot arsip desain untuk nomor revisi tertentu; `null` bila belum ada snapshot. */
    fun snapshotFor(revision: Int): SamplingSnapshot? =
        revisionHistory.firstOrNull { it.revision == revision }?.snapshot
            ?: revisionHistory.firstOrNull { it.revision == revision + 1 }?.snapshot

    /** Feedback yang tercatat untuk satu nomor revisi; `null` bila revisi tak ditemukan. */
    fun revisionFeedback(revision: Int): RevisionFeedback? =
        revisionHistory.firstOrNull { it.revision == revision }
            ?: revisionHistory.firstOrNull { it.revision == revision - 1 }

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
     * Menempelkan satu foto mockup desain (slot 'front' atau 'back').
     * Nilai yang disimpan adalah KEY object storage (bukan presigned URL).
     */
    fun attachMockup(storageKey: String, slot: String = "front", updatedAt: Instant): SamplingOrder {
        val key = storageKey.trim()
        if (key.isEmpty()) return this
        val front = if (slot == "front") key else mockupFrontKey ?: ""
        val back = if (slot == "back") key else mockupBackKey ?: ""
        val list = listOfNotNull(
            front.takeIf { it.isNotBlank() }?.let { if (!it.startsWith("front:") && !it.startsWith("back:")) "front:$it" else it },
            back.takeIf { it.isNotBlank() }?.let { if (!it.startsWith("front:") && !it.startsWith("back:")) "back:$it" else it }
        )
        return copy(knitSpec = knitSpec.copy(mockupImageUrls = list), updatedAt = updatedAt)
    }

    val mockupFrontKey: String? get() {
        val tagged = knitSpec.mockupImageUrls.firstOrNull { it.startsWith("front:") }?.removePrefix("front:")
        if (tagged != null) return tagged
        return knitSpec.mockupImageUrls.firstOrNull { !it.startsWith("back:") }
    }

    val mockupBackKey: String? get() {
        val tagged = knitSpec.mockupImageUrls.firstOrNull { it.startsWith("back:") }?.removePrefix("back:")
        if (tagged != null) return tagged
        val untagged = knitSpec.mockupImageUrls.filter { !it.startsWith("front:") && !it.startsWith("back:") }
        return untagged.getOrNull(1)
    }

    /** Foto mockup terbaru (fallback backward-compatible). */
    val latestMockupKey: String? get() = mockupFrontKey ?: knitSpec.mockupImageUrls.lastOrNull()

    /** Desain aktif = belum ACC dan belum dibatalkan (drop oleh buyer/admin). */
    val isActiveDesign: Boolean get() = status != SamplingStatus.ACC_APPROVED && status != SamplingStatus.CANCELLED

    // Timeline 5 langkah garment (Input Spek -> Rilis SPK -> Sampling -> Siap Kirim -> ACC)
    // tinggal di SamplingTimelineCalculator.kt sebagai extension function murni —
    // call site `order.resolveGarmentTimeline()` tidak berubah.

    private val hasCompleteMeasurement: Boolean
        get() = hasAtLeastOneCompleteMeasurementColumn(sizeMatrix)

    companion object {
        /** Batas foto mockup per desain — mockup terbaru yang ditampilkan. */
        const val MAX_MOCKUPS = 6

        fun defaultMilestones(): List<MilestoneProgress> = MilestoneStep.entries.sortedBy { it.defaultOrder }.map {
            MilestoneProgress(step = it, isCompleted = false)
        }
    }
}
