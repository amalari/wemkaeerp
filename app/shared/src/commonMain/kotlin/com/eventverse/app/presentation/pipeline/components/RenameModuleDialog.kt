package com.eventverse.app.presentation.pipeline.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.pipeline.PipelineNode
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Renames one module for the current tenant.
 *
 * The new name is persisted against this tenant's pipeline node only — the same built-in
 * module keeps its own name for every other factory.
 */
@Composable
fun RenameModuleDialog(
    node: PipelineNode,
    isSaving: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Keyed on the node so reopening the dialog for a different module starts from that
    // module's current name rather than the previously edited one.
    var draftName by remember(node.id) { mutableStateOf(node.title) }
    val trimmedName = draftName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        // Bentuknya diwarisi dari MaterialTheme.shapes.extraLarge (ClayShapes 24dp), jadi tidak
        // perlu di-override di sini lagi.
        containerColor = WeMadeColors.Surface,
        title = {
            Text(
                text = "Ubah Nama Modul",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Nama ini hanya berlaku untuk pabrik ini. Modul bawaan " +
                        "\"${node.module.displayName}\" tetap bernama asli di tenant lain.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
                OutlinedTextField(
                    value = draftName,
                    onValueChange = { draftName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isSaving,
                    isError = trimmedName.isBlank(),
                    label = { Text("Nama modul", fontSize = 12.sp) },
                    shape = RoundedCornerShape(8.dp)
                )
                if (trimmedName.isBlank()) {
                    Text(
                        text = "Nama modul tidak boleh kosong.",
                        fontSize = 11.sp,
                        color = WeMadeColors.Error
                    )
                }
            }
        },
        confirmButton = {
            ClayButton(
                text = if (isSaving) "Menyimpan…" else "Simpan",
                onClick = { onConfirm(trimmedName) },
                enabled = !isSaving && trimmedName.isNotBlank() && trimmedName != node.title,
                fontSize = 12.sp
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Batal", fontSize = 12.sp)
            }
        }
    )
}
