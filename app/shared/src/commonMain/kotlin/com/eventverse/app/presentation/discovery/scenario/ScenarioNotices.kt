package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.ScenarioIssue
import com.eventverse.app.infrastructure.api.BuilderHttpException
import com.eventverse.app.infrastructure.api.ScenarioProposeOutcome
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue

/** Pesan hasil "Usulkan skenario" (pola `briefRefreshNotice`). Teks Latin-1. */
fun scenarioProposeNotice(o: ScenarioProposeOutcome): String = when {
    !o.featureEnabled -> SCENARIO_FEATURE_OFF
    o.scenarios.isEmpty() -> "Belum ada skenario yang bisa disusun. Ceritakan alurnya di utas Semua dulu."
    else -> "${o.scenarios.size} skenario diusulkan. Periksa lalu konfirmasi."
}

internal const val SCENARIO_FEATURE_OFF = "Fitur skenario belum aktif di server ini."

/**
 * Pesan galat "Konfirmasi". 404 membawa badan galat (`{"error":..}`) bila id tak ada; 404 tanpa badan galat = rute tak
 * ada (fitur mati). 422 menampilkan alasan singkat dari `issues` berpath. Galat lain: pesannya apa adanya.
 */
fun scenarioConfirmFailureNotice(e: Throwable): String {
    if (e !is BuilderHttpException) return e.message ?: "Konfirmasi gagal."
    val body = runCatching { JsonParser.parse(e.message.orEmpty()) as? JsonValue.Obj }.getOrNull()
    return when (e.status) {
        404 -> if (body?.string("error") != null) "Skenario tidak ditemukan. Muat ulang lalu coba lagi." else SCENARIO_FEATURE_OFF
        403 -> "Anda tidak berwenang mengonfirmasi skenario."
        422 -> {
            val issues = body?.objectArray("issues").orEmpty()
                .mapNotNull { i -> i.string("message")?.let { m -> i.string("path")?.let { p -> "$p: $m" } ?: m } }
            val reason = issues.take(2).joinToString("; ").ifBlank { body?.string("error").orEmpty() }
            if (reason.isBlank()) "Skenario tidak lolos validasi." else "Skenario tidak lolos validasi: $reason"
        }
        else -> e.message ?: "Konfirmasi gagal."
    }
}

/** Pesan galat "Usulkan skenario" selain 404 (yang sudah jadi [scenarioProposeNotice]). */
fun scenarioProposeFailureNotice(e: Throwable): String =
    if (e is BuilderHttpException && e.status == 403) "Anda tidak berwenang menyusun skenario."
    else if (e is BuilderHttpException && e.status == 409) "Masih ada proses lain berjalan. Coba lagi sebentar."
    else e.message ?: "Penyusunan skenario gagal."

/** Pesan di langkah setelah "Lanjut": alasan bila ditolak, peringatan pasangan yang tak dibawa, atau keterangan netral; null = tak ada yang perlu dikatakan. */
fun scenarioAdvanceMessage(a: ScenarioAdvance): String? = when (a) {
    is ScenarioAdvance.Blocked -> a.reason + if (a.issues.isEmpty()) "" else " " + carryWarningText(a.issues)
    is ScenarioAdvance.Moved -> when {
        a.warnings.isNotEmpty() -> "Sebagian data tidak dibawa: " + carryWarningText(a.warnings)
        else -> a.note
    }
}

/** Teks peringatan pasangan field yang ditolak aturan pembawa, untuk ditampilkan di langkah. */
fun carryWarningText(issues: List<ScenarioIssue>): String =
    issues.take(3).joinToString("; ") { it.message } + if (issues.size > 3) " (+${issues.size - 3} lagi)" else ""
