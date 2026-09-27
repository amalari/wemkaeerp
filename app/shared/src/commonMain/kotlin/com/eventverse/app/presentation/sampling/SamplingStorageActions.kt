package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.infrastructure.api.SampleStorageResult
import com.eventverse.app.infrastructure.api.SamplingStorageRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Aksi kustodi penyimpanan — dipisah dari [SamplingViewModel] mengikuti pola
 * [SamplingStageWorkActions]: berbagi `MutableStateFlow` yang sama, state holder tidak membengkak.
 */
internal class SamplingStorageActions(
    private val tenantSlug: String,
    private val remote: SamplingStorageRemoteDataSource,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SamplingUiState>
) {
    fun open(order: SamplingOrder, mode: StorageDialogMode) {
        state.update { it.copy(storage = SamplingStorageUiState(target = order, mode = mode)) }
        scope.launch {
            remote.getStorage(tenantSlug, order.id.value).onSuccess { status ->
                state.update { current ->
                    if (current.storage.target?.id != order.id) current
                    else current.copy(storage = current.storage.copy(status = status))
                }
            }
        }
    }

    fun close() = state.update { it.copy(storage = SamplingStorageUiState()) }

    fun store(orderId: SamplingOrderId, locationLabel: String, qtyPcs: Int) = submit(
        call = { remote.store(tenantSlug, orderId.value, locationLabel, qtyPcs) },
        success = { "${it.order.spkNumber.value} disimpan di ${it.storage.record?.location?.value ?: locationLabel}" }
    )

    fun release(orderId: SamplingOrderId, partialReason: String?) = submit(
        call = { remote.release(tenantSlug, orderId.value, partialReason) },
        success = { "${it.order.spkNumber.value} dilepas ke pengiriman buyer" }
    )

    private fun submit(call: suspend () -> Result<SampleStorageResult>, success: (SampleStorageResult) -> String) {
        scope.launch {
            state.update { it.copy(isSubmitting = true, storage = it.storage.copy(error = null)) }
            call()
                .onSuccess { result ->
                    state.update { current ->
                        current.copy(
                            orders = current.orders.map { if (it.id == result.order.id) result.order else it },
                            isSubmitting = false,
                            storage = SamplingStorageUiState(),
                            statusMessage = success(result),
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { err ->
                    // Gerbang ditolak (mis. SPK sedeal belum lengkap) — dialog tetap terbuka
                    // supaya admin bisa langsung memilih kirim parsial dengan alasan.
                    state.update {
                        it.copy(isSubmitting = false, storage = it.storage.copy(error = err.message))
                    }
                }
        }
    }
}
