package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.FieldType
import kotlinx.datetime.LocalDate

/** Aturan entitas dan seed (plan §2.2: koherensi status, batas ukuran, seed cocok skema). */
internal object ProposalEntityRules {

    fun check(entity: EntityProposal, sink: IssueSink) {
        sink.key(".entity.id", entity.id, "entity.id")
        sink.text(".entity.label", entity.label, "entity.label")
        if (entity.fields.isEmpty()) sink.add(".entity.fields", "Entity wajib punya minimal 1 field")
        if (entity.fields.size > ProposalLimits.FIELDS) {
            sink.add(".entity.fields", "Terlalu banyak field (${entity.fields.size}); maksimum ${ProposalLimits.FIELDS}")
        }
        val seen = mutableSetOf<String>()
        entity.fields.forEachIndexed { i, f ->
            val at = ".entity.fields[$i]"
            sink.key("$at.key", f.key, "Kunci field")
            if (!seen.add(f.key)) sink.add("$at.key", "Kunci field '${f.key}' dipakai dua kali")
            sink.text("$at.label", f.label, "Label field '${f.key}'")
            checkOptions(f, at, sink)
        }
        checkStatus(entity, sink)
    }

    private fun checkOptions(f: FieldProposal, at: String, sink: IssueSink) {
        if (f.type != FieldType.ENUM) {
            if (f.options.isNotEmpty()) sink.add("$at.options", "Field '${f.key}' bertipe ${f.type.name}, bukan ENUM, jadi tidak boleh punya options")
            return
        }
        if (f.options.isEmpty()) sink.add("$at.options", "Field ENUM '${f.key}' wajib punya options")
        if (f.options.size > ProposalLimits.OPTIONS) {
            sink.add("$at.options", "Field '${f.key}' punya ${f.options.size} pilihan; maksimum ${ProposalLimits.OPTIONS}")
        }
        if (f.options.distinct().size != f.options.size) sink.add("$at.options", "Pilihan field '${f.key}' ada yang kembar")
        f.options.forEachIndexed { j, o -> sink.text("$at.options[$j]", o, "Pilihan field '${f.key}'") }
    }

    private fun checkStatus(entity: EntityProposal, sink: IssueSink) {
        val statusKey = entity.statusField
        val status = statusKey?.let { k -> entity.fields.firstOrNull { it.key == k } }
        if (statusKey != null) {
            when {
                status == null -> sink.add(".entity.statusField", "statusField '$statusKey' tidak ada di fields")
                status.type != FieldType.ENUM -> sink.add(".entity.statusField", "statusField '$statusKey' wajib field bertipe ENUM, bukan ${status.type.name}")
                status.options.size < 2 -> sink.add(".entity.statusField", "Status '$statusKey' butuh minimal 2 pilihan")
                status.options.size > ProposalLimits.STATUSES -> sink.add(".entity.statusField", "Status '$statusKey' punya ${status.options.size} pilihan; maksimum ${ProposalLimits.STATUSES}")
            }
        }
        if (entity.transitions.isNotEmpty() && statusKey == null) {
            sink.add(".entity.transitions", "transitions hanya bermakna bila statusField diisi")
        }
        val known = status?.options?.toSet() ?: return
        entity.transitions.forEach { (from, tos) ->
            if (from !in known) sink.add(".entity.transitions.$from", "Status asal '$from' bukan pilihan '$statusKey' (${known.joinToString()})")
            tos.forEachIndexed { i, to ->
                if (to !in known) sink.add(".entity.transitions.$from[$i]", "Status tujuan '$to' bukan pilihan '$statusKey' (${known.joinToString()})")
            }
        }
    }

    fun checkSeed(p: ScreenProposal, sink: IssueSink) {
        if (p.seed.isEmpty()) return
        val entity = p.entity
        if (entity == null) {
            sink.add(".seed", "seed hanya untuk layar ber-entity; widget ${p.widget.code} tanpa entity wajib seed kosong")
            return
        }
        if (p.seed.size > ProposalLimits.SEED_ROWS) {
            sink.add(".seed", "Terlalu banyak baris contoh (${p.seed.size}); maksimum ${ProposalLimits.SEED_ROWS}")
        }
        val byKey = entity.fields.associateBy { it.key }
        p.seed.forEachIndexed { i, row ->
            row.forEach { (k, v) ->
                val at = ".seed[$i].$k"
                val f = byKey[k]
                if (f == null) { sink.add(at, "Kunci '$k' tidak ada di field entity '${entity.id}'"); return@forEach }
                sink.text(at, v, "Nilai '$k'", required = false)
                checkValue(f, v, at, sink)
            }
            entity.fields.filter { it.required && row[it.key].isNullOrEmpty() }.forEach { f ->
                sink.add(".seed[$i].${f.key}", "Field wajib '${f.key}' harus terisi di setiap baris contoh")
            }
        }
    }

    private fun checkValue(f: FieldProposal, v: String, at: String, sink: IssueSink) {
        if (v.isEmpty()) return
        when (f.type) {
            FieldType.ENUM -> if (v !in f.options) sink.add(at, "'$v' bukan pilihan '${f.key}' (${f.options.joinToString()})")
            FieldType.NUMBER -> if (v.toDoubleOrNull() == null) sink.add(at, "'$v' bukan angka untuk field '${f.key}'")
            FieldType.BOOL -> if (v != "ya" && v != "tidak") sink.add(at, "Field BOOL '${f.key}' hanya menerima 'ya' atau 'tidak', dapat '$v'")
            FieldType.DATE -> if (runCatching { LocalDate.parse(v) }.isFailure) sink.add(at, "'$v' bukan tanggal ISO (YYYY-MM-DD) untuk field '${f.key}'")
            FieldType.TEXT -> Unit
        }
    }
}
