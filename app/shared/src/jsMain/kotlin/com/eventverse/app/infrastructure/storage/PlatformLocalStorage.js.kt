package com.eventverse.app.infrastructure.storage

import kotlinx.browser.localStorage

actual object PlatformLocalStorage {
    actual fun setItem(key: String, value: String) {
        try {
            localStorage.setItem(key, value)
        } catch (_: Throwable) {}
    }

    actual fun getItem(key: String): String? {
        return try {
            localStorage.getItem(key)
        } catch (_: Throwable) {
            null
        }
    }

    actual fun removeItem(key: String) {
        try {
            localStorage.removeItem(key)
        } catch (_: Throwable) {}
    }

    actual fun clear() {
        try {
            localStorage.clear()
        } catch (_: Throwable) {}
    }
}
