package com.eventverse.app.presentation.common

import com.eventverse.app.infrastructure.api.FieldFileHttpException

/**
 * Konstanta & pemetaan pesan berkas field (TRD-FIELD-002 FR-3/FR-6, Track C) — dipakai bersama
 * oleh form prototype (`FieldInput`) dan CRM (`LeadCustomField`). Angka batas di sini harus
 * cocok dengan `MAX_FIELD_FILE_BYTES` di server; server tetap sumber kebenaran (413 fail-closed),
 * nilai ini hanya untuk penjaga awal di klien dan teks bantuannya.
 */
const val FIELD_FILE_MAX_BYTES: Int = 10 * 1024 * 1024

/** Allowlist MIME v1 (pdf, png, jpeg, webp, txt, csv) — memfilter picker berkas klien. */
const val FIELD_FILE_ACCEPT: String =
    "application/pdf,image/png,image/jpeg,image/webp,text/plain,text/csv"

/**
 * Nama berkas yang tampil dari ref: segmen terakhir (kontrak R4 v1 — resolusi metadata
 * nama asli/ukuran menyusul lewat sidecar/route meta; ref tetap satu-satunya nilai sel).
 * Bentuk tak dikenal (bukan `fields/...`) kembali apa adanya — fallback id, tanpa rekayasa.
 */
fun fileRefDisplayName(ref: String): String =
    if (ref.contains('/')) ref.substringAfterLast('/').ifBlank { ref } else ref

/** Ukuran berkas dalam teks manusiawi (KB/MB, satu desimal, koma desimal Indonesia). */
fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    return when {
        kb >= 1024 -> "${oneDecimal(kb / 1024.0)} MB"
        kb >= 1 -> "${kb.toInt()} KB"
        else -> "$bytes B"
    }
}

/** Satu desimal dengan koma, tanpa `java.util.Locale` (tak tersedia di commonMain). */
private fun oneDecimal(value: Double): String {
    val tenths = (value * 10).toInt()
    val whole = tenths / 10
    val frac = tenths % 10
    return if (frac == 0) whole.toString() else "$whole,$frac"
}

/** Teks bantu batas di bawah kontrol berkas. */
fun fieldFileSizeHint(): String =
    "Maks ${formatFileSize(FIELD_FILE_MAX_BYTES.toLong())} · PDF, gambar, TXT, CSV"

/**
 * Penjaga ukuran sisi klien (satu sumber untuk form prototype & CRM): mengembalikan pesan galat
 * bila [actualBytes] melewati [FIELD_FILE_MAX_BYTES], atau `null` bila lolos. Ini **hanya**
 * rahmat pertama — server tetap penentu akhir (413 fail-closed).
 */
fun fieldFileClientSizeError(actualBytes: Long): String? =
    if (actualBytes > FIELD_FILE_MAX_BYTES) {
        "Berkas ${formatFileSize(actualBytes)} melebihi batas " +
            formatFileSize(FIELD_FILE_MAX_BYTES.toLong()) + "."
    } else {
        null
    }

/**
 * Pemetaan galat unggah/unduh ke pesan manusiawi. 413/415/503 dari server (TRD-FIELD-002 FR-3)
 * dipetakan ke sebab yang bisa dibaca; pesan server non-kosong untuk kasus lain diteruskan apa
 * adanya, dan sisanya pesan generik — tanpa kode HTTP mentah membocor ke layar.
 */
fun fieldFileErrorMessage(error: Throwable): String {
    if (error !is FieldFileHttpException) {
        return error.message?.takeIf { it.isNotBlank() } ?: "Gagal memproses berkas."
    }
    return when (error.status) {
        // Batas ukuran dimiliki server: bila server mengirim alasannya, pakai itu; kalau tidak,
        // sampaikan batas kontrak (JANGAN menelan pesan server lalu mengarang batas berbeda).
        413 -> error.message?.takeIf { it.isNotBlank() }
            ?: "Ukuran berkas melebihi batas ${formatFileSize(FIELD_FILE_MAX_BYTES.toLong())}."
        415 -> "Tipe berkas tidak didukung — gunakan PDF, PNG, JPEG, WebP, TXT, atau CSV."
        503 -> "Penyimpanan berkas belum siap di server (env S3 belum diatur). Hubungi admin."
        403 -> "Anda tidak berwenang memproses berkas pada data ini."
        404 -> "Data atau berkas tidak ditemukan — mungkin sudah dihapus."
        400 -> error.message?.takeIf { it.isNotBlank() } ?: "Permintaan unggah tidak sah."
        else -> error.message?.takeIf { it.isNotBlank() } ?: "Gagal memproses berkas (HTTP ${error.status})."
    }
}
