package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.LeadFieldProjection
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * The "detail" panel: full field-by-field view of one lead, core fields first, then the
 * tenant's active custom fields — one uniform list via [LeadFieldProjection], so a custom
 * field an admin just added appears here with zero UI change.
 */
@Composable
fun LeadInspectorPane(
    lead: CrmLead?,
    schema: List<LeadFieldDescriptor>,
    employees: List<OrgNode>,
    canWrite: Boolean,
    canManage: Boolean,
    onCommitField: (fieldId: String, value: JsonValue.Obj?) -> Unit,
    onUpdateStage: (LeadStage) -> Unit,
    onArchive: () -> Unit,
    onAddField: () -> Unit,
    onDeleteField: ((fieldId: String) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var fieldPendingDeletion by remember { mutableStateOf<LeadFieldDescriptor?>(null) }

    if (lead == null) {
        Column(
            modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl),
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "Pilih lead di kiri untuk melihat detailnya.", fontSize = 13.sp, color = WeMadeColors.OnSurfaceMuted)
        }
        return
    }

    val cells = LeadFieldProjection.cellsOf(lead)

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(ClaySpacing.Xxl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(text = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Detail Lead" }), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
                lead.whatsappNumber?.let { number ->
                    Text(
                        text = "Chat WA: ${number.value}",
                        fontSize = 12.sp,
                        color = WeMadeColors.Info
                    )
                }
            }
            if (onClose != null) {
                ClayButton(text = "Tutup", onClick = onClose, style = ClayButtonStyle.Ghost)
            }
        }

        StageRow(current = lead.stage, canWrite = canWrite, onUpdateStage = onUpdateStage)

        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Detail Inti", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
            schema.filter { it.isCore && it.fieldId != LeadFieldDescriptor.coreFieldId("stage") }.forEach { descriptor ->
                LeadCustomField(
                    descriptor = descriptor,
                    cell = cells[descriptor.fieldId],
                    editable = canWrite,
                    employees = employees,
                    onCommit = { value -> onCommitField(descriptor.fieldId, value) }
                )
            }
        }

        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Properti Kustom", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                if (canManage) {
                    ClayButton(text = "+ Kolom", onClick = onAddField, style = ClayButtonStyle.Secondary)
                }
            }

            val customFields = schema.filter { !it.isCore }
            if (customFields.isEmpty()) {
                Text(
                    text = "Belum ada properti kustom untuk modul ini.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Md)
                )
            } else {
                customFields.forEach { descriptor ->
                    LeadCustomField(
                        descriptor = descriptor,
                        cell = cells[descriptor.fieldId],
                        editable = canWrite,
                        employees = employees,
                        onDelete = if (canManage && descriptor.isDeletable && onDeleteField != null) {
                            { fieldPendingDeletion = descriptor }
                        } else null,
                        onCommit = { value -> onCommitField(descriptor.fieldId, value) }
                    )
                }
            }
        }

        if (canWrite) {
            ClayButton(text = "Arsipkan Lead", onClick = onArchive, style = ClayButtonStyle.Danger)
        }
    }

    val pending = fieldPendingDeletion
    if (pending != null) {
        Dialog(onDismissRequest = { fieldPendingDeletion = null }) {
            ClayCard(modifier = Modifier.width(380.dp)) {
                Text(
                    text = "Hapus Kolom Kustom?",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Kolom \"${pending.label}\" akan diarsipkan dari form lead. Data yang sudah tersimpan sebelumnya tetap tersimpan di riwayat sistem.",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(vertical = ClaySpacing.Md)
                )
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = { fieldPendingDeletion = null },
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "Hapus Kolom",
                        onClick = {
                            val idToDelete = pending.fieldId
                            fieldPendingDeletion = null
                            onDeleteField?.invoke(idToDelete)
                        },
                        style = ClayButtonStyle.Danger,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StageRow(current: LeadStage, canWrite: Boolean, onUpdateStage: (LeadStage) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text(text = "Tahap", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
        Row(modifier = Modifier.padding(top = ClaySpacing.Sm)) {
            ClayBadge(
                text = current.displayName,
                tint = current.tint(),
                modifier = if (canWrite) Modifier.clickable { expanded = true } else Modifier
            )
        }

        androidx.compose.material3.DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LeadStage.entries.filter { current.canTransitionTo(it) }.forEach { stage ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(stage.displayName) },
                    onClick = {
                        expanded = false
                        onUpdateStage(stage)
                    }
                )
            }
        }
    }
}
