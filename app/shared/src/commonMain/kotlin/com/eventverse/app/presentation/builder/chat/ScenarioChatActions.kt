package com.eventverse.app.presentation.builder.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.discovery.scenario.ScenarioActions
import com.eventverse.app.presentation.discovery.scenario.scenarioConfirmFailureNotice
import com.eventverse.app.presentation.discovery.scenario.scenarioProposeFailureNotice
import com.eventverse.app.presentation.discovery.scenario.scenarioProposeNotice
import kotlinx.coroutines.launch

/**
 * Menyambung tombol "Usulkan skenario" dan "Konfirmasi" ke server Builder (TRD-BLDR-002 SC3). Setelah sukses draf dimuat
 * ulang ([reloadDraft]); pesan memakai fungsi murni di `ScenarioNotices`. Simulasi memutar skenario TIDAK lewat sini
 * (nol HTTP): hanya dua aksi tulis ini yang memanggil server.
 */
@Composable
internal fun rememberScenarioActions(client: BuilderApiClient, reloadDraft: suspend () -> Unit): ScenarioActions {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    return ScenarioActions(
        busy = busy,
        notice = notice,
        onPropose = {
            if (!busy) scope.launch {
                busy = true
                notice = null
                client.proposeScenarios()
                    .onSuccess { o -> notice = scenarioProposeNotice(o); if (o.featureEnabled) reloadDraft() }
                    .onFailure { e -> notice = scenarioProposeFailureNotice(e) }
                busy = false
            }
        },
        onConfirm = { id ->
            if (!busy) scope.launch {
                busy = true
                notice = null
                client.confirmScenario(id)
                    .onSuccess { sc -> notice = "Skenario '${sc.title}' dikonfirmasi."; reloadDraft() }
                    .onFailure { e -> notice = scenarioConfirmFailureNotice(e) }
                busy = false
            }
        }
    )
}
