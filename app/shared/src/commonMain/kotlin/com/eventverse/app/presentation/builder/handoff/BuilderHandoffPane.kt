package com.eventverse.app.presentation.builder.handoff

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.builder.BuildRequestStatus
import com.eventverse.app.domain.builder.BuilderConsoleBadges
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.builder.buildRequestStatusTint
import com.eventverse.app.presentation.builder.BriefViewerDialog
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Pane Handoff (TRD-PLAT-014 FR-4): antrian pembuatan **sisi owner** — permintaan kode modul milik
 * tenant pemanggil saja. Server yang menyaring (`findByTenant`, gate `MANAGE_BUILDER` fail-closed);
 * klien tidak pernah mengirim identitas tenant (scope HIERARCHICAL).
 *
 * Berbeda dengan Antrian Pembuatan konsol superadmin (lintas tenant), penolakan 403 di sini berarti
 * sesi tidak membawa izin Builder — ditampilkan apa adanya, bukan daftar kosong yang menyiratkan
 * "tidak ada pekerjaan".
 */
@Composable
fun BuilderHandoffPane(modifier: Modifier = Modifier) {
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    var rows by remember { mutableStateOf(emptyList<BuilderHandoffRow>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    // Brief yang sedang dilihat: (baris, isi markdown | galat); null = dialog tertutup.
    var viewing by remember { mutableStateOf<BuilderHandoffRow?>(null) }
    var briefText by remember { mutableStateOf<String?>(null) }
    var briefError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            client.deployments()
                .onSuccess { rows = parseOwnedBuildRequests(it); error = null }
                .onFailure { e -> error = e.message }
            loaded = true
        }
    }
    LaunchedEffect(Unit) { load() }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Handoff",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val pending = BuilderConsoleBadges.handoffActiveCount(rows.mapNotNull { it.status })
            if (pending > 0) {
                ClayBadge(text = "$pending menunggu", tint = WeMadeColors.Warning)
            }
            ClayButton(
                text = "Muat ulang",
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                onClick = { load() }
            )
        }
        Text(
            text = "Permintaan pembuatan modul milik tenant ini. Brief developer dibekukan saat permintaan lahir dan tidak pernah ditulis ulang.",
            style = typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )

        error?.let {
            Text(
                text = it,
                color = WeMadeColors.Error,
                style = typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (rows.isEmpty() && error == null && loaded) {
            Text(
                text = "Belum ada permintaan pembuatan. Permintaan muncul saat deploy memakai pack kustom yang belum diimplementasi platform.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            rows.forEach { r -> BuilderHandoffRowCard(row = r, onOpenBrief = {
                viewing = r; briefText = null; briefError = null
                scope.launch {
                    client.buildRequestBriefOwned(r.id)
                        .onSuccess { briefText = it }
                        .onFailure { briefError = it.message ?: "Gagal memuat brief" }
                }
            }) }
        }
    }

    viewing?.let { r ->
        BriefViewerDialog(
            title = "Brief ${r.moduleId}" + if (r.briefVersion > 1) " (v${r.briefVersion})" else "",
            markdown = briefText,
            error = briefError,
            onDismissRequest = { viewing = null }
        )
    }
}

/** Satu baris permintaan: modul + alasan + status badge + quote bila ada + buka brief beku. */
@Composable
private fun BuilderHandoffRowCard(row: BuilderHandoffRow, onOpenBrief: () -> Unit) {
    val typography = rememberClayTypography()
    ClayCard {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.moduleId,
                    style = typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                ClayBadge(text = row.statusRaw.ifBlank { "?" }, tint = buildRequestStatusTint(row.status))
                if (row.briefVersion > 1) ClayBadge(text = "v${row.briefVersion}", tint = WeMadeColors.Primary)
                row.quoteId?.let {
                    ClayBadge(text = "Kutipan: $it", tint = WeMadeColors.Info)
                }
                if (row.hasBrief) {
                    ClayButton(
                        text = "Lihat brief",
                        style = ClayButtonStyle.Secondary,
                        fontSize = 11.sp,
                        onClick = onOpenBrief
                    )
                } else {
                    Text(
                        text = "Tanpa brief",
                        style = typography.bodySmall,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceDisabled
                    )
                }
            }
            // Elemen yang boleh mengalah: alasan + keterangan penggantian (Kontrak 13 design-system-rules).
            Text(
                text = row.detailText(),
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Alasan permintaan + keterangan SUPERSEDED apa adanya — tanpa dipalsukan jadi status lain. */
internal fun BuilderHandoffRow.detailText(): String {
    val reason = reason.ifBlank { "Tanpa catatan alasan (permintaan lama)." }
    val suffix = if (status == BuildRequestStatus.SUPERSEDED) {
        supersededBy?.let { " · digantikan oleh $it" } ?: " · digugurkan (modul tak lagi aktif)"
    } else ""
    return reason + suffix
}
