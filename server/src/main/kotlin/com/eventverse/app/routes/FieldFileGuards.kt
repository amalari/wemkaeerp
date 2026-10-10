package com.eventverse.app.routes

import com.eventverse.app.domain.storage.FileRef
import com.eventverse.app.domain.tenant.TenantContext
import com.eventverse.app.plugins.tenantContextOrNull
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger("FieldFileRoutes")

/** 503 + WARN tanpa isi body; pesan menyebut env yang harus diisi (FR-3). */
internal suspend fun ApplicationCall.rejectStorageUnavailable() {
    log.warn("FIELD FILE ditolak 503: object storage belum dikonfigurasi (S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_FILES)")
    respond(
        HttpStatusCode.ServiceUnavailable,
        "Object storage belum dikonfigurasi (S3_ENDPOINT/S3_ACCESS_KEY/S3_SECRET_KEY/S3_BUCKET_FILES)."
    )
}

/** 415 + WARN tanpa isi body — hanya nama tipe yang dicatat. */
internal suspend fun ApplicationCall.rejectUnsupportedMediaType(contentType: String) {
    log.warn("FIELD FILE ditolak 415: tipe konten '{}' di luar allowlist", contentType)
    respond(HttpStatusCode.UnsupportedMediaType, "Tipe berkas $contentType tidak diizinkan")
}

/** 413 + WARN tanpa isi body — hanya ukuran yang dicatat. */
internal suspend fun ApplicationCall.rejectPayloadTooLarge() {
    log.warn("FIELD FILE ditolak 413: ukuran melebihi {} MB", MAX_FIELD_FILE_BYTES / (1024 * 1024))
    respond(HttpStatusCode.PayloadTooLarge, "Ukuran berkas melebihi ${MAX_FIELD_FILE_BYTES / (1024 * 1024)} MB")
}

internal suspend fun ApplicationCall.requireFieldFileTenant(): TenantContext? {
    val tenant = tenantContextOrNull
    if (tenant == null) {
        respond(HttpStatusCode.NotFound, "No tenant context found")
    }
    return tenant
}

/**
 * Body unggahan dengan batas memori (DoS): `receive<ByteArray>()` memuat SELURUH body sebelum ukuran
 * dicek. Di sini (1) `Content-Length` > [MAX_FIELD_FILE_BYTES] ditolak 413 tanpa membaca satu byte pun,
 * dan (2) tanpa `Content-Length` (chunked) atau dengan header yang bohong, stream dibaca paling banyak
 * batas + 1 byte — body lebih besar tak pernah dimuat penuh. Mengembalikan `null` bila sudah menjawab 413;
 * array kosong dikembalikan apa adanya (pemanggil menjawab 400).
 */
internal suspend fun ApplicationCall.readBoundedBody(): ByteArray? {
    val declared = request.header(HttpHeaders.ContentLength)?.toLongOrNull()
    if (declared != null && declared > MAX_FIELD_FILE_BYTES) {
        rejectPayloadTooLarge()
        return null
    }
    val bytes = receiveChannel().readRemaining(MAX_FIELD_FILE_BYTES.toLong() + 1).readByteArray()
    if (bytes.size > MAX_FIELD_FILE_BYTES) {
        rejectPayloadTooLarge()
        return null
    }
    return bytes
}

/**
 * Sabuk kedua isolasi tenant pada unduh: ref yang dibaca dari sel HARUS milik tenant pemanggil, modul pada path
 * (bila [moduleCode] diberikan), dan **record** pada path ([recordId], wajib — TRD-FIELD-004 FR-1.3: ref berkas
 * record lain yang tertulis di sel ini tidak boleh diunduh lewat gerbang record ini) sebelum menyentuh
 * `ObjectStorage`. Ref asing = 403,
 * dicatat WARN tanpa key lengkap (hanya segmen tenant yang diklaim, bukan path objek).
 * Mengembalikan ref bila sah; `null` bila sudah menjawab 403.
 */
internal suspend fun ApplicationCall.requireOwnFileRef(tenant: TenantContext, ref: String, moduleCode: String?, recordId: String): String? {
    if (FileRef.isValidFor(tenant.tenantId.value, ref, moduleCode, recordId)) return ref
    log.warn(
        "FIELD FILE ditolak 403: ref bukan milik tenant {} (segmen tenant pada ref: '{}')",
        tenant.tenantId.value, ref.removePrefix(FileRef.PREFIX).substringBefore('/').take(40)
    )
    respond(HttpStatusCode.Forbidden, "Referensi berkas bukan milik data ini.")
    return null
}
