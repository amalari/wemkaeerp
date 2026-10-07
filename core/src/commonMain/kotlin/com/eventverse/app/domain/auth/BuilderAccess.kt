package com.eventverse.app.domain.auth

/**
 * Aturan tunggal "boleh membuka Builder": superadmin platform, atau peran pembawa `MANAGE_BUILDER`. Dipakai server
 * (gerbang fail-closed) **dan** klien (memutuskan pengalihan dari Studio Discovery) supaya keduanya tak pernah
 * berselisih. `null` (peran tak diketahui) = ditolak.
 */
fun Role?.canOpenBuilder(): Boolean =
    this != null && (this == Role.PLATFORM_SUPERADMIN || defaultPermissions.contains(Permission.MANAGE_BUILDER))
