package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Template Alur Pabrik — default yang diwarisi setiap desain baru: proses sisipan dan tag fase
 * `[Sampling ×] [Produksi ×]` pada Cuci & Setrika.
 *
 * Memakai [ProcessFlowViewModel] sendiri (lingkup bawaan `DefaultTenant`), bukan milik dialog
 * detail SPK, supaya membuka template tidak menggeser lingkup panel desain yang sedang diatur.
 */
@Composable
fun TenantFlowTemplateDialog(onDismiss: () -> Unit) {
    val viewModel = remember { ProcessFlowViewModel() }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ClayCard(
            modifier = Modifier.widthIn(max = 1100.dp).fillMaxWidth(0.95f),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text("TEMPLATE ALUR PABRIK", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                        Text(
                            text = "Default untuk desain baru. × tag Sampling pada Cuci/Setrika = kartu sampling " +
                                "melompati meja itu. SPK yang sudah masuk Program CAM tidak ikut berubah.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }
                ProcessFlowAdjusterPanel(viewModel = viewModel, hideScopeSelector = true)
            }
        }
    }
}
