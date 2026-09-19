package com.eventverse.app.presentation.traceability

/**
 * Ketersediaan pemindai kamera pada perangkat yang sedang dipakai.
 *
 * Dibedakan dari sekadar "berhasil/gagal" karena alasan ketidaktersediaannya menentukan apa yang
 * layak ditampilkan. Yang paling sering menjebak: `getUserMedia` dan `BarcodeDetector` hanya hidup di
 * *secure context*. Web app yang dibuka lewat `http://192.168.x.x` di LAN pabrik tidak akan pernah
 * menyalakan kamera, dan browser tidak memberi pesan apa pun — layar hanya diam. Karena itu
 * [INSECURE_CONTEXT] dijawab terpisah, supaya UI bisa mengatakan apa adanya alih-alih menggantung.
 */
enum class TraceScannerAvailability {
    AVAILABLE,
    INSECURE_CONTEXT,
    UNSUPPORTED_BROWSER,
    NOT_ON_THIS_PLATFORM
}

/** Apakah kamera bisa dipakai di sini, dan kalau tidak, kenapa. */
expect fun traceScannerAvailability(): TraceScannerAvailability

/**
 * Membuka kamera dan menunggu satu QR terbaca.
 *
 * Mengembalikan null bila pengguna membatalkan **atau** platformnya tidak mendukung — UI wajib
 * memperlakukan keduanya sama: tidak ada hasil, entri manual tetap tersedia. Pemindaian selalu
 * pelengkap, tidak pernah satu-satunya jalan; kartu di lantai produksi bisa sobek, basah, atau
 * dipegang orang yang HP-nya mati.
 */
expect suspend fun scanTraceCode(): String?
