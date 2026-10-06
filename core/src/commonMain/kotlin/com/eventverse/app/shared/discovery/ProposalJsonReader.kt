package com.eventverse.app.shared.discovery

import com.eventverse.app.shared.json.JsonValue

/**
 * Pembaca satu objek JSON dengan **path** untuk pesan galat (pola `DiscoveryDraftCodec.Reader`). Ketat: nilai
 * bertipe salah **ditolak**, tidak dibuang diam-diam. Kunci opsional yang absen memakai nilai bawaan skema
 * (bukan fallback atas data rusak — kunci yang ada tapi salah tipe tetap ditolak).
 */
internal class ProposalJsonReader(private val obj: JsonValue.Obj, val path: String) {

    /** Objek mentah, untuk helper kawat bersama yang membaca sendiri (mis. `InteractiveScreenCodec`). */
    fun rawNode(): JsonValue.Obj = obj

    fun fail(key: String?, message: String): Nothing =
        throw DiscoveryDraftDecodeException(if (key == null) path else "$path.$key", message)

    fun string(key: String): String = (obj[key] as? JsonValue.Str)?.value ?: fail(key, "wajib string")

    fun optString(key: String): String? = when (val v = obj[key]) {
        null, JsonValue.Null -> null
        is JsonValue.Str -> v.value
        else -> fail(key, "harus string")
    }

    fun boolean(key: String, default: Boolean): Boolean = when (val v = obj[key]) {
        null, JsonValue.Null -> default
        is JsonValue.Bool -> v.value
        else -> fail(key, "harus boolean")
    }

    /** Array objek wajib. */
    fun objects(key: String): List<ProposalJsonReader> = when (val v = obj[key]) {
        is JsonValue.Arr -> v.items.mapIndexed { i, item ->
            ProposalJsonReader(item as? JsonValue.Obj ?: fail("$key[$i]", "harus objek"), "$path.$key[$i]")
        }
        else -> fail(key, "wajib array")
    }

    /** Array objek opsional: absen = kosong; ada tapi bukan array = ditolak. */
    fun optObjects(key: String): List<ProposalJsonReader> = if (obj[key] == null || obj[key] == JsonValue.Null) emptyList() else objects(key)

    fun optObject(key: String): ProposalJsonReader? = when (val v = obj[key]) {
        null, JsonValue.Null -> null
        is JsonValue.Obj -> ProposalJsonReader(v, "$path.$key")
        else -> fail(key, "harus objek")
    }

    fun obj(key: String): ProposalJsonReader = optObject(key) ?: fail(key, "wajib objek")

    /** Array string opsional: absen = kosong; butir bukan string ditolak. */
    fun strings(key: String): List<String> = when (val v = obj[key]) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> v.items.mapIndexed { i, it -> (it as? JsonValue.Str)?.value ?: fail("$key[$i]", "harus string") }
        else -> fail(key, "harus array string")
    }

    /** Objek `{kunci: [string]}` opsional, mis. transisi status. */
    fun stringListMap(key: String): Map<String, List<String>> = when (val v = obj[key]) {
        null, JsonValue.Null -> emptyMap()
        is JsonValue.Obj -> v.entries.mapValues { (k, item) ->
            (item as? JsonValue.Arr)?.items?.mapIndexed { i, s -> (s as? JsonValue.Str)?.value ?: fail("$key.$k[$i]", "harus string") }
                ?: fail("$key.$k", "harus array string")
        }
        else -> fail(key, "harus objek")
    }

    /** Array objek `{kunci: string}` opsional; nilai non-string ditolak (angka harus ditulis "5"). */
    fun stringRows(key: String): List<Map<String, String>> = when (val v = obj[key]) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> v.items.mapIndexed { i, row ->
            (row as? JsonValue.Obj ?: fail("$key[$i]", "harus objek")).entries.mapValues { (k, cell) ->
                (cell as? JsonValue.Str)?.value ?: fail("$key[$i].$k", "nilai harus string (angka ditulis sebagai teks, mis. \"5\")")
            }
        }
        else -> fail(key, "harus array")
    }

    /** Membungkus galat konstruktor tipe domain jadi galat berpath pada objek ini. */
    fun <T> build(block: () -> T): T = try { block() } catch (e: DiscoveryDraftDecodeException) { throw e } catch (e: IllegalArgumentException) {
        fail(null, e.message ?: "tidak sah")
    }

    /** Pengurai nilai di [key] lewat [parse] yang boleh melempar `IllegalArgumentException`; galatnya diberi path kunci. */
    fun <T> parsed(key: String, parse: () -> T): T = try { parse() } catch (e: IllegalArgumentException) { fail(key, e.message ?: "tidak sah") }
}
