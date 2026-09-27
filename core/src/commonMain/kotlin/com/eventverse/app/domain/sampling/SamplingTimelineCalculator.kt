package com.eventverse.app.domain.sampling

/**
 * Kalkulator murni timeline 5 langkah garment — dipisah dari [SamplingOrder] supaya
 * entity tetap pada satu tanggung jawab dan logika presentasi timeline bisa diuji
 * tanpa menyentuh entity (pola kalkulator murni, lihat file-size-rules §4).
 */
fun SamplingOrder.resolveGarmentTimeline(): List<GarmentStepState> {
    val isDraft = status == SamplingStatus.DRAFT || pipelineStage == SamplingPipelineStage.NEW_INTAKE
    val totalDeposited = finishingDeposits.sumOf { it.qtyPcs }
    // Barang yang sudah masuk penyimpanan selesai diproduksi — yang tersisa hanya pengiriman.
    val isStored = pipelineStage == SamplingPipelineStage.STORAGE_HOLDING
    val isDeliveredOrApproved = isStored || pipelineStage == SamplingPipelineStage.IN_DELIVERY ||
        pipelineStage == SamplingPipelineStage.ACC_APPROVED ||
        isAccApproved ||
        !courierTracking.isNullOrBlank()

    val isSamplingComplete = !isDraft && isDeliveredOrApproved
    val isSamplingActive = !isDraft && !isDeliveredOrApproved

    val samplingSubtitle = when {
        isDraft -> "Menunggu Rilis SPK"
        status == SamplingStatus.REVISION -> "Perlu Revisi (Rev $revisionCount)"
        pipelineStage == SamplingPipelineStage.FLOW_REVIEW -> "Penentuan Alur Desain"
        pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING -> "Program Mesin CAM"
        pipelineStage == SamplingPipelineStage.MACHINE_KNITTING -> "Rajut Turun Mesin"
        pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY -> "Linking & Jahit"
        pipelineStage == SamplingPipelineStage.CUCI_SOFTENER ->
            "Cuci & Softener ($totalDeposited/$sampleQuantity pcs)"
        pipelineStage == SamplingPipelineStage.SETRIKA_UAP ->
            "Setrika Uap ($totalDeposited/$sampleQuantity pcs)"
        pipelineStage == SamplingPipelineStage.QC_FINISHING -> "QC 2 (Final Inspection)"
        pipelineStage == SamplingPipelineStage.PENGEMASAN -> "Pengemasan & Hangtag"
        isDeliveredOrApproved -> "Sampling Tuntas"
        else -> "Produksi Fisik Berjalan"
    }

    val samplingBadge = when {
        status == SamplingStatus.REVISION -> "Revisi"
        isSamplingComplete -> "Selesai"
        isSamplingActive -> when (pipelineStage) {
            SamplingPipelineStage.NEW_INTAKE -> "Draft"
            SamplingPipelineStage.FLOW_REVIEW -> "Alur"
            SamplingPipelineStage.CAM_PROGRAMMING -> "CAM"
            SamplingPipelineStage.MACHINE_KNITTING -> "Rajut"
            SamplingPipelineStage.LINKING_ASSEMBLY -> "Jahit"
            SamplingPipelineStage.CUCI_SOFTENER -> "Cuci"
            SamplingPipelineStage.SETRIKA_UAP -> "Setrika"
            SamplingPipelineStage.QC_FINISHING -> "QC 2"
            SamplingPipelineStage.PENGEMASAN -> "Kemas"
            SamplingPipelineStage.STORAGE_HOLDING -> "Disimpan"
            SamplingPipelineStage.IN_DELIVERY, SamplingPipelineStage.ACC_APPROVED -> "Selesai"
        }
        else -> null
    }

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

        // 3. Sampling (CAM, Rajut, Linking, QC 1, Finishing, QC 2)
        GarmentStepState(
            step = GarmentTrackingStep.SAMPLING,
            isCompleted = isSamplingComplete,
            isActive = isSamplingActive,
            subtitle = samplingSubtitle,
            badgeText = samplingBadge
        ),

        // 4. Siap Kirim / Terkirim ke Buyer
        GarmentStepState(
            step = GarmentTrackingStep.READY_TO_SHIP,
            isCompleted = !isDraft && !courierTracking.isNullOrBlank(),
            isActive = !isDraft && (isStored || pipelineStage == SamplingPipelineStage.IN_DELIVERY) && courierTracking.isNullOrBlank(),
            subtitle = when {
                !courierTracking.isNullOrBlank() -> "Resi: $courierTracking"
                isStored -> "Disimpan, Menunggu Lengkap"
                pipelineStage == SamplingPipelineStage.IN_DELIVERY -> "Siap Kirim ke Buyer"
                else -> "Menunggu Sampling Tuntas"
            },
            badgeText = when {
                isDraft || !courierTracking.isNullOrBlank() -> null
                isStored -> "Disimpan"
                pipelineStage == SamplingPipelineStage.IN_DELIVERY -> "Siap Kirim"
                else -> null
            }
        ),

        // 5. ACC Buyer
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
