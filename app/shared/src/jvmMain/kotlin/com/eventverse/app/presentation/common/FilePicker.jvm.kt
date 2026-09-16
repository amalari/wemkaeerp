package com.eventverse.app.presentation.common

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual suspend fun pickFile(extensions: List<String>): PickedFile? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Pilih berkas", FileDialog.LOAD)
    if (extensions.isNotEmpty()) {
        dialog.setFilenameFilter { _, name ->
            extensions.any { name.endsWith(".$it", ignoreCase = true) }
        }
    }
    dialog.isVisible = true

    val file: File? = dialog.directory?.let { dir -> dialog.file?.let { name -> File(dir, name) } }
    file?.takeIf { it.exists() }?.let { picked ->
        PickedFile(
            fileName = picked.name,
            mimeType = mimeTypeOf(picked.extension.lowercase()),
            bytes = picked.readBytes()
        )
    }
}

private fun mimeTypeOf(extension: String): String = when (extension) {
    "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    "png" -> "image/png"
    "jpg", "jpeg" -> "image/jpeg"
    "webp" -> "image/webp"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}
