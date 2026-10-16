package com.eventverse.app.presentation.discovery.scenario

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.MoqupsWireframeRenderer
import com.eventverse.app.presentation.discovery.PrototypeRenderer
import com.eventverse.app.presentation.discovery.PrototypeSession
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Panel prototype bermode skenario (TRD-BLDR-002 FR-7): [ScenarioPicker] di atas navigator yang SUDAH ada.
 *
 * - Tanpa skenario terpilih: [PrototypeRenderer] persis seperti sebelumnya (urutan daftar layar).
 * - Skenario terpilih: [MoqupsWireframeRenderer] menerima [scenarioScreens] (urutan skenario) dan "Lanjut Alur" menjalankan
 *   [ScenarioRun.advance] (bawa data di memori sesi, nol HTTP) sebelum pindah. Pindah layar oleh pemutar memasang ulang
 *   navigator lewat `key(epoch)` + `startIndex`; klik langsung pada rantai navigator tidak memengaruhi pemutar (sumber
 *   langkah diambil dari indeks layar saat "Lanjut" ditekan).
 */
@Composable
fun ScenarioPrototypeHost(
    draft: DiscoveryDraftUi,
    actions: ScenarioActions,
    modifier: Modifier = Modifier
) {
    val typography = rememberClayTypography()
    val scope = rememberCoroutineScope()
    val session = remember(draft.screens) { PrototypeSession(draft.screens) }
    val data = remember(session, draft.screens) { SessionScenarioData(session, draft.screens) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val selected = draft.scenarios.firstOrNull { it.id.value == selectedId }
    var run by remember(selected) { mutableStateOf(selected?.let { ScenarioRun(it) }) }
    var epoch by remember { mutableStateOf(0) }
    var message by remember(selected) { mutableStateOf<ScenarioNotice?>(null) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ScenarioPicker(
            scenarios = draft.scenarios,
            selectedId = selected?.id?.value,
            onSelect = { selectedId = it; session.clearFormNotices(); epoch++ },
            actions = actions
        )
        draft.scenariosError?.let {
            Text("Skenario draf tidak terbaca: $it", style = typography.bodySmall, color = WeMadeColors.Error)
        }
        val current = run
        message?.let { ScenarioNoticeBar(it) }
        current?.reworkLabel?.let { label ->
            ClayButton(
                text = "Kembalikan ke $label",
                onClick = { current.rework()?.let { run = it; message = null; session.clearFormNotices(); epoch++ } },
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                maxLines = 1
            )
        }
        Box(modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight()) {
            val active = run
            if (selected == null || active == null) {
                PrototypeRenderer(
                    draft = draft, screens = draft.screens, session = session,
                    carousel = true, fullWidth = true, wireframe = true,
                    modifier = Modifier.fillMaxSize()
                )
            } else key(selected.id.value, epoch) {
                MoqupsWireframeRenderer(
                    draft = draft,
                    screens = scenarioScreens(selected, draft.screens),
                    session = session,
                    modifier = Modifier.fillMaxSize(),
                    startIndex = active.stepIndex,
                    onAdvance = { from ->
                        scope.launch {
                            val result = active.copy(stepIndex = from).advance(data, actions.carryRule)
                            session.clearFormNotices()
                            message = scenarioAdvanceNotice(result)
                            when (result) {
                                is ScenarioAdvance.Moved -> { run = result.run; epoch++ }
                                is ScenarioAdvance.Blocked -> run = active.copy(stepIndex = from)
                            }
                        }
                    }
                )
            }
        }
    }
}
