package com.eventverse.app.presentation.builder

import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.shared.json.JsonValue

/**
 * Keadaan draf kerja tenant di Builder (`GET /api/builder/draft`). Tiga keadaan berbeda + galat:
 * [Loading] (belum ada respons), [Empty] (respons sukses `null` — pack tanpa blueprint draf, mis. pack
 * non-garment), [Loaded], dan [Failed] (jaringan/HTTP/isi tak terbaca). "Kosong" sengaja TIDAK dicampur
 * dengan galat maupun memuat.
 */
sealed interface BuilderDraftState {
    data object Loading : BuilderDraftState
    data object Empty : BuilderDraftState
    data class Loaded(val draft: DiscoveryDraftUi) : BuilderDraftState
    data class Failed(val message: String) : BuilderDraftState

    companion object {
        /** Menerjemahkan hasil klien ke keadaan; tidak ada fallback senyap — isi tak terbaca = [Failed]. */
        fun from(result: Result<JsonValue?>): BuilderDraftState = result.fold(
            onSuccess = { raw ->
                when (raw) {
                    null, JsonValue.Null -> Empty
                    is JsonValue.Obj -> runCatching { DiscoveryDraftUi.fromJson(raw) }.fold(
                        onSuccess = { Loaded(it) },
                        onFailure = { Failed(it.message ?: "Draf kerja tidak dapat dibaca") }
                    )
                    else -> Failed("Format draf kerja tidak dikenali")
                }
            },
            onFailure = { Failed(it.message ?: "Draf kerja gagal dimuat") }
        )

        suspend fun load(client: BuilderApiClient): BuilderDraftState = from(client.draft())
    }
}

/** Draf bila [BuilderDraftState.Loaded], selain itu null. */
val BuilderDraftState.draftOrNull: DiscoveryDraftUi?
    get() = (this as? BuilderDraftState.Loaded)?.draft
