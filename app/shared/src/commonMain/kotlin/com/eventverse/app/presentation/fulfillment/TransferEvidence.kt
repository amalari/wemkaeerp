package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.deal.pickLocalFile
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.SignaturePadState
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.launch

/**
 * Komponen bukti visual: pemilihan dan pengunggahan berkas bukti (kamera/galeri).
 */
@Composable
internal fun EvidencePhotoField(
    label: String,
    uploadedKey: String?,
    isUploading: Boolean,
    onPick: (fileName: String, mimeType: String, bytes: ByteArray) -> Unit
) {
    val scope = rememberCoroutineScope()
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ClayButton(
                text = if (isUploading) "Mengunggah..." else label,
                style = if (uploadedKey != null) ClayButtonStyle.Success else ClayButtonStyle.Secondary,
                enabled = !isUploading,
                onClick = {
                    scope.launch {
                        val picked = pickLocalFile(accept = "image/*") ?: return@launch
                        onPick(picked.fileName, picked.mimeType, picked.bytes)
                    }
                }
            )
            if (uploadedKey != null) {
                ClayBadge(text = "Bukti terlampir", tint = WeMadeColors.Success)
            }
        }
    }
}

/**
 * Payload TTD yang disimpan sebagai `signatureKey`: vektor goresan ternormalisasi. Kolomnya
 * TEXT, jadi tanda tangan tersimpan utuh tanpa putaran unggah berkas tambahan.
 */
internal fun SignaturePadState.toSignaturePayload(): String {
    val all = strokes + listOf(current)
    val strokesJson = all
        .filter { it.size > 1 }
        .joinToString(",") { stroke ->
            stroke.joinToString(";", "[", "]") { point -> "[${point.x},${point.y}]" }
        }
    return """{"v":1,"strokes":[$strokesJson]}"""
}
