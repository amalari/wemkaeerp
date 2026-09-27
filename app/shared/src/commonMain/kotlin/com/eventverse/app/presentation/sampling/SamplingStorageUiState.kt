package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.storage.SampleStorageStatus

/**
 * State dialog kustodi penyimpanan. Hanya satu dialog yang terbuka pada satu waktu, jadi
 * cukup satu target + jenisnya, bukan dua pasang field yang bisa saling bertabrakan.
 */
data class SamplingStorageUiState(
    val target: SamplingOrder? = null,
    val mode: StorageDialogMode? = null,
    /** Kustodi + kelengkapan deal dari server; `null` selama masih dimuat. */
    val status: SampleStorageStatus? = null,
    /** Pesan gerbang terakhir dari server, ditampilkan di dalam dialog, bukan snackbar. */
    val error: String? = null
)

enum class StorageDialogMode { STORE, RELEASE }
