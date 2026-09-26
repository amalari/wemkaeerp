package com.eventverse.app.presentation.deal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Picker desktop: AWT [FileDialog] dengan filter ekstensi yang diturunkan dari [accept]. */
actual suspend fun pickLocalFile(accept: String): PickedLocalFile? = withContext(Dispatchers.IO) {
    val allowedExtensions = accept.split(',')
        .mapNotNull { mime -> mime.substringAfter('/', "").trim().takeIf { it.isNotEmpty() } }
        .map { it.replace("jpg", "jpeg") }
        .toSet()
        .ifEmpty { setOf("png", "jpeg", "webp") }

    val dialog = FileDialog(null as Frame?, "Pilih foto desain", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        val extension = name.substringAfterLast('.', "").lowercase()
        extension == "jpg" || extension in allowedExtensions
    }
    dialog.isVisible = true

    val picked: File? = dialog.directory?.let { dir -> dialog.file?.let { name -> File(dir, name) } }
    picked?.takeIf { it.exists() }?.let { file ->
        val mime = when (file.extension.lowercase()) {
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        PickedLocalFile(fileName = file.name, mimeType = mime, bytes = file.readBytes())
    }
}
