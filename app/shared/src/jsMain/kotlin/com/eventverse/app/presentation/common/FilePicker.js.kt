package com.eventverse.app.presentation.common

/**
 * Belum ada picker di target ini — sejalan dengan `pickPoFile` di fitur Deal.
 * UI menampilkan perintah impor massal lewat task Gradle sebagai gantinya.
 */
actual suspend fun pickFile(extensions: List<String>): PickedFile? = null
