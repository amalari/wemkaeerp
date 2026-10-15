package com.eventverse.app.presentation.builder

import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu pemetaan status `BuildRequest` → warna token untuk seluruh pane Builder (Handoff sisi
 * tenant maupun Antrian Pembuatan superadmin). Dulu dua pane punya `when` salinan yang sudah
 * berbeda (QUOTED/IN_PROGRESS) — warna sama harus berarti sama, jadi kini satu sumber di sini.
 */
internal fun buildRequestStatusTint(status: BuildRequestStatus?): Color = when (status) {
    BuildRequestStatus.QUEUED -> WeMadeColors.Warning
    BuildRequestStatus.QUOTED, BuildRequestStatus.APPROVED, BuildRequestStatus.IN_PROGRESS -> WeMadeColors.Primary
    BuildRequestStatus.SHIPPED -> WeMadeColors.Success
    BuildRequestStatus.REJECTED -> WeMadeColors.Error
    BuildRequestStatus.SUPERSEDED, null -> WeMadeColors.OnSurfaceMuted
}

/**
 * Satu pemetaan status deployment → warna token, identik dengan pane Deployments (ACTIVE/IMPORTED
 * hijau, BLOCKED_ON_BUILD amber, ROLLED_BACK/FAILED merah, sisanya netral). Dipakai kartu Postur
 * Security dan pane Deployments supaya status yang sama tidak berubah warna antar layar.
 */
internal fun deploymentStatusTint(status: String): Color = when (status) {
    "ACTIVE", "IMPORTED" -> WeMadeColors.Success
    "BLOCKED_ON_BUILD" -> WeMadeColors.Warning
    "ROLLED_BACK", "FAILED" -> WeMadeColors.Error
    else -> WeMadeColors.OnSurfaceMuted
}
