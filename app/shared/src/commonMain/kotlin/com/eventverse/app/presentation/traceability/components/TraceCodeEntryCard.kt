package com.eventverse.app.presentation.traceability.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.traceability.TraceScannerAvailability

/**
 * Entri kode: kamera bila tersedia, ketikan tangan selalu.
 *
 * Entri manual bukan cadangan darurat, ia jalur setara. Kartu di lantai rajut bisa sobek, kena uap,
 * atau dipegang orang yang HP-nya mati — dan kode 16 karakter berhuruf besar dengan check digit
 * selesai diketik dalam beberapa detik.
 */
@Composable
fun TraceCodeEntryCard(
    code: String,
    onCodeChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onScan: () -> Unit,
    scannerAvailability: TraceScannerAvailability,
    isBusy: Boolean,
    modifier: Modifier = Modifier
) {
    val isValid = TraceCodec.parse(code) != null

    ClayCard(modifier = modifier.fillMaxWidth()) {
        Text("Pindai atau Ketik Kode Kartu", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(ClaySpacing.Md))

        ClayTextField(
            value = TraceCodec.groupedRaw(code),
            onValueChange = { onCodeChange(it) },
            label = "Kode kartu",
            placeholder = "W1SB-0A34-KFT0-500C",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(ClaySpacing.Sm))
        Text(
            text = checksumHint(code, isValid),
            color = if (isValid) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
        )

        Spacer(Modifier.height(ClaySpacing.Lg))
        Row(
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayButton(
                text = if (isBusy) "Memuat…" else "Buka Kartu",
                onClick = onSubmit,
                enabled = isValid && !isBusy
            )
            if (scannerAvailability == TraceScannerAvailability.AVAILABLE) {
                ClayButton(
                    text = "Pindai Kamera",
                    onClick = onScan,
                    style = ClayButtonStyle.Secondary,
                    enabled = !isBusy
                )
            }
        }

        scannerUnavailableReason(scannerAvailability)?.let { reason ->
            Spacer(Modifier.height(ClaySpacing.Md))
            Text(reason, color = WeMadeColors.OnSurfaceMuted, textAlign = TextAlign.Start)
        }
    }
}

/**
 * Menjelaskan kenapa tombol kamera tidak ada, alih-alih menyembunyikannya tanpa kata.
 *
 * Yang paling penting adalah kalimat HTTPS: di jaringan pabrik, aplikasi sering dibuka lewat
 * `http://192.168.x.x`, dan di sana browser mematikan kamera tanpa pesan apa pun. Tanpa penjelasan
 * ini, gejalanya di lantai terbaca sebagai "aplikasinya rusak".
 */
private fun scannerUnavailableReason(availability: TraceScannerAvailability): String? = when (availability) {
    TraceScannerAvailability.AVAILABLE -> null
    TraceScannerAvailability.INSECURE_CONTEXT ->
        "Kamera tidak bisa dipakai karena halaman ini dibuka lewat http biasa. " +
            "Browser hanya mengizinkan kamera pada alamat https. Sementara ini, ketik kodenya."
    TraceScannerAvailability.UNSUPPORTED_BROWSER ->
        "Browser ini belum mendukung pemindaian QR (Safari dan Firefox belum). " +
            "Pakai Chrome di Android, atau ketik kodenya."
    TraceScannerAvailability.NOT_ON_THIS_PLATFORM ->
        "Pemindaian kamera tersedia di versi web. Di sini kodenya diketik."
}

private fun checksumHint(code: String, isValid: Boolean): String = when {
    code.isBlank() -> "Kode tercetak di bawah kotak QR pada kartu."
    code.length < TraceCodec.LENGTH -> "Kurang ${TraceCodec.LENGTH - code.length} karakter lagi."
    isValid -> "Kode terbaca sah."
    else -> "Kode belum cocok — periksa lagi, kemungkinan ada satu huruf yang tertukar."
}
