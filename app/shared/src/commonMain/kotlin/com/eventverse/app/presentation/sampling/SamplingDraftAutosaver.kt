package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.infrastructure.api.SamplingRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Status autosave lembar kerja tahap di dialog Detail SPK. */
sealed interface DraftSaveStatus {
    data object Idle : DraftSaveStatus
    data object Saving : DraftSaveStatus
    data object Saved : DraftSaveStatus
    data class Failed(val message: String) : DraftSaveStatus
}

/** Status autosave diikat ke SPK-nya supaya hasil simpan SPK lain tidak bocor ke dialog yang baru dibuka. */
data class DraftSaveState(
    val orderId: SamplingOrderId? = null,
    val status: DraftSaveStatus = DraftSaveStatus.Idle
) {
    fun statusFor(id: SamplingOrderId): DraftSaveStatus = if (orderId == id) status else DraftSaveStatus.Idle
}

/**
 * Autosave draft lembar kerja tahap (Program CAM / hasil R&D) — menggantikan tombol "Simpan Program".
 *
 * Perubahan di-debounce agar satu sesi mengetik = satu request. Job hidup di scope ViewModel,
 * bukan di Composable, jadi menutup dialog sebelum debounce habis tidak membuang ketikan terakhir.
 * Sengaja tidak menyentuh `isSubmitting` / `statusMessage`: autosave tidak boleh men-disable
 * tombol "Mulai Pembuatan" atau memunculkan toast setiap kali mengetik.
 */
internal class SamplingDraftAutosaver(
    private val tenantSlug: String,
    private val remote: SamplingRemoteDataSource,
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<SamplingUiState>,
    private val debounceMillis: Long = 1_500
) {
    private var pending: Job? = null

    fun onDraftChanged(orderId: SamplingOrderId, stage: SamplingPipelineStage, sections: List<StageInputSection>) {
        val current = state.value.orders.firstOrNull { it.id == orderId } ?: return
        // Compose bisa memancarkan ulang isi yang sama (normalisasi saat render) — jangan jadikan request.
        if (sections.isEmpty() || current.stageInputFor(stage)?.sections == sections) return

        pending?.cancel()
        pending = scope.launch {
            delay(debounceMillis)
            setStatus(orderId, DraftSaveStatus.Saving)
            // Ambil order terbaru saat benar-benar menyimpan, bukan snapshot saat mengetik.
            val latest = state.value.orders.firstOrNull { it.id == orderId } ?: return@launch
            remote.saveOrder(tenantSlug, latest.fillStageInput(stage, sections, Clock.System.now()))
                .onSuccess { updated ->
                    state.update { s ->
                        s.copy(
                            orders = s.orders.map { if (it.id == updated.id) updated else it },
                            draftSave = DraftSaveState(orderId, DraftSaveStatus.Saved)
                        )
                    }
                }
                .onFailure { err ->
                    setStatus(orderId, DraftSaveStatus.Failed(err.message ?: "Gagal menyimpan draft"))
                }
        }
    }

    /**
     * Dipanggil sebelum pindah tahap: payload pindah tahap sudah membawa isi terbaru, jadi draft
     * yang masih menunggu dibuang — kalau tidak, responsnya bisa menimpa order yang sudah maju tahap.
     */
    fun cancelPending() {
        pending?.cancel()
        pending = null
    }

    private fun setStatus(orderId: SamplingOrderId, status: DraftSaveStatus) {
        state.update { it.copy(draftSave = DraftSaveState(orderId, status)) }
    }
}
