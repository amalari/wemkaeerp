package com.eventverse.app.presentation.builder

import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.infrastructure.api.BuilderRunEvent
import com.eventverse.app.infrastructure.api.SessionTokenProvider
import com.eventverse.app.presentation.builder.chat.focusedOn
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
        assertEquals("Menunggu jawaban Anda...", runPhaseLabel("waiting"))
        assertEquals("drafting", BuilderRunEvent(1, "status", """{"type":"status","phase":"drafting"}""").field("phase"))
        assertNull(BuilderRunEvent(1, "done", "bukan json").field("phase"))
    }

    @Test
    fun `requestFollowUps mengirim kode modul dan membaca flag created`() = runTest {
        val cap = Capture(HttpStatusCode.OK, """{"created":true}""")
        val api = BuilderApiClient(cap.client(), "http://x", tokens)
        assertEquals(true, api.requestFollowUps("klinik_poli").getOrThrow())
        assertEquals("http://x/api/builder/chat/followups", cap.urls.single())
        assertEquals("klinik_poli", (JsonParser.parse(cap.bodies.single()) as com.eventverse.app.shared.json.JsonValue.Obj).string("module"))
        assertEquals(false, BuilderApiClient(Capture(HttpStatusCode.OK, """{"created":false}""").client(), "http://x", tokens).requestFollowUps("a").getOrThrow())
    }

    @Test
    fun `fokus modul menyisakan modul dan layarnya saja, utas Semua tidak mengubah draf`() {
        val draft = com.eventverse.app.presentation.discovery.DiscoveryDraftUi.fromJson(JsonParser.parseObject(
            """{"id":"d","status":"DRAFT","schemaVersion":1,"packCode":"p","packDisplayName":"P","blueprintCode":"b","blueprintDescription":"","moduleCount":2,"activeModuleCount":2,"screenCount":2,
               "modules":[{"id":"a","displayName":"A","section":"S","active":true},{"id":"b","displayName":"B","section":"S","active":true}],"sections":[],"activeModuleCodes":["a","b"],
               "screens":[{"screenId":"s1","moduleId":"a","title":"T1","widget":"TABLE"},{"screenId":"s2","moduleId":"b","title":"T2","widget":"FORM"}],"portLabels":{},"slotLabels":{}}"""
        ))
        assertEquals(draft, draft.focusedOn(null))
        val f = draft.focusedOn("a")
        assertEquals(listOf("a"), f.modules.map { it.id })
        assertEquals(listOf("a"), f.activeModuleCodes)
        assertEquals(listOf("s1"), f.screens.map { it.screenId })
        assertEquals(2, draft.modules.size, "draf asal tidak berubah")
    }
}
