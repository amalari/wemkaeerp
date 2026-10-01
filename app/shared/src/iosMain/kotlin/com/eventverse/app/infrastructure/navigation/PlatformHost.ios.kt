package com.eventverse.app.infrastructure.navigation

actual object PlatformHost {
    actual fun currentHost(): String? = null
    actual fun queryParameter(name: String): String? = null
    actual fun openUrl(url: String) = Unit
}
