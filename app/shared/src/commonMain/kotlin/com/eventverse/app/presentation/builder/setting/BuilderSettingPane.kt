package com.eventverse.app.presentation.builder.setting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.builder.BuilderBillingPane
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

/** Empat tab Pengaturan (TRD-PLAT-014 FR-6) — kunci route internalnya: `setting/<key>`. */
enum class BuilderSettingTab(val key: String, val label: String) {
    IDENTITY("identity", "Identitas Bisnis"),
    PACK_THEME("pack", "Pack & Tema"),
    BILLING("billing", "Tagihan"),
    COLLABORATORS("collaborators", "Kolaborator");

    companion object {
        fun fromKey(key: String?): BuilderSettingTab = entries.firstOrNull { it.key == key } ?: IDENTITY
    }
}

/**
 * Pane Pengaturan Builder v2 (TRD-PLAT-014 FR-6): empat tab — Identitas Bisnis (isi Pengaturan
 * lama), Pack & Tema (read-only; ganti pack tetap lewat chat/deploy), Tagihan (pane Billing lama
 * **dikomposisi**, bukan disalin — FR-6-3), dan Kolaborator (izin `MANAGE_BUILDER`).
 */
@Composable
fun BuilderSettingPane(modifier: Modifier = Modifier, initialTab: String? = null) {
    // Segmen route `setting/<key>` (String) dipakai shell T2; kunci disimpan sebagai string
    // (pola route) — aman untuk saveable di kelima target KMP. Key tak dikenal → tab Identitas.
    var tabKey by rememberSaveable { mutableStateOf(BuilderSettingTab.fromKey(initialTab).key) }
    val tab = BuilderSettingTab.fromKey(tabKey)
    val typography = rememberClayTypography()

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
        Text(
            text = "Pengaturan",
            style = typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        BuilderSettingTabRow(selected = tab, onSelect = { tabKey = it.key })
        when (tab) {
            BuilderSettingTab.IDENTITY -> BuilderIdentityTab()
            BuilderSettingTab.PACK_THEME -> BuilderPackThemeTab()
            BuilderSettingTab.BILLING -> BuilderBillingPane()
            BuilderSettingTab.COLLABORATORS -> BuilderCollaboratorsTab()
        }
    }
}

/** Baris tab memakai komponen chip bersama `ClayChoiceChip` (katalog designsystem, Aturan Tiga Kali). */
@Composable
private fun BuilderSettingTabRow(selected: BuilderSettingTab, onSelect: (BuilderSettingTab) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BuilderSettingTab.entries.forEach { t ->
            ClayChoiceChip(text = t.label, selected = t == selected, onClick = { onSelect(t) }, fontSize = 11.sp)
        }
    }
}
