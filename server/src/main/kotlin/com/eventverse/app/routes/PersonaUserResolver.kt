package com.eventverse.app.routes

import com.eventverse.app.domain.auth.EmailAddress
import com.eventverse.app.domain.auth.Role
import com.eventverse.app.domain.auth.User
import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.auth.UserRepository
import com.eventverse.app.domain.auth.Username
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.domain.tenant.TenantId
import java.security.MessageDigest

/**
 * Id user persona baru. `users.id` adalah PK global, sedangkan slug persona hanya unik per tenant,
 * jadi id wajib memuat tenant: `usr-persona-<slug>-<hash8(tenantId)>`. Slug dipotong agar total
 * <= 64 karakter dan hash (yang membedakan tenant) tidak pernah ikut terpotong. Persona lama
 * tidak terdampak karena pemanggil memakai `existing.id` bila akunnya sudah ada.
 */
internal fun personaUserId(tenantId: TenantId, slug: String): String {
    val hash = MessageDigest.getInstance("SHA-256").digest(tenantId.value.toByteArray())
        .take(4).joinToString("") { "%02x".format(it) }
    return "usr-persona-${slug.take(43)}-$hash"
}

/**
 * Username persona, dijamin valid untuk [Username] (3..30 karakter). Nama pendek dipakai apa adanya
 * (`persona_<slug>`, identik dengan perilaku lama). Nama panjang dipotong ke 23 karakter + `_` +
 * hash 6 heks dari slug penuh, sehingga dua nama panjang berawalan sama tetap berbeda. Keunikan
 * database hanya per tenant (`uq_tenant_username`), jadi hash tidak perlu memuat tenant.
 */
internal fun personaUsername(slug: String): String {
    val base = "persona_${slug.replace('-', '_')}"
    if (base.length <= 30) return base
    val hash = MessageDigest.getInstance("SHA-256").digest(slug.toByteArray())
        .take(3).joinToString("") { "%02x".format(it) }
    return "${base.take(23)}_$hash"
}

/**
 * Menemukan atau membuat akun untuk sebuah persona pengujian.
 *
 * Tiga hal yang membuat fungsi ini tidak sesederhana "insert user":
 *
 *  1. **Idempoten.** `users.email` UNIQUE dan `uq_tenant_username` UNIQUE. Login persona yang sama
 *     dua kali harus menemukan baris yang sama, bukan menabrak constraint. Karena itu email dan id
 *     diturunkan secara deterministik dari nama persona, bukan diacak.
 *  2. **Dua sumbu identitas.** `Role` platform menentukan izin tingkat sistem; `custom_role_id`
 *     menentukan isi layar. Jabatan tenant dipetakan ke `Role` yang paling mendekati agar izin
 *     sistem tidak melebar, sementara id jabatan aslinya disimpan apa adanya.
 *  3. **Jabatan harus nyata.** Id jabatan yang tidak ada di tenant ini ditolak, bukan diabaikan
 *     diam-diam — persona dengan jabatan hantu akan tampak "tidak punya akses apa pun" dan
 *     dilaporkan sebagai kerusakan.
 */
internal suspend fun resolvePersonaUser(
    userRepo: UserRepository,
    roleRepo: RoleRepository,
    tenantId: TenantId,
    personaName: String,
    tenantSlug: String,
    requestedRoleId: String?,
    departmentId: String?
): Result<User> = runCatching {
    val trimmedName = personaName.trim()
    require(trimmedName.isNotBlank()) { "Nama persona tidak boleh kosong" }

    val slug = trimmedName.lowercase()
        .replace("[^a-z0-9]+".toRegex(), "-")
        .trim('-')
        .ifBlank { "anon" }

    val customRole = requestedRoleId
        ?.takeIf { it.isNotBlank() }
        ?.let { roleId ->
            roleRepo.findById(tenantId, com.eventverse.app.domain.rbac.RoleId(roleId))
                ?: error("Jabatan '$roleId' tidak ditemukan pada tenant ini")
        }

    val email = EmailAddress("persona-$tenantSlug-$slug@testing.local")
    val existing = userRepo.findByEmail(email)

    val persona = User(
        id = existing?.id ?: UserId(personaUserId(tenantId, slug)),
        tenantId = tenantId,
        username = existing?.username ?: Username(personaUsername(slug)),
        email = email,
        role = platformRoleFor(customRole?.name, requestedRoleId),
        isActive = true,
        departmentId = departmentId?.takeIf { it.isNotBlank() } ?: customRole?.departmentId,
        customRoleId = customRole?.id?.value
    )

    userRepo.save(persona).getOrThrow()
}

/**
 * Memetakan jabatan rakitan tenant ke [Role] platform yang paling mendekati.
 *
 * Default-nya sengaja [Role.OPERATOR] — wewenang tersempit. Jabatan yang tidak dikenali sebaiknya
 * membuat persona melihat terlalu sedikit, bukan terlalu banyak: yang pertama dilaporkan penguji,
 * yang kedua lolos tanpa disadari.
 */
private fun platformRoleFor(roleName: String?, roleId: String?): Role {
    val haystack = "${roleName.orEmpty()} ${roleId.orEmpty()}".lowercase()
    return when {
        haystack.contains("owner") || haystack.contains("direktur") -> Role.TENANT_ADMIN
        haystack.contains("sales") || haystack.contains("penjualan") -> Role.SALES
        haystack.contains("ppic") || haystack.contains("produksi") -> Role.PPIC_SUPERVISOR
        haystack.contains("qc") || haystack.contains("quality") -> Role.QC_INSPECTOR
        haystack.contains("gudang") || haystack.contains("warehouse") || haystack.contains("logistik") -> Role.WAREHOUSE
        else -> Role.OPERATOR
    }
}
