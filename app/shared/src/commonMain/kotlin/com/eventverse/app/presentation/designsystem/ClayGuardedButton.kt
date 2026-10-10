package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tombol clay yang, ketika dikunci, **mengatakan alasannya**.
 *
 * Tombol nonaktif tanpa keterangan adalah salah satu hal paling membingungkan di ERP: pengguna
 * tidak bisa membedakan "belum boleh", "belum siap", dan "rusak". [lockedHint] mengubah ketiganya
 * menjadi satu kalimat yang bisa dibawa ke admin.
 *
 * Netral terhadap domain sesuai Kontrak 6: ia menerima [enabled] dan sepotong teks, bukan tingkat
 * wewenang. Penerjemahan dari wewenang ke kalimat itu urusan lapisan fitur — lihat
 * `RbacGuardedButton`.
 */
@Composable
fun ClayGuardedButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    style: ClayButtonStyle = ClayButtonStyle.Primary,
    lockedHint: String? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 9.dp),
    maxLines: Int = Int.MAX_VALUE,
    leading: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = modifier,
        horizontalAlignment = androidx.compose.ui.Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        ClayButton(
            text = text,
            onClick = onClick,
            enabled = enabled,
            style = style,
            contentPadding = contentPadding,
            maxLines = maxLines,
            leading = leading
        )

        if (!enabled && lockedHint != null) {
            // Lebarnya dibatasi supaya keterangan tidak ikut melebarkan kolom tombol.
            //
            // Tanpa batas ini, kalimat alasan yang panjang menentukan lebar seluruh kolom, dan
            // deretan tombol di toolbar berjarak tidak rata — persis gejala yang terlihat saat
            // layar ini pertama kali dijalankan.
            Text(
                text = lockedHint,
                modifier = Modifier.widthIn(max = 160.dp),
                fontSize = 10.sp,
                lineHeight = 13.sp,
                color = WeMadeColors.OnSurfaceDisabled,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
