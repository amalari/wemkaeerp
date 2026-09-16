package com.eventverse.app.presentation.deal

import kotlin.coroutines.resume
import kotlin.js.JsFun
import kotlinx.coroutines.suspendCancellableCoroutine

/** Varian JS dari picker web — isinya sengaja identik dengan target Wasm. */
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

actual suspend fun pickLocalFile(accept: String): PickedLocalFile? =
    suspendCancellableCoroutine { continuation ->
        openFilePickerJs(accept) { dataUrl ->
            if (continuation.isActive) {
                continuation.resume(dataUrl?.let { decodeDataUrlToPickedFile(it) })
            }
        }
    }
