package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlinx.datetime.Instant

/** Tahap wajib yang tergabung di kolom R&D (rajut hingga kemas). */
val RD_STAGES: List<SamplingPipelineStage> = listOf(
    SamplingPipelineStage.MACHINE_KNITTING,
    SamplingPipelineStage.LINKING_ASSEMBLY,
    SamplingPipelineStage.CUCI_SOFTENER,
    SamplingPipelineStage.SETRIKA_UAP,
    SamplingPipelineStage.QC_FINISHING,
    SamplingPipelineStage.PENGEMASAN
)

enum class RdStepState { DONE, ACTIVE, PENDING }

/** Satu titik di jejak progres R&D: tahap wajib atau proses sisipan (bordir, sablon, …). */
data class RdStep(
    val label: String,
    val state: RdStepState,
    val stage: SamplingPipelineStage? = null,
    val isSubcontracted: Boolean = false
)

/**
 * Jejak progres R&D satu SPK — dirakit dari alur efektifnya.
 *
 * Proses sisipan berjangkar *sesudah* sebuah tahap. Order belum mencatat posisi di dalam proses
 * sisipan, jadi proses dianggap selesai begitu SPK sudah melewati tahap wajib sesudahnya, dan
 * masih tertunda selama SPK belum melewatinya — tidak pernah ditebak sebagai "aktif".
 */
fun SamplingOrder.rdProgress(processes: List<TenantOptionalProcess> = customFlowProcesses.orEmpty()): List<RdStep> {
    val current = pipelineStage.order
    fun stateOf(stage: SamplingPipelineStage) = when {
        stage.order < current -> RdStepState.DONE
        stage.order == current -> RdStepState.ACTIVE
        else -> RdStepState.PENDING
    }
    return RD_STAGES.flatMapIndexed { index, stage ->
        val next = RD_STAGES.getOrNull(index + 1) ?: SamplingPipelineStage.IN_DELIVERY
        val inserted = processes.filter { it.samplingAnchorAfter == stage }.map { process ->
            RdStep(
                label = process.displayName,
                state = if (current >= next.order) RdStepState.DONE else RdStepState.PENDING,
                isSubcontracted = process.executionMode == WorkExecutionMode.SUBCONTRACTED
            )
        }
        listOf(RdStep(label = stage.displayName, state = stateOf(stage), stage = stage)) + inserted
    }
}

/** Kapan SPK masuk tahap saat ini — dari jejak audit; null bila belum pernah berpindah. */
val SamplingOrder.enteredCurrentStageAt: Instant?
    get() = stageHistory.lastOrNull { it.toStage == pipelineStage }?.at

/** Ambang lama tertahan di satu tahap R&D sebelum kartu ditandai perlu perhatian. */
const val RD_STALL_WARNING_DAYS = 3

/** Berapa hari penuh SPK sudah berada di tahap saat ini; null bila tidak ada jejak audit. */
fun SamplingOrder.daysInCurrentStage(now: Instant): Int? =
    enteredCurrentStageAt?.let { ((now - it).inWholeHours / 24).toInt() }
