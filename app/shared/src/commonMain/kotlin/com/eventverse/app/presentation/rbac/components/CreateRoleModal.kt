package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun CreateRoleModal(
    isOpen: Boolean,
    nameInput: String,
    descInput: String,
    selectedTemplateId: String?,
    roles: List<CustomRole>,
    onInputsChanged: (String, String, String?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!isOpen) return

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // Modal Header
                Text(
                    text = "Tambah Jabatan Baru",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Buat struktur peran baru untuk menyesuaikan alur kerja pabrik Anda.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Input: Role Name
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { onInputsChanged(it, descInput, selectedTemplateId) },
                    label = { Text("Nama Jabatan (contoh: Mandor Sablon)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Input: Role Description
                OutlinedTextField(
                    value = descInput,
                    onValueChange = { onInputsChanged(nameInput, it, selectedTemplateId) },
                    label = { Text("Deskripsi Tugas / Tanggung Jawab") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Template Cloner Selector
                Text(
                    text = "Salin Izin dari Template Bawaan:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    roles.filter { it.isSystemDefault }.forEach { template ->
                        val isSelected = template.id.value == selectedTemplateId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) WeMadeColors.PrimaryContainer else Color(0xFFF8FAFC))
                                .border(
                                    1.dp,
                                    if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onInputsChanged(nameInput, descInput, template.id.value) }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface
                                )
                                Text(
                                    text = template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = { onInputsChanged(nameInput, descInput, template.id.value) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Batal")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = onConfirm,
                        enabled = nameInput.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = WeMadeColors.Primary),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Buat Jabatan")
                    }
                }
            }
        }
    }
}
