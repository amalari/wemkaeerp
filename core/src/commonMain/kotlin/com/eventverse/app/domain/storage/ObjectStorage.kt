package com.eventverse.app.domain.storage

/**
 * Port penyimpanan objek umum (C8, TRD-FIELD-002 FR-1) — bukan lagi milik deal (`PoFileStorage` di
 * `domain/deal/storage/` tetap seperti adanya; strangler: antarmuka baru di sampingnya). Byte tidak
 * pernah melewati domain selain sebagai argumen [put]; nilai field hanyalah [FileRef].
 *
 * Diimplementasikan infrastruktur server (adapter S3-compatible, Track B); SDK/kredensial/bucket
 * tidak pernah bocor ke domain. `isConfigured == false` = seluruh operasi FILE gagal 503 (fail-closed;
 * TIDAK ada fallback inline-base64 untuk field — beda dari mockup dev 2 MB).
 */
interface ObjectStorage {
    /** Simpan byte di [key] dengan [contentType]; idempotent per key. */
    suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit>

    /** URL unduh presigned berumur singkat (≤15 menit) — bukan berkas permanen. */
    suspend fun downloadUrl(key: String): Result<String>

    /** False = env S3 belum lengkap; pemanggil (route) wajib menolak dengan 503 yang menyebut env kurang. */
    val isConfigured: Boolean
}
