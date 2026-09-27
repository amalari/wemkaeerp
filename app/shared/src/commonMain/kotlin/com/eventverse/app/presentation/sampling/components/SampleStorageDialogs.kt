package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.storage.SampleStorageStatus
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayIconButton
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog masuk penyimpanan: barang dari meja pengemasan ditaruh di tempat yang disebut
 * namanya. Penerima simpan tidak diketik — server mengambilnya dari akun yang menekan tombol,
 * karena orang itulah yang ditanyai kalau barangnya dicari.
 */
@Composable
fun StoreSampleDialog(
    order: SamplingOrder,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (locationLabel: String, qtyPcs: Int) -> Unit
) {
    var location by remember(order.id) { mutableStateOf("") }
    var qtyText by remember(order.id) { mutableStateOf(order.sampleQuantity.toString()) }
    val qty = qtyText.trim().toIntOrNull()

    StorageDialogShell(
        title = "SIMPAN BARANG",
        subtitle = "${order.spkNumber.value} • ${order.styleName}",
        error = error,
        onDismiss = onDismiss,
        confirmText = if (isSubmitting) "Menyimpan..." else "Simpan",
        confirmStyle = ClayButtonStyle.Primary,
        confirmEnabled = !isSubmitting && location.isNotBlank() && qty != null && qty > 0,
        onConfirm = { onConfirm(location.trim(), qty ?: 0) }
    ) {
        Text(
            text = "Selesai kemas, barang disimpan dulu sampai seluruh SPK deal ini siap dikirim.",
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        ClayTextField(
            value = location,
            onValueChange = { location = it.take(80) },
            label = "Lokasi Penyimpanan",
            placeholder = "Contoh: Rak Packing A / Gudang Utama",
            modifier = Modifier.fillMaxWidth()
        )
        ClayTextField(
            value = qtyText,
            onValueChange = { qtyText = it.filter(Char::isDigit).take(5) },
            label = "Jumlah Disimpan (pcs)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        ClayBadge(
            text = "Penerima simpan: akun yang sedang login",
            tint = WeMadeColors.Info,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Dialog rilis kirim: barang keluar dari penyimpanan ke buyer. Bila SPK lain dalam deal yang
 * sama belum tersimpan, tombolnya berubah jadi "Kirim Parsial" dan alasannya wajib — keputusan
 * mengirim sebagian harus bisa ditanyakan balik nanti.
 */
@Composable
fun ReleaseFromStorageDialog(
    order: SamplingOrder,
    status: SampleStorageStatus?,
    isSubmitting: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onConfirm: (partialReason: String?) -> Unit
) {
    var reason by remember(order.id) { mutableStateOf("") }
    val isPartial = status != null && !status.isDealComplete

    StorageDialogShell(
        title = "RILIS KIRIM KE BUYER",
        subtitle = "${order.spkNumber.value} • ${order.clientName}",
        error = error,
        onDismiss = onDismiss,
        confirmText = when {
            isSubmitting -> "Memproses..."
            isPartial -> "Kirim Parsial"
            else -> "Rilis Kirim"
        },
        confirmStyle = if (isPartial) ClayButtonStyle.Accent else ClayButtonStyle.Primary,
        confirmEnabled = !isSubmitting && status != null && (!isPartial || reason.isNotBlank()),
        onConfirm = { onConfirm(reason.trim().takeIf { isPartial && it.isNotEmpty() }) }
    ) {
        val record = status?.record
        if (status == null) {
            Text(text = "Memuat data penyimpanan...", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
        } else {
            StorageInfoLine("Disimpan di", record?.location?.value ?: "-")
            StorageInfoLine("Penanggung jawab", record?.storedBy?.displayName ?: "-")
            StorageInfoLine("Jumlah", record?.let { "${it.qtyPcs} pcs" } ?: "-")
            StorageInfoLine("PIC kirim", "akun yang sedang login")
            ClayBadge(
                text = "${status.readyCount}/${status.total} SPK deal tersimpan",
                tint = if (isPartial) WeMadeColors.Warning else WeMadeColors.Success,
                dot = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (isPartial && status != null) {
            Text(
                text = "Belum masuk penyimpanan: ${status.pendingSpkNumbers.joinToString()}",
                fontSize = 12.sp,
                color = WeMadeColors.Warning
            )
            ClayTextField(
                value = reason,
                onValueChange = { reason = it },
                label = "Alasan Kirim Parsial",
                placeholder = "Contoh: Buyer minta ukuran S dikirim duluan untuk fitting",
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun StorageInfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
        Spacer(Modifier.width(ClaySpacing.Sm))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

@Composable
private fun StorageDialogShell(
    title: String,
    subtitle: String,
    error: String?,
    onDismiss: () -> Unit,
    confirmText: String,
    confirmStyle: ClayButtonStyle,
    confirmEnabled: Boolean,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(text = title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Primary)
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }
                content()
                if (!error.isNullOrBlank()) {
                    ClayStatusBanner(message = error, isError = true, onDismiss = {})
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ClayButton(text = "Batal", style = ClayButtonStyle.Secondary, onClick = onDismiss)
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayButton(text = confirmText, style = confirmStyle, enabled = confirmEnabled, onClick = onConfirm)
                }
            }
        }
    }
}
