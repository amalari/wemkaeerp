package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.prototype.PortError
import com.eventverse.app.domain.prototype.PortException
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * C1: klien port API. Yang dikunci: bentuk permintaan (path, verb, header, body), parse respons, **setiap status → PortError
 * yang benar**, dan bahwa kegagalan jaringan/respons rusak tidak pernah bocor sebagai pengecualian mentah.
 * Memakai `MockEngine`; tidak ada jaringan.
 */
class ApiBlockDataPortTest {
    private val base = "/api/tenant/modules/layanan_change_request/change_requests"
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private class Wire(val requests: MutableList<HttpRequestData> = mutableListOf())

    private fun port(wire: Wire = Wire(), handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(HttpRequestData) -> io.ktor.client.request.HttpResponseData) =
        ApiBlockDataPort(
            basePath = base,
            httpClient = HttpClient(MockEngine { req -> wire.requests += req; handler(req) }),
            tokenProvider = FixedSessionTokenProvider("tok-1"),
            tenantSlug = { "layanan-demo" }
        )

    private fun errorOf(r: Result<*>): PortError = assertIs<PortException>(r.exceptionOrNull()).error

    // ---- sukses ---------------------------------------------------------------------------------

    @Test
    fun load_parsesRows_andSendsAuthAndTenantHeaders() = runTest {
        val wire = Wire()
        val rows = port(wire) { respond("""[{"id":"cr-1","values":{"judul":"A","status":"Baru"}},{"id":"cr-2","values":{"judul":"B"}}]""", HttpStatusCode.OK, json) }
            .load().getOrThrow()

        assertEquals(listOf("cr-1", "cr-2"), rows.map { it.id })
        assertEquals("Baru", rows.first()["status"])
        val req = wire.requests.single()
        assertEquals(HttpMethod.Get, req.method)
        assertEquals(base, req.url.encodedPath)
        assertEquals("Bearer tok-1", req.headers[HttpHeaders.Authorization])
        assertEquals("layanan-demo", req.headers["X-Tenant-Slug"])
    }

    @Test
    fun create_postsValuesWrapper_andReturnsTheServerAssignedId() = runTest {
        val wire = Wire()
        val row = port(wire) { respond("""{"id":"change_request-9f2","values":{"judul":"Baru"}}""", HttpStatusCode.Created, json) }
            .create(mapOf("judul" to "Baru", "status" to "Baru")).getOrThrow()

        assertEquals("change_request-9f2", row.id, "id dibuat server, bukan klien")
        val req = wire.requests.single()
        assertEquals(HttpMethod.Post, req.method)
        val body = JsonParser.parseObject((req.body as TextContent).text)
        assertEquals("Baru", body.obj("values")!!.string("judul"))
        assertEquals(setOf("values"), body.entries.keys, "tubuh hanya membawa pembungkus values")
    }

    @Test
    fun update_putsOnlyTheChanges_toTheRowPath() = runTest {
        val wire = Wire()
        port(wire) { respond("""{"id":"cr-1","values":{"status":"Ditinjau"}}""", HttpStatusCode.OK, json) }
            .update("cr-1", mapOf("status" to "Ditinjau")).getOrThrow()

        val req = wire.requests.single()
        assertEquals(HttpMethod.Put, req.method)
        assertEquals("$base/cr-1", req.url.encodedPath)
        assertEquals(mapOf("status" to "Ditinjau"), JsonParser.parseObject((req.body as TextContent).text).stringMap("values"), "hanya perubahan yang dikirim")
    }

    @Test
    fun delete_succeedsOnAnyOkBody_andUsesDeleteVerb() = runTest {
        val wire = Wire()
        port(wire) { respond("""{"deleted":true}""", HttpStatusCode.OK, json) }.delete("cr-1").getOrThrow()
        assertEquals(HttpMethod.Delete, wire.requests.single().method)
        assertEquals("$base/cr-1", wire.requests.single().url.encodedPath)
    }

    // ---- setiap status → PortError --------------------------------------------------------------

    @Test
    fun statuses_mapToTheRightPortError() = runTest {
        fun failing(status: HttpStatusCode, body: String = "") = port { respondError(status, body) }
        assertEquals(PortError.Validation("'Judul' wajib diisi"), errorOf(failing(HttpStatusCode.BadRequest, "'Judul' wajib diisi").create(emptyMap())))
        assertIs<PortError.Validation>(errorOf(failing(HttpStatusCode.Conflict, "bentrok").update("x", emptyMap())))
        assertIs<PortError.Validation>(errorOf(failing(HttpStatusCode.UnprocessableEntity).create(emptyMap())))
        assertIs<PortError.Forbidden>(errorOf(failing(HttpStatusCode.Forbidden, "Butuh wewenang Input & Kerja").delete("x")))
        assertIs<PortError.Forbidden>(errorOf(failing(HttpStatusCode.Unauthorized).load()))
        assertIs<PortError.Forbidden>(errorOf(failing(HttpStatusCode.PaymentRequired, "Masa trial habis").load()), "trial habis = akses tertutup")
        assertIs<PortError.NotFound>(errorOf(failing(HttpStatusCode.NotFound, "Data tidak ditemukan.").update("x", emptyMap())))
        assertIs<PortError.Unavailable>(errorOf(failing(HttpStatusCode.InternalServerError, "stacktrace rahasia").load()))
        assertIs<PortError.Unavailable>(errorOf(failing(HttpStatusCode.BadGateway).load()))
        assertIs<PortError.Unavailable>(errorOf(failing(HttpStatusCode.TooManyRequests).load()))
    }

    @Test
    fun serverMessage_isShownForClientErrors_butNeverForServerErrors() = runTest {
        assertEquals("'Judul' wajib diisi", errorOf(port { respondError(HttpStatusCode.BadRequest, "'Judul' wajib diisi") }.create(emptyMap())).userMessage)
        val unavailable = errorOf(port { respondError(HttpStatusCode.InternalServerError, "NullPointerException at com.x") }.load())
        assertTrue("NullPointerException" !in unavailable.userMessage, "jejak teknis server tidak boleh sampai ke pengguna")
        assertTrue(unavailable.userMessage.startsWith("Gagal terhubung ke server"))
    }

    @Test
    fun overlongOrBlankErrorBodies_fallBackToAReadableMessage() = runTest {
        assertEquals("Isian ditolak server.", (errorOf(port { respondError(HttpStatusCode.BadRequest, "") }.create(emptyMap())) as PortError.Validation).message)
        val huge = "x".repeat(2000)
        assertEquals("Isian ditolak server.", (errorOf(port { respondError(HttpStatusCode.BadRequest, huge) }.create(emptyMap())) as PortError.Validation).message)
    }

    // ---- kegagalan non-HTTP ---------------------------------------------------------------------

    @Test
    fun networkFailure_becomesUnavailable_notARawException() = runTest {
        val r = port { throw java.io.IOException("connection refused") }.load()
        assertTrue(r.isFailure)
        assertIs<PortError.Unavailable>(errorOf(r))
    }

    @Test
    fun unreadableSuccessBodies_becomeUnavailable() = runTest {
        assertIs<PortError.Unavailable>(errorOf(port { respond("<html>bukan json</html>", HttpStatusCode.OK, json) }.load()))
        assertIs<PortError.Unavailable>(errorOf(port { respond("""{"bukan":"array"}""", HttpStatusCode.OK, json) }.load()))
        assertIs<PortError.Unavailable>(errorOf(port { respond("""[{"values":{"a":"b"}}]""", HttpStatusCode.OK, json) }.load()), "baris tanpa id ditolak")
        assertIs<PortError.Unavailable>(errorOf(port { respond("""{"values":{}}""", HttpStatusCode.Created, json) }.create(emptyMap())))
    }

    @Test
    fun cancellation_isNotSwallowed() = runTest {
        assertFailsWith<CancellationException> { port { throw CancellationException("dibatalkan") }.load() }
    }

    @Test
    fun blankBasePath_isRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { ApiBlockDataPort(" ", httpClient = HttpClient(MockEngine { respond("") })) }
    }
}
