package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconPlus
import com.eventverse.app.presentation.designsystem.IconStar
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu kolom vertikal di papan Kanban CRM, bergaya papan tugas:
 * - Header berupa bar clay berwarna stage + gelembung jumlah lead
 * - Badan kolom ber-tint lembut warna stage, sehingga kartu di dalamnya boleh netral
 * - Aksi "+ Tambah" di dasar kolom, hanya bila pemanggil memberi [onAddLead] (saat ini New Lead)
 * - Mendukung drop-target highlighting saat kartu Kanban di-drag
 */
@Composable
fun CrmKanbanColumn(
    stage: LeadStage,
    leads: List<CrmLead>,
    employees: List<OrgNode>,
    selectedLeadId: LeadId?,
    canWrite: Boolean,
    onSelectLead: (LeadId) -> Unit,
    onUpdateStage: (LeadId, LeadStage) -> Unit,
    onAddLead: (() -> Unit)?,
    onOpenActivities: (CrmLead) -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = stage.tint()
    val totalValue = leads.sumOf { it.estimatedValue?.amount ?: 0L }
    val canAdd = canWrite && onAddLead != null

    val dragDropState = LocalCrmDragDropState.current
    val isDropTarget = dragDropState?.isDragging == true &&
            dragDropState.hoveredStage == stage &&
            dragDropState.draggedLead?.stage != stage

    DisposableEffect(stage) {
        onDispose { dragDropState?.unregisterColumn(stage) }
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    dragDropState?.registerColumn(stage, Rect(coords.positionInWindow(), coords.size.toSize()))
                }
            }
            .clayFlat(
                shape = ClayShapes.Panel,
                background = tint.copy(alpha = if (isDropTarget) 0.18f else 0.07f),
                outline = if (isDropTarget) tint else tint.copy(alpha = 0.30f),
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        ColumnHeader(title = stage.displayName, count = leads.size, tint = tint)

        Text(
            text = when {
                isDropTarget -> "Lepas kartu untuk pindah ke ${stage.displayName}"
                stage == LeadStage.NEW_LEAD -> "Inquiry masuk · nilai belum diestimasi"
                stage == LeadStage.FOLLOW_UP -> "Sedang digali kebutuhannya"
                stage == LeadStage.QUALIFIED -> "Total: ${formatRupiah(totalValue)}"
                else -> "${leads.size} prospek dibatalkan"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDropTarget) tint else WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Md)
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (leads.isEmpty()) {
                ColumnEmptyState(stage = stage, modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    contentPadding = PaddingValues(bottom = ClaySpacing.Xs)
                ) {
                    items(leads, key = { it.id.value }) { lead ->
                        CrmKanbanCard(
                            lead = lead,
                            employees = employees,
                            selected = lead.id == selectedLeadId,
                            canWrite = canWrite,
                            onSelectLead = onSelectLead,
                            onUpdateStage = { targetStage -> onUpdateStage(lead.id, targetStage) },
                            onOpenActivities = onOpenActivities
                        )
                    }
                }
            }
        }

        if (canAdd && onAddLead != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = ClaySpacing.Sm)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onAddLead
                    )
                    .pointerHoverIcon(PointerIcon.Hand)
                    .padding(vertical = ClaySpacing.Md),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconPlus(Modifier.size(12.dp), color = tint)
                Spacer(Modifier.width(ClaySpacing.Xs))
                Text(text = "Tambah lead", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = tint)
            }
        }
    }
}

/** Bar judul kolom: permukaan clay berwarna stage, judul putih, gelembung jumlah di kanan. */
@Composable
private fun ColumnHeader(title: String, count: Int, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Button,
                background = tint,
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WeMadeColors.Surface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(ClaySpacing.Sm))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clayFlat(
                    shape = CircleShape,
                    background = WeMadeColors.Surface.copy(alpha = 0.25f),
                    outline = WeMadeColors.Surface.copy(alpha = 0.55f),
                    borderWidth = ClayBorder.Medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Surface)
        }
    }
}

@Composable
private fun ColumnEmptyState(stage: LeadStage, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = ClaySpacing.Lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        when (stage) {
            LeadStage.NEW_LEAD -> IconInbox(modifier = Modifier.size(40.dp), color = stage.tint())
            LeadStage.FOLLOW_UP -> IconChat(modifier = Modifier.size(40.dp), color = stage.tint())
            LeadStage.QUALIFIED -> IconStar(modifier = Modifier.size(40.dp), color = stage.tint())
            LeadStage.UNQUALIFIED -> IconBan(modifier = Modifier.size(40.dp), color = stage.tint())
        }
        Text(
            text = when (stage) {
                LeadStage.NEW_LEAD -> "Belum Ada Inquiry"
                LeadStage.FOLLOW_UP -> "Tidak Ada yang Di-follow Up"
                LeadStage.QUALIFIED -> "Belum Ada Qualified Lead"
                LeadStage.UNQUALIFIED -> "Pipeline Bersih"
            },
            style = MaterialTheme.typography.titleSmall,
            color = WeMadeColors.OnSurface
        )
        Text(
            text = when (stage) {
                LeadStage.NEW_LEAD -> "Mulai catat kontak atau brand baru dari WhatsApp, pameran, maupun referral."
                LeadStage.FOLLOW_UP -> "Pindahkan lead ke sini begitu sales mulai menghubungi dan menggali kebutuhannya."
                LeadStage.QUALIFIED -> "Prospek dengan kebutuhan jelas dan nilai estimasi tender akan muncul di sini."
                LeadStage.UNQUALIFIED -> "Lead yang batal, duplikat, atau tidak sesuai kriteria diarsipkan di kolom ini."
            },
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted,
            textAlign = TextAlign.Center,
            lineHeight = 18.sp
        )
    }
}
