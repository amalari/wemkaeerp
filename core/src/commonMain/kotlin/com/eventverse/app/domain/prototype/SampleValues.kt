package com.eventverse.app.domain.prototype

/**
 * Nilai contoh untuk **simulasi** (TRD-BLDR-002): ketika sebuah skenario berpindah ke layar yang menuntut field wajib yang
 * tidak tercakup pembawa data, field itu lahir di langkah tersebut (nomor antrean, status awal) dan tak mungkin berasal dari
 * langkah sebelumnya. Mengisinya dengan nilai contoh membuat alur tetap bisa dilihat; pemanggil wajib **menandai** bahwa nilainya
 * contoh, bukan data.
 *
 * Aturan: nilai contoh selalu lolos [FieldSpec.accepts] (bukan tebakan bentuk), dan tipe yang nilainya hanya bermakna bila **nyata**
 * - [FieldType.RELATION], [FieldType.USER_REF], [FieldType.FILE] - tidak punya nilai contoh (`null`): id yang dikarang akan
 * menunjuk sesuatu yang tidak ada. `when` sengaja tanpa `else`: tipe baru memaksa keputusan di sini (field-component-rules Kontrak 6).
 */
object SampleValues {

    /** Nilai contoh sah untuk [field], atau `null` bila tipenya tak punya nilai contoh. */
    fun forField(field: FieldSpec): String? = candidates(field).firstOrNull { it.isNotEmpty() && field.accepts(it) }

    /** Field wajib di [entity] yang belum berisi pada [values] (kosong atau tidak ada), menurut urutan entitas. */
    fun missingRequired(entity: EntitySpec, values: Map<String, String>): List<FieldSpec> =
        entity.fields.filter { it.required && values[it.key].isNullOrBlank() }

    /**
     * Status awal untuk entitas bermesin-status: keadaan yang hanya menjadi **sumber** transisi (tidak pernah tujuan), dengan cadangan
     * opsi pertama field status. `null` bila entitas tak punya mesin status atau field-nya tak ada.
     *
     * Kenapa ada: papan (kanban) mengelompokkan baris menurut field status; baris **tanpa status tidak masuk kolom mana pun** dan
     * tak terlihat (temuan cek visual klinik, 2026-10-16: baris hasil "Lanjut Alur" ada tetapi tidak tampil di papan Antrean).
     */
    fun initialState(entity: EntitySpec): String? {
        val machine = entity.stateMachine ?: return null
        val field = entity.fields.firstOrNull { it.key == machine.field } ?: return null
        val targets = machine.transitions.values.flatten().toSet()
        return machine.transitions.keys.firstOrNull { it !in targets && it in field.optionLabels } ?: field.optionLabels.firstOrNull()
    }

    /** [values] ditambah status awal bila entitas bermesin-status dan nilai statusnya kosong; selain itu tak berubah. */
    fun withInitialState(entity: EntitySpec, values: Map<String, String>): Map<String, String> {
        val key = entity.stateMachine?.field ?: return values
        if (!values[key].isNullOrBlank()) return values
        return initialState(entity)?.let { values + (key to it) } ?: values
    }

    private fun candidates(field: FieldSpec): List<String> = when (field.type) {
        // Beberapa kandidat: validasi EMAIL/PHONE menolak teks biasa, jadi `accepts` yang memilih.
        FieldType.TEXT -> listOf("(contoh)", "contoh@contoh.id", "081200000000")
        FieldType.LONG_TEXT -> listOf("(contoh)")
        FieldType.NUMBER -> listOf("1")
        FieldType.DATE -> if (field.withTime) listOf("2026-01-01T08:00") else listOf("2026-01-01")
        FieldType.TIME -> listOf("08:00")
        // Nilai sel = label opsi (bukan id) di kosakata prototype.
        FieldType.ENUM -> field.optionLabels.take(1)
        FieldType.MULTI_SELECT -> field.optionLabels.take(1).map { MultiSelectValues.encode(listOf(it), field.optionLabels) }
        FieldType.BOOL -> listOf("tidak")
        FieldType.RELATION, FieldType.USER_REF, FieldType.FILE -> emptyList()
    }
}
