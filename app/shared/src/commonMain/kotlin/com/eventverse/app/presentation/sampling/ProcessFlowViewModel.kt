package com.eventverse.app.presentation.sampling

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.FlowPhase
import com.eventverse.app.domain.process.PhaseTaggableStage
import com.eventverse.app.domain.process.StagePhaseTags
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.transfer.FlowNodeRef
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.domain.workqueue.WorkStationCode
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.infrastructure.api.OrderFlowDto
import com.eventverse.app.infrastructure.api.ProcessCatalogApiClient
import com.eventverse.app.infrastructure.api.ProcessCatalogRemoteDataSource
import com.eventverse.app.infrastructure.api.StoredTenantSlugProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Lingkup target alur proses yang sedang dilihat atau diatur di panel Adjust Flow. */
sealed interface ProcessFlowScope {
    /** Alur default / template bawaan pabrik untuk tenant. */
    data object DefaultTenant : ProcessFlowScope

    /** Alur spesifik untuk desain / SPK tertentu. */
    data class Design(
        val orderId: String,
        val styleName: String,
        val spkNumber: String
    ) : ProcessFlowScope
}

data class SamplingOrderScopeItem(
    val orderId: String,
    val styleName: String,
    val spkNumber: String,
    val isCustomFlow: Boolean = false
)

/** State UI panel "Adjust Flow" — daftar proses opsional tenant + posisi jangkarnya. */
data class ProcessFlowUiState(
    val scope: ProcessFlowScope = ProcessFlowScope.DefaultTenant,
    val isCustomFlow: Boolean = false,
    val processes: List<TenantOptionalProcess> = emptyList(),
    /**
     * Perpindahan barang yang tersirat di alur ini. Kosong untuk pabrik satu atap tanpa
     * makloon — dan itu kasus yang paling sering, jadi panel harus terlihat persis seperti
     * sebelum fitur ini ada ketika daftarnya kosong.
     */
    val legs: List<FlowLegView> = emptyList(),
    /** Tag `[Sampling ×] [Produksi ×]` pada Cuci & Setrika — efektif untuk lingkup ini. */
    val phaseTags: StagePhaseTags = StagePhaseTags.DEFAULT,
    /** Desain punya tag sendiri (bukan warisan template) — ikut memunculkan tombol reset. */
    val hasCustomPhaseTags: Boolean = false,
    val availableOrders: List<SamplingOrderScopeItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
) {
    /**
     * Leg yang berangkat dari sebuah simpul — inilah yang digambar di celah setelahnya.
     *
     * Dikunci pada simpul asal, bukan tujuan, karena celah di panel memang berada sesudah
     * chip asalnya.
     */
    fun legsLeaving(node: FlowNodeRef): List<FlowLegView> = legs.filter { it.leg.fromNode == node }
}

sealed interface ProcessFlowUiEvent {
    data object Load : ProcessFlowUiEvent
    data class SetAvailableOrders(val orders: List<SamplingOrderScopeItem>) : ProcessFlowUiEvent
    data class SelectScope(val scope: ProcessFlowScope) : ProcessFlowUiEvent

    /**
     * Sisipkan proses baru (dari palet) di celah setelah tahap [anchorAfter].
     *
     * [executionMode] dan [vendorRef] adalah *di mana* proses itu dikerjakan. Templat di
     * `WorkStationCatalog` tidak bisa memutuskannya — Bordir Komputer bisa in-house di satu
     * pabrik dan makloon di pabrik lain — jadi pemanggil yang menentukannya. Default
     * `IN_HOUSE` menjaga perilaku lama untuk pemanggil yang belum menanyakannya.
     */
    data class InsertProcess(
        val code: String,
        val displayName: String,
        val anchorAfter: SamplingPipelineStage,
        val executionMode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
        val vendorRef: String? = null
    ) : ProcessFlowUiEvent

    /** Pindahkan posisi proses (drag-and-drop antar celah). */
    data class MoveProcess(
        val processId: String,
        val newAnchorAfter: SamplingPipelineStage
    ) : ProcessFlowUiEvent

    /** Keluarkan proses dari flow (tombol x pada chip). */
    data class RemoveProcess(val processId: String) : ProcessFlowUiEvent

    /** Tombol × pada tag fase, atau tag hantu "+ Sampling" untuk mengembalikannya. */
    data class TogglePhaseTag(val stage: PhaseTaggableStage, val phase: FlowPhase) : ProcessFlowUiEvent

    /** Reset alur desain yang sedang aktif kembali ke alur default pabrik. */
    data object ResetToDefault : ProcessFlowUiEvent
}

/**
 * State holder panel Adjust Flow — mendukung dua tingkat pengaturan:
 * 1. Default Flow Pabrik (Template bawaan tenant)
 * 2. Per-Design Flow (Khusus untuk SPK / Desain tertentu)
 */
class ProcessFlowViewModel(
    private val remote: ProcessCatalogRemoteDataSource = ProcessCatalogApiClient(),
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(ProcessFlowUiState())
    val uiState: StateFlow<ProcessFlowUiState> = _uiState.asStateFlow()

    /**
     * Tenant untuk membangun [TenantOptionalProcess] di sisi klien pada jalur per-desain.
     *
     * Server tetap menimpanya dengan tenant dari sesi saat decode
     * (`ProcessCatalogCodec.decodeProcesses(items, tenant.tenantId)`), jadi nilai ini tidak
     * pernah menjadi sumber kebenaran. Tapi ia harus sah: [TenantId] menolak string kosong,
     * dan konstruktor entity dievaluasi sebelum request dikirim — sebelumnya di sini ada
     * `TenantId("")` yang membuat penyisipan proses di alur per-desain selalu melempar.
     */
    private val clientTenantId: TenantId
        get() = TenantId(StoredTenantSlugProvider.currentTenantSlug() ?: "demo-tenant")

    init {
        loadCurrentScope()
    }

    fun onEvent(event: ProcessFlowUiEvent) {
        when (event) {
            is ProcessFlowUiEvent.Load -> loadCurrentScope()
            is ProcessFlowUiEvent.SetAvailableOrders -> {
                _uiState.update { it.copy(availableOrders = event.orders) }
            }
            is ProcessFlowUiEvent.SelectScope -> {
                _uiState.update { it.copy(scope = event.scope) }
                loadCurrentScope()
            }
            is ProcessFlowUiEvent.InsertProcess -> handleInsert(event)
            is ProcessFlowUiEvent.MoveProcess -> handleMove(event)
            is ProcessFlowUiEvent.RemoveProcess -> handleRemove(event)
            is ProcessFlowUiEvent.TogglePhaseTag -> handleTogglePhaseTag(event)
            is ProcessFlowUiEvent.ResetToDefault -> handleReset()
        }
    }

    private fun loadCurrentScope() {
        val currentScope = _uiState.value.scope
        scope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            when (currentScope) {
                is ProcessFlowScope.DefaultTenant -> {
                    val tags = remote.fetchTenantPhaseTags().getOrDefault(StagePhaseTags.DEFAULT)
                    remote.fetchCatalog()
                        .onSuccess { processes ->
                            _uiState.update {
                                it.copy(processes = processes, isCustomFlow = false, phaseTags = tags, hasCustomPhaseTags = false, isLoading = false)
                            }
                        }
                        .onFailure { setError(it) }
                }
                is ProcessFlowScope.Design -> {
                    remote.fetchOrderFlow(currentScope.orderId)
                        .onSuccess { dto ->
                            _uiState.update {
                                it.withFlow(dto)
                            }
                            refreshLegs(currentScope.orderId)
                        }
                        .onFailure { setError(it) }
                }
            }
        }
    }

    private fun handleInsert(event: ProcessFlowUiEvent.InsertProcess) {
        val currentScope = _uiState.value.scope
        scope.launch {
            setBusy()
            when (currentScope) {
                is ProcessFlowScope.DefaultTenant -> {
                    val template = templateFor(event.code)
                    remote.addProcess(
                        code = event.code,
                        displayName = event.displayName,
                        anchorAfter = event.anchorAfter,
                        executionMode = event.executionMode,
                        vendorRef = event.vendorRef,
                        piecerateTariffIdr = template?.piecerateTariffIdr ?: 0L,
                        standardMinutesPerPiece = template?.standardMinutesPerPiece ?: 0.0
                    ).onSuccess { loadCurrentScope() }
                        .onFailure { setError(it) }
                }
                is ProcessFlowScope.Design -> {
                    val template = templateFor(event.code)
                    val newProc = TenantOptionalProcess(
                        processId = "proc-${event.code.lowercase()}",
                        tenantId = clientTenantId,
                        code = event.code,
                        displayName = event.displayName,
                        // Archetype asli templat, bukan CUSTOM_EXTENSION untuk semuanya:
                        // Laundry misalnya ber-archetype FINISHING, dan menyeragamkannya
                        // membuat proses itu tidak lagi sepadan dengan slot finishing.
                        archetype = template?.archetype ?: ModuleArchetype.CUSTOM_EXTENSION,
                        samplingAnchorAfter = event.anchorAfter,
                        executionMode = event.executionMode,
                        vendorRef = event.vendorRef,
                        piecerateTariffIdr = template?.piecerateTariffIdr ?: 0L,
                        standardMinutesPerPiece = template?.standardMinutesPerPiece ?: 0.0
                    )
                    val updatedList = _uiState.value.processes.filterNot { it.code == event.code } + newProc
                    remote.saveOrderFlow(currentScope.orderId, updatedList)
                        .onSuccess { dto ->
                            _uiState.update {
                                it.withFlow(dto)
                            }
                            refreshLegs(currentScope.orderId)
                        }
                        .onFailure { setError(it) }
                }
            }
        }
    }

    private fun handleMove(event: ProcessFlowUiEvent.MoveProcess) {
        val currentScope = _uiState.value.scope
        scope.launch {
            setBusy()
            when (currentScope) {
                is ProcessFlowScope.DefaultTenant -> {
                    remote.repositionProcess(event.processId, event.newAnchorAfter)
                        .onSuccess { loadCurrentScope() }
                        .onFailure { setError(it) }
                }
                is ProcessFlowScope.Design -> {
                    val updatedList = _uiState.value.processes.map { proc ->
                        if (proc.processId == event.processId) proc.copy(samplingAnchorAfter = event.newAnchorAfter) else proc
                    }
                    remote.saveOrderFlow(currentScope.orderId, updatedList)
                        .onSuccess { dto ->
                            _uiState.update {
                                it.withFlow(dto)
                            }
                            refreshLegs(currentScope.orderId)
                        }
                        .onFailure { setError(it) }
                }
            }
        }
    }

    private fun handleRemove(event: ProcessFlowUiEvent.RemoveProcess) {
        val currentScope = _uiState.value.scope
        scope.launch {
            setBusy()
            when (currentScope) {
                is ProcessFlowScope.DefaultTenant -> {
                    remote.removeProcess(event.processId)
                        .onSuccess { loadCurrentScope() }
                        .onFailure { setError(it) }
                }
                is ProcessFlowScope.Design -> {
                    val updatedList = _uiState.value.processes.filterNot { it.processId == event.processId }
                    remote.saveOrderFlow(currentScope.orderId, updatedList)
                        .onSuccess { dto ->
                            _uiState.update {
                                it.withFlow(dto)
                            }
                            refreshLegs(currentScope.orderId)
                        }
                        .onFailure { setError(it) }
                }
            }
        }
    }

    private fun handleTogglePhaseTag(event: ProcessFlowUiEvent.TogglePhaseTag) {
        val currentScope = _uiState.value.scope
        val proposed = _uiState.value.phaseTags.toggled(event.stage, event.phase)
        scope.launch {
            setBusy()
            when (currentScope) {
                is ProcessFlowScope.DefaultTenant -> remote.saveTenantPhaseTags(proposed)
                    .onSuccess { saved -> _uiState.update { it.copy(phaseTags = saved, isLoading = false) } }
                    .onFailure { setError(it) }
                is ProcessFlowScope.Design -> remote.saveOrderPhaseTags(currentScope.orderId, proposed)
                    .onSuccess { dto ->
                        _uiState.update { it.withFlow(dto) }
                        // Tahap yang dilompati tidak lagi punya leg masuk — konektornya ikut berubah.
                        refreshLegs(currentScope.orderId)
                    }
                    .onFailure { setError(it) }
            }
        }
    }

    private fun handleReset() {
        val currentScope = _uiState.value.scope as? ProcessFlowScope.Design ?: return
        scope.launch {
            setBusy()
            remote.resetOrderFlow(currentScope.orderId)
                .onSuccess { dto ->
                    _uiState.update {
                        it.withFlow(dto)
                    }
                    refreshLegs(currentScope.orderId)
                }
                .onFailure { setError(it) }
        }
    }

    /**
     * Templat stasiun opsional untuk sebuah kode proses, sumber archetype dan tarif bawaan.
     * `null` untuk proses yang tenant definisikan sendiri di luar katalog bawaan.
     */
    private fun templateFor(code: String): WorkStationSpec? =
        WorkStationCatalog.optionalStations().firstOrNull { it.code == WorkStationCode(code) }

    /**
     * Memuat ulang konektor setelah alur berubah.
     *
     * Kegagalannya sengaja tidak dinaikkan menjadi error layar: alurnya sendiri sudah tersimpan,
     * dan menampilkan pesan merah untuk konektor yang gagal dimuat akan membuat operator mengira
     * pekerjaannya batal. Konektor hilang sementara lebih jujur daripada kesalahan palsu.
     */
    private suspend fun refreshLegs(orderId: String) {
        remote.fetchFlowLegs(orderId)
            .onSuccess { board -> _uiState.update { it.copy(legs = board.legs) } }
            .onFailure { _uiState.update { state -> state.copy(legs = emptyList()) } }
    }

    private fun ProcessFlowUiState.withFlow(dto: OrderFlowDto) = copy(
        processes = dto.processes,
        isCustomFlow = dto.isCustomFlow,
        phaseTags = dto.stagePhaseTags,
        hasCustomPhaseTags = dto.hasCustomPhaseTags,
        isLoading = false
    )

    private fun setBusy() = _uiState.update { it.copy(isLoading = true, error = null) }

    private fun setError(throwable: Throwable) =
        _uiState.update { it.copy(isLoading = false, error = throwable.message ?: "Terjadi kesalahan") }
}