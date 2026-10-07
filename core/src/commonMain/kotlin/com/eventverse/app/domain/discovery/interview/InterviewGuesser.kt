package com.eventverse.app.domain.discovery.interview

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.pack.DomainPack

/**
 * Port pembuat tebakan wawancara (PLAN-iv-B §6). Dua pelaksana: [DeterministicInterviewGuesser] (kamus pack, tanpa
 * LLM) dan `AgentInterviewGuesser` (Koog, agent C). Keduanya hanya mengeluarkan **usulan** — `InterviewValidator`
 * yang menegakkan, jadi pelaksana yang keliru tidak pernah merusak draf.
 */
interface InterviewGuesser {
    suspend fun guess(step: InterviewStep, pack: DomainPack, draft: DiscoveryDraft, narrative: String): Result<List<Guess>>
}
