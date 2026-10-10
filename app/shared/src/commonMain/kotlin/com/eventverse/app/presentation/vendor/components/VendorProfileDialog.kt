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
import com.eventverse.app.infrastructure.api.VendorProfileInput
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField

/** Tambah (initial = null) atau ubah kontak vendor. Menonaktifkan hanya tersedia saat mengubah. */
@Composable
internal fun VendorProfileDialog(
    initial: Vendor?,
    isSubmitting: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (VendorProfileInput) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name?.value ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var notes by remember { mutableStateOf(initial?.notes ?: "") }
    var isActive by remember { mutableStateOf(initial?.isActive ?: true) }

    VendorDialogFrame(
        title = if (initial == null) "Tambah Kontak Vendor" else "Ubah Kontak Vendor",
        subtitle = "Nama vendor ikut tercetak di Surat Jalan, jadi pakai nama yang dikenal kurir dan gudang.",
        confirmText = "Simpan Kontak",
        confirmEnabled = name.isNotBlank(),
        isSubmitting = isSubmitting,
        onDismiss = onDismiss,
        errorMessage = errorMessage,
        onConfirm = {
            onSave(
                VendorProfileInput(
                    name = name.trim(),
                    phone = phone.trim(),
                    address = address.trim(),
                    notes = notes.trim(),
                    isActive = if (initial == null) null else isActive
                )
            )
        }
    ) {
        ClayTextField(value = name, onValueChange = { name = it.take(150) }, label = "Nama vendor *", placeholder = "CV Sablon Jaya", modifier = Modifier.fillMaxWidth())
        ClayTextField(
            value = phone,
            onValueChange = { phone = it.filter { c -> c.isDigit() || c == '+' }.take(20) },
            label = "Nomor WA",
            placeholder = "0812...",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )
        ClayTextField(value = address, onValueChange = { address = it }, label = "Alamat", singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
        ClayTextField(value = notes, onValueChange = { notes = it }, label = "Catatan", placeholder = "Spesialisasi, jam kerja, syarat min order...", singleLine = false, minLines = 2, modifier = Modifier.fillMaxWidth())
        if (initial != null) {
            VendorFieldLabel("Status")
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayChoiceChip(text = "Aktif", selected = isActive, onClick = { isActive = true })
                ClayChoiceChip(text = "Nonaktif", selected = !isActive, onClick = { isActive = false })
            }
        }
    }
}
