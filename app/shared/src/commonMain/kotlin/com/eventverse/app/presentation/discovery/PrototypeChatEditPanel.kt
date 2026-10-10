package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.prototype.DeterministicSpecOpProposer
import com.eventverse.app.domain.prototype.SpecOp
import com.eventverse.app.domain.prototype.SpecOpApplier
import com.eventverse.app.infrastructure.api.BuilderApiClient
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.pack.InteractiveScreenCodec
import com.eventverse.app.shared.pack.SpecOpCodec
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Panel Chat Edit prototype di /builder/prototype (TRD-PLAT-003, butir A4).
 * Memungkinkan pengguna/klien mengubah spec prototype (status kanban, kolom tabel, transisi)
 * secara interaktif menggunakan bahasa biasa.
 *
 * Alur:
 * 1. Kirim instruksi ke POST /api/builder/draft/spec-ops (dengan fallback lokal [DeterministicSpecOpProposer]).
 * 2. Menerapkan usulan [SpecOp] lewat [SpecOpApplier.applyAll] pada layar yang dipilih.
 * 3. Memperbarui [PrototypeSession] dan layar pratinjau, menyimpan riwayat untuk Undo/Redo.
 * 4. Mencatat log [CaptureEntry] di sesi untuk ekspor brief (A5).
 */
@Composable
fun PrototypeChatEditPanel(
    screens: List<DiscoveryScreenUi>,
    session: PrototypeSession,
    onScreensUpdated: (List<DiscoveryScreenUi>) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactiveScreens = remember(screens) { screens.filter { it.interactive != null } }
    if (interactiveScreens.isEmpty()) return

    // Kunci = daftar id layar, bukan objeknya: spec yang berubah (hasil edit) tidak boleh mengembalikan pilihan ke layar pertama.
    var selectedScreenId by remember(screens.map { it.screenId }) {
        mutableStateOf(interactiveScreens.firstOrNull()?.screenId.orEmpty())
    }
    var messageText by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var operationResult by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var screenMenuOpen by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val client = remember { BuilderApiClient() }

    val selectedScreen = interactiveScreens.firstOrNull { it.screenId == selectedScreenId }
        ?: interactiveScreens.first()

    val suggestions = listOf(
        "tambah status Revisi setelah Dikerjakan",
        "ganti nama Selesai jadi Ditutup",
        "tambah kolom Prioritas",
        "izinkan Baru ke Selesai",
        "ubah jadi tabel",
        "ubah jadi kanban"
    )

    fun executeEdit(instruction: String) {
        if (instruction.isBlank() || isSubmitting) return
        val currentInteractive = selectedScreen.interactive ?: return
        isSubmitting = true
        operationResult = null
        isError = false

        scope.launch {
            val encodedSpec = InteractiveScreenCodec.encode(currentInteractive).encode()
            val proposedOps = runCatching {
                // Coba panggil server endpoint spec-ops
                val res = client.proposeSpecOps(instruction, selectedScreen.screenId, encodedSpec).getOrThrow()
                val opsArray = (res as? com.eventverse.app.shared.json.JsonValue.Obj)?.array("ops")
                opsArray?.mapNotNull { (it as? com.eventverse.app.shared.json.JsonValue.Obj)?.let { obj -> SpecOpCodec.decode(obj).getOrNull() } }
            }.getOrNull() ?: run {
                // Fallback lokal deterministik (offline / dev mode)
                DeterministicSpecOpProposer().propose(instruction, currentInteractive).getOrNull()
            }

            if (proposedOps.isNullOrEmpty()) {
                val isWidgetChange = instruction.contains("ubah jadi", ignoreCase = true) ||
                    instruction.contains("ganti ke", ignoreCase = true)
                isError = true
                operationResult = if (isWidgetChange) {
                    val targetWidget = if (instruction.contains("kanban", ignoreCase = true)) "kanban" else "tabel"
                    val entity = currentInteractive.spec.entities.firstOrNull { it.id == currentInteractive.spec.screens.firstOrNull()?.entityId }
                    val hasEnumStatus = entity?.fields?.any { it.type == com.eventverse.app.domain.prototype.FieldType.ENUM } == true
                    if (targetWidget == "kanban" && !hasEnumStatus) {
                        "Penolakan: Tidak dapat mengubah jadi kanban karena entitas tidak memiliki field status bertipe ENUM."
                    } else {
                        "Operasi ubah jadi $targetWidget belum didukung oleh mesin operasi aktif."
                    }
                } else {
                    "Belum bisa memahami permintaan itu. Gunakan contoh format yang didukung."
                }
                isSubmitting = false
                return@launch
            }

            val timestamp = Clock.System.now().toString()
            val applied = SpecOpApplier.applyAll(currentInteractive, proposedOps, timestamp)

            // Catat log perubahan untuk brief (A5)
            session.recordCapture(applied.log)

            val okCount = applied.log.count { it.ok }
            val failCount = applied.log.count { !it.ok }

            if (okCount > 0) {
                // Update spec di session
                session.updateScreenSpec(selectedScreen.screenId, currentInteractive, applied.screen)
                val updatedList = screens.map { s ->
                    if (s.screenId == selectedScreen.screenId) s.copy(interactive = applied.screen) else s
                }
                onScreensUpdated(updatedList)

                isError = false
                operationResult = if (failCount == 0) {
                    "$okCount perubahan berhasil diterapkan pada '${selectedScreen.title}'!"
                } else {
                    "$okCount diterapkan, $failCount ditolak (${applied.log.firstOrNull { !it.ok }?.message})."
                }
                messageText = ""
            } else {
                isError = true
                operationResult = applied.log.firstOrNull()?.message ?: "Perubahan ditolak oleh spec."
            }
            isSubmitting = false
        }
    }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            // Header bar & selector layar sasaran
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Edit Prototype Lewat Percakapan",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Text(
                        text = "Ubah status, kolom, atau transisi pada layar secara instan.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                // Pemilih layar
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                    Text("Layar:", style = MaterialTheme.typography.labelSmall, color = WeMadeColors.OnSurfaceMuted)
                    ClayButton(
                        text = selectedScreen.title,
                        style = ClayButtonStyle.Secondary,
                        onClick = { screenMenuOpen = true }
                    )
                    DropdownMenu(expanded = screenMenuOpen, onDismissRequest = { screenMenuOpen = false }) {
                        interactiveScreens.forEach { s ->
                            DropdownMenuItem(
                                text = { Text(s.title) },
                                onClick = {
                                    selectedScreenId = s.screenId
                                    screenMenuOpen = false
                                }
                            )
                        }
                    }
                }
            }

            // Input teks dan tombol aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier.weight(1f),
                    placeholder = "Contoh: tambah status Revisi setelah Dikerjakan"
                )

                ClayButton(
                    text = if (isSubmitting) "Memproses..." else "Terapkan",
                    style = ClayButtonStyle.Primary,
                    enabled = !isSubmitting && messageText.isNotBlank(),
                    onClick = { executeEdit(messageText) }
                )

                ClayButton(
                    text = "Undo",
                    style = ClayButtonStyle.Secondary,
                    enabled = session.canUndo(selectedScreen.screenId) && !isSubmitting,
                    onClick = {
                        val previous = session.undo(selectedScreen.screenId)
                        if (previous != null) {
                            val updatedList = screens.map { s ->
                                if (s.screenId == selectedScreen.screenId) s.copy(interactive = previous) else s
                            }
                            onScreensUpdated(updatedList)
                            operationResult = "Perubahan sebelumnya dibatalkan (Undo)."
                            isError = false
                        }
                    }
                )
            }

            // Saran cepat
            ClayFlowRow(spacing = ClaySpacing.Xs) {
                suggestions.forEach { suggestion ->
                    ClayChoiceChip(
                        text = suggestion,
                        selected = false,
                        onClick = {
                            messageText = suggestion
                            executeEdit(suggestion)
                        }
                    )
                }
            }

            // Pesan hasil / feedback
            operationResult?.let { res ->
                Text(
                    text = res,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isError) WeMadeColors.Defect else WeMadeColors.Success,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
