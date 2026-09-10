package com.eventverse.app.shared.json

/**
 * Minimal, dependency-free JSON document model shared by every Kotlin Multiplatform target
 * (Android, iOS, JVM/Ktor server, JS, WasmJS).
 *
 * Exists because the project previously extracted JSON fields with regular expressions and
 * `substringAfter`, which silently broke whenever a producer changed key ordering, added
 * whitespace, or nested an object. PostgreSQL `JSONB` in particular normalises and reorders
 * object keys on read, so positional parsing is never safe against it.
 *
 * Values are read strictly by key and are order-independent.
 */
sealed interface JsonValue {

    data object Null : JsonValue

    data class Bool(val value: Boolean) : JsonValue

    /** Numbers keep their original literal so re-encoding never loses precision. */
    data class Num(val raw: String) : JsonValue {
        val asDouble: Double? get() = raw.toDoubleOrNull()
        val asInt: Int? get() = raw.toIntOrNull() ?: raw.toDoubleOrNull()?.toInt()
        val asLong: Long? get() = raw.toLongOrNull() ?: raw.toDoubleOrNull()?.toLong()
    }

    data class Str(val value: String) : JsonValue

    data class Arr(val items: List<JsonValue>) : JsonValue

    data class Obj(val entries: Map<String, JsonValue>) : JsonValue {

        operator fun get(key: String): JsonValue? = entries[key]

        fun has(key: String): Boolean = entries.containsKey(key)

        fun string(key: String): String? = (entries[key] as? Str)?.value

        fun boolean(key: String): Boolean? = (entries[key] as? Bool)?.value

        fun int(key: String): Int? = (entries[key] as? Num)?.asInt

        fun long(key: String): Long? = (entries[key] as? Num)?.asLong

        fun double(key: String): Double? = (entries[key] as? Num)?.asDouble

        fun obj(key: String): Obj? = entries[key] as? Obj

        fun array(key: String): List<JsonValue> = (entries[key] as? Arr)?.items ?: emptyList()

        fun objectArray(key: String): List<Obj> = array(key).filterIsInstance<Obj>()

        /** A `["a","b"]` array of strings, e.g. a set of granted module ids. */
        fun stringArray(key: String): List<String> = array(key).filterIsInstance<Str>().map { it.value }

        /** Flat `{"k":"v"}` maps such as tenant-specific formula parameters. */
        fun stringMap(key: String): Map<String, String> =
            obj(key)?.entries
                ?.mapNotNull { (k, v) -> (v as? Str)?.let { k to it.value } }
                ?.toMap()
                ?: emptyMap()

        /**
         * Re-encodes a nested value back to compact JSON text, for payloads this codec
         * deliberately treats as opaque (e.g. a custom module's config schema).
         */
        fun rawJson(key: String): String? = entries[key]?.takeIf { it !is Null }?.encode()
    }

    /** Serialises this value back to compact JSON text. */
    fun encode(): String = JsonWriter.write(this)
}

// ---------------------------------------------------------------------------
// Builders — keep call sites free of manual string concatenation.
// ---------------------------------------------------------------------------

fun jsonOf(value: String?): JsonValue = value?.let { JsonValue.Str(it) } ?: JsonValue.Null

fun jsonOf(value: Boolean): JsonValue = JsonValue.Bool(value)

fun jsonOf(value: Int): JsonValue = JsonValue.Num(value.toString())

fun jsonOf(value: Long): JsonValue = JsonValue.Num(value.toString())

fun jsonOf(value: Double): JsonValue = JsonValue.Num(value.toString())

/** Builds an object preserving insertion order, dropping nothing. */
fun jsonObjectOf(vararg pairs: Pair<String, JsonValue>): JsonValue.Obj =
    JsonValue.Obj(linkedMapOf(*pairs))

fun jsonArrayOf(items: List<JsonValue>): JsonValue.Arr = JsonValue.Arr(items)

fun jsonStringMapOf(values: Map<String, String>): JsonValue.Obj =
    JsonValue.Obj(values.entries.associateTo(LinkedHashMap()) { it.key to JsonValue.Str(it.value) })
