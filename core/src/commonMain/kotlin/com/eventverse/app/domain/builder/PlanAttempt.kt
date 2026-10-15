package com.eventverse.app.domain.builder

import com.eventverse.app.domain.discovery.DiscoveryDraft
import com.eventverse.app.domain.discovery.interview.InterviewSession
import com.eventverse.app.domain.discovery.interview.InterviewPlanner
import com.eventverse.app.domain.discovery.interview.InterviewValidator
import kotlinx.coroutines.CancellationException

/**
 * Alasan rencana wawancara dari perencana (model) **tidak dipakai**. Enum sah (Uji Variabilitas): mekanik platform —
 * jenis kegagalan perencana — bukan kosakata industri.
 *
 * Dulu semua jenis ini diam-diam menjadi "tidak ada rencana": chat tetap jalan, tetapi pengguna tidak mendapat wawancara
 * dan modul tidak diringkas, tanpa jejak sedikit pun (temuan cek visual model sungguhan, 2026-10-16).
 */
enum class PlanRejectionKind {
    /** Perencana melempar galat (timeout, jawaban model rusak, jaringan). */
    PLANNER_ERROR,

    /** Rencana ada tetapi tidak lolos [InterviewValidator]; [PlanRejection.detail] memuat temuan berpath. */
    INVALID_PLAN,

    /** Setelah satu putaran tanya, perencana meminta putaran kedua — tidak diizinkan. */
    UNEXPECTED_SECOND_ROUND
}

/** Satu penolakan rencana. [detail] ringkas dan **tanpa isi narasi pengguna** (aman untuk log). */
data class PlanRejection(val kind: PlanRejectionKind, val detail: String)

/** Hasil satu percobaan rencana: tepat salah satu dari [session] (dipakai) atau [rejection] (tidak dipakai). */
internal data class PlanAttempt(val session: InterviewSession?, val rejection: PlanRejection?) {
    init { require((session == null) != (rejection == null)) { "PlanAttempt: tepat satu dari session atau rejection" } }
}

/** Cukup untuk beberapa temuan validator berpath; dulu 300 memotong persis bagian yang menjelaskan sebabnya. */
private const val DETAIL_MAX_CHARS = 900
private const val ISSUES_SHOWN = 4

/**
 * Menjalankan [planner] dan menilai hasilnya: galat → [PlanRejectionKind.PLANNER_ERROR]; tak lolos validator →
 * [PlanRejectionKind.INVALID_PLAN]; minta putaran kedua sesudah [answeredRound] → [PlanRejectionKind.UNEXPECTED_SECOND_ROUND].
 * Perilaku chat tidak berubah (penolakan tetap berarti "tanpa rencana"); yang baru hanya **alasannya terlihat**.
 * Pembatalan korutin selalu diteruskan.
 */
internal suspend fun attemptPlan(
    planner: InterviewPlanner,
    draft: DiscoveryDraft,
    input: InterviewSession,
    narrative: String,
    answeredRound: Boolean
): PlanAttempt {
    val planned = try {
        planner.plan(draft.copy(interview = input), input, narrative)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        return PlanAttempt(null, PlanRejection(PlanRejectionKind.PLANNER_ERROR, "${e::class.simpleName}: ${e.message.orEmpty()}".take(DETAIL_MAX_CHARS)))
    }
    val issues = InterviewValidator.validate(planned, draft.pack)
    if (issues.isNotEmpty()) {
        val shown = issues.take(ISSUES_SHOWN).joinToString("; ") { "${it.path}: ${it.message}" }
        val more = if (issues.size > ISSUES_SHOWN) " (+${issues.size - ISSUES_SHOWN} lagi)" else ""
        return PlanAttempt(null, PlanRejection(PlanRejectionKind.INVALID_PLAN, (shown + more).take(DETAIL_MAX_CHARS)))
    }
    if (answeredRound && planned.awaitingClarification) {
        return PlanAttempt(null, PlanRejection(PlanRejectionKind.UNEXPECTED_SECOND_ROUND, "perencana meminta putaran tanya kedua"))
    }
    return PlanAttempt(planned, null)
}
