package com.eventverse.app.presentation.fulfillment.packing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.carton.CartonId
import com.eventverse.app.domain.fulfillment.carton.CartonStatus
import com.eventverse.app.domain.fulfillment.carton.PackingCarton
import com.eventverse.app.presentation.designsystem.ClayActionSurface
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayDialog
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Layar Packing Bench — meja kerja packing karton (TRD-FUL-001 Track C).
 *
 * Menggunakan Clay design system — tidak ada komponen Material mentah.
 */
@Composable
fun PackingBenchScreen(
    canManage: Boolean = false,
    modifier: Modifier = Modifier,
    tenantSlug: String,
    viewModel: PackingBenchViewModel = remember(tenantSlug) {
        PackingBenchViewModel(tenantSlug = tenantSlug)
    }
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Header
        HeaderSection(
            openCount = state.openCartons.size,
            sealedCount = state.sealedCartons.size,
            onCreateCarton = { viewModel.onEvent(PackingBenchUiEvent.ShowCreateDialog) }
        )

        // Error message
        state.error?.let { error ->
            ErrorCard(message = error, onDismiss = { viewModel.onEvent(PackingBenchUiEvent.DismissError) })
        }

        // Success message
        state.successMessage?.let { message ->
            SuccessCard(message = message, onDismiss = { viewModel.onEvent(PackingBenchUiEvent.DismissSuccess) })
        }

        // Loading
        if (state.isLoading) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        }

        // Carton list
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(state.cartons, key = { it.id.value }) { carton ->
                CartonCard(
                    carton = carton,
                    canManage = canManage,
                    onSeal = { viewModel.onEvent(PackingBenchUiEvent.ShowSealDialog(carton.id)) },
                    onReopen = { viewModel.onEvent(PackingBenchUiEvent.ShowReopenDialog(carton.id)) }
                )
            }
        }
    }

    // Dialog: Create Carton
    if (state.showCreateDialog) {
        CreateCartonDialog(
            onDismiss = { viewModel.onEvent(PackingBenchUiEvent.DismissCreateDialog) },
            onConfirm = { productionRef, notes ->
                viewModel.onEvent(PackingBenchUiEvent.CreateCarton(productionRef, notes))
            }
        )
    }

    // Dialog: Seal Carton
    state.showSealDialog?.let { cartonId ->
        SealCartonDialog(
            onDismiss = { viewModel.onEvent(PackingBenchUiEvent.DismissSealDialog) },
            onConfirm = { sealNumber, weight, actor ->
                viewModel.onEvent(PackingBenchUiEvent.SealCarton(cartonId.value, sealNumber, weight, actor))
            }
        )
    }

    // Dialog: Reopen Carton
    state.showReopenDialog?.let { cartonId ->
        ReopenCartonDialog(
            onDismiss = { viewModel.onEvent(PackingBenchUiEvent.DismissReopenDialog) },
            onConfirm = { reason, actor ->
                viewModel.onEvent(PackingBenchUiEvent.ReopenCarton(cartonId.value, reason, actor))
            }
        )
    }
}

@Composable
private fun HeaderSection(
    openCount: Int,
    sealedCount: Int,
    onCreateCarton: () -> Unit
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
                    M3Text(
                        text = "PACKING BENCH",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    IconInbox()
                }
                M3Text(
                    text = "Kelola karton packing — buat, isi, dan segel karton.",
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayBadge(text = "Dibuka: $openCount", tint = WeMadeColors.Info)
                ClayBadge(text = "Disegel: $sealedCount", tint = WeMadeColors.Success)
                ClayButton(
                    text = "Buat Karton",
                    style = ClayButtonStyle.Accent,
                    onClick = onCreateCarton
                )
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    ClayCard(
        containerColor = WeMadeColors.ErrorBg,
        outlineColor = WeMadeColors.Error
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3Text(
                text = message,
                fontSize = 11.sp,
                color = WeMadeColors.Error,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "Tutup",
                style = ClayButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun SuccessCard(message: String, onDismiss: () -> Unit) {
    ClayCard(
        containerColor = WeMadeColors.SuccessBg,
        outlineColor = WeMadeColors.Success
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            M3Text(
                text = message,
                fontSize = 11.sp,
                color = WeMadeColors.Success,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = "OK",
                style = ClayButtonStyle.Ghost,
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun CartonCard(
    carton: PackingCarton,
    canManage: Boolean,
    onSeal: () -> Unit,
    onReopen: () -> Unit
) {
    val statusTint = when (carton.status) {
        CartonStatus.OPEN -> WeMadeColors.Info
        CartonStatus.SEALED -> WeMadeColors.Success
        CartonStatus.REOPENED -> WeMadeColors.Warning
    }

    ClayCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    M3Text(
                        text = carton.cartonNumber.value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    M3Text(
                        text = carton.productionRef,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                ClayBadge(text = carton.status.displayName, tint = statusTint)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    M3Text(
                        text = "Isi: ${carton.quantityPcs} pcs",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurface
                    )
                    if (carton.grossWeightKg > 0) {
                        M3Text(
                            text = "Berat: ${carton.grossWeightKg} kg",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    if (carton.sealNumber != null) {
                        M3Text(
                            text = "Seal: ${carton.sealNumber?.value}",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
                M3Text(
                    text = formatTimestamp(carton.updatedAt),
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            // Item breakdown
            if (carton.itemBreakdown.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    modifier = Modifier.padding(top = ClaySpacing.Xs)
                ) {
                    carton.itemBreakdown.forEach { (size, qty) ->
                        ClayBadge(
                            text = "$size: $qty",
                            tint = WeMadeColors.SurfaceMuted
                        )
                    }
                }
            }

            // Actions
            if (carton.status == CartonStatus.OPEN && carton.quantityPcs > 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(
                        text = "Segel Karton",
                        style = ClayButtonStyle.Primary,
                        onClick = onSeal
                    )
                }
            } else if (carton.status == CartonStatus.SEALED && canManage) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(
                        text = "Buka Kembali",
                        style = ClayButtonStyle.Secondary,
                        onClick = onReopen
                    )
                }
            }
        }
    }
}

@Composable
private fun CreateCartonDialog(
    onDismiss: () -> Unit,
    onConfirm: (productionRef: String, notes: String) -> Unit
) {
    var productionRef by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    ClayDialog(
        title = "Buat Karton Packing Baru",
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            ClayTextField(
                value = productionRef,
                onValueChange = { productionRef = it },
                label = "Referensi Produksi",
                placeholder = "PO-2026-0001",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = notes,
                onValueChange = { notes = it },
                label = "Catatan (opsional)",
                placeholder = "Catatan untuk karton ini...",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Buat",
                    style = ClayButtonStyle.Primary,
                    enabled = productionRef.isNotBlank(),
                    onClick = { onConfirm(productionRef, notes) }
                )
            }
        }
    }
}

@Composable
private fun SealCartonDialog(
    onDismiss: () -> Unit,
    onConfirm: (sealNumber: String, weight: Double, actor: String) -> Unit
) {
    var sealNumber by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }

    ClayDialog(
        title = "Segel Karton",
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            M3Text(
                text = "Karton akan dikunci dan tidak bisa diubah lagi.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            ClayTextField(
                value = sealNumber,
                onValueChange = { sealNumber = it },
                label = "Nomor Segel",
                placeholder = "SL-xxxxx",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = weight,
                onValueChange = { weight = it },
                label = "Berat Kotor (kg)",
                placeholder = "10.5",
                modifier = Modifier.fillMaxWidth()
            )
            ClayTextField(
                value = actor,
                onValueChange = { actor = it },
                label = "Nama Petugas",
                placeholder = "Nama Anda",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Segel",
                    style = ClayButtonStyle.Primary,
                    enabled = sealNumber.isNotBlank() && weight.isNotBlank() && actor.isNotBlank(),
                    onClick = {
                        val weightValue = weight.toDoubleOrNull() ?: 0.0
                        onConfirm(sealNumber, weightValue, actor)
                    }
                )
            }
        }
    }
}

@Composable
private fun ReopenCartonDialog(
    onDismiss: () -> Unit,
    onConfirm: (reason: String, actor: String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var actor by remember { mutableStateOf("") }

    ClayDialog(
        title = "Buka Kembali Karton",
        onDismiss = onDismiss
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            M3Text(
                text = "Membuka karton membutuhkan alasan dan wewenang MANAGE.",
                fontSize = 11.sp,
                color = WeMadeColors.Warning
            )
            ClayTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan Pembukaan",
                placeholder = "Mengapa karton dibuka kembali?",
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
            ClayTextField(
                value = actor,
                onValueChange = { actor = it },
                label = "Nama Manager",
                placeholder = "Nama Anda",
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Secondary,
                    onClick = onDismiss
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = "Buka",
                    style = ClayButtonStyle.Primary,
                    enabled = reason.isNotBlank() && actor.isNotBlank(),
                    onClick = { onConfirm(reason, actor) }
                )
            }
        }
    }
}

private fun formatTimestamp(instant: Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.dayOfMonth}/${local.monthNumber}/${local.year} ${local.hour}:${local.minute.toString().padStart(2, '0')}"
}
