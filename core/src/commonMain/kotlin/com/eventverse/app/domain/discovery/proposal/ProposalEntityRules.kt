package com.eventverse.app.domain.discovery.proposal

import com.eventverse.app.domain.prototype.CurrencyCode
import com.eventverse.app.domain.prototype.DateFieldValues
import com.eventverse.app.domain.prototype.FieldType
import com.eventverse.app.domain.prototype.MultiSelectValues
import com.eventverse.app.domain.prototype.NumberFormat
import com.eventverse.app.domain.prototype.TextValidation
import com.eventverse.app.domain.prototype.TextValidations
import com.eventverse.app.domain.prototype.TimeFieldValues
import com.eventverse.app.domain.prototype.relationTargetFormatError

/** Aturan entitas dan seed (plan §2.2: koherensi status, batas ukuran, seed cocok skema). */
internal object ProposalEntityRules {

    fun check(entity: EntityProposal, sink: IssueSink, packModuleIds: Set<String>?) {
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
            // C4 Irisan 2 (keputusan D3): format adalah varian NUMBER, bukan tipe lain.
            if (f.type != FieldType.NUMBER && f.format != NumberFormat.PLAIN) {
                sink.add("$at.format", "Field '${f.key}' bertipe ${f.type.name}, bukan NUMBER, jadi tidak boleh punya format ${f.format.name}")
            }
            val code = f.currencyCode
            if (f.type == FieldType.NUMBER && f.format == NumberFormat.CURRENCY) {
                if (code == null || !CurrencyCode.isValid(code)) {
                    sink.add("$at.currencyCode", "Field '${f.key}' berformat CURRENCY wajib punya kode mata uang tiga huruf besar (mis. IDR)")
                }
            } else if (code != null) {
                sink.add("$at.currencyCode", "Field '${f.key}' tidak berformat CURRENCY, jadi tidak boleh punya kode mata uang")
            }
            if (f.type != FieldType.DATE && f.withTime) {
                sink.add("$at.withTime", "Field '${f.key}' bertipe ${f.type.name}, bukan DATE, jadi tidak boleh punya withTime")
            }
            if (f.type != FieldType.TEXT && f.validation != TextValidation.NONE) {
                sink.add("$at.validation", "Field '${f.key}' bertipe ${f.type.name}, bukan TEXT, jadi tidak boleh punya validation ${f.validation.name}")
            }
            // A0 (TRD-FIELD-003): maxSelections hanya sah untuk MULTI_SELECT, dan (bila diisi) 1..options.size.
            if (f.type != FieldType.MULTI_SELECT) {
                if (f.maxSelections != null) {
                    sink.add("$at.maxSelections", "Field '${f.key}' bertipe ${f.type.name}, bukan MULTI_SELECT, jadi tidak boleh punya maxSelections")
                }
            } else {
                val max = f.maxSelections
                if (max != null && (max < 1 || max > f.options.size)) {
                    sink.add("$at.maxSelections", "Field MULTI_SELECT '${f.key}' maxSelections harus 1..${f.options.size}, dapat $max")
                }
            }
            checkTarget(f, at, sink, packModuleIds)
        }
        checkStatus(entity, sink)
    }

    /**
     * C7 (TRD-FIELD-001 FR-2/FR-6): `target` rujukan wajib tepat untuk RELATION, tidak boleh ada pada tipe
     * lain. Bentuknya `"entityId"` (satu modul) atau `"moduleId:entityId"` (lintas modul). Target lintas modul
     * yang modulnya tidak dapat diresolusi pack **ditolak** (bukan dibiarkan lolos ke server). Modul sendiri
     * pack di [packModuleIds]; `null` = konteks pack tak diketahui, resolusi modul tak dapat diperiksa di sini.
     */
    private fun checkTarget(f: FieldProposal, at: String, sink: IssueSink, packModuleIds: Set<String>?) {
        val target = f.target
        if (f.type != FieldType.RELATION) {
            if (target != null) sink.add("$at.target", "Field '${f.key}' bertipe ${f.type.name}, bukan RELATION, jadi tidak boleh punya target")
            return
        }
        if (target.isNullOrBlank()) {
            sink.add("$at.target", "Field RELATION '${f.key}' wajib punya target 'entityId' atau 'moduleId:entityId'")
            return
        }
        // Bentuk = satu sumber aturan bersama invariant FieldSpec (`relationTargetFormatError`), tak boleh berbeda.
        val shapeError = relationTargetFormatError(target)
        if (shapeError != null) {
            sink.add("$at.target", "Target field RELATION '${f.key}' $shapeError, dapat '$target'")
            return
        }
        val parts = target.split(':')
        if (parts.size == 2 && packModuleIds != null && parts[0] !in packModuleIds) {
            sink.add("$at.target", "Target lintas modul '${parts[0]}' tidak dapat diresolusi pack ini (modul: ${packModuleIds.sorted().joinToString()})")
        }
    }

    private fun checkOptions(f: FieldProposal, at: String, sink: IssueSink) {
        // A0 (TRD-FIELD-003): ENUM dan MULTI_SELECT sama-sama wajib opsi (unik, ≤ batas); tipe lain tanpa opsi.
        if (f.type != FieldType.ENUM && f.type != FieldType.MULTI_SELECT) {
            if (f.options.isNotEmpty()) sink.add("$at.options", "Field '${f.key}' bertipe ${f.type.name}, bukan ENUM atau MULTI_SELECT, jadi tidak boleh punya options")
            return
        }
        if (f.options.isEmpty()) sink.add("$at.options", "Field ${f.type.name} '${f.key}' wajib punya options")
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
            val statusKey = entity.statusField?.takeIf { k -> entity.fields.any { it.key == k } }   // statusField rusak sudah dilaporkan checkStatus
            if (p.view is ViewProposal.Kanban && statusKey != null && row[statusKey].isNullOrEmpty()) {
                sink.add(".seed[$i].$statusKey", "Kartu papan wajib punya '$statusKey' supaya tampil di salah satu kolom (${entity.fields.first { it.key == statusKey }.options.joinToString()})")
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
            // A0 (TRD-FIELD-003): seed MULTI_SELECT wajib array JSON kanonik dari opsi (aturan tunggal MultiSelectValues).
            FieldType.MULTI_SELECT -> if (!MultiSelectValues.isValid(v, f.options, f.maxSelections)) {
                sink.add(at, "'$v' bukan pilihan ganda yang sah untuk field '${f.key}' (${f.options.joinToString()})")
            }
            FieldType.NUMBER -> if (v.toDoubleOrNull() == null) sink.add(at, "'$v' bukan angka untuk field '${f.key}'")
            FieldType.BOOL -> if (v != "ya" && v != "tidak") sink.add(at, "Field BOOL '${f.key}' hanya menerima 'ya' atau 'tidak', dapat '$v'")
            FieldType.DATE -> if (!DateFieldValues.isValid(v, f.withTime)) sink.add(
                at,
                if (f.withTime) "'$v' bukan tanggal-jam ISO (YYYY-MM-DDTHH:MM, tanpa detik/zona) untuk field '${f.key}'"
                else "'$v' bukan tanggal ISO (YYYY-MM-DD) untuk field '${f.key}'"
            )
            // C6: seed TIME wajib jam dinding JJ:MM 24-jam tepat menit — tanpa koersi ke bentuk lain.
            FieldType.TIME -> if (!TimeFieldValues.isValid(v)) {
                sink.add(at, "'$v' bukan jam JJ:MM (24 jam, tepat menit, tanpa detik/zona) untuk field '${f.key}'")
            }
            FieldType.TEXT -> if (!TextValidations.isValid(f.validation, v)) {
                sink.add(at, "'$v' bukan ${f.validation.name.lowercase()} yang sah untuk field '${f.key}'")
            }
            FieldType.LONG_TEXT -> Unit
            // Keputusan R2 TRD-FIELD-001: seed RELATION wajib kosong di v1 — tidak mengarang id target.
            FieldType.RELATION -> sink.add(at, "Field RELATION '${f.key}' wajib kosong di baris contoh (v1); isi nilai rujukan lewat data nyata")
            // C8 (TRD-FIELD-002): seed FILE wajib kosong — referensi ke objek yang tidak ada ditolak.
            FieldType.FILE -> sink.add(at, "Field FILE '${f.key}' wajib kosong di baris contoh (v1); berkas diunggah lewat data nyata")
        }
    }
}
