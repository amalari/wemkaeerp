package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.presentation.designsystem.*
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
        ClayCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(ClaySpacing.Lg),
            shape = ClayShapes.Panel,
            containerColor = WeMadeColors.Surface,
            outlineColor = WeMadeColors.Outline,
            shadowColor = WeMadeColors.Outline,
            offset = ClayOffset.Rest,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Xl)
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

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // Input: Role Name
            ClayTextField(
                value = nameInput,
                onValueChange = { onInputsChanged(it, descInput, selectedTemplateId) },
                label = "Nama Jabatan (contoh: Mandor Sablon)",
                placeholder = "Masukkan nama jabatan...",
                leadingIcon = { IconUser(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            // Input: Role Description
            ClayTextField(
                value = descInput,
                onValueChange = { onInputsChanged(nameInput, it, selectedTemplateId) },
                label = "Deskripsi Tugas / Tanggung Jawab",
                placeholder = "Jelaskan wewenang dan tanggung jawab jabatan...",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Lg))

            // Template Cloner Selector
            Text(
                text = "Salin Izin dari Template Bawaan:",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                roles.filter { it.isSystemDefault }.forEach { template ->
                    val isSelected = template.id.value == selectedTemplateId

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Card,
                                background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium
                            )
                            .clickable {
                                val nextId = if (isSelected) null else template.id.value
                                onInputsChanged(nameInput, descInput, nextId)
                            }
                            .padding(ClaySpacing.Md)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.name,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = template.description,
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isSelected) {
                                ClayTag(
                                    text = "Terpilih",
                                    tint = WeMadeColors.Primary,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xl))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Ghost
                )

                Spacer(modifier = Modifier.width(ClaySpacing.Md))

                ClayButton(
                    text = "Buat Jabatan",
                    onClick = onConfirm,
                    enabled = nameInput.isNotBlank(),
                    style = ClayButtonStyle.Primary
                )
            }
        }
    }
}
