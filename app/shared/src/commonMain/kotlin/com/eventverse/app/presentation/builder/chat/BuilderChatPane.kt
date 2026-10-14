package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.builder.BuilderDraftState
import com.eventverse.app.presentation.builder.draftOrNull
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
fun BuilderChatPane(templateCode: String? = null, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    val client = remember { BuilderApiClient() }
    val typography = rememberClayTypography()
    val listState = rememberLazyListState()

    var messages by remember { mutableStateOf<List<BuilderChatEntry>>(emptyList()) }
    var draftState by remember { mutableStateOf<BuilderDraftState>(BuilderDraftState.Loading) }
    val draft = draftState.draftOrNull
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
    // Follow-up modal state: tracking questions dan visibility
    var currentQuestions by remember { mutableStateOf<List<PendingQuestion>>(emptyList()) }
    var showFollowUpModal by remember { mutableStateOf(false) }
    // Pertanyaan yang sengaja dilewati pengguna: tidak dimunculkan lagi oleh reload berikutnya.
    var skippedQuestionIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    // Wizard: sekali semai pesan pembuka dari templateCode (prop route internal), tidak pernah ganda.
    var templateInitialized by remember { mutableStateOf(false) }

    suspend fun reloadChat() {
        client.chat(thread).onSuccess { raw ->
            messages = parseMessages(raw)
            // Follow-up dihitung server setiap riwayat dimuat; utas Semua mencakup semua utas.
            // Modal hanya memuat pertanyaan milik utas aktif: utas modul = modul itu saja, Semua = pertanyaan umum
            // (moduleId null); follow-up modul lain tidak bocor ke sini.
            val all = parseFollowUp(raw)
            val followUps = all.filter { it.moduleId == thread }
            // Utas Semua memuat follow-up semua utas, jadi dari sana badge SEMUA tab dihitung sekaligus; utas modul
            // hanya tahu miliknya sendiri.
            pendingByThread = if (thread == null) all.groupBy { it.moduleId }.mapValues { it.value.size }
            else pendingByThread + (thread to followUps.size)
            val visible = followUps.filter { it.id !in skippedQuestionIds }
            currentQuestions = visible
            showFollowUpModal = visible.isNotEmpty()
        }.onFailure { error = it.message }
    }
    suspend fun reloadDraft() {
        draftState = BuilderDraftState.load(client)
    }
    LaunchedEffect(Unit) {
        reloadChat()
        reloadDraft()
        loaded = true
    }

    // Follow-up tiap modul disiapkan sekali saat draf termuat (idempoten di server), supaya badge tab modul sudah
    // terisi dari awal — bukan baru muncul setelah modulnya dibuka.
    val moduleIds = draft?.activeModules.orEmpty().map { it.id }
    LaunchedEffect(moduleIds) {
        if (moduleIds.isEmpty()) return@LaunchedEffect
        moduleIds.forEach { client.requestFollowUps(it) }
        if (thread == null) reloadChat()
    }

    // Wizard: templateCode datang dari route internal (`/builder/chat?template=...`), bukan URL browser —
    // jadi berperilaku sama di kelima target KMP. Kode tak dikenal → tidak ada yang dikirim (tanpa fallback senyap).
    LaunchedEffect(templateCode, loaded, draft) {
        val seed = templateCode?.let(::templateSeedPrompt) ?: return@LaunchedEffect
        if (!loaded || templateInitialized || draft == null || seed.isEmpty() || busy) return@LaunchedEffect
        templateInitialized = true
        input = seed
        scope.launch {
            busy = true
            error = null
            runPhase = null
            client.startRun(seed, thread)
                .onSuccess { runId ->
                    input = ""
                    reloadChat()   // tampilkan pesan pengguna segera
                    runCatching {
                        client.runEvents(runId).collect { ev ->
                            when (ev.type) {
                                "status" -> runPhase = ev.field("phase")
                                "question" -> runPhase = "waiting"
                                "module_started" -> runPhase = "module:${ev.field("title") ?: ev.field("moduleId").orEmpty()}"
                                "module_ready" -> runPhase = "ready:${ev.field("title") ?: ev.field("moduleId").orEmpty()}"
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

    // Riwayat kosong tetapi draf tenant ada → satu pesan pembuka lokal supaya chat tidak kosong.
    val shown = remember(messages, draft, loaded, thread) { shownEntries(messages, draft, loaded, thread) }
    val latestBriefs = remember(shown) { latestBriefIds(shown) }
    // Segarkan ringkasan BRIEF (TRD-BLDR-001 FR-8); 404 = fitur belum aktif di server, pesan ramah (bukan galat).
    var briefsBusy by remember { mutableStateOf(false) }
    var briefNotice by remember { mutableStateOf<String?>(null) }
    fun refreshBriefs() {
        if (briefsBusy || busy) return
        scope.launch {
            briefsBusy = true
            briefNotice = null
            client.requestBriefs(thread)
                .onSuccess { o ->
                    briefNotice = briefRefreshNotice(o)
                    reloadChat()
                }
                .onFailure { e -> error = e.message }
            briefsBusy = false
        }
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
                                "module_started" -> runPhase = "module:${ev.field("title") ?: ev.field("moduleId").orEmpty()}"
                                "module_ready" -> runPhase = "ready:${ev.field("title") ?: ev.field("moduleId").orEmpty()}"
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

    fun skipFollowUps() {
        skippedQuestionIds = skippedQuestionIds + currentQuestions.map { it.id }
        showFollowUpModal = false
        currentQuestions = emptyList()
    }

    fun submitFollowUpAnswers(answers: List<Pair<String, String>>) {
        if (answers.isEmpty() || busy) return
        // Filter hanya pertanyaan yang sudah dijawab (tidak kosong)
        val validAnswers = answers.filter { it.second.isNotBlank() }
        if (validAnswers.isEmpty()) {
            skipFollowUps()
            return
        }
        scope.launch {
            busy = true
            error = null
            // Format: "Jawab pertanyaan {id}: {jawaban}, {id}: {jawaban}, ..." dan answered list
            val text = validAnswers.joinToString(", ") { (id, answ) ->
                "pertanyaan $id: $answ"
            }.let { "Jawab $it" }
            client.startRun(text, thread, validAnswers)
                .onSuccess { runId ->
                    showFollowUpModal = false
                    currentQuestions = emptyList()
                    reloadChat()
                    reloadDraft()
                }
                .onFailure { e -> error = e.message }
            busy = false
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val wide = maxWidth >= ClayBreakpoints.MasterDetail
        val chatWidth = when {
            maxWidth >= 1500.dp -> 400.dp
            maxWidth >= 1200.dp -> 370.dp
            maxWidth >= 1024.dp -> 340.dp
            else -> 320.dp
        }
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
                if (threads.size > 1) {
                    SelectedModuleChatCard(
                        draft = draft,
                        selectedModuleId = thread,
                        threads = threads,
                        pendingByThread = pendingByThread,
                        briefsBusy = briefsBusy,
                        briefNotice = briefNotice,
                        onRefreshBriefs = ::refreshBriefs,
                        onSelectThread = { picked ->
                            if (!busy && picked != thread) {
                                thread = picked
                                messages = emptyList()
                                currentQuestions = emptyList()
                                showFollowUpModal = false
                                scope.launch {
                                    if (picked != null) client.requestFollowUps(picked)
                                    reloadChat()
                                }
                            }
                        }
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
                            briefExpanded = entry.id in latestBriefs,
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
                if (showFollowUpModal && currentQuestions.isNotEmpty()) {
                    FollowUpModal(
                        questions = currentQuestions,
                        moduleId = thread,
                        onSubmit = ::submitFollowUpAnswers,
                        onSkip = ::skipFollowUps,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 360.dp)
                    )
                } else {
                    ChatComposer(
                        value = input, onValueChange = { input = it }, busy = busy, onSend = ::send,
                        // Ada pertanyaan agent yang menunggu di utas ini: ketikan berikutnya adalah jawabannya.
                        placeholder = if ((pendingByThread[thread] ?: 0) > 0) "Ketik jawaban Anda untuk pertanyaan di atas"
                        else "Contoh: pabrik kaos FOB dengan tahap sablon"
                    )
                }
            }
        }
        val panel: @Composable (Modifier) -> Unit = { m ->
            ChatResultPanel(
                draft = draft?.focusedOn(thread),
                draftState = draftState,
                patchPreview = previewSummary,
                onClose = if (wide) null else ({ panelOpen = false }),
                modifier = m,
                focusModuleId = thread
            )
        }
        Box(Modifier.fillMaxSize()) {
            when {
                wide -> Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    chat(Modifier.width(chatWidth).fillMaxHeight())
                    // Pemisah vertikal neo-brutalist antara Chat dan Hasil Request
                    Box(
                        modifier = Modifier
                            .width(ClayBorder.Medium)
                            .fillMaxHeight()
                            .background(WeMadeColors.Outline)
                    )
                    panel(Modifier.weight(1f).fillMaxHeight())
                }
                panelOpen -> panel(Modifier.fillMaxSize())
                else -> chat(Modifier.fillMaxSize())
            }
        }
    }
}
