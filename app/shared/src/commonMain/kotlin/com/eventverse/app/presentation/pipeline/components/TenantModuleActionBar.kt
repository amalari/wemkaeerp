package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayTag
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
    onToggleHideBypassed: () -> Unit = {},
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
                    label = if (state.hideBypassedNodes) "${state.bypassedCount} bypass (tersembunyi)" else "${state.bypassedCount} bypass (ditampilkan)",
                    color = if (state.hideBypassedNodes) WeMadeColors.OnSurfaceMuted else WeMadeColors.Warning,
                    onClick = onToggleHideBypassed
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

        ClayButton(
            text = if (state.isModulePanelVisible) "Tutup Pengaturan Modul" else "Atur Modul Tenant",
            onClick = onToggleModulePanel,
            enabled = !state.isLoading,
            // Panel yang sedang terbuka membuat tombolnya menetap di posisi tertekan.
            style = if (state.isModulePanelVisible) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
            fontSize = 12.sp,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun SummaryChip(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: (() -> Unit)? = null
) {
    if (onClick != null) {
        ClayTag(
            text = label,
            tint = color,
            fontSize = 11.sp,
            modifier = Modifier.clickable(onClick = onClick)
        )
    } else {
        ClayTag(text = label, tint = color, fontSize = 11.sp)
    }
}
