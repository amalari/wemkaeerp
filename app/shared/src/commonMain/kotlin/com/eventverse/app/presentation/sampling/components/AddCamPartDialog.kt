package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog untuk menambahkan bagian garmen baru pada Program CAM.
 * Menggunakan input standar teks dengan autocomplete dinamis yang menyaring saran saat pengguna mengetik.
 */
@Composable
fun AddCamPartDialog(
    existingPartNames: List<String>,
    onDismiss: () -> Unit,
    onAddPart: (String) -> Unit
) {
    var partNameInput by remember { mutableStateOf("") }
    val matchingSuggestions = remember(partNameInput, existingPartNames) {
        val trimmed = partNameInput.trim()
        if (trimmed.isBlank()) {
            emptyList()
        } else {
            SUGGESTED_CAM_PARTS.filter { suggestion ->
                suggestion.contains(trimmed, ignoreCase = true) &&
                    !suggestion.equals(trimmed, ignoreCase = true) &&
                    existingPartNames.none { it.equals(suggestion, ignoreCase = true) }
            }
        }
    }
    val canAdd = partNameInput.isNotBlank() && existingPartNames.none { it.equals(partNameInput.trim(), ignoreCase = true) }

    fun submit() {
        if (canAdd) {
            onAddPart(partNameInput.trim())
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md)) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(
                    text = "Tambah Bagian Garmen",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    ClayTextField(
                        value = partNameInput,
                        onValueChange = { partNameInput = it },
                        label = "Nama Bagian",
                        placeholder = "Ketik nama bagian (mis. Depan, Belakang, Lengan)...",
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Autocomplete suggestions saat mengetik
                    if (matchingSuggestions.isNotEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .claySurface(
                                    shape = ClayShapes.Chip,
                                    background = WeMadeColors.Surface,
                                    outline = WeMadeColors.OutlineSoft,
                                    borderWidth = ClayBorder.Hairline,
                                    offset = ClayOffset.Small
                                )
                                .padding(vertical = ClaySpacing.Xs)
                        ) {
                            matchingSuggestions.take(4).forEach { suggestion ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            partNameInput = suggestion
                                        }
                                        .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = suggestion,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = WeMadeColors.OnSurface
                                    )
                                    Text(
                                        text = "Gunakan",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = WeMadeColors.Primary
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Ghost,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Tambah",
                        style = ClayButtonStyle.Primary,
                        fontSize = 11.sp,
                        contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs),
                        enabled = canAdd,
                        onClick = { submit() }
                    )
                }
            }
        }
    }
}
