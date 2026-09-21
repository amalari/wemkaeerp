package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.traceability.scanTraceCode
import kotlinx.coroutines.launch

/**
 * Workspace modul Packing & Pengiriman — dirancang mobile-first: satu kolom, kartu karung
 * memakai lebar penuh, aksi kontekstual inline di kartunya, dan tombol scan di dekat ibu jari.
 *
 * Penerima bukan pengguna aplikasi: semua perekaman (foto, TTD, resi) dilakukan petugas
 * internal di perangkatnya sendiri.
 */
@Composable
fun FulfillmentWorkspaceScreen(
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: FulfillmentViewModel = FulfillmentViewModel(tenantSlug = persona?.tenantSlug ?: "wemade-demo")
) {
    val state by viewModel.uiState.collectAsState()

    val level = decision.config.level
    if (level.weight <= AccessLevel.NONE.weight) {
        AccessDenied(modifier = modifier)
        return
    }
    val canWork = level.weight >= AccessLevel.OPERATE.weight
    val canApprove = level.weight >= AccessLevel.MANAGE.weight || persona?.isOwnerOrSuperAdmin == true

    val navigator = LocalAppNavigator.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        HeaderCard(
            state = state,
            canApprove = canApprove,
            onNavigateToSuratJalan = { navigator(AppNavScreen.SURAT_JALAN) }
        )

        state.error?.let { message ->
            ClayCard(containerColor = WeMadeColors.Error.copy(alpha = 0.08f)) {
                Text(
                    text = message,
                    fontSize = 11.sp,
                    color = WeMadeColors.Error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        ScanEntryCard(
            enabled = canWork,
            onScanned = { payload -> viewModel.onEvent(FulfillmentUiEvent.ScanSack(payload)) }
        )

        state.scannedSack?.let { sackPayload ->
            ClayCard {
                SubmitSackForm(
                    sackPayload = sackPayload,
                    isSubmitting = state.isSubmitting,
                    onSubmit = { leg, weight, photoKey, requestedBy ->
                        viewModel.onEvent(
                            FulfillmentUiEvent.SubmitTransfer(sackPayload, leg, weight, photoKey, requestedBy)
                        )
                    },
                    onUploadEvidence = { fileName, mime, bytes, onDone ->
                        viewModel.onEvent(FulfillmentUiEvent.UploadEvidence(fileName, mime, bytes, onDone))
                    },
                    onCancel = { viewModel.onEvent(FulfillmentUiEvent.DismissScan) }
                )
            }
        }

        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(state.transfers, key = { it.id.value }) { transfer ->
                TransferCard(
                    transfer = transfer,
                    canApprove = canApprove,
                    canWork = canWork,
                    isSubmitting = state.isSubmitting,
                    onEvent = viewModel::onEvent
                )
            }
        }
    }
}

@Composable
private fun HeaderCard(
    state: FulfillmentUiState,
    canApprove: Boolean,
    onNavigateToSuratJalan: () -> Unit = {}
) {
    ClayCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PENGIRIMAN ANTAR DIVISI",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    IconPackage()
                }
                Text(
                    text = "Karung tertutup di-scan, ditimbang, di-ACC admin, lalu diterima di tujuan.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayBadge(
                    text = if (canApprove) "Anda bisa ACC" else "Wewenang kerja",
                    tint = if (canApprove) WeMadeColors.Primary else WeMadeColors.Info
                )
                ClayButton(
                    text = "Surat Jalan",
                    style = ClayButtonStyle.Secondary,
                    leading = { IconTruck(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurface) },
                    onClick = onNavigateToSuratJalan
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ClayBadge(text = "Menunggu ACC: ${state.pendingApproval.size}", tint = WeMadeColors.Warning)
            ClayBadge(text = "Diantar: ${state.inTransit.size}", tint = WeMadeColors.Info)
            ClayBadge(text = "Selesai: ${state.done.size}", tint = WeMadeColors.Success)
        }
    }
}

@Composable
private fun ScanEntryCard(enabled: Boolean, onScanned: (String) -> Unit) {
    var manualCode by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ClayCard {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            Text(
                text = "SCAN / KETIK KODE KARUNG",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = "Pindai QR Karung",
                    style = ClayButtonStyle.Accent,
                    enabled = enabled,
                    onClick = {
                        scope.launch {
                            scanTraceCode()?.let(onScanned)
                        }
                    }
                )
                ClayTextField(
                    value = manualCode,
                    onValueChange = { manualCode = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "W1SK-....",
                    enabled = enabled
                )
                ClayButton(
                    text = "Cari",
                    style = ClayButtonStyle.Primary,
                    enabled = enabled && manualCode.isNotBlank(),
                    onClick = {
                        onScanned(manualCode)
                        manualCode = ""
                    }
                )
            }
        }
    }
}

@Composable
private fun AccessDenied(modifier: Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ClayCard(shape = ClayShapes.Card) {
            Text(
                text = "Akses ditolak",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Error
            )
            Text(
                text = "Divisi Anda tidak punya wewenang apa pun ke modul Packing & Pengiriman.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
