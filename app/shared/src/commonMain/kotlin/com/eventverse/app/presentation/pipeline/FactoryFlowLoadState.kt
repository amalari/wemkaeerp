package com.eventverse.app.presentation.pipeline

import com.eventverse.app.infrastructure.api.PipelineRequestException
import com.eventverse.app.presentation.common.FriendlyErrors

/**
 * Keadaan pemuatan alur tenant Factory Flow (TRD-PLAT-012 Q2). Keadaan yang berbeda dan TIDAK dicampur:
 * [Loading], [Loaded] (server menjawab sukses), [AccessDenied] (403 dari gerbang fail-closed), dan [Failed]
 * (jaringan/5xx/isi tak terbaca).
 *
 * Kontrak server: `GET /api/tenant/pipeline` memprovisi alur dari preset tenant di sisi server dan menjawab
 * sukses, jadi "tenant belum punya pipeline" tidak pernah sampai ke klien sebagai galat. Karena itu klien TIDAK
 * punya jalur galat -> preset: preset hanya muncul lewat pratinjau eksplisit pengguna (`SelectPreset`).
 */
sealed interface FactoryFlowLoadState {
    data object Loading : FactoryFlowLoadState
    data object Loaded : FactoryFlowLoadState
    data class AccessDenied(val message: String) : FactoryFlowLoadState
    data class Failed(val message: String) : FactoryFlowLoadState

    companion object {
        const val DEFAULT_DENIED = "Anda tidak punya akses ke Factory Flow."

        /** Pemetaan murni galat klien -> keadaan; tanpa fallback senyap ke data contoh. */
        fun fromFailure(cause: Throwable): FactoryFlowLoadState {
            val http = cause as? PipelineRequestException
            if (http?.status == 403) {
                val server = http.serverMessage.trim()
                    .takeIf { it.isNotBlank() && !FriendlyErrors.isTransportNoise(it) && it.length <= 200 }
                return AccessDenied(server ?: DEFAULT_DENIED)
            }
            return Failed(FriendlyErrors.friendly(cause, "Gagal memuat alur pabrik."))
        }
    }
}

/** True bila layar harus menampilkan kartu penghalang, bukan kanvas. */
val FactoryFlowLoadState.isBlocked: Boolean
    get() = this is FactoryFlowLoadState.AccessDenied || this is FactoryFlowLoadState.Failed
