package com.eventverse.app.presentation.washing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.workqueue.WashingBatch
import com.eventverse.app.domain.workqueue.WashingBatchStatus
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.infrastructure.api.BundlePhotoUploadItem
import com.eventverse.app.infrastructure.api.WashingBatchApiClient
import com.eventverse.app.infrastructure.api.WashingBatchRemoteDataSource
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconPlus
import com.eventverse.app.presentation.designsystem.IconWarning
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Layar Workbench Stasiun Cuci (Washing Gate Control).
 * Mengontrol peleburan bundle berfoto dan rekonsiliasi sortir lot per PO ke Meja Setrika.
 */
@Composable
fun WashingBatchWorkbenchScreen(
    apiClient: WashingBatchRemoteDataSource = remember { WashingBatchApiClient() },
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var pendingBundles by remember { mutableStateOf<List<WorkCard>>(emptyList()) }
    var batches by remember { mutableStateOf<List<WashingBatch>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var showEntryDialog by remember { mutableStateOf(false) }
    var selectedBatchForSorting by remember { mutableStateOf<WashingBatch?>(null) }

    fun refreshData() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null
            val bundlesRes = apiClient.fetchPendingWashingBundles()
            val batchesRes = apiClient.fetchBatches()

            bundlesRes.onSuccess { pendingBundles = it }
            batchesRes.onSuccess { batches = it }

            if (bundlesRes.isFailure) errorMessage = bundlesRes.exceptionOrNull()?.message
            if (batchesRes.isFailure) errorMessage = batchesRes.exceptionOrNull()?.message
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        refreshData()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Workbench Stasiun Cuci (Washing & Softener)",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Peleburan bundle individual berfoto bukti & sortir lot ke stasiun setrika uap",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = "+ Sesi Cuci Masal Baru",
                    style = ClayButtonStyle.Primary,
                    leading = { IconPlus(modifier = Modifier.size(16.dp)) },
                    onClick = { showEntryDialog = true }
                )
            }
        }

        // Summary Metric Bento
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            MetricCard(
                title = "Antrean Bundle Siap Cuci",
                value = "${pendingBundles.size} Bundle",
                subtext = "${pendingBundles.sumOf { it.wipPcs }} Pcs menunggu drum",
                tint = WeMadeColors.Primary,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Batch Sedang Proses",
                value = "${batches.count { it.status != WashingBatchStatus.SORTED_COMPLETED }} Sesi",
                subtext = "Di drum cuci & dryer",
                tint = WeMadeColors.Accent,
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Batch Selesai Disortir",
                value = "${batches.count { it.status == WashingBatchStatus.SORTED_COMPLETED }} Sesi",
                subtext = "Telah diteruskan ke Setrika Uap",
                tint = WeMadeColors.Success,
                modifier = Modifier.weight(1f)
            )
        }

        if (errorMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconWarning(modifier = Modifier.size(16.dp), color = WeMadeColors.Error)
                Text(errorMessage.orEmpty(), fontSize = 12.sp, color = WeMadeColors.Error)
            }
        }

        Text(
            text = "Daftar Sesi Batch Cuci:",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        // List of Batches
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            if (batches.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Lg),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Belum ada sesi cuci masal. Klik '+ Sesi Cuci Masal Baru' untuk memulai.",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            items(batches, key = { it.id.value }) { batch ->
                ClayCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                            ) {
                                IconPackage(modifier = Modifier.size(24.dp), color = WeMadeColors.Primary)
                                Column {
                                    Text(
                                        text = "Batch ${batch.batchCode} · ${batch.machineDrumNo}",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "Resep: ${batch.washRecipe} | Operator: ${batch.operatorName}",
                                        fontSize = 12.sp,
                                        color = WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            }

                            when (batch.status) {
                                WashingBatchStatus.SORTED_COMPLETED -> {
                                    ClayBadge(
                                        text = "Selesai Disortir ke Setrika",
                                        tint = WeMadeColors.Success,
                                        leading = { IconCheck(modifier = Modifier.size(12.dp)) }
                                    )
                                }
                                WashingBatchStatus.IN_DRYER -> {
                                    ClayBadge(
                                        text = "Di Mesin Pengering (Dryer)",
                                        tint = WeMadeColors.Warning
                                    )
                                }
                                WashingBatchStatus.IN_WASHER -> {
                                    ClayBadge(
                                        text = "Sedang Dicuci Masal",
                                        tint = WeMadeColors.Primary
                                    )
                                }
                            }
                        }

                        // Items & Photo Proof Preview
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.SurfaceMuted,
                                    outline = WeMadeColors.Outline,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Isi: ${batch.totalBundles} Bundle (${batch.totalInputPcs} Pcs) · Seluruh foto bundle terverifikasi",
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurface
                            )

                            if (batch.status != WashingBatchStatus.SORTED_COMPLETED) {
                                ClayButton(
                                    text = "Buka Meja Sortir Pasca-Dryer",
                                    style = ClayButtonStyle.Success,
                                    onClick = { selectedBatchForSorting = batch }
                                )
                            } else {
                                Text(
                                    text = "Diteruskan: ${batch.totalOutputPcs} Pcs ke Meja Setrika" +
                                        if (batch.missingPcs > 0) " (Selisih: ${batch.missingPcs} Pcs)" else "",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (batch.missingPcs > 0) WeMadeColors.Error else WeMadeColors.Success
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Input Batch Cuci Baru
    if (showEntryDialog) {
        WashingBatchEntryDialog(
            pendingBundles = pendingBundles,
            isSubmitting = isSubmitting,
            onDismiss = { showEntryDialog = false },
            onSubmit = { batchCode, drum, recipe, op, bundles, notes ->
                coroutineScope.launch {
                    isSubmitting = true
                    val res = apiClient.createBatch(batchCode, drum, recipe, op, bundles, notes)
                    isSubmitting = false
                    res.onSuccess {
                        showEntryDialog = false
                        refreshData()
                    }
                    res.onFailure {
                        errorMessage = it.message
                    }
                }
            }
        )
    }

    // Modal Meja Sortir Pasca-Dryer
    selectedBatchForSorting?.let { batch ->
        WashingSortingTableDialog(
            batch = batch,
            isSubmitting = isSubmitting,
            onDismiss = { selectedBatchForSorting = null },
            onSubmit = { outputs ->
                coroutineScope.launch {
                    isSubmitting = true
                    val res = apiClient.completeSorting(batch.id, outputs)
                    isSubmitting = false
                    res.onSuccess {
                        selectedBatchForSorting = null
                        refreshData()
                    }
                    res.onFailure {
                        errorMessage = it.message
                    }
                }
            }
        )
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtext: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        Text(text = title, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(text = value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = tint)
        Text(text = subtext, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
    }
}
