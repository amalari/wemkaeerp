package com.eventverse.app.presentation.crm.components

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconStar
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu kolom vertikal di dalam papan Kanban CRM:
 * - Border warna tegas per stage (Qualified hijau, Unqualified merah, New Lead biru)
 * - Empty state tanpa kotak kartu, terpusat secara vertikal dengan ikon vector Skiko
 * - Tombol tambah di Qualified menggunakan warna hijau (Success)
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
    val totalValue = leads.sumOf { it.estimatedValue?.amount ?: 0L }

    val dragDropState = LocalCrmDragDropState.current
    val isDropTarget = dragDropState?.isDragging == true &&
            dragDropState.hoveredStage == stage &&
            dragDropState.draggedLead?.stage != stage

    val columnOutline = when {
        isDropTarget -> stage.tint()
        stage == LeadStage.NEW_LEAD -> WeMadeColors.Primary
        stage == LeadStage.QUALIFIED -> WeMadeColors.Success
        stage == LeadStage.UNQUALIFIED -> WeMadeColors.Error
        else -> WeMadeColors.Outline
    }

    DisposableEffect(stage) {
        onDispose {
            dragDropState?.unregisterColumn(stage)
        }
    }

    Column(
        modifier = modifier
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    dragDropState?.registerColumn(
                        stage,
                        Rect(coords.positionInWindow(), coords.size.toSize())
                    )
                }
            }
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isDropTarget) {
                    when (stage) {
                        LeadStage.NEW_LEAD -> WeMadeColors.PrimaryContainer
                        LeadStage.QUALIFIED -> WeMadeColors.SuccessBg
                        LeadStage.UNQUALIFIED -> WeMadeColors.ErrorBg
                    }
                } else WeMadeColors.SurfaceMuted,
                outline = columnOutline,
                borderWidth = if (isDropTarget) ClayBorder.Thick else ClayBorder.Medium
            )
            .padding(ClaySpacing.Lg)
    ) {
        // Drop Banner saat kartu di-drag melayang di atas kolom ini
        if (isDropTarget) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = ClaySpacing.Md)
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.Surface,
                        outline = columnOutline,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Lepas kartu untuk pindah ke ${stage.displayName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = stage.tint()
                )
            }
        }

        // Header Kolom: Nama Stage + Counter Tag + Tombol Tambah
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                ClayBadge(
                    text = stage.displayName,
                    tint = stage.tint(),
                    fontSize = 12.sp
                )
                ClayTag(
                    text = "${leads.size}",
                    tint = stage.tint(),
                    fontSize = 11.sp
                )
            }

            // Qualified Lead dan New Lead bisa langsung ditambahkan (+ Tambah), Unqualified tidak ada
            if (stage != LeadStage.UNQUALIFIED && onAddLead != null) {
                ClayButton(
                    text = "+ Tambah",
                    style = if (stage == LeadStage.QUALIFIED) ClayButtonStyle.Success else ClayButtonStyle.Primary,
                    fontSize = 11.sp,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                    onClick = onAddLead
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        // Sub-Header: Ringkasan Nilai Kolom
        Text(
            text = when (stage) {
                LeadStage.NEW_LEAD -> "Inquiry Masuk • Nilai Belum Diestimasi"
                LeadStage.QUALIFIED -> if (totalValue > 0) "Total: ${formatRupiah(totalValue)}" else "Total: Rp 0"
                LeadStage.UNQUALIFIED -> "${leads.size} Prospek Dibatalkan"
            },
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted
        )

        Spacer(Modifier.height(ClaySpacing.Lg))

        // Konten Kartu Lead / Empty State Terpusat Vertikal Tanpa Kotak Inner Card
        if (leads.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = ClaySpacing.Md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    when (stage) {
                        LeadStage.NEW_LEAD -> IconInbox(modifier = Modifier.size(48.dp), color = WeMadeColors.Primary)
                        LeadStage.QUALIFIED -> IconStar(modifier = Modifier.size(48.dp), color = WeMadeColors.Success)
                        LeadStage.UNQUALIFIED -> IconBan(modifier = Modifier.size(48.dp), color = WeMadeColors.Error)
                    }

                    Text(
                        text = when (stage) {
                            LeadStage.NEW_LEAD -> "Belum Ada Inquiry"
                            LeadStage.QUALIFIED -> "Belum Ada Qualified Lead"
                            LeadStage.UNQUALIFIED -> "Pipeline Bersih"
                        },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    Text(
                        text = when (stage) {
                            LeadStage.NEW_LEAD -> "Mulai catat kontak atau brand baru dari WhatsApp, pameran, maupun referral."
                            LeadStage.QUALIFIED -> "Prospek dengan kebutuhan jelas dan nilai estimasi tender akan muncul di sini."
                            LeadStage.UNQUALIFIED -> "Lead yang batal, duplikat, atau tidak sesuai kriteria diarsipkan di kolom ini."
                        },
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )

                    if (canWrite && onAddLead != null && stage != LeadStage.UNQUALIFIED) {
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        ClayButton(
                            text = if (stage == LeadStage.QUALIFIED) "+ Tambah Qualified" else "+ Tambah Inquiry",
                            style = if (stage == LeadStage.QUALIFIED) ClayButtonStyle.Success else ClayButtonStyle.Primary,
                            fontSize = 12.sp,
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            onClick = onAddLead
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
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
}
