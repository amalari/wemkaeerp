package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.usecases.PriceDiscoveryDraftUseCase
import com.eventverse.app.domain.moduledev.Percentage
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContext
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route

/**
 * `GET /api/builder/draft/price` — estimasi harga **draf kerja tenant** untuk panel "Paket & Harga"
 * di `/builder/prototype`. Endpoint `/api/discovery/drafts/{id}/price` tidak cocok untuk jalur ini:
 * ia hanya untuk pemilik draf, sedangkan draf kerja tenant diakses lewat gerbang builder.
 *
 * Gerbang builder yang sama dengan `GET /api/builder/draft` (fail-closed: identitas tak bisa dihitung = 403).
 * Hanya baca — tidak menulis draf (draf dibuat `GET /api/builder/draft`) maupun catatan estimasi. `modules=a,b` = harga hanya modul terpilih;
 * id di luar draf ditolak 400 oleh use case.
 */
fun Route.builderPriceRoutes(drafts: DiscoveryDraftRepository, priceDraft: PriceDiscoveryDraftUseCase) {
    route("/api/builder/draft/price") {
        get {
            call.gate() ?: return@get
            if (call.callerPrincipalOrNull == null) {
                call.respond(HttpStatusCode.Forbidden, "Identitas pemanggil tidak bisa dihitung.")
                return@get
            }
            // Hanya membaca: draf dibuat oleh `GET /api/builder/draft` (bootstrap); estimasi tidak pernah menulis.
            val stored = drafts.findByTenant(call.tenantContext.tenantId)
                ?: return@get call.respond(HttpStatusCode.NotFound, "Belum ada draf kerja.")
            val margin = call.request.queryParameters["marginPercent"]?.toDoubleOrNull() ?: 35.0
            val only = call.request.queryParameters["modules"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
            priceDraft(stored.draft, Percentage(margin), onlyModuleIds = only).fold(
                onSuccess = { call.respondText(priceJson(it).encode(), ContentType.Application.Json) },
                onFailure = { call.respond(HttpStatusCode.BadRequest, it.message ?: "Gagal menghitung estimasi") }
            )
        }
    }
}
