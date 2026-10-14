package com.eventverse.app.presentation.builder

import com.eventverse.app.domain.builder.BriefSource
import com.eventverse.app.domain.builder.ChatMessageKind
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.presentation.builder.chat.briefRefreshNotice
import com.eventverse.app.presentation.builder.chat.latestBriefIds
import com.eventverse.app.presentation.builder.chat.parseMessages
import com.eventverse.app.presentation.builder.chat.stageCaption
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pesan BRIEF di klien chat Builder (TRD-BLDR-001 Track C), tanpa jaringan. */
class ChatBriefModelTest {

    private fun brief(id: String, module: String, source: String = "AI", stages: String, extra: String = "") =
        """{"id":"$id","role":"AGENT","text":"Ringkasan $module","kind":"BRIEF","moduleId":"$module",
           "features":["Lampirkan foto"],"stages":$stages,"briefSource":"$source","basisRef":"h1"$extra}"""

    private fun parse(vararg messages: String) =
        parseMessages(JsonParser.parse("""{"messages":[${messages.joinToString(",")}]}"""))

    @Test
    fun `parseMessages BRIEF garment membaca tahap rework dan sumber AI`() {
        val e = parse(
            brief("b1", "sewing", stages = """[{"name":"Intake"},{"name":"Revisi","optional":true,"reworkTo":"Intake"}]""")
        ).single()
        val b = assertNotNull(e.brief)
        assertNull(e.parseError)
        assertEquals(ChatMessageKind.BRIEF, e.kind)
        assertEquals(BriefSource.AI, b.source)
        assertEquals(listOf("Lampirkan foto"), b.features)
        assertEquals("Intake", b.stages[1].reworkTo)
        assertEquals("opsional · rework ke Intake", stageCaption(b.stages[1]))
        assertNull(stageCaption(b.stages[0]))
    }

    @Test
    fun `parseMessages BRIEF non-garment sumber deterministik`() {
        val e = parse(brief("b1", "bordir_digitizing", "DETERMINISTIC", """[{"name":"Digitizing"},{"name":"Hooping"}]""")).single()
        assertEquals(BriefSource.DETERMINISTIC, e.brief?.source)
        assertEquals(listOf("Digitizing", "Hooping"), e.brief?.stages?.map { it.name })
    }

    @Test
    fun `kind tak dikenal ditolak sebagai galat bukan teks biasa`() {
        val e = parse("""{"id":"x","role":"AGENT","text":"halo","kind":"WIDGET"}""").single()
        assertNull(e.brief)
        assertTrue(e.parseError.orEmpty().contains("WIDGET"))
    }

    @Test
    fun `briefSource tak dikenal dan stages rusak ditolak`() {
        val badSource = parse(brief("b1", "m", "ROBOT", "[]")).single()
        assertTrue(badSource.parseError.orEmpty().contains("ROBOT"))
        val badStages = parse(brief("b2", "m", stages = """[{"nama":"x"}]""")).single()
        assertNotNull(badStages.parseError)
        assertNull(badStages.brief)
    }

    @Test
    fun `pesan tanpa kind tetap TEXT tanpa galat`() {
        val e = parse("""{"id":"t","role":"USER","text":"halo"}""").single()
        assertEquals(ChatMessageKind.TEXT, e.kind)
        assertNull(e.parseError)
    }

    @Test
    fun `BRIEF terbaru per utas tampil penuh dan yang lama dilipat`() {
        val entries = parse(
            brief("old", "sewing", stages = "[]"),
            """{"id":"t","role":"USER","text":"ubah"}""",
            brief("new", "sewing", stages = "[]"),
            brief("other", "cutting", stages = "[]")
        )
        assertEquals(setOf("new", "other"), latestBriefIds(entries))
        assertFalse("old" in latestBriefIds(entries))
    }

    @Test
    fun `requestBriefs 404 berarti fitur belum aktif bukan galat`() = runTest {
        val client = BuilderApiClient(
            HttpClient(MockEngine { respond("nf", HttpStatusCode.NotFound) }),
            tokenProvider = object : SessionTokenProvider { override fun currentToken() = "t" }
        )
        val o = client.requestBriefs("sewing").getOrThrow()
        assertFalse(o.featureEnabled)
    }

    @Test
    fun `requestBriefs menghitung status dan menolak status tak dikenal`() = runTest {
        fun client(body: String) = BuilderApiClient(
            HttpClient(MockEngine { respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }),
            tokenProvider = object : SessionTokenProvider { override fun currentToken() = "t" }
        )
        val ok = client("""{"results":[{"moduleId":"a","status":"created"},{"moduleId":"b","status":"fresh"}]}""")
            .requestBriefs(null).getOrThrow()
        assertTrue(ok.featureEnabled)
        assertEquals(1, ok.created)
        assertEquals(1, ok.fresh)
        assertTrue(client("""{"results":[{"moduleId":"a","status":"wat"}]}""").requestBriefs(null).isFailure)
    }

    @Test
    fun `refresh notice should distinguish skipped from fresh`() {
        fun n(c: Int, f: Int, s: Int, on: Boolean = true) =
            briefRefreshNotice(com.eventverse.app.infrastructure.api.BriefRefreshOutcome(on, c, f, s))
        assertEquals("Fitur ringkasan belum aktif di server ini.", n(0, 0, 0, on = false))
        assertEquals("Ringkasan disegarkan (2).", n(2, 1, 1))
        assertEquals("Ringkasan sudah terbaru.", n(0, 1, 3))
        assertEquals("Belum ada bahan untuk ringkasan modul ini. Ceritakan alurnya di utas Semua dulu.", n(0, 0, 1))
        assertEquals("Tidak ada modul yang perlu diringkas.", n(0, 0, 0))
    }
}
