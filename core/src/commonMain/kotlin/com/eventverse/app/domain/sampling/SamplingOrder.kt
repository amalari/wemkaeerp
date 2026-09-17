package com.eventverse.app.domain.sampling

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
    val pipelineStage: SamplingPipelineStage = SamplingPipelineStage.NEW_INTAKE,
    val finishingPath: FinishingPath = FinishingPath.INTERNAL,
    val vendorInfo: MakloonVendorInfo = MakloonVendorInfo(),
    val finishingDeposits: List<FinishingDeposit> = emptyList(),
    val qcInspections: List<QcInspectionReport> = emptyList()
)

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
    val pipelineStage: SamplingPipelineStage = SamplingPipelineStage.NEW_INTAKE,
    val finishingPath: FinishingPath = FinishingPath.INTERNAL,
    val vendorInfo: MakloonVendorInfo = MakloonVendorInfo(),
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
    val createdAt: Instant,
    val updatedAt: Instant,
    val archivedAt: Instant? = null
) {
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
            pipelineStage = pipelineStage,
            finishingPath = finishingPath,
            vendorInfo = vendorInfo,
            finishingDeposits = finishingDeposits,
            qcInspections = qcInspections
        )
        return copy(
            status = SamplingStatus.REVISION,
            pipelineStage = SamplingPipelineStage.CAM_PROGRAMMING,
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

    fun advancePipelineStage(target: SamplingPipelineStage, updatedAt: Instant): SamplingOrder =
        copy(
            pipelineStage = target,
            status = if (status == SamplingStatus.DRAFT && target != SamplingPipelineStage.NEW_INTAKE) {
                SamplingStatus.IN_PROGRESS
            } else status,
            updatedAt = updatedAt
        )

    fun assignMakloonVendor(info: MakloonVendorInfo, updatedAt: Instant): SamplingOrder =
        copy(
            finishingPath = FinishingPath.MAKLOON_VENDOR,
            vendorInfo = info.copy(status = VendorFollowUpStatus.WITH_VENDOR),
            pipelineStage = SamplingPipelineStage.LINKING_ASSEMBLY,
            updatedAt = updatedAt
        )

    fun recordVendorReturn(returnedAt: LocalDate, updatedAt: Instant): SamplingOrder =
        copy(
            vendorInfo = vendorInfo.copy(
                returnedAt = returnedAt,
                status = VendorFollowUpStatus.RETURNED
            ),
            pipelineStage = SamplingPipelineStage.FINISHING_QC,
            updatedAt = updatedAt
        )

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
        val newStage = if (newFinishedQty >= sampleQuantity && pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY) {
            SamplingPipelineStage.FINISHING_QC
        } else {
            pipelineStage
        }
        return copy(
            finishingDeposits = updatedDeposits,
            pipelineStage = newStage,
            updatedAt = updatedAt
        )
    }

    fun completeQcInspection(report: QcInspectionReport, updatedAt: Instant): SamplingOrder {
        val updatedInspections = qcInspections + report
        val newStage = if (report.qcResult == QcInspectionResult.PASSED && pipelineStage == SamplingPipelineStage.FINISHING_QC) {
            SamplingPipelineStage.IN_DELIVERY
        } else {
            pipelineStage
        }
        return copy(
            qcInspections = updatedInspections,
            pipelineStage = newStage,
            updatedAt = updatedAt
        )
    }

    /** Snapshot arsip desain untuk nomor revisi tertentu; `null` bila belum ada snapshot. */
    fun snapshotFor(revision: Int): SamplingSnapshot? =
        revisionHistory.firstOrNull { it.revision == revision }?.snapshot
            ?: revisionHistory.firstOrNull { it.revision == revision + 1 }?.snapshot

    /** Feedback yang tercatat untuk satu nomor revisi; `null` bila revisi tak ditemukan. */
    fun revisionFeedback(revision: Int): RevisionFeedback? =
        revisionHistory.firstOrNull { it.revision == revision }
            ?: revisionHistory.firstOrNull { it.revision == revision - 1 }

    /**
     * Daftar syarat wajib ACC buyer yang BELUM terpenuhi. List kosong = desain siap di-ACC.
     *
     * 1. Foto mockup **Tampak Depan** wajib diunggah; Tampak Belakang opsional.
     * 2. Size chart harus punya minimal 1 ukuran yang datanya lengkap — semua baris POM
     *    yang ada di tabel wajib terisi untuk ukuran tersebut.
     * 3. Jumlah sampel minimal 1 pcs (dihitung dari kolom ukuran yang lengkap).
     *
     * Catatan (`notes`) bersifat opsional dan sengaja tidak divalidasi.
     * [sizeMatrix] boleh di-override pemanggil (mis. nilai input UI yang belum ter-autosave);
     * default memakai matriks milik order ini.
     */
    fun missingApprovalRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
        if (mockupFrontKey.isNullOrBlank()) {
            add("Foto mockup Tampak Depan wajib diunggah (Tampak Belakang opsional).")
        }
        if (firstCompleteSizeColumn(sizeMatrix) == null) {
            add("Size chart wajib punya minimal 1 ukuran dengan seluruh baris pengukuran terisi lengkap.")
        }
        if (calculateTotalSampleQuantity(sizeMatrix) < 1) {
            add("Jumlah sampel minimal 1 pcs.")
        }
    }

    /** Gerbang tombol ACC: true hanya jika seluruh syarat wajib sudah terpenuhi. */
    val isReadyForAcc: Boolean get() = missingApprovalRequirements().isEmpty()

    /**
     * Syarat wajib penerbitan SPK ke Divisi Sampling.
     * Mengembalikan daftar string kesalahan fatal yang menghalangi terbitnya SPK.
     *
     * 1. Nama desain wajib diisi (tidak boleh kosong/blank).
     * 2. Foto mockup Tampak Depan wajib diunggah (Divisi Sampling memerlukan acuan visual produk).
     * 3. Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris spesifikasi POM terisi lengkap.
     * 4. Jumlah sampel minimal 1 pcs (dihitung dari baris Qty pada matriks ukuran untuk kolom yang aktif).
     */
    fun missingSpkRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
        if (styleName.isBlank()) {
            add("Nama desain tidak boleh kosong.")
        }
        if (mockupFrontKey.isNullOrBlank()) {
            add("Foto mockup Tampak Depan wajib diunggah.")
        }
        if (!hasAtLeastOneCompleteMeasurementColumn(sizeMatrix)) {
            add("Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris spesifikasi (POM) terisi lengkap (misal: ALL SIZE terisi seluruhnya).")
        }
        val totalQty = calculateTotalSampleQuantity(sizeMatrix, sampleQuantity)
        if (totalQty < 1) {
            add("Jumlah sampel minimal 1 pcs. Silakan tentukan alokasi kuantitas pada kolom ukuran yang aktif di tabel Size Chart.")
        }
    }

    /**
     * Peringatan kelengkapan data (non-fatal) sebelum SPK diteruskan ke Divisi Sampling.
     */
    fun spkValidationWarnings(): List<String> = buildList {
        if (mockupBackKey.isNullOrBlank()) {
            add("Foto mockup Tampak Belakang belum diunggah (opsional).")
        }
    }

    /** Gerbang penerbitan SPK: true jika seluruh syarat wajib terpenuhi. */
    val isReadyForSpk: Boolean get() = missingSpkRequirements().isEmpty()

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
    /**
     * Menempelkan satu foto mockup desain (slot 'front' atau 'back').
     * Nilai yang disimpan adalah KEY object storage.
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

    /**
     * Menghitung status kronologis 8 langkah fisik garmen secara deterministik
     * berdasarkan status SPK, setoran finishing, hasil QC, dan status pengiriman.
     */
    fun resolveGarmentTimeline(): List<GarmentStepState> {
        val isDraft = status == SamplingStatus.DRAFT || pipelineStage == SamplingPipelineStage.NEW_INTAKE
        val totalDeposited = finishingDeposits.sumOf { it.qtyPcs }
        val isFinishingTuntas = totalDeposited >= sampleQuantity && totalDeposited > 0
        val isDeliveredOrApproved = pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
            pipelineStage == SamplingPipelineStage.ACC_APPROVED

        return listOf(
            // 1. Input Spek & Pola (Pra-Rilis) — aktif selama masih Draft belum terbit SPK
            GarmentStepState(
                step = GarmentTrackingStep.INPUT_SPEK,
                isCompleted = !isDraft,
                isActive = isDraft,
                subtitle = if (isDraft) "Draft (Sedang Diisi Sales)" else "Spek & Pola Lengkap",
                badgeText = if (isDraft) "Draft" else null
            ),

            // 2. Rilis SPK — selesai saat SPK resmi diterbitkan dari tombol Buat SPK
            GarmentStepState(
                step = GarmentTrackingStep.SPK_RELEASED,
                isCompleted = !isDraft,
                isActive = false,
                subtitle = if (isDraft) "Menunggu Buat SPK" else "SPK #${spkNumber.value}",
                badgeText = if (!isDraft) "Rilis" else null
            ),

            // 3. Rajut / Potong (CAM & Mesin)
            GarmentStepState(
                step = GarmentTrackingStep.KNITTING,
                isCompleted = !isDraft && pipelineStage > SamplingPipelineStage.LINKING_ASSEMBLY,
                isActive = !isDraft && (pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING ||
                    pipelineStage == SamplingPipelineStage.MACHINE_KNITTING ||
                    pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY),
                subtitle = when {
                    isDraft -> "Menunggu SPK"
                    pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING -> "Program Mesin CAM"
                    pipelineStage == SamplingPipelineStage.MACHINE_KNITTING -> "Rajut Turun Mesin"
                    pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY -> "Linking & Jahit"
                    else -> "Rajut & Jahit Tuntas"
                }
            ),

            // 4. QC 1 (In-Line / Jahitan Mentah)
            GarmentStepState(
                step = GarmentTrackingStep.QC_IN_LINE,
                isCompleted = !isDraft && (pipelineStage >= SamplingPipelineStage.FINISHING_QC || isDeliveredOrApproved),
                isActive = !isDraft && pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY && totalDeposited == 0,
                subtitle = when {
                    isDraft -> "Menunggu Rajut"
                    pipelineStage >= SamplingPipelineStage.FINISHING_QC || isDeliveredOrApproved -> "Jahitan Mentah Sesuai"
                    else -> "Inspeksi In-Line"
                },
                badgeText = if (!isDraft && (pipelineStage >= SamplingPipelineStage.FINISHING_QC || isDeliveredOrApproved)) "Lolos QC 1" else null
            ),

            // 5. Finishing & Steam (Washing, Setrika, Trimming)
            GarmentStepState(
                step = GarmentTrackingStep.FINISHING,
                isCompleted = !isDraft && (isFinishingTuntas || isDeliveredOrApproved),
                isActive = !isDraft && pipelineStage == SamplingPipelineStage.FINISHING_QC && !isFinishingTuntas,
                subtitle = when {
                    isDraft -> "Menunggu QC 1"
                    isFinishingTuntas || isDeliveredOrApproved -> "Cuci & Steam Tuntas ($totalDeposited pcs)"
                    pipelineStage == SamplingPipelineStage.FINISHING_QC -> "Pencucian & Steam ($totalDeposited/$sampleQuantity pcs)"
                    else -> "Menunggu Selesai Jahit"
                },
                badgeText = if (!isDraft && pipelineStage == SamplingPipelineStage.FINISHING_QC && !isFinishingTuntas) "Finishing" else null
            ),

            // 6. QC 2 (Final Inspection Ukuran Jadi)
            GarmentStepState(
                step = GarmentTrackingStep.QC_FINAL,
                isCompleted = !isDraft && isDeliveredOrApproved,
                isActive = !isDraft && pipelineStage == SamplingPipelineStage.FINISHING_QC && isFinishingTuntas,
                subtitle = when {
                    isDeliveredOrApproved -> "Ukuran Jadi Lolos AQL 1.5"
                    pipelineStage == SamplingPipelineStage.FINISHING_QC && isFinishingTuntas -> "Inspeksi Akhir Garmen Jadi"
                    else -> "Menunggu Selesai Finishing"
                },
                badgeText = if (isDeliveredOrApproved) "Lolos QC 2" else null
            ),

            // 7. Siap Kirim / Terkirim ke Buyer
            GarmentStepState(
                step = GarmentTrackingStep.READY_TO_SHIP,
                isCompleted = !isDraft && !courierTracking.isNullOrBlank(),
                isActive = !isDraft && pipelineStage == SamplingPipelineStage.IN_DELIVERY && courierTracking.isNullOrBlank(),
                subtitle = when {
                    !courierTracking.isNullOrBlank() -> "Resi: $courierTracking"
                    pipelineStage == SamplingPipelineStage.IN_DELIVERY -> "Siap Kirim ke Buyer"
                    else -> "Menunggu Lolos QC 2"
                },
                badgeText = if (!isDraft && pipelineStage == SamplingPipelineStage.IN_DELIVERY && courierTracking.isNullOrBlank()) "Siap Kirim" else null
            ),

            // 8. ACC Buyer
            GarmentStepState(
                step = GarmentTrackingStep.ACC_APPROVED,
                isCompleted = isAccApproved,
                isActive = status == SamplingStatus.REVISION,
                subtitle = when {
                    isAccApproved -> "Disetujui Buyer (Golden Sample)"
                    status == SamplingStatus.REVISION -> "Perlu Revisi (Rev $revisionCount)"
                    else -> "Menunggu Feedback Buyer"
                },
                badgeText = when {
                    isAccApproved -> "ACC"
                    status == SamplingStatus.REVISION -> "Revisi"
                    else -> null
                }
            )
        )
    }

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
