package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.storage.FileRef

/**
 * Mesin status satu field ENUM: dari status X boleh ke status mana. Status yang tidak disebut di
 * [transitions] tidak punya jalan keluar; pindah ke status yang sama selalu sah.
 */
data class StateMachine(val field: String, val transitions: Map<String, Set<String>>) {
    fun allows(from: String, to: String): Boolean = from == to || to in transitions[from].orEmpty()
}

/**
 * Satu jenis dokumen/benda yang dikelola layar prototype (mis. SPK, PO). Isinya data pack.
 * Kosakata field/tipe ([FieldType], [NumberFormat], [FieldSpec]) tinggal di `FieldSpec.kt` (paket sama).
 */
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
            val known = field.optionLabels.toSet()
            require(sm.transitions.all { (from, tos) -> from in known && tos.all { it in known } }) {
                "Transisi entitas '$id' memuat status di luar opsi '${sm.field}'"
            }
        }
    }

    fun field(key: String): FieldSpec? = fields.firstOrNull { it.key == key }

    /**
     * Kepemilikan nilai FILE untuk jalur TULIS di server (hardening lintas tenant): [FieldSpec.accepts] hanya
     * memeriksa bentuk [FileRef], jadi tanpa ini tenant A bisa menyimpan `fields/<tenantB>/...`. Mengembalikan
     * pesan galat untuk field FILE pertama yang berisi ref bukan milik [tenantId], atau `null` bila bersih.
     * Kosong = belum diisi (dilewati); bentuk rusak dilaporkan reducer, bukan di sini. Reducer sengaja tetap
     * tenant-buta (dipakai klien/port in-memory), jadi route server wajib memanggil ini SEBELUM reducer.
     *
     * [recordId] (TRD-FIELD-004 FR-1.3, **wajib** — tanpa default agar kompilator memaksa tiap pemanggil memutuskan):
     * id record yang sedang ditulis, dan ref harus terikat padanya. `null` = record BARU (POST): belum ada berkas
     * yang mungkin sah, jadi nilai FILE terisi ditolak (unggah dilakukan setelah record tersimpan).
     */
    fun fileOwnershipProblem(tenantId: String, recordId: String?, values: Map<String, String>): String? =
        fields.firstNotNullOfOrNull { f ->
            val v = values[f.key].orEmpty()
            when {
                f.type != FieldType.FILE || v.isEmpty() -> null
                recordId == null -> "'${f.label}' tidak dapat diisi saat membuat data baru; unggah berkas setelah data tersimpan."
                !FileRef.isValidFor(tenantId, v, recordId = recordId) -> "'${f.label}' merujuk berkas yang bukan milik data ini."
                else -> null
            }
        }
}
