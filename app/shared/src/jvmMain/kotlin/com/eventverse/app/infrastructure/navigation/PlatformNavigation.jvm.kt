package com.eventverse.app.infrastructure.navigation

import java.util.concurrent.CopyOnWriteArrayList

actual object PlatformNavigation {
    @Volatile
    private var currentPath: String = "/"
    private val listeners = CopyOnWriteArrayList<(String) -> Unit>()

    actual fun getCurrentPath(): String = currentPath

    actual fun pushPath(path: String) {
        currentPath = path
        listeners.forEach { it(path) }
    }

    actual fun replacePath(path: String) {
        currentPath = path
        listeners.forEach { it(path) }
    }

    actual fun listenToPathChanges(onPathChanged: (String) -> Unit) {
        listeners.add(onPathChanged)
    }
}
