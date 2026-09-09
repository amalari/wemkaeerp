package com.eventverse.app.infrastructure.storage

import platform.Foundation.NSUserDefaults

actual object PlatformLocalStorage {
    actual fun setItem(key: String, value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = key)
    }

    actual fun getItem(key: String): String? {
        return NSUserDefaults.standardUserDefaults.stringForKey(key)
    }

    actual fun removeItem(key: String) {
        NSUserDefaults.standardUserDefaults.removeObjectForKey(key)
    }

    actual fun clear() {
        // Standard user defaults clear
    }
}
