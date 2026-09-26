package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.domain.workqueue.WorkStationCatalog
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import kotlinx.datetime.LocalDate

/**
 * Menambah harga layanan vendor. Layanan dipilih dari templat proses opsional (Sablon, Bordir,
 * Laundry) supaya kodenya sama dengan proses di alur SPK — itulah yang membuat harga terisi
 * otomatis saat penunjukan. Kode lain tetap bisa diketik untuk proses kustom tenant.
 */
@Composable
internal fun VendorRateDialog(
    vendor: Vendor,
    today: LocalDate,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (VendorServiceRate) -> Unit
) {
    val templates = remember { WorkStationCatalog.optionalStations() }
    var serviceCode by remember { mutableStateOf(templates.firstOrNull()?.code?.value ?: "") }
    var serviceName by remember { mutableStateOf(templates.firstOrNull()?.displayName ?: "") }
    var priceText by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(VendorPriceUnit.PER_PIECE) }
    var minQtyText by remember { mutableStateOf("") }
    var effectiveText by remember { mutableStateOf(today.toString()) }

    val effectiveFrom = runCatching { LocalDate.parse(effectiveText.trim()) }.getOrNull()
    val price = priceText.toLongOrNull()

    VendorDialogFrame(
        title = "Harga Layanan — ${vendor.name.value}",
        subtitle = "Harga lama pada layanan & satuan yang sama otomatis ditutup di tanggal mulai berlaku harga baru.",
        confirmText = "Simpan Harga",
        confirmEnabled = serviceCode.isNotBlank() && serviceName.isNotBlank() && price != null && effectiveFrom != null,
        isSubmitting = isSubmitting,
        onDismiss = onDismiss,
        errorMessage = errorMessage,
        onConfirm = {
            if (price != null && effectiveFrom != null) {
                onSave(
                    VendorServiceRate(
                        serviceCode = VendorServiceRate.normalizeCode(serviceCode),
                        serviceName = serviceName.trim(),
                        priceIdr = price,
                        unit = unit,
                        minQuantity = minQtyText.toIntOrNull() ?: 0,
                        effectiveFrom = effectiveFrom
                    )
                )
            }
        }
    ) {
        VendorFieldLabel("Layanan")
        ClayFlowRow(spacing = ClaySpacing.Xs) {
            templates.forEach { spec ->
                ClayChoiceChip(
                    text = spec.displayName,
                    selected = serviceCode.equals(spec.code.value, ignoreCase = true),
                    onClick = {
                        serviceCode = spec.code.value
                        serviceName = spec.displayName
                    }
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            ClayTextField(
                value = serviceCode,
                onValueChange = { serviceCode = it.uppercase().filter { c -> c.isLetterOrDigit() || c == '_' }.take(64) },
                label = "Kode proses",
                modifier = Modifier.weight(1f)
            )
            ClayTextField(value = serviceName, onValueChange = { serviceName = it }, label = "Nama layanan", modifier = Modifier.weight(1.5f))
        }
        ClayTextField(
            value = priceText,
            onValueChange = { priceText = it.filter(Char::isDigit).take(12) },
            label = "Harga (Rp) *",
            placeholder = "3500",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )
        VendorUnitPicker(selected = unit, onSelect = { unit = it })
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            ClayTextField(
                value = minQtyText,
                onValueChange = { minQtyText = it.filter(Char::isDigit).take(6) },
                label = "Min order (pcs)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = effectiveText,
                onValueChange = { effectiveText = it.take(10) },
                label = "Berlaku mulai (YYYY-MM-DD)",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/** Pemilih satuan harga — dipakai dialog harga dan dialog penunjukan. */
@Composable
internal fun VendorUnitPicker(selected: VendorPriceUnit, onSelect: (VendorPriceUnit) -> Unit) {
    VendorFieldLabel("Satuan tagihan")
    ClayFlowRow(spacing = ClaySpacing.Xs) {
        VendorPriceUnit.entries.forEach { option ->
            ClayChoiceChip(text = option.displayName, selected = option == selected, onClick = { onSelect(option) })
        }
    }
}
