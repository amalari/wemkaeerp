package com.eventverse.app.domain.discovery.brief

import com.eventverse.app.domain.prototype.SpecOp

/**
 * Menyusun [RequirementsBrief] menjadi Markdown **deterministik** (masukan sama → keluaran sama,
 * byte per byte) untuk developer yang tidak ikut sesi. Susunan bagian: ringkasan, modul & layar,
 * perubahan klien, cakupan katalog (sudah ada vs perlu dibangun + harga), kebutuhan kustom.
 * Renderer tidak mengenal istilah satu industri — seluruh isi datang dari data brief.
 */
object BriefRenderer {

    fun markdown(brief: RequirementsBrief): String = buildString {
        appendLine("# Brief Kebutuhan — ${brief.packCode}")
        appendLine()
        appendLine("## Ringkasan")
        val covered = brief.coverage.count { it.covered }
        val ok = brief.changes.count { it.ok }
        appendLine("- Modul: ${brief.modules.size} ($covered sudah ada, ${brief.coverage.size - covered} perlu dibangun)")
        appendLine("- Layar: ${brief.modules.sumOf { it.screens.size }}")
        appendLine("- Perubahan klien: ${brief.changes.size} ($ok diterapkan, ${brief.changes.size - ok} ditolak)")
        appendLine("- Kebutuhan kustom: ${brief.customNeeds.size}")
        appendLine()
        appendLine("## Modul & layar")
        if (brief.modules.isEmpty()) appendLine("_Belum ada modul dipilih._")
        brief.modules.forEach { m ->
            appendLine("### ${m.displayName} (`${m.moduleId}`)")
            m.screens.forEach { s ->
                val target = s.entityId?.let { "entitas `$it`" } ?: "tanpa entitas"
                appendLine("- Layar: ${s.title} (${s.widget}) → $target")
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
                        appendLine("  - Status `${e.statusField}`: $from → ${tos.joinToString(", ")}")
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

    /** Deskripsi operasi berbahasa pengguna — bukan nama kelas, supaya terbaca developer non-sesi. */
    private fun describe(op: SpecOp): String = when (op) {
        is SpecOp.AddEnumOption -> "tambah status '${op.option}' pada '${op.field}'" + (op.after?.let { " setelah '$it'" } ?: "")
        is SpecOp.RenameEnumOption -> "ganti nama status '${op.from}' menjadi '${op.to}' pada '${op.field}'"
        is SpecOp.AddTransition -> "izinkan '${op.from}' ke '${op.to}' pada '${op.field}'"
        is SpecOp.AddField -> "tambah field '${op.field.key}' (${op.field.type.name}) ke '${op.entityId}'"
        is SpecOp.RenameFieldLabel -> "ganti label '${op.key}' menjadi '${op.label}'"
    }

    /** Format rupiah deterministik tanpa bergantung locale: pemisah ribuan titik. */
    private fun idr(value: Long): String =
        "Rp " + value.toString().reversed().chunked(3).joinToString(".").reversed()
}
