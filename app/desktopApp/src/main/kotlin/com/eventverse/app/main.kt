package com.eventverse.app

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "WeMade ERP — Sistem Manajemen Konveksi & Garmen",
    ) {
        App()
    }
}