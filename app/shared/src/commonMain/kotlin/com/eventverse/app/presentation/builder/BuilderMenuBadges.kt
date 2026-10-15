package com.eventverse.app.presentation.builder

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.domain.builder.BuilderConsoleBadges
import com.eventverse.app.domain.builder.BuilderLiveSignal
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Data badge menu Builder v2 (TRD-PLAT-014 FR-1-4, keputusan PLAN §7.1/§7.4): angka antrean Handoff
 * dan titik sinyal Live. Angka/jumlahnya dihitung lewat kontrak Track 0 ([BuilderConsoleBadges]) —
 * lapisan ini hanya memuat dan menurunkan data tampilan dari `GET /api/builder/deployments`.
 */
internal data class BuilderMenuBadges(
    val handoffActiveCount: Int = 0,
    val liveSignal: BuilderLiveSignal = BuilderLiveSignal.NOT_DEPLOYED,
)

/**
 * Parse murni respons `GET /api/builder/deployments` menjadi badge menu. Bentuk respons adalah
 * **data tampilan** (bukan kontrak domain — lihat KDoc [BuilderApiClient]), jadi status tak dikenal
 * tidak ikut dihitung; keputusan pending/sinyal tetap milik kontrak `BuilderConsoleBadges`.
 * Murni — bisa diuji tanpa Compose maupun jaringan.
 */
internal fun parseMenuBadges(raw: JsonValue?): BuilderMenuBadges {
    val obj = raw as? JsonValue.Obj ?: return BuilderMenuBadges()
    val pendingStatuses = obj.objectArray("buildRequests")
        .mapNotNull { it.string("status") }
        .mapNotNull { s -> BuildRequestStatus.entries.firstOrNull { it.name == s } }
    val activeStatus = obj.objectArray("deployments")
        .firstOrNull { it.string("status") == "ACTIVE" || it.string("status") == "IMPORTED" }
        ?.string("status")
    val activeDeployment = activeStatus?.let { s ->
        com.eventverse.app.domain.builder.DeploymentStatus.entries.firstOrNull { it.name == s }
    }
    return BuilderMenuBadges(
        handoffActiveCount = BuilderConsoleBadges.handoffActiveCount(pendingStatuses),
        liveSignal = BuilderConsoleBadges.liveSignal(activeDeployment),
    )
}

/**
 * Memuat badge menu sekali saat komposisi (NFR lazy-load: drawer tidak menunggu pane mana pun;
 * gagal memuat → badge default, menu tetap hidup). Dipakai drawer Builder di `App` — satu sumber
 * untuk badge Handoff dan titik Live.
 */
@Composable
internal fun rememberBuilderMenuBadges(): BuilderMenuBadges {
    var badges by remember { mutableStateOf(BuilderMenuBadges()) }
    LaunchedEffect(Unit) {
        BuilderApiClient().deployments().onSuccess { badges = parseMenuBadges(it) }
    }
    return badges
}

/**
 * Warna titik Live di menu — keputusan UI di lapisan tema (kontrak `BuilderLiveSignal` sengaja
 * bebas warna): sinyal dibedakan lewat *peran* token, bukan sebaliknya.
 */
internal fun BuilderLiveSignal.badgeTint(): Color = when (this) {
    BuilderLiveSignal.LIVE -> WeMadeColors.Success
    BuilderLiveSignal.VALIDATING -> WeMadeColors.Info
    BuilderLiveSignal.BLOCKED_ON_BUILD -> WeMadeColors.Warning
    BuilderLiveSignal.FAILED -> WeMadeColors.Error
    BuilderLiveSignal.NOT_DEPLOYED -> WeMadeColors.OnSurfaceDisabled
}
