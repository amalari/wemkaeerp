package com.eventverse.app.domain.prototype

import kotlinx.datetime.LocalDate

/**
 * Tipe field prototype: kosakata **tertutup milik sistem** (lolos Uji Variabilitas — renderer harus
 * bisa menggambar tiap tipe di semua vertikal). Nama field, opsi enum, dan transisi tetap data.
 *
 * Tanda tangan format simpan per tipe (A0 Irisan 2, lihat `docs/plannings/PLAN-field-component-gaps.md` §2):
 * - [TEXT], [LONG_TEXT]: string bebas, kolom SQL `TEXT`; [LONG_TEXT] untuk isi panjang/multibaris
 *   (padanan CRM `FieldType.LongText`; per keputusan D2 kosakatanya tetap terpisah).
 * - [NUMBER]: string angka desimal, kolom `NUMERIC(18,4)`.
 * - [DATE]: tanggal kalender ISO `TTTT-BB-HH`, kolom `DATE`.
 * - [ENUM]: salah satu opsi di [FieldSpec.options].
 * - [BOOL]: `ya` / `tidak`, kolom `BOOLEAN`.
 */
enum class FieldType { TEXT, LONG_TEXT, NUMBER, DATE, ENUM, BOOL }

/**
 * Varian tampilan [FieldType.NUMBER] (C4 Irisan 2, keputusan D3): penyimpanan, filter, urutan, dan
 * koersi **identik** dengan angka polos (`NUMERIC(18,4)`) — hanya render dan parsing masukan yang beda.
 * Ini parameter, bukan tipe baru; padanan CRM `Number(format = Currency)`.
 */
enum class NumberFormat { PLAIN, CURRENCY, PERCENT }

data class FieldSpec(
    val key: String,
    val label: String,
    val type: FieldType,
    /** Wajib terisi untuk [FieldType.ENUM]; kosong untuk tipe lain. */
    val options: List<String> = emptyList(),
    /** Kontrak v1: field wajib. Ditegakkan reducer pada `Create`; `SetField` boleh mengosongkan hanya bila tidak wajib. */
    val required: Boolean = false,
    /**
     * C4 Irisan 2: varian tampilan angka; wajib [NumberFormat.PLAIN] untuk tipe selain [FieldType.NUMBER].
     * Tanda tangan simpan tidak berubah — tetap string angka polos di kolom `NUMERIC(18,4)`.
     */
    val format: NumberFormat = NumberFormat.PLAIN,
    /**
     * Kode mata uang per field ([CurrencyCode]); wajib terisi **tepat** bila [format] = [NumberFormat.CURRENCY],
     * dan wajib `null` selain itu. Metadata tampilan: tidak masuk kolom SQL.
     */
    val currencyCode: String? = null
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
        require(type == FieldType.NUMBER || format == NumberFormat.PLAIN) {
            "Field '$key' bertipe ${type.name}, bukan NUMBER, jadi tidak boleh punya format ${format.name}"
        }
        if (format == NumberFormat.CURRENCY) {
            require(currencyCode != null && CurrencyCode.isValid(currencyCode)) {
                "Field '$key' berformat CURRENCY wajib punya kode mata uang tiga huruf besar (mis. IDR), bukan '$currencyCode'"
            }
        } else {
            require(currencyCode == null) { "Field '$key' berformat ${format.name}, jadi tidak boleh punya kode mata uang" }
        }
    }

    /** Nilai [value] sah untuk field ini? Kosong selalu sah (belum diisi). */
    fun accepts(value: String): Boolean {
        if (value.isEmpty()) return true
        return when (type) {
            FieldType.TEXT -> true
            FieldType.LONG_TEXT -> true
            FieldType.NUMBER -> value.toDoubleOrNull() != null
            // Sama dengan `ProposalEntityRules`: tanggal kalender ISO (TTTT-BB-HH), bukan teks bebas.
            FieldType.DATE -> runCatching { LocalDate.parse(value) }.isSuccess
            FieldType.ENUM -> value in options
            FieldType.BOOL -> value == "ya" || value == "tidak"
        }
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
