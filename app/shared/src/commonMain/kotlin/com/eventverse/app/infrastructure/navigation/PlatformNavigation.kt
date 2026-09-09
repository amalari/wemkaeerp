package com.eventverse.app.infrastructure.navigation

/**
 * Multiplatform contract for browser URL synchronization and client-side routing.
 *
 * In web targets (WasmJS, JS), this bridges to window.location, window.history,
 * and popstate/hashchange events.
 * In non-web targets (Android, iOS, Desktop JVM), this maintains an in-memory route
 * state for seamless cross-platform execution.
 */
expect object PlatformNavigation {
    /**
     * Returns the current normalized pathname or hash path (e.g. "/org-chart", "/rbac").
     */
    fun getCurrentPath(): String

    /**
     * Pushes a new entry to the browser history and updates the address bar URL.
     */
    fun pushPath(path: String)

    /**
     * Replaces the current browser history entry without creating a new back-stack step.
     */
    fun replacePath(path: String)

    /**
     * Registers a listener triggered whenever the browser URL changes via Back/Forward buttons.
     */
    fun listenToPathChanges(onPathChanged: (String) -> Unit)
}
