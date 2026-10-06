package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.discovery.WidgetKind

/**
 * Pengusul operasi berbasis **kata kunci Indonesia** — tanpa LLM, tanpa jaringan, deterministik
 * (butir B5). Kalimat yang dikenal (huruf besar/kecil bebas, nama baru dipakai apa adanya):
 *  - "tambah(kan) status X" / "… setelah Y" → [SpecOp.AddEnumOption]
 *  - "tambah(kan) kolom|field X"           → [SpecOp.AddField] (teks, tidak wajib)
 *  - "(ganti|ubah) nama X (jadi|menjadi|→) Z" → [SpecOp.RenameEnumOption] bila X opsi status,
 *    selain itu [SpecOp.RenameFieldLabel] bila X field — dicocokkan case-insensitive ke layar.
 *  - "(izinkan|bolehkan) X ke Y"           → [SpecOp.AddTransition] (keduanya opsi yang ada)
 *
 * Pemisah beberapa permintaan: baris baru, `;`, atau kata "lalu". **Tidak menebak**: satu segmen
 * tak dikenal menggagalkan seluruh pesan dengan pesan yang memuat contoh kalimat yang didukung —
 * bukan fallback ke operasi apa pun. Maksimal [SpecOpApplier.MAX_OPS_PER_TURN] permintaan sekali kirim.
 * Keluaran tetap hanya **usulan**: validasi akhir di [SpecOpApplier].
 */
class DeterministicSpecOpProposer : SpecOpProposer {

    override suspend fun propose(message: String, screen: InteractiveScreen): Result<List<SpecOp>> = runCatching {
        val segments = message.split("\n", ";", " lalu ").map { it.trim() }.filter { it.isNotEmpty() }
        require(segments.isNotEmpty()) { EXAMPLES }
        require(segments.size <= SpecOpApplier.MAX_OPS_PER_TURN) {
            "Maksimal ${SpecOpApplier.MAX_OPS_PER_TURN} permintaan sekali kirim; sisanya kirim di giliran berikutnya."
        }
        val scope = Scope(screen)
        segments.map { CHANGE.find(it)?.let { m -> scope.parseChange(m) } ?: scope.parse(it) }
    }

    private companion object {
        val ADD = Regex("""^tambah(?:kan)?\s+(status|kolom|field)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val AFTER = Regex("""^(.*?)\s+setelah\s+(.+)$""", RegexOption.IGNORE_CASE)
        val RENAME = Regex("""^(?:ganti|ubah)\s+nama\s+(?:(?:status|kolom|field)\s+)?(.+?)\s+(?:jadi|menjadi|->|→)\s+(.+)$""", RegexOption.IGNORE_CASE)
        val ALLOW = Regex("""^(?:izinkan|bolehkan|boleh)\s+(.+?)\s+ke\s+(.+)$""", RegexOption.IGNORE_CASE)
        /** SP-B5: "ubah jadi tabel", "ubah tampilan jadi papan", "jadikan kanban". */
        val CHANGE = Regex("""^(?:ubah|ganti|jadikan)(?:\s+tampilan)?(?:\s+(?:jadi|menjadi))?\s+(tabel|papan|kanban)$""", RegexOption.IGNORE_CASE)
        val EXAMPLES = "Belum bisa memahami permintaan itu. Contoh: " +
            "\"tambah status Revisi setelah Dikerjakan\", \"ganti nama Selesai jadi Ditutup\", " +
            "\"tambah kolom Prioritas\", \"izinkan Baru ke Selesai\", \"ubah jadi tabel\"."
    }
    /** Resolusi deterministik entitas & field status dari layar; gagal jelas bila ambigu. */
    private class Scope(private val screen: InteractiveScreen) {
        /** Layar sasaran "ubah jadi …": satu-satunya layar data (tabel/papan/daftar periksa); ambigu = ditolak. */
        fun parseChange(m: MatchResult): SpecOp {
            val dataScreens = screen.spec.screens.filter { it.widget == WidgetKind.TABLE || it.widget == WidgetKind.KANBAN || it.widget == WidgetKind.CHECKLIST }
            require(dataScreens.size == 1) {
                if (dataScreens.isEmpty()) "Layar ini tidak punya tampilan data yang bisa diubah." else "Ada ${dataScreens.size} tampilan data di layar ini; sebutkan yang mana."
            }
            val widget = if (m.groupValues[1].equals("tabel", ignoreCase = true)) WidgetKind.TABLE else WidgetKind.KANBAN
            return SpecOp.ChangeWidget(dataScreens.single().screenId, widget)
        }

        val entity: EntitySpec = if (screen.spec.entities.size == 1) {
            screen.spec.entities.single()
        } else {
            throw IllegalArgumentException(
                "Layar ini punya ${screen.spec.entities.size} entitas; pengusul ini butuh layar satu entitas. " + EXAMPLES
            )
        }

        private val enumFields = entity.fields.filter { it.type == FieldType.ENUM }

        /** Satu-satunya field ENUM, atau yang ditunjuk struktur layar (kanban/tabel/mesin status). */
        val statusField: FieldSpec by lazy { resolveStatusField() }

        private fun resolveStatusField(): FieldSpec = when {
            enumFields.size == 1 -> enumFields.single()
            else -> {
                val hinted = buildSet {
                    screen.spec.screens.forEach { s ->
                        s.kanban?.let { add(it.groupField) }
                        s.table?.statusField?.let { add(it) }
                    }
                    entity.stateMachine?.let { add(it.field) }
                }
                val matches = enumFields.filter { it.key in hinted }
                if (matches.size == 1) matches.single()
                else throw IllegalArgumentException(
                    "Layar punya ${enumFields.size} pilihan status — sebutkan field statusnya secara eksplisit. " + EXAMPLES
                )
            }
        }

        fun parse(raw: String): SpecOp {
            ADD.find(raw)?.let { return parseAdd(it) }
            RENAME.find(raw)?.let { return parseRename(it) }
            ALLOW.find(raw)?.let { return parseAllow(it) }
            throw IllegalArgumentException(EXAMPLES)
        }

        private fun parseAdd(m: MatchResult): SpecOp {
            val rest = m.groupValues[2].trim()
            if (!m.groupValues[1].equals("status", ignoreCase = true)) {
                require(rest.isNotBlank()) { EXAMPLES }
                return SpecOp.AddField(entity.id, FieldSpec(rest, rest, FieldType.TEXT))
            }
            val afterMatch = AFTER.find(rest)
            val name = (afterMatch?.groupValues?.get(1) ?: rest).trim()
            require(name.isNotBlank()) { EXAMPLES }
            val after = afterMatch?.let { canonicalOption(it.groupValues[2].trim()) }
            if (afterMatch != null) {
                requireNotNull(after) { "Status '${afterMatch.groupValues[2].trim()}' tidak ada di '${statusField.label}'." }
            }
            return SpecOp.AddEnumOption(entity.id, statusField.key, name, after)
        }

        private fun parseRename(m: MatchResult): SpecOp {
            val from = m.groupValues[1].trim()
            val to = m.groupValues[2].trim()
            require(to.isNotBlank()) { EXAMPLES }
            canonicalOption(from)?.let { return SpecOp.RenameEnumOption(entity.id, statusField.key, it, to) }
            entity.fields.firstOrNull { it.key.equals(from, ignoreCase = true) || it.label.equals(from, ignoreCase = true) }
                ?.let { return SpecOp.RenameFieldLabel(entity.id, it.key, to) }
            throw IllegalArgumentException("'$from' bukan status maupun field yang dikenal di layar ini. " + EXAMPLES)
        }

        private fun parseAllow(m: MatchResult): SpecOp {
            val fromName = m.groupValues[1].trim()
            val toName = m.groupValues[2].trim()
            val from = canonicalOption(fromName)
            val to = canonicalOption(toName)
            if (from == null || to == null) {
                val unknown = listOf(fromName, toName).first { canonicalOption(it) == null }
                throw IllegalArgumentException(
                    "Status '$unknown' tidak ada di '${statusField.label}' (opsi: ${statusField.options.joinToString(", ")})."
                )
            }
            return SpecOp.AddTransition(entity.id, statusField.key, from, to)
        }

        private fun canonicalOption(name: String): String? =
            statusField.options.firstOrNull { it.equals(name, ignoreCase = true) }
    }
}