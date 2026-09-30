package com.eventverse.app.routes

import com.eventverse.app.domain.discovery.DiscoveryDraftId
import com.eventverse.app.domain.discovery.DiscoveryDraftRepository
import com.eventverse.app.domain.discovery.StoredDiscoveryDraft
import com.eventverse.app.domain.discovery.print.BlueprintPdfDocument
import com.eventverse.app.domain.discovery.print.BlueprintSheetLayout
import com.eventverse.app.infrastructure.auth.PrintTicketService
import com.eventverse.app.infrastructure.pdf.BlueprintPdfRenderer
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.plugins.tenantContextOrNull
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonOf
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.path
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * Cetakan blueprint draf discovery (plan §5, Fase D): `POST /print-ticket` lalu
 * `GET /blueprint.pdf?ticket=…`.
 *
 * ## Kenapa dua langkah
 *
 * Tab browser tidak bisa membawa header `Authorization`; tautan PDF biasa akan selalu 401 — termasuk
 * untuk superadmin. Jadi klien menukar sesinya dengan tiket pendek (±60 detik) yang cakupan pathnya
 * **hanya draf ini** dan hanya berkas `.pdf`. Mekanismenya sama dengan cetakan jejak produksi
 * (`TraceabilityPrintRoutes`), termasuk `Cache-Control: private, no-store`: PDF blueprint memuat
 * susunan modul & parameter tenant, dan salinan di cache proxy bersama adalah cara termudah
 * membocorkannya ke tenant lain.
 *
 * ## Kenapa gerbangnya diperiksa dua kali
 *
 * `TenantResolutionPlugin` memverifikasi tiket hanya sebatas "tenant-nya ada" (tiket membawa tenant
 * karena plugin membutuhkannya). Kepemilikan draf — gerbang T12 yang sama dengan endpoint JSON —
 * tetap diperiksa di sini lewat `mayAccessPdf`, supaya tiket yang bocor dari riwayat browser tidak
 * membuka draf orang lain.
 *
 * ## Catatan `audit-variability.sh` untuk `POST /print-ticket`
 *
 * Skrip menandai setiap rute `post` dan menanyakan guard modul / fail-closed-nya. Rute ini **bukan**
 * endpoint modul: ia platform (`/api/discovery`, bukan `/api/tenant/...`), digerbang berlapis yang semuanya
 * default — tanpa principal → 401, bukan pemilik/superadmin → 403, tanpa konteks tenant → 409, dan
 * draf tidak ada → 404. Yang diterbitkannya pun bukan data, melainkan tiket berumur 60 detik yang
 * cakupannya hanya satu berkas PDF; tidak ada `requireModuleAccess` yang masuk akal dipasang di sini
 * karena draf prospek memang belum menjadi modul milik tenant mana pun.
 */
fun Route.discoveryBlueprintPdfRoutes(
    repository: DiscoveryDraftRepository,
    printTickets: PrintTicketService = PrintTicketService(),
    renderer: BlueprintPdfRenderer = BlueprintPdfRenderer()
) {
    route("/api/discovery/drafts/{id}") {

        post("/print-ticket") {
            val principal = call.callerPrincipalOrNull ?: return@post unauthorized()
            val tenant = call.tenantContextOrNull
                ?: return@post call.respondText(
                    "Sesi ini belum terikat perusahaan; pilih workspace dulu sebelum mengunduh blueprint.",
                    ContentType.Text.Plain, HttpStatusCode.Conflict
                )
            val stored = call.draftOrNull(repository) ?: return@post notFound("Draf tidak ditemukan")
            if (!mayAccess(principal, stored)) return@post forbidden()

            val ticket = printTickets.issue(
                subject = principal.userId,
                tenantId = tenant.tenantId,
                scopePath = draftPdfScope(stored.id),
                platformSuperadmin = principal.isPlatformSuperadmin
            )
            call.response.header(HttpHeaders.CacheControl, "private, no-store")
            call.respondText(jsonObjectOf("ticket" to jsonOf(ticket)).encode(), ContentType.Application.Json)
        }

        // Ber-watermark, memuat modul aktif **dan** yang di-bypass, parameter, fase, dan layar kustom.
        get("/blueprint.pdf") {
            val stored = call.draftOrNull(repository) ?: return@get notFound("Draf tidak ditemukan")
            if (!mayAccessPdf(call, stored, printTickets)) return@get forbidden()

            val document = BlueprintPdfDocument.of(
                draft = stored.draft,
                generatedAtLabel = generatedAtLabel(),
                watermark = BlueprintPdfDocument.watermarkFor(stored.status)
            )
            call.response.header(HttpHeaders.CacheControl, "private, no-store")
            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Inline
                    .withParameter(ContentDisposition.Parameters.FileName, "blueprint-${stored.id.value}.pdf")
                    .toString()
            )
            call.respondBytes(
                renderer.render(BlueprintSheetLayout.layout(document)),
                ContentType.Application.Pdf
            )
        }
    }
}

/** Cakupan path tiket: `/api/discovery/drafts/{id}` — dipakai penerbit dan pemverifikasi. */
private fun draftPdfScope(id: DiscoveryDraftId): String = "/api/discovery/drafts/${id.value}"

private suspend fun ApplicationCall.draftOrNull(repository: DiscoveryDraftRepository): StoredDiscoveryDraft? =
    repository.findById(DiscoveryDraftId(parameters["id"].orEmpty()))

/**
 * Gerbang unduhan PDF: Bearer seperti endpoint lain, **atau** `?ticket=` yang diterbitkan
 * `print-ticket`.
 *
 * Tiket tidak diperlakukan sebagai sesi — ia hanya membawa subjek + penanda superadmin, sehingga
 * penambahan peran RBAC kelak tidak diam-diam ikut berlaku lewat tautan cetak.
 */
private fun mayAccessPdf(
    call: ApplicationCall,
    stored: StoredDiscoveryDraft,
    printTickets: PrintTicketService
): Boolean {
    call.callerPrincipalOrNull?.let { return mayAccess(it, stored) }
    val ticket = call.request.queryParameters[PrintTicketService.QUERY_PARAM] ?: return false
    val user = printTickets.verifyUser(ticket, call.request.path()) ?: return false
    return user.isPlatformSuperadmin || stored.ownerUserId.value == user.userId
}

/**
 * Waktu cetak berlabel. Offset zona ikut dicetak karena PDF berpindah tangan: "14:05" tanpa zona
 * membuat dua orang di dua pulau memperdebatkan jam berapa dokumen itu dibuat.
 */
private fun generatedAtLabel(): String {
    val now = java.time.ZonedDateTime.now()
    val stamp = now.format(
        java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm", java.util.Locale("id", "ID"))
    )
    return "$stamp ${now.offset.id}"
}
