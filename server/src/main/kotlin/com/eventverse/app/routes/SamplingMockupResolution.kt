package com.eventverse.app.routes

import com.eventverse.app.domain.deal.storage.PoFileStorage
import com.eventverse.app.domain.sampling.SamplingOrder

/**
 * Mengganti key object storage di `knitSpec.mockupImageUrls` dengan presigned URL segar.
 *
 * DB menyimpan OBJECT KEY, bukan presigned URL (yang kedaluwarsa). URL segar di-resolve saat
 * data dibaca, jadi seluruh endpoint yang mengembalikan `SamplingOrder` ke client web wajib
 * melewatkannya lewat sini — tanpa ini UI menerima key mentah dan gambarnya broken/403.
 *
 * Dipakai bersama oleh [DealRoutes] (endpoint deal yang menyertakan sampling) dan
 * [samplingRoutes] (list & detail SPK pada modul sampling).
 *
 * Entri yang sudah berupa URL absolut / data URI dibiarkan; entri yang gagal di-resolve
 * (storage belum dikonfigurasi atau object hilang) dibuang supaya UI tidak menerima tautan mati.
 */
internal suspend fun withResolvedMockups(
    order: SamplingOrder,
    storage: PoFileStorage?
): SamplingOrder {
    val entries = order.knitSpec.mockupImageUrls
    if (entries.isEmpty()) return order
    val resolved = entries.mapNotNull { entry ->
        val prefix = when {
            entry.startsWith("front:") -> "front:"
            entry.startsWith("back:") -> "back:"
            else -> ""
        }
        val key = entry.removePrefix(prefix)
        val url = when {
            key.startsWith("http") -> key
            key.startsWith("data:") -> key
            storage?.isConfigured == true -> storage.downloadUrl(key).getOrNull()
            else -> null
        }
        url?.let { prefix + it }
    }
    return order.copy(knitSpec = order.knitSpec.copy(mockupImageUrls = resolved))
}
