package com.eventverse.app.infrastructure.api

import com.eventverse.app.domain.pack.GarmentBlueprints

import com.eventverse.app.domain.pipeline.CustomTenantPipeline
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.shared.pipeline.PipelineGraphCodec
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Every tenant-scoped endpoint is authenticated from a signed JWT, so a data client that
 * forgets the `Authorization` header would fail with 401 at runtime while still compiling
 * and passing every parsing test. These cases pin the header onto the wire.
 */
class ApiClientAuthHeaderTest {

    private val tenantSlug = "cv-berkah-makloon"
    private val token = "test.session.token"

    /** Captures outgoing requests and answers with a minimal valid body. */
    private class CapturingEngine(private val responseBody: String) {
        val requests = mutableListOf<HttpRequestData>()

        fun client(): HttpClient = HttpClient(
            MockEngine { request ->
                requests += request
                respond(
                    content = responseBody,
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        )
    }

    private fun samplePipelineJson(): String = PipelineGraphCodec.encodePipeline(
        CustomTenantPipeline.fromPreset(TenantId("ten-demo-cmt"), GarmentBlueprints.CMT_MAKLOON)
    )

    @Test
    fun pipelineClient_shouldSendBearerTokenAndTenantSlug() = runTest {
        val engine = CapturingEngine(samplePipelineJson())
        val client = PipelineApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider(token)
        )

        client.getPipeline(tenantSlug).getOrThrow()

        val request = engine.requests.single()
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
        assertEquals(tenantSlug, request.headers["X-Tenant-Slug"])
    }

    @Test
    fun pipelineClient_shouldAuthenticateEveryWriteEndpointToo() = runTest {
        val engine = CapturingEngine(samplePipelineJson())
        val client = PipelineApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider(token)
        )

        client.resetPipeline(tenantSlug, GarmentBlueprints.CMT_MAKLOON).getOrThrow()
        client.setModuleActivation(tenantSlug, "inventory", false).getOrThrow()
        client.renameModule(tenantSlug, "node-x", "Gudang Kain").getOrThrow()
        client.getModuleCatalog(tenantSlug)

        assertEquals(4, engine.requests.size)
        assertTrue(
            engine.requests.all { it.headers[HttpHeaders.Authorization] == "Bearer $token" },
            "Setiap endpoint harus mengirim token, bukan hanya endpoint baca"
        )
    }

    /**
     * B4c: kode preset menjadi `BlueprintCode` (value class). Di string template ia akan tercetak
     * `BlueprintCode(value=…)` — kompilator tidak menangkapnya, jadi body reset dikunci di sini.
     */
    @Test
    fun resetPipeline_shouldSendRawBlueprintCodeInBody() = runTest {
        val engine = CapturingEngine(samplePipelineJson())
        val client = PipelineApiClient(httpClient = engine.client(), tokenProvider = FixedSessionTokenProvider(token))

        client.resetPipeline(tenantSlug, GarmentBlueprints.CMT_MAKLOON).getOrThrow()

        val body = (engine.requests.single().body as io.ktor.http.content.TextContent).text
        assertEquals("{\"preset\":\"cmt_makloon\"}", body)
    }

    @Test
    fun rbacClient_shouldSendBearerToken() = runTest {
        val engine = CapturingEngine("[]")
        val client = RbacApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider(token)
        )

        client.getRoles(tenantSlug).getOrThrow()

        val request = engine.requests.single()
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
        assertEquals(tenantSlug, request.headers["X-Tenant-Slug"])
    }

    @Test
    fun orgChartClient_shouldSendBearerToken() = runTest {
        val engine = CapturingEngine("[]")
        val client = OrgChartApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider(token)
        )

        client.getDepartments(tenantSlug).getOrThrow()

        val request = engine.requests.single()
        assertEquals("Bearer $token", request.headers[HttpHeaders.Authorization])
    }

    @Test
    fun withoutASession_shouldOmitAuthorizationRatherThanSendGarbage() = runTest {
        // A logged-out client must produce a clean 401 from the server, not a malformed
        // "Bearer null" that would be harder to diagnose.
        val engine = CapturingEngine(samplePipelineJson())
        val client = PipelineApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider(null)
        )

        client.getPipeline(tenantSlug)

        assertNull(engine.requests.single().headers[HttpHeaders.Authorization])
    }

    @Test
    fun blankStoredToken_shouldBeTreatedAsNoSession() = runTest {
        val engine = CapturingEngine(samplePipelineJson())
        val client = PipelineApiClient(
            httpClient = engine.client(),
            tokenProvider = FixedSessionTokenProvider("   ")
        )

        client.getPipeline(tenantSlug)

        assertNull(engine.requests.single().headers[HttpHeaders.Authorization])
    }
}
