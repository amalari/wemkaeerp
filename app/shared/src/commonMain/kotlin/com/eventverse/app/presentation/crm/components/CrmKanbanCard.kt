package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
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
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconMail
import com.eventverse.app.presentation.designsystem.IconPhone
import com.eventverse.app.presentation.designsystem.IconUser
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

fun formatRupiah(amount: Long): String {
    val str = amount.toString()
    val builder = StringBuilder()
    val len = str.length
    for (i in 0 until len) {
        if (i > 0 && (len - i) % 3 == 0) {
            builder.append('.')
        }
        builder.append(str[i])
    }
    return "Rp " + builder.toString()
}

private fun getInitials(name: String): String {
    val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> "${parts[0].first()}${parts[1].first()}".uppercase()
    }
}

/**
 * Kartu Kanban Lead untuk modul CRM Sales.
 *
 * Mengikuti bahasa visual Claymorphism WeMade:
 * - Outline tebal 3dp, hard shadow tanpa blur
 * - Dropdown stage di pojok kanan atas badge
 * - Tag HP & Email berdampingan
 * - Estimasi nilai & kuantiti pcs
 * - Footer: Ikon aktivitas sales dengan counter di kiri, Avatar bulat PIC di kanan
 */
@Composable
fun CrmKanbanCard(
    lead: CrmLead,
    employees: List<OrgNode>,
    selected: Boolean,
    canWrite: Boolean,
    onSelectLead: (LeadId) -> Unit,
    onUpdateStage: (LeadStage) -> Unit,
    onOpenActivities: (CrmLead) -> Unit,
    modifier: Modifier = Modifier
) {
    var stageMenuExpanded by remember { mutableStateOf(false) }
    val owner = lead.ownerEmployeeId?.let { id -> employees.firstOrNull { it.id == id } }

    val dragDropState = LocalCrmDragDropState.current
    val isBeingDragged = canWrite && dragDropState?.isDragging == true && dragDropState.draggedLead?.id == lead.id
    var cardWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }

    val cardOutline = when (lead.stage) {
        LeadStage.NEW_LEAD -> WeMadeColors.Primary
        LeadStage.QUALIFIED -> WeMadeColors.Success
        LeadStage.UNQUALIFIED -> WeMadeColors.Error
    }

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(if (canWrite) PointerIcon.Hand else PointerIcon.Default)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    cardWindowOffset = coords.positionInWindow()
                    cardSize = coords.size.toSize()
                }
            }
            .then(
                if (canWrite) {
                    Modifier.pointerInput(lead.id, lead.stage) {
                        detectDragGestures(
                            onDragStart = { pointerOffset ->
                                dragDropState?.onDragStart(lead, cardWindowOffset, cardSize, pointerOffset)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDropState?.onDrag(dragAmount)
                            },
                            onDragEnd = {
                                dragDropState?.onDragEnd { targetStage ->
                                    onUpdateStage(targetStage)
                                }
                            },
                            onDragCancel = {
                                dragDropState?.onDragCancel()
                            }
                        )
                    }
                } else Modifier
            ),
        outlineColor = if (isBeingDragged) WeMadeColors.OutlineSoft else cardOutline,
        containerColor = if (isBeingDragged) WeMadeColors.SurfaceMuted else WeMadeColors.Surface,
        borderWidth = if (isBeingDragged) ClayBorder.Hairline else ClayBorder.Medium,
        selected = selected,
        onClick = null,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        if (isBeingDragged) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(with(LocalDensity.current) {
                        (cardSize.height - 32f).coerceAtLeast(64f).toDp()
                    }),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    IconInbox(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = "Memindahkan ${lead.title}…",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        } else {
            val uriHandler = LocalUriHandler.current

            // Baris Atas: Title (Brand / Kontak) & Order Archetype + Badge Status Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                val titleInteractionSource = remember { MutableInteractionSource() }
                val pcs = lead.estimatedPcs
                val orderSpecs = buildString {
                    if (pcs != null && pcs > 0) append("$pcs pcs ")
                    if (lead.productCategory.value.isNotBlank()) append(lead.productCategory.value)
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(
                            interactionSource = titleInteractionSource,
                            indication = null,
                            enabled = canWrite
                        ) { onSelectLead(lead.id) }
                ) {
                    Text(
                        text = lead.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (orderSpecs.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = orderSpecs,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.Primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(Modifier.width(ClaySpacing.Sm))

                val badgeInteractionSource = remember { MutableInteractionSource() }
                Box {
                    ClayBadge(
                        text = lead.stage.displayName,
                        tint = lead.stage.tint(),
                        fontSize = 10.sp,
                        trailing = if (canWrite) {
                            { IconChevronDown(Modifier.size(9.dp), color = lead.stage.tint()) }
                        } else null,
                        modifier = if (canWrite) {
                            Modifier.clickable(
                                interactionSource = badgeInteractionSource,
                                indication = null
                            ) { stageMenuExpanded = true }
                        } else Modifier
                    )
                    if (canWrite) {
                        DropdownMenu(
                            expanded = stageMenuExpanded,
                            onDismissRequest = { stageMenuExpanded = false }
                        ) {
                            LeadStage.entries.filter { it != lead.stage }.forEach { targetStage ->
                                val itemColor = when (targetStage) {
                                    LeadStage.QUALIFIED -> WeMadeColors.Success
                                    LeadStage.UNQUALIFIED -> WeMadeColors.Error
                                    LeadStage.NEW_LEAD -> WeMadeColors.Primary
                                }
                                DropdownMenuItem(
                                    leadingIcon = {
                                        when (targetStage) {
                                            LeadStage.QUALIFIED -> IconCheck(Modifier.size(16.dp), color = WeMadeColors.Success)
                                            LeadStage.UNQUALIFIED -> IconBan(Modifier.size(16.dp), color = WeMadeColors.Error)
                                            LeadStage.NEW_LEAD -> IconInbox(Modifier.size(16.dp), color = WeMadeColors.Primary)
                                        }
                                    },
                                    text = {
                                        Text(
                                            text = when (targetStage) {
                                                LeadStage.NEW_LEAD -> "Pindahkan ke Inquiry / New Lead"
                                                LeadStage.QUALIFIED -> "Kualifikasi (Qualified)"
                                                LeadStage.UNQUALIFIED -> "Tandai Unqualified"
                                            },
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = itemColor
                                        )
                                    },
                                    onClick = {
                                        stageMenuExpanded = false
                                        onUpdateStage(targetStage)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Estimasi Nilai & Tombol Cepat WhatsApp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = canWrite
                    ) { onSelectLead(lead.id) }
            ) {
                Text(
                    text = "Est. Deal Value",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val estValue = lead.estimatedValue
                    Text(
                        text = if (estValue != null) formatRupiah(estValue.amount) else "Nilai Belum Diestimasi",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (estValue != null) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
                    )

                    val whatsapp = lead.whatsappNumber
                    if (whatsapp != null) {
                        Row(
                            modifier = Modifier
                                .clayFlat(
                                    shape = ClayShapes.Pill,
                                    background = WeMadeColors.SuccessBg,
                                    outline = WeMadeColors.Success,
                                    borderWidth = ClayBorder.Hairline
                                )
                                .clickable {
                                    runCatching { uriHandler.openUri(whatsapp.waLink) }
                                }
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            IconPhone(Modifier.size(10.dp), color = WeMadeColors.Success)
                            Text(
                                text = "WhatsApp",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Success
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Kontak Klien & Avatar Sales PIC
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    IconUser(Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
                    Text(
                        text = if (lead.contactPerson.isNotBlank()) lead.contactPerson else "PIC Klien Belum Diisi",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Avatar Bulat PIC dengan Inisial
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    owner?.let {
                        Text(
                            text = it.name,
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clayFlat(
                                shape = CircleShape,
                                background = if (owner != null) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (owner != null) getInitials(owner.name) else "?",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (owner != null) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Footer Kartu: Tag Sumber Channel (Kiri) & Badge Follow-up / Aktivitas (Kanan)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sourceLabel = if (lead.source.value.isNotBlank()) lead.source.value else "Inquiry Langsung"
                ClayTag(
                    text = sourceLabel,
                    tint = WeMadeColors.Primary,
                    fontSize = 9.sp
                )

                val activityInteractionSource = remember { MutableInteractionSource() }
                val hasActivities = lead.activityCount > 0
                Row(
                    modifier = Modifier
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = if (hasActivities) WeMadeColors.SurfaceMuted else WeMadeColors.WarningBg,
                            outline = if (hasActivities) WeMadeColors.Outline else WeMadeColors.Warning,
                            borderWidth = ClayBorder.Hairline
                        )
                        .clickable(
                            interactionSource = activityInteractionSource,
                            indication = null
                        ) {
                            onOpenActivities(lead)
                        }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconChat(
                        modifier = Modifier.size(10.dp),
                        color = if (hasActivities) WeMadeColors.OnSurfaceMuted else WeMadeColors.Warning
                    )
                    Text(
                        text = if (hasActivities) "${lead.activityCount} Aktivitas" else "Perlu Follow-up",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (hasActivities) WeMadeColors.OnSurface else WeMadeColors.Warning
                    )
                }
            }
        }
    }
}

