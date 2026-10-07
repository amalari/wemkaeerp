package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    // Utas aktif (null = Semua), label fase run (SSE) untuk indikator memuat, dan follow-up menunggu per utas.
    var thread by remember { mutableStateOf<String?>(null) }
    var runPhase by remember { mutableStateOf<String?>(null) }
    var pendingByThread by remember { mutableStateOf<Map<String?, Int>>(emptyMap()) }

    suspend fun reloadChat() {
        client.chat(thread).onSuccess { raw ->
            messages = parseMessages(raw)
            // Follow-up dihitung server setiap riwayat dimuat; utas Semua mencakup semua utas.
            pendingByThread = pendingByThread + (thread to parseFollowUp(raw).size)
        }.onFailure { error = it.message }
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
    val shown = remember(messages, draft, loaded, thread) {
        val d = draft
        if (loaded && messages.isEmpty() && d != null && thread == null) listOf(openingEntryFor(d)) else messages
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
            runPhase = null
            // Run asinkron: 202 + SSE progres. Riwayat di DB tetap sumber kebenaran, jadi SSE yang putus tidak
            // menghilangkan hasil — setelah selesai (atau putus) riwayat dimuat ulang.
            client.startRun(text, thread)
                .onSuccess { runId ->
                    input = ""
                    reloadChat()   // tampilkan pesan pengguna segera
                    runCatching {
                        client.runEvents(runId).collect { ev ->
                            when (ev.type) {
                                "status" -> runPhase = ev.field("phase")
                                "question" -> runPhase = "waiting"
                                "error" -> error = ev.field("message") ?: "Proses gagal"
                                else -> Unit
                            }
                        }
                    }.onFailure { e -> error = e.message ?: "Koneksi progres terputus; memuat ulang riwayat" }
                    reloadChat()
                    reloadDraft()
                }
                .onFailure { e -> error = e.message }
            runPhase = null
            busy = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= ClayBreakpoints.MasterDetail
        val threads = remember(draft) { threadsOf(draft?.activeModules.orEmpty().map { it.id to it.displayName }) }
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
                        Text(runPhaseLabel(runPhase), style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
                    }
                }
                ChatComposer(
                    value = input, onValueChange = { input = it }, busy = busy, onSend = ::send,
                    // Ada pertanyaan agent yang menunggu di utas ini: ketikan berikutnya adalah jawabannya.
                    placeholder = if ((pendingByThread[thread] ?: 0) > 0) "Ketik jawaban Anda untuk pertanyaan di atas"
                    else "Contoh: pabrik kaos FOB dengan tahap sablon"
                )
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            ChatResultPanel(
                draft = draft?.focusedOn(thread),
                patchPreview = previewSummary,
                onClose = if (wide) null else ({ panelOpen = false }),
                modifier = m,
                focusModuleId = thread
            )
        }
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            // Tab utas: "Semua" (tanpa filter) + satu tab per modul; memilih tab memuat ulang riwayat utas itu.
            if (threads.size > 1) ChatThreadTabs(
                threads = threads,
                selected = thread,
                pending = pendingByThread,
                onSelect = { picked ->
                    if (!busy && picked != thread) {
                        thread = picked
                        messages = emptyList()
                        scope.launch {
                            // Membuka tab modul: server menghitung celah modul itu dan membuat pertanyaan bila ada (idempoten).
                            if (picked != null) client.requestFollowUps(picked)
                            reloadChat()
                        }
                    }
                }
            )
            Box(Modifier.fillMaxWidth().weight(1f)) {
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
    }
}
