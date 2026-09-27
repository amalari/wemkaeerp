package com.eventverse.app.routes

import com.eventverse.app.domain.traceability.TraceAllocationPlan
import com.eventverse.app.domain.traceability.TraceContainerRepository
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.domain.traceability.TraceWorkOrderProvider
import com.eventverse.app.domain.traceability.print.TraceLabelSheetLayout
import com.eventverse.app.domain.traceability.usecases.PlanTraceAllocationUseCase
import com.eventverse.app.infrastructure.auth.PrintTicketService
import com.eventverse.app.infrastructure.pdf.KnitWorksheetPdfRenderer
import com.eventverse.app.infrastructure.pdf.SpkCardPdfRenderer
import com.eventverse.app.infrastructure.pdf.TraceLabelSheetPdfRenderer
import com.eventverse.app.infrastructure.traceability.KnitWorksheetBuilder
import com.eventverse.app.infrastructure.traceability.SpkCardBuilder
import com.eventverse.app.plugins.callerPrincipalOrNull
import com.eventverse.app.shared.traceability.TraceAllocationCodec
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * Rute yang menghasilkan kertas: rencana pra-cetak, kartu potong, dan lembar kerja rajut.
 *
 * Seluruh PDF dikirim `private, no-store`. Kartu memuat kode yang membuka data produksi, dan PDF yang
 * tersimpan di cache proxy bersama adalah cara paling mudah membocorkannya ke tenant lain.
 */
fun Route.traceabilityPrintRoutes(
    containers: TraceContainerRepository,
    workOrders: TraceWorkOrderProvider,
    worksheets: KnitWorksheetBuilder,
    spkCards: SpkCardBuilder,
    scanHost: String,
    printTickets: PrintTicketService = PrintTicketService()
) {
    val planAllocation = PlanTraceAllocationUseCase(containers, workOrders)
    val labelRenderer = TraceLabelSheetPdfRenderer(scanHost)
    val worksheetRenderer = KnitWorksheetPdfRenderer(scanHost)
    val spkCardRenderer = SpkCardPdfRenderer(scanHost)

    route("/api/tenant/traceability/work-orders/{kind}/{id}") {

        // Tab browser tidak bisa membawa header Bearer; klien menukar sesinya dengan tiket pendek
        // yang hanya membuka PDF work order ini, lalu menempelkannya sebagai `?ticket=`.
        post("/print-ticket") {
            val tenant = call.traceTenant() ?: return@post
            val ref = call.traceRef() ?: return@post
            val subject = call.callerPrincipalOrNull?.userId
                ?: return@post call.respond(HttpStatusCode.Unauthorized, "Sesi diperlukan")
            val scope = "/api/tenant/traceability/work-orders/${ref.kind.name}/${ref.id}"
            val ticket = printTickets.issue(subject, tenant, scope)
            call.response.header(HttpHeaders.CacheControl, "private, no-store")
            call.respondTraceJson("{\"ticket\":\"$ticket\"}")
        }

        get("/allocation") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get

            planAllocation(tenant, ref, call.setsPerBundle(), call.pcsPerSack())
                .onSuccess { call.respondTraceJson(TraceAllocationCodec.encodePlan(it).encode()) }
                .onFailure { call.respondTraceFailure(HttpStatusCode.BadRequest, it) }
        }

        get("/labels.pdf") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get
            val tier = TraceTier.entries
                .firstOrNull { it.name.equals(call.request.queryParameters["tier"], ignoreCase = true) }
                ?: TraceTier.BUNDLE
            if (!tier.isContainer) {
                return@get call.respond(HttpStatusCode.BadRequest, "${tier.displayName} dicetak lewat worksheet.pdf")
            }

            planAllocation(tenant, ref, call.setsPerBundle(), call.pcsPerSack())
                .onSuccess { plan ->
                    val sheet = TraceLabelSheetLayout.solvePlan(plan, tier, call.request.queryParameters["size"])
                    val bytes = labelRenderer.render(sheet, plan.snapshot.spkNumber, writingLinesFor(tier, plan))
                    call.respondPdf(bytes, "kartu-${tier.shortLabel.lowercase()}-${plan.snapshot.spkNumber}.pdf")
                }
                .onFailure { call.respondTraceFailure(HttpStatusCode.BadRequest, it) }
        }

        get("/worksheet.pdf") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get

            planAllocation(tenant, ref, call.setsPerBundle(), call.pcsPerSack())
                .onSuccess { plan ->
                    val worksheet = worksheets.build(tenant, plan.snapshot, plan)
                        ?: return@onSuccess call.respond(
                            HttpStatusCode.NotFound,
                            "Lembar kerja rajut baru tersedia untuk SPK sampling."
                        )
                    call.respondPdf(
                        worksheetRenderer.render(worksheet),
                        "lembar-kerja-${worksheet.spkNumber}.pdf"
                    )
                }
                .onFailure { call.respondTraceFailure(HttpStatusCode.BadRequest, it) }
        }

        get("/spk-card.pdf") {
            val tenant = call.traceTenant() ?: return@get
            val ref = call.traceRef() ?: return@get

            planAllocation(tenant, ref, call.setsPerBundle(), call.pcsPerSack())
                .onSuccess { plan ->
                    val card = spkCards.build(tenant, plan.snapshot, plan)
                        ?: return@onSuccess call.respond(
                            HttpStatusCode.NotFound,
                            "Kartu SPK tersedia untuk SPK sampling."
                        )
                    call.respondPdf(
                        spkCardRenderer.render(card),
                        "kartu-spk-${card.content.spkNumber}.pdf"
                    )
                }
                .onFailure { call.respondTraceFailure(HttpStatusCode.BadRequest, it) }
        }
    }
}

/**
 * Label kotak isian yang tercetak di kartu.
 *
 * Ini bagian yang mudah dianggap remeh: kartu tanpa kotak tulis tangan memaksa operator menunggu
 * jaringan pulih sebelum boleh mengikat bundel — dan yang sebenarnya terjadi adalah dia mengikatnya
 * tanpa kartu sama sekali.
 */
private fun writingLinesFor(tier: TraceTier, plan: TraceAllocationPlan): List<String> = when (tier) {
    TraceTier.BUNDLE -> buildList {
        plan.snapshot.panelRequirements.forEach { requirement ->
            val suffix = if (requirement.piecesPerGarment > 1) " (x${requirement.piecesPerGarment})" else ""
            add("${requirement.panel.displayName}$suffix")
        }
        add("Operator")
        add("Shift / Tanggal")
    }
    TraceTier.SACK -> listOf("Jumlah (pcs)", "Berat (kg)", "Bundel induk", "", "Operator", "Tanggal")
    TraceTier.WORKSHEET -> emptyList()
}

private fun ApplicationCall.setsPerBundle(): Int =
    request.queryParameters["setsPerBundle"]?.toIntOrNull()?.takeIf { it > 0 }
        ?: TraceAllocationPlan.DEFAULT_SETS_PER_BUNDLE

private fun ApplicationCall.pcsPerSack(): Int =
    request.queryParameters["pcsPerSack"]?.toIntOrNull()?.takeIf { it > 0 }
        ?: TraceAllocationPlan.DEFAULT_PCS_PER_SACK

private suspend fun ApplicationCall.respondPdf(bytes: ByteArray, fileName: String) {
    response.header(HttpHeaders.CacheControl, "private, no-store")
    response.header(
        HttpHeaders.ContentDisposition,
        ContentDisposition.Inline.withParameter(ContentDisposition.Parameters.FileName, fileName).toString()
    )
    respondBytes(bytes, ContentType.Application.Pdf)
}
