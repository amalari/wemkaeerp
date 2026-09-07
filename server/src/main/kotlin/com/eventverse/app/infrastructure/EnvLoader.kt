package com.eventverse.app.infrastructure

import java.io.File

/**
 * Utility to load environment variables from system env or root .env file.
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
