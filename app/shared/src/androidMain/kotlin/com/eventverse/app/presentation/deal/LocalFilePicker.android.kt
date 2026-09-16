package com.eventverse.app.presentation.deal

/**
 * Fase ini: picker Android belum terpasang.
 *
 * Memilih berkas di Android memerlukan `ActivityResultLauncher`/`registerForActivityResult`
 * yang harus didaftarkan dari `MainActivity` di `androidApp` dan diteruskan ke lapisan shared.
 * Selama belum ada, UI menampilkan pesan jelas (bukan tombol diam yang terasa rusak).
 */
actual suspend fun pickLocalFile(accept: String): PickedLocalFile? = null
