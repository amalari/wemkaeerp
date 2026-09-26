package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.pipeline.DefectLiability
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.infrastructure.api.SamplingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aksi meja operator (ambil SPK, kembalikan ke antrian, kirim rework) — dipisah dari
 * [SamplingViewModel] supaya state holder-nya tidak terus membengkak per fitur meja.
 * Berbagi `MutableStateFlow` yang sama, jadi satu sumber kebenaran tetap satu.
 */
internal class SamplingStageWorkActions(
    private val tenantSlug: String,
    private val remote: SamplingRemoteDataSource,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SamplingUiState>
) {
    fun start(orderId: SamplingOrderId, operatorName: String) = submit(
        call = { remote.startStageWork(tenantSlug, orderId.value, operatorName) },
        success = { "${it.spkNumber.value} mulai dikerjakan" },
        failure = "Gagal mengambil SPK"
    )

    fun release(orderId: SamplingOrderId) = submit(
        call = { remote.releaseStageWork(tenantSlug, orderId.value) },
        success = { "${it.spkNumber.value} dikembalikan ke antrian" },
        failure = "Gagal mengembalikan SPK"
    )

    fun sendBackForRework(
        orderId: SamplingOrderId,
        target: SamplingPipelineStage,
        reason: String,
        liability: DefectLiability
    ) = submit(
        call = { remote.sendBackForRework(tenantSlug, orderId.value, target, reason, liability) },
        success = { "${it.spkNumber.value} dikirim rework ke ${target.displayName}" },
        failure = "Gagal mengirim rework"
    )

    private fun submit(
        call: suspend () -> Result<SamplingOrder>,
        success: (SamplingOrder) -> String,
        failure: String
    ) {
        scope.launch {
            state.update { it.copy(isSubmitting = true) }
            call()
                .onSuccess { updated ->
                    state.update { current ->
                        current.copy(
                            orders = current.orders.map { if (it.id == updated.id) updated else it },
                            isSubmitting = false,
                            reworkTarget = null,
                            statusMessage = success(updated),
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    state.update {
                        it.copy(isSubmitting = false, statusMessage = "$failure: ${err.message}", isErrorMessage = true)
                    }
                }
        }
    }
}
