package com.eventverse.app.presentation.traceability

import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Melaporkan apakah kamera bisa dipakai, dan kalau tidak, kenapa.
 *
 * `isSecureContext` diperiksa lebih dulu dan terpisah karena inilah jebakan yang paling mahal:
 * di jaringan pabrik, aplikasi sering diakses lewat `http://192.168.x.x`, dan di sana `getUserMedia`
 * tidak pernah menyala sementara browser tidak memberi pesan apa pun. Tanpa pemeriksaan ini,
 * gejalanya di lantai adalah "tombol kameranya rusak" — dan yang sebenarnya kurang cuma HTTPS.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """() => {
        if (typeof window === 'undefined') { return 'NOT_ON_THIS_PLATFORM'; }
        if (!window.isSecureContext) { return 'INSECURE_CONTEXT'; }
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) { return 'UNSUPPORTED_BROWSER'; }
        if (typeof window.BarcodeDetector === 'undefined') { return 'UNSUPPORTED_BROWSER'; }
        return 'AVAILABLE';
    }"""
)
private external fun scannerAvailabilityJs(): String

/**
 * Membuka kamera belakang layar penuh, memindai tiap frame, lalu menutup sendiri.
 *
 * Trek kamera dihentikan di `cleanup` pada semua jalur keluar — termasuk saat pengguna membatalkan.
 * Trek yang lupa dihentikan membuat lampu kamera HP tetap menyala setelah layar ditutup, dan operator
 * yang melihat itu akan menutup seluruh aplikasi.
 */
@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
@JsFun(
    """(cb) => {
        let done = false;
        let stream = null;
        const overlay = document.createElement('div');
        overlay.setAttribute('style',
            'position:fixed;inset:0;z-index:99999;background:#000;display:flex;' +
            'flex-direction:column;align-items:center;justify-content:center;');

        const video = document.createElement('video');
        video.setAttribute('playsinline', 'true');
        video.setAttribute('style', 'max-width:100%;max-height:80%;');
        overlay.appendChild(video);

        const cancel = document.createElement('button');
        cancel.textContent = 'Batal';
        cancel.setAttribute('style',
            'margin-top:24px;padding:14px 32px;font-size:18px;border:3px solid #fff;' +
            'border-radius:16px;background:#EA580C;color:#fff;font-weight:700;');
        overlay.appendChild(cancel);
        document.body.appendChild(overlay);

        const finish = (value) => {
            if (done) { return; }
            done = true;
            if (stream) { stream.getTracks().forEach((t) => t.stop()); }
            if (overlay.parentNode) { overlay.parentNode.removeChild(overlay); }
            cb(value);
        };
        cancel.onclick = () => finish(null);

        navigator.mediaDevices.getUserMedia({ video: { facingMode: 'environment' } })
            .then((s) => {
                stream = s;
                video.srcObject = s;
                return video.play();
            })
            .then(() => {
                const detector = new window.BarcodeDetector({ formats: ['qr_code'] });
                const tick = () => {
                    if (done) { return; }
                    detector.detect(video)
                        .then((codes) => {
                            if (codes && codes.length > 0) { finish(codes[0].rawValue); }
                            else { requestAnimationFrame(tick); }
                        })
                        .catch(() => requestAnimationFrame(tick));
                };
                requestAnimationFrame(tick);
            })
            .catch(() => finish(null));
    }"""
)
private external fun scanQrJs(onResult: (String?) -> Unit)

actual fun traceScannerAvailability(): TraceScannerAvailability =
    when (scannerAvailabilityJs()) {
        "AVAILABLE" -> TraceScannerAvailability.AVAILABLE
        "INSECURE_CONTEXT" -> TraceScannerAvailability.INSECURE_CONTEXT
        "UNSUPPORTED_BROWSER" -> TraceScannerAvailability.UNSUPPORTED_BROWSER
        else -> TraceScannerAvailability.NOT_ON_THIS_PLATFORM
    }

@OptIn(kotlin.js.ExperimentalWasmJsInterop::class)
actual suspend fun scanTraceCode(): String? {
    if (traceScannerAvailability() != TraceScannerAvailability.AVAILABLE) return null
    return suspendCancellableCoroutine { continuation ->
        scanQrJs { value ->
            if (continuation.isActive) continuation.resume(value)
        }
    }
}
