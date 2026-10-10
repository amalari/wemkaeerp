package com.eventverse.app.presentation.builder

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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import kotlinx.coroutines.launch
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Pane Antrian Pembuatan (FR-M2-4, konsol superadmin).
 *
 * Antrean ini **lintas tenant** — satu-satunya daftar di konsol ini yang memang begitu, karena yang
 * mengerjakannya platform, bukan pabrik. Karena itu pane ini menampilkan penolakan server apa adanya:
 * tenant yang membukanya menerima 403 dan membaca "butuh wewenang superadmin", bukan daftar kosong
 * yang menyiratkan "tidak ada pekerjaan".
 */
internal data class BuildQueueRow(
    val id: String,
    val tenantId: String,
    val moduleId: String,
    val status: String,
    val reason: String,
    /** Ada brief beku untuk permintaan ini; false untuk permintaan lama atau bila penyusunan brief gagal. */
    val hasBrief: Boolean = false,
    /** Versi brief modul ini (naik tiap deploy ulang yang menggantikan permintaan sebelumnya). */
    val briefVersion: Int = 1,
    /** Pengganti bila SUPERSEDED; null = digugurkan tanpa pengganti (atau bukan SUPERSEDED). */
    val supersededBy: String? = null
)

internal fun parseBuildQueue(raw: JsonValue): List<BuildQueueRow> =
    ((raw as? JsonValue.Obj)?.get("requests") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>()
        ?.map { r ->
            BuildQueueRow(
                id = r.string("id").orEmpty(),
                tenantId = r.string("tenantId").orEmpty(),
                moduleId = r.string("moduleId").orEmpty(),
                status = r.string("status").orEmpty(),
                reason = r.string("reason").orEmpty(),
                hasBrief = (r.get("hasBrief") as? JsonValue.Bool)?.value == true,
                briefVersion = (r.get("briefVersion") as? JsonValue.Num)?.asInt ?: 1,
                supersededBy = r.string("supersededBy")
            )
        }
        .orEmpty()

@Composable
fun BuilderBuildQueuePane(modifier: Modifier = Modifier) {
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    var rows by remember { mutableStateOf(emptyList<BuildQueueRow>()) }
    var error by remember { mutableStateOf<String?>(null) }
    // Brief yang sedang dilihat: (permintaan, isi markdown | galat); null = dialog tertutup.
    var viewing by remember { mutableStateOf<BuildQueueRow?>(null) }
    var briefText by remember { mutableStateOf<String?>(null) }
    var briefError by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    LaunchedEffect(Unit) {
        client.buildQueue()
            .onSuccess { rows = parseBuildQueue(it) }
            .onFailure { e -> error = e.message }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = "Antrian Pembuatan",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Text(
            text = "Permintaan kode modul dari seluruh tenant. Dikelola platform, bukan self-service.",
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

        if (rows.isEmpty() && error == null) {
            Text(
                text = "Antrean kosong - semua modul yang diminta tenant sudah tersedia.",
                style = typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Column, bukan LazyColumn: shell sudah `verticalScroll` (lihat catatan pane Billing).
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            rows.forEach { r ->
                ClayCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = r.moduleId,
                            style = typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayBadge(
                            text = r.status,
                            tint = when (r.status) {
                                "QUEUED" -> WeMadeColors.Warning
                                "QUOTED", "APPROVED" -> WeMadeColors.Primary
                                "IN_PROGRESS" -> WeMadeColors.Primary
                                "SHIPPED" -> WeMadeColors.Success
                                "REJECTED" -> WeMadeColors.Error
                                else -> WeMadeColors.OnSurfaceMuted
                            }
                        )
                        if (r.briefVersion > 1) ClayBadge(text = "v${r.briefVersion}", tint = WeMadeColors.Primary)
                        Text(
                            text = "${r.tenantId} · ${r.reason}" + if (r.status == "SUPERSEDED") {
                                r.supersededBy?.let { " · digantikan oleh $it" } ?: " · digugurkan (modul tak lagi aktif)"
                            } else "",
                            style = typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.weight(1f, fill = false),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (r.hasBrief) {
                            ClayButton(
                                text = "Lihat brief",
                                style = ClayButtonStyle.Secondary,
                                onClick = {
                                    viewing = r; briefText = null; briefError = null
                                    scope.launch {
                                        client.buildRequestBrief(r.id).onSuccess { briefText = it }.onFailure { briefError = it.message ?: "Gagal memuat brief" }
                                    }
                                }
                            )
                        } else {
                            Text("Tanpa brief", style = typography.bodySmall, color = WeMadeColors.OnSurfaceDisabled)
                        }
                    }
                }
            }
        }
    }

    viewing?.let { r ->
        BriefViewerDialog(
            title = "Brief ${r.moduleId} (${r.tenantId})",
            markdown = briefText,
            error = briefError,
            onDismissRequest = { viewing = null }
        )
    }
}
