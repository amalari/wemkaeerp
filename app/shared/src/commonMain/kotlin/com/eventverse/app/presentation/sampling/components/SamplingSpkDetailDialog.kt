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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.StageInputSection
import com.eventverse.app.domain.sampling.StageSectionNames
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconClose
import com.eventverse.app.presentation.sampling.ProcessFlowScope
import com.eventverse.app.presentation.sampling.ProcessFlowUiEvent
import com.eventverse.app.presentation.sampling.ProcessFlowViewModel
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
    onStartCam: () -> Unit,
    onSubmitCamProgram: (List<StageInputSection>) -> Unit = {},
    onDetermineFlow: () -> Unit = {},
    onCreateTechPack: ((SamplingOrder) -> Unit)? = null,
    processFlowViewModel: ProcessFlowViewModel? = null,
    initialShowFlowSection: Boolean = false
) {
    // Gerbang klien: tombol mulai CAM hanya tampil pada tahap SPK Masuk / Penentuan Alur.
    val isGateStage = order.pipelineStage == SamplingPipelineStage.NEW_INTAKE ||
        order.pipelineStage == SamplingPipelineStage.FLOW_REVIEW

    // Tahap Program CAM: alur sudah final (dikunci) dan section Program dibuka untuk tim sampling.
    val isCamStage = order.pipelineStage == SamplingPipelineStage.CAM_PROGRAMMING
    val isFlowLocked = order.pipelineStage.order >= SamplingPipelineStage.CAM_PROGRAMMING.order
    var camSections by remember(order.id) {
        val saved = order.stageInputFor(SamplingPipelineStage.CAM_PROGRAMMING)
        mutableStateOf(
            CAM_SECTION_SPECS.map { spec ->
                saved?.section(spec.sectionName) ?: StageInputSection(section = spec.sectionName, rows = emptyList())
            }
        )
    }

    // Pada tahap SPK Masuk (NEW_INTAKE), section alur proses awalnya belum muncul (tinggal detail saja)
    // kecuali jika diminta secara eksplisit atau sudah melewati tahap SPK Masuk.
    var isFlowSectionVisible by remember(order.id, initialShowFlowSection) {
        mutableStateOf(initialShowFlowSection || order.pipelineStage != SamplingPipelineStage.NEW_INTAKE)
    }
    var camValidationTrigger by remember(order.id) { mutableStateOf(0) }

    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(isFlowSectionVisible) {
        if (isFlowSectionVisible && (order.pipelineStage == SamplingPipelineStage.NEW_INTAKE || initialShowFlowSection)) {
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
                        Text(
                            text = "${order.clientName} · ${order.styleName}",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    ClayBadge(
                        text = order.pipelineStage.displayName,
                        tint = samplingStageTint(order.pipelineStage)
                    )
                    androidx.compose.material3.IconButton(onClick = onDismiss) {
                        IconClose(modifier = Modifier.size(18.dp))
                    }
                }

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
                            isLocked = isFlowLocked
                        )
                    }

                    if (isCamStage) {
                        CamProgramTabbedSection(
                            sections = camSections,
                            onSectionsChange = { camSections = it },
                            validationTrigger = camValidationTrigger
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
                            text = "Simpan Program -> Masuk Mesin Rajut",
                            style = ClayButtonStyle.Accent,
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
                        } else {
                            ClayButton(
                                text = "Alur Siap -> Mulai CAM",
                                style = ClayButtonStyle.Primary,
                                modifier = Modifier.weight(2f),
                                enabled = !isSubmitting,
                                onClick = onStartCam
                            )
                        }
                    }
                }
            }
        }
    }
}
