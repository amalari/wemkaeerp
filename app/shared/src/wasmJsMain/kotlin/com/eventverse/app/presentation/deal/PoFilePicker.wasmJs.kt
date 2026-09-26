package com.eventverse.app.presentation.deal

actual suspend fun pickPoFile(): PickedPoFile? = null

actual fun openInBrowser(url: String) {
    kotlinx.browser.window.open(url, "_blank")
}
