package com.eventverse.app.routes

import com.eventverse.app.domain.auth.UserId
import com.eventverse.app.domain.i18n.LanguageCode
import com.eventverse.app.domain.i18n.LanguagePreferenceRepository
import com.eventverse.app.domain.i18n.ServerErrorCode
import com.eventverse.app.domain.pack.GarmentModules
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAssignmentRepository
import com.eventverse.app.domain.rbac.RoleRepository
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.put

/**
 * Preferensi bahasa (PLAN-i18n Fase 1, Track B2) — dua tingkat, dua gerbang.
 *
 * - `PUT /api/me/language` — **pilihan pribadi**. Setiap pemakai yang terautentikasi
 *   boleh mengubah bahasanya sendiri; tak ada wewenang modul yang dibutuhkan karena
 *   yang diubah adalah preferensi dirinya, bukan data pabrik. Fail-closed: tanpa sesi
 *   → 401, tanpa `userId` → 403, bahasa tak dikenal → 400.
 * - `PUT /api/tenant/language` — **bawaan pabrik**. Gerbang `MANAGE` atas
 *   [GarmentModules.DYNAMIC_RBAC] (modul tata kelola pengaturan tenant). Fail-closed:
 *   keputusan RBAC yang tak bisa dihitung hanya dilewatkan oleh peran platform
 *   pemegang `MANAGE_TENANT` (pola `mayEditWithoutDecision` di `TenantStageFlowRoutes`).
 *
 * Nilai bahasa divalidasi dengan `LanguageCode.parse` — `"fr"` ditolak 400, tidak
 * jatuh diam-diam ke `id` (Kontrak 4 variabilitas: tolak, jangan fallback senyap).
 */
fun Route.languageRoutes(
    preferences: LanguagePreferenceRepository,
    roleRepository: RoleRepository,
    moduleAssignmentRepository: ModuleAssignmentRepository
) {
    put("/api/me/language") {
        val principal = call.callerPrincipalOrNull
            ?: return@put call.respondError(
                HttpStatusCode.Unauthorized, ServerErrorCode.UNAUTHORIZED, "Sesi diperlukan."
            )
        val userId = principal.userId.takeIf { it.isNotBlank() }
            ?: return@put call.respondError(
                HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN, "Sesi tidak memuat identitas pengguna."
            )

        val language = call.receiveLanguage() ?: return@put
        // tenantId untuk RLS: pengguna tenant menulis lewat koneksi tenant-scoped;
        // superadmin tanpa workspace lewat koneksi platform.
        preferences.setUserLanguage(UserId(userId), language, call.tenantContextOrNull?.tenantId)
        call.respondLanguage(language)
    }

    put("/api/tenant/language") {
        val tenant = call.tenantContextOrNull
            ?: return@put call.respondError(
                HttpStatusCode.NotFound, ServerErrorCode.NO_TENANT_CONTEXT, "Konteks tenant tidak ditemukan."
            )
        val decision = call.moduleDecision(GarmentModules.DYNAMIC_RBAC, tenant, roleRepository, moduleAssignmentRepository)
        // Fail-closed: `moduleDecision` selalu mengembalikan keputusan (NONE tanpa jabatan yang
        // memberi akses), jadi menulis hanya lolos bila MANAGE. Dijawab di sini (bukan lewat
        // `requireModuleAccess`) supaya 403 membawa kode stabil (B3). Owner/superadmin tanpa
        // jabatan tenant memperoleh MANAGE lewat matriks, jadi tak perlu jalan pintas peran.
        if (!decision.config.level.isAtLeast(AccessLevel.MANAGE)) {
            return@put call.respondError(
                HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN,
                "Butuh wewenang Kelola atas pengaturan tenant untuk mengubah bahasa bawaan."
            )
        }

        val language = call.receiveLanguage() ?: return@put
        preferences.setTenantLanguage(tenant.tenantId, language)
        call.respondLanguage(language)
    }
}

private suspend fun ApplicationCall.receiveLanguage(): LanguageCode? {
    val body = runCatching { JsonParser.parse(receiveText()) as? JsonValue.Obj }.getOrNull()
        ?: return failLanguage(HttpStatusCode.BadRequest, ServerErrorCode.INVALID_BODY, "Body JSON tidak terbaca.")

    val raw = body.string("language")
    if (raw.isNullOrBlank()) {
        return failLanguage(HttpStatusCode.BadRequest, ServerErrorCode.MISSING_LANGUAGE, "Field 'language' wajib diisi.")
    }
    return LanguageCode.parse(raw)
        ?: failLanguage(
            HttpStatusCode.BadRequest, ServerErrorCode.UNKNOWN_LANGUAGE,
            "Bahasa '$raw' tidak didukung. Didukung: id, en."
        )
}

private suspend fun ApplicationCall.failLanguage(
    status: HttpStatusCode,
    code: String,
    message: String
): LanguageCode? {
    respondError(status, code, message)
    return null
}

private suspend fun ApplicationCall.respondLanguage(language: LanguageCode) {
    respondText(
        text = jsonObjectOf("language" to jsonOf(language.code)).encode(),
        contentType = io.ktor.http.ContentType.Application.Json,
        status = HttpStatusCode.OK
    )
}
