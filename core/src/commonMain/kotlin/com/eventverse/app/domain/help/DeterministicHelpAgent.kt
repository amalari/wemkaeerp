package com.eventverse.app.domain.help

/** Agent tanpa LLM: menyarankan kandidat teratas dengan ringkasannya. Selalu berhasil — dipakai sebagai fallback. */
class DeterministicHelpAgent : HelpAgent {
    override val agentRef: String = "deterministic/help-v1"

    override suspend fun answer(question: HelpQuestion): Result<HelpAnswer> {
        val top = question.candidates.firstOrNull()
            ?: return Result.success(HelpAnswer(NO_MATCH, tutorialId = null, stepIndex = null, agentRef = agentRef))
        val text = "Panduan yang paling cocok: \"${top.tutorial.title}\". ${top.tutorial.summary}".trim()
        return Result.success(HelpAnswer(text, top.tutorial.id, top.stepIndex, agentRef))
    }

    companion object {
        const val NO_MATCH = "Saya belum menemukan panduan yang cocok. Coba sebutkan nama menu atau tombol yang ingin dipakai."
    }
}
