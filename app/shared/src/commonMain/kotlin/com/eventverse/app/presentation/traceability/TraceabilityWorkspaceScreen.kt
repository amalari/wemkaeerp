package com.eventverse.app.presentation.traceability

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Titik masuk layar telusur dari navigasi.
 *
 * Membungkus pembuatan ViewModel supaya `App.kt` tidak perlu tahu apa pun soal sumber datanya —
 * pola yang sama dipakai layar kerja modul lain.
 */
@Composable
fun TraceabilityWorkspaceScreen(
    tenantSlug: String,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug) { TraceabilityViewModel(tenantSlug = tenantSlug) }
    TraceScanScreen(viewModel = viewModel, modifier = modifier)
}
