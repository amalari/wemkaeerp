package com.eventverse.app.presentation.costing.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.costing.EstimateBasis
import com.eventverse.app.domain.costing.EstimateConfidence
import com.eventverse.app.domain.costing.GarmentSilhouette
import com.eventverse.app.domain.costing.KnitThickness
import com.eventverse.app.domain.costing.MaterialCharacter
import com.eventverse.app.domain.costing.QuickQuotationEstimateResult
import com.eventverse.app.presentation.costing.CostingUiEvent
import com.eventverse.app.presentation.costing.CostingUiState
import com.eventverse.app.presentation.costing.QuickEstimatorFormState
import com.eventverse.app.presentation.costing.formatRupiah
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Formulir estimasi cepat untuk Customer Service.
 *
 * ## Mengapa hasilnya rentang, bukan satu angka?
 * Lihat [QuickQuotationEstimateResult]. UI mengikuti keputusan domain itu dan tidak menampilkan
 * nilai tengah sebagai angka utama — kalau satu angka besar muncul di layar, itulah yang akan
 * disalin CS ke WhatsApp, dan rentangnya jadi percuma.
 */
@Composable
fun AiQuickEstimatorPane(
    state: CostingUiState,
    onEvent: (CostingUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val form = state.estimatorForm

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)) {
            Text(
                text = "Estimasi Harga Instan",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "Isi lima pertanyaan sederhana, dapatkan rentang harga untuk dikirim ke klien.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        MockupUploadCard(state = state, onEvent = onEvent)

        EstimatorField(label = "1. Berapa jumlah pesanannya?") {
            ClayTextField(
                value = form.quantityText,
                onValueChange = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(quantityText = it))) },
                placeholder = "mis. 100",
                modifier = Modifier.fillMaxWidth()
            )
        }

        EstimatorField(label = "2. Karakter bahannya seperti apa?") {
            OptionChips(
                options = MaterialCharacter.entries,
                selected = form.materialCharacter,
                labelOf = { it.displayName },
                onSelect = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(materialCharacter = it))) }
            )
        }

        EstimatorField(label = "3. Seberapa tebal rajutannya?") {
            OptionChips(
                options = KnitThickness.entries,
                selected = form.thickness,
                labelOf = { it.displayName },
                onSelect = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(thickness = it))) }
            )
        }

        EstimatorField(label = "4. Model potongannya apa?") {
            OptionChips(
                options = GarmentSilhouette.entries,
                selected = form.silhouette,
                labelOf = { it.displayName },
                onSelect = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(silhouette = it))) }
            )
        }

        EstimatorField(label = "5. Kancing & label") {
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTextField(
                    value = form.buttonCountText,
                    onValueChange = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(buttonCountText = it))) },
                    label = "Jumlah kancing",
                    placeholder = "0",
                    modifier = Modifier.width(140.dp)
                )
                LabelledCheckbox(
                    checked = form.hasWovenLabel,
                    label = "Label woven",
                    onCheckedChange = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(hasWovenLabel = it))) }
                )
                LabelledCheckbox(
                    checked = form.hasHangtag,
                    label = "Hangtag",
                    onCheckedChange = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(hasHangtag = it))) }
                )
            }
        }

        ClayTextField(
            value = form.notes,
            onValueChange = { onEvent(CostingUiEvent.UpdateEstimatorForm(form.copy(notes = it))) },
            label = "Catatan tambahan (opsional)",
            placeholder = "mis. ada bordir logo di dada kiri",
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            ClayButton(
                text = if (state.isEstimating) "Menghitung…" else "⚡ Hitung Estimasi Harga",
                onClick = { onEvent(CostingUiEvent.RunQuickEstimate) },
                style = ClayButtonStyle.Accent,
                enabled = form.isValid && !state.isEstimating
            )
            ClayButton(
                text = "Reset",
                onClick = { onEvent(CostingUiEvent.ResetQuickEstimate) },
                style = ClayButtonStyle.Ghost,
                enabled = !state.isEstimating
            )
        }

        state.estimatorResult?.let { result -> EstimateResultCard(result) }
    }
}

@Composable
private fun MockupUploadCard(state: CostingUiState, onEvent: (CostingUiEvent) -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.PrimaryContainer,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs)
            ) {
                Text(
                    text = "Gambar desain / mockup",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = state.mockupImageUrl ?: "Opsional — AI membaca siluet dan jumlah kancing dari gambar.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(ClaySpacing.Md))
            ClayButton(
                text = if (state.isAnalyzingMockup) "Membaca…" else "Pilih Gambar",
                onClick = { onEvent(CostingUiEvent.PickAndAnalyzeMockup) },
                style = ClayButtonStyle.Secondary,
                enabled = !state.isAnalyzingMockup
            )
        }

        // Ditampilkan hanya bila model cukup yakin; hint keyakinan rendah lebih sering
        // menyesatkan CS daripada membantunya.
        state.mockupHints
            ?.takeIf { it.confidence >= 0.4 && (it.summary.isNotBlank() || it.detectedFeatures.isNotEmpty()) }
            ?.let { hints ->
                Spacer(Modifier.height(ClaySpacing.Md))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayBadge(text = "AI", tint = WeMadeColors.Purple)
                    Text(
                        text = hints.summary.ifBlank { hints.detectedFeatures.joinToString(", ") },
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
    }
}

@Composable
private fun EstimateResultCard(result: QuickQuotationEstimateResult) {
    val clipboard = LocalClipboardManager.current

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.SuccessBg,
        outlineColor = WeMadeColors.Success,
        contentPadding = PaddingValues(ClaySpacing.Xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hasil Estimasi",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(ClaySpacing.Sm))
            ClayBadge(
                text = "Akurasi ${result.confidence.badge}",
                tint = when (result.confidence) {
                    EstimateConfidence.HIGH -> WeMadeColors.Success
                    EstimateConfidence.MEDIUM -> WeMadeColors.Warning
                    EstimateConfidence.LOW -> WeMadeColors.Error
                },
                dot = true
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        Text(
            text = "Harga jual per pcs",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            text = "${result.suggestedPriceLow.formatRupiah()} – ${result.suggestedPriceHigh.formatRupiah()}",
            fontWeight = FontWeight.Black,
            fontSize = 22.sp,
            color = WeMadeColors.Success
        )

        Spacer(Modifier.height(ClaySpacing.Md))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            ClayTag(text = "HPP ${result.hppLow.formatRupiah()} – ${result.hppHigh.formatRupiah()}", tint = WeMadeColors.Primary)
            ClayTag(text = "Margin ${result.appliedMargin.asPercentageString()}", tint = WeMadeColors.Accent)
            ClayTag(text = "${result.estimatedWeightGrams.toInt()} g/pcs", tint = WeMadeColors.Teal)
            ClayTag(text = "${result.estimatedKnittingMinutes} menit rajut", tint = WeMadeColors.Info)
        }

        Spacer(Modifier.height(ClaySpacing.Md))
        Text(
            text = "Dasar perhitungan: ${result.basis.displayName}",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )

        if (result.breakdown.isNotEmpty()) {
            Spacer(Modifier.height(ClaySpacing.Lg))
            Text("Rincian biaya per pcs", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.OnSurface)
            Spacer(Modifier.height(ClaySpacing.Xs))
            result.breakdown.forEach { line ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Xxs),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(line.label, fontSize = 12.sp, color = WeMadeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (line.explanation.isNotBlank()) {
                            Text(line.explanation, fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    Text(
                        text = line.amountPerUnit.formatRupiah(),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                }
            }
        }

        if (result.matches.isNotEmpty()) {
            Spacer(Modifier.height(ClaySpacing.Lg))
            Text("Acuan dari arsip", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = WeMadeColors.OnSurface)
            Spacer(Modifier.height(ClaySpacing.Xs))
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                result.matches.take(3).forEach { match ->
                    ClayTag(
                        text = "${match.benchmark.styleName} (${(match.similarity * 100).toInt()}%)",
                        tint = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }

        result.warnings.forEach { warning ->
            Spacer(Modifier.height(ClaySpacing.Md))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.WarningBg,
                        outline = WeMadeColors.Warning.copy(alpha = 0.55f),
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                Text("⚠", fontSize = 12.sp, color = WeMadeColors.Warning)
                Text(warning, fontSize = 11.sp, color = WeMadeColors.OnSurface)
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        ClayButton(
            text = "📋 Salin Format Penawaran WhatsApp",
            onClick = {
                clipboard.setText(
                    AnnotatedString(result.toWhatsAppSummary { money -> money.formatRupiah() })
                )
            },
            style = ClayButtonStyle.Success
        )
    }
}

// ── Potongan kecil yang dipakai berulang di dalam pane ini ───────────────────────────────

/**
 * `ClayCheckbox` sengaja hanya berupa kotaknya. Pembungkus berlabel ini tinggal di sini
 * karena baru dipakai dua kali; kalau pemakaian ketiga muncul, naikkan ke `designsystem/`
 * sesuai Aturan Tiga Kali.
 */
@Composable
private fun LabelledCheckbox(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayCheckbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, fontSize = 12.sp, color = WeMadeColors.OnSurface)
    }
}

@Composable
private fun EstimatorField(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = WeMadeColors.OnSurface)
        content()
    }
}

/**
 * Pilihan tunggal berbentuk deretan pil.
 *
 * Dipakai alih-alih `DropdownMenu` karena opsinya sedikit dan CS memilih sambil bicara di
 * telepon — semua pilihan terlihat sekaligus, tanpa langkah membuka menu.
 */
@Composable
private fun <T> OptionChips(
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit
) {
    FlowRowCompat(options) { option ->
        val isActive = option == selected
        ClayButton(
            text = labelOf(option),
            onClick = { onSelect(option) },
            style = if (isActive) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
            fontSize = 12.sp
        )
    }
}

/**
 * Pembungkus `FlowRow` eksperimental Compose, dikurung di satu tempat supaya anotasi
 * opt-in-nya tidak perlu diulang di setiap pemakaian.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun <T> FlowRowCompat(items: List<T>, item: @Composable (T) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        items.forEach { item(it) }
    }
}
