package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.sampling.SizeMode
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun CreateSamplingOrderDialog(
    isOpen: Boolean,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (clientName: String, styleName: String, sizeMode: SizeMode, useFactoryPreset: Boolean) -> Unit
) {
    if (!isOpen) return

    var clientName by remember { mutableStateOf("") }
    var styleName by remember { mutableStateOf("") }
    var sizeMode by remember { mutableStateOf(SizeMode.ALL_SIZE) }
    var usePreset by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Text(
                text = "BUAT SPK SAMPLE BARU",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )

            Text(
                text = "Isi detail awal pesanan sampel rajut / garmen untuk tim R&D.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            OutlinedTextField(
                value = clientName,
                onValueChange = { clientName = it },
                label = { Text("Nama Klien / Buyer (cth: BIANCA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            OutlinedTextField(
                value = styleName,
                onValueChange = { styleName = it },
                label = { Text("Nama Style / Artikel (cth: FLORAL CARDIGAN)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            Text(
                text = "PILIHAN UKURAN:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayCard(
                    modifier = Modifier.weight(1f),
                    shape = ClayShapes.Tile,
                    selected = sizeMode == SizeMode.ALL_SIZE,
                    // Isi **opaque** + outline berperan: isi transparan akan menampakkan bayangan
                    // hard di belakangnya (lihat KDoc `ClayCard`).
                    containerColor = if (sizeMode == SizeMode.ALL_SIZE) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                    outlineColor = if (sizeMode == SizeMode.ALL_SIZE) WeMadeColors.Primary else WeMadeColors.Outline,
                    contentPadding = PaddingValues(ClaySpacing.Sm),
                    onClick = { sizeMode = SizeMode.ALL_SIZE }
                ) {
                    Text(
                        text = "All Size (1 Ukuran)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sizeMode == SizeMode.ALL_SIZE) WeMadeColors.Primary else WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Rekomendasi rajut",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                ClayCard(
                    modifier = Modifier.weight(1f),
                    shape = ClayShapes.Tile,
                    selected = sizeMode == SizeMode.MULTI_SIZE,
                    containerColor = if (sizeMode == SizeMode.MULTI_SIZE) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                    outlineColor = if (sizeMode == SizeMode.MULTI_SIZE) WeMadeColors.Primary else WeMadeColors.Outline,
                    contentPadding = PaddingValues(ClaySpacing.Sm),
                    onClick = { sizeMode = SizeMode.MULTI_SIZE }
                ) {
                    Text(
                        text = "Multi-Size",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sizeMode == SizeMode.MULTI_SIZE) WeMadeColors.Primary else WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Grading S/M/L/XL",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { usePreset = !usePreset },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayBadge(
                    text = if (usePreset) "AKTIF" else "NONAKTIF",
                    tint = if (usePreset) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
                )
                Text(
                    text = "Gunakan template ukuran All Size standar pabrik otomatis",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurface
                )
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Batal",
                    style = ClayButtonStyle.Ghost,
                    onClick = onDismiss
                )

                Spacer(modifier = Modifier.width(ClaySpacing.Sm))

                ClayButton(
                    text = if (isSubmitting) "Menyimpan..." else "Terbitkan SPK Sample",
                    style = ClayButtonStyle.Primary,
                    enabled = clientName.isNotBlank() && styleName.isNotBlank() && !isSubmitting,
                    onClick = {
                        onSubmit(clientName, styleName, sizeMode, usePreset)
                    }
                )
            }
        }
    }
}
