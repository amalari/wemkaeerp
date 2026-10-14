package com.eventverse.app.presentation.builder.chat

import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.ChatMessageKind
import com.eventverse.app.domain.builder.StageSketch
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.shared.builder.ModuleBriefCodec
import com.eventverse.app.shared.json.JsonValue

/** Bagian BRIEF satu pesan chat (TRD-BLDR-001): fitur pelengkap, sketsa Tahap berurutan, dan asal penyusunnya. */
internal data class ChatBrief(
    val features: List<String>,
    val stages: List<StageSketch>,
    val source: BriefSource,
    val basisRef: String?
)

/**
 * Parser tunggal `kind` + bagian BRIEF pesan. `kind` tak dikenal atau bentuk BRIEF rusak **ditolak** lewat
 * [Result.failure] dengan pesan jelas (dirender sebagai galat di gelembung) — bukan jatuh senyap jadi teks biasa.
 * Kind tidak ada = TEXT (server lama).
 */
internal fun parseKindAndBrief(m: JsonValue.Obj): Result<Pair<ChatMessageKind, ChatBrief?>> = runCatching {
    val kindName = m.string("kind")
    val kind = if (kindName == null) ChatMessageKind.TEXT
    else ChatMessageKind.entries.firstOrNull { it.name == kindName } ?: error("Jenis pesan tak dikenal: '$kindName'")
    if (kind != ChatMessageKind.BRIEF) return@runCatching kind to null
    val features = ModuleBriefCodec.decodeFeatures(m.get("features") ?: error("features wajib pada pesan BRIEF"))
    val stages = ModuleBriefCodec.decodeStages(m.get("stages") ?: error("stages wajib pada pesan BRIEF"))
    val source = ModuleBriefCodec.parseSource(m.string("briefSource") ?: error("briefSource wajib pada pesan BRIEF"))
    kind to ChatBrief(features, stages, source, m.string("basisRef"))
}

/** Id pesan BRIEF yang tampil penuh: yang TERBARU per utas modul (urutan kronologis); sisanya dilipat (FR-7). */
internal fun latestBriefIds(entries: List<BuilderChatEntry>): Set<String> =
    entries.filter { it.kind == ChatMessageKind.BRIEF }
        .groupBy { it.moduleId }
        .mapNotNull { (_, briefs) -> briefs.lastOrNull()?.id }
        .toSet()

/**
 * Entri yang ditampilkan: riwayat bila ada; bila kosong dan draf ada, pesan pembuka lokal. Pembuka modul hanya
 * KEADAAN KOSONG — begitu utas modul punya BRIEF (atau riwayat apa pun) ia tidak tampil.
 */
internal fun shownEntries(
    messages: List<BuilderChatEntry>,
    draft: DiscoveryDraftUi?,
    loaded: Boolean,
    thread: String?
): List<BuilderChatEntry> = when {
    !loaded || messages.isNotEmpty() || draft == null -> messages
    thread == null -> listOf(openingEntryFor(draft))
    else -> listOf(openingEntryForModule(draft, thread))
}

/**
 * Pesan hasil "Segarkan ringkasan". `skipped` BUKAN "sudah terbaru": modul itu belum punya bahan (tautan wawancara atau
 * layar berproposal), jadi pengguna perlu bercerita dulu - pesan yang menyamarkan keduanya menyesatkan. Teks Latin-1.
 */
internal fun briefRefreshNotice(o: com.eventverse.app.infrastructure.api.BriefRefreshOutcome): String = when {
    !o.featureEnabled -> "Fitur ringkasan belum aktif di server ini."
    o.created > 0 -> "Ringkasan disegarkan (${o.created})."
    o.fresh > 0 -> "Ringkasan sudah terbaru."
    o.skipped > 0 -> "Belum ada bahan untuk ringkasan modul ini. Ceritakan alurnya di utas Semua dulu."
    else -> "Tidak ada modul yang perlu diringkas."
}
