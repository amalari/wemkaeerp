package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.sampling.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

@Composable
fun QcInspectionDialog(
    isOpen: Boolean,
    order: SamplingOrder?,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (QcInspectionReport) -> Unit
) {
    if (!isOpen || order == null) return

    val finishedChart = remember(order.id) {
        order.finishedSizeCharts.firstOrNull() ?: FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED
    }

    var inspectorName by remember(order.id) { mutableStateOf("Tim QC Inspeksi") }
    var qcNotes by remember(order.id) { mutableStateOf("") }

    // State for measured values of standard POM
    var actualBodyLength by remember(order.id) { mutableStateOf(finishedChart.bodyLength.toString()) }
    var actualBodyWidth by remember(order.id) { mutableStateOf(finishedChart.bodyWidth.toString()) }
    var actualSleeveLength by remember(order.id) { mutableStateOf(finishedChart.sleeveLength.toString()) }
    var actualArmHole by remember(order.id) { mutableStateOf(finishedChart.armHole.toString()) }

    // Defect checklist toggles
    var defectJarumPatah by remember(order.id) { mutableStateOf(false) }
    var defectBelangBenang by remember(order.id) { mutableStateOf(false) }
    var defectDropStitch by remember(order.id) { mutableStateOf(false) }
    var defectJahitanLoncat by remember(order.id) { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.92f).padding(ClaySpacing.Md),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "LEMBAR AUDIT QUALITY CONTROL",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "${order.spkNumber.value} • ${order.styleName} (${order.clientName})",
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayIconButton(onClick = onDismiss, shape = ClayShapes.Tile) {
                        IconClose(modifier = Modifier.size(16.dp))
                    }
                }

                // Inspector Name
                OutlinedTextField(
                    value = inspectorName,
                    onValueChange = { inspectorName = it },
                    label = { Text("Nama Petugas QC Inspeksi") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                // POM Audit Table
                Text(
                    text = "1. Verifikasi Point of Measurement (POM) Fisik vs Size Chart Buyer:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                PomAuditRow(
                    name = "Panjang Baju (Body Length)",
                    target = finishedChart.bodyLength,
                    actualText = actualBodyLength,
                    onActualChange = { actualBodyLength = it }
                )

                PomAuditRow(
                    name = "Lebar Dada (Body Width)",
                    target = finishedChart.bodyWidth,
                    actualText = actualBodyWidth,
                    onActualChange = { actualBodyWidth = it }
                )

                PomAuditRow(
                    name = "Panjang Tangan (Sleeve Length)",
                    target = finishedChart.sleeveLength,
                    actualText = actualSleeveLength,
                    onActualChange = { actualSleeveLength = it }
                )

                PomAuditRow(
                    name = "Arm Hole",
                    target = finishedChart.armHole,
                    actualText = actualArmHole,
                    onActualChange = { actualArmHole = it }
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                // Defect Checklist
                Text(
                    text = "2. Checklist Visual Cacat Kain / Jahitan:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                DefectToggleItem(
                    label = "Jarum Patah / Garis Vertikal Rusak",
                    isChecked = defectJarumPatah,
                    onToggle = { defectJarumPatah = !defectJarumPatah }
                )

                DefectToggleItem(
                    label = "Belang Benang / Lot Berbeda",
                    isChecked = defectBelangBenang,
                    onToggle = { defectBelangBenang = !defectBelangBenang }
                )

                DefectToggleItem(
                    label = "Bolong / Drop Stitch Rajutan",
                    isChecked = defectDropStitch,
                    onToggle = { defectDropStitch = !defectDropStitch }
                )

                DefectToggleItem(
                    label = "Jahitan / Linking Loncat",
                    isChecked = defectJahitanLoncat,
                    onToggle = { defectJahitanLoncat = !defectJahitanLoncat }
                )

                Spacer(modifier = Modifier.height(ClaySpacing.Sm))

                // Notes
                OutlinedTextField(
                    value = qcNotes,
                    onValueChange = { qcNotes = it },
                    label = { Text("Catatan Hasil Inspeksi / Instruksi Perbaikan") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = ClayShapes.Tile,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = WeMadeColors.Primary,
                        unfocusedBorderColor = WeMadeColors.Border
                    )
                )

                // Decision Action Buttons
                Text(
                    text = "3. Keputusan Akhir QC:",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )

                fun submitWithResult(result: QcInspectionResult) {
                    val poms = listOf(
                        QcPomMeasurement("Panjang Baju", finishedChart.bodyLength, actualBodyLength.toDoubleOrNull() ?: finishedChart.bodyLength),
                        QcPomMeasurement("Lebar Dada", finishedChart.bodyWidth, actualBodyWidth.toDoubleOrNull() ?: finishedChart.bodyWidth),
                        QcPomMeasurement("Panjang Tangan", finishedChart.sleeveLength, actualSleeveLength.toDoubleOrNull() ?: finishedChart.sleeveLength),
                        QcPomMeasurement("Arm Hole", finishedChart.armHole, actualArmHole.toDoubleOrNull() ?: finishedChart.armHole)
                    )
                    val defects = buildList {
                        if (defectJarumPatah) add("Jarum Patah")
                        if (defectBelangBenang) add("Belang Benang")
                        if (defectDropStitch) add("Bolong / Drop Stitch")
                        if (defectJahitanLoncat) add("Jahitan Loncat")
                    }
                    val report = QcInspectionReport(
                        id = "",
                        samplingOrderId = order.id.value,
                        inspectorName = inspectorName,
                        inspectedAt = Clock.System.now(),
                        pomMeasurements = poms,
                        defectsFound = defects,
                        qcResult = result,
                        qcNotes = qcNotes
                    )
                    onSubmit(report)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayButton(
                        text = "QC PASSED",
                        style = ClayButtonStyle.Success,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1.3f),
                        onClick = { submitWithResult(QcInspectionResult.PASSED) }
                    )

                    ClayButton(
                        text = "REWORK",
                        style = ClayButtonStyle.Secondary,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        onClick = { submitWithResult(QcInspectionResult.REWORK) }
                    )

                    ClayButton(
                        text = "REJECT",
                        style = ClayButtonStyle.Secondary,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        onClick = { submitWithResult(QcInspectionResult.REJECT) }
                    )
                }
            }
        }
    }
}

@Composable
private fun PomAuditRow(
    name: String,
    target: Double,
    actualText: String,
    onActualChange: (String) -> Unit
) {
    val actual = actualText.toDoubleOrNull() ?: target
    val dev = kotlin.math.abs(actual - target)
    val isOk = dev <= 1.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(WeMadeColors.SurfaceMuted, ClayShapes.Tile)
            .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Column(modifier = Modifier.weight(1.5f)) {
            Text(text = name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Text(text = "Target: $target cm", fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        }

        OutlinedTextField(
            value = actualText,
            onValueChange = onActualChange,
            singleLine = true,
            modifier = Modifier.width(90.dp).height(42.dp),
            shape = ClayShapes.Tile,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Border
            )
        )

        ClayBadge(
            text = if (isOk) "Lolos (+/-${dev.toString().take(3)}cm)" else "Deviasi (${dev.toString().take(3)}cm)",
            tint = if (isOk) WeMadeColors.Success else WeMadeColors.Error
        )
    }
}

@Composable
private fun DefectToggleItem(
    label: String,
    isChecked: Boolean,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .background(
                if (isChecked) WeMadeColors.Error.copy(alpha = 0.1f) else WeMadeColors.SurfaceMuted,
                ClayShapes.Tile
            )
            .padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (isChecked) FontWeight.Bold else FontWeight.Normal,
            color = if (isChecked) WeMadeColors.Error else WeMadeColors.OnSurface
        )
        ClayBadge(
            text = if (isChecked) "Ditemukan Cacat" else "Bebas",
            tint = if (isChecked) WeMadeColors.Error else WeMadeColors.Success
        )
    }
}
