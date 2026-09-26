package com.eventverse.app.presentation.deal

actual suspend fun pickPoFile(): PickedPoFile? = null

actual fun openInBrowser(url: String) {
    // Tanpa akses Activity context dari lapisan shared; UI Android dapat menampilkan
    // URL-nya sendiri bila diperlukan (Custom Tab). Fase 1: no-op yang aman.
}
