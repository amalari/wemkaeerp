package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch

/**
 * Chat Builder (FR-M1-1/2/3): dua panel — percakapan di kiri (riwayat + komposer menempel di bawah),
 * "Hasil request" selalu terlihat di kanan; layar sempit menukar keduanya lewat tombol Hasil. Patch usulan **tidak
 * otomatis** jadi draf — tombol Terapkan yang memutuskan (plan §4); draf dimuat ulang setelah Terapkan.
 */
@Composable
fun BuilderChatPane(modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<BuilderChatEntry>>(emptyList()) }
    var draft by remember { mutableStateOf<DiscoveryDraftUi?>(null) }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var panelOpen by remember { mutableStateOf(false) }
    var previewSummary by remember { mutableStateOf<List<String>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }

    suspend fun reloadChat() {
        client.chat().onSuccess { messages = parseMessages(it) }.onFailure { error = it.message }
    }
    suspend fun reloadDraft() {
        client.draft().onSuccess { raw ->
            draft = (raw as? JsonValue.Obj)?.let { runCatching { DiscoveryDraftUi.fromJson(it) }.getOrNull() }
        }
    }
    LaunchedEffect(Unit) {
        reloadChat()
        reloadDraft()
        loaded = true
    }

    // Riwayat kosong tetapi draf tenant ada → satu pesan pembuka lokal supaya chat tidak kosong.
    val shown = remember(messages, draft, loaded) {
        val d = draft
        if (loaded && messages.isEmpty() && d != null) listOf(openingEntryFor(d)) else messages
    }
    LaunchedEffect(shown.size) {
        if (shown.isNotEmpty()) listState.animateScrollToItem(shown.lastIndex)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || busy) return
        scope.launch {
            busy = true
            error = null
            // POST mengembalikan array telanjang, GET mengembalikan envelope — baca ulang lewat GET.
            client.sendMessage(text)
                .onSuccess { input = ""; reloadChat() }
                .onFailure { e -> error = e.message }
            busy = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= ClayBreakpoints.MasterDetail
                val chat: @Composable (Modifier) -> Unit = { m ->
            Column(modifier = m, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Ceritakan yang ingin dibangun",
                        style = typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    if (!wide) ClayButton(
                        text = "Hasil",
                        onClick = { previewSummary = emptyList(); panelOpen = true },
                        style = ClayButtonStyle.Secondary,
                        leading = { IconLayers(Modifier.size(14.dp)) }
                    )
                }
                error?.let {
                    Text(it, color = WeMadeColors.Error, style = typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    items(shown, key = { it.id }) { entry ->
                        ChatMessageBubble(
                            entry = entry,
                            busy = busy,
                            onShowResult = {
                                previewSummary = if (entry.hasPendingPatch) entry.summary else emptyList()
                                panelOpen = true
                            },
                            onApply = {
                                scope.launch {
                                    busy = true
                                    client.applyPatch(entry.id)
                                        .onSuccess { reloadChat(); reloadDraft(); previewSummary = emptyList() }
                                        .onFailure { e -> error = e.message }
                                    busy = false
                                }
                            }
                        )
                    }
                    if (busy) item(key = "typing") {
                        Text("Agent mengetik…", style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
                    }
                }
                ChatComposer(value = input, onValueChange = { input = it }, busy = busy, onSend = ::send)
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            ChatResultPanel(
                draft = draft,
                patchPreview = previewSummary,
                onClose = if (wide) null else ({ panelOpen = false }),
                modifier = m
            )
        }
        when {
            wide -> Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
                chat(Modifier.weight(0.42f).fillMaxHeight())
                panel(Modifier.weight(0.58f).fillMaxHeight())
            }
            panelOpen -> panel(Modifier.fillMaxSize())
            else -> chat(Modifier.fillMaxSize())
        }
    }
}
