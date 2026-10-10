package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.vendor.VendorPriceUnit
import com.eventverse.app.domain.vendor.VendorQueueItem
import com.eventverse.app.infrastructure.api.VendorAssignmentInput
import com.eventverse.app.presentation.crm.components.formatRupiah
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.presentation.vendor.VendorOption
import kotlinx.datetime.LocalDate

/**
 * Admin produksi menunjuk vendor untuk satu proses Vendor Luar.
 *
 * Harga terisi dari daftar harga vendor terpilih dan boleh diubah (nego); label sumber harga
 * menunjukkan mana yang akan tercatat. Kebenarannya tetap diputuskan server.
 */
@Composable
internal fun VendorAssignDialog(
    item: VendorQueueItem,
    options: List<VendorOption>,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onAddVendor: () -> Unit,
    onConfirm: (VendorAssignmentInput) -> Unit
) {
    val need = item.need
    val initial = options.firstOrNull { it.vendor.id == item.assignment?.vendorId }
        ?: options.firstOrNull { it.listedRate != null }
        ?: options.firstOrNull()

    var selected by remember { mutableStateOf(initial) }
    var unit by remember { mutableStateOf(initial?.listedRate?.unit ?: VendorPriceUnit.PER_PIECE) }
    var priceText by remember { mutableStateOf(initial?.listedRate?.priceIdr?.toString() ?: "") }
    var pointsText by remember { mutableStateOf("1") }
    var qtyText by remember { mutableStateOf(need.quantityPcs.toString()) }
    var returnText by remember { mutableStateOf(item.assignment?.expectedReturnAt?.toString() ?: "") }
    var notes by remember { mutableStateOf("") }

    val price = priceText.toLongOrNull()
    val qty = qtyText.toIntOrNull()?.takeIf { it > 0 }
    val points = pointsText.toIntOrNull()?.takeIf { it > 0 } ?: 1
    val expectedReturn = returnText.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val returnValid = returnText.isBlank() || expectedReturn != null
    val listed = selected?.listedRate
    val isListedPrice = listed != null && listed.unit == unit && listed.priceIdr == price

    VendorDialogFrame(
        title = "Tunjuk Vendor - ${need.processName}",
        subtitle = "${need.subjectLabel} · ${need.clientName} · ${need.styleName}",
        confirmText = "Tunjuk Vendor",
        confirmEnabled = selected != null && price != null && qty != null && returnValid,
        isSubmitting = isSubmitting,
        onDismiss = onDismiss,
        errorMessage = errorMessage,
        onConfirm = {
            val vendor = selected ?: return@VendorDialogFrame
            onConfirm(
                VendorAssignmentInput(
                    subjectId = need.subjectId,
                    processCode = need.processCode,
                    vendorId = vendor.vendor.id.value,
                    pricePerUnitIdr = price,
                    unit = unit,
                    unitsPerPiece = points,
                    quantityPcs = qty,
                    expectedReturnAt = expectedReturn,
                    notes = notes
                )
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            VendorFieldLabel("Pilih vendor")
            ClayButton(text = "+ Tambah Vendor", style = ClayButtonStyle.Ghost, onClick = onAddVendor)
        }
        if (options.isEmpty()) {
            Text("Belum ada vendor aktif. Tambahkan kontak vendor lebih dulu.", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
        }
        options.forEach { option ->
            VendorOptionCard(
                option = option,
                isSelected = option.vendor.id == selected?.vendor?.id,
                onClick = {
                    selected = option
                    option.listedRate?.let {
                        unit = it.unit
                        priceText = it.priceIdr.toString()
                    }
                }
            )
        }

        VendorUnitPicker(selected = unit, onSelect = { unit = it })
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            ClayTextField(
                value = priceText,
                onValueChange = { priceText = it.filter(Char::isDigit).take(12) },
                label = "Harga / ${unit.shortLabel} (Rp)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = qtyText,
                onValueChange = { qtyText = it.filter(Char::isDigit).take(6) },
                label = "Jumlah (pcs)",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.weight(1f)
            )
            if (unit == VendorPriceUnit.PER_PRINT_POINT) {
                ClayTextField(
                    value = pointsText,
                    onValueChange = { pointsText = it.filter(Char::isDigit).take(2) },
                    label = "Titik / pcs",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(0.8f)
                )
            }
        }
        ClayTextField(
            value = returnText,
            onValueChange = { returnText = it.take(10) },
            label = if (returnValid) "Estimasi kembali (YYYY-MM-DD)" else "Estimasi kembali - format tanggal salah",
            focusColor = if (returnValid) WeMadeColors.Primary else WeMadeColors.Error,
            modifier = Modifier.fillMaxWidth()
        )
        ClayTextField(value = notes, onValueChange = { notes = it }, label = "Catatan untuk vendor", modifier = Modifier.fillMaxWidth())

        if (listed != null && qty != null && qty < listed.minQuantity) {
            Text(
                text = "Di bawah minimum order vendor (${listed.minQuantity} pcs) - pastikan vendor bersedia.",
                fontSize = 11.sp,
                color = WeMadeColors.Warning
            )
        }
        if (price != null && qty != null) {
            TotalPreview(total = unit.totalFor(price, qty, points), isListedPrice = isListedPrice)
        }
    }
}

@Composable
private fun VendorOptionCard(option: VendorOption, isSelected: Boolean, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        containerColor = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        offset = ClayOffset.Small,
        contentPadding = PaddingValues(ClaySpacing.Md),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = option.vendor.name.value,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (option.vendor.phone.isNotBlank()) {
                    Text(option.vendor.phone, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
                }
            }
            Spacer(Modifier.width(ClaySpacing.Sm))
            val rate = option.listedRate
            if (rate != null) {
                Text(
                    text = "${formatRupiah(rate.priceIdr)} / ${rate.unit.shortLabel}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
            } else {
                ClayTag(text = "Belum ada harga", tint = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}

@Composable
private fun TotalPreview(total: Long, isListedPrice: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayTag(
            text = if (isListedPrice) "Sesuai daftar harga" else "Harga nego",
            tint = if (isListedPrice) WeMadeColors.Teal else WeMadeColors.Accent
        )
        Column(horizontalAlignment = Alignment.End) {
            Text("Total biaya vendor", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
            Text(formatRupiah(total), fontSize = 16.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        }
    }
}
