package com.eventverse.app.presentation.builder.audit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.audit.AuditAction
import com.eventverse.app.domain.audit.AuditLogEntry
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * Pane Audit (TRD-PLAT-014 FR-5-2, sisi owner): jejak kronologis aksi atas tenant ini — aksi,
 * aktor, waktu, ringkasan — dengan filter per jenis aksi, plus kartu Postur Security di kepala.
 *
 * Semua entri dibaca lewat codec kontrak Track 0 di ViewModel: aksi/peran/waktu tak dikenal
 * ditolak server-side codec-nya dan tampil sebagai galat, bukan baris yang dipalsukan. Gate
 * `MANAGE_BUILDER` dijaga server; klien tidak pernah mengirim identitas tenant.
 */
@Composable
fun BuilderAuditPane(modifier: Modifier = Modifier, viewModel: BuilderAuditViewModel = remember { BuilderAuditViewModel() }) {
    val state by viewModel.uiState.collectAsState()
    val typography = rememberClayTypography()

    LaunchedEffect(Unit) { viewModel.onEvent(BuilderAuditUiEvent.Load) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "Audit",
                    style = typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Jejak aksi platform atas tenant ini: siapa, kapan, apa (50 terakhir).",
                    style = typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayButton(
                text = "Muat ulang",
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                onClick = { viewModel.onEvent(BuilderAuditUiEvent.Load) }
            )
        }

        BuilderSecurityPostureCard(posture = state.posture)

        state.error?.let {
            Text(
                text = it,
                color = WeMadeColors.Error,
                style = typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (state.entries.isNotEmpty()) {
            BuilderAuditFilterRow(
                actions = state.entries.map { it.action }.distinct(),
                selected = state.filter,
                onSelect = { viewModel.onEvent(BuilderAuditUiEvent.Filter(it)) }
            )
        }

        val visible = state.visibleEntries()
        if (visible.isEmpty() && state.error == null && !state.isLoading) {
            Text(
                text = if (state.filter == null) {
                    "Belum ada jejak audit. Aksi deploy, rollback, dan tagihan akan tercatat di sini."
                } else {
                    "Tidak ada entri untuk jenis aksi ini."
                },
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        if (state.isLoading) {
            Text(
                text = "Memuat jejak audit...",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            visible.forEach { entry -> BuilderAuditEntryCard(entry = entry) }
        }
    }
}

/** Filter per jenis aksi: satu chip "Semua" + satu chip per jenis aksi yang ada di jejak. */
@Composable
private fun BuilderAuditFilterRow(
    actions: List<AuditAction>,
    selected: AuditAction?,
    onSelect: (AuditAction?) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayChoiceChip(text = "Semua", selected = selected == null, onClick = { onSelect(null) }, fontSize = 10.sp)
        actions.forEach { action ->
            ClayChoiceChip(text = action.code, selected = selected == action, onClick = { onSelect(action) }, fontSize = 10.sp)
        }
    }
}

/** Satu entri kronologis: aksi (badge), aktor + peran, waktu, ringkasan. */
@Composable
private fun BuilderAuditEntryCard(entry: AuditLogEntry) {
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
                ClayBadge(
                    text = entry.action.code,
                    tint = actionTint(entry.action),
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = formatAuditTime(entry.occurredAt),
                    style = typography.bodySmall,
                    fontSize = 10.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            Text(
                text = entry.summary,
                style = typography.bodySmall,
                color = WeMadeColors.OnSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${entry.actorUserId} · ${entry.actorRole.name}",
                style = typography.bodySmall,
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Warna jenis aksi dari token theme: uang = oranye, rilis = biru/hijau, pembatalan = merah. */
private fun actionTint(action: AuditAction) = when (action) {
    AuditAction.MODULE_PRICE_QUOTED,
    AuditAction.MODULE_BUILD_RECORDED,
    AuditAction.BUILDER_INVOICE_ISSUED,
    AuditAction.BUILDER_INVOICE_PAID,
    AuditAction.TENANT_TIER_UPDATED,
    AuditAction.TENANT_TRIAL_EXTENDED -> WeMadeColors.Accent
    AuditAction.BUILDER_DEPLOYMENT_ROLLED_BACK -> WeMadeColors.Error
    AuditAction.BUILDER_DEPLOYMENT_ACTIVATED -> WeMadeColors.Success
    AuditAction.TENANT_ENTITLEMENT_UPDATED,
    AuditAction.TENANT_DOMAIN_PACK_ASSIGNED,
    AuditAction.TENANT_DOMAIN_PACK_SHARED,
    AuditAction.PLATFORM_ACT_AS_STARTED -> WeMadeColors.Primary
}

/** Waktu audit ringkas ("16 Okt 2026, 10:00") — ISO server dibaca apa adanya, zona default host. */
internal fun formatAuditTime(instant: kotlinx.datetime.Instant): String {
    val local = instant.toLocalDateTime(TimeZone.currentSystemDefault())
    val months = listOf("", "Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    val month = months.getOrNull(local.monthNumber) ?: local.monthNumber.toString()
    val hh = local.hour.toString().padStart(2, '0')
    val mm = local.minute.toString().padStart(2, '0')
    return "${local.dayOfMonth} $month ${local.year}, $hh:$mm"
}
