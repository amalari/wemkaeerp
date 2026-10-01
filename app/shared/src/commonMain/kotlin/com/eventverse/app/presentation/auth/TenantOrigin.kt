package com.eventverse.app.presentation.auth

import com.eventverse.app.infrastructure.navigation.PlatformHost

/**
 * Origin subdomain tenant yang mempertahankan skema & port halaman ini — `http://bordir.lvh.me:3001`
 * di dev, `https://bordir.wemakeerp.com` di produksi. `null` bila base domain belum dikenal.
 * Dipakai login platform dan konsol superadmin (discovery-M3/M3b).
 */
internal fun tenantOriginFromHere(slug: String, baseDomain: String?): String? {
    val base = baseDomain ?: return null
    val port = PlatformHost.currentHost()?.substringAfter(":", "")?.takeIf { it.isNotBlank() }?.let { ":$it" }.orEmpty()
    return "${PlatformHost.currentProtocol()}//$slug.$base$port"
}
