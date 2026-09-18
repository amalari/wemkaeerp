package com.eventverse.app.presentation.qc.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.sampling.FactorySizePresets
import com.eventverse.app.domain.sampling.QcInspectionReport
import com.eventverse.app.domain.sampling.QcInspectionResult
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.qc.QcDraftStore
import com.eventverse.app.presentation.qc.QcInspectionFormState
import com.eventverse.app.presentation.qc.QcQueueItem
import com.eventverse.app.presentation.qc.pomFieldsFor
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock

/**
 * Panel kanan master-detail: lembar inspeksi satu pcs.
 *
 * Dijadikan panel, bukan dialog, karena stasiun QC adalah layar satu-tugas — membuka dan
 * menutup modal puluhan kali sehari adalah gesekan murni, dan antrean tetap perlu terlihat
 * sementara inspektor mengukur.
 */
@Composable
fun QcInspectionPane(
    item: QcQueueItem?,
    inspectorName: String,
    isSubmitting: Boolean,
    onSubmit: (QcInspectionReport) -> Unit,
    modifier: Modifier = Modifier
) {
    if (item == null) {
        QcEmptyInspectionPane(modifier = modifier)
        return
    }

    val order = item.order
    val chart = remember(order.id) {
        order.finishedSizeCharts.firstOrNull() ?: FactorySizePresets.ALL_SIZE_CARDIGAN_FINISHED
    }

    // Kunci remember memuat nomor pcs: begitu satu pcs selesai disubmit, nomornya naik dan
    // form terakit ulang dalam keadaan kosong untuk baju berikutnya. Tanpa itu, angka baju
    // sebelumnya akan terbawa dan tersimpan sebagai hasil ukur baju yang belum disentuh.
    val draftKey = remember(order.id, item.kind, item.nextPieceNo) {
        QcDraftStore.keyFor(order.id.value, item.kind, item.nextPieceNo)
    }
    val form = remember(draftKey) {
        QcInspectionFormState(
            pomFields = pomFieldsFor(chart),
            pieceNo = item.nextPieceNo,
            previousReport = item.previousReportForNextPiece
        ).apply {
            QcDraftStore.load(draftKey)?.let(::restore)
        }
    }

    var savedAt by remember(draftKey) { mutableStateOf(false) }

    // Simpan otomatis dengan jeda: tiap ketikan membatalkan efek sebelumnya, jadi tulisan ke
    // penyimpanan baru terjadi setelah petugas berhenti mengetik sesaat — bukan per huruf.
    val draft = form.draft
    LaunchedEffect(draftKey, draft) {
        delay(AUTOSAVE_DEBOUNCE_MS)
        QcDraftStore.save(draftKey, draft)
        savedAt = !draft.isEmpty
    }

    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
        ) {
            QcInspectionHeader(item = item, sizeLabel = chart.sizeLabel)

            QcInspectorTallyPanel(
                contributions = item.contributions,
                inspectedQty = item.inspectedQty,
                targetQty = item.targetQty
            )

            if (item.isFullyInspected) {
                QcAllPiecesDoneCard()
            } else {
                QcPomChecklist(form = form)
            }

            Spacer(modifier = Modifier.height(ClaySpacing.Md))
        }

        if (!item.isFullyInspected) {
            QcSubmitBar(
                form = form,
                item = item,
                inspectorName = inspectorName,
                isSubmitting = isSubmitting,
                isDraftSaved = savedAt,
                onSubmit = {
                    // Draf hanya berlaku sampai lembarnya jadi fakta.
                    QcDraftStore.clear(draftKey)
                    onSubmit(form.buildReport(order, item.kind, inspectorName, Clock.System.now()))
                }
            )
        }
    }
}

@Composable
private fun QcEmptyInspectionPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        ClayCard(
            modifier = Modifier.widthIn(max = 420.dp),
            shape = ClayShapes.Card,
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            Text(
                text = "Pilih SPK dari antrean",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(modifier = Modifier.height(ClaySpacing.Sm))
            Text(
                text = "Lembar ukur akan terbuka di panel ini tanpa menutup antrean.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun QcAllPiecesDoneCard() {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        containerColor = WeMadeColors.SuccessBg,
        outlineColor = WeMadeColors.Success,
        contentPadding = PaddingValues(ClaySpacing.Xl)
    ) {
        Text(
            text = "Seluruh pcs sudah diperiksa",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
        Text(
            text = "SPK ini beres untuk meja ini. Pilih SPK berikutnya dari antrean.",
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun QcInspectionHeader(item: QcQueueItem, sizeLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.spk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(ClaySpacing.Md))
            ClayBadge(
                text = when {
                    item.isFullyInspected -> "Selesai"
                    item.isRecheck -> "Periksa ulang pcs ke-${item.nextPieceNo}"
                    else -> "Pcs ke-${item.nextPieceNo} dari ${item.targetQty}"
                },
                tint = when {
                    item.isFullyInspected -> WeMadeColors.Success
                    item.isRecheck -> WeMadeColors.Accent
                    else -> WeMadeColors.Primary
                }
            )
        }

        Text(
            text = "${item.order.clientName} • ${item.order.styleName}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface
        )

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTag(text = item.kind.shortLabel, tint = WeMadeColors.Primary)
            ClayTag(text = "Size $sizeLabel", tint = WeMadeColors.OnSurfaceMuted)
            ClayTag(text = "Menunggu ${item.waitingLabel}", tint = WeMadeColors.OnSurfaceMuted)
        }
    }
}

/**
 * Bilah submit menempel di bawah panel, tidak ikut menggulir: keputusannya diambil setelah
 * seluruh titik ukur terisi, dan menggulir balik ke bawah untuk menekannya adalah langkah sia-sia.
 */
@Composable
private fun QcSubmitBar(
    form: QcInspectionFormState,
    item: QcQueueItem,
    inspectorName: String,
    isSubmitting: Boolean,
    isDraftSaved: Boolean,
    onSubmit: () -> Unit
) {
    val result = form.result
    val isLastPiece = item.nextPieceNo >= item.targetQty
    val canSubmit = !isSubmitting && form.isComplete && inspectorName.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline
            )
            .padding(ClaySpacing.Lg),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = when {
                    inspectorName.isBlank() -> "Sesi tidak mengenali petugas — lembar tidak bisa ditandatangani."
                    !form.isComplete && form.isRecheck ->
                        "Pemeriksaan ulang — tinggal ${form.requiredCount} titik yang dulu bermasalah (terisi ${form.filledCount})."
                    !form.isComplete -> "Terisi ${form.filledCount} dari ${form.requiredCount} titik ukur."
                    form.flaggedCount > 0 -> "${form.flaggedCount} titik bermasalah — pcs ini tercatat perlu perbaikan."
                    else -> "Semua titik sesuai — pcs ini tercatat lolos."
                },
                style = MaterialTheme.typography.bodySmall,
                color = when {
                    inspectorName.isBlank() || !form.isComplete -> WeMadeColors.Warning
                    form.flaggedCount > 0 -> WeMadeColors.Error
                    else -> WeMadeColors.OnSurfaceMuted
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(modifier = Modifier.width(ClaySpacing.Sm))
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                if (isDraftSaved && !form.isComplete) {
                    ClayBadge(text = "Tersimpan otomatis", tint = WeMadeColors.Info)
                }
                if (form.isComplete) {
                    ClayBadge(
                        text = result.displayName,
                        tint = if (result == QcInspectionResult.PASSED) WeMadeColors.Success else WeMadeColors.Error
                    )
                }
            }
        }

        ClayButton(
            text = when {
                form.isRecheck -> "Submit Hasil Perbaikan Pcs ke-${item.nextPieceNo}"
                isLastPiece -> "Submit — SPK Selesai"
                else -> "Submit & Lanjut Pcs ke-${item.nextPieceNo + 1}"
            },
            style = if (form.isComplete && form.flaggedCount > 0) ClayButtonStyle.Accent else ClayButtonStyle.Success,
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            onClick = onSubmit
        )
    }
}

/**
 * Jeda simpan otomatis. Cukup panjang untuk tidak menulis per huruf, cukup pendek untuk
 * bertahan dari peramban yang ditutup mendadak.
 */
private const val AUTOSAVE_DEBOUNCE_MS = 400L
