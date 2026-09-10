package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.pipeline.FactoryFlowUiState
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Header controls summarising this tenant's module configuration and opening the
 * provisioning panel.
 */
@Composable
fun TenantModuleActionBar(
    state: FactoryFlowUiState,
    onToggleModulePanel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (state.isTenantDataLoaded) {
            SummaryChip(
                label = "${state.snapshot.activeModulesCount} modul aktif",
                color = WeMadeColors.Success
            )
            if (state.bypassedCount > 0) {
                SummaryChip(
                    label = "${state.bypassedCount} bypass",
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            if (state.customPluginCount > 0) {
                SummaryChip(
                    label = "${state.customPluginCount} plugin kustom",
                    color = WeMadeColors.Primary
                )
            }
        }

        if (state.isSaving) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = WeMadeColors.Primary
            )
        }

        OutlinedButton(
            onClick = onToggleModulePanel,
            enabled = !state.isLoading,
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            border = BorderStroke(1.dp, WeMadeColors.Primary.copy(alpha = 0.4f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = WeMadeColors.Primary)
        ) {
            Text(
                text = if (state.isModulePanelVisible) "Tutup Pengaturan Modul" else "Atur Modul Tenant",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun SummaryChip(
    label: String,
    color: androidx.compose.ui.graphics.Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}
