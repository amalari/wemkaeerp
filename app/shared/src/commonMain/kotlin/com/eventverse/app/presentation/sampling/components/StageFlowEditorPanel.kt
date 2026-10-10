package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.stageflow.IndustryTemplateCode
import com.eventverse.app.domain.stageflow.StageCode
import com.eventverse.app.domain.stageflow.StageDefinition
import com.eventverse.app.domain.stageflow.StageKind
import com.eventverse.app.domain.stageflow.StageTrait
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCheckbox
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.sampling.StageFlowEditorUiEvent
import com.eventverse.app.presentation.sampling.StageFlowEditorViewModel
import com.eventverse.app.presentation.sampling.moveDownTarget
import com.eventverse.app.presentation.sampling.moveUpTarget
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Editor kerangka tahap pabrik (TRD-FLOW-001 Tahap 3c): ganti nama, urutkan, tambah, dan hapus
 * tahap kerja, atau ganti ke template industri lain. Tahap masuk/keluar tampil terkunci.
 *
 * Perubahan hanya berlaku untuk SPK yang belum masuk lantai — SPK yang sudah jalan memegang
 * kerangka bekunya sendiri (FR-5b). Penolakan (tahap QC terakhir, proses yang masih berjangkar,
 * wewenang) datang dari server dan ditampilkan apa adanya.
 */
@Composable
fun StageFlowEditorPanel(viewModel: StageFlowEditorViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(viewModel) { viewModel.onEvent(StageFlowEditorUiEvent.Load) }
    var editing by remember { mutableStateOf<StageCode?>(null) }

    Column(
        modifier = modifier.fillMaxWidth().clayFlat(shape = ClayShapes.Card, background = WeMadeColors.SurfaceMuted, outline = WeMadeColors.Outline).padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("KERANGKA TAHAP", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Spacer(Modifier.width(ClaySpacing.Sm))
            state.template?.let { ClayTag(text = "Template: ${it.displayName}", tint = WeMadeColors.Info) }
        }
        Text(
            "Berlaku untuk SPK yang belum masuk lantai produksi. SPK yang sudah berjalan tetap memakai kerangka saat ia mulai.",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
        state.message?.let {
            ClayStatusBanner(message = it, isError = state.isError, onDismiss = { viewModel.onEvent(StageFlowEditorUiEvent.DismissMessage) })
        }

        state.stages.forEachIndexed { index, stage ->
            if (editing == stage.code) {
                StageRenameRow(stage, isBusy = state.isBusy, onCancel = { editing = null }) { name, label ->
                    viewModel.onEvent(StageFlowEditorUiEvent.Rename(stage.code, name, label)); editing = null
                }
            } else {
                StageRow(
                    step = index + 1,
                    stage = stage,
                    canMoveUp = moveUpTarget(state.stages, stage.code) != null,
                    canMoveDown = moveDownTarget(state.stages, stage.code) != null,
                    isBusy = state.isBusy,
                    onEdit = { editing = stage.code },
                    onEvent = viewModel::onEvent
                )
            }
        }

        AddStageForm(stages = state.stages, isBusy = state.isBusy, onAdd = viewModel::onEvent)
        TemplateResetRow(current = state.template, isBusy = state.isBusy, onReset = { viewModel.onEvent(StageFlowEditorUiEvent.Reset(it)) })
    }
}

@Composable
private fun StageRow(
    step: Int,
    stage: StageDefinition,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    isBusy: Boolean,
    onEdit: () -> Unit,
    onEvent: (StageFlowEditorUiEvent) -> Unit
) {
    val isWork = stage.kind == StageKind.WORK
    Row(
        modifier = Modifier.fillMaxWidth().clayFlat(shape = ClayShapes.Tile, background = WeMadeColors.Surface, outline = WeMadeColors.Outline).padding(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        // Warna tahap = data tenant (design-system-rules Kontrak 1, pengecualian 1).
        ClayTag(text = "$step", tint = Color(stage.colorHex))
        Column(modifier = Modifier.weight(1f)) {
            Text(stage.displayName, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(stage.shortLabel, "meja operator".takeIf { stage.has(StageTrait.OPERATOR_DESK) }).joinToString(" · "),
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (!isWork) {
            ClayTag(text = "Terkunci", tint = WeMadeColors.OnSurfaceMuted)
        }
        // Tahap masuk/keluar hanya boleh diganti namanya — kodenya dipakai invoice & surat jalan.
        ClayButton(text = "Ubah", style = ClayButtonStyle.Ghost, fontSize = 11.sp, enabled = !isBusy, onClick = onEdit)
        if (isWork) {
            ClayButton(text = "Naik", style = ClayButtonStyle.Secondary, fontSize = 11.sp, enabled = !isBusy && canMoveUp,
                onClick = { onEvent(StageFlowEditorUiEvent.MoveUp(stage.code)) })
            ClayButton(text = "Turun", style = ClayButtonStyle.Secondary, fontSize = 11.sp, enabled = !isBusy && canMoveDown,
                onClick = { onEvent(StageFlowEditorUiEvent.MoveDown(stage.code)) })
            ClayButton(text = "Hapus", style = ClayButtonStyle.Danger, fontSize = 11.sp, enabled = !isBusy,
                onClick = { onEvent(StageFlowEditorUiEvent.Remove(stage.code)) })
        }
    }
}

@Composable
private fun StageRenameRow(stage: StageDefinition, isBusy: Boolean, onCancel: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(stage.code) { mutableStateOf(stage.displayName) }
    var label by remember(stage.code) { mutableStateOf(stage.shortLabel) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
        ClayTextField(value = name, onValueChange = { name = it }, label = "Nama tahap", modifier = Modifier.weight(2f))
        ClayTextField(value = label, onValueChange = { label = it }, label = "Label pendek", modifier = Modifier.weight(1f))
        ClayButton(text = "Simpan", enabled = !isBusy && name.isNotBlank(), onClick = { onSave(name, label) })
        ClayButton(text = "Batal", style = ClayButtonStyle.Ghost, onClick = onCancel)
    }
}

@Composable
private fun AddStageForm(stages: List<StageDefinition>, isBusy: Boolean, onAdd: (StageFlowEditorUiEvent) -> Unit) {
    // Jangkar sisip: tahap masuk & kerja (bukan tahap keluar — sesudahnya tidak ada tempat kerja).
    val anchors = stages.filter { it.kind != StageKind.EXIT_ANCHOR }
    var name by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }
    var after by remember(anchors) { mutableStateOf(anchors.lastOrNull { it.kind == StageKind.WORK }?.code ?: anchors.lastOrNull()?.code) }
    var isDesk by remember { mutableStateOf(true) }

    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Text("TAMBAH TAHAP", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurfaceMuted)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTextField(value = name, onValueChange = { name = it }, placeholder = "Nama, mis. Aplikasi Kain", modifier = Modifier.weight(2f))
            ClayTextField(value = label, onValueChange = { label = it }, placeholder = "Label pendek", modifier = Modifier.widthIn(max = 180.dp).weight(1f))
            ClayCheckbox(checked = isDesk, onCheckedChange = { isDesk = it })
            Text("Meja operator", fontSize = 11.sp, color = WeMadeColors.OnSurface)
        }
        Text("Sisipkan sesudah:", fontSize = 10.sp, color = WeMadeColors.OnSurfaceMuted)
        ClayFlowRow {
            anchors.forEach { stage -> ClayChoiceChip(text = stage.shortLabel, selected = after == stage.code, onClick = { after = stage.code }) }
        }
        ClayButton(
            text = "Tambah Tahap",
            enabled = !isBusy && name.isNotBlank() && after != null,
            onClick = {
                after?.let { onAdd(StageFlowEditorUiEvent.Add(name, label, it, isDesk)) }
                name = ""; label = ""
            }
        )
    }
}

@Composable
private fun TemplateResetRow(current: IndustryTemplateCode?, isBusy: Boolean, onReset: (IndustryTemplateCode) -> Unit) {
    var pending by remember { mutableStateOf<IndustryTemplateCode?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        Text("GANTI KE TEMPLATE INDUSTRI", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = WeMadeColors.OnSurfaceMuted)
        ClayFlowRow {
            IndustryTemplateCode.entries.forEach { template ->
                ClayChoiceChip(text = template.displayName, selected = pending == template, enabled = !isBusy, onClick = { pending = template })
            }
        }
        pending?.let { target ->
            // Konfirmasi eksplisit: reset membuang tahap kustom & nama yang sudah disunting.
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                Text(
                    if (target == current) "Pulihkan template ${target.displayName}? Suntingan tahap akan hilang."
                    else "Ganti kerangka ke ${target.displayName}? Suntingan tahap akan hilang.",
                    fontSize = 11.sp,
                    color = WeMadeColors.Error,
                    modifier = Modifier.weight(1f, fill = false)
                )
                ClayButton(text = "Ya, ganti", style = ClayButtonStyle.Danger, fontSize = 11.sp, enabled = !isBusy, onClick = { onReset(target); pending = null })
                ClayButton(text = "Batal", style = ClayButtonStyle.Ghost, fontSize = 11.sp, onClick = { pending = null })
            }
        }
    }
}
