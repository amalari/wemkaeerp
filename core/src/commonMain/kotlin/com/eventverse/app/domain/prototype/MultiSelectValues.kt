package com.eventverse.app.domain.prototype

import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Aturan **nilai** tipe [FieldType.MULTI_SELECT] (TRD-FIELD-003 R2, keputusan A0).
 *
 * Nilai sel adalah **string JSON array** nama opsi (mis. `["Gigi","Jantung"]`), bukan pemisah teks — opsi boleh
 * memuat koma/spasi apa pun sehingga pemisah akan bentrok. Aturan kanonik:
 *  - urutan elemen **mengikuti urutan `options`** (bukan urutan klik), tanpa duplikat, tiap elemen harus ada di
 *    `options`; nilai yang sama selalu menghasilkan string yang sama (byte-stabil);
 *  - belum diisi = string kosong `""`; array kosong `"[]"` **ditolak** (satu bentuk kosong, tidak dua);
 *  - [maxSelections] (bila ada) membatasi jumlah pilihan yang boleh dipilih.
 *
 * File ini sengaja terpisah dari [EntitySpec] supaya aturan nilai tidak menambah panjang file kosakata (batas 250).
 */
object MultiSelectValues {

    /** Batas pilihan dari [FieldType.MULTI_SELECT]; nilai harus persis [options] **dan** tidak melewati batas. */
    fun isValid(raw: String, options: List<String>, maxSelections: Int?): Boolean {
        if (raw.isEmpty()) return true // belum diisi
        val parsed = parse(raw) ?: return false // bukan array JSON string sah
        if (parsed.isEmpty()) return false // "[]" ditolak — satu bentuk kosong saja
        if (parsed.distinct().size != parsed.size) return false // duplikat
        if (parsed.any { it !in options }) return false // elemen di luar options
        if (maxSelections != null && parsed.size > maxSelections) return false // melebihi batas
        return true
    }

    /**
     * Kayuh nilai dari daftar [selected]: ambil hanya elemen yang ada di [options], susun mengikuti urutan [options],
     * buang duplikat. Hasil = string JSON array kanonik, atau `""` bila tidak ada yang terpilih.
     */
    fun encode(selected: Collection<String>, options: List<String>): String {
        val chosen = selected.toSet()
        val ordered = options.filter { it in chosen }
        if (ordered.isEmpty()) return ""
        return jsonArrayOf(ordered.map { jsonOf(it) }).encode()
    }

    /** Baca [raw] sebagai array JSON string; `null` bila bukan array JSON dari string. */
    fun parse(raw: String): List<String>? {
        if (raw.isEmpty()) return null
        val node = runCatching { JsonParser.parse(raw) }.getOrNull() ?: return null
        val arr = node as? JsonValue.Arr ?: return null
        val out = ArrayList<String>(arr.items.size)
        for (item in arr.items) {
            val str = item as? JsonValue.Str ?: return null
            out.add(str.value)
        }
        return out
    }
}
