package com.eventverse.app.presentation.deal.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.eventverse.app.presentation.deal.decodeBase64
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.readBytes
import org.jetbrains.compose.resources.decodeToImageBitmap

/**
 * Satu klien HTTP dipakai bersama untuk seluruh thumbnail mockup — membuat klien baru per
 * kartu berarti membuka connection pool baru per desain.
 */
private val mockupHttpClient by lazy { HttpClient() }

/**
 * Memuat satu foto mockup menjadi [ImageBitmap].
 *
 * Dua bentuk referensi yang didukung, keduanya berasal dari server:
 * - `data:image/…;base64,…` — foto kecil yang disimpan inline (object storage belum dikonfigurasi)
 * - `https://…` — presigned URL yang dibuat server saat data dibaca
 *
 * Gagal memuat bukan kesalahan fatal: UI tinggal menampilkan placeholder, jadi error
 * dikembalikan sebagai `null`, bukan dilempar.
 */
suspend fun loadMockupBitmap(reference: String): ImageBitmap? = runCatching {
    val bytes = if (reference.startsWith("data:")) {
        decodeBase64(reference.substringAfter(',')) ?: return@runCatching null
    } else {
        mockupHttpClient.get(reference).readBytes()
    }
    bytes.decodeToImageBitmap()
}.getOrNull()

/** Versi composable: memuat ulang hanya ketika [reference] berubah. */
@Composable
fun rememberMockupBitmap(reference: String?): ImageBitmap? {
    var bitmap by remember(reference) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(reference) {
        bitmap = reference?.let { loadMockupBitmap(it) }
    }
    return bitmap
}
