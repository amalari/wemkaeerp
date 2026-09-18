package com.eventverse.app.presentation.qc

import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.infrastructure.storage.PlatformLocalStorage
import com.eventverse.app.shared.json.JsonParser
import com.eventverse.app.shared.json.JsonValue
import com.eventverse.app.shared.json.jsonObjectOf
import com.eventverse.app.shared.json.jsonStringMapOf

/** Isi lembar yang sedang diketik, cukup untuk memulihkannya persis seperti ditinggalkan. */
data class QcDraft(
    val actuals: Map<String, String> = emptyMap(),
    val notes: Map<String, String> = emptyMap()
) {
    val isEmpty: Boolean
        get() = actuals.values.none { it.isNotBlank() } && notes.values.none { it.isNotBlank() }
}

/**
 * Simpanan draf lembar QC di perangkat.
 *
 * Kenapa lokal, bukan ke server: draf adalah pekerjaan yang **belum** jadi pernyataan. Mengirim
 * ukuran setengah jadi ke server berarti membuat baris berita acara yang belum ditandatangani
 * siapa pun, dan lembar QC yang setengah tersimpan lebih berbahaya daripada lembar yang hilang —
 * ia terbaca seperti hasil pemeriksaan. Yang disimpan di sini murni ketikan; yang menjadi fakta
 * tetap hanya yang disubmit.
 *
 * Konsekuensinya jelas dan harus diterima: draf terikat pada satu peramban. Petugas yang pindah
 * komputer mulai dari lembar kosong. Untuk stasiun QC — satu meja, satu layar — itu bukan kerugian.
 *
 * Seluruh akses dibungkus [runCatching] karena penyimpanan peramban bisa dimatikan, penuh, atau
 * melempar di mode penyamaran. Draf yang gagal disimpan tidak boleh menjatuhkan lembar yang
 * sedang diisi petugas.
 */
object QcDraftStore {

    fun keyFor(orderId: String, kind: QcInspectionKind, pieceNo: Int): String =
        "qc_draft:$orderId:${kind.name}:$pieceNo"

    fun load(key: String): QcDraft? = runCatching {
        val raw = PlatformLocalStorage.getItem(key) ?: return null
        val obj = JsonParser.parse(raw) as? JsonValue.Obj ?: return null
        QcDraft(
            actuals = obj.obj("actuals")?.let { readStringMap(it) } ?: emptyMap(),
            notes = obj.obj("notes")?.let { readStringMap(it) } ?: emptyMap()
        )
    }.getOrNull()

    fun save(key: String, draft: QcDraft) {
        runCatching {
            // Draf kosong tidak perlu memenuhi penyimpanan — dan menghapusnya di sini yang
            // membersihkan sisa ketikan saat petugas mengosongkan lagi kolomnya.
            if (draft.isEmpty) {
                PlatformLocalStorage.removeItem(key)
                return@runCatching
            }
            val payload = jsonObjectOf(
                "actuals" to jsonStringMapOf(draft.actuals.filterValues { it.isNotBlank() }),
                "notes" to jsonStringMapOf(draft.notes.filterValues { it.isNotBlank() })
            ).encode()
            PlatformLocalStorage.setItem(key, payload)
        }
    }

    fun clear(key: String) {
        runCatching { PlatformLocalStorage.removeItem(key) }
    }
}

private fun readStringMap(obj: JsonValue.Obj): Map<String, String> =
    obj.entries.mapNotNull { (key, value) ->
        (value as? JsonValue.Str)?.let { key to it.value }
    }.toMap()
