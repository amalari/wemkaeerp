package com.eventverse.app.routes

import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respondText

/**
 * Bentuk respons error bersamanya untuk rute yang dimigrasi ke kosakata **kode stabil**
 * (PLAN-i18n Fase 1, B3).
 *
 * Sebelumnya rute menjawab teks bebas berbahasa Indonesia; klien tak punya cara andal
 * menerjemahkannya. Format baru:
 *
 * ```json
 * { "code": "FORBIDDEN", "message": "pesan manusia (boleh berubah)" }
 * ```
 *
 * `code` adalah kontrak mesin ([com.eventverse.app.domain.i18n.ServerErrorCode]) dan
 * **tidak boleh** berubah tanpa migrasi klien; `message` hanya petunjuk untuk log/fallback.
 *
 * Ini melengkapi `ServerErrorCodes` (Track 0) dan menjadi titik yang dibaca Track C4.
 */
internal suspend fun ApplicationCall.respondError(
    status: HttpStatusCode,
    code: String,
    message: String
) {
    respondText(
        text = jsonObjectOf(
            "code" to jsonOf(code),
            "message" to jsonOf(message)
        ).encode(),
        contentType = ContentType.Application.Json,
        status = status
    )
}
