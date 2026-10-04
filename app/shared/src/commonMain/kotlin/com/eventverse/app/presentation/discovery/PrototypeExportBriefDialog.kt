package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.discovery.brief.BriefCoverage
import com.eventverse.app.domain.discovery.brief.BriefEntity
import com.eventverse.app.domain.discovery.brief.BriefField
import com.eventverse.app.domain.discovery.brief.BriefModule
import com.eventverse.app.domain.discovery.brief.BriefRenderer
import com.eventverse.app.domain.discovery.brief.BriefScreen
import com.eventverse.app.domain.discovery.brief.RequirementsBrief
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.pack.SpecOpCodec

/**
 * Dialog Ekspor Brief Kebutuhan (TRD-PLAT-003, butir A5).
 * Merakit log perubahan klien dari [session] dan mengirimkannya ke endpoint brief,
 * atau menghasilkan dokumen Markdown deterministik lewat [BriefRenderer].
 */
@Composable
fun PrototypeExportBriefDialog(
    draft: DiscoveryDraftUi,
    includedModuleIds: Set<String>,
    session: PrototypeSession,
    onDismissRequest: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val client = remember { BuilderApiClient() }

    var markdownContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var copied by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null

        // Susun payload changes
        val changesJson = com.eventverse.app.shared.json.jsonArrayOf(
            session.captureLog.map(SpecOpCodec::encode)
        ).encode()

        val serverResult = runCatching {
            val res = client.exportBrief(includedModuleIds, changesJson).getOrThrow()
            (res as? JsonValue.Obj)?.string("markdown")
        }.getOrNull()

        if (serverResult != null) {
            markdownContent = serverResult
            isLoading = false
        } else {
            // Fallback rendering lokal deterministik (pola BriefRenderer)
            val briefModules = draft.modules
                .filter { it.id in includedModuleIds }
                .map { m ->
                    val screens = draft.screens
                        .filter { it.moduleId == m.id }
                        .map { s -> BriefScreen(s.title, s.widget, s.interactive?.spec?.screens?.firstOrNull()?.entityId) }
                    val entities = draft.screens
                        .filter { it.moduleId == m.id }
                        .mapNotNull { it.interactive?.spec?.entities }
                        .flatten()
                        .distinctBy { it.id }
                        .map { e ->
                            BriefEntity(
                                id = e.id,
                                label = e.label,
                                fields = e.fields.map { BriefField(it.label, it.type.name, it.required, it.options) },
                                statusField = e.stateMachine?.field,
                                transitions = e.stateMachine?.transitions?.mapValues { it.value.toList() } ?: emptyMap()
                            )
                        }
                    BriefModule(m.id, m.displayName, screens, entities)
                }

            val coverage = draft.modules
                .filter { it.id in includedModuleIds }
                .map { m ->
                    BriefCoverage(
                        moduleId = m.id,
                        displayName = m.displayName,
                        covered = true,
                        monthlyIdr = 0L,
                        gapLowIdr = null,
                        gapHighIdr = null
                    )
                }

            val localBrief = RequirementsBrief(
                packCode = draft.packCode,
                modules = briefModules,
                changes = session.captureLog.toList(),
                coverage = coverage,
                customNeeds = emptyList()
            )

            markdownContent = BriefRenderer.markdown(localBrief)
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Brief Kebutuhan Prototype",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text(
                    text = "Dokumen Markdown terstruktur hasil diskusi dan uji coba prototype untuk tim pengembang.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )

                if (isLoading) {
                    Text(
                        text = "Menyusun brief…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = WeMadeColors.Primary
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp, max = 360.dp)
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = WeMadeColors.SurfaceMuted.copy(alpha = 0.5f),
                                outline = WeMadeColors.Outline.copy(alpha = 0.3f),
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Md)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = markdownContent.orEmpty(),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }

                if (copied) {
                    Text(
                        text = "✓ Teks brief disalin ke clipboard!",
                        style = MaterialTheme.typography.bodySmall,
                        color = WeMadeColors.Success,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        confirmButton = {
            ClayButton(
                text = if (copied) "Tersalin" else "Salin ke Clipboard",
                style = ClayButtonStyle.Primary,
                enabled = !isLoading && markdownContent != null,
                onClick = {
                    markdownContent?.let {
                        clipboardManager.setText(AnnotatedString(it))
                        copied = true
                    }
                }
            )
        },
        dismissButton = {
            ClayButton(
                text = "Tutup",
                style = ClayButtonStyle.Secondary,
                onClick = onDismissRequest
            )
        }
    )
}
