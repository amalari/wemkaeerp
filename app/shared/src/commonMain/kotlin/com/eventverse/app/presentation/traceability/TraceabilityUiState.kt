package com.eventverse.app.presentation.traceability

import com.eventverse.app.domain.contracts.GarmentPanel
import com.eventverse.app.domain.traceability.*
import com.eventverse.app.infrastructure.api.SackCloseResult
import com.eventverse.app.infrastructure.api.TraceScanView

/** Langkah yang sedang dikerjakan di layar telusur. */
enum class TraceScanStep {
    IDLE,
    BUNDLE_TALLY,
    SACK_CLOSE
}

/** Satu baris hitungan panel di form bundel. */
data class PanelTallyInput(
    val panel: GarmentPanel,
    val piecesPerGarment: Int,
    val rawValue: String = ""
) {
    val pieces: Int get() = rawValue.trim().toIntOrNull() ?: 0
    val label: String
        get() = if (piecesPerGarment > 1) "${panel.displayName} (x$piecesPerGarment)" else panel.displayName
}

data class TraceabilityUiState(
    val manualCode: String = "",
    val isBusy: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val scannerAvailability: TraceScannerAvailability = TraceScannerAvailability.NOT_ON_THIS_PLATFORM,
    val step: TraceScanStep = TraceScanStep.IDLE,
    val scan: TraceScanView? = null,

    // Form bundel
    val tallies: List<PanelTallyInput> = emptyList(),
    val operatorName: String = "",
    val shift: String = "",
    val notes: String = "",

    // Form karung
    val availableBundles: List<TraceContainer> = emptyList(),
    val selectedBundleCodes: Set<String> = emptySet(),
    val declaredPcs: String = "",
    val weightKg: String = "",
    val lastSackResult: SackCloseResult? = null,

    val reconciliation: TraceReconciliation? = null
) {
    val requirements: List<PanelRequirement>
        get() = tallies.map { PanelRequirement(it.panel, it.piecesPerGarment) }

    /**
     * Pratinjau jumlah set lengkap, dihitung langsung dari hitungan yang sedang diketik.
     *
     * Ditampilkan sambil mengetik, bukan setelah menyimpan, karena inilah momen operator bisa
     * mengoreksi: "5 depan, 4 belakang" yang menghasilkan 4 set akan segera terlihat salah kalau
     * dia memang mengikat 5.
     */
    val previewCompleteSets: Int
        get() {
            if (tallies.isEmpty()) return 0
            return tallies.minOf { it.pieces / it.piecesPerGarment.coerceAtLeast(1) }
        }

    val previewLeftover: List<PanelTallyInput>
        get() {
            val sets = previewCompleteSets
            return tallies.filter { it.pieces - sets * it.piecesPerGarment > 0 }
        }

    val manualCodeIsValid: Boolean
        get() = TraceCodec.parse(manualCode) != null

    val selectedBundleSets: Int
        get() = availableBundles
            .filter { it.code.value in selectedBundleCodes }
            .sumOf { it.completeSets(requirements) }

    /** Selisih yang langsung terlihat sebelum karung ditutup, bukan setelahnya. */
    val previewShrinkage: Int
        get() = selectedBundleSets - (declaredPcs.trim().toIntOrNull() ?: 0)
}

sealed interface TraceabilityUiEvent {
    data class UpdateManualCode(val value: String) : TraceabilityUiEvent
    data object SubmitManualCode : TraceabilityUiEvent
    data object ScanWithCamera : TraceabilityUiEvent
    data object Reset : TraceabilityUiEvent
    data object DismissMessage : TraceabilityUiEvent

    data class UpdateTally(val panel: GarmentPanel, val value: String) : TraceabilityUiEvent
    data class UpdateOperator(val value: String) : TraceabilityUiEvent
    data class UpdateShift(val value: String) : TraceabilityUiEvent
    data class UpdateNotes(val value: String) : TraceabilityUiEvent
    data object SaveTally : TraceabilityUiEvent

    data class ToggleBundle(val code: String) : TraceabilityUiEvent
    data class UpdateDeclaredPcs(val value: String) : TraceabilityUiEvent
    data class UpdateWeightKg(val value: String) : TraceabilityUiEvent
    data object CloseSack : TraceabilityUiEvent
}
