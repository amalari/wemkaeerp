package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Layar konfigurasi pola serah terima rute karung/bundel (TRD-FLOW-003 §4.4).
 *
 * Mengikuti prinsip fail-closed: hanya pengguna dengan wewenang [AccessLevel.MANAGE] atau
 * Owner/SuperAdmin yang boleh menyimpan perubahan mode (PUT /route-settings). Pengguna lain
 * hanya dapat melihat konfigurasi aktif tanpa tombol simpan aktif.
 */
@Composable
fun FulfillmentRouteSettingsScreen(
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    tenantSlug: String,
    viewModel: FulfillmentViewModel = remember(tenantSlug) { FulfillmentViewModel(tenantSlug = tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    val navigator = LocalAppNavigator.current
    val level = decision.config.level
    val canManage = level.weight >= AccessLevel.MANAGE.weight || persona?.isOwnerOrSuperAdmin == true

    // State lokal untuk perubahan mode sebelum ditekan simpan
    val initialModes = remember(state.routeSettingsView) {
        state.effectiveRoutes.associate { it.route.code to it.mode }
    }
    var pendingModes by remember(initialModes) { mutableStateOf(initialModes) }
    var saveSuccessMessage by remember { mutableStateOf<String?>(null) }

    val hasChanges = pendingModes != initialModes

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Kartu Header & Navigasi
        ClayCard {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "← Kembali ke Pengiriman",
                        style = ClayButtonStyle.Ghost,
                        onClick = { navigator(AppNavScreen.FULFILLMENT) }
                    )
                    ClayBadge(
                        text = if (canManage) "Wewenang Atur (MANAGE)" else "Hanya Lihat (VIEW)",
                        tint = if (canManage) WeMadeColors.Primary else WeMadeColors.Info
                    )
                }

                Text(
                    text = "PENGATURAN POLA SERAH TERIMA",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Pilih pola untuk tiap rute serah terima antar divisi. Perubahan mode berlaku untuk perjalanan baru dan tidak mengubah riwayat perjalanan yang sudah berangkat.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
        }

        // Pesan error atau sukses
        state.error?.let { errorMsg ->
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.ErrorBg,
                outlineColor = WeMadeColors.Error
            ) {
                Text(
                    text = errorMsg,
                    fontSize = 11.sp,
                    color = WeMadeColors.Error,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        saveSuccessMessage?.let { successMsg ->
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.SuccessBg,
                outlineColor = WeMadeColors.Success
            ) {
                Text(
                    text = successMsg,
                    fontSize = 11.sp,
                    color = WeMadeColors.Success,
                    fontWeight = FontWeight.SemiBold
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

        if (state.effectiveRoutes.isEmpty() && !state.isLoading) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(ClaySpacing.Md),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    Text(
                        text = "Belum Ada Rute Serah Terima",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Tenant ini belum memiliki rute serah terima yang aktif dalam sistem alur.",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }

        // Daftar Rute
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            items(state.effectiveRoutes, key = { it.route.code.value }) { setting ->
                val route = setting.route
                val currentMode = pendingModes[route.code] ?: setting.mode

                RouteSettingRowCard(
                    routeLabel = route.label,
                    routeCode = route.code.value,
                    selectedMode = currentMode,
                    canManage = canManage,
                    onModeSelected = { newMode ->
                        saveSuccessMessage = null
                        pendingModes = pendingModes + (route.code to newMode)
                    }
                )
            }
        }

        // Tombol Aksi Simpan di bagian bawah bila ada wewenang
        if (canManage) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasChanges) "Ada perubahan mode yang belum disimpan" else "Semua pengaturan tersimpan",
                        fontSize = 11.sp,
                        color = if (hasChanges) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted,
                        fontWeight = if (hasChanges) FontWeight.Bold else FontWeight.Normal
                    )
                    ClayButton(
                        text = if (state.isSubmitting) "Menyimpan..." else "Simpan Pengaturan",
                        style = ClayButtonStyle.Primary,
                        enabled = hasChanges && !state.isSubmitting,
                        onClick = {
                            viewModel.onEvent(
                                FulfillmentUiEvent.UpdateRouteModes(pendingModes) { result ->
                                    result.onSuccess {
                                        saveSuccessMessage = "Pola serah terima berhasil diperbarui."
                                    }
                                }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteSettingRowCard(
    routeLabel: String,
    routeCode: String,
    selectedMode: HandoverMode,
    canManage: Boolean,
    onModeSelected: (HandoverMode) -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = routeLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = routeCode,
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                ClayBadge(
                    text = if (selectedMode == HandoverMode.ADMIN_HUB) "Lewat Meja Admin" else "Antar Langsung",
                    tint = if (selectedMode == HandoverMode.ADMIN_HUB) WeMadeColors.Warning else WeMadeColors.Success
                )
            }

            // Pemilih Mode
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = "Lewat Meja Admin",
                    style = if (selectedMode == HandoverMode.ADMIN_HUB) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    enabled = canManage,
                    onClick = { onModeSelected(HandoverMode.ADMIN_HUB) }
                )
                ClayButton(
                    text = "Antar Langsung",
                    style = if (selectedMode == HandoverMode.DIRECT) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    enabled = canManage,
                    onClick = { onModeSelected(HandoverMode.DIRECT) }
                )
            }

            // Keterangan Mode Terpilih
            Text(
                text = if (selectedMode == HandoverMode.ADMIN_HUB) {
                    "Wajib: timbang karung tertutup, foto bukti timbangan, dan tanda tangan ACC admin produksi sebelum barang berangkat."
                } else {
                    "Cepat: diantar langsung tanpa meja admin; pencocokan dilakukan di tujuan berdasarkan hitungan pcs fisik."
                },
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
