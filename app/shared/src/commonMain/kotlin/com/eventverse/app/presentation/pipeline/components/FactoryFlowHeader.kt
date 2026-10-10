package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.pipeline.FactoryFlowUiState
import com.eventverse.app.presentation.pipeline.isBlocked
import com.eventverse.app.presentation.theme.WeMadeColors

/** Di bawah lebar ini judul dan aksi ditumpuk; di atasnya berdampingan. */
private val COMPACT_BREAKPOINT: Dp = 720.dp

/**
 * Kepala layar Factory Flow: judul + badge tenant + aksi modul.
 *
 * Badge tenant dan judul memakai [ClayFlowRow] supaya membungkus, bukan menyempit sampai teksnya
 * pecah satu huruf per baris (Kontrak 13). Di lebar sempit aksi turun ke bawah judul alih-alih
 * terdorong keluar layar. Aksi tulis disembunyikan selama alur diblokir (AccessDenied/Failed):
 * tombol yang menulis ke alur yang tak bisa dimuat tidak punya arti.
 */
@Composable
fun FactoryFlowHeader(
    companyLabel: String,
    subtitle: String,
    state: FactoryFlowUiState,
    onToggleModulePanel: () -> Unit,
    onToggleHideBypassed: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val compact = maxWidth < COMPACT_BREAKPOINT
        val actions: @Composable () -> Unit = {
            if (!state.loadState.isBlocked) {
                TenantModuleActionBar(
                    state = state,
                    onToggleModulePanel = onToggleModulePanel,
                    onToggleHideBypassed = onToggleHideBypassed
                )
            }
        }
        if (compact) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HeaderTitle(companyLabel, subtitle, Modifier.fillMaxWidth())
                actions()
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HeaderTitle(companyLabel, subtitle, Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
private fun HeaderTitle(companyLabel: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clayFlat(
                    shape = ClayShapes.Tile,
                    background = WeMadeColors.PrimaryContainer,
                    outline = WeMadeColors.Outline,
                    borderWidth = ClayBorder.Medium
                ),
            contentAlignment = Alignment.Center
        ) {
            IconFlowGraph(modifier = Modifier.size(24.dp), color = WeMadeColors.Primary)
        }

        Column(modifier = Modifier.weight(1f)) {
            ClayFlowRow(spacing = 8.dp) {
                Text(
                    text = "Alur Operasional Pabrik",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayTag(text = companyLabel, tint = WeMadeColors.Primary, fontSize = 11.sp)
            }
            Text(
                text = subtitle,
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
