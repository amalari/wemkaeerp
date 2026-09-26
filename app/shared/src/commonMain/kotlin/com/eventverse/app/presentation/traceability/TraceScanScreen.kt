package com.eventverse.app.presentation.traceability

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.traceability.components.BundleTallySheet
import com.eventverse.app.presentation.traceability.components.SackCompositionCard
import com.eventverse.app.presentation.traceability.components.TraceCodeEntryCard

/**
 * Layar telusur lantai produksi: satu kolom, satu langkah pada satu waktu.
 *
 * Sengaja tidak dibuat master-detail seperti layar kantor. Ini dipakai sambil berdiri di samping meja
 * QC dengan satu tangan memegang bundel, sering di layar HP — tata letak dua panel di situ berarti
 * setengah layar selalu menampilkan hal yang tidak sedang dikerjakan.
 */
@Composable
fun TraceScanScreen(
    viewModel: TraceabilityViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(ClaySpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        Text("Telusur Bundel & Karung", style = MaterialTheme.typography.headlineSmall)

        state.error?.let { MessageCard(it, WeMadeColors.Defect) { viewModel.onEvent(TraceabilityUiEvent.DismissMessage) } }
        state.info?.let { MessageCard(it, WeMadeColors.Success) { viewModel.onEvent(TraceabilityUiEvent.DismissMessage) } }

        when (state.step) {
            TraceScanStep.IDLE -> TraceCodeEntryCard(
                code = state.manualCode,
                onCodeChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateManualCode(it)) },
                onSubmit = { viewModel.onEvent(TraceabilityUiEvent.SubmitManualCode) },
                onScan = { viewModel.onEvent(TraceabilityUiEvent.ScanWithCamera) },
                scannerAvailability = state.scannerAvailability,
                isBusy = state.isBusy
            )

            TraceScanStep.BUNDLE_TALLY -> {
                ScannedCardHeader(state) { viewModel.onEvent(TraceabilityUiEvent.Reset) }
                BundleTallySheet(
                    tallies = state.tallies,
                    completeSets = state.previewCompleteSets,
                    leftover = state.previewLeftover,
                    operatorName = state.operatorName,
                    shift = state.shift,
                    notes = state.notes,
                    isBusy = state.isBusy,
                    onTallyChange = { tally, value ->
                        viewModel.onEvent(TraceabilityUiEvent.UpdateTally(tally.panel, value))
                    },
                    onOperatorChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateOperator(it)) },
                    onShiftChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateShift(it)) },
                    onNotesChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateNotes(it)) },
                    onSave = { viewModel.onEvent(TraceabilityUiEvent.SaveTally) }
                )
            }

            TraceScanStep.SACK_CLOSE -> {
                ScannedCardHeader(state) { viewModel.onEvent(TraceabilityUiEvent.Reset) }
                SackCompositionCard(
                    sizeLabel = state.scan?.sizeLabel.orEmpty(),
                    bundles = state.availableBundles,
                    selectedCodes = state.selectedBundleCodes,
                    requirements = state.requirements,
                    selectedSets = state.selectedBundleSets,
                    declaredPcs = state.declaredPcs,
                    weightKg = state.weightKg,
                    previewShrinkage = state.previewShrinkage,
                    isBusy = state.isBusy,
                    onToggleBundle = { viewModel.onEvent(TraceabilityUiEvent.ToggleBundle(it)) },
                    onDeclaredPcsChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateDeclaredPcs(it)) },
                    onWeightChange = { viewModel.onEvent(TraceabilityUiEvent.UpdateWeightKg(it)) },
                    onClose = { viewModel.onEvent(TraceabilityUiEvent.CloseSack) }
                )
            }
        }
    }
}

@Composable
private fun ScannedCardHeader(state: TraceabilityUiState, onReset: () -> Unit) {
    val scan = state.scan ?: return
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(TraceCodec.grouped(scan.code), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    listOfNotNull(
                        scan.snapshot?.spkNumber,
                        "Size ${scan.sizeLabel}",
                        scan.snapshot?.styleName
                    ).joinToString(" · "),
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 2
                )
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayBadge(text = scan.tierLabel, tint = WeMadeColors.Primary)
        }
        Spacer(Modifier.height(ClaySpacing.Md))
        ClayButton(text = "Pindai Kartu Lain", onClick = onReset, style = ClayButtonStyle.Secondary)
    }
}

@Composable
private fun MessageCard(message: String, tint: androidx.compose.ui.graphics.Color, onDismiss: () -> Unit) {
    ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = tint) {
        Text(message)
        Spacer(Modifier.height(ClaySpacing.Md))
        ClayButton(text = "Tutup", onClick = onDismiss, style = ClayButtonStyle.Secondary)
    }
}
