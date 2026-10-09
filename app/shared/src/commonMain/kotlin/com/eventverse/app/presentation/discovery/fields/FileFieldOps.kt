package com.eventverse.app.presentation.discovery.fields

import com.eventverse.app.domain.prototype.DataBinding
import com.eventverse.app.infrastructure.api.FieldFileApiClient
import com.eventverse.app.infrastructure.api.FieldFileRemoteDataSource

/**
 * Aksi jaringan field `FILE` untuk **satu record** prototype (TRD-FIELD-002 Track C).
 * Mengikuti pola unggah yang sudah terbukti (FulfillmentUploadEvidence): callback non-suspend
 * dengan `onDone` — host yang menjalankan coroutine, komponen UI tetap murni.
 *
 * v1 kontrak endpoint §4.4 mengunci `recordId` di path: unggah pertama hanya mungkin untuk
 * record yang **sudah ada** (form detail kanban / edit sel). Form pembuatan (blok Form dan
 * baris inline) belum punya id server, jadi tanpa ops — UI menampilkan penjelasannya, bukan
 * pemilih palsu (Kontrak 8 field-component-rules: komponen yang belum bisa jangan dipalsukan).
 *
 * @param fieldKey kunci field pengirim — bagian path endpoint upload/download.
 * @param ref yang dikirim ke [downloadUrl] hanya data tampilan; server menuntun dari
 *   `recordId`+`fieldKey` di path, bukan dari ref.
 */
class FileFieldOps(
    private val client: FieldFileRemoteDataSource,
    private val moduleCode: String,
    private val recordId: String
) {
    val upload: suspend (
        fieldKey: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        onDone: (Result<String>) -> Unit
    ) -> Unit = { fieldKey, fileName, mimeType, bytes, onDone ->
        onDone(client.uploadFieldFile(moduleCode, recordId, fieldKey, fileName, mimeType, bytes))
    }

    val downloadUrl: suspend (fieldKey: String, ref: String, onDone: (Result<String>) -> Unit) -> Unit =
        { fieldKey, _, onDone ->
            onDone(client.fieldFileDownloadUrl(moduleCode, recordId, fieldKey))
        }
}

/**
 * Klien bersama untuk seluruh aksi field `FILE` prototype — satu `HttpClient` per proses, bukan
 * per call site (kolom tabel & dialog kanban memanggil [fieldFileOpsOrNull] saat recomposisi;
 * membuat klien baru di sana akan membocorkan engine/connection pool). HttpClient Ktor memang
 * dirancang dipakai bersama.
 */
private val sharedFieldFileClient: FieldFileRemoteDataSource by lazy { FieldFileApiClient() }

/**
 * Membuat [FileFieldOps] dari binding layar; `null` untuk [DataBinding.Memory] (demo memori tak
 * punya server penyimpanan — unggah tidak pernah mungkin, dan tidak boleh pura-pura bisa).
 * `basePath` API berbentuk `/api/tenant/modules/{moduleCode}/{tabel}` (route hasil generate,
 * `SpecRoutesWriter`), jadi `moduleCode` diambil dari segmen setelah `modules/`.
 */
fun fieldFileOpsOrNull(binding: DataBinding?, recordId: String): FileFieldOps? {
    val basePath = (binding as? DataBinding.Api)?.basePath ?: return null
    val moduleCode = basePath.substringAfter("/modules/", "").substringBefore('/')
    if (moduleCode.isBlank()) return null
    return FileFieldOps(sharedFieldFileClient, moduleCode, recordId)
}
