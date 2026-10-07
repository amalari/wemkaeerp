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
            if (edit.field.key != edit.key) throw ProposalEditException("Mengganti field tidak boleh mengubah kuncinya ('${edit.key}' ≠ '${edit.field.key}')")
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
    com.eventverse.app.domain.prototype.FieldType.NUMBER -> v.toDoubleOrNull() != null
    com.eventverse.app.domain.prototype.FieldType.BOOL -> v == "ya" || v == "tidak"
    com.eventverse.app.domain.prototype.FieldType.DATE -> runCatching { kotlinx.datetime.LocalDate.parse(v) }.isSuccess
    com.eventverse.app.domain.prototype.FieldType.TEXT -> true
}

private fun FieldProposal.sampleValue(): String = when (type) {
    com.eventverse.app.domain.prototype.FieldType.ENUM -> options.firstOrNull() ?: "contoh"
    com.eventverse.app.domain.prototype.FieldType.NUMBER -> "0"
    com.eventverse.app.domain.prototype.FieldType.BOOL -> "tidak"
    com.eventverse.app.domain.prototype.FieldType.DATE -> "2026-01-01"
    com.eventverse.app.domain.prototype.FieldType.TEXT -> "contoh"
}
