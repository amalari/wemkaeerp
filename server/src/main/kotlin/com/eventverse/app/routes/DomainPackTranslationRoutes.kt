package com.eventverse.app.routes

import com.eventverse.app.domain.i18n.PackLanguageValidator
import com.eventverse.app.domain.pack.DomainPackCode
import com.eventverse.app.domain.pack.DomainPackRepository
import com.eventverse.app.domain.pack.StoredDomainPack
import com.eventverse.app.domain.pack.missingTranslations
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonArrayOf
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get

/**
 * Laporan kelengkapan terjemahan pack (TRD-I18N-002 F2, "Monitoring"):
 * `GET /api/admin/domain-packs/{code}/translation-report[?version=N]`.
 *
 * Dipasang di bawah `/api/admin` — plugin tenant menolak non-superadmin **sebelum** handler ini jalan (fail-closed,
 * sama dengan baca daftar pack). Tanpa `version` = versi terbaru (termasuk draf, karena penulis pack ingin tahu
 * selisih sebelum mengunci). Kode/versi tak sah atau pack tak ada → 400/404, tidak pernah laporan kosong yang
 * menyesatkan. Daftar jalur datang dari `DomainPack.missingTranslations()`; rute ini tidak menyalin daftar field per tipe.
 */
fun Route.domainPackTranslationRoutes(packRepository: DomainPackRepository) {
    get("/{code}/translation-report") {
        val code = runCatching { DomainPackCode(call.parameters["code"].orEmpty()) }.getOrNull()
            ?: return@get call.respond(HttpStatusCode.BadRequest, "Kode pack tidak sah")
        val version = call.request.queryParameters["version"]?.let {
            it.toIntOrNull() ?: return@get call.respond(HttpStatusCode.BadRequest, "version harus bilangan bulat")
        }
        val stored = (if (version == null) packRepository.findLatest(code) else packRepository.findVersion(code, version))
            ?: return@get call.respond(HttpStatusCode.NotFound, "Pack '${code.value}' tidak ditemukan")
        call.respondText(translationReport(stored).encode(), ContentType.Application.Json)
    }
}

internal fun translationReport(stored: StoredDomainPack): JsonValue.Obj {
    val missing = stored.pack.missingTranslations()
    val counts = PackLanguageValidator.countByLanguage(missing)
    return jsonObjectOf(
        "code" to jsonOf(stored.pack.code.value),
        "version" to jsonOf(stored.version),
        "claimed" to jsonArrayOf(stored.pack.languages.languages.map { it.code }.sorted().map { jsonOf(it) }),
        "missing" to jsonArrayOf(missing.map { jsonObjectOf("path" to jsonOf(it.path), "language" to jsonOf(it.language.code)) }),
        "countByLanguage" to jsonObjectOf(*counts.entries.sortedBy { it.key.code }.map { it.key.code to jsonOf(it.value) }.toTypedArray())
    )
}
