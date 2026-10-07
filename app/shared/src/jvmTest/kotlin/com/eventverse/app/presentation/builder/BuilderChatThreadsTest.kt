package com.eventverse.app.presentation.builder

import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.infrastructure.api.BuilderRunEvent
import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.presentation.builder.chat.parseFollowUp
import com.eventverse.app.presentation.builder.chat.parseMessages
import com.eventverse.app.presentation.builder.chat.runPhaseLabel
import com.eventverse.app.presentation.builder.chat.threadsOf
import com.eventverse.app.shared.json.JsonParser
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Klien chat Builder per utas + SSE (PLAN-builder-interview-chat Fase A), tanpa jaringan. */
class BuilderChatThreadsTest {

    private val tokens = object : SessionTokenProvider { override fun currentToken() = "tok" }

    private class Capture(private val status: HttpStatusCode, private val body: String) {
        val urls = mutableListOf<String>()
        val bodies = mutableListOf<String>()
        fun client() = HttpClient(MockEngine { req ->
            urls += req.url.toString(); bodies += String(req.body.toByteArray())
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        })
    }

    @Test
    fun `chat tanpa modul tidak memakai query dan chat modul menambah parameter module`() = runTest {
        val cap = Capture(HttpStatusCode.OK, """{"messages":[]}""")
        val api = BuilderApiClient(cap.client(), "http://x", tokens)
        api.chat(); api.chat("klinik_poli")
        assertEquals(listOf("http://x/api/builder/chat", "http://x/api/builder/chat?module=klinik_poli"), cap.urls)
    }

    @Test
    fun `startRun mengirim teks dan modul, membaca runId, dan galat server menjadi pesan`() = runTest {
        val ok = Capture(HttpStatusCode.Accepted, """{"runId":"run-1"}""")
        val r = BuilderApiClient(ok.client(), "http://x", tokens).startRun("Halo \"dunia\"", "klinik_poli")
        assertEquals("run-1", r.getOrThrow())
        assertEquals("http://x/api/builder/chat/runs", ok.urls.single())
        val sent = JsonParser.parse(ok.bodies.single()) as com.eventverse.app.shared.json.JsonValue.Obj
        assertEquals("Halo \"dunia\"", sent.string("text"))
        assertEquals("klinik_poli", sent.string("module"))

        val busy = Capture(HttpStatusCode.Conflict, "Masih ada proses yang berjalan")
        val failed = BuilderApiClient(busy.client(), "http://x", tokens).startRun("x")
        assertTrue(failed.exceptionOrNull()?.message.orEmpty().contains("Masih ada proses"))
        val noModule = JsonParser.parse(Capture(HttpStatusCode.Accepted, """{"runId":"r"}""").also {
            BuilderApiClient(it.client(), "http://x", tokens).startRun("a")
        }.bodies.single()) as com.eventverse.app.shared.json.JsonValue.Obj
        assertNull(noModule.string("module"), "utas Semua tidak mengirim kunci module")
    }

    @Test
    fun `riwayat dan follow-up diparse dari balasan server`() {
        val raw = JsonParser.parse(
            """{"messages":[
                 {"id":"1","role":"USER","text":"hai","summary":[],"hasPendingPatch":false,"appliedDraftId":null,"moduleId":null,"kind":"TEXT","questions":[]},
                 {"id":"2","role":"AGENT","text":"tanya","summary":[],"hasPendingPatch":false,"appliedDraftId":null,"moduleId":"klinik_poli","kind":"QUESTION",
                  "questions":[{"id":"c1","question":"Siapa yang mengisi?"},{"id":"c2","question":"Kapan selesai?","answer":"hari ini"}]}],
               "followUp":{"scope":"module","moduleId":"klinik_poli","questions":[{"id":"c1","question":"Siapa yang mengisi?","messageId":"2","moduleId":"klinik_poli"}]}}"""
        )
        val msgs = parseMessages(raw)
        assertEquals(listOf(null, "klinik_poli"), msgs.map { it.moduleId })
        assertEquals(listOf(false, true), msgs[1].questions.map { it.answered })
        assertEquals(listOf("c1"), parseFollowUp(raw).map { it.id })
        assertEquals(emptyList(), parseFollowUp(JsonParser.parse("""{"messages":[]}""")), "server lama tanpa followUp = tidak ada")
    }

    @Test
    fun `tab utas Semua pertama lalu modul, label fase run, dan field peristiwa`() {
        assertEquals(listOf(null, "a", "b"), threadsOf(listOf("a" to "Modul A", "b" to "Modul B")).map { it.moduleId })
        assertEquals("Menyusun draf...", runPhaseLabel("drafting"))
        assertEquals("Memproses...", runPhaseLabel(null))
        assertEquals("drafting", BuilderRunEvent(1, "status", """{"type":"status","phase":"drafting"}""").field("phase"))
        assertNull(BuilderRunEvent(1, "done", "bukan json").field("phase"))
    }
}
