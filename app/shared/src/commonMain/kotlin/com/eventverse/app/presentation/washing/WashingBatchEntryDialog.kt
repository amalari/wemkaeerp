package com.eventverse.app.presentation.washing

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.workqueue.WorkCard
import com.eventverse.app.domain.workqueue.WorkCardId
import com.eventverse.app.infrastructure.api.BundlePhotoUploadItem
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconImage
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconWarning
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Dialog peleburan bundle ke drum cuci (Washing Batch Ingestion).
 * Menegakkan aturan wajib foto fisik per bundle sebelum tali dilepas dan dicuci masal.
 */
@Composable
fun WashingBatchEntryDialog(
    pendingBundles: List<WorkCard>,
    isSubmitting: Boolean = false,
    onDismiss: () -> Unit,
    onSubmit: (
        batchCode: String,
        machineDrumNo: String,
        washRecipe: String,
        operatorName: String,
        bundles: List<BundlePhotoUploadItem>,
        notes: String
    ) -> Unit
) {
    var batchCode by remember { mutableStateOf("WB-${(100..999).random()}") }
    var machineDrumNo by remember { mutableStateOf("Drum 01 (100kg)") }
    var washRecipe by remember { mutableStateOf("Bio-wash + Softener") }
    var operatorName by remember { mutableStateOf("Operator Cuci") }
    var notes by remember { mutableStateOf("") }

    val selectedBundles = remember { mutableStateMapOf<WorkCardId, Boolean>() }
    val bundlePhotos = remember { mutableStateMapOf<WorkCardId, String>() }

    val activeSelectedCards = pendingBundles.filter { selectedBundles[it.id] == true }
    val totalSelectedPcs = activeSelectedCards.sumOf { it.wipPcs }
    val allSelectedHavePhoto = activeSelectedCards.isNotEmpty() && activeSelectedCards.all {
        !bundlePhotos[it.id].isNullOrBlank()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .width(760.dp)
                .heightIn(max = 680.dp)
                .padding(ClaySpacing.Md)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Peleburan Bundle ke Mesin Cuci (Washing Gate)",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Tali bundle wajib difoto sebelum dilepas dan dicampur ke drum cuci",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayBadge(
                        text = "${activeSelectedCards.size} Bundle Terpilih",
                        tint = if (activeSelectedCards.isEmpty()) WeMadeColors.OnSurfaceMuted else WeMadeColors.Primary
                    )
                }

                // Batch Parameters
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    OutlinedTextField(
                        value = batchCode,
                        onValueChange = { batchCode = it },
                        label = { Text("Kode Batch") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = machineDrumNo,
                        onValueChange = { machineDrumNo = it },
                        label = { Text("No. Drum Mesin") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = washRecipe,
                        onValueChange = { washRecipe = it },
                        label = { Text("Resep Cuci") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                    OutlinedTextField(
                        value = operatorName,
                        onValueChange = { operatorName = it },
                        label = { Text("Operator Cuci") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = outlinedColors()
                    )
                }

                Text(
                    text = "Pilih Bundle & Lampirkan Foto Fisik (Wajib):",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                // List of Pending Bundles
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(max = 300.dp)
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Xs),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    if (pendingBundles.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(ClaySpacing.Md),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tidak ada bundle yang sedang antre di stasiun WASHING",
                                    fontSize = 13.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }

                    items(pendingBundles, key = { it.id.value }) { card ->
                        val isChecked = selectedBundles[card.id] == true
                        val currentPhoto = bundlePhotos[card.id].orEmpty()
                        val hasPhoto = currentPhoto.isNotBlank()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clayFlat(
                                    shape = ClayShapes.Chip,
                                    background = if (isChecked) WeMadeColors.Surface else WeMadeColors.SurfaceMuted,
                                    outline = if (isChecked) WeMadeColors.Primary else WeMadeColors.Outline,
                                    borderWidth = if (isChecked) ClayBorder.Thick else ClayBorder.Hairline
                                )
                                .clickable {
                                    selectedBundles[card.id] = !isChecked
                                    if (!isChecked && currentPhoto.isBlank()) {
                                        bundlePhotos[card.id] = "photos/washing/${card.id.value}.jpg"
                                    }
                                }
                                .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    selectedBundles[card.id] = checked
                                    if (checked && currentPhoto.isBlank()) {
                                        bundlePhotos[card.id] = "photos/washing/${card.id.value}.jpg"
                                    }
                                },
                                colors = CheckboxDefaults.colors(checkedColor = WeMadeColors.Primary)
                            )

                            IconPackage(modifier = Modifier.size(18.dp), color = WeMadeColors.Primary)

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${card.subject.orderNumber} · ${card.subject.articleName}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "Bundle #${card.bundleNo ?: 1} | Size: ${card.sizeLabel} | Qty: ${card.wipPcs} Pcs",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }

                            // Slot Foto
                            if (isChecked) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                                ) {
                                    OutlinedTextField(
                                        value = currentPhoto,
                                        onValueChange = { bundlePhotos[card.id] = it },
                                        placeholder = { Text("Key foto...", fontSize = 11.sp) },
                                        modifier = Modifier.width(200.dp).height(46.dp),
                                        singleLine = true,
                                        leadingIcon = {
                                            IconImage(modifier = Modifier.size(16.dp))
                                        },
                                        colors = outlinedColors()
                                    )

                                    if (hasPhoto) {
                                        ClayBadge(
                                            text = "Foto Siap",
                                            tint = WeMadeColors.Success,
                                            leading = { IconCheck(modifier = Modifier.size(12.dp)) }
                                        )
                                    } else {
                                        ClayBadge(
                                            text = "Wajib Foto",
                                            tint = WeMadeColors.Error,
                                            leading = { IconWarning(modifier = Modifier.size(12.dp)) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Summary & Audit Banner
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Chip,
                            background = if (allSelectedHavePhoto) WeMadeColors.SurfaceMuted else WeMadeColors.ErrorBg,
                            outline = if (allSelectedHavePhoto) WeMadeColors.Outline else WeMadeColors.Error,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Total Masuk Drum: $totalSelectedPcs Pcs (${activeSelectedCards.size} Bundle)",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    if (!allSelectedHavePhoto && activeSelectedCards.isNotEmpty()) {
                        Text(
                            text = "Peringatan: Ada bundle terpilih yang belum memiliki foto!",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Error
                        )
                    }
                }

                // Footer Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Ghost,
                        enabled = !isSubmitting,
                        onClick = onDismiss
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = if (isSubmitting) "Menyimpan..." else "Mulai Cuci Masal (${totalSelectedPcs} Pcs)",
                        style = ClayButtonStyle.Primary,
                        enabled = !isSubmitting && allSelectedHavePhoto && activeSelectedCards.isNotEmpty(),
                        onClick = {
                            val items = activeSelectedCards.map {
                                BundlePhotoUploadItem(
                                    cardId = it.id,
                                    bundlePhotoKey = bundlePhotos[it.id].orEmpty()
                                )
                            }
                            onSubmit(batchCode, machineDrumNo, washRecipe, operatorName, items, notes)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun outlinedColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = WeMadeColors.Primary,
    unfocusedBorderColor = WeMadeColors.Outline,
    focusedContainerColor = WeMadeColors.Surface,
    unfocusedContainerColor = WeMadeColors.Surface
)
