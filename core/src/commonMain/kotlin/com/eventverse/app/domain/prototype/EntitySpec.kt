package com.eventverse.app.domain.prototype

/**
 * Tipe field prototype: kosakata **tertutup milik sistem** (lolos Uji Variabilitas — renderer harus
 * bisa menggambar tiap tipe di semua vertikal). Nama field, opsi enum, dan transisi tetap data.
 */
enum class FieldType { TEXT, NUMBER, DATE, ENUM, BOOL }

data class FieldSpec(
    val key: String,
    val label: String,
    val type: FieldType,
    /** Wajib terisi untuk [FieldType.ENUM]; kosong untuk tipe lain. */
    val options: List<String> = emptyList(),
    /** Kontrak v1: field wajib. Ditegakkan reducer pada `Create`; `SetField` boleh mengosongkan hanya bila tidak wajib. */
    val required: Boolean = false
) {
    init {
        require(key.isNotBlank()) { "FieldSpec.key kosong" }
        require(label.isNotBlank()) { "Field '$key' tanpa label" }
        if (type == FieldType.ENUM) {
            require(options.isNotEmpty() && options.distinct().size == options.size) {
                "Field ENUM '$key' wajib punya opsi unik"
            }
        } else {
            require(options.isEmpty()) { "Field '$key' bukan ENUM tapi punya opsi" }
        }
    }

    /** Nilai [value] sah untuk field ini? Kosong selalu sah (belum diisi). */
    fun accepts(value: String): Boolean = when {
        value.isEmpty() -> true
        type == FieldType.ENUM -> value in options
        type == FieldType.NUMBER -> value.toDoubleOrNull() != null
        type == FieldType.BOOL -> value == "ya" || value == "tidak"
        else -> true
    }
}

/**
 * Mesin status satu field ENUM: dari status X boleh ke status mana. Status yang tidak disebut di
 * [transitions] tidak punya jalan keluar; pindah ke status yang sama selalu sah.
 */
data class StateMachine(val field: String, val transitions: Map<String, Set<String>>) {
    fun allows(from: String, to: String): Boolean = from == to || to in transitions[from].orEmpty()
}

/** Satu jenis dokumen/benda yang dikelola layar prototype (mis. SPK, PO). Isinya data pack. */
data class EntitySpec(
    val id: String,
    val label: String,
    val fields: List<FieldSpec>,
    val stateMachine: StateMachine? = null
) {
    init {
        require(id.isNotBlank()) { "EntitySpec.id kosong" }
        require(fields.map { it.key }.distinct().size == fields.size) { "Entitas '$id' punya field kembar" }
        stateMachine?.let { sm ->
            val field = requireNotNull(fields.firstOrNull { it.key == sm.field }) {
                "Mesin status entitas '$id' menunjuk field '${sm.field}' yang tidak ada"
            }
            require(field.type == FieldType.ENUM) { "Mesin status '${sm.field}' wajib field ENUM" }
            val known = field.options.toSet()
            require(sm.transitions.all { (from, tos) -> from in known && tos.all { it in known } }) {
                "Transisi entitas '$id' memuat status di luar opsi '${sm.field}'"
            }
        }
    }

    fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }
}
