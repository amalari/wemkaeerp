package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.prototype.SpecOp

/**
 * Menyusun [RequirementsBrief] menjadi Markdown **deterministik** (masukan sama -> keluaran sama,
 * byte per byte) untuk developer yang tidak ikut sesi. Susunan bagian: ringkasan, modul & layar,
 * perubahan klien, cakupan katalog (sudah ada vs perlu dibangun + harga), kebutuhan kustom.
 * Renderer tidak mengenal istilah satu industri — seluruh isi datang dari data brief.
 */
object BriefRenderer {

    fun markdown(brief: RequirementsBrief): String = buildString {
        appendLine("# Brief Kebutuhan — ${brief.packCode}")
        appendLine()
        brief.revision?.let { renderRevision(it) }
        appendLine("## Ringkasan")
        val covered = brief.coverage.count { it.covered }
        val ok = brief.changes.count { it.ok }
        appendLine("- Modul: ${brief.modules.size} ($covered sudah ada, ${brief.coverage.size - covered} perlu dibangun)")
        appendLine("- Layar: ${brief.modules.sumOf { it.screens.size }}")
        appendLine("- Perubahan klien: ${brief.changes.size} ($ok diterapkan, ${brief.changes.size - ok} ditolak)")
        appendLine("- Kebutuhan kustom: ${brief.customNeeds.size}")
        appendLine()
        brief.context?.let { renderContext(it, brief) }
        appendLine("## Modul & layar")
        if (brief.modules.isEmpty()) appendLine("_Belum ada modul dipilih._")
        brief.modules.forEach { m ->
            appendLine("### ${m.displayName} (`${m.moduleId}`)")
            m.screens.forEach { s ->
                val target = s.entityId?.let { "entitas `$it`" } ?: "tanpa entitas"
                appendLine("- Layar: ${s.title} (${s.widget}) -> $target")
            }
            m.entities.forEach { e ->
                appendLine("- Entitas `${e.id}` — ${e.label}")
                e.fields.forEach { f ->
                    val opts = if (f.options.isEmpty()) "" else " (opsi: ${f.options.joinToString(" | ")})"
                    val req = if (f.required) ", wajib" else ""
                    appendLine("  - ${f.label}: ${f.type}$req$opts")
                }
                when {
                    e.statusField == null || e.transitions.isEmpty() -> {}
                    else -> e.transitions.forEach { (from, tos) ->
                        appendLine("  - Status `${e.statusField}`: $from -> ${tos.joinToString(", ")}")
                    }
                }
                if (e.statusField != null && e.transitions.isEmpty()) {
                    appendLine("  - Status `${e.statusField}`: bebas (tanpa aturan transisi)")
                }
            }
            appendLine()
        }
        appendLine("## Perubahan dari klien")
        if (brief.changes.isEmpty()) appendLine("_Tidak ada perubahan._")
        brief.changes.forEach { c ->
            val status = if (c.ok) "diterapkan" else "ditolak (${c.message.orEmpty()})"
            appendLine("- ${c.at} — ${describe(c.op)}: $status")
        }
        appendLine()
        appendLine("## Cakupan katalog")
        if (brief.coverage.isEmpty()) appendLine("_Belum dihitung._")
        brief.coverage.forEach { c ->
            val price = when {
                c.covered && c.monthlyIdr != null -> " — ${idr(c.monthlyIdr)}/bulan"
                c.covered -> ""
                c.gapLowIdr != null && c.gapHighIdr != null -> " — estimasi ${idr(c.gapLowIdr)}–${idr(c.gapHighIdr)}/bulan"
                else -> " — harga belum dihitung"
            }
            appendLine("- ${c.displayName}: ${if (c.covered) "sudah ada" else "perlu dibangun"}$price")
        }
        appendLine()
        appendLine("## Kebutuhan kustom")
        if (brief.customNeeds.isEmpty()) appendLine("_Tidak ada._")
        brief.customNeeds.forEach { appendLine("- $it") }
    }

    private const val REVISION_HEADING = "## Revisi brief"
    private const val MAX_DIFF_LINES = 40

    /** Bagian "Revisi brief": versi, permintaan yang digantikan, dan selisih isi (dipotong bila panjang). */
    private fun StringBuilder.renderRevision(r: BriefRevision) {
        appendLine(REVISION_HEADING)
        appendLine("Versi ${r.version} — menggantikan `${r.supersedes}` (status sebelumnya: ${r.previousStatus}).")
        fun section(title: String, lines: List<String>, sign: String) {
            if (lines.isEmpty()) return
            appendLine("**$title**")
            lines.take(MAX_DIFF_LINES).forEach { appendLine("- $sign $it") }
            if (lines.size > MAX_DIFF_LINES) appendLine("- ... dan ${lines.size - MAX_DIFF_LINES} baris lain")
        }
        if (r.added.isEmpty() && r.removed.isEmpty()) appendLine("_Isi tidak berubah dibanding versi sebelumnya._")
        section("Ditambahkan", r.added, "+")
        section("Dihapus", r.removed, "-")
        appendLine()
    }

    /** Markdown tanpa bagian revisi — dasar pembanding selisih antar versi (agar revisi lama tidak mencemari selisih). */
    fun withoutRevision(markdown: String): String {
        val out = mutableListOf<String>()
        var skipping = false
        markdown.lines().forEach { line ->
            when {
                line == REVISION_HEADING -> skipping = true
                skipping && line.startsWith("## ") -> { skipping = false; out += line }
                !skipping -> out += line
            }
        }
        return out.joinToString("\n")
    }

    /** Selisih isi dua Markdown brief: baris yang bertambah/hilang (tanpa judul dan baris kosong), urutan terjaga, tanpa duplikat. */
    fun diff(previous: String, current: String): Pair<List<String>, List<String>> {
        fun content(md: String) = md.lines().map { it.trimEnd() }.filter { it.isNotBlank() && !it.startsWith("#") }
        val old = content(withoutRevision(previous)); val now = content(withoutRevision(current))
        return now.filter { it !in old.toSet() }.distinct() to old.filter { it !in now.toSet() }.distinct()
    }

    /**
     * Konteks dari chat Builder: cerita asli, tanya-jawab, keputusan terapan, lalu **Belum jelas** (yang masih menunggu
     * jawaban). Hanya ditulis bila brief membawa konteks, sehingga brief tanpa chat tetap identik byte-per-byte.
     */
    private fun StringBuilder.renderContext(c: BriefContext, brief: RequirementsBrief) {
        fun label(moduleId: String?) = moduleId?.let { id -> brief.modules.firstOrNull { it.moduleId == id }?.displayName ?: id } ?: "Seluruh alur"
        appendLine("## Konteks & keputusan")
        c.narrative?.takeIf { it.isNotBlank() }?.let {
            appendLine("### Cerita pemilik usaha")
            it.lines().forEach { line -> appendLine("> $line") }
            appendLine()
        }
        if (c.answered.isNotEmpty()) {
            appendLine("### Tanya-jawab")
            c.answered.forEach { appendLine("- [${label(it.moduleId)}] ${it.question} — ${it.answer}") }
            appendLine()
        }
        if (c.decisions.isNotEmpty()) {
            appendLine("### Keputusan yang diterapkan")
            c.decisions.forEach { d ->
                val at = d.at?.let { "$it — " }.orEmpty()
                appendLine("- $at[${label(d.moduleId)}] " + d.summary.joinToString("; ").ifEmpty { "(tanpa rincian)" })
            }
            appendLine()
        }
        appendLine("## Belum jelas")
        if (c.open.isEmpty()) appendLine("_Tidak ada pertanyaan yang tertunda._")
        c.open.forEach { appendLine("- [${label(it.moduleId)}] ${it.question}") }
        appendLine()
    }

    /** Deskripsi operasi berbahasa pengguna — bukan nama kelas, supaya terbaca developer non-sesi. */
    private fun describe(op: SpecOp): String = when (op) {
        is SpecOp.AddEnumOption -> "tambah status '${op.option}' pada '${op.field}'" + (op.after?.let { " setelah '$it'" } ?: "")
        is SpecOp.RenameEnumOption -> "ganti nama status '${op.from}' menjadi '${op.to}' pada '${op.field}'"
        is SpecOp.AddTransition -> "izinkan '${op.from}' ke '${op.to}' pada '${op.field}'"
        is SpecOp.AddField -> "tambah field '${op.field.key}' (${op.field.type.name}) ke '${op.entityId}'"
        is SpecOp.RenameFieldLabel -> "ganti label '${op.key}' menjadi '${op.label}'"
        is SpecOp.ShowFieldOnCard -> "tampilkan '${op.field}' di kartu"
        is SpecOp.ChangeWidget -> "ubah tampilan layar '${op.screenId}' menjadi ${if (op.widget == com.eventverse.app.domain.discovery.WidgetKind.KANBAN) "papan (kanban)" else op.widget.code.lowercase()}"
        is SpecOp.SetFieldRequired -> "jadikan '${op.field}' ${if (op.required) "wajib diisi" else "boleh dikosongkan"}"
    }

    /** Format rupiah deterministik tanpa bergantung locale: pemisah ribuan titik. */
    private fun idr(value: Long): String =
        "Rp " + value.toString().reversed().chunked(3).joinToString(".").reversed()
}
