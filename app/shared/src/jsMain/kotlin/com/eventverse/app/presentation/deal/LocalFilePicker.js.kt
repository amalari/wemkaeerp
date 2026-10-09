package com.eventverse.app.presentation.deal

import kotlinx.browser.document
import kotlinx.coroutines.suspendCancellableCoroutine
import org.w3c.dom.HTMLInputElement
import org.w3c.files.FileReader
import kotlin.coroutines.resume

/**
 * Varian JS dari picker web.
 *
 * Perilakunya identik dengan target Wasm (buat `<input type="file">` sementara, baca berkasnya
 * sebagai **data URL**), tapi caranya berbeda: target Wasm memanggil JavaScript lewat `@JsFun`,
 * sedangkan target JS memakai binding DOM `kotlinx.browser`/`org.w3c` yang sudah tersedia
 * langsung — sama seperti `PlatformLocalStorage.js.kt` di modul ini. `@JsFun` sendiri adalah
 * anotasi khusus Kotlin/Wasm dan tidak ada di Kotlin/JS.
 */
actual suspend fun pickLocalFile(accept: String): PickedLocalFile? =
    suspendCancellableCoroutine { continuation ->
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = accept
        input.style.display = "none"
        document.body?.appendChild(input)

        // Dipanggil sekali saja: `onchange` tidak pernah terpicu kalau pengguna menekan Cancel,
        // jadi coroutine-nya diselesaikan lewat pembatalan, bukan lewat cabang kedua di sini.
        fun finish(dataUrl: String?, fileName: String?) {
            input.parentNode?.removeChild(input)
            if (continuation.isActive) {
                continuation.resume(dataUrl?.let { decodeDataUrlToPickedFile(it, fileName) })
            }
        }

        input.onchange = {
            val file = input.files?.item(0)
            if (file == null) {
                finish(null, null)
            } else {
                val reader = FileReader()
                reader.onload = { _ -> finish(reader.result as? String, file.name) }
                reader.onerror = { _ -> finish(null, null) }
                reader.readAsDataURL(file)
            }
        }

        continuation.invokeOnCancellation { input.parentNode?.removeChild(input) }
        input.click()
    }
