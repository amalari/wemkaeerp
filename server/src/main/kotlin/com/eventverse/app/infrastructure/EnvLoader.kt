package com.eventverse.app.infrastructure

import java.io.File

/**
 * Utility to load environment variables from system env or root .env file.
 *
 * **Ini satu-satunya pintu pembacaan env di server.** Memanggil `System.getenv` langsung berarti
 * nilai yang sudah ditulis di `.env` tidak akan terbaca oleh `./gradlew :server:run` maupun
 * `:server:test` — persis yang membuat `DB_APP_USER` tampak selesai selama berbulan-bulan padahal
 * penegakan RLS-nya belum pernah aktif (plan A0, 2026-09-30).
 *
 * Test yang di-gate oleh env wajib memakai object ini juga; guard yang memakai `System.getenv`
 * akan `return` diam-diam dan assertion-nya tidak pernah dijalankan.
 */
object EnvLoader {
    private val envMap: Map<String, String> by lazy {
        val map = mutableMapOf<String, String>()
        val candidates = listOf(
            File(".env"),
            File("../.env"),
            File("../../.env")
        )
        val envFile = candidates.firstOrNull { it.exists() && it.isFile }
        envFile?.readLines()?.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val idx = trimmed.indexOf('=')
                val key = trimmed.substring(0, idx).trim()
                val value = trimmed.substring(idx + 1).trim()
                map[key] = value
            }
        }
        map
    }

    fun get(key: String, default: String = ""): String {
        return System.getenv(key)?.takeIf { it.isNotBlank() }
            ?: envMap[key]
            ?: default
    }
}
