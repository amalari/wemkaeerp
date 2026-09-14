package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.crm.WhatsappNumber
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog pembuatan Lead baru dengan gaya Claymorphism Neo-Brutalis:
 * - Brand / Perusahaan: Opsional
 * - Nomor Handphone: Validasi format nomor Indonesia (10–13 digit lokal 08... atau +628...)
 * - Email: Opsional
 * - Tahap Awal: Mendukung langsung New Lead maupun Qualified Lead
 */
@Composable
fun CreateLeadDialog(
    initialStage: LeadStage = LeadStage.NEW_LEAD,
    onDismiss: () -> Unit,
    onCreate: (brandName: String, contactPerson: String, phoneNumber: String, email: String, stage: LeadStage) -> Unit
) {
    var stage by remember { mutableStateOf(if (initialStage == LeadStage.QUALIFIED) LeadStage.QUALIFIED else LeadStage.NEW_LEAD) }
    var brandName by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    val isPhoneFilled = phoneNumber.trim().isNotBlank()
    val isPhoneValid = !isPhoneFilled || WhatsappNumber.isValidIndonesianPhone(phoneNumber.trim())

    val isEmailFilled = email.trim().isNotBlank()
    val isEmailValid = !isEmailFilled || (email.contains("@") && email.contains(".") && email.trim().length >= 5)

    val hasAnyIdentifier = brandName.trim().isNotBlank() || contactPerson.trim().isNotBlank() || isPhoneFilled
    val canSubmit = hasAnyIdentifier && isPhoneValid && isEmailValid

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.width(440.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (stage == LeadStage.QUALIFIED) "Tambah Qualified Lead" else "Tambah Lead Baru",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // Stage selector pills
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StagePill(
                        label = "Inquiry",
                        selected = stage == LeadStage.NEW_LEAD,
                        onClick = { stage = LeadStage.NEW_LEAD }
                    )
                    StagePill(
                        label = "Qualified",
                        selected = stage == LeadStage.QUALIFIED,
                        onClick = { stage = LeadStage.QUALIFIED }
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayTextField(
                    value = brandName,
                    onValueChange = { brandName = it },
                    label = "Nama Brand/Perusahaan (Opsional)",
                    placeholder = "Misal: PT Sinar Jaya / Brand XYZ",
                    modifier = Modifier.fillMaxWidth()
                )

                ClayTextField(
                    value = contactPerson,
                    onValueChange = { contactPerson = it },
                    label = "Nama Kontak",
                    placeholder = "Misal: Budi / Bu Dewi",
                    modifier = Modifier.fillMaxWidth()
                )

                Column(modifier = Modifier.fillMaxWidth()) {
                    ClayTextField(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = "Nomor Handphone",
                        placeholder = "Misal: 081234567890 atau +62812...",
                        focusColor = if (!isPhoneValid) WeMadeColors.Error else WeMadeColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isPhoneFilled && !isPhoneValid) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Nomor handphone Indonesia: 10–13 digit (diawali 08, 628, atau +628)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.Error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    ClayTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email (Opsional)",
                        placeholder = "nama@perusahaan.com",
                        focusColor = if (!isEmailValid) WeMadeColors.Error else WeMadeColors.Primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isEmailFilled && !isEmailValid) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Format email belum sesuai (contoh: sales@brand.co.id)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.Error,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Xl),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Secondary,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "Simpan",
                    onClick = {
                        if (canSubmit) {
                            onCreate(brandName.trim(), contactPerson.trim(), phoneNumber.trim(), email.trim(), stage)
                        }
                    },
                    enabled = canSubmit,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun StagePill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .claySurface(
                shape = ClayShapes.Pill,
                background = if (selected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                outline = if (selected) WeMadeColors.Primary else WeMadeColors.OutlineSoft,
                offset = if (selected) ClayOffset.Flat else ClayOffset.Small,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = ClaySpacing.Sm, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurfaceMuted
        )
    }
}
