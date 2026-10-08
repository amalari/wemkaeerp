package com.eventverse.app.presentation.fulfillment

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.fulfillment.HandoverMode
import com.eventverse.app.domain.fulfillment.HandoverRouteCode
import com.eventverse.app.domain.fulfillment.WeightKg
import com.eventverse.app.domain.traceability.TraceCodec
import com.eventverse.app.domain.traceability.TraceTier
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Kumpulan form isian ringkas — filosofi "tanda tangan saja": setiap aksi cukup beberapa isian
 * dan satu tanda tangan di kartu karung yang sama, bukan layar terpisah.
 */

@Composable
internal fun FormCaption(text: String) {
    Text(text = text, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
}

/**
 * Form pengajuan antar wadah — muncul setelah karung/bundel di-scan.
 *
 * Bentuknya ditentukan [HandoverMode] rute yang dipilih, dan karena itu **berubah saat chip
 * rute diklik**: rute lewat meja admin meminta timbangan dan fotonya, rute antar langsung
 * hanya meminta hitungan pcs. Rute diambil dinamis dari konfigurasi data tenant.
 */
@Composable
internal fun SubmitSackForm(
    sackPayload: String,
    isSubmitting: Boolean,
    state: FulfillmentUiState,
    onSubmit: (
        routeCode: HandoverRouteCode,
        weight: String?,
        photoKey: String?,
        requestedBy: String,
        declaredPcs: Int?
    ) -> Unit,
    onUploadEvidence: (fileName: String, mimeType: String, bytes: ByteArray, onDone: (Result<String>) -> Unit) -> Unit,
    onCancel: () -> Unit
) {
    val isBundleCard = TraceCodec.parse(sackPayload)?.tier == TraceTier.BUNDLE
    val allowedRoutes = state.routesAccepting(isClosedSack = !isBundleCard)
    val allActiveRoutes = state.effectiveRoutes

    var selectedRouteCode by remember(allowedRoutes) {
        mutableStateOf(allowedRoutes.firstOrNull()?.route?.code ?: allActiveRoutes.firstOrNull()?.route?.code)
    }
    var weight by remember { mutableStateOf("") }
    var photoKey by remember { mutableStateOf<String?>(null) }
    var uploading by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var pcs by remember { mutableStateOf("") }

    val currentRouteCode = selectedRouteCode
    val mode = currentRouteCode?.let { state.modeFor(it) } ?: HandoverMode.ADMIN_HUB
    val lewatMejaAdmin = mode == HandoverMode.ADMIN_HUB
    val pcsParsed = pcs.trim().toIntOrNull()?.takeIf { it > 0 }
    val buktiLengkap = if (lewatMejaAdmin) {
        WeightKg.parse(weight) != null && !photoKey.isNullOrBlank()
    } else {
        pcsParsed != null
    }
    val isSelectedAllowed = allowedRoutes.any { it.route.code == currentRouteCode }
    val canSubmit = buktiLengkap && name.isNotBlank() && isSelectedAllowed && !isSubmitting && currentRouteCode != null

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        Text(
            text = if (lewatMejaAdmin) "Ajukan Antar Karung" else "Antar ke Divisi Tujuan",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )

        if (allActiveRoutes.isEmpty()) {
            FormCaption("Belum ada rute serah terima aktif untuk tenant ini. Atur rute terlebih dahulu.")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                allActiveRoutes.forEach { candidateSetting ->
                    val candidate = candidateSetting.route
                    val enabled = allowedRoutes.any { it.route.code == candidate.code }
                    ClayButton(
                        text = candidate.label,
                        style = if (currentRouteCode == candidate.code) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        enabled = enabled,
                        onClick = { selectedRouteCode = candidate.code }
                    )
                }
            }
            if (isBundleCard && allowedRoutes.size < allActiveRoutes.size) {
                FormCaption("Rute yang diredupkan lewat meja admin — tuang dulu bundel ini ke karung.")
            }
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
                    val code = currentRouteCode ?: return@ClayButton
                    if (lewatMejaAdmin) {
                        onSubmit(code, weight, photoKey.orEmpty(), name, null)
                    } else {
                        onSubmit(code, null, null, name, pcsParsed)
                    }
                }
            )
        }
    }
}
