package com.eventverse.app.presentation.deal.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.delay

/** Jeda sebelum toaster menutup sendiri; klik pada pil menutup lebih cepat. */
private const val TOAST_AUTO_DISMISS_MILLIS = 4_000L

/**
 * Toaster mengambang untuk umpan balik aksi di dialog detail deal (terbit SPK sampling,
 * simpan desain, ganti tahap). Melayang di tengah-atas konten — bukan teks inline yang
 * menggeser layout — sehingga keberhasilan dan kegagalan bisa tampil berdampingan tanpa
 * mendorong isi tab. Auto-dismiss setelah [TOAST_AUTO_DISMISS_MILLIS]; klik untuk menutup.
 *
 * Kontrak desain: warna lewat token [WeMadeColors], bentuk lewat [ClayShapes] + `clayFlat`,
 * warna diteruskan ke pemanggil `Text` (tidak ditanam di TextStyle).
 */
@Composable
fun DealStatusToast(
    statusMessage: String?,
    error: String?,
    onDismissStatus: () -> Unit,
    onDismissError: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(statusMessage, error) {
        if (statusMessage != null || error != null) {
            delay(TOAST_AUTO_DISMISS_MILLIS)
            onDismissStatus()
            onDismissError()
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = statusMessage != null || error != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                statusMessage?.let { message ->
                    DealToastPill(
                        text = message,
                        background = WeMadeColors.SuccessBg,
                        tint = WeMadeColors.Success,
                        onClick = onDismissStatus
                    )
                }
                error?.let { message ->
                    DealToastPill(
                        text = message,
                        background = WeMadeColors.ErrorBg,
                        tint = WeMadeColors.Error,
                        onClick = onDismissError
                    )
                }
            }
        }
    }
}

/** Satu pil toast: latar wash peran (sukses/error) + outline tipis warna peran. */
@Composable
private fun DealToastPill(
    text: String,
    background: Color,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .widthIn(max = 560.dp)
            .clayFlat(shape = ClayShapes.Card, background = background, outline = tint)
            .clickable { onClick() }
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}
