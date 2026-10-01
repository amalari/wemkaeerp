package com.eventverse.app.infrastructure.navigation

import kotlinx.browser.window
import org.w3c.dom.url.URLSearchParams

actual object PlatformHost {
    actual fun currentHost(): String? =
        runCatching { window.location.host }.getOrNull()?.takeIf { it.isNotBlank() }

    actual fun queryParameter(name: String): String? =
        runCatching { URLSearchParams(window.location.search).get(name) }.getOrNull()?.takeIf { it.isNotBlank() }

    actual fun openUrl(url: String) {
        runCatching { window.location.assign(url) }
    }
}
