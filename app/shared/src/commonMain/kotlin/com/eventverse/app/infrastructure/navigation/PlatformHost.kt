package com.eventverse.app.infrastructure.navigation

/**
 * Host browser dan perpindahan antar-origin (discovery-M3-login-split).
 *
 * Di web, ini jembatan ke `window.location`. Di target non-web tidak ada host: [currentHost]
 * mengembalikan `null`, sehingga `HostSurface.parse` jatuh ke `Local` dan login berperilaku
 * seperti sebelum pemisahan (kolom slug tetap ada).
 */
expect object PlatformHost {
    /** `window.location.host` (dengan port), atau `null` di luar browser. */
    fun currentHost(): String?

    /** Nilai query param pada URL saat ini, atau `null`. */
    fun queryParameter(name: String): String?

    /** Pindah ke origin lain (full page load). Tidak berbuat apa-apa di luar browser. */
    fun openUrl(url: String)
}
