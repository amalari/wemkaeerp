package com.eventverse.app.presentation.transfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.designsystem.IconWarning
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.transfer.components.SuratJalanManifestCard

@Composable
fun SuratJalanWorkspaceScreen(
    modifier: Modifier = Modifier,
    viewModel: SuratJalanViewModel = remember { SuratJalanViewModel() }
) {
    val state by viewModel.uiState.collectAsState()
    val navigator = LocalAppNavigator.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.Background)
            .padding(ClaySpacing.Xl)
    ) {
        // 1. Header Layar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(WeMadeColors.Primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    IconTruck(modifier = Modifier.size(24.dp), color = WeMadeColors.Surface)
                }
                Spacer(modifier = Modifier.width(ClaySpacing.Md))
                Column {
                    Text(
                        text = "Surat Jalan & Ekspedisi Produksi",
                        style = MaterialTheme.typography.headlineMedium,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Manajemen mutasi antar-gedung, makloon vendor luar, dan pengiriman bertahap buyer",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
            ClayButton(
                text = "Kurir Karung",
                style = ClayButtonStyle.Secondary,
                leading = { IconPackage(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurface) },
                onClick = { navigator(AppNavScreen.FULFILLMENT) }
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // 2. Notifikasi Toast / Pesan Sukses / Error
        if (state.successMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.Success.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCheck(modifier = Modifier.size(18.dp), color = WeMadeColors.Success)
                        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                        Text(
                            text = state.successMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WeMadeColors.Success
                        )
                    }
                    Box(modifier = Modifier.clickable { viewModel.onEvent(SuratJalanUiEvent.DismissMessage) }) {
                        IconClose(modifier = Modifier.size(16.dp), color = WeMadeColors.Success)
                    }
                }
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))
        }

        if (state.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.Error.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(ClaySpacing.Sm)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconWarning(modifier = Modifier.size(18.dp), color = WeMadeColors.Error)
                        Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                        Text(
                            text = state.error ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WeMadeColors.Error
                        )
                    }
                    Box(modifier = Modifier.clickable { viewModel.onEvent(SuratJalanUiEvent.DismissMessage) }) {
                        IconClose(modifier = Modifier.size(16.dp), color = WeMadeColors.Error)
                    }
                }
            }
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))
        }

        // 3. Tab Bar Navigasi Clay
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            SuratJalanTab.entries.forEach { tab ->
                val isSelected = state.selectedTab == tab
                val count = when (tab) {
                    SuratJalanTab.INTERNAL_MUTASI -> state.internalManifests.size
                    SuratJalanTab.MAKLOON_VENDOR -> state.makloonManifests.size
                    SuratJalanTab.PENGIRIMAN_KLIEN -> state.customerManifests.size
                }

                Box(
                    modifier = Modifier
                        .background(
                            if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { viewModel.onEvent(SuratJalanUiEvent.SelectTab(tab)) }
                        .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm)
                ) {
                    Text(
                        text = "${tab.title} ($count)",
                        style = MaterialTheme.typography.titleSmall,
                        color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        // 4. Daftar Manifes Surat Jalan
        if (state.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = WeMadeColors.Primary)
            }
        } else {
            val activeList = when (state.selectedTab) {
                SuratJalanTab.INTERNAL_MUTASI -> state.internalManifests
                SuratJalanTab.MAKLOON_VENDOR -> state.makloonManifests
                SuratJalanTab.PENGIRIMAN_KLIEN -> state.customerManifests
            }

            if (activeList.isEmpty()) {
                ClayCard(
                    shape = ClayShapes.Card,
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(ClaySpacing.Xl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        IconTruck(modifier = Modifier.size(48.dp), color = WeMadeColors.OnSurfaceMuted)
                        Spacer(modifier = Modifier.height(ClaySpacing.Md))
                        Text(
                            text = "Belum Ada Surat Jalan",
                            style = MaterialTheme.typography.titleMedium,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Belum ada manifes terbit pada kategori ${state.selectedTab.title.lowercase()}.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    contentPadding = PaddingValues(bottom = ClaySpacing.Xl)
                ) {
                    items(activeList, key = { it.id.value }) { manifest ->
                        SuratJalanManifestCard(
                            manifest = manifest,
                            onReceive = { manifestId ->
                                viewModel.onEvent(
                                    SuratJalanUiEvent.Receive(
                                        manifestId = manifestId,
                                        receiverName = "Staff Penerima"
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
