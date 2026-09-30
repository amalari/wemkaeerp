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
import androidx.compose.ui.unit.sp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch

private data class BuilderChatEntry(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val summary: List<String>,
    val hasPendingPatch: Boolean,
    val applied: Boolean
)

private fun parseMessages(raw: JsonValue): List<BuilderChatEntry> =
    ((raw as? JsonValue.Obj)?.get("messages") as? JsonValue.Arr)?.items
        ?.filterIsInstance<JsonValue.Obj>()
        ?.map { m ->
            BuilderChatEntry(
                id = m.string("id").orEmpty(),
                isUser = m.string("role") == "USER",
                text = m.string("text").orEmpty(),
                summary = (m.get("summary") as? JsonValue.Arr)?.items
                    ?.mapNotNull { (it as? JsonValue.Str)?.value }.orEmpty(),
                hasPendingPatch = (m.get("hasPendingPatch") as? JsonValue.Bool)?.value == true,
                applied = (m.get("appliedDraftId") as? JsonValue.Str)?.value != null
            )
        }.orEmpty()

/**
 * Pane Chat Builder (FR-M1-1/2/3): cerita → usulan patch → Terapkan. Patch usulan **tidak otomatis**
 * jadi draf — tombol Terapkan yang memutuskan (plan §4); gagal validasi = pesan kesalahan tampil,
 * draf lama tetap utuh.
 */
@Composable
fun BuilderChatPane(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()

    var messages by remember { mutableStateOf<List<BuilderChatEntry>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        client.chat().onSuccess { messages = parseMessages(it) }
            .onFailure { error = it.message }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = "Ceritakan yang ingin dibangun",
            style = typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        error?.let {
            Text(
                text = it,
                color = WeMadeColors.Error,
                style = typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            items(messages, key = { it.id }) { m ->
                ClayCard {
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), verticalAlignment = Alignment.CenterVertically) {
                            ClayBadge(
                                text = if (m.isUser) "Anda" else "Agent",
                                tint = if (m.isUser) WeMadeColors.Primary else WeMadeColors.Success
                            )
                            if (m.hasPendingPatch) ClayBadge(text = "Usulan patch", tint = WeMadeColors.Warning)
                            if (m.applied) ClayBadge(text = "Diterapkan", tint = WeMadeColors.Success)
                        }
                        Text(
                            text = m.text,
                            style = typography.bodyMedium,
                            color = WeMadeColors.OnSurface,
                            maxLines = 12,
                            overflow = TextOverflow.Ellipsis
                        )
                        m.summary.forEach { line ->
                            Text(
                                text = "• $line",
                                style = typography.bodySmall,
                                fontSize = 12.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                        }
                        if (m.hasPendingPatch) {
                            ClayButton(
                                text = if (busy) "Memproses…" else "Terapkan",
                                enabled = !busy,
                                onClick = {
                                    scope.launch {
                                        busy = true
                                        client.applyPatch(m.id)
                                            .onSuccess { client.chat().onSuccess { r -> messages = parseMessages(r) } }
                                            .onFailure { e -> error = e.message }
                                        busy = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = "Contoh: pabrik kaos FOB dengan tahap sablon",
                modifier = Modifier.weight(1f)
            )
            ClayButton(
                text = if (busy) "Mengirim…" else "Kirim",
                enabled = !busy && input.isNotBlank(),
                onClick = {
                    scope.launch {
                        busy = true
                        error = null
                        client.sendMessage(input.trim())
                            .onSuccess { messages = parseMessages(it); input = "" }
                            .onFailure { e -> error = e.message }
                        busy = false
                    }
                }
            )
        }
    }
}
