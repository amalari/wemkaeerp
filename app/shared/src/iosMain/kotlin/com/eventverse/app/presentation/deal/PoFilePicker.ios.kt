package com.eventverse.app.presentation.deal

actual suspend fun pickPoFile(): PickedPoFile? = null

actual fun openInBrowser(url: String) {
    // Fase 1: buka URL eksternal di iOS diserahkan ke lapisan iosApp (SFSafariViewController).
}
