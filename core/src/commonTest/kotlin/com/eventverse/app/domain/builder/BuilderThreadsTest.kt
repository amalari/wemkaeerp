package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuilderThreadsTest {
    private val conv = BuilderConversationId("c")
    private val tenant = TenantId("ten-uji")

    private fun text(id: String, module: String? = null, role: ChatRole = ChatRole.USER) =
        ChatMessage(ChatMessageId(id), conv, tenant, role, "isi $id", moduleId = module)

    private fun question(id: String, module: String?, vararg qs: Clarification) =
        ChatMessage(ChatMessageId(id), conv, tenant, ChatRole.AGENT, "tanya", moduleId = module, kind = ChatMessageKind.QUESTION, questions = qs.toList())

    private val all = listOf(
        text("m1"), text("m2", module = "klinik_poli"), text("m3", module = "klinik_kasir"),
        question("q1", null, Clarification("c1", "Satu antrean?"), Clarification("c2", "Obat dijual?", "ya")),
        question("q2", "klinik_poli", Clarification("c3", "Siapa mengisi?"))
    )

    @Test
    fun `utas Semua tidak difilter dan utas modul hanya miliknya`() {
        assertEquals(all, all.inThread(null))
        assertEquals(listOf("m2", "q2"), all.inThread("klinik_poli").map { it.id.value })
        assertEquals(listOf("m3"), all.inThread("klinik_kasir").map { it.id.value })
        assertEquals(emptyList(), all.inThread("modul_hantu"))
    }

    @Test
    fun `follow-up menunggu hanya yang belum dijawab, utas Semua mencakup semua utas, utas modul hanya miliknya`() {
        assertEquals(listOf("c1", "c3"), all.pendingFollowUps(null).map { it.question.id })
        assertEquals(listOf("c3"), all.pendingFollowUps("klinik_poli").map { it.question.id })
        assertEquals(emptyList(), all.pendingFollowUps("klinik_kasir"))
    }

    @Test
    fun `pesan QUESTION wajib dari AGENT dan punya pertanyaan, pertanyaan di luar QUESTION ditolak`() {
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(ChatMessageId("x"), conv, tenant, ChatRole.USER, "t", kind = ChatMessageKind.QUESTION, questions = listOf(Clarification("c", "q")))
        }
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(ChatMessageId("x"), conv, tenant, ChatRole.AGENT, "t", kind = ChatMessageKind.QUESTION)
        }
        assertFailsWith<IllegalArgumentException> {
            ChatMessage(ChatMessageId("x"), conv, tenant, ChatRole.AGENT, "t", questions = listOf(Clarification("c", "q")))
        }
    }
}
