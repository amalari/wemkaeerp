package com.eventverse.app.presentation.builder

import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Draf Builder: "tanpa draf" (pack non-garment) harus berbeda dari "memuat" dan dari galat. */
class BuilderDraftStateTest {

    private val tokens = object : SessionTokenProvider { override fun currentToken() = "tok" }

    private fun clientFor(status: HttpStatusCode, body: String) = BuilderApiClient(
        HttpClient(MockEngine { respond(body, status, headersOf(HttpHeaders.ContentType, "application/json")) }),
        "http://x",
        tokens
    )

    private val draftJson = """{"id":"d","status":"DRAFT","schemaVersion":1,"packCode":"p","packDisplayName":"P","blueprintCode":"b","blueprintDescription":"","moduleCount":1,"activeModuleCount":1,"screenCount":0,
        "modules":[{"id":"a","displayName":"A","section":"S","active":true}],"sections":[],"activeModuleCodes":["a"],"screens":[],"portLabels":{},"slotLabels":{}}"""

    @Test
    fun `respons null dari server menghasilkan Empty, bukan Loading`() = runTest {
        val state = BuilderDraftState.load(clientFor(HttpStatusCode.OK, "null"))
        assertEquals(BuilderDraftState.Empty, state)
        assertNull(state.draftOrNull)
    }

    @Test
    fun `respons draf menghasilkan Loaded`() = runTest {
        val state = BuilderDraftState.load(clientFor(HttpStatusCode.OK, draftJson))
        assertIs<BuilderDraftState.Loaded>(state)
        assertEquals("P", state.draft.packDisplayName)
    }

    @Test
    fun `galat HTTP menghasilkan Failed, bukan Empty`() = runTest {
        val state = BuilderDraftState.load(clientFor(HttpStatusCode.InternalServerError, "boom"))
        assertIs<BuilderDraftState.Failed>(state)
        assertEquals("boom", state.message)
    }

    @Test
    fun `isi bukan objek menghasilkan Failed`() {
        assertIs<BuilderDraftState.Failed>(BuilderDraftState.from(Result.success(JsonParser.parse("[1]"))))
    }

    @Test
    fun `galat jaringan menghasilkan Failed`() {
        assertIs<BuilderDraftState.Failed>(BuilderDraftState.from(Result.failure(IllegalStateException("offline"))))
    }

    @Test
    fun `transisi Loading ke Empty lalu ke Loaded dan ketiganya tak sama`() = runTest {
        val states = listOf(
            BuilderDraftState.Loading,
            BuilderDraftState.load(clientFor(HttpStatusCode.OK, "null")),
            BuilderDraftState.load(clientFor(HttpStatusCode.OK, draftJson))
        )
        assertEquals(3, states.map { it::class }.toSet().size)
        assertTrue(states[0] == BuilderDraftState.Loading && states[1] == BuilderDraftState.Empty)
    }
}
