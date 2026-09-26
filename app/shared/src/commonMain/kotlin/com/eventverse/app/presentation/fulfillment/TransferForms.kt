package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.HandoverProof
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.SackRoute
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.presentation.designsystem.ClayBadge
import androidx.compose.material3.HorizontalDivider
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.SignaturePadState
import com.eventverse.app.presentation.designsystem.SignaturePad
import com.eventverse.app.presentation.designsystem.rememberSignaturePadState
import com.eventverse.app.presentation.deal.pickLocalFile
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.domain.fulfillment.WeightKg
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/**
 * Kumpulan form isian ringkas — filosofi "tanda tangan saja": setiap aksi cukup beberapa isian
 * dan satu tanda tangan di kartu karung yang sama, bukan layar terpisah.
 */

@Composable
private fun FormCaption(text: String) {
    Text(text = text, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
}

/** Pilih foto bukti dari kamera/galeri perangkat, lalu unggah; key-nya dipakai form. */
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
 * Form pengajuan antar wadah — muncul setelah karung/bundel di-scan.
 *
 * Bentuknya ditentukan [HandoverMode] rute yang dipilih, dan karena itu **berubah saat chip
 * rute diklik**: rute lewat meja admin meminta timbangan dan fotonya, rute antar langsung
 * hanya meminta hitungan pcs. Perubahan bentuk itu sendiri yang menjelaskan modenya — lebih
 * mudah dipercaya daripada satu baris keterangan yang gampang dilewati mata.
 */
@Composable
internal fun SubmitSackForm(
    sackPayload: String,
    isSubmitting: Boolean,
    state: FulfillmentUiState,
    onSubmit: (
        leg: SackRoute,
        weight: String?,
        photoKey: String?,
        requestedBy: String,
        declaredPcs: Int?
    ) -> Unit,
    onUploadEvidence: (fileName: String, mimeType: String, bytes: ByteArray, onDone: (Result<String>) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    // Kartu bundel (prefix B) tidak bisa dikirim lewat meja admin — ia harus dituang ke karung
    // dulu. Rute yang menuntut itu tetap ditampilkan tapi diredupkan: chip yang hilang membuat
    // orang mengira sistemnya rusak, sedangkan chip mati yang menjelaskan diri bisa dipahami.
    val isBundleCard = TraceCodec.parse(sackPayload)?.tier == TraceTier.BUNDLE
    val allowedRoutes = state.routesAccepting(isClosedSack = !isBundleCard)

    var leg by remember(allowedRoutes) {
        mutableStateOf(allowedRoutes.firstOrNull() ?: SackRoute.entries.first())
    }
    var weight by remember { mutableStateOf("") }
    var photoKey by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pcs by remember { mutableStateOf("") }

    val mode = state.modeFor(leg)
    val lewatMejaAdmin = mode == HandoverMode.ADMIN_HUB
    val pcsParsed = pcs.trim().toIntOrNull()?.takeIf { it > 0 }
    val buktiLengkap = if (lewatMejaAdmin) {
        WeightKg.parse(weight) != null && !photoKey.isNullOrBlank()
    } else {
        pcsParsed != null
    }
    val canSubmit = buktiLengkap && name.isNotBlank() && leg in allowedRoutes && !isSubmitting

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = if (lewatMejaAdmin) "Ajukan Antar Karung" else "Antar ke Divisi Tujuan",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            SackRoute.entries.forEach { candidate ->
                val enabled = candidate in allowedRoutes
                ClayButton(
                    text = candidate.displayName,
                    style = if (leg == candidate) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    enabled = enabled,
                    onClick = { leg = candidate }
                )
            }
        }
        if (isBundleCard && allowedRoutes.size < SackRoute.entries.size) {
            FormCaption("Rute yang diredupkan lewat meja admin — tuang dulu bundel ini ke karung.")
        }

        if (lewatMejaAdmin) {
            ClayTextField(
                value = weight,
                onValueChange = { weight = it },
                label = "Berat timbangan (kg)",
                placeholder = "contoh: 8,35"
            )
            EvidencePhotoField(
                label = "Foto timbangan",
                uploadedKey = photoKey,
                isUploading = uploading,
                onPick = { fileName, mime, bytes ->
                    uploading = true
                    onUploadEvidence(fileName, mime, bytes) { result ->
                        uploading = false
                        result.onSuccess { photoKey = it }
                    }
                }
            )
            FormCaption("Wajib: timbang dulu, foto angkanya, baru ajukan.")
        } else {
            ClayTextField(
                value = pcs,
                onValueChange = { pcs = it },
                label = "Jumlah pcs",
                placeholder = "contoh: 120"
            )
            FormCaption("Diantar langsung tanpa ACC admin — hitungan pcs yang dicocokkan di tujuan.")
        }

        ClayTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nama petugas",
            placeholder = "nama Anda"
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(text = "Batal", style = ClayButtonStyle.Ghost, onClick = onCancel)
            ClayButton(
                text = if (lewatMejaAdmin) "Ajukan Antar" else "Antar Sekarang",
                style = ClayButtonStyle.Primary,
                enabled = canSubmit,
                onClick = {
                    if (lewatMejaAdmin) {
                        onSubmit(leg, weight, photoKey.orEmpty(), name, null)
                    } else {
                        onSubmit(leg, null, null, name, pcsParsed)
                    }
                }
            )
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

/** Form ACC/tolak admin produksi — TTD wajib untuk ACC, alasan wajib untuk tolak. */
@Composable
internal fun ApproveSection(
    isSubmitting: Boolean,
    onDecide: (approved: Boolean, approverName: String, signatureKey: String, reason: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("") }
    var rejecting by remember { mutableStateOf(false) }
    val pad = rememberSignaturePadState()

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.4f))
        Text("Keputusan Admin Produksi", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        ClayTextField(
            value = name,
            onValueChange = { name = it },
            label = "Nama Anda",
            placeholder = "nama admin"
        )
        if (rejecting) {
            ClayTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan penolakan",
                placeholder = "mis. karung size M tapi isinya size L"
            )
        } else {
            SignaturePad(state = pad, onCleared = {}, modifier = Modifier.fillMaxWidth())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = if (rejecting) "Kembali ke ACC" else "Tolak",
                style = if (rejecting) ClayButtonStyle.Ghost else ClayButtonStyle.Danger,
                onClick = { rejecting = !rejecting }
            )
            ClayButton(
                text = if (rejecting) "Kirim Penolakan" else "ACC dan Izinkan Berangkat",
                style = if (rejecting) ClayButtonStyle.Danger else ClayButtonStyle.Success,
                enabled = name.isNotBlank() &&
                    (if (rejecting) reason.isNotBlank() else !pad.isEmpty) &&
                    !isSubmitting,
                onClick = { onDecide(!rejecting, name, pad.toSignaturePayload(), reason) }
            )
        }
    }
}

/** Form penerimaan di ujung tujuan — dua jalur: diterima langsung (TTD), atau via resi kurir. */
@Composable
internal fun ReceiveSection(
    declaredPcs: Int,
    isSubmitting: Boolean,
    onReceive: (proof: HandoverProof, receivedWeightKg: String?, receivedPcs: Int?, recordedBy: String) -> Unit,
    onUploadEvidence: (fileName: String, mimeType: String, bytes: ByteArray, onDone: (Result<String>) -> Unit) -> Unit
) {
    var viaCourier by remember { mutableStateOf(false) }
    var receiverName by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var pcs by remember { mutableStateOf("") }
    var photoKey by remember { mutableStateOf<String?>(null) }
    var carrier by remember { mutableStateOf("") }
    var resi by remember { mutableStateOf("") }
    var uploading by remember { mutableStateOf(false) }
    val pad = rememberSignaturePadState()

    val weightValue = WeightKg.parse(weight)
    val canSubmit = !isSubmitting && !photoKey.isNullOrBlank() &&
        (if (viaCourier) carrier.isNotBlank() && resi.isNotBlank() && weightValue != null
        else receiverName.isNotBlank() && !pad.isEmpty)

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.4f))
        Text("Catat Penerimaan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        FormCaption("Deklarasi: $declaredPcs pcs. Penerima tidak perlu punya akun — cukup tanda tangan di layar ini.")
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Diterima Langsung",
                style = if (!viaCourier) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                onClick = { viaCourier = false }
            )
            ClayButton(
                text = "Via Kurir / Vendor",
                style = if (viaCourier) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                onClick = { viaCourier = true }
            )
        }
        if (viaCourier) {
            ClayTextField(value = carrier, onValueChange = { carrier = it }, label = "Ekspedisi / vendor", placeholder = "mis. JNE")
            ClayTextField(value = resi, onValueChange = { resi = it }, label = "Nomor resi", placeholder = "JNE1234567890")
            ClayTextField(value = weight, onValueChange = { weight = it }, label = "Berat resi (kg, eksak sampai koma)", placeholder = "contoh: 8,35")
            FormCaption("Salin persis angka yang tercetak di resi — jangan dibulatkan.")
        } else {
            ClayTextField(value = receiverName, onValueChange = { receiverName = it }, label = "Nama penerima", placeholder = "siapa yang menerimanya")
            ClayTextField(value = weight, onValueChange = { weight = it }, label = "Timbangan saat diterima (opsional, kg)", placeholder = "contoh: 8,20")
            ClayTextField(value = pcs, onValueChange = { pcs = it }, label = "Jumlah pcs fisik (opsional)", placeholder = declaredPcs.toString())
            SignaturePad(state = pad, onCleared = {}, modifier = Modifier.fillMaxWidth())
        }
        EvidencePhotoField(
            label = if (viaCourier) "Foto resi" else "Foto timbangan",
            uploadedKey = photoKey,
            isUploading = uploading,
            onPick = { fileName, mime, bytes ->
                uploading = true
                onUploadEvidence(fileName, mime, bytes) { result ->
                    uploading = false
                    result.onSuccess { photoKey = it }
                }
            }
        )
        ClayButton(
            text = "Terima Karung",
            style = ClayButtonStyle.Success,
            enabled = canSubmit,
            onClick = {
                if (viaCourier) {
                    onReceive(
                        HandoverProof.CourierShipment(
                            carrier = carrier,
                            trackingNumber = resi,
                            chargeableWeightKg = weightValue ?: WeightKg(0.0),
                            evidencePhotoKey = photoKey.orEmpty()
                        ),
                        weightValue?.value?.toString(), pcs.toIntOrNull(), receiverName.ifBlank { "Kurir internal" }
                    )
                } else {
                    onReceive(
                        HandoverProof.ReceiverHandover(
                            receiverName = receiverName,
                            signatureKey = pad.toSignaturePayload(),
                            evidencePhotoKey = photoKey.orEmpty()
                        ),
                        weight, pcs.toIntOrNull(), receiverName
                    )
                }
            }
        )
    }
}

/** Form ajukan ulang karung yang ditolak — butuh bukti timbang terbaru. */
@Composable
internal fun ResubmitSection(
    isSubmitting: Boolean,
    onResubmit: (weight: String, photoKey: String, requestedBy: String) -> Unit,
    onUploadEvidence: (fileName: String, mimeType: String, bytes: ByteArray, onDone: (Result<String>) -> Unit) -> Unit
) {
    var weight by remember { mutableStateOf("") }
    var photoKey by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        HorizontalDivider(color = WeMadeColors.Outline.copy(alpha = 0.4f))
        Text("Ajukan Ulang Setelah Diperiksa", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        ClayTextField(value = weight, onValueChange = { weight = it }, label = "Berat timbangan terbaru (kg)", placeholder = "contoh: 8,35")
        EvidencePhotoField(
            label = "Foto timbangan terbaru",
            uploadedKey = photoKey,
            isUploading = uploading,
            onPick = { fileName, mime, bytes ->
                uploading = true
                onUploadEvidence(fileName, mime, bytes) { result ->
                    uploading = false
                    result.onSuccess { photoKey = it }
                }
            }
        )
        ClayTextField(value = name, onValueChange = { name = it }, label = "Nama kurir / petugas", placeholder = "nama Anda")
        ClayButton(
            text = "Ajukan Ulang",
            style = ClayButtonStyle.Primary,
            enabled = WeightKg.parse(weight) != null && !photoKey.isNullOrBlank() && name.isNotBlank() && !isSubmitting,
            onClick = { onResubmit(weight, photoKey.orEmpty(), name) }
        )
    }
}
