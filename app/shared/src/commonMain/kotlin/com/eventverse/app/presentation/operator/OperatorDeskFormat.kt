package com.eventverse.app.presentation.operator

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** "25/09 14:05" — cukup untuk lantai produksi; tahun tidak pernah jadi pertanyaan di sini. */
fun formatDeskTime(instant: Instant, timeZone: TimeZone): String {
    val t = instant.toLocalDateTime(timeZone)
    fun two(n: Int) = n.toString().padStart(2, '0')
    return "${two(t.dayOfMonth)}/${two(t.monthNumber)} ${two(t.hour)}:${two(t.minute)}"
}

/** "1j 35m" / "20m" / "2h 3j" — satuan terbesar yang masih bermakna, maksimal dua satuan. */
fun formatDeskDuration(minutes: Long): String {
    val days = minutes / (60 * 24)
    val hours = (minutes / 60) % 24
    val mins = minutes % 60
    return when {
        days > 0 -> "${days}h ${hours}j"
        hours > 0 -> "${hours}j ${mins}m"
        else -> "${mins}m"
    }
}

/** Siapa yang mengerjakan: nama operator dari klaim "Mulai", jatuh ke akun yang memindahkan. */
val DeskHandoff.workerLabel: String
    get() = audit.operatorName?.takeIf { it.isNotBlank() } ?: audit.actorEmail

/**
 * "mulai 25/09 09:10 • selesai 10:45 • kerja 1j 35m • tunggu 20m".
 * SPK yang diserahkan tanpa pernah ditekan "Mulai" (data lama, jalur makloon) hanya punya jam
 * selesai — dikatakan terus terang, bukan dikarang dari jam tiba.
 */
fun DeskHandoff.timingLine(timeZone: TimeZone): String {
    val endVerb = when {
        audit.isRelease -> "dikembalikan"
        audit.isRework -> "dikirim"
        else -> "selesai"
    }
    val done = formatDeskTime(audit.at, timeZone)
    val started = audit.workStartedAt ?: return "$endVerb $done • tanpa Mulai"
    return buildList {
        add("mulai ${formatDeskTime(started, timeZone)}")
        add("$endVerb $done")
        workMinutes?.let { add("kerja ${formatDeskDuration(it)}") }
        waitMinutes?.let { add("tunggu ${formatDeskDuration(it)}") }
    }.joinToString(" • ")
}

/** Apa yang terjadi pada kartu di entri ini, dalam bahasa lantai produksi. */
val DeskHandoff.eventLabel: String
    get() = when {
        audit.isRelease -> "Dikembalikan ke antrian"
        audit.isRework -> "Rework ke ${order.deskLabelOf(audit.toCode)}: ${audit.reason.orEmpty()}"
        else -> "Diserahkan ke ${order.deskLabelOf(audit.toCode)}"
    }
