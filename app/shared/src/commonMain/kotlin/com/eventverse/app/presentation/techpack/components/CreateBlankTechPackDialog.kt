package com.eventverse.app.presentation.techpack.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun CreateBlankTechPackDialog(
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSave: (styleName: String, styleCode: String?, clientName: String) -> Unit
) {
    var styleName by remember { mutableStateOf("") }
    var styleCode by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                Text(
                    text = "Buat Draft Tech Pack Baru",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Nama Model / Style (Wajib):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = styleName,
                        onValueChange = { styleName = it },
                        placeholder = "Contoh: Oversized Knit Cardigan V-Neck",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Kode Style (Opsional - otomatis jika kosong):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = styleCode,
                        onValueChange = { styleCode = it },
                        placeholder = "Contoh: STY-0042",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text(
                        text = "Nama Klien / Brand Buyer:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                    ClayTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        placeholder = "Contoh: Misty Garden, Brand Distro...",
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = onDismiss,
                        style = ClayButtonStyle.Secondary
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Membuat..." else "Buat Draft Tech Pack",
                        onClick = {
                            onSave(
                                styleName.trim(),
                                styleCode.trim().takeIf { it.isNotBlank() },
                                clientName.trim()
                            )
                        },
                        style = ClayButtonStyle.Primary,
                        enabled = styleName.isNotBlank() && !isSubmitting
                    )
                }
            }
        }
    }
}
