package com.eventverse.app.presentation.deal

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Picker berkas untuk browser: membuat `<input type="file">` sementara, membiarkan pengguna
 * memilih, lalu mengembalikan isinya sebagai **data URL** lewat callback.
 *
 * Dipilih data URL, bukan ArrayBuffer, karena konversi typed-array berbeda antara JS dan Wasm;
 * data URL hanya `String` sehingga satu implementasi cukup untuk kedua target web.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(accept, cb) => {
        const input = document.createElement('input');
        input.type = 'file';
        input.accept = accept;
        input.style.display = 'none';
        document.body.appendChild(input);
        const finish = (value) => {
            if (input.parentNode) { input.parentNode.removeChild(input); }
            cb(value);
        };
        input.onchange = () => {
            const file = input.files && input.files[0];
            if (!file) { finish(null); return; }
            const reader = new FileReader();
            reader.onload = () => finish(typeof reader.result === 'string' ? reader.result : null);
            reader.onerror = () => finish(null);
            reader.readAsDataURL(file);
        };
        input.click();
    }"""
)
private external fun openFilePickerJs(accept: String, onLoaded: (String?) -> Unit)

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual suspend fun pickLocalFile(accept: String): PickedLocalFile? =
    suspendCancellableCoroutine { continuation ->
        openFilePickerJs(accept) { dataUrl ->
            if (continuation.isActive) {
                continuation.resume(dataUrl?.let { decodeDataUrlToPickedFile(it) })
            }
        }
    }
