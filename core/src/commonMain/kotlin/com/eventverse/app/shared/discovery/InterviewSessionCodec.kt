package com.eventverse.app.shared.discovery

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.interview.Confirmation
import com.eventverse.app.domain.discovery.interview.DivisionCode
import com.eventverse.app.domain.discovery.interview.DivisionDraft
import com.eventverse.app.domain.discovery.interview.InterviewAnswer
import com.eventverse.app.domain.discovery.interview.InterviewQuestion
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ItemSource
import com.eventverse.app.domain.discovery.interview.ModuleHandoff
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.domain.discovery.interview.RoleDraft
import com.eventverse.app.domain.discovery.interview.RoleKey
import com.eventverse.app.domain.discovery.interview.RoleModuleLink
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf

/**
 * Parser tunggal JSON ⇄ [InterviewSession], bagian dari `DiscoveryDraftCodec` (kunci `interview`).
 *
 * **Ketat** (Kontrak 4): kunci enum tak dikenal, field wajib hilang, atau tipe salah **ditolak** dengan path —
 * tidak ada fallback ke nilai bawaan. Kunci opsional (`confidence`, `text`, `isHead`, `features`) ditulis hanya bila
 * berisi, sehingga round-trip stabil.
 */
object InterviewSessionCodec {

    fun encode(s: InterviewSession): JsonValue.Obj = encodeCore(s).let { core ->
        // Kunci berdasar-cerita ditulis hanya bila ada: wawancara lama ter-encode byte-per-byte sama.
        val extra = buildMap<String, JsonValue> {
            if (s.version != 1) put("version", jsonOf(s.version))
            s.narrative?.let { put("narrative", jsonOf(it)) }
            s.profile?.let { put("profile", InterviewBasisCodec.encodeProfile(it)) }
            if (s.specs.isNotEmpty()) put("specs", InterviewBasisCodec.encodeSpecs(s.specs))
            if (s.clarifications.isNotEmpty()) put("clarifications", jsonArrayOf(s.clarifications.map { c ->
                val base = jsonObjectOf("id" to jsonOf(c.id), "question" to jsonOf(c.question))
                c.answer?.let { JsonValue.Obj(base.entries + ("answer" to jsonOf(it))) } ?: base
            }))
        }
        if (extra.isEmpty()) core else JsonValue.Obj(core.entries + extra)
    }

    private fun encodeCore(s: InterviewSession): JsonValue.Obj = jsonObjectOf(
        "step" to jsonOf(s.step.code),
        "divisions" to jsonArrayOf(s.divisions.map { jsonObjectOf("code" to jsonOf(it.code.value), "name" to jsonOf(it.name), "source" to jsonOf(it.source.code)).let { o -> InterviewBasisCodec.withRef(o, it.basisRef) } }),
        "roles" to jsonArrayOf(s.roles.map { r ->
            val base = jsonObjectOf("roleKey" to jsonOf(r.roleKey.value), "label" to jsonOf(r.label), "divisionCode" to jsonOf(r.divisionCode.value), "source" to jsonOf(r.source.code))
            InterviewBasisCodec.withRef(if (r.isHead) JsonValue.Obj(base.entries + ("isHead" to jsonOf(true))) else base, r.basisRef)
        }),
        "links" to jsonArrayOf(s.links.map { l ->
            val base = jsonObjectOf(
                "roleKey" to jsonOf(l.roleKey.value), "moduleId" to jsonOf(l.moduleId.value), "origin" to jsonOf(l.origin.code),
                "features" to jsonArrayOf(l.features.map { jsonOf(it) }), "confirmed" to jsonOf(l.confirmed.code)
            )
            InterviewBasisCodec.withRef(l.confidence?.let { JsonValue.Obj(base.entries + ("confidence" to jsonOf(it))) } ?: base, l.basisRef)
        }),
        "handoffs" to jsonArrayOf(s.handoffs.map { jsonObjectOf("from" to jsonOf(it.from.value), "to" to jsonOf(it.to.value), "portType" to jsonOf(it.portType.value), "confirmed" to jsonOf(it.confirmed.code)).let { o -> InterviewBasisCodec.withRef(o, it.basisRef) } }),
        "answers" to jsonArrayOf(s.answers.map { a ->
            val base = jsonObjectOf("turn" to jsonOf(a.turn), "step" to jsonOf(a.step.code), "questionId" to jsonOf(a.questionId), "outcome" to jsonOf(a.outcome.code))
            a.text?.let { JsonValue.Obj(base.entries + ("text" to jsonOf(it))) } ?: base
        })
    )

    /** Pertanyaan untuk klien: tebakan membawa `origin` (kode) hanya untuk giliran modul. */
    fun encodeQuestion(q: InterviewQuestion): JsonValue.Obj = jsonObjectOf(
        "id" to jsonOf(q.id), "step" to jsonOf(q.step.code), "prompt" to jsonOf(q.prompt),
        "guesses" to jsonArrayOf(q.guesses.map { g ->
            val base = jsonObjectOf("key" to jsonOf(g.key), "label" to jsonOf(g.label), "confidence" to jsonOf(g.confidence))
            g.origin?.let { JsonValue.Obj(base.entries + ("origin" to jsonOf(it.code))) } ?: base
        })
    )

    fun decode(obj: JsonValue.Obj, at: String): InterviewSession = InterviewSession(
        step = R(obj, at).enum("step", InterviewStep::fromCode),
        version = R(obj, at).optInt("version")?.also { v ->
            if (v != 1 && v != InterviewSession.BASED_ON_STORY) throw DiscoveryDraftDecodeException("$at.version", "versi $v tidak dikenal (1 atau ${InterviewSession.BASED_ON_STORY})")
        } ?: 1,
        narrative = R(obj, at).optString("narrative"),
        profile = when (val v = obj["profile"]) {
            null, JsonValue.Null -> null
            is JsonValue.Obj -> InterviewBasisCodec.decodeProfile(v, "$at.profile")
            else -> throw DiscoveryDraftDecodeException("$at.profile", "harus objek")
        },
        specs = InterviewBasisCodec.decodeSpecs(obj["specs"], "$at.specs"),
        clarifications = when (val v = obj["clarifications"]) {
            null, JsonValue.Null -> emptyList()
            is JsonValue.Arr -> v.items.mapIndexed { i, item ->
                val r = R(item as? JsonValue.Obj ?: throw DiscoveryDraftDecodeException("$at.clarifications[$i]", "harus objek"), "$at.clarifications[$i]")
                r.build { Clarification(r.string("id"), r.string("question"), r.optString("answer")) }
            }
            else -> throw DiscoveryDraftDecodeException("$at.clarifications", "harus array")
        },
        divisions = R(obj, at).items("divisions") { r ->
            r.build { DivisionDraft(r.value("code", ::DivisionCode), r.string("name"), r.enum("source", ItemSource::fromCode), r.ref()) }
        },
        roles = R(obj, at).items("roles") { r ->
            r.build { RoleDraft(r.value("roleKey", ::RoleKey), r.string("label"), r.value("divisionCode", ::DivisionCode), r.enum("source", ItemSource::fromCode), r.optBoolean("isHead") ?: false, r.ref()) }
        },
        links = R(obj, at).items("links") { r ->
            r.build {
                RoleModuleLink(
                    r.value("roleKey", ::RoleKey), r.value("moduleId", ::ModuleId), r.enum("origin", ModuleOrigin::fromCode),
                    r.strings("features"), r.enum("confirmed", Confirmation::fromCode), r.optInt("confidence"), r.ref()
                )
            }
        },
        handoffs = R(obj, at).items("handoffs") { r ->
            r.build { ModuleHandoff(r.value("from", ::ModuleId), r.value("to", ::ModuleId), r.value("portType", ::PortType), r.enum("confirmed", Confirmation::fromCode), r.ref()) }
        },
        answers = R(obj, at).items("answers") { r ->
            r.build { InterviewAnswer(r.int("turn"), r.enum("step", InterviewStep::fromCode), r.string("questionId"), r.enum("outcome", Confirmation::fromCode), r.optString("text")) }
        }
    )

    private class R(private val obj: JsonValue.Obj, val path: String) {
        fun ref(): com.eventverse.app.domain.discovery.interview.BasisRef? = InterviewBasisCodec.optRef(obj, path)

        fun fail(key: String?, message: String): Nothing = throw DiscoveryDraftDecodeException(if (key == null) path else "$path.$key", message)

        fun <T> build(block: () -> T): T = try { block() } catch (e: DiscoveryDraftDecodeException) { throw e } catch (e: IllegalArgumentException) { fail(null, e.message ?: "tidak sah") }

        fun string(key: String): String = (obj[key] as? JsonValue.Str)?.value ?: fail(key, "wajib string")

        fun optString(key: String): String? = when (val v = obj[key]) { null, JsonValue.Null -> null; is JsonValue.Str -> v.value; else -> fail(key, "harus string") }

        fun int(key: String): Int = (obj[key] as? JsonValue.Num)?.asInt ?: fail(key, "wajib angka")

        fun optInt(key: String): Int? = when (val v = obj[key]) { null, JsonValue.Null -> null; is JsonValue.Num -> v.asInt ?: fail(key, "harus bilangan bulat"); else -> fail(key, "harus angka") }

        fun optBoolean(key: String): Boolean? = when (val v = obj[key]) { null, JsonValue.Null -> null; is JsonValue.Bool -> v.value; else -> fail(key, "harus boolean") }

        fun <T> value(key: String, ctor: (String) -> T): T = try { ctor(string(key)) } catch (e: IllegalArgumentException) { fail(key, e.message ?: "tidak sah") }

        fun <T> enum(key: String, parse: (String) -> T?): T {
            val raw = string(key)
            return parse(raw) ?: fail(key, "'$raw' bukan kosakata tertutup yang dikenal")
        }

        fun strings(key: String): List<String> = when (val v = obj[key]) {
            is JsonValue.Arr -> v.items.mapIndexed { i, it -> (it as? JsonValue.Str)?.value ?: fail("$key[$i]", "harus string") }
            else -> fail(key, "wajib array")
        }

        fun <T> items(key: String, read: (R) -> T): List<T> = when (val v = obj[key]) {
            is JsonValue.Arr -> v.items.mapIndexed { i, item -> read(R(item as? JsonValue.Obj ?: fail("$key[$i]", "harus objek"), "$path.$key[$i]")) }
            else -> fail(key, "wajib array")
        }
    }
}
