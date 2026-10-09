package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.storage.FileRef

/**
 * Tipe field prototype: kosakata **tertutup milik sistem** (lolos Uji Variabilitas — renderer harus
 * bisa menggambar tiap tipe di semua vertikal). Nama field, opsi enum, dan transisi tetap data.
 *
 * Tanda tangan format simpan per tipe (A0 Irisan 2, lihat `docs/plannings/PLAN-field-component-gaps.md` §2):
 * - [TEXT], [LONG_TEXT]: string, kolom SQL `TEXT`, disimpan **apa adanya**. [TEXT] boleh punya [FieldSpec.validation]
 *   ([TextValidation] EMAIL/PHONE: hanya aturan bentuk nilai, penyimpanan tak berubah); [LONG_TEXT] untuk isi panjang/multibaris tanpa validasi
 *   (padanan CRM `FieldType.LongText`; per keputusan D2 kosakatanya tetap terpisah).
 * - [NUMBER]: string angka desimal, kolom `NUMERIC(18,4)`.
 * - [DATE]: tanggal kalender ISO `TTTT-BB-HH`, kolom `DATE`; dengan [FieldSpec.withTime] = true: `TTTT-BB-HH'T'JJ:MM`
 *   (mis. `2026-10-08T14:30`), waktu dinding **tanpa zona waktu** tepat sampai menit, kolom `TIMESTAMP` (aturan di
 *   [DateFieldValues]).
 * - [ENUM]: salah satu opsi di [FieldSpec.options].
 * - [BOOL]: `ya` / `tidak`, kolom `BOOLEAN`.
 * - [RELATION]: id baris target (string) di modul pemegang field, kolom `VARCHAR(64)` **tanpa**
 *   `REFERENCES` (rujukan logis, pagar J3 — TRD-FIELD-001 FR-1); target rujukan ada di
 *   [FieldSpec.target].
 * - [FILE]: referensi objek `FileRef` (string key) — byte hidup di `ObjectStorage`, TIDAK PERNAH di
 *   kolom/jsonb (TRD-FIELD-002 FR-2); seed v1 wajib kosong.
 */
enum class FieldType { TEXT, LONG_TEXT, NUMBER, DATE, ENUM, BOOL, RELATION, FILE }

/**
 * Varian tampilan [FieldType.NUMBER] (C4 Irisan 2, keputusan D3): penyimpanan, filter, urutan, dan
 * koersi **identik** dengan angka polos (`NUMERIC(18,4)`) — hanya render dan parsing masukan yang beda.
 * Ini parameter, bukan tipe baru; padanan CRM `Number(format = Currency)`.
 *
 * Arti nilai tersimpan (sama untuk semua format; hanya render/parsing masukan yang beda):
 * - [PLAIN]: angka apa adanya.
 * - [CURRENCY]: jumlah dalam **satuan utama** mata uang field ([FieldSpec.currencyCode]), bukan sen/satuan terkecil;
 *   kode **tidak** ikut tersimpan di kolom.
 * - [PERCENT]: angka persen **apa adanya** — `12.5` berarti 12,5%, **bukan** pecahan `0.125`. Tidak ada skala
 *   tersembunyi di penyimpanan, jadi filter/urutan/agregat SQL tidak perlu tahu formatnya. (Padanan CRM
 *   `NumberFormat.Percent` belum mendefinisikan skala di kodenya; aturan ini berlaku untuk kosakata prototype.)
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
    val currencyCode: String? = null,
    /**
     * A0(C6) Irisan 2: tanggal + jam (menit). Hanya sah untuk [FieldType.DATE]. Mengubah **penyimpanan** (kolom
     * `TIMESTAMP`, nilai `TTTT-BB-HH'T'JJ:MM`), bukan hanya tampilan — lihat [DateFieldValues].
     */
    val withTime: Boolean = false,
    /**
     * A0(C9) Irisan 2: validasi bentuk teks. Hanya sah untuk [FieldType.TEXT] (bukan [FieldType.LONG_TEXT]); selain
     * [TextValidation.NONE] pada tipe lain ditolak. Nilai tetap disimpan apa adanya — lihat [TextValidations].
     */
    val validation: TextValidation = TextValidation.NONE,
    /**
     * C7 (TRD-FIELD-001): target rujukan; wajib tepat bila type == [FieldType.RELATION], wajib null selain itu.
     * Format: "entityId" (satu modul) atau "moduleId:entityId" (lintas modul, harus bisa diresolusi
     * `DomainPack.resolveModule` — modul sendiri atau moduleReferences/sharedModules). Metadata spec saja:
     * tidak masuk kolom SQL (kolomnya menyimpan id baris target).
     */
    val target: String? = null
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
        require(type == FieldType.DATE || !withTime) {
            "Field '$key' bertipe ${type.name}, bukan DATE, jadi tidak boleh punya withTime"
        }
        require(type == FieldType.TEXT || validation == TextValidation.NONE) {
            "Field '$key' bertipe ${type.name}, bukan TEXT, jadi tidak boleh punya validation ${validation.name}"
        }
        if (format == NumberFormat.CURRENCY) {
            require(currencyCode != null && CurrencyCode.isValid(currencyCode)) {
                "Field '$key' berformat CURRENCY wajib punya kode mata uang tiga huruf besar (mis. IDR), bukan '$currencyCode'"
            }
        } else {
            require(currencyCode == null) { "Field '$key' berformat ${format.name}, jadi tidak boleh punya kode mata uang" }
        }
        if (type == FieldType.RELATION) {
            // C7: bentuk target divalidasi di sini; keberadaan baris target divalidasi server saat tulis nilai
            // (fail-closed lewat jalur baca modul target), bukan di konstruktor ini.
            require(!target.isNullOrBlank() && !target.contains(' ') && target.count { it == ':' } <= 1) {
                "Field RELATION '$key' wajib punya target 'entityId' atau 'moduleId:entityId' (tanpa spasi, maksimum satu ':'), dapat '$target'"
            }
        } else {
            require(target == null) { "Field '$key' bertipe ${type.name}, bukan RELATION, jadi tidak boleh punya target" }
        }
    }

    /** Nilai [value] sah untuk field ini? Kosong selalu sah (belum diisi). */
    fun accepts(value: String): Boolean {
        if (value.isEmpty()) return true
        return when (type) {
            FieldType.TEXT -> TextValidations.isValid(validation, value)
            FieldType.LONG_TEXT -> true
            FieldType.NUMBER -> value.toDoubleOrNull() != null
            // Sama dengan `ProposalEntityRules`: tanggal ISO (TTTT-BB-HH) atau, bila withTime, TTTT-BB-HHTJJ:MM; bukan teks bebas.
            FieldType.DATE -> DateFieldValues.isValid(value, withTime)
            FieldType.ENUM -> value in options
            FieldType.BOOL -> value == "ya" || value == "tidak"
            // C7: id target non-blank tanpa ".."; keberadaan target diverifikasi server, bukan klien.
            FieldType.RELATION -> value.isNotBlank() && !value.contains("..")
            // C8: kosong = belum diisi; selain itu wajib FileRef sah (bentuk key, bukan keberadaan objek).
            FieldType.FILE -> FileRef.isValid(value)
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
