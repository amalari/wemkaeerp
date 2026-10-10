package com.eventverse.app.routes

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username

private val DEMO_SUPERADMIN_EMAIL = EmailAddress("superadmin@wemade.id")

/**
 * Akun platform superadmin untuk login demo: `PLATFORM_SUPERADMIN` **tanpa** `tenantId`.
 *
 * Dulu akun ini dibuat/dipinjam dengan `tenantId` tenant yang kebetulan diminta pertama kali, sehingga
 * token superadmin membawa `tenant_id` tenant itu selamanya (identitas platform "menempel" ke satu
 * pabrik). Sekarang akun dicari lewat email; bila baris lama masih terikat tenant, ia dilepas
 * (`tenantId = null`) — superadmin memang bukan milik tenant mana pun. Akun dengan email itu yang
 * bukan superadmin ditolak, bukan dipinjam.
 */
internal suspend fun resolveDemoPlatformSuperadmin(userRepo: UserRepository): User {
    val existing = userRepo.findByEmail(DEMO_SUPERADMIN_EMAIL)
    if (existing != null) {
        require(existing.role == Role.PLATFORM_SUPERADMIN) {
            "Akun ${DEMO_SUPERADMIN_EMAIL.value} bukan superadmin platform"
        }
        if (existing.tenantId == null) return existing
        return existing.copy(tenantId = null).also { userRepo.save(it).getOrThrow() }
    }
    return User(
        id = UserId("usr-superadmin-001"),
        tenantId = null,
        username = Username("superadmin_apps"),
        email = DEMO_SUPERADMIN_EMAIL,
        role = Role.PLATFORM_SUPERADMIN,
        isActive = true
    ).also { userRepo.save(it).getOrThrow() }
}
