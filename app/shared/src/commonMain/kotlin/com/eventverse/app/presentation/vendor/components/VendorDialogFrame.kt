package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/** Bingkai bersama tiga dialog Kontak Vendor: judul, isi yang bisa digulir, dan tombol Batal/Simpan. */
@Composable
internal fun VendorDialogFrame(
    title: String,
    subtitle: String?,
    confirmText: String,
    confirmEnabled: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    /** Penolakan server (nama ganda, Surat Jalan sudah terbit, …). Dialog menutupi banner layar, jadi pesannya tampil di sini. */
    errorMessage: String? = null,
    content: @Composable () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(0.95f).widthIn(max = 560.dp),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                if (subtitle != null) Text(subtitle, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                content()
                if (errorMessage != null) {
                    Text(errorMessage, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Error)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm, androidx.compose.ui.Alignment.End)
                ) {
                    ClayButton(text = "Batal", style = ClayButtonStyle.Ghost, onClick = onDismiss)
                    ClayButton(
                        text = if (isSubmitting) "Menyimpan…" else confirmText,
                        enabled = confirmEnabled && !isSubmitting,
                        onClick = onConfirm
                    )
                }
            }
        }
    }
}

/** Label kecil di atas grup chip pilihan. */
@Composable
internal fun VendorFieldLabel(text: String) {
    Text(text, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
}
