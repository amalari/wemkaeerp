package com.eventverse.app.routes

import com.eventverse.app.domain.auth.User
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Bentuk JSON sesi terotentikasi yang dipakai seluruh endpoint auth publik.
 *
 * Diangkat jadi satu fungsi karena tiga endpoint (`/demo`, `/google`, `/me`) sebelumnya merakit
 * string yang sama secara terpisah, dan penambahan field identitas tenant harus muncul di
 * ketiganya sekaligus — kalau tidak, client melihat persona hanya di sebagian jalur masuk.
 */
internal fun authSessionJson(user: User, token: String, tenantSlug: String): String =
    buildJsonObject {
        put("token", token)
        putJsonObject("user") {
            put("id", user.id.value)
            put("tenantId", user.tenantId?.value ?: "")
            put("username", user.username.value)
            put("email", user.email.value)
            put("role", user.role.name)
            put("departmentId", user.departmentId)
            put("customRoleId", user.customRoleId)
            putJsonArray("permissions") { user.effectivePermissions.forEach { add(it.name) } }
        }
        put("tenantSlug", tenantSlug)
    }.toString()
