package com.eventverse.app.domain.discovery.handoff

/**
 * Penamaan aman untuk keluaran generator. Spec berasal dari data (bisa dari LLM), jadi **semua**
 * identifier SQL dinormalisasi ke `[a-z][a-z0-9_]*` dan ditolak bila kosong, terlalu panjang, atau kata
 * cadangan SQL — tidak ada teks spec yang masuk SQL/Kotlin tanpa lewat sini (anti-injeksi). Literal
 * teks di-escape, bukan disambung mentah.
 */
internal object SpecNaming {

    private val reserved = setOf(
        "all", "analyse", "analyze", "and", "any", "array", "as", "asc", "both", "case", "cast", "check", "collate",
        "column", "constraint", "create", "current_date", "current_time", "current_timestamp", "current_user",
        "default", "desc", "distinct", "do", "else", "end", "except", "fetch", "for", "foreign", "from", "grant",
        "group", "having", "in", "initially", "intersect", "into", "leading", "limit", "localtime", "localtimestamp",
        "not", "null", "offset", "on", "only", "or", "order", "placing", "primary", "references", "returning",
        "select", "session_user", "some", "symmetric", "table", "then", "to", "trailing", "union", "unique", "user",
        "using", "variadic", "when", "where", "window", "with"
    )

    /** Kolom bawaan tabel — kunci field spec tidak boleh bertabrakan dengannya. */
    val systemColumns = setOf("id", "tenant_id", "created_at", "updated_at")

    fun ident(raw: String, what: String): String {
        val norm = raw.lowercase().map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }.joinToString("")
            .replace(Regex("_+"), "_").trim('_')
        require(norm.isNotEmpty() && norm.first() in 'a'..'z') { "$what '$raw' tidak bisa dijadikan nama SQL (harus diawali huruf)." }
        require(norm.length <= 60) { "$what '$raw' terlalu panjang untuk nama SQL (maks 60)." }
        require(norm !in reserved) { "$what '$raw' adalah kata cadangan SQL; ganti kuncinya." }
        return norm
    }

    fun pascal(snake: String): String = snake.split('_').filter { it.isNotEmpty() }.joinToString("") { it.replaceFirstChar(Char::uppercase) }
    fun camel(snake: String): String = pascal(snake).replaceFirstChar(Char::lowercase)

    /** Literal teks Kotlin: escape `\`, `"`, `$`, dan baris baru. */
    fun kString(s: String): String = buildString {
        append('"')
        s.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '$' -> append("\\$")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                else -> append(c)
            }
        }
        append('"')
    }

    /** Literal teks SQL: kutip digandakan; karakter NUL ditolak. */
    fun sqlString(s: String): String {
        require('\u0000' !in s) { "Teks mengandung karakter NUL." }
        return "'" + s.replace("'", "''") + "'"
    }
}
