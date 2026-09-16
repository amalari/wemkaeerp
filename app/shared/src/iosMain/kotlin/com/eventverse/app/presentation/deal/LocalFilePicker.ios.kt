package com.eventverse.app.presentation.deal

/**
 * Fase ini: picker iOS belum terpasang.
 *
 * Memilih foto di iOS memerlukan `UIDocumentPickerViewController`/`PHPickerViewController`
 * beserta presentasi dari root view controller aplikasi. UI menampilkan pesan jelas sampai
 * wiring itu tersedia.
 */
actual suspend fun pickLocalFile(accept: String): PickedLocalFile? = null
