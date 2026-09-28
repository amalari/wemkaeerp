package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.stageflow.StageTrait

/**
 * Kalkulator murni timeline 5 langkah garment — dipisah dari [SamplingOrder] supaya
 * entity tetap pada satu tanggung jawab dan logika presentasi timeline bisa diuji
 * tanpa menyentuh entity (pola kalkulator murni, lihat file-size-rules §4).
 */
fun SamplingOrder.resolveGarmentTimeline(): List<GarmentStepState> {
    val stage = currentStage
    val isDraft = status == SamplingStatus.DRAFT || stageCode == SamplingPipelineStage.NEW_INTAKE.toStageCode()
    val totalDeposited = finishingDeposits.sumOf { it.qtyPcs }
    // Barang yang sudah masuk penyimpanan selesai diproduksi — yang tersisa hanya pengiriman.
    val isStored = stageCode == ExitStages.STORAGE
    val isDeliveredOrApproved = isStored || stageCode == ExitStages.DELIVERY ||
        stageCode == ExitStages.APPROVED ||
        isAccApproved ||
        !courierTracking.isNullOrBlank()

    val isSamplingComplete = !isDraft && isDeliveredOrApproved
    val isSamplingActive = !isDraft && !isDeliveredOrApproved

    val samplingSubtitle = when {
        isDraft -> "Menunggu Rilis SPK"
        status == SamplingStatus.REVISION -> "Perlu Revisi (Rev $revisionCount)"
        isDeliveredOrApproved -> "Sampling Tuntas"
        // Nama tahap milik kerangka SPK (keputusan 2026-09-29: satu nama per tahap, bukan dua).
        stage.has(StageTrait.WET_OR_PRESS) -> "${stage.displayName} ($totalDeposited/$sampleQuantity pcs)"
        else -> stage.displayName
    }

    val samplingBadge = when {
        status == SamplingStatus.REVISION -> "Revisi"
        isSamplingComplete -> "Selesai"
        isSamplingActive -> stage.shortLabel
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
            isActive = !isDraft && (isStored || stageCode == ExitStages.DELIVERY) && courierTracking.isNullOrBlank(),
            subtitle = when {
                !courierTracking.isNullOrBlank() -> "Resi: $courierTracking"
                isStored -> "Disimpan, Menunggu Lengkap"
                stageCode == ExitStages.DELIVERY -> "Siap Kirim ke Buyer"
                else -> "Menunggu Sampling Tuntas"
            },
            badgeText = when {
                isDraft || !courierTracking.isNullOrBlank() -> null
                isStored -> "Disimpan"
                stageCode == ExitStages.DELIVERY -> "Siap Kirim"
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
