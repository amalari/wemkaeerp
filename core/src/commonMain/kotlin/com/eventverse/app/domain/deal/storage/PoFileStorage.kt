package com.eventverse.app.domain.deal.storage

/**
 * Port untuk menyimpan berkas PO (scan/foto) ke object storage S3-compatible (MinIO/S3).
 *
 * Domain hanya mengenal kontrak ini; SDK, bucket, dan kredensial hidup di adapter server
 * (`infrastructure/storage/`). Key-nya deterministik per tenant/deal/PO supaya orphan
 * object mudah dikenali saat cleanup berkala.
 */
interface PoFileStorage {

    /**
     * Stores the bytes under [key]. Must be idempotent per key (same key, same content).
     * Fails with a descriptive message when storage is not configured/unreachable.
     */
    suspend fun put(key: String, bytes: ByteArray, contentType: String): Result<Unit>

    /** Short-lived download URL (presigned GET). Never a permanent URL. */
    suspend fun downloadUrl(key: String): Result<String>

    /** True when the adapter has the configuration it needs (endpoint, credentials, bucket). */
    val isConfigured: Boolean
}
