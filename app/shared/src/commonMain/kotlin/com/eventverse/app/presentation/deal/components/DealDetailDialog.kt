package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.text.TextStyle
import com.eventverse.app.domain.sampling.STANDARD_SAMPLING_SIZE_COLUMNS
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.defaultSamplingSizeMatrix
import com.eventverse.app.domain.sampling.ensureSamplingQtyRow
import com.eventverse.app.domain.sampling.resolveGarmentTimeline
import com.eventverse.app.domain.sampling.sanitizeSamplingMatrix
import com.eventverse.app.domain.sampling.isSizeColumnActive
import com.eventverse.app.domain.sampling.hasAtLeastOneCompleteMeasurementColumn
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isQtyRow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.TimeZone
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.domain.sampling.GarmentStepState
import com.eventverse.app.domain.sampling.GarmentTrackingStep
import com.eventverse.app.domain.sampling.MilestoneStep
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.FinishingPath
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.deal.DealDetailTab
import com.eventverse.app.presentation.deal.DealUiEvent
import com.eventverse.app.presentation.deal.DealUiState
import com.eventverse.app.presentation.deal.DealViewModel
import com.eventverse.app.presentation.invoicing.InvoicePrefillCoordinator
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.deal.MOCKUP_IMAGE_ACCEPT
import com.eventverse.app.presentation.deal.PickedLocalFile
import com.eventverse.app.presentation.deal.pickLocalFile
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Popup modal BESAR detail Deal (~92vw / maks 1150dp, tinggi 88vh) bergaya clay:
 * dual-tab Siklus Sampling vs Produksi Massal & PO dengan gerbang Golden Sample Lock —
 * Tab Produksi terkunci sampai seluruh desain sampling berstatus ACC.
 *
 * Meng-host [DealViewModel]-nya sendiri supaya bisa dibuka dari pane mana pun.
 */
@Composable
fun DealDetailDialog(
    tenantSlug: String,
    dealId: String? = null,
    sourceLeadId: String? = null,
    onDismiss: () -> Unit
) {
    val viewModel = remember(tenantSlug, dealId, sourceLeadId) {
        DealViewModel(tenantSlug = tenantSlug, dealId = dealId, sourceLeadId = sourceLeadId)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.onEvent(DealUiEvent.Load) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 1150.dp)
                .fillMaxHeight(0.88f),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            when {
                state.isLoading -> Text(
                    text = "Memuat deal...",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                state.deal == null -> Column {
                    Text(
                        text = "Deal belum tersedia",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = state.statusMessage ?: "Deal tidak ditemukan.",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                else -> DealDetailContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DealDetailContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val deal = state.deal ?: return

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header: judul deal + badge stage + tombol tutup ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deal.title.value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(ClaySpacing.Md))
                ClayBadge(text = deal.stage.displayName, tint = deal.stage.tint())
                state.contactName?.let { name ->
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayTag(text = name, tint = WeMadeColors.OnSurfaceMuted)
                }
            }
            ClayIconButton(onClick = onDismiss) {
                IconClose(Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Tab switcher: Siklus Sampling vs Produksi Massal (gerbang gembok) ─
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            DealTabButton(
                tab = DealDetailTab.SAMPLING,
                state = state,
                icon = { IconClipboard(Modifier.size(14.dp)) },
                onEvent = onEvent
            )
            DealTabButton(
                tab = DealDetailTab.MASS_PRODUCTION,
                state = state,
                icon = {
                    if (state.productionUnlocked) {
                        IconPackage(Modifier.size(14.dp))
                    } else {
                        IconLock(Modifier.size(14.dp))
                    }
                },
                onEvent = onEvent
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Konten tab (scrollable) ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.activeTab) {
                DealDetailTab.SAMPLING -> SamplingTabContent(state = state, onEvent = onEvent)
                DealDetailTab.MASS_PRODUCTION -> MassProductionTabContent(state = state, onEvent = onEvent)
            }
        }

        state.statusMessage?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Success)
        }
        state.error?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Error)
        }
    }
}

@Composable
private fun DealTabButton(
    tab: DealDetailTab,
    state: DealUiState,
    icon: @Composable () -> Unit,
    onEvent: (DealUiEvent) -> Unit
) {
    val isSelected = state.activeTab == tab
    val locked = tab == DealDetailTab.MASS_PRODUCTION && !state.productionUnlocked
    val prefix = if (tab == DealDetailTab.SAMPLING) "Tab 1" else "Tab 2"
    val label = if (tab == DealDetailTab.SAMPLING) {
        "$prefix: ${tab.label} (${state.samplingOrders.size} Desain)"
    } else {
        "$prefix: ${tab.label}"
    }
    val tint = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted

    Column(
        modifier = Modifier
            .width(IntrinsicSize.Max)
            .clickable { onEvent(DealUiEvent.SelectDealTab(tab)) }
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            icon()
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
            if (locked) {
                ClayBadge(
                    text = "Menunggu ${state.activeDesigns.size} Desain ACC",
                    tint = WeMadeColors.OnSurfaceMuted,
                    fontSize = 10.sp,
                    leading = { IconLock(Modifier.size(11.dp)) }
                )
            }
        }
        Spacer(Modifier.height(ClaySpacing.Sm))
        // Garis bawah tab: satu-satunya penanda tab aktif (mockup), non-aktif dibiarkan kosong
        // sehingga lebarnya tidak melompat saat berpindah tab.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(if (isSelected) WeMadeColors.Primary else Color.Transparent, ClayShapes.Pill)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: Siklus Sampling — grid accordion multi-desain (mockup-aligned)
// ─────────────────────────────────────────────────────────────────────────────

/** Kode desain tampilan (`DSG-01`, `DSG-02`, …) diturunkan dari urutan desain dalam deal. */
private fun designCodeOf(orders: List<SamplingOrder>, order: SamplingOrder): String {
    val index = orders.indexOfFirst { it.id == order.id }
    val number = if (index >= 0) index + 1 else orders.size
    return "DSG-" + number.toString().padStart(2, '0')
}

/**
 * Kode awal desain baru — autogenerate begitu tombol "Tambah Desain Baru" diklik, tanpa form
 * nama. Admin bisa me-rename lewat field "Nama Desain" di dalam kartunya.
 */
private fun nextDesignCode(orders: List<SamplingOrder>): String =
    "DSG-" + (orders.size + 1).toString().padStart(2, '0')

@Composable
private fun SamplingTabContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit
) {
    // State expand diangkat ke sini supaya layout tahu kartu mana yang perlu satu baris penuh.
    // Nilai default tetap mengikuti status kartu (aktif = terbuka); override hanya terisi
    // setelah admin menekan chevron.
    var expandedOverride by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                state.samplingOrders.isEmpty() ->
                    "Belum ada desain sampling. Tambahkan desain pertama di bawah."
                state.productionUnlocked ->
                    "Semua desain sudah di-ACC — Tab Produksi Massal terbuka."
                else ->
                    "${state.approvedDesigns.size} dari ${state.samplingOrders.size} Desain sudah di-ACC"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(ClaySpacing.Lg))

        // Layout Masonry 2 Kolom: Kolom kiri dan kanan mengalir secara independen sehingga
        // kartu di bawah kartu yang di-collapse tidak tertinggal jauh ("ompong") mengikuti
        // tinggi kartu tetangganya yang sedang terbuka.
        val leftColumnOrders = state.samplingOrders.filterIndexed { index, _ -> index % 2 == 0 }
        val rightColumnOrders = state.samplingOrders.filterIndexed { index, _ -> index % 2 == 1 }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                leftColumnOrders.forEach { order ->
                    val isExpanded = expandedOverride[order.id.value] ?: order.isActiveDesign
                    SamplingDesignCard(
                        order = order,
                        designCode = designCodeOf(state.samplingOrders, order),
                        expanded = isExpanded,
                        onToggleExpanded = {
                            expandedOverride = expandedOverride + (order.id.value to !isExpanded)
                        },
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                rightColumnOrders.forEach { order ->
                    val isExpanded = expandedOverride[order.id.value] ?: order.isActiveDesign
                    SamplingDesignCard(
                        order = order,
                        designCode = designCodeOf(state.samplingOrders, order),
                        expanded = isExpanded,
                        onToggleExpanded = {
                            expandedOverride = expandedOverride + (order.id.value to !isExpanded)
                        },
                        onEvent = onEvent,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        Spacer(Modifier.height(ClaySpacing.Lg))

        // Tambah desain TANPA form: kartu langsung dibuat dengan kode autogenerate
        // (DSG-01, DSG-02, …) — nama bisa direname admin lewat field di dalam kartu.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ClayButton(
                text = if (state.samplingOrders.isEmpty()) "Tambah Desain Sampling" else "Tambah Desain Baru",
                onClick = {
                    onEvent(
                        DealUiEvent.SaveSamplingOrder(
                            samplingOrderId = null,
                            styleName = nextDesignCode(state.samplingOrders),
                            sampleQuantity = 2,
                            courierTracking = null,
                            samplingFeeIdr = 0L,
                            notes = ""
                        )
                    )
                },
                style = ClayButtonStyle.Secondary,
                leading = { IconPlus(Modifier.size(13.dp), color = WeMadeColors.Primary) }
            )
        }
    }
}

@Composable
private fun SamplingDesignCard(
    order: SamplingOrder,
    designCode: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onEvent: (DealUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // Rename inline di header: tombol pensil → edit → autosave begitu edit ditutup.
    var isRenaming by remember(order.id) { mutableStateOf(false) }
    var styleNameInput by remember(order.id) { mutableStateOf(order.styleName) }
    // Sampling fee, catatan & size matrix: autosave 800ms setelah berhenti mengetik.
    // Fee default 0 dan TIDAK BISA dikosongkan — field selalu menampilkan minimal "0".
    var feeInput by remember(order.id) {
        mutableStateOf(order.samplingFeeIdr.toString())
    }
    var notesInput by remember(order.id) { mutableStateOf(order.notes) }
    var sizeMatrixInput by remember(order.id, order.sizeMatrix) {
        mutableStateOf(ensureSamplingQtyRow(order.sizeMatrix))
    }
    var detailTouched by remember(order.id) { mutableStateOf(false) }
    var deadlineInput by remember(order.id, order.deadlineDelivery) {
        mutableStateOf(order.deadlineDelivery?.toString() ?: "")
    }
    var isRevisionDialogOpen by remember(order.id) { mutableStateOf(false) }
    var isRevisionDropdownOpen by remember(order.id) { mutableStateOf(false) }
    var isConfirmSpkDialogOpen by remember(order.id) { mutableStateOf(false) }
    var showValidationErrors by remember(order.id) { mutableStateOf(false) }
    // Selector revisi: revisi mana yang feedback-nya sedang ditampilkan. Default = terbaru;
    // admin bisa memilih Rev 0, Rev 1, Rev 2 lewat dropdown menu melayang.
    var selectedRevision by remember(order.id) { mutableStateOf(order.revisionCount) }
    LaunchedEffect(order.revisionCount) { selectedRevision = order.revisionCount }

    // Mode revisi lampau (arsip / read-only) vs revisi aktif (terakhir)
    val isHistoricRevision = selectedRevision < order.revisionCount
    val historicSnapshot = remember(selectedRevision, order.revisionHistory) {
        if (isHistoricRevision) order.snapshotFor(selectedRevision) else null
    }

    // Berkas foto yang baru dipilih dan MENUNGGU dipotong kotak (1:1) di cropper.
    var pendingCropPick by remember(order.id) { mutableStateOf<PickedLocalFile?>(null) }
    var pendingCropSlot by remember(order.id) { mutableStateOf("front") }
    val cardScope = rememberCoroutineScope()

    /** Feedback untuk nomor revisi [revision]; fallback data lama (tanpa history) = accNotes. */
    fun feedbackFor(revision: Int): String? = when {
        revision <= 0 -> null
        revision == order.revisionCount && order.revisionHistory.none { it.revision == revision } ->
            order.accNotes.takeIf { it.isNotBlank() }
        else -> order.revisionFeedback(revision)?.notes?.takeIf { it.isNotBlank() }
    }

    fun commitRename() {
        val newName = styleNameInput.trim()
        if (newName.isBlank()) {
            styleNameInput = order.styleName
        } else if (newName != order.styleName) {
            val totalQty = calculateTotalSampleQuantity(sizeMatrixInput, order.sampleQuantity)
            val parsedDeadline = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(deadlineInput.trim()) ?: order.deadlineDelivery
            onEvent(
                DealUiEvent.SaveSamplingOrder(
                    samplingOrderId = order.id.value,
                    styleName = newName,
                    sampleQuantity = totalQty,
                    courierTracking = null,
                    samplingFeeIdr = feeInput.toLongOrNull() ?: 0L,
                    notes = notesInput,
                    sizeMatrix = sizeMatrixInput,
                    deadlineDelivery = parsedDeadline
                )
            )
        }
        isRenaming = false
    }

    // Garis batas mode: Draft (Step 1 - masih isi formulir) vs Produksi/Arsip (Step 2-8 - Spek Terkunci)
    val isDraft = order.status == SamplingStatus.DRAFT || order.pipelineStage == SamplingPipelineStage.NEW_INTAKE
    val isFormReadOnly = !isDraft || isHistoricRevision
    val navigator = LocalAppNavigator.current

    LaunchedEffect(feeInput, notesInput, sizeMatrixInput, deadlineInput) {
        if (!detailTouched || !isDraft) {
            if (!detailTouched) detailTouched = true
            return@LaunchedEffect
        }
        delay(800)
        val calculatedQty = calculateTotalSampleQuantity(sizeMatrixInput)
        val totalQty = if (calculatedQty > 0) calculatedQty else order.sampleQuantity
        val parsedDeadline = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(deadlineInput.trim())
        onEvent(
            DealUiEvent.SaveSamplingOrder(
                samplingOrderId = order.id.value,
                styleName = styleNameInput,
                sampleQuantity = totalQty,
                courierTracking = null,
                samplingFeeIdr = feeInput.toLongOrNull() ?: 0L,
                notes = notesInput,
                sizeMatrix = sizeMatrixInput,
                deadlineDelivery = parsedDeadline
            )
        )
    }

    // Dua foto mockup (Tampak Depan & Tampak Belakang): me-load snapshot jika melihat arsip
    val frontRef = if (isHistoricRevision) {
        historicSnapshot?.mockupFrontKey?.takeIf { it.startsWith("http") || it.startsWith("data:") }
    } else {
        order.mockupFrontKey?.takeIf { it.startsWith("http") || it.startsWith("data:") }
    }
    val backRef = if (isHistoricRevision) {
        historicSnapshot?.mockupBackKey?.takeIf { it.startsWith("http") || it.startsWith("data:") }
    } else {
        order.mockupBackKey?.takeIf { it.startsWith("http") || it.startsWith("data:") }
    }
    val frontBitmap = rememberMockupBitmap(frontRef)
    val backBitmap = rememberMockupBitmap(backRef)

    val displayedSizeMatrix = if (isHistoricRevision) {
        historicSnapshot?.sizeMatrix ?: emptyList()
    } else {
        sizeMatrixInput
    }
    val displayedFee = if (isHistoricRevision) {
        (historicSnapshot?.samplingFeeIdr ?: 0L).toString()
    } else {
        feeInput
    }
    val displayedNotes = if (isHistoricRevision) {
        historicSnapshot?.notes ?: ""
    } else {
        notesInput
    }


    ClayCard(
        modifier = modifier,
        outlineColor = when {
            order.isAccApproved -> WeMadeColors.Success
            order.status == SamplingStatus.REVISION -> WeMadeColors.Accent
            else -> WeMadeColors.Outline
        },
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // ── Header accordion: kode+nama (pensil rename), badge, dropdown revisi, chevron ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(statusTint(order.status), CircleShape)
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                val isStyleNameError = showValidationErrors && styleNameInput.isBlank()
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isDraft && isRenaming) {
                            ClayTextField(
                                value = styleNameInput,
                                onValueChange = { styleNameInput = it },
                                placeholder = "Nama desain",
                                keyboardActions = KeyboardActions(onDone = { commitRename() }),
                                modifier = Modifier.weight(1f, fill = false)
                            )
                        } else {
                            Text(
                                // Nama tampil hanya setelah admin me-rename — sebelum itu kartu cukup
                                // menyandang kode autogenerate-nya sendiri (mis. "DSG-02").
                                text = if (order.styleName == designCode) designCode else "$designCode: ${order.styleName}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        if (isDraft) {
                            Spacer(Modifier.width(ClaySpacing.Xs))
                            ClayIconButton(
                                onClick = {
                                    if (isRenaming) {
                                        commitRename()
                                    } else {
                                        styleNameInput = order.styleName
                                        isRenaming = true
                                    }
                                }
                            ) {
                                if (isRenaming) {
                                    IconCheck(Modifier.size(14.dp), color = WeMadeColors.Success)
                                } else {
                                    IconEdit(Modifier.size(13.dp))
                                }
                            }
                        }
                    }
                    if (isStyleNameError) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Nama desain tidak boleh kosong",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SamplingStatusBadge(status = order.status)
                if (order.revisionCount > 0) {
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    Box {
                        ClayActionSurface(
                            onClick = { isRevisionDropdownOpen = !isRevisionDropdownOpen },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                            ) {
                                Text(
                                    text = "Rev $selectedRevision",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Primary
                                )
                                if (isRevisionDropdownOpen) {
                                    IconChevronUp(Modifier.size(10.dp), color = WeMadeColors.Primary)
                                } else {
                                    IconChevronDown(Modifier.size(10.dp), color = WeMadeColors.Primary)
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = isRevisionDropdownOpen,
                            onDismissRequest = { isRevisionDropdownOpen = false }
                        ) {
                            for (rev in order.revisionCount downTo 0) {
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = "Rev $rev",
                                            fontSize = 12.sp,
                                            fontWeight = if (rev == selectedRevision) FontWeight.Bold else FontWeight.Normal,
                                            color = if (rev == selectedRevision) WeMadeColors.Primary else WeMadeColors.OnSurface
                                        )
                                    },
                                    onClick = {
                                        selectedRevision = rev
                                        isRevisionDropdownOpen = false
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayActionSurface(
                    onClick = onToggleExpanded,
                    contentPadding = PaddingValues(ClaySpacing.Sm)
                ) {
                    if (expanded) {
                        IconChevronUp(Modifier.size(14.dp))
                    } else {
                        IconChevronDown(Modifier.size(14.dp))
                    }
                }
            }
        }

        // ── Process Stepper (5 Langkah Alur Fisik Garmen: Pra-Rilis s/d ACC) ──
        // Tampil baik saat Accordion Expanded maupun Collapsed (ketika collapsed tetap muncul steppernya)
        Spacer(Modifier.height(ClaySpacing.Md))
        val garmentTimeline = remember(order) { order.resolveGarmentTimeline() }
        val stepperSteps = remember(garmentTimeline) {
            garmentTimeline.map { stepState ->
                ClayStepData(
                    title = stepState.step.displayName,
                    subtitle = stepState.subtitle,
                    status = when {
                        stepState.isCompleted -> ClayStepStatus.COMPLETED
                        stepState.isActive -> ClayStepStatus.ACTIVE
                        else -> ClayStepStatus.PENDING
                    },
                    badgeText = stepState.badgeText,
                    stepNumber = stepState.step.order
                )
            }
        }
        ClayProcessStepper(
            steps = stepperSteps,
            activeColor = if (order.status == SamplingStatus.REVISION) WeMadeColors.Warning else WeMadeColors.Purple,
            completedColor = WeMadeColors.Success,
            fullWidth = true,
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Card,
                    background = WeMadeColors.SurfaceMuted.copy(alpha = 0.45f),
                    outline = WeMadeColors.Border,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        )

        if (!expanded) {
            return@ClayCard
        }

        if (isHistoricRevision) {
            Spacer(Modifier.height(ClaySpacing.Sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Primary)
                Spacer(Modifier.width(ClaySpacing.Sm))
                Text(
                    text = "Menampilkan arsip Rev $selectedRevision (Hanya Baca). Pilih Rev ${order.revisionCount} untuk mengedit desain.",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        // ── Warning Revisi Feedback di Paling Atas (Full Width) ──
        val selectedFeedback = feedbackFor(selectedRevision)
        if (selectedFeedback != null) {
            Spacer(Modifier.height(ClaySpacing.Sm))
            RevisionFeedbackCallout(
                title = "Feedback Revisi (Rev $selectedRevision)",
                notes = selectedFeedback
            )
        }

        // ── Info: revisi baru otomatis mewarisi seluruh data revisi sebelumnya ──
        // requestRevision() mengarsipkan snapshot lama TANPA menghapus mockup, size chart, fee,
        // dan catatan milik order — jadi user cukup mengubah bagian yang diperlukan saja.
        if (order.status == SamplingStatus.REVISION && !isHistoricRevision) {
            Spacer(Modifier.height(ClaySpacing.Sm))
            RevisionCarryOverCallout(revision = order.revisionCount)
        }

        // ── Status Produksi Aktif & Banner Acuan Spek Terkunci (Saat SPK sudah rilis) ──
        if (!isDraft) {
            Spacer(Modifier.height(ClaySpacing.Md))
            SamplingActiveStepCard(
                order = order,
                garmentTimeline = garmentTimeline,
                onNavigateToSampling = { navigator(AppNavScreen.SAMPLING_ORDER) }
            )
            Spacer(Modifier.height(ClaySpacing.Sm))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    IconLock(Modifier.size(13.dp), color = WeMadeColors.Primary)
                    Text(
                        text = "Spesifikasi Fisik Terkunci (Golden Sample SPK #${order.spkNumber.value})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
                Text(
                    text = "Hanya Baca • Acuan Produksi",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Grid 2 Kolom: Kiri (Slot Foto Depan & Belakang Atas-Bawah) vs Kanan (Size Chart & Detail Lainnya) ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            verticalAlignment = Alignment.Top
        ) {
            // ── Kolom Kiri: Foto Tampak Depan & Tampak Belakang (Atas - Bawah) ──
            val isFrontMockupError = showValidationErrors && (frontRef.isNullOrBlank() && order.mockupFrontKey.isNullOrBlank())
            Column(
                modifier = Modifier.width(200.dp),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Column {
                    DesignMockupSlot(
                        label = "Tampak Depan",
                        bitmap = frontBitmap,
                        readOnly = isFormReadOnly,
                        isError = isFrontMockupError,
                        onUpload = {
                            if (!isFormReadOnly) {
                                pendingCropSlot = "front"
                                cardScope.launch { pendingCropPick = pickLocalFile(MOCKUP_IMAGE_ACCEPT) }
                            }
                        }
                    )
                    if (isFrontMockupError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Foto Tampak Depan wajib diunggah",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }
                DesignMockupSlot(
                    label = "Tampak Belakang",
                    bitmap = backBitmap,
                    readOnly = isFormReadOnly,
                    onUpload = {
                        if (!isFormReadOnly) {
                            pendingCropSlot = "back"
                            cardScope.launch { pendingCropPick = pickLocalFile(MOCKUP_IMAGE_ACCEPT) }
                        }
                    }
                )
            }

            // ── Kolom Kanan: Size Chart, Feedback, Sampling Fee, Catatan, dan Aksi ──
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // ── Tabel 1: Size Chart / POM (Spesifikasi Ukuran Fisik) ──
                val hasCompleteSizePom = hasAtLeastOneCompleteMeasurementColumn(displayedSizeMatrix)
                val isSizeChartError = showValidationErrors && !hasCompleteSizePom

                Column {
                    SamplingSizeChartTable(
                        pomRows = displayedSizeMatrix.filter { !it.isQtyRow },
                        readOnly = isFormReadOnly,
                        isError = isSizeChartError,
                        onUpdateRow = { updatedRow ->
                            if (!isFormReadOnly) {
                                val updated = sizeMatrixInput.map { if (it.id == updatedRow.id) updatedRow else it }
                                sizeMatrixInput = updated
                            }
                        },
                        onDeleteRow = { rowId ->
                            if (!isFormReadOnly) {
                                val updated = sizeMatrixInput.filter { it.id != rowId }
                                sizeMatrixInput = sanitizeSamplingMatrix(updated)
                            }
                        },
                        onAddRow = {
                            if (!isFormReadOnly) {
                                val nextId = "pom_${Clock.System.now().toEpochMilliseconds()}"
                                val newRow = SizeChartRow(
                                    id = nextId,
                                    pomName = "Ukuran Baru",
                                    values = STANDARD_SAMPLING_SIZE_COLUMNS.associateWith { "" }
                                )
                                sizeMatrixInput = sizeMatrixInput + newRow
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSizeChartError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Size chart wajib memiliki minimal 1 ukuran dengan seluruh baris ukuran (POM) terisi lengkap (misal: ALL SIZE terisi seluruhnya).",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }

                // ── Tabel 2: Alokasi Jumlah Sampel per Ukuran (Tabel Baru Mandiri) ──
                val currentQtyRow = displayedSizeMatrix.firstOrNull { it.isQtyRow }
                    ?: ensureSamplingQtyRow(displayedSizeMatrix).first { it.isQtyRow }
                val totalSampleQty = calculateTotalSampleQuantity(displayedSizeMatrix)
                val isQtyError = showValidationErrors && totalSampleQty < 1

                Column {
                    SamplingQuantityTable(
                        qtyRow = currentQtyRow,
                        fullMatrix = displayedSizeMatrix,
                        totalQty = totalSampleQty,
                        readOnly = isFormReadOnly,
                        isError = isQtyError,
                        onUpdateQty = { col, newQty ->
                            if (!isFormReadOnly) {
                                val withQty = ensureSamplingQtyRow(sizeMatrixInput)
                                val qtyRow = withQty.first { it.isQtyRow }
                                val newValues = qtyRow.values.toMutableMap()
                                newValues[col] = newQty
                                val updatedQtyRow = qtyRow.copy(values = newValues)
                                sizeMatrixInput = listOf(updatedQtyRow) + withQty.filter { !it.isQtyRow }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isQtyError) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Jumlah sampel minimal 1 pcs. Silakan isi alokasi jumlah pada kolom ukuran aktif di atas.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }

                if (order.isAccApproved) {
                    Column {
                        Text(
                            text = "Approved",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = order.accNotes.ifBlank { "Spesifikasi disetujui buyer." },
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }

                // ── Sampling Fee & Catatan — autosave, tanpa tombol simpan ──────
                ClayTextField(
                    value = displayedFee,
                    onValueChange = { input ->
                        if (!isFormReadOnly) {
                            // Fee default 0 — mengosongkan input mengembalikannya ke "0", tak pernah blank.
                            feeInput = input.filter { it.isDigit() }.ifBlank { "0" }
                        }
                    },
                    label = "Sampling Fee (Rp)",
                    placeholder = "mis. 350000",
                    leadingIcon = { IconReceipt(Modifier.size(13.dp)) },
                    readOnly = isFormReadOnly
                )

                // ── Input Target Deadline Selesai / Kirim Sampel (Wajib) ──────────────
                val parsedDeadline = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(deadlineInput.trim()) ?: order.deadlineDelivery
                val isDeadlineError = showValidationErrors && parsedDeadline == null

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
                    ClayTextField(
                        value = if (isHistoricRevision) order.deadlineDelivery?.toString() ?: "" else deadlineInput,
                        onValueChange = { input ->
                            if (!isFormReadOnly) deadlineInput = input.trim()
                        },
                        label = "Deadline Pengiriman Sampel (Wajib)",
                        placeholder = "YYYY-MM-DD (cth: 2026-09-30)",
                        leadingIcon = {
                            IconCalendarGrid(
                                Modifier.size(14.dp),
                                color = if (isDeadlineError) WeMadeColors.Error else WeMadeColors.Primary
                            )
                        },
                        readOnly = isFormReadOnly
                    )
                    if (isDeadlineError) {
                        Text(
                            text = "Target deadline pengiriman/selesai sampel wajib diisi (format: YYYY-MM-DD).",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                    if (!isFormReadOnly) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Preset:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            listOf(3, 7, 14).forEach { days ->
                                val presetDate = remember(days) {
                                    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
                                    today.plus(days, DateTimeUnit.DAY)
                                }
                                ClayTag(
                                    text = "+$days Hari (${presetDate})",
                                    tint = if (deadlineInput == presetDate.toString()) WeMadeColors.Success else WeMadeColors.Primary,
                                    modifier = Modifier.clickable {
                                        deadlineInput = presetDate.toString()
                                    }
                                )
                            }
                        }
                    }
                }

                ClayTextField(
                    value = displayedNotes,
                    onValueChange = { if (!isFormReadOnly) notesInput = it },
                    label = "Catatan",
                    placeholder = "Penempatan bahan, catatan khusus… detail teknis diisi tim sampling.",
                    singleLine = false,
                    minLines = 3,
                    readOnly = isFormReadOnly
                )

                // ── Aksi: Terbitkan Invoice (jika ACC), ACC & Revisi (jika status pengiriman), atau Buat SPK Sampling ──
                if (!isHistoricRevision && order.status != SamplingStatus.CANCELLED) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (order.isAccApproved) {
                            ClayButton(
                                text = "Terbitkan Invoice Sampling",
                                onClick = {
                                    InvoicePrefillCoordinator.setPending(
                                        InvoicePrefillData(
                                            kind = InvoiceKind.SAMPLE,
                                            clientName = order.clientName,
                                            sourceKind = InvoiceSourceKind.DEAL,
                                            sourceRef = order.id.value,
                                            lineDescription = "Biaya sampling ${order.styleName} (${order.spkNumber.value})",
                                            lineQty = 1.0,
                                            linePrice = order.samplingFeeIdr
                                        )
                                    )
                                },
                                style = ClayButtonStyle.Accent,
                                fontSize = 12.sp
                            )
                        } else if (order.isInDelivery) {
                            ClayButton(
                                text = "Tandai ACC Desain ${designCode.removePrefix("DSG-").toIntOrNull() ?: ""}",
                                onClick = {
                                    onEvent(
                                        DealUiEvent.ToggleSampleAcc(
                                            samplingId = order.id.value,
                                            isApproved = true,
                                            notes = ""
                                        )
                                    )
                                },
                                style = ClayButtonStyle.Success,
                                fontSize = 12.sp
                            )
                            ClayButton(
                                text = "Ajukan Revisi",
                                onClick = { isRevisionDialogOpen = true },
                                style = ClayButtonStyle.Accent,
                                fontSize = 12.sp
                            )
                        } else {
                            // Belum di status pengiriman: sembunyikan tombol ACC & Revisi, tampilkan Buat SPK Sampling
                            if (isDraft) {
                                ClayButton(
                                    text = "Buat SPK Sampling",
                                    onClick = {
                                        val totalQty = calculateTotalSampleQuantity(sizeMatrixInput, order.sampleQuantity)
                                        val hasMockup = !frontRef.isNullOrBlank() || !order.mockupFrontKey.isNullOrBlank()
                                        val hasName = styleNameInput.isNotBlank()
                                        val hasCompleteSize = hasAtLeastOneCompleteMeasurementColumn(sizeMatrixInput)
                                        val hasValidQty = totalQty >= 1
                                        val hasValidDeadline = (com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(deadlineInput.trim()) ?: order.deadlineDelivery) != null

                                        if (!hasName || !hasMockup || !hasCompleteSize || !hasValidQty || !hasValidDeadline) {
                                            showValidationErrors = true
                                            if (!expanded) {
                                                onToggleExpanded()
                                            }
                                        } else {
                                            showValidationErrors = false
                                            isConfirmSpkDialogOpen = true
                                        }
                                    },
                                    style = ClayButtonStyle.Primary,
                                    leading = { IconPlus(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                                    fontSize = 12.sp
                                )
                            } else {
                                ClayTag(
                                    text = "SPK #${order.spkNumber.value} • ${order.pipelineStage.displayName}",
                                    tint = WeMadeColors.Primary
                                )
                                ClayButton(
                                    text = "Lihat SPK",
                                    onClick = { navigator(AppNavScreen.SAMPLING_ORDER) },
                                    style = ClayButtonStyle.Secondary,
                                    fontSize = 11.sp
                                )
                                ClayButton(
                                    text = "Kirim ke Buyer",
                                    onClick = {
                                        onEvent(DealUiEvent.AdvanceSamplingStage(order.id.value, SamplingPipelineStage.IN_DELIVERY))
                                    },
                                    style = ClayButtonStyle.Accent,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Popup pengajuan revisi: catatan revisi diisi di sini, bukan inline di kartu ──
    if (isRevisionDialogOpen) {
        RevisionNotesDialog(
            designCode = designCode,
            onDismiss = { isRevisionDialogOpen = false },
            onSubmit = { notes ->
                isRevisionDialogOpen = false
                onEvent(
                    DealUiEvent.ToggleSampleAcc(
                        samplingId = order.id.value,
                        isApproved = false,
                        notes = notes
                    )
                )
            }
        )
    }

    // ── Popup konfirmasi penerbitan SPK ke Divisi Sampling ──
    if (isConfirmSpkDialogOpen) {
        val parsedDeadline = com.eventverse.app.shared.common.DateTimeCodec.parseLocalDateOrNull(deadlineInput.trim()) ?: order.deadlineDelivery
        ConfirmSpkDialog(
            order = order.copy(
                styleName = styleNameInput,
                notes = notesInput,
                samplingFeeIdr = feeInput.toLongOrNull() ?: order.samplingFeeIdr,
                deadlineDelivery = parsedDeadline
            ),
            sizeMatrix = sizeMatrixInput,
            onDismiss = { isConfirmSpkDialogOpen = false },
            onConfirm = {
                isConfirmSpkDialogOpen = false
                onEvent(DealUiEvent.CreateSamplingSpk(order.id.value))
            }
        )
    }

    // ── Cropper kotak: foto yang baru dipilih dipotong 1:1 sebelum diunggah ──
    pendingCropPick?.let { picked ->
        MockupCropDialog(
            bytes = picked.bytes,
            onConfirm = { croppedBytes ->
                pendingCropPick = null
                onEvent(
                    DealUiEvent.UploadSamplingMockup(
                        samplingId = order.id.value,
                        fileName = picked.fileName,
                        mimeType = picked.mimeType,
                        bytes = croppedBytes,
                        slot = pendingCropSlot
                    )
                )
            },
            onDismiss = { pendingCropPick = null }
        )
    }
}

/** Slot foto mockup (Depan / Belakang): placeholder → foto terunggah, dan selalu bisa diklik untuk mengganti. */
@Composable
private fun DesignMockupSlot(
    label: String,
    bitmap: ImageBitmap?,
    onUpload: () -> Unit,
    readOnly: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceMuted,
                outline = if (isError) WeMadeColors.Error else WeMadeColors.Border,
                borderWidth = if (isError) ClayBorder.Thick else ClayBorder.Medium
            )
            .let {
                if (!readOnly) it.clickable(onClick = onUpload) else it
            },
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Mockup $label",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(ClaySpacing.Sm)
            )

            // Label tag tampak depan/belakang di pojok kiri atas di dalam area gambar
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(ClaySpacing.Sm)
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.Surface.copy(alpha = 0.9f),
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }

            // Tombol ganti foto di pojok kanan atas di dalam area gambar
            if (!readOnly) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(ClaySpacing.Sm)
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = WeMadeColors.Primary.copy(alpha = 0.9f),
                            outline = WeMadeColors.Primary,
                            borderWidth = ClayBorder.Hairline
                        )
                        .clickable(onClick = onUpload)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = "Ganti Foto",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Surface
                    )
                }
            }
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(ClaySpacing.Md)
            ) {
                IconImage(
                    Modifier.size(28.dp),
                    color = if (readOnly) WeMadeColors.OnSurfaceMuted else WeMadeColors.Primary
                )
                Spacer(Modifier.height(ClaySpacing.Xs))
                Text(
                    text = if (readOnly) "Tidak ada foto $label" else "Upload $label",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (readOnly) WeMadeColors.OnSurfaceMuted else WeMadeColors.Primary
                )
            }
        }
    }
}

/**
 * Tabel Size Chart / Point of Measurement (POM) per desain sampling.
 * Mendukung penambahan baris kustom, edit nama POM, baris jumlah sampel (pcs),
 * dan penguncian kolom kuantitas secara dinamis sesuai spesifikasi POM yang terisi.
 */
/**
 * Tabel Size Chart / Point of Measurement (POM) per desain sampling.
 * Murni memuat spesifikasi fisik pola garmen (Lebar Dada, Panjang Baju, dll).
 */
@Composable
private fun SamplingSizeChartTable(
    pomRows: List<SizeChartRow>,
    onUpdateRow: (SizeChartRow) -> Unit,
    onDeleteRow: (rowId: String) -> Unit,
    onAddRow: () -> Unit,
    readOnly: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = if (isError) WeMadeColors.Error else WeMadeColors.Border,
                borderWidth = if (isError) ClayBorder.Thick else ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Text(
                    text = "Size Chart / POM",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "• Spesifikasi Pola (cm)",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            if (!readOnly) {
                ClayActionSurface(
                    onClick = onAddRow,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        IconPlus(Modifier.size(11.dp), color = WeMadeColors.Primary)
                        Text(
                            text = "Tambah Ukuran",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        val scrollState = rememberScrollState()
        Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
            Column {
                // Baris header kolom
                Row(
                    modifier = Modifier
                        .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                        .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Bagian / POM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.width(115.dp)
                    )
                    for (col in STANDARD_SAMPLING_SIZE_COLUMNS) {
                        Text(
                            text = col,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(52.dp)
                        )
                    }
                    Spacer(Modifier.width(28.dp)) // ruang tombol hapus
                }

                Spacer(Modifier.height(ClaySpacing.Xs))

                // Baris data POM
                if (pomRows.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = ClaySpacing.Md),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada baris ukuran. Klik '+ Tambah Ukuran'.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                } else {
                    pomRows.forEach { row ->
                        Row(
                            modifier = Modifier
                                .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Input nama POM
                            Box(
                                modifier = Modifier
                                    .width(115.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 8.dp, vertical = 5.dp)
                            ) {
                                BasicTextField(
                                    value = row.pomName,
                                    readOnly = readOnly,
                                    onValueChange = { newPom ->
                                        onUpdateRow(row.copy(pomName = newPom))
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WeMadeColors.OnSurface
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Input nilai per ukuran (cm)
                            for (col in STANDARD_SAMPLING_SIZE_COLUMNS) {
                                val currentVal = row.values[col] ?: ""

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .width(48.dp)
                                        .clayFlat(
                                            shape = ClayShapes.Pill,
                                            background = WeMadeColors.SurfaceMuted,
                                            outline = WeMadeColors.Border,
                                            borderWidth = ClayBorder.Hairline
                                        )
                                        .padding(horizontal = 4.dp, vertical = 5.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    BasicTextField(
                                        value = currentVal,
                                        readOnly = readOnly,
                                        onValueChange = { newVal ->
                                            val newValues = row.values.toMutableMap()
                                            newValues[col] = newVal
                                            onUpdateRow(row.copy(values = newValues))
                                        },
                                        textStyle = TextStyle(
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Normal,
                                            color = WeMadeColors.OnSurface,
                                            textAlign = TextAlign.Center
                                        ),
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }

                            // Tombol hapus baris POM
                            if (!readOnly) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clickable { onDeleteRow(row.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    IconClose(
                                        modifier = Modifier.size(12.dp),
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            } else {
                                Spacer(Modifier.width(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Tabel Alokasi Kuantitas Sampel per Ukuran (Tabel Mandiri).
 * Ditampilkan tepat di bawah Size Chart, dengan kolom Total Pcs di sisi kanan.
 * Gating dinamis: cell kuantitas ukuran tertentu hanya aktif jika kolom ukuran tersebut
 * telah memiliki spesifikasi parameter fisik di tabel Size Chart.
 */
@Composable
private fun SamplingQuantityTable(
    qtyRow: SizeChartRow,
    fullMatrix: List<SizeChartRow>,
    totalQty: Int,
    onUpdateQty: (col: String, value: String) -> Unit,
    readOnly: Boolean = false,
    isError: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Surface,
                outline = if (isError) WeMadeColors.Error else WeMadeColors.Border,
                borderWidth = if (isError) ClayBorder.Thick else ClayBorder.Medium
            )
            .padding(ClaySpacing.Sm)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Text(
                    text = "Alokasi Jumlah Sampel",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "• Qty aktif jika POM terisi",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            // Total Badge Netral
            Box(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "Total: $totalQty pcs",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        val activeColumns = STANDARD_SAMPLING_SIZE_COLUMNS.filter { isSizeColumnActive(fullMatrix, it) }

        if (activeColumns.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = ClaySpacing.Md),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Belum ada ukuran aktif. Isi parameter di Size Chart untuk mengalokasikan sampel.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val scrollState = rememberScrollState()
            Box(modifier = Modifier.fillMaxWidth().horizontalScroll(scrollState)) {
                Column {
                    // Header Kolom Ukuran Aktif
                    Row(
                        modifier = Modifier
                            .background(WeMadeColors.SurfaceMuted, ClayShapes.Pill)
                            .padding(horizontal = ClaySpacing.Sm, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ukuran",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.width(115.dp)
                        )
                        for (col in activeColumns) {
                            Text(
                                text = col,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurfaceMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.width(52.dp)
                            )
                        }
                        Text(
                            text = "Total",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(52.dp)
                        )
                    }

                    Spacer(Modifier.height(ClaySpacing.Xs))

                    // Baris Input Qty per Ukuran Aktif (Gaya biasa persis seperti baris Size Chart)
                    Row(
                        modifier = Modifier
                            .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Label baris biasa (lebar 115.dp dan tinggi seragam dengan POM Size Chart)
                        Box(
                            modifier = Modifier
                                .width(115.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = "Jumlah (pcs)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Cell per ukuran aktif (gaya dan ukuran persis cell Size Chart)
                        for (col in activeColumns) {
                            val currentVal = qtyRow.values[col] ?: ""

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .width(48.dp)
                                    .clayFlat(
                                        shape = ClayShapes.Pill,
                                        background = WeMadeColors.SurfaceMuted,
                                        outline = WeMadeColors.Border,
                                        borderWidth = ClayBorder.Hairline
                                    )
                                    .padding(horizontal = 4.dp, vertical = 5.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                BasicTextField(
                                    value = currentVal,
                                    readOnly = readOnly,
                                    onValueChange = { newVal ->
                                        val filtered = newVal.filter { it.isDigit() }
                                        onUpdateQty(col, filtered)
                                    },
                                    textStyle = TextStyle(
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = WeMadeColors.OnSurface,
                                        textAlign = TextAlign.Center
                                    ),
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Total Cell (gaya dan ukuran persis cell Size Chart)
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .width(48.dp)
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Border,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = 4.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$totalQty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WeMadeColors.OnSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kotak catatan evaluasi buyer — amber supaya terbaca sebagai sesuatu yang menuntut tindakan. */
@Composable
private fun RevisionFeedbackCallout(
    notes: String,
    title: String = "Revisi Feedback"
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Warning.copy(alpha = 0.12f),
                outline = WeMadeColors.Warning.copy(alpha = 0.55f),
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        IconWarning(Modifier.size(15.dp), color = WeMadeColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Xxs))
            Text(text = notes, fontSize = 12.sp, color = WeMadeColors.OnSurface)
        }
    }
}


/**
 * Banner info carry-over revisi: saat buyer mengajukan revisi, seluruh data revisi sebelumnya
 * (mockup, size chart, biaya, catatan) otomatis ditarik ke revisi berikutnya — user tinggal
 * mengubah bagian yang diperlukan.
 */
@Composable
private fun RevisionCarryOverCallout(revision: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Primary.copy(alpha = 0.08f),
                outline = WeMadeColors.Primary.copy(alpha = 0.45f),
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        IconRestore(Modifier.size(15.dp), color = WeMadeColors.Primary)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Revisi $revision dimulai dari data revisi sebelumnya",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Xxs))
            Text(
                text = "Foto mockup, size chart, biaya sampling, dan catatan otomatis ditarik dari revisi sebelumnya — cukup ubah bagian yang diperlukan.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurface
            )
        }
    }
}

@Composable
private fun SamplingStatusBadge(status: SamplingStatus) {
    val label = when (status) {
        SamplingStatus.DRAFT -> "Draft SPK"
        SamplingStatus.IN_PROGRESS -> "Sedang Jahit Sampel"
        // Nomor revisi tidak lagi menempel di badge — angkanya sekarang jadi selector
        // riwayat (pil "Rev N") di sebelah badge.
        SamplingStatus.REVISION -> "Perlu Revisi"
        SamplingStatus.ACC_APPROVED -> "ACC Disetujui"
        SamplingStatus.CANCELLED -> "Dibatalkan"
    }
    ClayBadge(text = label, tint = statusTint(status), dot = true)
}

private fun statusTint(status: SamplingStatus): Color = when (status) {
    SamplingStatus.DRAFT -> WeMadeColors.OnSurfaceMuted
    SamplingStatus.IN_PROGRESS -> WeMadeColors.Purple
    SamplingStatus.REVISION -> WeMadeColors.Warning
    SamplingStatus.ACC_APPROVED -> WeMadeColors.Success
    SamplingStatus.CANCELLED -> WeMadeColors.Error
}

@Composable
private fun SamplingActiveStepCard(
    order: SamplingOrder,
    garmentTimeline: List<GarmentStepState>,
    onNavigateToSampling: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeStep = garmentTimeline.firstOrNull { it.isActive }
        ?: garmentTimeline.lastOrNull { it.isCompleted }
        ?: garmentTimeline.first()

    val currentStep = activeStep.step
    val isRevisionActive = order.status == SamplingStatus.REVISION

    val stageBg = when (currentStep) {
        GarmentTrackingStep.INPUT_SPEK -> WeMadeColors.SurfaceMuted
        GarmentTrackingStep.SPK_RELEASED -> WeMadeColors.Purple.copy(alpha = 0.08f)
        GarmentTrackingStep.SAMPLING -> if (isRevisionActive) WeMadeColors.Warning.copy(alpha = 0.08f) else WeMadeColors.Purple.copy(alpha = 0.08f)
        GarmentTrackingStep.READY_TO_SHIP -> WeMadeColors.Accent.copy(alpha = 0.10f)
        GarmentTrackingStep.ACC_APPROVED -> if (order.isAccApproved) WeMadeColors.Success.copy(alpha = 0.12f) else WeMadeColors.Warning.copy(alpha = 0.08f)
    }

    val stageOutline = when (currentStep) {
        GarmentTrackingStep.SAMPLING -> if (isRevisionActive) WeMadeColors.Warning.copy(alpha = 0.6f) else WeMadeColors.Purple.copy(alpha = 0.45f)
        GarmentTrackingStep.ACC_APPROVED -> if (order.isAccApproved) WeMadeColors.Success.copy(alpha = 0.5f) else WeMadeColors.Warning.copy(alpha = 0.5f)
        GarmentTrackingStep.READY_TO_SHIP -> WeMadeColors.Accent.copy(alpha = 0.5f)
        else -> WeMadeColors.Purple.copy(alpha = 0.35f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = stageBg,
                outline = stageOutline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // ── Header Status Aktif Stepper ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayTag(
                    text = "Langkah ${currentStep.order} dari 5: ${currentStep.displayName}",
                    tint = when (currentStep) {
                        GarmentTrackingStep.SAMPLING -> if (isRevisionActive) WeMadeColors.Warning else WeMadeColors.Purple
                        GarmentTrackingStep.ACC_APPROVED -> if (order.isAccApproved) WeMadeColors.Success else WeMadeColors.Warning
                        GarmentTrackingStep.READY_TO_SHIP -> WeMadeColors.Accent
                        else -> WeMadeColors.Purple
                    }
                )
                Text(
                    text = activeStep.subtitle ?: currentStep.displayName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }

            ClayActionSurface(
                onClick = onNavigateToSampling,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "Buka Modul Sampling",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary
                    )
                    IconArrowForward(Modifier.size(10.dp), color = WeMadeColors.Primary)
                }
            }
        }

        // ── Card Khusus Catatan Revisi Buyer (Jika status revisi atau pernah revisi) ──
        if (order.status == SamplingStatus.REVISION || order.revisionCount > 0) {
            SamplingRevisionNoticeCard(order = order)
        }

        // ── Konten Monitoring Berdasarkan Tahap Transaksi ──
        when (currentStep) {
            GarmentTrackingStep.INPUT_SPEK, GarmentTrackingStep.SPK_RELEASED -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusDetailChip(label = "Nomor SPK", value = "#${order.spkNumber.value}")
                    StatusDetailChip(label = "Jumlah Sampel", value = "${order.sampleQuantity} pcs")
                    StatusDetailChip(label = "Status Alur", value = "Menunggu Pemrograman CAM")
                }
            }

            GarmentTrackingStep.SAMPLING -> {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    Text(
                        text = "Monitoring alur fisik sampel di lantai produksi: CAM, Rajut Mesin, QC In-Line, Finishing, hingga QC Final Ukuran Jadi.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )

                    // Timeline 5 Fase Monitoring Produksi Sampling
                    SamplingMonitoringTimeline(order = order)
                }
            }

            GarmentTrackingStep.READY_TO_SHIP -> {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Sampel fisik telah selesai diproduksi dan sedang dikirimkan ke buyer untuk evaluasi fitting dan persetujuan (ACC).",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDetailChip(
                            label = "Nomor Resi Pengiriman",
                            value = order.courierTracking?.ifBlank { "Belum ada resi (Kurir)" } ?: "Belum ada resi (Kurir)"
                        )
                        StatusDetailChip(
                            label = "Status Ekspedisi",
                            value = if (!order.courierTracking.isNullOrBlank()) "Dalam Perjalanan ke Buyer" else "Siap Diserahkan ke Kurir"
                        )
                    }
                }
            }

            GarmentTrackingStep.ACC_APPROVED -> {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    if (order.isAccApproved) {
                        Text(
                            text = "Sampel telah disetujui buyer (ACC). Pola fisik, jenis benang, dan gramasi ini terkunci sebagai acuan Golden Sample untuk Produksi Massal di Tab 2.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.Success
                        )
                    } else {
                        Text(
                            text = "Sampel telah diterima buyer. Menunggu keputusan buyer: ACC (disetujui) untuk lanjut ke PO Massal, atau Ajukan Revisi jika perlu penyesuaian ukuran/detail.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SamplingMonitoringTimeline(
    order: SamplingOrder,
    modifier: Modifier = Modifier
) {
    val camMilestone = order.milestones.find { it.step == MilestoneStep.PROGRAM }
    val isCamDone = camMilestone?.isCompleted == true || order.pipelineStage > SamplingPipelineStage.CAM_PROGRAMMING
    val isCamActive = order.pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING

    val knitMilestone = order.milestones.find { it.step == MilestoneStep.RAJUT }
    val linkMilestone = order.milestones.find { it.step == MilestoneStep.LINKING }
    val isKnitDone = (knitMilestone?.isCompleted == true && linkMilestone?.isCompleted == true) || order.pipelineStage > SamplingPipelineStage.LINKING_ASSEMBLY
    val isKnitActive = order.pipelineStage == SamplingPipelineStage.MACHINE_KNITTING || order.pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY

    val inlineQc = order.qcInspections.firstOrNull()
    val isQc1Done = order.pipelineStage >= SamplingPipelineStage.FINISHING_QC || order.isInDelivery || inlineQc?.qcResult == QcInspectionResult.PASSED
    val isQc1Active = order.pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY && order.finishingDeposits.isEmpty()

    val isFinishingDone = order.isFinishingComplete || order.isInDelivery
    val isFinishingActive = order.pipelineStage == SamplingPipelineStage.FINISHING_QC && !isFinishingDone
    val isMakloon = order.finishingPath == FinishingPath.MAKLOON_VENDOR

    val finalQc = order.latestQcReport
    val isQc2Done = order.isInDelivery || order.isAccApproved || (finalQc?.qcResult == QcInspectionResult.PASSED && isFinishingDone)
    val isQc2Active = order.pipelineStage == SamplingPipelineStage.FINISHING_QC && isFinishingDone

    val camMulai = formatInstantWithTime(order.createdAt)
    val camSelesai = if (isCamDone) {
        camMilestone?.completedAt?.let { formatLocalDateWithTime(it, order.updatedAt, "10:30") }
            ?: formatInstantWithTime(order.updatedAt)
    } else "-"

    val knitMulai = if (isKnitActive || isKnitDone) {
        if (camSelesai != "-") camSelesai else formatInstantWithTime(order.updatedAt)
    } else "-"
    val knitSelesai = if (isKnitDone) {
        (linkMilestone?.completedAt ?: knitMilestone?.completedAt)?.let {
            formatLocalDateWithTime(it, order.updatedAt, "15:45")
        } ?: formatInstantWithTime(order.updatedAt)
    } else "-"

    val qc1Mulai = if (isQc1Active || isQc1Done) {
        if (knitSelesai != "-") knitSelesai else formatInstantWithTime(order.updatedAt)
    } else "-"
    val qc1Selesai = if (isQc1Done) {
        inlineQc?.inspectedAt?.let { formatInstantWithTime(it) }
            ?: linkMilestone?.completedAt?.let { formatLocalDateWithTime(it, order.updatedAt, "16:15") }
            ?: formatInstantWithTime(order.updatedAt)
    } else "-"

    val finishingMulai = if (isFinishingActive || isFinishingDone) {
        if (isMakloon) {
            order.vendorInfo.sentAt?.let { formatLocalDateWithTime(it, order.updatedAt, "08:30") }
                ?: if (qc1Selesai != "-") qc1Selesai else formatInstantWithTime(order.updatedAt)
        } else {
            order.finishingDeposits.firstOrNull()?.createdAt?.let { formatInstantWithTime(it) }
                ?: order.finishingDeposits.firstOrNull()?.depositDate?.let { formatLocalDateWithTime(it, order.updatedAt, "08:30") }
                ?: if (qc1Selesai != "-") qc1Selesai else formatInstantWithTime(order.updatedAt)
        }
    } else "-"
    val finishingSelesai = if (isFinishingDone) {
        if (isMakloon) {
            order.vendorInfo.returnedAt?.let { formatLocalDateWithTime(it, order.updatedAt, "14:00") }
                ?: formatInstantWithTime(order.updatedAt)
        } else {
            order.finishingDeposits.lastOrNull()?.createdAt?.let { formatInstantWithTime(it) }
                ?: order.finishingDeposits.lastOrNull()?.depositDate?.let { formatLocalDateWithTime(it, order.updatedAt, "14:00") }
                ?: formatInstantWithTime(order.updatedAt)
        }
    } else "-"

    val qc2Mulai = if (isQc2Active || isQc2Done) {
        if (finishingSelesai != "-") finishingSelesai else formatInstantWithTime(order.updatedAt)
    } else "-"
    val qc2Selesai = if (isQc2Done) {
        finalQc?.inspectedAt?.let { formatInstantWithTime(it) } ?: formatInstantWithTime(order.updatedAt)
    } else "-"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // 1. Program CAM
        TimelineStepCard(
            stepNumber = "1",
            title = "Program CAM",
            status = if (isCamDone) "Selesai" else if (isCamActive) "Pengerjaan" else "Antrean",
            isDone = isCamDone,
            isActive = isCamActive,
            mulaiText = camMulai,
            selesaiText = camSelesai,
            modifier = Modifier.weight(1f)
        )

        // 2. Rajut Mesin & Linking
        TimelineStepCard(
            stepNumber = "2",
            title = "Rajut & Jahit",
            status = if (isKnitDone) "Selesai" else if (order.pipelineStage == SamplingPipelineStage.LINKING_ASSEMBLY) "Sedang Jahit" else if (order.pipelineStage == SamplingPipelineStage.MACHINE_KNITTING) "Sedang Rajut" else "Antrean",
            isDone = isKnitDone,
            isActive = isKnitActive,
            mulaiText = knitMulai,
            selesaiText = knitSelesai,
            modifier = Modifier.weight(1f)
        )

        // 3. QC 1 (In-Line)
        TimelineStepCard(
            stepNumber = "3",
            title = "QC In-Line",
            status = if (isQc1Done) "Lolos QC 1" else if (isQc1Active) "Inspeksi Mentah" else "Menunggu Rajut",
            isDone = isQc1Done,
            isActive = isQc1Active,
            mulaiText = qc1Mulai,
            selesaiText = qc1Selesai,
            modifier = Modifier.weight(1f)
        )

        // 4. Finishing & Steam
        TimelineStepCard(
            stepNumber = "4",
            title = "Finishing",
            status = if (isFinishingDone) "Tuntas (${order.totalFinishedDepositedQty} pcs)" else if (isMakloon) "Di Vendor Makloon" else if (isFinishingActive) "Cuci & Steam" else "Menunggu QC 1",
            isDone = isFinishingDone,
            isActive = isFinishingActive,
            mulaiText = finishingMulai,
            selesaiText = finishingSelesai,
            modifier = Modifier.weight(1f)
        )

        // 5. QC 2 (Final)
        TimelineStepCard(
            stepNumber = "5",
            title = "QC 2 (Final)",
            status = if (isQc2Done) "Lolos Final" else if (isQc2Active) "Inspeksi Akhir" else "Menunggu Finishing",
            isDone = isQc2Done,
            isActive = isQc2Active,
            mulaiText = qc2Mulai,
            selesaiText = qc2Selesai,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun TimelineStepCard(
    stepNumber: String,
    title: String,
    status: String,
    isDone: Boolean,
    isActive: Boolean,
    mulaiText: String,
    selesaiText: String,
    modifier: Modifier = Modifier
) {
    val cardBg = when {
        isDone -> WeMadeColors.Success.copy(alpha = 0.08f)
        isActive -> WeMadeColors.Purple.copy(alpha = 0.08f)
        else -> WeMadeColors.Surface.copy(alpha = 0.90f)
    }
    val cardOutline = when {
        isDone -> WeMadeColors.Success.copy(alpha = 0.5f)
        isActive -> WeMadeColors.Purple.copy(alpha = 0.55f)
        else -> WeMadeColors.Border
    }

    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = cardBg,
                outline = cardOutline,
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$stepNumber. $title",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = when {
                    isDone -> WeMadeColors.Success
                    isActive -> WeMadeColors.Purple
                    else -> WeMadeColors.OnSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (isDone) {
                IconCheck(Modifier.size(11.dp), color = WeMadeColors.Success)
            } else if (isActive) {
                IconActivity(Modifier.size(11.dp), color = WeMadeColors.Purple)
            }
        }

        ClayBadge(
            text = status,
            tint = when {
                isDone -> WeMadeColors.Success
                isActive -> WeMadeColors.Purple
                else -> WeMadeColors.OnSurfaceMuted
            },
            fontSize = 9.sp
        )

        Spacer(Modifier.height(2.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Mulai:",
                    fontSize = 9.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = mulaiText,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    text = "Selesai:",
                    fontSize = 9.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = selesaiText,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDone) WeMadeColors.Success else WeMadeColors.OnSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SamplingRevisionNoticeCard(order: SamplingOrder) {
    val latestRev = order.revisionHistory.lastOrNull()
    val revNumber = order.revisionCount.coerceAtLeast(1)
    val revDate = latestRev?.at?.let { formatInstantWithTime(it) } ?: formatInstantWithTime(order.updatedAt)
    val revNotes = latestRev?.notes?.ifBlank { null }
        ?: order.accNotes.ifBlank { "Menunggu penyesuaian spesifikasi fisik dari buyer." }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.Warning.copy(alpha = 0.10f),
                outline = WeMadeColors.Warning.copy(alpha = 0.6f),
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                IconWarning(Modifier.size(14.dp), color = WeMadeColors.Warning)
                Text(
                    text = "Informasi Revisi Buyer (Revisi ke-$revNumber)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Warning
                )
            }
            Text(
                text = "Diajukan: $revDate",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Text(
            text = "\"$revNotes\"",
            fontSize = 11.sp,
            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
            color = WeMadeColors.OnSurface,
            modifier = Modifier.padding(start = 18.dp)
        )

        Text(
            text = "Status Alur: SPK diturunkan kembali ke lantai sampling untuk pengerjaan ulang.",
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier.padding(start = 18.dp)
        )
    }
}

private fun formatLocalDate(date: LocalDate?): String {
    if (date == null) return "-"
    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    val m = months.getOrNull(date.monthNumber) ?: date.monthNumber.toString()
    return "${date.dayOfMonth} $m ${date.year}"
}

private fun formatInstant(instant: Instant?): String {
    if (instant == null) return "-"
    val dateStr = instant.toString().take(10)
    val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull()
    return if (parsed != null) formatLocalDate(parsed) else dateStr
}

private fun formatInstantWithTime(instant: Instant?): String {
    if (instant == null) return "-"
    val str = instant.toString()
    val dateStr = str.take(10)
    val timeStr = if (str.contains('T')) str.substringAfter('T').take(5) else "00:00"
    val parsed = runCatching { LocalDate.parse(dateStr) }.getOrNull()
    val formattedDate = if (parsed != null) {
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
        val m = months.getOrNull(parsed.monthNumber) ?: parsed.monthNumber.toString()
        "${parsed.dayOfMonth} $m ${parsed.year}"
    } else dateStr
    return "$formattedDate, $timeStr"
}

private fun formatLocalDateWithTime(date: LocalDate?, updatedAt: Instant? = null, defaultTime: String = "09:00"): String {
    if (date == null) {
        return if (updatedAt != null) formatInstantWithTime(updatedAt) else "-"
    }
    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    val m = months.getOrNull(date.monthNumber) ?: date.monthNumber.toString()
    val datePart = "${date.dayOfMonth} $m ${date.year}"
    val timePart = if (updatedAt != null && updatedAt.toString().startsWith(date.toString())) {
        updatedAt.toString().substringAfter('T').take(5)
    } else {
        defaultTime
    }
    return "$datePart, $timePart"
}

@Composable
private fun StatusDetailChip(label: String, value: String) {
    Column(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = WeMadeColors.Surface.copy(alpha = 0.85f),
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            text = value,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MilestoneMiniBadge(
    label: String,
    status: String,
    isDone: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = if (isDone) WeMadeColors.Success.copy(alpha = 0.12f) else WeMadeColors.Surface.copy(alpha = 0.85f),
                outline = if (isDone) WeMadeColors.Success.copy(alpha = 0.4f) else WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = if (isDone) WeMadeColors.Success else WeMadeColors.OnSurface
        )
        Text(
            text = status,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDone) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun MassProductionTabContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit
) {
    var poNumber by remember { mutableStateOf("") }
    var poDescription by remember { mutableStateOf("") }
    var poQuantity by remember { mutableStateOf("1") }
    var poUnitPrice by remember { mutableStateOf("") }
    val navigator = LocalAppNavigator.current

    val grandTotalIdr = state.purchaseOrders.sumOf { it.totalValueIdr }

    Column {

        // ── Banner verifikasi Golden Sample ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.Success.copy(alpha = 0.12f),
                    outline = WeMadeColors.Success.copy(alpha = 0.55f),
                    borderWidth = ClayBorder.Medium
                )
                .padding(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            IconCheckCircle(Modifier.size(16.dp))
            Text(
                text = "Mengacu pada Sampel ACC: " +
                    state.approvedDesigns.joinToString(" | ") { "#${it.spkNumber.value} (${it.styleName})" } +
                    " — pola & benang terkunci.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Daftar PO terlampir ──────────────────────────────────────────────
        Text(
            text = "Dokumen PO Buyer (${state.purchaseOrders.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        if (state.purchaseOrders.isEmpty()) {
            Text(
                text = "Belum ada PO. Tempelkan PO resmi buyer lewat upload berkas atau input manual di bawah.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        } else {
            state.purchaseOrders.forEach { po ->
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = po.poNumber.value,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(ClaySpacing.Sm))
                            ClayBadge(
                                text = po.origin.displayName,
                                tint = if (po.origin == com.eventverse.app.domain.deal.PoOrigin.UPLOADED) {
                                    WeMadeColors.Info
                                } else {
                                    WeMadeColors.Success
                                }
                            )
                        }
                        Text(
                            text = formatIdr(po.totalValueIdr),
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    if (po.hasFile) {
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        ClayActionSurface(onClick = { onEvent(DealUiEvent.OpenPoDownload(po.id.value)) }) {
                            Text(
                                text = "Unduh berkas PO (${po.fileName ?: "berkas"})",
                                fontSize = 11.sp,
                                color = WeMadeColors.Primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(ClaySpacing.Xs))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Size breakdown massal (baris per item PO) ───────────────────────
        Text(
            text = "Rincian Pesanan Massal",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        val orderLines = state.purchaseOrders.flatMap { po ->
            po.lines.map { line -> Triple(line.description, line.quantity, line.unitPriceIdr) }
        }
        if (orderLines.isEmpty()) {
            Text(
                text = "Belum ada rincian ukuran — tambahkan lewat PO manual di bawah.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Medium
                    )
            ) {
                SizeBreakdownRow(
                    cells = listOf("Item", "Qty", "Harga Satuan", "Subtotal"),
                    header = true
                )
                orderLines.forEach { (desc, qty, price) ->
                    SizeBreakdownRow(
                        cells = listOf(desc, formatQty(qty), formatIdr(price), formatIdr((qty * price).toLong())),
                        header = false
                    )
                }
                SizeBreakdownRow(
                    cells = listOf("Total", "", "", formatIdr(grandTotalIdr)),
                    header = true
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Form PO manual + tombol upload ──────────────────────────────────
        Text(
            text = "Tempel PO Baru",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poNumber,
            onValueChange = { poNumber = it },
            label = "Nomor PO",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poDescription,
            onValueChange = { poDescription = it },
            label = "Deskripsi item (mis. Size M - Polo Navy)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTextField(
                value = poQuantity,
                onValueChange = { poQuantity = it },
                label = "Qty",
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = poUnitPrice,
                onValueChange = { input -> poUnitPrice = input.filter { it.isDigit() } },
                label = "Harga satuan (Rp)",
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(ClaySpacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Simpan PO Manual",
                onClick = {
                    onEvent(
                        DealUiEvent.AttachManualPo(
                            poNumber = poNumber,
                            description = poDescription,
                            quantity = poQuantity.toDoubleOrNull() ?: 1.0,
                            unitPriceIdr = poUnitPrice.toLongOrNull() ?: 0L
                        )
                    )
                    poNumber = ""
                    poDescription = ""
                    poQuantity = "1"
                    poUnitPrice = ""
                },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Upload Berkas PO",
                onClick = { onEvent(DealUiEvent.UploadPoFile) },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        SpkPrintActions(samplingOrderId = state.approvedDesigns.firstOrNull()?.id?.value.orEmpty())

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Action footer: invoice DP + SPK massal ──────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Terbitkan Invoice DP (50%)",
                onClick = {
                    val deal = state.deal ?: return@ClayButton
                    InvoicePrefillCoordinator.setPending(
                        InvoicePrefillData(
                            kind = InvoiceKind.DOWN_PAYMENT,
                            clientName = state.contactName ?: deal.title.value,
                            sourceKind = InvoiceSourceKind.DEAL,
                            sourceRef = deal.id.value,
                            lineDescription = "Uang muka 50% ${deal.title.value}",
                            lineQty = 1.0,
                            linePrice = grandTotalIdr / 2
                        )
                    )
                    navigator(AppNavScreen.INVOICING)
                },
                style = ClayButtonStyle.Success,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Luncurkan SPK Massal",
                onClick = { onEvent(DealUiEvent.LaunchBulkProduction) },
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp
            )
        }
    }
}
