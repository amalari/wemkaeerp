package com.eventverse.app.domain.i18n

/**
 * Kosakata **kode error stabil** yang dipakai respons server dan diterjemahkan klien
 * (PLAN-i18n Fase 1, Track B3 & Track C4).
 *
 * Ini satu-satunya sumber kebenaran: klien (`presentation/i18n/ServerErrorCodes`)
 * merujuk konstanta di sini alih-alih menggandakan string. Kode adalah kontrak
 * mesin — **jangan** diubah tanpa migrasi klien, karena klien memetakannya ke teks
 * berbahasa id/en. Pesan manusia boleh berubah; `code` tidak.
 */
object ServerErrorCode {
    const val UNKNOWN = "UNKNOWN"
    const val NOT_FOUND = "NOT_FOUND"
    const val UNAUTHORIZED = "UNAUTHORIZED"
    const val FORBIDDEN = "FORBIDDEN"
    const val VALIDATION_ERROR = "VALIDATION_ERROR"
    const val DUPLICATE_ENTRY = "DUPLICATE_ENTRY"
    const val INTERNAL_ERROR = "INTERNAL_ERROR"
    const val NETWORK_ERROR = "NETWORK_ERROR"
    const val BAD_REQUEST = "BAD_REQUEST"
    const val CONFLICT = "CONFLICT"

    /** Body/parameter bahasa hilang atau tidak bisa dibaca. */
    const val MISSING_LANGUAGE = "MISSING_LANGUAGE"

    /** Nilai bahasa di luar kosakata yang didukung (`id`, `en`). */
    const val UNKNOWN_LANGUAGE = "UNKNOWN_LANGUAGE"

    /** Konteks tenant tidak ditemukan pada permintaan yang membutuhkannya. */
    const val NO_TENANT_CONTEXT = "NO_TENANT_CONTEXT"

    /** Muatan JSON tidak terbaca atau bentuknya salah. */
    const val INVALID_BODY = "INVALID_BODY"

    /** Transisi status tidak sah untuk agregat (mis. seal karton yang sudah SEALED). */
    const val INVALID_STATE = "INVALID_STATE"
}
