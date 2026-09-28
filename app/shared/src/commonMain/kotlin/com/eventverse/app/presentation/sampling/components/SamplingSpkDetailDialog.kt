package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.masterdata.MaterialItem
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.SamplingRoute
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.domain.sampling.currentStage
import com.eventverse.app.domain.sampling.positionOf
import com.eventverse.app.domain.sampling.stageInputFor
import com.eventverse.app.domain.sampling.stagesWith
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.domain.traceability.TraceWorkOrderKind
import com.eventverse.app.domain.traceability.TraceWorkOrderRef
import com.eventverse.app.presentation.deal.components.rememberPdfPrintLauncher
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.sampling.DraftSaveStatus
import com.eventverse.app.presentation.sampling.ProcessFlowScope
import com.eventverse.app.presentation.sampling.ProcessFlowUiEvent
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
import com.eventverse.app.presentation.sampling.effectiveFrame
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Dialog detail SPK — dibuka saat kartu di kolom Kanban diklik.
 *
 * Pada tahap SPK Masuk (NEW_INTAKE), dialog awalnya hanya menampilkan referensi detail klien
 * (mockup, ukuran POM, catatan). Alur proses baru muncul saat "Tentukan Alur Desain" diklik
 * dan otomatis scroll ke section alur proses tersebut.
 */
@Composable
fun SamplingSpkDetailDialog(
    order: SamplingOrder,
    isSubmitting: Boolean,
    onDismiss: () -> Unit,
    onStartCam: () -> Unit = {},
    onSubmitCamProgram: (List<StageInputSection>) -> Unit = {},
    /**
     * Draft lembar Program CAM berubah (termasuk hasil R&D: gramasi, waktu, ukuran jadi) —
     * di-autosave oleh ViewModel tanpa pindah tahap.
     */
    onDraftChange: (List<StageInputSection>) -> Unit = {},
    draftSaveStatus: DraftSaveStatus = DraftSaveStatus.Idle,
    onDetermineFlow: () -> Unit = {},
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null,
    processFlowViewModel: ProcessFlowViewModel? = null,
    /** Kerangka pabrik — dipakai bila SPK belum membeku (TRD-FLOW-001). */
    stageFlow: List<StageDefinition> = SamplingRoute.DEFAULT_STAGES,
    availableMaterials: List<MaterialItem> = emptyList(),
    initialShowFlowSection: Boolean = false,
    initialShowCamSection: Boolean = false
) {
    // Gerbang klien: tombol mulai CAM hanya tampil pada tahap SPK Masuk / Penentuan Alur.
    val isGateStage = order.stageCode == SamplingPipelineStage.NEW_INTAKE.toStageCode() ||
        order.stageCode == SamplingPipelineStage.FLOW_REVIEW.toStageCode()

    // Tahap Program CAM: alur sudah final (dikunci) dan section Program dibuka untuk tim sampling.
    val isCamStage = order.stageCode == SamplingPipelineStage.CAM_PROGRAMMING.toStageCode()
    // Lantai R&D (rajut s/d kemas): hasil sampel baru diketahui di sini.
    val isRdStage = order.currentStage.has(StageTrait.OPERATOR_DESK)
    // Kartu SPK A6 baru boleh dibuka manual ketika SPK sudah masuk lantai R&D / produksi (rajut ke atas).
    // Saat masih di tahap SPK Masuk, Penentuan Alur, atau Program CAM, kartu fisik belum dicetak.
    val firstDesk = order.stagesWith(StageTrait.OPERATOR_DESK).firstOrNull()?.code
    val canPrintSpkCard = firstDesk != null && order.positionOf(order.stageCode) >= order.positionOf(firstDesk)
    // Lembar Program CAM tetap terbuka di lantai R&D (rajut s/d kemas): operator mengerjakan
    // sampel berdasarkan program, instruksi panah, dan tenselity buatan tim CAM, sementara
    // hasil R&D (gramasi, waktu, ukuran jadi) disimpan di lembar yang sama — satu lembar teknis per SPK.
    var isCamSectionVisible by remember(order.id, initialShowCamSection, isCamStage, isRdStage) {
        mutableStateOf(initialShowCamSection || isCamStage || isRdStage)
    }
    // Terkunci begitu SPK meninggalkan tahap masuk (rajut: masuk Program CAM).
    val isFlowLocked = order.currentStage.kind != StageKind.ENTRY_ANCHOR || isCamSectionVisible
    var camSections by remember(order.id) {
        val saved = order.stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
        // Pakai seluruh section tersimpan (bukan hanya CAM_SECTION_SPECS) agar hasil R&D ikut terbawa.
        mutableStateOf(
            saved?.sections ?: CAM_SECTION_SPECS.map { StageInputSection(section = it.sectionName, rows = emptyList()) }
        )
    }

    // Pada tahap SPK Masuk (NEW_INTAKE), section alur proses awalnya belum muncul (tinggal detail saja)
    // kecuali jika diminta secara eksplisit atau sudah melewati tahap SPK Masuk.
    var isFlowSectionVisible by remember(order.id, initialShowFlowSection, initialShowCamSection) {
        mutableStateOf(initialShowFlowSection || initialShowCamSection || order.stageCode != SamplingPipelineStage.NEW_INTAKE.toStageCode())
    }
    var camValidationTrigger by remember(order.id) { mutableStateOf(0) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val printer = rememberPdfPrintLauncher()

    // Kartu SPK A6 — dibuka manual dari tombol header. Pembukaan otomatis saat "Mulai Pembuatan"
    // ada di SamplingWorkspaceScreen, setelah server mengonfirmasi pindah tahap. Urgensi & antrean
    // dihitung server saat PDF dibuka, jadi kartu yang keluar selalu segar.
    val openSpkCard = {
        printer.open {
            spkCardPdfUrl(it, TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, order.id.value))
        }
    }

    LaunchedEffect(isFlowSectionVisible) {
        if (isFlowSectionVisible && (order.stageCode == SamplingPipelineStage.NEW_INTAKE.toStageCode() || initialShowFlowSection) && !isCamSectionVisible) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    // Scroll ke lembar CAM hanya saat section dibuka lewat aksi "Mulai CAM" di gerbang awal;
    // di tahap CAM/R&D lembar sudah tampil sejak awal — jangan ganggu posisi scroll pembuka.
    LaunchedEffect(isCamSectionVisible) {
        if (isCamSectionVisible && !isCamStage && !isRdStage) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.72f)
                .widthIn(min = 680.dp, max = 960.dp)
                .heightIn(max = 780.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Column(
                modifier = Modifier.padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = "Detail SPK — ${order.spkNumber.value}",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        val subtitle = if (!order.sizeLabel.isNullOrBlank()) {
                            "${order.clientName} · ${order.styleName} (Size ${order.sizeLabel})"
                        } else {
                            "${order.clientName} · ${order.styleName}"
                        }
                        Text(
                            text = subtitle,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (isCamStage || isRdStage) {
                        DraftSaveIndicator(draftSaveStatus)
                    }
                    if (canPrintSpkCard) {
                        ClayButton(
                            text = "Kartu SPK A6",
                            style = ClayButtonStyle.Secondary,
                            fontSize = 11.sp,
                            onClick = openSpkCard
                        )
                    }
                    ClayBadge(
                        text = order.currentStage.displayName,
                        tint = Color(order.currentStage.colorHex)
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        IconClose(modifier = Modifier.size(18.dp))
                    }
                }

                printer.error?.let { Text(text = it, fontSize = 11.sp, color = WeMadeColors.Error) }

                // Konten yang dapat di-scroll: referensi klien di atas, alur proses di bawah
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClientSamplingReferenceCard(order)

                    // Alur Proses Khusus SPK / Desain Ini (muncul saat tentukan alur desain)
                    if (isFlowSectionVisible && processFlowViewModel != null) {
                        LaunchedEffect(order.id) {
                            processFlowViewModel.onEvent(
                                ProcessFlowUiEvent.SelectScope(
                                    ProcessFlowScope.Design(
                                        orderId = order.id.value,
                                        styleName = order.styleName,
                                        spkNumber = order.spkNumber.value
                                    )
                                )
                            )
                        }
                        ProcessFlowAdjusterPanel(
                            viewModel = processFlowViewModel,
                            hideScopeSelector = true,
                            isLocked = isFlowLocked,
                            frame = order.effectiveFrame(stageFlow)
                        )
                    }

                    if (isCamSectionVisible) {
                        CamProgramTabbedSection(
                            sections = camSections,
                            onSectionsChange = {
                                camSections = it
                                // Gerbang (SPK Masuk/Penentuan Alur) belum punya lembar CAM resmi;
                                // isinya baru tersimpan lewat "Simpan & Masuk Program CAM".
                                if (isCamStage || isRdStage) onDraftChange(it)
                            },
                            validationTrigger = camValidationTrigger
                        )
                    }

                    if (isRdStage) {
                        RdResultSection(
                            sections = camSections,
                            onSectionsChange = {
                                camSections = it
                                if (isCamStage || isRdStage) onDraftChange(it)
                            },
                            availableMaterials = availableMaterials
                        )
                    }
                }

                // Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    if (onCreateTechPack != null) {
                        ClayButton(
                            text = "Buat Tech Pack",
                            style = ClayButtonStyle.Secondary,
                            modifier = Modifier.weight(1f),
                            onClick = { onCreateTechPack(order) }
                        )
                    }
                    ClayButton(
                        text = "Tutup",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    if (isCamStage) {
                        ClayButton(
                            text = "Mulai Pembuatan ->",
                            style = ClayButtonStyle.Accent,
                            modifier = Modifier.weight(2f),
                            enabled = !isSubmitting,
                            onClick = {
                                val (currentTabs, _) = parseCamSections(camSections)
                                if (currentTabs.isEmpty() || currentTabs.any { !it.isComplete }) {
                                    camValidationTrigger++
                                } else {
                                    // Kartu A6 dibuka oleh layar setelah server mengonfirmasi SPK
                                    // masuk lantai produksi (lihat SamplingUiState.spkCardToPrint).
                                    onSubmitCamProgram(camSections)
                                }
                            }
                        )
                    }
                    if (isGateStage) {
                        if (!isFlowSectionVisible) {
                            ClayButton(
                                text = "Tentukan Alur Desain ->",
                                style = ClayButtonStyle.Primary,
                                modifier = Modifier.weight(2f),
                                enabled = !isSubmitting,
                                onClick = {
                                    isFlowSectionVisible = true
                                    onDetermineFlow()
                                    coroutineScope.launch {
                                        delay(120)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            )
                        } else if (!isCamSectionVisible) {
                            ClayButton(
                                text = "Alur Siap -> Mulai CAM",
                                style = ClayButtonStyle.Primary,
                                modifier = Modifier.weight(2f),
                                enabled = !isSubmitting,
                                onClick = {
                                    isCamSectionVisible = true
                                    coroutineScope.launch {
                                        delay(120)
                                        scrollState.animateScrollTo(scrollState.maxValue)
                                    }
                                }
                            )
                        } else {
                            ClayButton(
                                text = "Simpan & Masuk Program CAM",
                                style = ClayButtonStyle.Primary,
                                modifier = Modifier.weight(2f),
                                enabled = !isSubmitting,
                                onClick = {
                                    val (currentTabs, _) = parseCamSections(camSections)
                                    if (currentTabs.isEmpty() || currentTabs.any { !it.isComplete }) {
                                        camValidationTrigger++
                                    } else {
                                        onSubmitCamProgram(camSections)
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Pengganti tombol "Simpan Program": memberi tahu tim bahwa ketikan sudah tersimpan otomatis. */
@Composable
private fun DraftSaveIndicator(status: DraftSaveStatus) {
    val (text, color) = when (status) {
        DraftSaveStatus.Idle -> return
        DraftSaveStatus.Saving -> "Menyimpan…" to WeMadeColors.OnSurfaceMuted
        DraftSaveStatus.Saved -> "Tersimpan" to WeMadeColors.Success
        is DraftSaveStatus.Failed -> "Gagal menyimpan — ubah lagi untuk mencoba ulang" to WeMadeColors.Error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        // Glyph ✓ tidak ada di Nunito (tampil tofu di web) — pakai ikon vektor.
        if (status == DraftSaveStatus.Saved) IconCheck(modifier = Modifier.size(12.dp), color = color)
        Text(text = text, fontSize = 11.sp, color = color, maxLines = 1)
    }
}
