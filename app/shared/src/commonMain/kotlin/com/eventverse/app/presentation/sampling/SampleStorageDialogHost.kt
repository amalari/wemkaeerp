package com.eventverse.app.presentation.sampling

import androidx.compose.runtime.Composable
import com.eventverse.app.presentation.sampling.components.ReleaseFromStorageDialog
import com.eventverse.app.presentation.sampling.components.StoreSampleDialog

/**
 * Memasang dialog kustodi penyimpanan yang sedang terbuka. Dipakai layar Sampling dan meja
 * operator Kemas — keduanya jalur masuk penyimpanan, jadi dialognya tidak boleh berbeda.
 */
@Composable
fun SampleStorageDialogHost(state: SamplingUiState, onEvent: (SamplingUiEvent) -> Unit) {
    val target = state.storage.target ?: return
    val close = { onEvent(SamplingUiEvent.CloseStorageDialog) }
    when (state.storage.mode) {
        StorageDialogMode.STORE -> StoreSampleDialog(
            order = target,
            isSubmitting = state.isSubmitting,
            error = state.storage.error,
            onDismiss = close,
            onConfirm = { location, qty -> onEvent(SamplingUiEvent.ConfirmStore(target.id, location, qty)) }
        )
        StorageDialogMode.RELEASE -> ReleaseFromStorageDialog(
            order = target,
            status = state.storage.status,
            isSubmitting = state.isSubmitting,
            error = state.storage.error,
            onDismiss = close,
            onConfirm = { reason -> onEvent(SamplingUiEvent.ConfirmRelease(target.id, reason)) }
        )
        null -> Unit
    }
}
