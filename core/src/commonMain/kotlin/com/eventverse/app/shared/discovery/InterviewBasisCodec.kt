package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.BasisRef
import com.eventverse.app.domain.discovery.interview.BusinessProfile
import com.eventverse.app.domain.discovery.interview.RequirementSpec
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Codec bagian "berdasar cerita" dari wawancara: `basisRef`, `profile`, `specs`. Semuanya **opsional di JSON**
 * (wawancara lama tanpa kunci ini terbaca apa adanya) dan ditulis hanya bila ada. Ketat: kode `basis` tak dikenal
 * atau tipe salah **ditolak** berpath, tidak diganti default.
 */
object InterviewBasisCodec {

    fun encodeRef(ref: BasisRef): JsonValue.Obj {
        val base = jsonObjectOf("basis" to jsonOf(ref.basis.code))
        val extra = buildMap<String, JsonValue> {
            ref.quote?.let { put("quote", jsonOf(it)) }
            ref.answerId?.let { put("answerId", jsonOf(it)) }
        }
        return JsonValue.Obj(base.entries + extra)
    }

    /** Menambahkan `basisRef` ke objek [base] hanya bila ada. */
    fun withRef(base: JsonValue.Obj, ref: BasisRef?): JsonValue.Obj =
        if (ref == null) base else JsonValue.Obj(base.entries + ("basisRef" to encodeRef(ref)))

    fun optRef(parent: JsonValue.Obj, at: String): BasisRef? = when (val v = parent["basisRef"]) {
        null, JsonValue.Null -> null
        is JsonValue.Obj -> {
            val p = "$at.basisRef"
            val code = v.string("basis") ?: fail("$p.basis", "wajib string")
            BasisRef(
                Basis.fromCode(code) ?: fail("$p.basis", "'$code' bukan kosakata tertutup yang dikenal"),
                optString(v, "quote", p), optString(v, "answerId", p)
            )
        }
        else -> fail("$at.basisRef", "harus objek")
    }

    fun encodeProfile(p: BusinessProfile): JsonValue.Obj = jsonObjectOf(
        "summary" to jsonOf(p.summary),
        "goals" to jsonArrayOf(p.goals.map { jsonOf(it) }),
        "painPoints" to jsonArrayOf(p.painPoints.map { jsonOf(it) })
    )

    fun decodeProfile(o: JsonValue.Obj, at: String): BusinessProfile =
        BusinessProfile(o.string("summary") ?: fail("$at.summary", "wajib string"), strings(o, "goals", at), strings(o, "painPoints", at))

    fun encodeSpecs(specs: List<RequirementSpec>): JsonValue.Arr = jsonArrayOf(specs.map { s ->
        val extra = buildMap<String, JsonValue> {
            s.whoFills?.let { put("whoFills", jsonOf(it)) }; s.whatRecorded?.let { put("whatRecorded", jsonOf(it)) }
            s.whoSees?.let { put("whoSees", jsonOf(it)) }; s.doneWhen?.let { put("doneWhen", jsonOf(it)) }
        }
        withRef(JsonValue.Obj(jsonObjectOf("areaKey" to jsonOf(s.areaKey.value)).entries + extra), s.basisRef)
    })

    fun decodeSpecs(raw: JsonValue?, at: String): List<RequirementSpec> = when (raw) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> raw.items.mapIndexed { i, item ->
            val p = "$at[$i]"
            val o = item as? JsonValue.Obj ?: fail(p, "harus objek")
            val key = o.string("areaKey") ?: fail("$p.areaKey", "wajib string")
            RequirementSpec(
                try { RoleKey(key) } catch (e: IllegalArgumentException) { fail("$p.areaKey", e.message ?: "tidak sah") },
                optString(o, "whoFills", p), optString(o, "whatRecorded", p), optString(o, "whoSees", p), optString(o, "doneWhen", p), optRef(o, p)
            )
        }
        else -> fail(at, "harus array")
    }

    fun optString(o: JsonValue.Obj, key: String, at: String): String? = when (val v = o[key]) {
        null, JsonValue.Null -> null
        is JsonValue.Str -> v.value
        else -> fail("$at.$key", "harus string")
    }

    private fun strings(o: JsonValue.Obj, key: String, at: String): List<String> = when (val v = o[key]) {
        null, JsonValue.Null -> emptyList()
        is JsonValue.Arr -> v.items.mapIndexed { i, t -> (t as? JsonValue.Str)?.value ?: fail("$at.$key[$i]", "harus string") }
        else -> fail("$at.$key", "harus array")
    }

    private fun fail(path: String, message: String): Nothing = throw DiscoveryDraftDecodeException(path, message)
}
