package com.eventverse.app.presentation.costing

import com.eventverse.app.domain.costing.CostingRateCard
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.infrastructure.api.CostingApiClient
import com.eventverse.app.infrastructure.api.CostingBenchmarkApiClient
import com.eventverse.app.infrastructure.api.CostingBenchmarkRemoteDataSource
import com.eventverse.app.infrastructure.api.CostingRemoteDataSource
import com.eventverse.app.presentation.common.PickedFile
import com.eventverse.app.presentation.common.pickFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CostingViewModel(
    private val tenantSlug: String,
    private val decision: AccessDecision? = null,
    private val persona: TestingPersona? = null,
    private val remoteDataSource: CostingRemoteDataSource = CostingApiClient(),
    /**
     * Null = pakai klien HTTP sungguhan, dibangun **malas**.
     *
     * Default eager (`= CostingBenchmarkApiClient()`) akan membangun `HttpClient` di setiap
     * konstruksi ViewModel — termasuk di test yang sudah menyuntikkan [remoteDataSource] palsu
     * dan tidak punya engine Ktor di classpath-nya. Gejalanya `NoClassDefFoundError` yang sama
     * sekali tidak menyebut costing.
     */
    private val benchmarkDataSourceOverride: CostingBenchmarkRemoteDataSource? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    /**
     * Disuntikkan supaya test ViewModel tidak pernah membuka dialog berkas sistem —
     * `pickFile` bawaan platform akan menggantung test JVM pada dialog AWT yang tak pernah
     * ditutup siapa pun.
     */
    private val filePicker: suspend (List<String>) -> PickedFile? = ::pickFile,
    canApproveOverride: Boolean? = null,
    showMarginOverride: Boolean? = null
) {
    private val isOwner = persona?.isOwnerOrSuperAdmin == true
    private val isManage = decision?.config?.level?.isAtLeast(AccessLevel.MANAGE) == true
    private val isOperate = decision?.config?.level?.isAtLeast(AccessLevel.OPERATE) == true
    private val isSalesRole = persona?.roleTitle?.contains("Sales", ignoreCase = true) == true

    val canApprove: Boolean = canApproveOverride ?: (isOwner || isManage)
    val showMargin: Boolean = showMarginOverride ?: (isOwner || isManage || (isSalesRole && decision?.config?.level?.isAtLeast(AccessLevel.VIEW) == true))
    val canWrite: Boolean = isOwner || isManage || isOperate

    private val benchmarkDataSource: CostingBenchmarkRemoteDataSource by lazy {
        benchmarkDataSourceOverride ?: CostingBenchmarkApiClient()
    }

    private val _uiState = MutableStateFlow(
        CostingUiState(
            canApprove = canApprove,
            showMargin = showMargin,
            canWrite = canWrite
        )
    )
    val uiState: StateFlow<CostingUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onEvent(event: CostingUiEvent) {
        when (event) {
            is CostingUiEvent.Load -> load()
            is CostingUiEvent.SelectSheet -> {
                _uiState.update { it.copy(selectedSheet = event.sheet) }
                if (_uiState.value.activeWorkbenchTab == CostingWorkbenchTab.DRIFT) {
                    checkDrift(event.sheet.id.value)
                }
            }
            is CostingUiEvent.SetStatusFilter -> _uiState.update { it.copy(selectedStatusFilter = event.status) }
            is CostingUiEvent.SetSearchQuery -> _uiState.update { it.copy(searchQuery = event.query) }
            is CostingUiEvent.SelectWorkbenchTab -> {
                _uiState.update { it.copy(activeWorkbenchTab = event.tab) }
                if (event.tab == CostingWorkbenchTab.DRIFT && _uiState.value.selectedSheet != null) {
                    checkDrift(_uiState.value.selectedSheet!!.id.value)
                } else if (event.tab == CostingWorkbenchTab.RATE_CARD) {
                    loadRateCard(_uiState.value.rateCardBehavior)
                } else if (event.tab == CostingWorkbenchTab.HISTORICAL_BENCHMARKS) {
                    loadBenchmarks()
                }
            }
            is CostingUiEvent.SelectMobileTab -> _uiState.update { it.copy(activeMobileTab = event.tab) }
            is CostingUiEvent.SelectRateCardBehavior -> {
                _uiState.update { it.copy(rateCardBehavior = event.behavior) }
                loadRateCard(event.behavior)
            }
            is CostingUiEvent.DismissStatusMessage -> _uiState.update { it.copy(statusMessage = null) }
            is CostingUiEvent.OpenCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = true) }
            is CostingUiEvent.CloseCreateDialog -> _uiState.update { it.copy(isCreateDialogOpen = false) }
            is CostingUiEvent.CreateDraft -> createDraft(event.techPackId, event.orderQuantity, event.behavior)
            is CostingUiEvent.Calculate -> calculate(event.sheetId)
            is CostingUiEvent.Reprice -> reprice(event.sheetId)
            is CostingUiEvent.OpenOverrideParamsDialog -> _uiState.update { it.copy(isOverrideParamsDialogOpen = true) }
            is CostingUiEvent.CloseOverrideParamsDialog -> _uiState.update { it.copy(isOverrideParamsDialogOpen = false) }
            is CostingUiEvent.SaveOverrides -> saveOverrides(event.sheetId, event.overrides)
            is CostingUiEvent.SubmitForApproval -> submitForApproval(event.sheetId)
            is CostingUiEvent.Approve -> approve(event.sheetId)
            is CostingUiEvent.OpenRejectDialog -> _uiState.update { it.copy(isRejectDialogOpen = true) }
            is CostingUiEvent.CloseRejectDialog -> _uiState.update { it.copy(isRejectDialogOpen = false) }
            is CostingUiEvent.Reject -> reject(event.sheetId, event.reason)
            is CostingUiEvent.Revise -> revise(event.sheetId)
            is CostingUiEvent.CheckDrift -> checkDrift(event.sheetId)
            is CostingUiEvent.UpdateEstimatorForm -> _uiState.update { it.copy(estimatorForm = event.form) }
            is CostingUiEvent.RunQuickEstimate -> runQuickEstimate()
            is CostingUiEvent.ResetQuickEstimate -> _uiState.update {
                it.copy(
                    estimatorForm = QuickEstimatorFormState(),
                    estimatorResult = null,
                    mockupHints = null,
                    mockupImageUrl = null
                )
            }
            is CostingUiEvent.PickAndAnalyzeMockup -> pickAndAnalyzeMockup()
            is CostingUiEvent.LoadBenchmarks -> loadBenchmarks()
            is CostingUiEvent.SetBenchmarkSearchQuery -> _uiState.update { it.copy(benchmarkSearchQuery = event.query) }
            is CostingUiEvent.PickAndImportWorkbook -> pickAndImportWorkbook()
            is CostingUiEvent.OpenRateCardDialog -> _uiState.update { it.copy(isRateCardDialogOpen = true) }
            is CostingUiEvent.CloseRateCardDialog -> _uiState.update { it.copy(isRateCardDialogOpen = false) }
            is CostingUiEvent.SaveRateCard -> saveRateCard(event.rateCard)
        }
    }

    private fun load() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val sheetsResult = remoteDataSource.getSheets(tenantSlug)
            val telemetryResult = remoteDataSource.getTelemetry(tenantSlug)
            val rateCardResult = remoteDataSource.getActiveRateCard(tenantSlug, _uiState.value.rateCardBehavior)

            _uiState.update { state ->
                val sheets = sheetsResult.getOrDefault(emptyList())
                val currentSelected = state.selectedSheet
                val newSelected = sheets.firstOrNull { it.id == currentSelected?.id }
                    ?: sheets.firstOrNull()

                state.copy(
                    isLoading = false,
                    sheets = sheets,
                    selectedSheet = newSelected,
                    telemetry = telemetryResult.getOrNull(),
                    activeRateCard = rateCardResult.getOrNull()
                )
            }
        }
    }

    private fun loadRateCard(behavior: CostingBehavior) {
        scope.launch {
            val result = remoteDataSource.getActiveRateCard(tenantSlug, behavior)
            _uiState.update { it.copy(activeRateCard = result.getOrNull()) }
        }
    }

    private fun createDraft(techPackId: String, orderQuantity: Long, behavior: CostingBehavior) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.createDraft(tenantSlug, techPackId, orderQuantity, behavior)
                .onSuccess { newSheet ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            isCreateDialogOpen = false,
                            sheets = listOf(newSheet) + state.sheets,
                            selectedSheet = newSheet,
                            statusMessage = "Draft HPP #${newSheet.number.value} berhasil dibuat",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal membuat draft", isErrorMessage = true)
                    }
                }
        }
    }

    private fun calculate(sheetId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.calculate(tenantSlug, sheetId)
                .onSuccess { updated ->
                    updateSheetInState(updated, "HPP #${updated.number.value} berhasil dihitung")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal menghitung HPP", isErrorMessage = true)
                    }
                }
        }
    }

    private fun reprice(sheetId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.reprice(tenantSlug, sheetId)
                .onSuccess { updated ->
                    updateSheetInState(updated, "HPP #${updated.number.value} berhasil dihitung ulang")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal reprice HPP", isErrorMessage = true)
                    }
                }
        }
    }

    private fun saveOverrides(sheetId: String, overrides: Map<String, String>) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.overrideParameters(tenantSlug, sheetId, overrides)
                .onSuccess { updated ->
                    updateSheetInState(updated, "Parameter HPP berhasil disimpan. Hitung ulang untuk menerapkan.", closeDialog = true)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal simpan parameter", isErrorMessage = true)
                    }
                }
        }
    }

    private fun submitForApproval(sheetId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.submitForApproval(tenantSlug, sheetId)
                .onSuccess { updated ->
                    updateSheetInState(updated, "Lembar HPP #${updated.number.value} diajukan ke persetujuan")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal mengajukan HPP", isErrorMessage = true)
                    }
                }
        }
    }

    private fun approve(sheetId: String) {
        if (!canApprove) {
            _uiState.update {
                it.copy(statusMessage = "Akses ditolak: Anda tidak memiliki wewenang APPROVE_COSTING", isErrorMessage = true)
            }
            return
        }
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.approve(tenantSlug, sheetId)
                .onSuccess { updated ->
                    updateSheetInState(updated, "Lembar HPP #${updated.number.value} berhasil disetujui sebagai komitmen komersial")
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal menyetujui HPP", isErrorMessage = true)
                    }
                }
        }
    }

    private fun reject(sheetId: String, reason: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.reject(tenantSlug, sheetId, reason)
                .onSuccess { updated ->
                    updateSheetInState(updated, "Lembar HPP #${updated.number.value} ditolak: $reason", closeDialog = true)
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal menolak HPP", isErrorMessage = true)
                    }
                }
        }
    }

    private fun revise(sheetId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.revise(tenantSlug, sheetId)
                .onSuccess { newDraft ->
                    _uiState.update { state ->
                        state.copy(
                            isLoading = false,
                            sheets = listOf(newDraft) + state.sheets,
                            selectedSheet = newDraft,
                            statusMessage = "Revisi baru #${newDraft.number.value} berhasil dibuat dalam status DRAFT",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal membuat revisi", isErrorMessage = true)
                    }
                }
        }
    }

    // ── Estimator cepat (CS) ─────────────────────────────────────────────────────────────

    private fun runQuickEstimate() {
        val state = _uiState.value
        val input = state.estimatorForm.toInput()
        if (input == null) {
            _uiState.update {
                it.copy(statusMessage = "Isi jumlah pesanan dengan angka lebih dari 0", isErrorMessage = true)
            }
            return
        }

        scope.launch {
            _uiState.update { it.copy(isEstimating = true) }
            benchmarkDataSource.estimateQuick(
                tenantSlug = tenantSlug,
                input = input,
                visionHints = state.mockupHints
            )
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            isEstimating = false,
                            estimatorResult = result,
                            statusMessage = null
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isEstimating = false,
                            estimatorResult = null,
                            statusMessage = error.message ?: "Gagal menghitung estimasi",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun pickAndAnalyzeMockup() {
        scope.launch {
            val picked = filePicker(listOf("png", "jpg", "jpeg", "webp"))
            if (picked == null) {
                _uiState.update { it.copy(statusMessage = PICKER_UNAVAILABLE, isErrorMessage = true) }
                return@launch
            }

            _uiState.update { it.copy(isAnalyzingMockup = true) }
            benchmarkDataSource.analyzeMockup(tenantSlug, picked.fileName, picked.mimeType, picked.bytes)
                .onSuccess { analysis ->
                    _uiState.update { state ->
                        state.copy(
                            isAnalyzingMockup = false,
                            mockupHints = analysis.hints,
                            mockupImageUrl = analysis.mockupImageUrl,
                            // Jumlah kancing hasil pembacaan AI hanya MENGISI AWAL kolom yang masih
                            // kosong. Menimpa isian CS berarti angka penawaran berubah diam-diam
                            // setelah ia mengetiknya sendiri.
                            estimatorForm = state.estimatorForm.copy(
                                buttonCountText = analysis.hints.detectedButtonCount
                                    ?.takeIf { state.estimatorForm.buttonCount == 0 }
                                    ?.toString()
                                    ?: state.estimatorForm.buttonCountText
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAnalyzingMockup = false,
                            statusMessage = error.message ?: "Gagal menganalisis gambar",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    // ── Knowledge Base arsip historis ────────────────────────────────────────────────────

    private fun loadBenchmarks() {
        scope.launch {
            _uiState.update { it.copy(isLoadingBenchmarks = true) }
            benchmarkDataSource.listBenchmarks(tenantSlug)
                .onSuccess { list ->
                    _uiState.update { it.copy(isLoadingBenchmarks = false, benchmarks = list) }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoadingBenchmarks = false,
                            statusMessage = error.message ?: "Gagal memuat arsip produk",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun pickAndImportWorkbook() {
        if (!canWrite) {
            _uiState.update {
                it.copy(statusMessage = "Akses ditolak: impor arsip butuh wewenang kelola HPP", isErrorMessage = true)
            }
            return
        }

        scope.launch {
            val picked = filePicker(listOf("xlsx"))
            if (picked == null) {
                _uiState.update { it.copy(statusMessage = PICKER_UNAVAILABLE, isErrorMessage = true) }
                return@launch
            }

            _uiState.update { it.copy(isImportingWorkbook = true) }
            benchmarkDataSource.importWorkbook(tenantSlug, picked.fileName, picked.bytes)
                .onSuccess { report ->
                    _uiState.update { state ->
                        state.copy(
                            isImportingWorkbook = false,
                            // Sisipkan di depan alih-alih memuat ulang seluruh daftar: operator
                            // biasanya mengimpor beberapa berkas berturut-turut, dan setiap
                            // muat-ulang penuh menggulirkan tabelnya kembali ke atas.
                            benchmarks = report.imported + state.benchmarks,
                            statusMessage = buildImportMessage(report.importedCount, report.skipped),
                            isErrorMessage = report.importedCount == 0
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isImportingWorkbook = false,
                            statusMessage = error.message ?: "Gagal mengimpor berkas",
                            isErrorMessage = true
                        )
                    }
                }
        }
    }

    private fun buildImportMessage(
        importedCount: Int,
        skipped: List<com.eventverse.app.infrastructure.api.BenchmarkImportResult.Skipped>
    ): String = when {
        importedCount > 0 -> "$importedCount artikel berhasil masuk ke Knowledge Base"
        skipped.isNotEmpty() -> "Berkas dilewati: ${skipped.first().reason}"
        else -> "Tidak ada artikel yang dapat dibaca dari berkas ini"
    }

    private fun checkDrift(sheetId: String) {
        // Implementasi drift check
    }

    private fun saveRateCard(card: CostingRateCard) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true) }
            remoteDataSource.updateRateCard(tenantSlug, card)
                .onSuccess { updated ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            activeRateCard = updated,
                            isRateCardDialogOpen = false,
                            statusMessage = "Rate card v${updated.version} berhasil diperbarui",
                            isErrorMessage = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(isLoading = false, statusMessage = error.message ?: "Gagal menyimpan rate card", isErrorMessage = true)
                    }
                }
        }
    }

    private fun updateSheetInState(updated: CostingSheet, message: String, closeDialog: Boolean = false) {
        _uiState.update { state ->
            val newSheets = state.sheets.map { if (it.id == updated.id) updated else it }
            state.copy(
                isLoading = false,
                sheets = newSheets,
                selectedSheet = updated,
                statusMessage = message,
                isErrorMessage = false,
                isOverrideParamsDialogOpen = if (closeDialog) false else state.isOverrideParamsDialogOpen,
                isRejectDialogOpen = if (closeDialog) false else state.isRejectDialogOpen
            )
        }
    }

    private companion object {
        const val PICKER_UNAVAILABLE =
            "Pemilih berkas belum tersedia di platform ini. Untuk impor massal, jalankan " +
                "./gradlew :server:importHistoricalCosting --args=\"--dir=<folder> --tenant=<id>\""
    }
}
