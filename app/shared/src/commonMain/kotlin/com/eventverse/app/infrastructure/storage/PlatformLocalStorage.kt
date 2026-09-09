package com.eventverse.app.infrastructure.storage

expect object PlatformLocalStorage {
    fun setItem(key: String, value: String)
    fun getItem(key: String): String?
    fun removeItem(key: String)
    fun clear()
}
