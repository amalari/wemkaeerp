package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageTrait
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
    val stageCode: StageCode,
    val operatorName: String,
    val actorEmail: String,
    val startedAt: Instant
) {
    constructor(stage: SamplingPipelineStage, operatorName: String, actorEmail: String, startedAt: Instant) :
        this(stage.toStageCode(), operatorName, actorEmail, startedAt)

    val stage: SamplingPipelineStage get() = stageCode.requireSamplingStage()
}

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

/**
 * Meja rework yang sah untuk SPK ini: meja operator sebelum tahapnya pada kerangka SPK, kecuali
 * yang dilompati rutenya — meja itu tidak pernah memegang barangnya.
 */
val SamplingOrder.reworkTargetCodes: List<StageCode>
    get() = stageFrame.take(positionOf(stageCode).coerceAtLeast(0))
        .filter { it.has(StageTrait.OPERATOR_DESK) && it.code in samplingRoute }
        .map { it.code }

/** Jembatan enum untuk layar yang belum pindah (TRD-FLOW-001 R3). */
val SamplingOrder.reworkTargets: List<SamplingPipelineStage>
    get() = reworkTargetCodes.map { it.requireSamplingStage() }

/** Klaim yang masih berlaku untuk tahap SPK saat ini, atau `null`. */
val SamplingOrder.currentWork: StageWorkClaim?
    get() = activeWork?.takeIf { it.stageCode == stageCode }

/** Posisi SPK di meja [stage]; `null` bila SPK tidak sedang berada di tahap itu. */
fun SamplingOrder.deskColumn(stage: StageCode): OperatorDeskColumn? = when {
    stageCode != stage -> null
    currentWork != null -> OperatorDeskColumn.IN_PROGRESS
    else -> OperatorDeskColumn.QUEUE
}

fun SamplingOrder.deskColumn(stage: SamplingPipelineStage): OperatorDeskColumn? = deskColumn(stage.toStageCode())

/** Berapa kali SPK ini pernah dikirim balik untuk rework. */
val SamplingOrder.reworkCount: Int
    get() = stageHistory.count { it.isRework }

/**
 * Kiriman balik rework yang membuat SPK sekarang ada di tahapnya — `null` bila SPK tiba di
 * tahap ini lewat jalur maju biasa. Dipakai untuk menaruh kartu rework di puncak antrian.
 */
val SamplingOrder.pendingRework: StageTransitionAudit?
    get() = stageHistory.lastOrNull { !it.isRelease }?.takeIf { it.isRework && it.toCode == stageCode }

/**
 * Semua kali SPK ini diserahkan maju dari [stage], terbaru dulu. Kiriman balik rework tidak
 * dihitung "selesai" — barangnya justru belum beres.
 */
fun SamplingOrder.handoffsFrom(stage: StageCode): List<StageTransitionAudit> =
    stageHistory
        .filter { it.fromCode == stage && !it.isRework && positionOf(it.toCode) > positionOf(stage) }
        .sortedByDescending { it.at }

fun SamplingOrder.handoffsFrom(stage: SamplingPipelineStage): List<StageTransitionAudit> = handoffsFrom(stage.toStageCode())

fun SamplingOrder.startStageWork(operatorName: String, actorEmail: String, now: Instant): SamplingOrder {
    require(currentStage.has(StageTrait.OPERATOR_DESK)) { "${currentStage.displayName} bukan meja operator" }
    require(operatorName.isNotBlank()) { "Nama operator wajib diisi" }
    currentWork?.let { claim ->
        throw IllegalArgumentException("Sudah dikerjakan ${claim.operatorName} sejak ${claim.startedAt}")
    }
    return copy(
        activeWork = StageWorkClaim(stageCode, operatorName.trim(), actorEmail, now),
        updatedAt = now
    )
}

/**
 * Mengembalikan SPK ke antrian mejanya (salah ambil, operator ganti shift). Ikut tercatat di
 * riwayat — kartu yang berkali-kali diambil lalu dikembalikan adalah sinyal ada yang macet.
 */
fun SamplingOrder.releaseStageWork(now: Instant, actorEmail: String = ""): SamplingOrder {
    val claim = requireNotNull(currentWork) { "SPK ini tidak sedang dikerjakan" }
    val audit = StageTransitionAudit(
        fromCode = stageCode,
        toCode = stageCode,
        actorEmail = actorEmail,
        actorRole = "OPERATOR",
        at = now,
        workStartedAt = claim.startedAt,
        operatorName = claim.operatorName,
        isRelease = true
    )
    return copy(activeWork = null, stageHistory = stageHistory + audit, updatedAt = now)
}

/** Kapan SPK tiba di tahap asal entri [handoff] — entri maju/rework terakhir yang menuju ke sana. */
fun SamplingOrder.arrivalBefore(handoff: StageTransitionAudit): Instant? =
    stageHistory
        .takeWhile { it !== handoff }
        .lastOrNull { !it.isRelease && it.toCode == handoff.fromCode }
        ?.at

/**
 * Mengirim SPK mundur ke meja penyebab cacat. Kartunya mendarat di antrian meja tujuan dan
 * tercatat di [SamplingOrder.stageHistory] berikut alasan dan penanggungnya (Kontrak 5).
 */
fun SamplingOrder.sendBackForRework(
    target: StageCode,
    reason: String,
    liability: DefectLiability,
    actorEmail: String,
    actorRole: String,
    now: Instant
): SamplingOrder {
    require(target in reworkTargetCodes) {
        "Rework hanya bisa dikirim ke meja sebelum ${currentStage.displayName}"
    }
    require(reason.isNotBlank()) { "Alasan rework wajib diisi" }
    return movedTo(
        target,
        StageTransitionAudit(
            fromCode = stageCode,
            toCode = target,
            actorEmail = actorEmail,
            actorRole = actorRole,
            at = now,
            reason = reason.trim(),
            liability = liability
        )
    )
}

/** Jembatan enum untuk layar yang belum pindah (TRD-FLOW-001 R3). */
fun SamplingOrder.sendBackForRework(
    target: SamplingPipelineStage,
    reason: String,
    liability: DefectLiability,
    actorEmail: String,
    actorRole: String,
    now: Instant
): SamplingOrder = sendBackForRework(target.toStageCode(), reason, liability, actorEmail, actorRole, now)
