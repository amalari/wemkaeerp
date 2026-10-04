package com.eventverse.app.domain.discovery.brief

/**
 * Menyusun [RequirementsBrief] menjadi Markdown **deterministik** (masukan sama → keluaran sama,
 * byte per byte) untuk developer yang tidak ikut sesi.
 *
 * **KERANGKA (B0):** susunan bagian sudah tetap dan dipakai endpoint; butir B4 memperkaya isinya dan
 * mengunci dengan golden test. Renderer tidak mengenal istilah satu industri — isi datang dari data.
 */
object BriefRenderer {

    fun markdown(brief: RequirementsBrief): String = buildString {
        appendLine("# Brief Kebutuhan — ${brief.packCode}")
        appendLine()
        appendLine("## Modul & layar")
        if (brief.modules.isEmpty()) appendLine("_Belum ada modul dipilih._")
        brief.modules.forEach { m ->
            appendLine("- **${m.displayName}** (`${m.moduleId}`)")
            m.screens.forEach { appendLine("  - Layar: ${it.title} (${it.widget})") }
        }
        appendLine()
        appendLine("## Perubahan dari klien")
        if (brief.changes.isEmpty()) appendLine("_Tidak ada perubahan._")
        brief.changes.forEach { appendLine("- ${it.at} — ${it.op::class.simpleName}: ${if (it.ok) "diterapkan" else "ditolak (${it.message.orEmpty()})"}") }
        appendLine()
        appendLine("## Cakupan katalog")
        if (brief.coverage.isEmpty()) appendLine("_Belum dihitung._")
        brief.coverage.forEach { c ->
            appendLine("- ${c.displayName}: " + if (c.covered) "sudah ada" else "perlu dibangun")
        }
        appendLine()
        appendLine("## Kebutuhan kustom")
        if (brief.customNeeds.isEmpty()) appendLine("_Tidak ada._")
        brief.customNeeds.forEach { appendLine("- $it") }
    }
}
