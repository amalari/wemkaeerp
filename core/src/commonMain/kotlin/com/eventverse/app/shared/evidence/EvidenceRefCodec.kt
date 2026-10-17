package com.eventverse.app.shared.evidence

import com.eventverse.app.domain.evidence.EvidenceRef
import com.eventverse.app.domain.evidence.EvidenceSource
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Bentuk JSON [EvidenceRef] (TRD-BLDR-003 FR-1, dibekukan Track 0) — **satu parser** untuk payload brief/skenario
 * v2 dan klien. Ketat (tenant-variability Kontrak 4): `source` tak dikenal, kunci tak dikenal, dan tipe salah
 * **ditolak** dengan path; tidak ada fallback ke sumber lain (FR-1.4).
 *
 * ```
 * {"source":"NARRATIVE","quote":"..."}
 * {"source":"INTERVIEW_ANSWER","referenceId":"q-3"}
 * {"source":"PACK_DEFAULT","artifactKey":"modules.qc.summary"}
 * ```
 *
 * Kunci referensi ditulis hanya bila ada; kecukupan isinya ditegakkan [com.eventverse.app.domain.evidence.EvidenceRules].
 */
object EvidenceRefCodec {

    val KNOWN_KEYS = setOf("source", "quote", "referenceId", "artifactKey")

    fun encode(ref: EvidenceRef): JsonValue.Obj {
        val base = jsonObjectOf("source" to jsonOf(ref.source.name))
        val extra = buildMap<String, JsonValue> {
            ref.quote?.let { put("quote", jsonOf(it)) }
            ref.referenceId?.let { put("referenceId", jsonOf(it)) }
            ref.artifactKey?.let { put("artifactKey", jsonOf(it)) }
        }
        return JsonValue.Obj(base.entries + extra)
    }

    /** Menambahkan `evidence` ke objek [base] hanya bila ada (butir legacy tanpa bukti tetap bentuk lamanya). */
    fun withEvidence(base: JsonValue.Obj, ref: EvidenceRef?): JsonValue.Obj =
        if (ref == null) base else JsonValue.Obj(base.entries + ("evidence" to encode(ref)))

    fun decode(item: JsonValue, path: String): EvidenceRef {
        val o = item as? JsonValue.Obj ?: error("$path bukan objek")
        val unknown = o.entries.keys - KNOWN_KEYS
        require(unknown.isEmpty()) { "$path memuat kunci tak dikenal: ${unknown.sorted().joinToString()}" }
        val name = (o["source"] as? JsonValue.Str)?.value ?: error("$path.source wajib teks")
        val source = EvidenceSource.entries.firstOrNull { it.name == name } ?: error("$path.source tak dikenal: '$name'")
        return EvidenceRef(
            source = source,
            quote = optString(o, "quote", path),
            referenceId = optString(o, "referenceId", path),
            artifactKey = optString(o, "artifactKey", path),
        )
    }

    private fun optString(o: JsonValue.Obj, key: String, path: String): String? = when (val v = o[key]) {
        null, JsonValue.Null -> null
        is JsonValue.Str -> v.value
        else -> error("$path.$key harus teks")
    }
}
