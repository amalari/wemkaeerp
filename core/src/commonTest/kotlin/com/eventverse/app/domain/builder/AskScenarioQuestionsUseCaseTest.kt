package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.scenario.ScenarioId
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.domain.discovery.scenario.ScenarioStep
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** SC3 / FR-9 Track A: pertanyaan skenario menjadi pesan QUESTION utas Semua; idempoten, id unik, per tenant. */
class AskScenarioQuestionsUseCaseTest {
    private val klinik = TenantId("klinik-uji")
    private val bordir = TenantId("bordir-uji")
    private val clock = object : kotlinx.datetime.Clock { override fun now() = Instant.fromEpochSeconds(1_700_000_000) }

    /** Meniru PK Postgres: id yang MASUK ke `append` tidak boleh ganda (repo in-memory menempelkan nomor urut). */
    private class StrictChats(private val inner: InMemoryBuilderChatRepository) : BuilderChatRepository by inner {
        private val seen = mutableSetOf<String>()
        override suspend fun append(message: ChatMessage): ChatMessage {
            check(seen.add(message.id.value)) { "duplicate key: ${message.id.value}" }
            return inner.append(message)
        }
    }

    private fun scenario(id: String, vararg questions: String) = ScenarioSketch(
        ScenarioId(id), "Judul $id", listOf(ScenarioStep(ModuleId("klinik_kasir"), null, "Bayar")), ScenarioSource.AI,
        questions = questions.toList()
    )

    private suspend fun BuilderChatRepository.all(t: TenantId) = messages(conversationFor(t).id)

    @Test
    fun `scenario with questions should create one QUESTION message in the all thread`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val n = AskScenarioQuestionsUseCase(chats, clock)(klinik, listOf(scenario("scn-1", "DP harus lunas dulu?", "Siapa menerbitkan invoice?"))).getOrThrow()
        val m = chats.all(klinik).single()
        assertEquals(1, n)
        assertEquals(ChatMessageKind.QUESTION, m.kind)
        assertEquals(null, m.moduleId, "utas Semua")
        assertEquals(ChatRole.AGENT, m.role)
        assertTrue(m.text.contains("Judul scn-1"))
        assertEquals(2, m.questions.size)
        assertTrue(m.questions.all { it.id.startsWith("scn:scn-1:") && it.answer == null })
    }

    @Test
    fun `calling again with the same scenarios should not duplicate messages`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val ask = AskScenarioQuestionsUseCase(chats, clock)
        val s = listOf(scenario("scn-1", "DP harus lunas dulu?"))
        assertEquals(1, ask(klinik, s).getOrThrow())
        assertEquals(0, ask(klinik, s).getOrThrow())
        assertEquals(1, chats.all(klinik).size)
    }

    @Test
    fun `two scenarios in the same second should get distinct message ids`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val n = AskScenarioQuestionsUseCase(chats, clock)(klinik, listOf(scenario("scn-1", "Pertanyaan satu?"), scenario("scn-2", "Pertanyaan satu?"))).getOrThrow()
        assertEquals(2, n)
        assertEquals(2, chats.all(klinik).map { it.id }.toSet().size)
    }

    @Test
    fun `answered question should not be asked again`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val ask = AskScenarioQuestionsUseCase(chats, clock)
        val s = listOf(scenario("scn-1", "DP harus lunas dulu?"))
        ask(klinik, s).getOrThrow()
        val m = chats.all(klinik).single()
        chats.markAnswered(m.id, mapOf(m.questions.single().id to "Ya, lunas dulu"))
        assertEquals(0, ask(klinik, s).getOrThrow())
        assertEquals(1, chats.all(klinik).size)
    }

    @Test
    fun `scenario questions should count in the all thread badge but not in a module thread`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        AskScenarioQuestionsUseCase(chats, clock)(klinik, listOf(scenario("scn-1", "A?", "B?"))).getOrThrow()
        val thread = chats.all(klinik)
        assertEquals(2, thread.pendingFollowUps(null).size)
        assertEquals(0, thread.pendingFollowUps("klinik_kasir").size)
    }

    @Test
    fun `answer should reach the narrative that feeds the next proposal`() = runTest {
        val chats = InMemoryBuilderChatRepository()
        AskScenarioQuestionsUseCase(chats, clock)(klinik, listOf(scenario("scn-1", "DP harus lunas dulu?"))).getOrThrow()
        val m = chats.all(klinik).single()
        chats.markAnswered(m.id, mapOf(m.questions.single().id to "Ya, lunas dulu"))
        val narrative = composeNarrative(chats.all(klinik))
        assertTrue(narrative.contains("DP harus lunas dulu?") && narrative.contains("Ya, lunas dulu"), narrative)
    }

    @Test
    fun `scenarios without questions should create nothing and another tenant should stay untouched`() = runTest {
        val chats = StrictChats(InMemoryBuilderChatRepository())
        val ask = AskScenarioQuestionsUseCase(chats, clock)
        assertEquals(0, ask(klinik, listOf(scenario("scn-1"))).getOrThrow())
        assertEquals(0, chats.all(klinik).size)
        ask(klinik, listOf(scenario("scn-2", "Satu?"))).getOrThrow()
        assertEquals(0, chats.all(bordir).size, "tenant lain tidak ikut")
        assertEquals(1, ask(bordir, listOf(scenario("scn-2", "Satu?"))).getOrThrow(), "tenant kedua punya utasnya sendiri")
    }
}
