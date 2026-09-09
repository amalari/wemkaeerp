package com.eventverse.app.infrastructure.storage

import java.util.concurrent.ConcurrentHashMap

actual object PlatformLocalStorage {
    private val memoryStore = ConcurrentHashMap<String, String>()

    actual fun setItem(key: String, value: String) {
        memoryStore[key] = value
    }

    actual fun getItem(key: String): String? {
        return memoryStore[key]
    }

    actual fun removeItem(key: String) {
        memoryStore.remove(key)
    }

    actual fun clear() {
        memoryStore.clear()
    }
}
