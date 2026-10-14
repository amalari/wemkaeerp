package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Paritas: tiap [ChatMessageKind] punya perlakuan eksplisit di utas, narasi, dan konteks brief developer. */
class ChatMessageKindParityTest {

    private val tenant = TenantId("ten-uji")
    private val conv = BuilderConversationId("conv-ten-uji")

    private fun msg(kind: ChatMessageKind, role: ChatRole = ChatRole.AGENT, moduleId: String? = "klinik_poli") = when (kind) {
        ChatMessageKind.TEXT -> ChatMessage(ChatMessageId("t"), conv, tenant, role, "teks agent biasa", moduleId = moduleId)
        ChatMessageKind.QUESTION -> ChatMessage(
            ChatMessageId("q"), conv, tenant, ChatRole.AGENT, "tanya", moduleId = moduleId, kind = kind,
            questions = listOf(Clarification("c1", "Siapa?"))
        )
        ChatMessageKind.BRIEF -> ChatMessage(
            ChatMessageId("b"), conv, tenant, ChatRole.AGENT, "RINGKASAN-AGENT-RAHASIA", moduleId = "klinik_poli", kind = kind,
            features = listOf("f"), basisRef = "b1:x", briefSource = BriefSource.AI
        )
    }

    @Test
    fun `setiap ChatMessageKind returns tidak pernah menjadi ucapan pengguna di narasi`() {
        ChatMessageKind.entries.forEach { kind ->
            val narrative = composeNarrative(listOf(msg(kind)))
            assertTrue("RINGKASAN-AGENT-RAHASIA" !in narrative, "kind $kind mencemari narasi")
            assertTrue("teks agent biasa" !in narrative, "kind $kind mencemari narasi")
        }
    }

    @Test
    fun `setiap ChatMessageKind returns hanya QUESTION yang dihitung sebagai pertanyaan menunggu`() {
        ChatMessageKind.entries.forEach { kind ->
            val expected = if (kind == ChatMessageKind.QUESTION) 1 else 0
            assertEquals(expected, listOf(msg(kind)).pendingFollowUps(null).size, "kind $kind")
        }
    }

    @Test
    fun `setiap ChatMessageKind returns konteks brief developer tidak memuat ringkasan agent`() = runTest {
        ChatMessageKind.entries.forEach { kind ->
            val ctx = briefContextOf(listOf(msg(kind)), setOf("klinik_poli"))
            assertTrue(ctx?.narrative.orEmpty().isEmpty(), "kind $kind masuk cerita")
            assertTrue(ctx?.decisions.orEmpty().isEmpty(), "kind $kind dianggap keputusan")
            assertTrue(ctx?.answered.orEmpty().isEmpty(), "kind $kind dianggap tanya-jawab")
        }
    }
}
