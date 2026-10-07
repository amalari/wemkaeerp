package com.eventverse.app.domain.discovery.interview

/**
 * Satu tebakan sistem: [key] mengacu ke butir yang ditebak (kode divisi, kunci peran, atau `peran:modul`),
 * [label] teks tampil, [confidence] 0–100. Tebakan hanyalah **usulan**; validator menegakkan.
 */
data class Guess(val key: String, val label: String, val confidence: Int, val origin: ModuleOrigin? = null) {
    init {
        require(key.isNotBlank()) { "Guess.key kosong" }
        require(confidence in 0..100) { "Guess.confidence harus 0–100" }
    }
}

/** Pertanyaan = data, bukan teks tempel: klien menggambarnya, agent mengisinya. `nextQuestion` menyusul di B3. */
data class InterviewQuestion(
    val id: String,
    val step: InterviewStep,
    val prompt: String,
    val guesses: List<Guess> = emptyList()
) {
    init {
        require(id.isNotBlank()) { "InterviewQuestion.id kosong" }
        require(prompt.isNotBlank()) { "InterviewQuestion.prompt kosong" }
    }
}
