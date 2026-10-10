package com.eventverse.app.domain.discovery.proposal

/**
 * Sunting isian sebuah layar (PLAN-builder-interview-chat Fase C): tambah, kurangi, atau ganti **field** entitasnya —
 * "input apa saja" yang dicatat sebuah modul. Hanya *usulan*: hasilnya wajib lolos [ScreenProposalValidator] sebelum
 * dipakai, jadi keluaran model yang salah ditolak berpath, bukan disaring diam-diam.
 */
sealed interface ProposalEdit {
    data class AddField(val field: FieldProposal) : ProposalEdit
    data class RemoveField(val key: String) : ProposalEdit

    /** Mengganti definisi field bernama [key] (label, tipe, wajib, opsi); kuncinya sendiri tidak berubah. */
    data class ReplaceField(val key: String, val field: FieldProposal) : ProposalEdit
}

class ProposalEditException(message: String) : IllegalArgumentException(message)

/**
 * Menerapkan [edits] berurutan ke usulan. Murni dan **tidak mengubah** objek asal. Tampilan ikut disinkronkan supaya
 * field baru terlihat dan field yang dibuang tidak menggantung: Tabel (kolom + kolom yang boleh disunting), Formulir,
 * Cetak, dan formulir detail Kanban. Hasil akhir divalidasi penuh; usulan tak sah → [Result.failure] dengan galat
 * berpath yang bisa dikembalikan ke model.
 */
fun ScreenProposal.applyEdits(edits: List<ProposalEdit>, packModuleIds: Set<String>? = null): Result<ScreenProposal> = runCatching {
    var current = this
    edits.forEach { current = current.applyOne(it) }
    val issues = ScreenProposalValidator.validate(current, "$.proposal", null, packModuleIds)
    if (issues.isNotEmpty()) throw ProposalEditException(issues.joinToString("; ") { "${it.path}: ${it.message}" })
    current
}

private fun ScreenProposal.applyOne(edit: ProposalEdit): ScreenProposal {
    val e = entity ?: throw ProposalEditException("Layar '$title' tidak punya isian yang bisa disunting")
    return when (edit) {
        is ProposalEdit.AddField -> {
            if (e.fields.any { it.key == edit.field.key }) throw ProposalEditException("Field '${edit.field.key}' sudah ada")
            if (e.fields.size >= ProposalLimits.FIELDS) throw ProposalEditException("Terlalu banyak field (maksimum ${ProposalLimits.FIELDS})")
            copy(entity = e.copy(fields = e.fields + edit.field), view = view.withField(edit.field.key), seed = seed.reconcileFor(edit.field))
        }
        is ProposalEdit.RemoveField -> {
            if (e.fields.none { it.key == edit.key }) throw ProposalEditException("Field '${edit.key}' tidak ada")
            if (e.statusField == edit.key) throw ProposalEditException("Field status '${edit.key}' tidak boleh dibuang")
            copy(
                entity = e.copy(fields = e.fields.filterNot { it.key == edit.key }),
                view = view.withoutField(edit.key),
                seed = seed.map { it - edit.key }
            )
        }
        is ProposalEdit.ReplaceField -> {
            if (e.fields.none { it.key == edit.key }) throw ProposalEditException("Field '${edit.key}' tidak ada")
            if (edit.field.key != edit.key) throw ProposalEditException("Mengganti field tidak boleh mengubah kuncinya ('${edit.key}' != '${edit.field.key}')")
            if (e.statusField == edit.key && edit.field.type != e.fields.first { it.key == edit.key }.type) {
                throw ProposalEditException("Tipe field status '${edit.key}' tidak boleh diganti")
            }
            copy(entity = e.copy(fields = e.fields.map { if (it.key == edit.key) edit.field else it }), seed = seed.reconcileFor(edit.field))
        }
    }
}

private fun ViewProposal.withField(key: String): ViewProposal = when (this) {
    is ViewProposal.Table -> copy(columns = columns + key, editableFields = if (editableFields.isEmpty()) editableFields else editableFields + key)
    is ViewProposal.Form -> copy(fields = fields + key)
    is ViewProposal.Print -> copy(fields = fields + key)
    is ViewProposal.Kanban -> if (detailFormFields.isEmpty()) this else copy(detailFormFields = detailFormFields + key)
    else -> this
}

private fun ViewProposal.withoutField(key: String): ViewProposal = when (this) {
    is ViewProposal.Table -> copy(columns = columns - key, editableFields = editableFields - key)
    is ViewProposal.Form -> copy(fields = fields - key)
    is ViewProposal.Print -> copy(fields = fields - key)
    is ViewProposal.Kanban -> copy(detailFormFields = detailFormFields - key)
    else -> this
}

/**
 * Menjaga baris contoh (seed) tetap sah setelah sebuah field ditambah/diganti — validator mewajibkan field wajib terisi di
 * **setiap** baris dan nilai sesuai tipenya. Nilai yang masih sah dibiarkan; yang tak sesuai tipe baru dibuang bila field
 * tidak wajib, atau diganti nilai contoh bertipe sama bila wajib. Hanya baris contoh yang tersentuh, bukan data pengguna.
 */
private fun List<Map<String, String>>.reconcileFor(f: FieldProposal): List<Map<String, String>> = map { row ->
    val v = row[f.key]
    when {
        v != null && v.isNotEmpty() && f.accepts(v) -> row
        f.required -> row + (f.key to f.sampleValue())
        v != null -> row - f.key
        else -> row
    }
}

private fun FieldProposal.accepts(v: String): Boolean = when (type) {
    com.eventverse.app.domain.prototype.FieldType.ENUM -> v in options
    // A0 (TRD-FIELD-003): bentuk nilai MULTI_SELECT = aturan tunggal MultiSelectValues.
    com.eventverse.app.domain.prototype.FieldType.MULTI_SELECT ->
        com.eventverse.app.domain.prototype.MultiSelectValues.isValid(v, options, maxSelections)
    com.eventverse.app.domain.prototype.FieldType.NUMBER -> v.toDoubleOrNull() != null
    com.eventverse.app.domain.prototype.FieldType.BOOL -> v == "ya" || v == "tidak"
    com.eventverse.app.domain.prototype.FieldType.DATE -> com.eventverse.app.domain.prototype.DateFieldValues.isValid(v, withTime)
    // C6: jam dinding JJ:MM — aturan tunggal TimeFieldValues, sama dengan FieldSpec.accepts.
    com.eventverse.app.domain.prototype.FieldType.TIME -> com.eventverse.app.domain.prototype.TimeFieldValues.isValid(v)
    com.eventverse.app.domain.prototype.FieldType.TEXT -> com.eventverse.app.domain.prototype.TextValidations.isValid(validation, v)
    com.eventverse.app.domain.prototype.FieldType.LONG_TEXT -> true
    // C7: bentuk id rujukan sama dengan FieldSpec.accepts (keberadaan target diverifikasi server).
    com.eventverse.app.domain.prototype.FieldType.RELATION -> v.isNotBlank() && !v.contains("..")
    // A0 (penyatuan kosakata): bentuk id pengguna sama dengan RELATION; keberadaan `users.id` diverifikasi server.
    com.eventverse.app.domain.prototype.FieldType.USER_REF -> v.isNotBlank() && !v.contains("..")
    // C8: bentuk FileRef (key `fields/...`).
    com.eventverse.app.domain.prototype.FieldType.FILE -> com.eventverse.app.domain.storage.FileRef.isValid(v)
}

private fun FieldProposal.sampleValue(): String = when (type) {
    com.eventverse.app.domain.prototype.FieldType.ENUM -> options.firstOrNull() ?: "contoh"
    // A0 (TRD-FIELD-003): contoh MULTI_SELECT = satu pilihan pertama, sebagai string JSON kanonik.
    com.eventverse.app.domain.prototype.FieldType.MULTI_SELECT ->
        com.eventverse.app.domain.prototype.MultiSelectValues.encode(options.take(1), options)
    com.eventverse.app.domain.prototype.FieldType.NUMBER -> "0"
    com.eventverse.app.domain.prototype.FieldType.BOOL -> "tidak"
    com.eventverse.app.domain.prototype.FieldType.DATE -> com.eventverse.app.domain.prototype.DateFieldValues.sample(withTime)
    // C6: contoh jam dari satu sumber aturan nilai TIME (bukan angka karangan).
    com.eventverse.app.domain.prototype.FieldType.TIME -> com.eventverse.app.domain.prototype.TimeFieldValues.sample()
    com.eventverse.app.domain.prototype.FieldType.TEXT -> com.eventverse.app.domain.prototype.TextValidations.sample(validation)
    com.eventverse.app.domain.prototype.FieldType.LONG_TEXT -> "contoh"
    // Keputusan R2 TRD-FIELD-001: seed RELATION = kosong di v1 (tidak mengarang id target).
    com.eventverse.app.domain.prototype.FieldType.RELATION -> ""
    // A0 (penyatuan kosakata): seed USER_REF = kosong — id pengguna platform tidak dikarang di contoh.
    com.eventverse.app.domain.prototype.FieldType.USER_REF -> ""
    // C8 (TRD-FIELD-002): seed FILE = kosong di v1 (tidak mengarang referensi objek yang tak ada).
    com.eventverse.app.domain.prototype.FieldType.FILE -> ""
}
