package com.eventverse.app.presentation.builder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch

private data class DeploymentRow(
    val number: Int,
    val status: String,
    val packVersion: Int?,
    val appBuild: String?
)

private fun parseDeployments(raw: JsonValue): List<DeploymentRow> =
    ((raw as? JsonValue.Obj)?.get("deployments") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>()
        ?.map { d ->
            DeploymentRow(
                number = (d.get("number") as? JsonValue.Num)?.asInt ?: 0,
                status = d.string("status").orEmpty(),
                packVersion = (d.get("packVersion") as? JsonValue.Num)?.asInt,
                appBuild = d.string("appBuild")
            )
        }.orEmpty()

/**
 * Pane Deployments (FR-M2-1/2/3): riwayat append-only + tombol Deploy/Rollback. Rollback yang
 * menurunkan struktur dengan data hidup ditolak server (409) — pesan gerbang datanya tampil apa
 * adanya; keputusan "Arsipkan modul" (force) tetap di tangan manusia.
 */
@Composable
fun BuilderDeploymentsPane(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()

    var rows by remember { mutableStateOf<List<DeploymentRow>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.deployments().onSuccess { rows = parseDeployments(it) }.onFailure { error = it.message }
    }


    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Deployments",
                style = typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClayButton(
                text = if (busy) "Memproses…" else "Deploy",
                enabled = !busy,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        client.deploy()
                            .onSuccess { client.deployments().onSuccess { r -> rows = parseDeployments(r) } }
                            .onFailure { e -> error = e.message }
                        busy = false
                    }
                }
            )
            ClayButton(
                text = "Rollback",
                enabled = !busy && rows.any { it.status == "ACTIVE" },
                style = ClayButtonStyle.Secondary,
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        client.rollback()
                            .onSuccess { client.deployments().onSuccess { r -> rows = parseDeployments(r) } }
                            .onFailure { e -> error = e.message }
                        busy = false
                    }
                }
            )
        }
        error?.let {
            Text(
                text = it,
                color = WeMadeColors.Error,
                style = typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            items(rows, key = { it.number }) { r ->
                ClayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${r.number}",
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = r.status,
                            tint = when (r.status) {
                                "ACTIVE", "IMPORTED" -> WeMadeColors.Success
                                "BLOCKED_ON_BUILD" -> WeMadeColors.Warning
                                "ROLLED_BACK", "FAILED" -> WeMadeColors.Error
                                else -> WeMadeColors.OnSurfaceMuted
                            }
                        )
                        Text(
                            text = r.packVersion?.let { "versi pack $it" }
                                ?: r.appBuild?.let { "build $it" }
                                ?: "tanpa versi",
                            style = typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
