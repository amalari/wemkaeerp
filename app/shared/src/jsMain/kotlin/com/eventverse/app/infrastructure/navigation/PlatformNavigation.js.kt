package com.eventverse.app.infrastructure.navigation

import kotlinx.browser.window

actual object PlatformNavigation {
    actual fun getCurrentPath(): String {
        return try {
            val hash = window.location.hash
            if (hash.startsWith("#/")) {
                hash.removePrefix("#")
            } else if (hash.startsWith("#") && hash.length > 1) {
                "/" + hash.removePrefix("#").removePrefix("/")
            } else {
                val path = window.location.pathname
                if (path.isEmpty()) "/" else path
            }
        } catch (_: Throwable) {
            "/"
        }
    }

    actual fun pushPath(path: String) {
        try {
            if (window.location.pathname != path) {
                window.history.pushState(null, "", path)
            }
        } catch (_: Throwable) {}
    }

    actual fun replacePath(path: String) {
        try {
            window.history.replaceState(null, "", path)
        } catch (_: Throwable) {}
    }

    actual fun listenToPathChanges(onPathChanged: (String) -> Unit) {
        try {
            val notify = {
                onPathChanged(getCurrentPath())
            }
            window.addEventListener("popstate", { notify() })
            window.addEventListener("hashchange", { notify() })
        } catch (_: Throwable) {}
    }
}
