package com.eventverse.app.infrastructure.api

/**
 * Respons non-2xx dari API pipeline tenant, dengan status dan isi pesan server dipisah supaya
 * lapisan presentasi dapat membedakan 403 (akses ditolak) dari 5xx/jaringan tanpa mengurai teks.
 * [message] tetap berformat lama ("Gagal ... (HTTP n): isi") agar pemanggil lama tidak berubah.
 */
class PipelineRequestException(
    val action: String,
    val status: Int,
    val serverMessage: String
) : IllegalStateException("Gagal $action (HTTP $status): $serverMessage")
