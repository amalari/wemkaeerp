package com.eventverse.app.presentation.production.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.pipeline.FlowHealthStatus
import com.eventverse.app.domain.production.BulkProductionStatus
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pembungkus tipis berbasis domain di atas [ClayBadge].
 *
 * Tinggal di package fitur, bukan di `designsystem/`: komponen bersama tidak boleh tahu apa itu
 * `BulkProductionStatus` (Kontrak 6 aturan design system). Di sinilah domain diterjemahkan
 * menjadi teks dan warna.
 */
@Composable
fun ProductionStatusBadge(status: BulkProductionStatus) {
    ClayBadge(text = status.displayName, tint = status.tint(), dot = true)
}

/**
 * Warna kesehatan node diambil dari domain (`badgeColorHex`), bukan dari token.
 *
 * Hijau/amber/merah di sini adalah **sinyal produksi**, dan nilainya hidup di
 * `FlowHealthStatus` supaya kanvas Alur Pabrik dan layar ini tidak pernah menampilkan
 * dua warna berbeda untuk keadaan yang sama.
 */
@Composable
fun ProductionHealthBadge(health: FlowHealthStatus) {
    ClayBadge(text = health.label, tint = Color(health.badgeColorHex), dot = true)
}

fun BulkProductionStatus.tint(): Color = when (this) {
    BulkProductionStatus.DRAFT -> WeMadeColors.OnSurfaceMuted
    BulkProductionStatus.RELEASED -> WeMadeColors.Info
    BulkProductionStatus.CUTTING -> WeMadeColors.Accent
    BulkProductionStatus.SEWING -> WeMadeColors.Primary
    BulkProductionStatus.FINISHING -> WeMadeColors.Purple
    BulkProductionStatus.COMPLETED -> WeMadeColors.Success
    BulkProductionStatus.CANCELLED -> WeMadeColors.Error
}
