package com.eventverse.app.presentation.costing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.contracts.CostBucket
import com.eventverse.app.domain.costing.CostingSheet
import com.eventverse.app.domain.costing.CostingSheetStatus
import com.eventverse.app.domain.pipeline.CostingBehavior
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.costing.components.AiQuickEstimatorPane
import com.eventverse.app.presentation.costing.components.HistoricalBenchmarksPane
import com.eventverse.app.domain.pack.GarmentTutorialAnchors
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.tutorial.tutorialAnchor
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun CostingWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: CostingViewModel = remember(tenantSlug, decision, persona) {
        CostingViewModel(
            tenantSlug = tenantSlug,
            decision = decision,
            persona = persona
        )
    }
) {
    val state by viewModel.uiState.collectAsState()

    Column(modifier = modifier.fillMaxSize()) {
        // 1. Top Toolbar
        ClayCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .claySurface(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.PrimaryContainer,
                                outline = WeMadeColors.Outline,
                                offset = ClayOffset.Small,
                                borderWidth = ClayBorder.Medium
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Rp", fontWeight = FontWeight.Black, color = WeMadeColors.Primary, fontSize = 16.sp)
                    }

                    Column {
                        Text(
                            text = "Kalkulasi HPP & Biaya",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Mesin kalkulasi biaya per preset bisnis (FOB, CMT, D2C)",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    state.telemetry?.let { tel ->
                        ClayBadge(
                            text = "Status: ${tel.healthStatus.name}",
                            tint = when (tel.healthStatus.name) {
                                "HEALTHY" -> WeMadeColors.Success
                                "BOTTLENECK" -> WeMadeColors.Warning
                                else -> WeMadeColors.Error
                            }
                        )
                    }
                }

                if (state.canWrite) {
                    ClayButton(
                        text = "+ Hitung HPP Baru", onClick = { viewModel.onEvent(CostingUiEvent.OpenCreateDialog) },
                        style = ClayButtonStyle.Primary, modifier = Modifier.tutorialAnchor(GarmentTutorialAnchors.COSTING_NEW_SHEET)
                    )
                }
            }
        }

        // 2. Status banner
        state.statusMessage?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
                    .claySurface(
                        shape = ClayShapes.Chip,
                        background = if (state.isErrorMessage) WeMadeColors.ErrorBg else WeMadeColors.SuccessBg,
                        outline = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                        offset = ClayOffset.Small,
                        borderWidth = ClayBorder.Medium
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        fontSize = 13.sp,
                        color = if (state.isErrorMessage) WeMadeColors.Error else WeMadeColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Tutup",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.clickable { viewModel.onEvent(CostingUiEvent.DismissStatusMessage) }
                    )
                }
            }
        }

        // 3. Main Workbench Layout
        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (maxWidth < 900.dp) {
                CostingMobileWorkbench(state = state, onEvent = viewModel::onEvent)
            } else {
                CostingDesktopWorkbench(state = state, onEvent = viewModel::onEvent)
            }
        }
    }

    // Create Draft Dialog
    if (state.isCreateDialogOpen) {
        CreateCostingDraftDialog(
            onDismiss = { viewModel.onEvent(CostingUiEvent.CloseCreateDialog) },
            onCreate = { tpId, qty, behavior ->
                viewModel.onEvent(CostingUiEvent.CreateDraft(tpId, qty, behavior))
            }
        )
    }

    // Reject Dialog
    if (state.isRejectDialogOpen && state.selectedSheet != null) {
        RejectCostingDialog(
            sheetNumber = state.selectedSheet!!.number.value,
            onDismiss = { viewModel.onEvent(CostingUiEvent.CloseRejectDialog) },
            onReject = { reason ->
                viewModel.onEvent(CostingUiEvent.Reject(state.selectedSheet!!.id.value, reason))
            }
        )
    }
}

@Composable
private fun CostingDesktopWorkbench(
    state: CostingUiState,
    onEvent: (CostingUiEvent) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxSize().padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Left Column: Filter & List
        Column(
            modifier = Modifier.width(340.dp).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            CostingFilterBar(
                searchQuery = state.searchQuery,
                selectedStatus = state.selectedStatusFilter,
                onSearchChange = { onEvent(CostingUiEvent.SetSearchQuery(it)) },
                onStatusSelect = { onEvent(CostingUiEvent.SetStatusFilter(it)) }
            )

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                items(state.filteredSheets) { sheet ->
                    CostingSheetCard(
                        sheet = sheet,
                        isSelected = sheet.id == state.selectedSheet?.id,
                        onClick = { onEvent(CostingUiEvent.SelectSheet(sheet)) }
                    )
                }
            }
        }

        // Right Column: Detail & Tabs
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            // Tabs Bar — FlowRow, bukan Row: tujuh tab tidak muat satu baris di 1280dp dan Row memotong tab terakhir.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            (FlowRow(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.tutorialAnchor(GarmentTutorialAnchors.COSTING_TABS),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                CostingWorkbenchTab.entries.forEach { tab ->
                    val isActive = tab == state.activeWorkbenchTab
                    ClayButton(
                        text = tab.label,
                        onClick = { onEvent(CostingUiEvent.SelectWorkbenchTab(tab)) },
                        style = if (isActive) ClayButtonStyle.Primary else ClayButtonStyle.Ghost
                    )
                }
            })

            // Tab Content
            ClayCard(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Md)
            ) {
                val sheet = state.selectedSheet
                // Tab yang berdiri sendiri dirender lebih dulu: estimator dan Knowledge Base
                // justru dipakai SEBELUM lembar HPP pertama ada.
                when {
                    state.activeWorkbenchTab == CostingWorkbenchTab.QUICK_ESTIMATOR ->
                        AiQuickEstimatorPane(state = state, onEvent = onEvent)

                    state.activeWorkbenchTab == CostingWorkbenchTab.HISTORICAL_BENCHMARKS ->
                        HistoricalBenchmarksPane(state = state, onEvent = onEvent)

                    sheet == null ->
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Pilih atau buat lembar HPP terlebih dahulu", color = WeMadeColors.OnSurfaceMuted)
                        }

                    else -> when (state.activeWorkbenchTab) {
                        CostingWorkbenchTab.DETAIL -> CostingDetailTab(sheet = sheet, state = state, onEvent = onEvent)
                        CostingWorkbenchTab.BUCKETS -> CostingBucketsTab(sheet = sheet, showMargin = state.showMargin)
                        CostingWorkbenchTab.DRIFT -> CostingDriftTab(sheet = sheet, drift = state.drift)
                        CostingWorkbenchTab.RATE_CARD -> CostingRateCardTab(rateCard = state.activeRateCard, behavior = state.rateCardBehavior, onEvent = onEvent)
                        CostingWorkbenchTab.SIMULATION -> CostingSimulationTab(sheet = sheet)
                        CostingWorkbenchTab.QUICK_ESTIMATOR,
                        CostingWorkbenchTab.HISTORICAL_BENCHMARKS -> Unit // sudah ditangani di atas
                    }
                }
            }
        }
    }
}

@Composable
private fun CostingMobileWorkbench(
    state: CostingUiState,
    onEvent: (CostingUiEvent) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(ClaySpacing.Sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            CostingMobileTab.entries.forEach { tab ->
                ClayButton(
                    text = tab.label,
                    onClick = { onEvent(CostingUiEvent.SelectMobileTab(tab)) },
                    style = if (tab == state.activeMobileTab) ClayButtonStyle.Primary else ClayButtonStyle.Ghost
                )
            }
        }

        when (state.activeMobileTab) {
            CostingMobileTab.LIST -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    items(state.filteredSheets) { sheet ->
                        CostingSheetCard(
                            sheet = sheet,
                            isSelected = sheet.id == state.selectedSheet?.id,
                            onClick = {
                                onEvent(CostingUiEvent.SelectSheet(sheet))
                                onEvent(CostingUiEvent.SelectMobileTab(CostingMobileTab.DETAIL))
                            }
                        )
                    }
                }
            }
            CostingMobileTab.DETAIL -> {
                state.selectedSheet?.let { sheet ->
                    CostingDetailTab(sheet = sheet, state = state, onEvent = onEvent)
                } ?: Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Belum ada lembar yang dipilih", color = WeMadeColors.OnSurfaceMuted)
                }
            }
            CostingMobileTab.RATE_CARD -> {
                CostingRateCardTab(rateCard = state.activeRateCard, behavior = state.rateCardBehavior, onEvent = onEvent)
            }
        }
    }
}

@Composable
private fun CostingFilterBar(
    searchQuery: String,
    selectedStatus: CostingSheetStatus?,
    onSearchChange: (String) -> Unit,
    onStatusSelect: (CostingSheetStatus?) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        ClayTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = "Cari nomor HPP / Tech Pack...",
            modifier = Modifier.fillMaxWidth().tutorialAnchor(GarmentTutorialAnchors.COSTING_SEARCH)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            ClayBadge(
                text = "Semua",
                tint = if (selectedStatus == null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.clickable { onStatusSelect(null) }
            )
            CostingSheetStatus.entries.take(4).forEach { status ->
                ClayBadge(
                    text = status.displayName,
                    tint = if (selectedStatus == status) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.clickable { onStatusSelect(status) }
                )
            }
        }
    }
}

@Composable
private fun CostingSheetCard(
    sheet: CostingSheet,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        selected = isSelected,
        onClick = onClick,
        contentPadding = PaddingValues(ClaySpacing.Sm)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "#${sheet.number.value}",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = sheet.status.displayName,
                    tint = when (sheet.status) {
                        CostingSheetStatus.APPROVED -> WeMadeColors.Success
                        CostingSheetStatus.CALCULATED -> WeMadeColors.Primary
                        CostingSheetStatus.PENDING_APPROVAL -> WeMadeColors.Warning
                        CostingSheetStatus.REJECTED -> WeMadeColors.Error
                        else -> WeMadeColors.OnSurfaceMuted
                    }
                )
            }

            Text(
                text = "Tech Pack: ${sheet.techPackId} · Qty: ${sheet.orderQuantity} pcs",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            sheet.latestResult?.let { res ->
                Text(
                    text = "HPP: Rp ${res.billablePerUnit.minorUnits.toFormattedIdr()}/pcs",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = WeMadeColors.Primary
                )
            }
        }
    }
}

@Composable
private fun CostingDetailTab(
    sheet: CostingSheet,
    state: CostingUiState,
    onEvent: (CostingUiEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Header & Actions Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Lembar HPP #${sheet.number.value}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Behavior: ${sheet.behavior.code.uppercase()} · Status: ${sheet.status.displayName}", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                if (sheet.isEditable) {
                    ClayButton(
                        text = "Hitung HPP",
                        onClick = { onEvent(CostingUiEvent.Calculate(sheet.id.value)) },
                        style = ClayButtonStyle.Primary
                    )
                    ClayButton(
                        text = "Reprice",
                        onClick = { onEvent(CostingUiEvent.Reprice(sheet.id.value)) },
                        style = ClayButtonStyle.Ghost
                    )
                }

                if (sheet.status == CostingSheetStatus.CALCULATED) {
                    ClayButton(
                        text = "Ajukan Persetujuan",
                        onClick = { onEvent(CostingUiEvent.SubmitForApproval(sheet.id.value)) },
                        style = ClayButtonStyle.Success
                    )
                }

                if (sheet.status == CostingSheetStatus.PENDING_APPROVAL) {
                    if (state.canApprove) {
                        ClayButton(
                            text = "Setujui HPP",
                            onClick = { onEvent(CostingUiEvent.Approve(sheet.id.value)) },
                            style = ClayButtonStyle.Success
                        )
                    }
                    ClayButton(
                        text = "Tolak",
                        onClick = { onEvent(CostingUiEvent.OpenRejectDialog) },
                        style = ClayButtonStyle.Danger
                    )
                }

                if (sheet.status == CostingSheetStatus.APPROVED || sheet.status == CostingSheetStatus.REJECTED) {
                    ClayButton(
                        text = "Buat Revisi",
                        onClick = { onEvent(CostingUiEvent.Revise(sheet.id.value)) },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }

        // Summary Metric Cards
        val res = sheet.approvedSnapshot?.result ?: sheet.latestResult
        if (res != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                MetricSummaryCard(
                    title = "HPP per Pcs",
                    value = "Rp ${res.billablePerUnit.minorUnits.toFormattedIdr()}",
                    subtitle = "Biaya ditagihkan ke klien",
                    modifier = Modifier.weight(1f)
                )
                MetricSummaryCard(
                    title = "Total Tagihan Batch",
                    value = "Rp ${res.billableTotal.minorUnits.toFormattedIdr()}",
                    subtitle = "Order ${sheet.orderQuantity} pcs",
                    modifier = Modifier.weight(1f)
                )

                if (state.showMargin) {
                    MetricSummaryCard(
                        title = "Harga Jual Rekomendasi",
                        value = "Rp ${res.sellingPricePerUnit.minorUnits.toFormattedIdr()}",
                        subtitle = "Termasuk margin laba",
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    MetricSummaryCard(
                        title = "Margin & Harga Jual",
                        value = "DIRAHASIAKAN",
                        subtitle = "Hak akses wewenang terbatas",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Xl),
                contentAlignment = Alignment.Center
            ) {
                Text("Lembar HPP berstatus DRAFT dan belum dihitung. Klik tombol 'Hitung HPP' di atas.", color = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun CostingBucketsTab(
    sheet: CostingSheet,
    showMargin: Boolean
) {
    val res = sheet.approvedSnapshot?.result ?: sheet.latestResult
    if (res == null || res.buckets.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Belum ada data bucket biaya", color = WeMadeColors.OnSurfaceMuted)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Text("Rincian Waterfall Komponen Biaya", fontWeight = FontWeight.Black, fontSize = 16.sp)

        res.buckets.forEach { bucket ->
            if (bucket.kind.name == "MARGIN" && !showMargin) {
                // Sembunyikan margin jika caller tidak punya wewenang VIEW_COSTING_MARGIN
                return@forEach
            }
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                shape = ClayShapes.Card,
                contentPadding = PaddingValues(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(bucket.label, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Kategori: ${bucket.kind.name} · Kepemilikan: ${bucket.ownership.code}", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                    }

                    Text(
                        text = "Rp ${bucket.amountPerUnit.minorUnits.toFormattedIdr()}",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        color = if (bucket.isBillableToClient) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}

@Composable
private fun CostingDriftTab(
    sheet: CostingSheet,
    drift: com.eventverse.app.domain.costing.CostingDrift?
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Text("Deteksi Pergeseran Biaya (Drift Detection)", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text(
            "Membandingkan komitmen komersial pada approval snapshot terhadap fluktuasi harga material & rate card hari ini.",
            fontSize = 13.sp,
            color = WeMadeColors.OnSurfaceMuted
        )

        if (sheet.approvedSnapshot == null) {
            Text("Lembar ini belum disetujui, sehingga belum memiliki snapshot komersial untuk dibandingkan.", color = WeMadeColors.OnSurfaceMuted)
        } else {
            when (drift) {
                null -> Text("Memeriksa data pergeseran...", color = WeMadeColors.OnSurfaceMuted)
                is com.eventverse.app.domain.costing.CostingDrift.None -> {
                    ClayCard(shape = ClayShapes.Card, contentPadding = PaddingValues(ClaySpacing.Md)) {
                        Text("Tidak Ada Drift Terdeteksi", fontWeight = FontWeight.Bold, color = WeMadeColors.Success)
                        Text("Harga material dan operasional masih cocok dengan snapshot komersial.", fontSize = 13.sp)
                    }
                }
                is com.eventverse.app.domain.costing.CostingDrift.Detected -> {
                    ClayCard(shape = ClayShapes.Card, contentPadding = PaddingValues(ClaySpacing.Md)) {
                        Text("Pergeseran Biaya Terdeteksi: ${drift.deltaPercent}%", fontWeight = FontWeight.Black, color = WeMadeColors.Error)
                        Text("Selisih per unit: Rp ${drift.deltaPerUnit.minorUnits.toFormattedIdr()}", fontSize = 13.sp)
                        if (drift.isSignificant) {
                            Text("Pergeseran > 2%! Disarankan membuat lembar revisi baru.", color = WeMadeColors.Error, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CostingRateCardTab(
    rateCard: com.eventverse.app.domain.costing.CostingRateCard?,
    behavior: CostingBehavior,
    onEvent: (CostingUiEvent) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Rate Card Tenant: ${behavior.code.uppercase()}", fontWeight = FontWeight.Black, fontSize = 16.sp)

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                CostingBehavior.entries.forEach { b ->
                    ClayBadge(
                        text = b.code,
                        tint = if (b == behavior) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.clickable { onEvent(CostingUiEvent.SelectRateCardBehavior(b)) }
                    )
                }
            }
        }

        if (rateCard != null) {
            Text("Versi: ${rateCard.version} · Berlaku sejak: ${rateCard.effectiveFrom}", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)

            rateCard.laborRatePerSamMinute?.let {
                RateCardRow("Tarif Tenaga Kerja", "Rp ${it.minorUnits.toFormattedIdr()} / SAM-menit")
            }
            rateCard.serviceFeePerUnit?.let {
                RateCardRow("Tarif Jasa Makloon (CMT)", "Rp ${it.minorUnits.toFormattedIdr()} / pcs")
            }
            rateCard.overheadPerUnit?.let {
                RateCardRow("Overhead Pabrik", "Rp ${it.minorUnits.toFormattedIdr()} / pcs")
            }
            rateCard.marginRatio?.let {
                RateCardRow("Margin Laba Standar", it.asPercentageString())
            }
        } else {
            Text("Belum ada rate card tersimpan untuk behavior ini (menggunakan default sistem).", color = WeMadeColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun CostingSimulationTab(sheet: CostingSheet) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Text("Simulasi Cepat What-If", fontWeight = FontWeight.Black, fontSize = 16.sp)
        Text("Uji dampak perubahan batch size terhadap HPP dan alokasi biaya per pcs.", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            listOf(50L, 100L, 500L, 1000L).forEach { qty ->
                ClayCard(shape = ClayShapes.Card, contentPadding = PaddingValues(ClaySpacing.Sm)) {
                    Text("$qty pcs", fontWeight = FontWeight.Black)
                    Text("Hitung simulasi", fontSize = 11.sp, color = WeMadeColors.Primary)
                }
            }
        }
    }
}

@Composable
private fun RateCardRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Xs),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = WeMadeColors.OnSurface)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
    }
}

@Composable
private fun MetricSummaryCard(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
            Text(title, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
            Text(value, fontWeight = FontWeight.Black, fontSize = 18.sp, color = WeMadeColors.Primary)
            Text(subtitle, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        }
    }
}

@Composable
private fun CreateCostingDraftDialog(
    onDismiss: () -> Unit,
    onCreate: (techPackId: String, qty: Long, behavior: CostingBehavior) -> Unit
) {
    var techPackId by remember { mutableStateOf("tp-001") }
    var qtyText by remember { mutableStateOf("100") }
    var selectedBehavior by remember { mutableStateOf(CostingBehavior.FULL_PACKAGE_COGS) }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.width(420.dp),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text("Buat Lembar HPP Baru", fontWeight = FontWeight.Black, fontSize = 18.sp)

                ClayTextField(
                    value = techPackId,
                    onValueChange = { techPackId = it },
                    placeholder = "ID Tech Pack (mis. tp-001)",
                    modifier = Modifier.fillMaxWidth()
                )

                ClayTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    placeholder = "Kuantitas Order (pcs)",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    CostingBehavior.entries.forEach { b ->
                        ClayBadge(
                            text = b.code,
                            tint = if (b == selectedBehavior) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.clickable { selectedBehavior = b }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(text = "Batal", onClick = onDismiss, style = ClayButtonStyle.Ghost)
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Buat Draft",
                        onClick = {
                            val qty = qtyText.toLongOrNull() ?: 1L
                            onCreate(techPackId, qty, selectedBehavior)
                        },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }
    }
}

@Composable
private fun RejectCostingDialog(
    sheetNumber: String,
    onDismiss: () -> Unit,
    onReject: (reason: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.width(400.dp),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text("Tolak HPP #$sheetNumber", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Text("Berikan alasan penolakan agar tim teknis dapat melakukan revisi:", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)

                ClayTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    placeholder = "Mis. Biaya makloon terlalu tinggi...",
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(text = "Batal", onClick = onDismiss, style = ClayButtonStyle.Ghost)
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Tolak Lembar",
                        onClick = { if (reason.isNotBlank()) onReject(reason) },
                        style = ClayButtonStyle.Danger
                    )
                }
            }
        }
    }
}
