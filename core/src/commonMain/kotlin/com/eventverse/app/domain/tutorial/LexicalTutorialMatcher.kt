package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId

/**
 * Pencocok berbasis tumpang-tindih kata (FR-5). Deterministik dan tanpa jaringan: dipakai sendiri saat LLM mati,
 * dan sebagai penyaring kandidat sebelum LLM memilih (Fase 3).
 *
 * Bobot: contoh pertanyaan & kata kunci 3, judul 2, ringkasan & judul langkah 1. Tutorial modul yang sedang dibuka
 * mendapat +1 — hanya bila sudah cocok, supaya layar aktif tidak memenangkan pertanyaan yang tak berhubungan.
 */
class LexicalTutorialMatcher(private val limit: Int = 5) : TutorialMatcher {

    override fun rank(question: String, candidates: List<ModuleTutorial>, currentModule: ModuleId?): List<TutorialMatch> {
        val asked = tokens(question)
        if (asked.isEmpty()) return emptyList()
        return candidates.mapNotNull { t ->
            val base = 3 * overlap(asked, t.sampleQuestions + t.keywords) +
                2 * overlap(asked, listOf(t.title)) +
                overlap(asked, listOf(t.summary) + t.steps.map { it.title })
            if (base == 0) return@mapNotNull null
            val bonus = if (currentModule != null && t.moduleId == currentModule) 1 else 0
            TutorialMatch(t, bestStep(asked, t), base + bonus)
        }.sortedByDescending { it.score }.take(limit)
    }

    private fun bestStep(asked: Set<String>, t: ModuleTutorial): Int {
        val scores = t.steps.map { overlap(asked, listOf(it.title, it.body)) }
        val best = scores.maxOrNull() ?: 0
        return if (best == 0) 0 else scores.indexOf(best)
    }

    private fun overlap(asked: Set<String>, texts: List<String>): Int {
        val field = texts.flatMapTo(mutableSetOf()) { tokens(it) }
        return asked.count { it in field }
    }

    internal companion object {
        private val STOPWORDS = setOf(
            "yang", "dan", "di", "ke", "dari", "untuk", "dengan", "ini", "itu", "ada", "atau", "saya", "aku", "kita",
            "kami", "gimana", "bagaimana", "cara", "caranya", "mau", "ingin", "bisa", "tidak", "gak", "nggak", "ga",
            "apa", "kenapa", "mana", "dimana", "tolong", "dong", "sih", "nih", "ya", "kok", "lagi", "sudah", "udah",
            "belum", "jadi", "akan", "the", "how", "to", "a", "is",
        )
        private val SUFFIXES = listOf("nya", "kan", "lah")

        fun tokens(text: String): Set<String> = text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .map(::stem)
            .filterTo(mutableSetOf()) { it.length >= 2 && it !in STOPWORDS }

        /** Pemotong akhiran ringan: "leadnya" → "lead", "tambahkan" → "tambah". Bukan stemmer bahasa lengkap. */
        private fun stem(word: String): String =
            SUFFIXES.firstOrNull { word.length > it.length + 3 && word.endsWith(it) }?.let { word.dropLast(it.length) } ?: word
    }
}
