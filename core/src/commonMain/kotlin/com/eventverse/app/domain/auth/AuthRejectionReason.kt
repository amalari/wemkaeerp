package com.eventverse.app.domain.auth

/**
 * Kode alasan penolakan sesi yang dibaca mesin, dikirim server lewat header [HEADER] di samping teks
 * penolakan yang dibaca manusia. Klien membedakan penolakan lewat kode ini, bukan dengan mencocokkan
 * teks pesan (yang bisa berubah dan berbahasa).
 */
object AuthRejectionReason {
    const val HEADER = "X-Auth-Reason"

    /** Token tenant-bound tanpa klaim slug, mis. sesi tersimpan sebelum token wajib-slug. */
    const val TOKEN_WITHOUT_TENANT = "token_without_tenant"
}
