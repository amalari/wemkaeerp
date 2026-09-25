package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import kotlinx.datetime.Instant

/**
 * "SPK ini sedang di tangan siapa" — klaim operator atas satu SPK di mejanya.
 *
 * Klaim hanya berlaku selama [stage] sama dengan `pipelineStage` order. Begitu SPK pindah tahap
 * lewat jalur apa pun (termasuk jalur lama yang belum lewat [SamplingOrder.movedTo]), klaim yang
 * tertinggal otomatis tidak berlaku — tidak ada keadaan "sedang dikerjakan di tahap yang sudah
 * lewat".
 */
data class StageWorkClaim(
    val stage: SamplingPipelineStage,
    val operatorName: String,
    val actorEmail: String,
    val startedAt: Instant
)

/** Tiga kolom meja operator. Satu meja = satu [SamplingPipelineStage]. */
enum class OperatorDeskColumn(val displayName: String) {
    QUEUE("Antrian"),
    IN_PROGRESS("Sedang Dikerjakan"),
    DONE("Selesai")
}

/**
 * Tahap yang dikerjakan tangan operator di lantai produksi: dari Rajut sampai Pengemasan.
 * Rentang, bukan daftar — tahap yang disisipkan di antaranya ikut mendapat meja.
 */
val SamplingPipelineStage.isOperatorDesk: Boolean
    get() = order in SamplingPipelineStage.MACHINE_KNITTING.order..SamplingPipelineStage.PENGEMASAN.order

/** Tahap tujuan yang sah untuk kiriman balik rework dari tahap ini: meja-meja sebelumnya. */
val SamplingPipelineStage.reworkTargets: List<SamplingPipelineStage>
    get() = SamplingPipelineStage.entries.filter { it.isOperatorDesk && it.order < order }

/** Klaim yang masih berlaku untuk tahap SPK saat ini, atau `null`. */
val SamplingOrder.currentWork: StageWorkClaim?
    get() = activeWork?.takeIf { it.stage == pipelineStage }

/** Posisi SPK di meja [stage]; `null` bila SPK tidak sedang berada di tahap itu. */
fun SamplingOrder.deskColumn(stage: SamplingPipelineStage): OperatorDeskColumn? = when {
    pipelineStage != stage -> null
    currentWork != null -> OperatorDeskColumn.IN_PROGRESS
    else -> OperatorDeskColumn.QUEUE
}

/** Berapa kali SPK ini pernah dikirim balik untuk rework. */
val SamplingOrder.reworkCount: Int
    get() = stageHistory.count { it.isRework }

/**
 * Kiriman balik rework yang membuat SPK sekarang ada di tahapnya — `null` bila SPK tiba di
 * tahap ini lewat jalur maju biasa. Dipakai untuk menaruh kartu rework di puncak antrian.
 */
val SamplingOrder.pendingRework: StageTransitionAudit?
    get() = stageHistory.lastOrNull()?.takeIf { it.isRework && it.toStage == pipelineStage }

/**
 * Semua kali SPK ini diserahkan maju dari [stage], terbaru dulu. Kiriman balik rework tidak
 * dihitung "selesai" — barangnya justru belum beres.
 */
fun SamplingOrder.handoffsFrom(stage: SamplingPipelineStage): List<StageTransitionAudit> =
    stageHistory
        .filter { it.fromStage == stage && !it.isRework && it.toStage.order > stage.order }
        .sortedByDescending { it.at }

fun SamplingOrder.startStageWork(operatorName: String, actorEmail: String, now: Instant): SamplingOrder {
    require(pipelineStage.isOperatorDesk) { "${pipelineStage.displayName} bukan meja operator" }
    require(operatorName.isNotBlank()) { "Nama operator wajib diisi" }
    currentWork?.let { claim ->
        throw IllegalArgumentException("Sudah dikerjakan ${claim.operatorName} sejak ${claim.startedAt}")
    }
    return copy(
        activeWork = StageWorkClaim(pipelineStage, operatorName.trim(), actorEmail, now),
        updatedAt = now
    )
}

/** Mengembalikan SPK ke antrian mejanya (salah ambil, operator ganti shift). */
fun SamplingOrder.releaseStageWork(now: Instant): SamplingOrder {
    require(currentWork != null) { "SPK ini tidak sedang dikerjakan" }
    return copy(activeWork = null, updatedAt = now)
}

/**
 * Mengirim SPK mundur ke meja penyebab cacat. Kartunya mendarat di antrian meja tujuan dan
 * tercatat di [SamplingOrder.stageHistory] berikut alasan dan penanggungnya (Kontrak 5).
 */
fun SamplingOrder.sendBackForRework(
    target: SamplingPipelineStage,
    reason: String,
    liability: DefectLiability,
    actorEmail: String,
    actorRole: String,
    now: Instant
): SamplingOrder {
    require(target in pipelineStage.reworkTargets) {
        "Rework hanya bisa dikirim ke meja sebelum ${pipelineStage.displayName}"
    }
    require(reason.isNotBlank()) { "Alasan rework wajib diisi" }
    return movedTo(
        target,
        StageTransitionAudit(
            fromStage = pipelineStage,
            toStage = target,
            actorEmail = actorEmail,
            actorRole = actorRole,
            at = now,
            reason = reason.trim(),
            liability = liability
        )
    )
}
