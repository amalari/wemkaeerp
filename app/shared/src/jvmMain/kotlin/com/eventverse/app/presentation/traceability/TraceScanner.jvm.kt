package com.eventverse.app.presentation.traceability

/**
 * Belum ada pemindai kamera di target ini — sejalan dengan `pickFile`.
 * UI jatuh ke entri kode manual, yang memang selalu tersedia.
 */
actual fun traceScannerAvailability(): TraceScannerAvailability =
    TraceScannerAvailability.NOT_ON_THIS_PLATFORM

actual suspend fun scanTraceCode(): String? = null
