package com.eventverse.app.presentation.deal

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

actual suspend fun pickPoFile(): PickedPoFile? = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Pilih berkas PO", FileDialog.LOAD)
    dialog.setFilenameFilter { _, name ->
        name.endsWith(".pdf", true) || name.endsWith(".png", true) ||
            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true) || name.endsWith(".webp", true)
    }
    dialog.isVisible = true
    val file: File? = dialog.directory?.let { dir -> dialog.file?.let { name -> File(dir, name) } }
    file?.takeIf { it.exists() }?.let { f ->
        val mime = when (f.extension.lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
        PickedPoFile(
            fileName = f.name,
            mimeType = mime,
            bytes = f.readBytes(),
            suggestedPoNumber = f.nameWithoutExtension.uppercase().replace(Regex("[^A-Z0-9]+"), "-").trim('-')
        )
    }
}

actual fun openInBrowser(url: String) {
    val osName = System.getProperty("os.name").lowercase()
    val command = when {
        osName.contains("mac") -> arrayOf("open", url)
        osName.contains("win") -> arrayOf("cmd", "/c", "start", "", url)
        else -> arrayOf("xdg-open", url)
    }
    Runtime.getRuntime().exec(command)
}
