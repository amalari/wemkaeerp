package com.eventverse.app.shared.pack

import com.eventverse.app.domain.pack.DomainPack
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.ModuleAction
import com.eventverse.app.domain.pack.ModuleActionCode
import com.eventverse.app.domain.pack.ModuleDefinition
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.pack.ModuleSection
import com.eventverse.app.domain.pack.ModuleSectionCode
import com.eventverse.app.domain.pack.PhaseCode
import com.eventverse.app.domain.pack.PhaseDefinition
import com.eventverse.app.domain.pack.PortType
import com.eventverse.app.domain.pack.SlotCode
import com.eventverse.app.domain.pack.SlotDefinition
import com.eventverse.app.domain.pack.VocabularyKey
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleKind
import com.eventverse.app.domain.rbac.ScopeCapability
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import com.eventverse.app.shared.json.jsonStringMapOf

/** Dokumen pack tidak sah. [path] menunjuk field yang salah (`modules[2].kind`), supaya pesan bisa dikembalikan ke AI/penyunting. */
class DomainPackDecodeException(val path: String, message: String) : IllegalArgumentException("$path: $message")

/**
 * Parser tunggal JSON ⇄ [DomainPack] (B7, TRD-PLAT-001-tenant-pack). Dipakai kolom `domain_packs.definition`,
 * `GET /api/tenant/pack`, dan kelak keluaran generator AI.
 *
 * **Ketat** (tenant-variability-rules Kontrak 4): field wajib yang hilang, enum tak dikenal, atau warna rusak
 * **ditolak**, tidak diganti default. Invarian struktur (fase/slot/port/seksi saling menunjuk) tetap milik
 * `DomainPack.init`; pelanggarannya dibungkus menjadi [DomainPackDecodeException] dengan path `$`.
 *
 * Warna ditulis `"#AARRGGBB"` agar terbaca manusia dan AI.
 */
object DomainPackCodec {

    fun encode(pack: DomainPack): JsonValue.Obj = jsonObjectOf(
        "code" to jsonOf(pack.code.value),
        "displayName" to jsonOf(pack.displayName),
        "phases" to jsonArrayOf(pack.phases.map { p ->
            jsonObjectOf(
                "code" to jsonOf(p.code.value), "order" to jsonOf(p.order), "displayName" to jsonOf(p.displayName),
                "subtitle" to jsonOf(p.subtitle), "color" to jsonOf(color(p.colorHex))
            )
        }),
        "slots" to jsonArrayOf(pack.slots.map(SlotDefinitionCodec::encode)),
        "portTypes" to jsonArrayOf(pack.portTypes.map { jsonOf(it.value) }.sortedBy { (it as JsonValue.Str).value }),
        "wiredPortTypes" to jsonArrayOf(pack.wiredPortTypes.map { jsonOf(it.value) }.sortedBy { (it as JsonValue.Str).value }),
        "sections" to jsonArrayOf(pack.sections.map { s ->
            jsonObjectOf(
                "code" to jsonOf(s.code.value), "displayName" to jsonOf(s.displayName), "order" to jsonOf(s.order),
                "color" to jsonOf(color(s.colorHex)), "tint" to jsonOf(color(s.tintHex))
            )
        }),
        "modules" to jsonArrayOf(pack.modules.map { m ->
            jsonObjectOf(
                "id" to jsonOf(m.id.value), "displayName" to jsonOf(m.displayName), "description" to jsonOf(m.description),
                "section" to jsonOf(m.section.value), "kind" to jsonOf(m.kind.name), "iconKey" to jsonOf(m.iconKey),
                "scopeCapability" to jsonOf(m.scopeCapability.name),
                "supportedScopes" to jsonArrayOf(DataScope.entries.filter { it in m.supportedScopes }.map { jsonOf(it.name) }),
                "slot" to jsonOf(m.slot?.value)
            )
        }),
        "actions" to jsonArrayOf(pack.actions.map { a -> jsonObjectOf("code" to jsonOf(a.code.name), "label" to jsonOf(a.label)) }),
        "vocabulary" to jsonStringMapOf(pack.vocabulary.entries.associate { it.key.name to it.value }),
        "portLabels" to jsonStringMapOf(pack.portLabels),
        "screenSuggestions" to ScreenSuggestionCodec.encode(pack.screenSuggestions)
    ).let { root ->
        // Kunci baru ditulis hanya bila ada: pack tanpa kamus ter-encode byte-per-byte sama seperti dulu.
        if (pack.roleHints.isEmpty()) root else JsonValue.Obj(root.entries + ("roleHints" to RoleHintCodec.encode(pack.roleHints)))
    }

    fun encodeToString(pack: DomainPack): String = encode(pack).encode()

    fun decode(raw: String): DomainPack {
        val root = try { JsonParser.parseObject(raw) } catch (e: Exception) {
            throw DomainPackDecodeException("$", "bukan objek JSON (${e.message})")
        }
        return decode(root)
    }

    fun decode(root: JsonValue.Obj): DomainPack {
        val r = Reader(root, "$")
        val phases = r.objects("phases").map { p ->
            p.build { PhaseDefinition(p.value("code", ::PhaseCode), p.int("order"), p.string("displayName"), p.string("subtitle"), p.color("color")) }
        }
        val slots = r.objects("slots").map { s -> SlotDefinitionCodec.decode(s.node(), s.at()) }
        val sections = r.objects("sections").map { s ->
            ModuleSection(s.value("code", ::ModuleSectionCode), s.string("displayName"), s.int("order"), s.color("color"), s.color("tint"))
        }
        val modules = r.objects("modules").map { m ->
            m.build { ModuleDefinition(
                id = m.value("id", ::ModuleId),
                displayName = m.string("displayName"),
                description = m.string("description"),
                section = m.value("section", ::ModuleSectionCode),
                kind = m.enum("kind", ModuleKind.entries),
                iconKey = m.string("iconKey"),
                scopeCapability = m.enum("scopeCapability", ScopeCapability.entries),
                supportedScopes = m.enumSet("supportedScopes", DataScope.entries),
                slot = m.optional("slot")?.let { m.value("slot", ::SlotCode) }
            ) }
        }
        // A4: dua field ini boleh tidak ada — artinya pack "belum mendeklarasikan", dan chrome memakai
        // kata netral platform. Itu **bukan** fallback senyap ke kosakata vertikal: kata netral bukan data
        // vertikal mana pun, dan test mengunci bahwa ia tak pernah memuat kata vertikal.
        val actions = r.objectsOrNull("actions")
            ?.map { a -> a.build { ModuleAction(a.enum("code", ModuleActionCode.entries), a.string("label")) } }
            ?: ModuleActionCode.neutral
        val vocabulary = r.enumKeyedStrings("vocabulary", VocabularyKey.entries)
        // Usulan layar prototype: field boleh tidak ada (pack sebelum fitur mock) — kosong berarti pack
        // belum mengusulkan layar, bukan fallback ke usulan pack lain. Decode ketatnya kini milik
        // ScreenSuggestionCodec (dipecah dari file ini, batas ukuran file).
        val screenSuggestions = ScreenSuggestionCodec.decode(root["screenSuggestions"])
        val roleHints = RoleHintCodec.decode(root["roleHints"], "$.roleHints")
        return r.build {
            DomainPack(
                code = r.value("code", ::DomainPackCode),
                displayName = r.string("displayName"),
                phases = phases,
                slots = slots,
                portTypes = r.values("portTypes", ::PortType).toSet(),
                wiredPortTypes = r.values("wiredPortTypes", ::PortType).toSet(),
                sections = sections,
                modules = modules,
                actions = actions,
                vocabulary = vocabulary,
                portLabels = r.stringMapOrNull("portLabels"),
                screenSuggestions = screenSuggestions,
                roleHints = roleHints
            )
        }
    }

    private fun color(argb: Long): String = "#" + argb.toString(16).uppercase().padStart(8, '0')

    /** Pembaca satu objek dengan path untuk pesan galat. Konstruktor domain yang menolak dibungkus [build] dengan path objeknya. */
    private class Reader(private val obj: JsonValue.Obj, private val path: String) {
        private fun fail(key: String?, message: String): Nothing =
            throw DomainPackDecodeException(if (key == null) path else "$path.$key", message)

        /** Objek mentah dan path-nya, untuk codec bagian yang membaca sendiri ([SlotDefinitionCodec]). */
        fun node(): JsonValue.Obj = obj
        fun at(): String = path

        fun <T> build(block: () -> T): T = try { block() } catch (e: DomainPackDecodeException) { throw e } catch (e: IllegalArgumentException) {
            fail(null, e.message ?: "tidak sah")
        } catch (e: IllegalStateException) {
            fail(null, e.message ?: "tidak sah")
        }

        fun optional(key: String): String? = when (val v = obj[key]) {
            null, JsonValue.Null -> null
            is JsonValue.Str -> v.value
            else -> fail(key, "harus string atau null")
        }

        fun string(key: String): String = optional(key) ?: fail(key, "wajib diisi")

        fun int(key: String): Int = obj.int(key) ?: fail(key, "wajib bilangan bulat")

        fun <T> value(key: String, ctor: (String) -> T): T {
            val raw = string(key)
            return try { ctor(raw) } catch (e: IllegalArgumentException) { fail(key, e.message ?: "tidak sah") }
        }

        fun <T> values(key: String, ctor: (String) -> T): List<T> = array(key).mapIndexed { i, v ->
            val raw = (v as? JsonValue.Str)?.value ?: fail("$key[$i]", "harus string")
            try { ctor(raw) } catch (e: IllegalArgumentException) { fail("$key[$i]", e.message ?: "tidak sah") }
        }

        fun color(key: String): Long {
            val raw = string(key)
            if (!Regex("^#[0-9A-Fa-f]{8}$").matches(raw)) fail(key, "warna harus #AARRGGBB, bukan '$raw'")
            return raw.substring(1).toLong(16)
        }

        fun <E : Enum<E>> enum(key: String, entries: List<E>): E {
            val raw = string(key)
            return entries.firstOrNull { it.name == raw } ?: fail(key, "'$raw' bukan salah satu dari ${entries.map { it.name }}")
        }

        fun <E : Enum<E>> enumSet(key: String, entries: List<E>): Set<E> = array(key).mapIndexed { i, v ->
            val raw = (v as? JsonValue.Str)?.value ?: fail("$key[$i]", "harus string")
            entries.firstOrNull { it.name == raw } ?: fail("$key[$i]", "'$raw' bukan salah satu dari ${entries.map { it.name }}")
        }.toSet()

        private fun array(key: String): List<JsonValue> = (obj[key] as? JsonValue.Arr)?.items ?: fail(key, "wajib array")

        /** Array objek yang **boleh tidak ada**; `null` = field belum ada, bukan "array kosong". */
        fun objectsOrNull(key: String): List<Reader>? = when (obj[key]) {
            null, JsonValue.Null -> null
            else -> objects(key)
        }

        /**
         * Peta berkunci enum (`{"WORKPLACE":"klinik"}`). Field yang tidak ada = pack tidak
         * mendeklarasikan istilah apa pun; kunci **tak dikenal ditolak** (Kontrak 4: nilai tak dikenal
         * tidak boleh hilang diam-diam).
         */
        fun enumKeyedStrings(key: String, entries: List<VocabularyKey>): Map<VocabularyKey, String> {
            val node: JsonValue.Obj = when (val v = obj[key]) {
                null, JsonValue.Null -> return emptyMap()
                is JsonValue.Obj -> v
                else -> fail(key, "harus objek")
            }
            val out = mutableMapOf<VocabularyKey, String>()
            for (entry in node.entries) {
                val rawKey: String = entry.key
                val known: VocabularyKey = entries.firstOrNull { known -> known.name == rawKey }
                    ?: fail("$key.$rawKey", "'$rawKey' bukan istilah yang dikenal (${entries.map { it.name }})")
                out[known] = (entry.value as? JsonValue.Str)?.value ?: fail("$key.$rawKey", "harus string")
            }
            return out
        }

        /**
         * Peta berkunci kode port (`{"ProductionOrderDraft":"Draf Pesanan Produksi (PO)"}`). Field
         * boleh tidak ada (pack sebelum kosakata label). Kunci tak dikenal **tidak** diverifikasi
         * di sini — invarian `DomainPack.init` yang menolaknya, supaya encode/decode dan konstruksi
         * langsung punya aturan validasi yang sama.
         */
        fun stringMapOrNull(key: String): Map<String, String> = when (val v = obj[key]) {
            null, JsonValue.Null -> emptyMap()
            is JsonValue.Obj -> v.entries.mapValues { (k, value) ->
                (value as? JsonValue.Str)?.value ?: fail("$key.$k", "harus string")
            }
            else -> fail(key, "harus objek")
        }

        fun objects(key: String): List<Reader> = array(key).mapIndexed { i, v ->
            Reader(v as? JsonValue.Obj ?: fail("$key[$i]", "harus objek"), "$path.$key[$i]")
        }
    }
}
