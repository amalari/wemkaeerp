package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlinx.datetime.Instant

/** Tahap wajib yang tergabung di kolom R&D (rajut hingga kemas) pada kerangka rajut. */
val RD_STAGES: List<SamplingPipelineStage> = listOf(
    SamplingPipelineStage.MACHINE_KNITTING,
    SamplingPipelineStage.LINKING_ASSEMBLY,
    SamplingPipelineStage.CUCI_SOFTENER,
    SamplingPipelineStage.SETRIKA_UAP,
    SamplingPipelineStage.QC_FINISHING,
    SamplingPipelineStage.PENGEMASAN
)

/** SKIPPED = tahap dilompati rute sampling desain ini (tag Sampling di-×) — "Hanya Produksi". */
enum class RdStepState { DONE, ACTIVE, PENDING, SKIPPED }

/** Satu titik di jejak progres R&D: tahap wajib atau proses sisipan (bordir, sablon, …). */
data class RdStep(
    val label: String,
    val state: RdStepState,
    val stageCode: StageCode? = null,
    val isSubcontracted: Boolean = false
) {
    /** Jembatan enum untuk layar yang belum pindah (TRD-FLOW-001 R3). */
    val stage: SamplingPipelineStage? get() = stageCode?.requireSamplingStage()
}

/**
 * Jejak progres R&D satu SPK — dirakit dari alur efektifnya.
 *
 * Proses sisipan berjangkar *sesudah* sebuah tahap. Order belum mencatat posisi di dalam proses
 * sisipan, jadi proses dianggap selesai begitu SPK sudah melewati tahap wajib sesudahnya, dan
 * masih tertunda selama SPK belum melewatinya — tidak pernah ditebak sebagai "aktif".
 */
fun SamplingOrder.rdProgress(processes: List<TenantOptionalProcess> = customFlowProcesses.orEmpty()): List<RdStep> {
    // Kolom R&D = meja operator pada kerangka SPK (rajut: Rajut hingga Kemas, sama dengan RD_STAGES).
    val rdStages = stagesWith(StageTrait.OPERATOR_DESK)
    val current = positionOf(stageCode)
    val route = samplingRoute
    val afterRd = rdStages.lastOrNull()?.let { positionOf(it.code) + 1 } ?: stageFrame.size
    fun stateOf(code: StageCode) = when {
        code !in route -> RdStepState.SKIPPED
        positionOf(code) < current -> RdStepState.DONE
        positionOf(code) == current -> RdStepState.ACTIVE
        else -> RdStepState.PENDING
    }
    return rdStages.flatMapIndexed { index, stage ->
        val next = rdStages.getOrNull(index + 1)?.let { positionOf(it.code) } ?: afterRd
        val inserted = processes.filter { it.samplingAnchorAfter == stage.code }.map { process ->
            RdStep(
                label = process.displayName,
                state = if (current >= next) RdStepState.DONE else RdStepState.PENDING,
                isSubcontracted = process.executionMode == WorkExecutionMode.SUBCONTRACTED
            )
        }
        listOf(RdStep(label = stage.displayName, state = stateOf(stage.code), stageCode = stage.code)) + inserted
    }
}

/** Kapan SPK masuk tahap saat ini — dari jejak audit; null bila belum pernah berpindah. */
val SamplingOrder.enteredCurrentStageAt: Instant?
    get() = stageHistory.lastOrNull { it.toCode == stageCode }?.at

/** Ambang lama tertahan di satu tahap R&D sebelum kartu ditandai perlu perhatian. */
const val RD_STALL_WARNING_DAYS = 3

/** Berapa hari penuh SPK sudah berada di tahap saat ini; null bila tidak ada jejak audit. */
fun SamplingOrder.daysInCurrentStage(now: Instant): Int? =
    enteredCurrentStageAt?.let { ((now - it).inWholeHours / 24).toInt() }
