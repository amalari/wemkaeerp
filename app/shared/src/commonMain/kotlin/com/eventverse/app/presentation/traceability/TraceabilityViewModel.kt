package com.eventverse.app.presentation.traceability

import com.eventverse.app.domain.traceability.*
import com.eventverse.app.infrastructure.api.TraceScanView
import com.eventverse.app.infrastructure.api.TraceabilityApiClient
import com.eventverse.app.infrastructure.api.TraceabilityRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TraceabilityViewModel(
    private val tenantSlug: String,
    private val remoteDataSource: TraceabilityRemoteDataSource = TraceabilityApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(
        TraceabilityUiState(scannerAvailability = traceScannerAvailability())
    )
    val uiState: StateFlow<TraceabilityUiState> = _uiState.asStateFlow()

    fun onEvent(event: TraceabilityUiEvent) {
        when (event) {
            is TraceabilityUiEvent.UpdateManualCode ->
                _uiState.update { it.copy(manualCode = TraceCodec.normalize(event.value), error = null) }

            is TraceabilityUiEvent.SubmitManualCode -> resolve(_uiState.value.manualCode)
            is TraceabilityUiEvent.ScanWithCamera -> scanWithCamera()
            is TraceabilityUiEvent.Reset -> _uiState.value = TraceabilityUiState(
                scannerAvailability = _uiState.value.scannerAvailability,
                operatorName = _uiState.value.operatorName,
                shift = _uiState.value.shift
            )
            is TraceabilityUiEvent.DismissMessage ->
                _uiState.update { it.copy(error = null, info = null) }

            is TraceabilityUiEvent.UpdateTally -> _uiState.update { state ->
                state.copy(
                    tallies = state.tallies.map {
                        if (it.panel == event.panel) it.copy(rawValue = event.value.filter(Char::isDigit)) else it
                    }
                )
            }
            is TraceabilityUiEvent.UpdateOperator -> _uiState.update { it.copy(operatorName = event.value) }
            is TraceabilityUiEvent.UpdateShift -> _uiState.update { it.copy(shift = event.value) }
            is TraceabilityUiEvent.UpdateNotes -> _uiState.update { it.copy(notes = event.value) }
            is TraceabilityUiEvent.SaveTally -> saveTally()

            is TraceabilityUiEvent.ToggleBundle -> _uiState.update { state ->
                val selected = state.selectedBundleCodes.toMutableSet()
                if (!selected.add(event.code)) selected.remove(event.code)
                state.copy(selectedBundleCodes = selected)
            }
            is TraceabilityUiEvent.UpdateDeclaredPcs ->
                _uiState.update { it.copy(declaredPcs = event.value.filter(Char::isDigit)) }
            is TraceabilityUiEvent.UpdateWeightKg ->
                _uiState.update { it.copy(weightKg = event.value.filter { c -> c.isDigit() || c == '.' }) }
            is TraceabilityUiEvent.CloseSack -> closeSack()
        }
    }

    private fun scanWithCamera() {
        scope.launch {
            val payload = scanTraceCode()
            if (payload.isNullOrBlank()) return@launch
            resolve(payload)
        }
    }

    private fun resolve(rawCode: String) {
        if (rawCode.isBlank()) return
        scope.launch {
            _uiState.update { it.copy(isBusy = true, error = null, info = null) }

            remoteDataSource.resolve(tenantSlug, rawCode)
                .onSuccess { scan ->
                    // Kartu pra-cetak yang baru pertama disentuh belum punya baris; membukanya di sini
                    // membuat operator cukup satu kali scan, bukan scan lalu menekan "buat".
                    val opened = if (scan.isNewCard && scan.tier.isContainer) {
                        remoteDataSource.open(tenantSlug, rawCode, "").getOrNull()
                    } else {
                        scan.container
                    }
                    applyScan(scan.copy(container = opened ?: scan.container))
                }
                .onFailure { failure -> _uiState.update { it.copy(isBusy = false, error = failure.message) } }
        }
    }

    private suspend fun applyScan(scan: TraceScanView) {
        val requirements = scan.snapshot?.panelRequirements.orEmpty()

        when (scan.tier) {
            TraceTier.BUNDLE -> _uiState.update { state ->
                state.copy(
                    isBusy = false,
                    scan = scan,
                    step = TraceScanStep.BUNDLE_TALLY,
                    manualCode = "",
                    tallies = requirements.map { requirement ->
                        PanelTallyInput(
                            panel = requirement.panel,
                            piecesPerGarment = requirement.piecesPerGarment,
                            // Bundel yang sudah pernah dihitung dibuka dengan angkanya, bukan kosong:
                            // operator yang membuka ulang biasanya ingin mengoreksi satu angka saja.
                            rawValue = scan.container
                                ?.countFor(requirement.panel)
                                ?.takeIf { it > 0 }?.toString().orEmpty()
                        )
                    },
                    operatorName = scan.container?.operatorName?.ifBlank { state.operatorName } ?: state.operatorName,
                    shift = scan.container?.shift?.value?.ifBlank { state.shift } ?: state.shift,
                    notes = scan.container?.notes.orEmpty()
                )
            }

            TraceTier.SACK -> {
                val ref = scan.snapshot?.ref
                val bundles = ref?.let { remoteDataSource.containers(tenantSlug, it).getOrNull() }.orEmpty()
                _uiState.update { state ->
                    state.copy(
                        isBusy = false,
                        scan = scan,
                        step = TraceScanStep.SACK_CLOSE,
                        manualCode = "",
                        tallies = requirements.map { PanelTallyInput(it.panel, it.piecesPerGarment) },
                        // Hanya bundel yang sudah dihitung, seukuran, dan belum dituang ke karung lain.
                        // Menyaringnya di sini menghemat langkah penolakan yang sudah pasti terjadi.
                        availableBundles = bundles.filter {
                            it.isBundle &&
                                it.state == TraceContainerState.TALLIED &&
                                it.sizeLabel.equals(scan.sizeLabel, ignoreCase = true)
                        },
                        selectedBundleCodes = emptySet(),
                        declaredPcs = "",
                        weightKg = ""
                    )
                }
            }

            TraceTier.WORKSHEET -> _uiState.update {
                it.copy(
                    isBusy = false,
                    scan = scan,
                    step = TraceScanStep.IDLE,
                    manualCode = "",
                    info = "Kartu ini Lembar Kerja Rajut untuk size ${scan.sizeLabel} — " +
                        "dibaca di mesin, bukan diisi seperti kartu bundel."
                )
            }
        }
    }

    private fun saveTally() {
        val state = _uiState.value
        val code = state.scan?.code ?: return
        if (state.tallies.none { it.pieces > 0 }) {
            _uiState.update { it.copy(error = "Isi dulu minimal satu hitungan panel.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            remoteDataSource.recordTally(
                tenantSlug = tenantSlug,
                code = code,
                tallies = state.tallies.filter { it.pieces > 0 }.map { PanelTally(it.panel, it.pieces) },
                operatorName = state.operatorName,
                shift = state.shift,
                recordedAt = null,
                notes = state.notes
            )
                .onSuccess { saved ->
                    val sets = saved.completeSets(state.requirements)
                    _uiState.update {
                        TraceabilityUiState(
                            scannerAvailability = it.scannerAvailability,
                            operatorName = it.operatorName,
                            shift = it.shift,
                            info = "Bundel ${TraceCodec.grouped(saved.code)} tersimpan: $sets set lengkap."
                        )
                    }
                }
                .onFailure { failure -> _uiState.update { it.copy(isBusy = false, error = failure.message) } }
        }
    }

    private fun closeSack() {
        val state = _uiState.value
        val sackCode = state.scan?.code ?: return
        val pcs = state.declaredPcs.trim().toIntOrNull() ?: 0
        if (pcs <= 0) {
            _uiState.update { it.copy(error = "Isi dulu jumlah isi karung.") }
            return
        }
        if (state.selectedBundleCodes.isEmpty()) {
            _uiState.update { it.copy(error = "Pilih dulu bundel yang dituang ke karung ini.") }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isBusy = true, error = null) }
            remoteDataSource.closeSack(
                tenantSlug = tenantSlug,
                sackCode = sackCode,
                bundleCodes = state.selectedBundleCodes.map { TraceCode(it) },
                declaredPcs = pcs,
                weightKg = state.weightKg.trim().toDoubleOrNull() ?: 0.0,
                operatorName = state.operatorName,
                recordedAt = null,
                notes = state.notes
            )
                .onSuccess { result ->
                    _uiState.update {
                        TraceabilityUiState(
                            scannerAvailability = it.scannerAvailability,
                            operatorName = it.operatorName,
                            shift = it.shift,
                            lastSackResult = result,
                            info = sackSummary(result)
                        )
                    }
                }
                .onFailure { failure -> _uiState.update { it.copy(isBusy = false, error = failure.message) } }
        }
    }

    private fun sackSummary(result: com.eventverse.app.infrastructure.api.SackCloseResult): String {
        val base = "Karung ${result.humanCode} ditutup: ${result.declaredPcs} pcs " +
            "dari ${result.consumedBundleCount} bundel."
        return when {
            result.shrinkagePcs > 0 -> "$base Susut ${result.shrinkagePcs} pcs."
            result.shrinkagePcs < 0 -> "$base Isinya ${-result.shrinkagePcs} pcs lebih banyak " +
                "daripada yang tercatat masuk — kemungkinan ada bundel yang belum di-scan."
            else -> "$base Angkanya cocok."
        }
    }
}
