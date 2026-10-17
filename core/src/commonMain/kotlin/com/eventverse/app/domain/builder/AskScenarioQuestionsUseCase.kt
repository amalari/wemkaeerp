package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.interview.Clarification
import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.datetime.Clock

/**
 * Mengubah pertanyaan pengusul skenario (FR-9, [ScenarioSketch.questions]) menjadi pesan QUESTION di utas **Semua**
 * (`moduleId = null`), memakai mekanik follow-up yang sudah ada: badge utas Semua menghitungnya lewat `pendingFollowUps(null)`,
 * jawaban kembali ke model lewat `composeNarrative`.
 *
 * Satu pesan per skenario (teks diawali judul skenario). Idempoten: pertanyaan yang `Clarification.id`-nya ([ScenarioKeys.questionId])
 * sudah ada di utas — dijawab atau belum — dilewati, sehingga memanggilnya ulang dengan skenario yang sama tidak menggandakan
 * pesan. **Teks yang sama (ternormalisasi) tidak ditanyakan dua kali walau datang dari skenario lain**: jawabannya sudah masuk
 * narasi dan berlaku untuk semua skenario (cek visual 2026-10-18: dua skenario menanyakan hal yang sama, pengguna menjawab dua kali). Id pesan memuat id skenario **dan** sidik pertanyaan barunya, jadi dua skenario tak pernah bentrok (pelajaran
 * `FollowUpMessageIdTest`).
 *
 * Pemanggil memberi skenario yang BARU ditambahkan (id final, sesudah `admit` memberi akhiran bila bentrok) dan hanya setelah
 * gerbang akses lolos dan `propose` berhasil. Mengembalikan jumlah pesan yang dibuat.
 */
class AskScenarioQuestionsUseCase(
    private val chats: BuilderChatRepository,
    private val clock: Clock = Clock.System
) {
    suspend operator fun invoke(tenantId: TenantId, scenarios: List<ScenarioSketch>): Result<Int> = runCatching {
        val withQuestions = scenarios.filter { it.questions.isNotEmpty() }
        if (withQuestions.isEmpty()) return@runCatching 0
        val conversation = chats.conversationFor(tenantId)
        val asked = chats.messages(conversation.id).filter { it.kind == ChatMessageKind.QUESTION }
            .flatMap { m -> m.questions }.filter { ScenarioKeys.isQuestionId(it.id) }
        val known = asked.map { it.id }.toMutableSet()
        val knownText = asked.map { BriefKeys.normalize(it.question) }.toMutableSet()
        var created = 0
        for (scenario in withQuestions) {
            val fresh = scenario.questions.map { Clarification(ScenarioKeys.questionId(scenario.id, it), it.trim()) }
                .filter { known.add(it.id) && knownText.add(BriefKeys.normalize(it.question)) }
            if (fresh.isEmpty()) continue
            chats.append(
                ChatMessage(
                    id = ChatMessageId("msg-${conversation.id.value}-s-${scenario.id.value}-${BriefKeys.fingerprint(fresh.joinToString("|") { it.id }).take(ID_SUFFIX)}"),
                    conversationId = conversation.id, tenantId = tenantId, role = ChatRole.AGENT,
                    text = "Skenario '${scenario.title}': ada hal yang perlu dipastikan sebelum alurnya dianggap benar.",
                    createdAt = clock.now(), moduleId = null, kind = ChatMessageKind.QUESTION, questions = fresh
                )
            )
            created++
        }
        created
    }

    private companion object { const val ID_SUFFIX = 12 }
}
