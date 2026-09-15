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
            // Baris Atas: Title (Brand / Kontak) & Badge Status Dropdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleInteractionSource = remember { MutableInteractionSource() }
                Text(
                    text = lead.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clickable(
                            interactionSource = titleInteractionSource,
                            indication = null,
                            enabled = canWrite
                        ) { onSelectLead(lead.id) }
                )

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

        val contentInteractionSource = remember { MutableInteractionSource() }
        // Area Tengah: Kontak & Nilai (bisa diklik untuk membuka Lead Inspector)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = contentInteractionSource,
                    indication = null,
                    enabled = canWrite
                ) { onSelectLead(lead.id) }
        ) {

        // Kontak person jika berbeda dengan nama brand
        if (lead.contactPerson.isNotBlank() && lead.brandName.value.isNotBlank()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconUser(Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
                Text(
                    text = lead.contactPerson,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Baris Tag HP dan Email Berdampingan
        if (lead.whatsappNumber != null || lead.email.isNotBlank()) {
            Spacer(Modifier.height(ClaySpacing.Xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val whatsapp = lead.whatsappNumber
                if (whatsapp != null) {
                    ClayTag(
                        text = whatsapp.normalizedNumber,
                        tint = WeMadeColors.Success,
                        fontSize = 9.sp,
                        leading = { IconPhone(Modifier.size(10.dp), color = WeMadeColors.Success) }
                    )
                }

                if (lead.email.isNotBlank()) {
                    ClayTag(
                        text = lead.email,
                        tint = WeMadeColors.Info,
                        fontSize = 9.sp,
                        leading = { IconMail(Modifier.size(10.dp), color = WeMadeColors.Info) }
                    )
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // Estimasi Nilai & Kuantiti Pcs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val estValue = lead.estimatedValue
            if (estValue != null) {
                Text(
                    text = formatRupiah(estValue.amount),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
            } else {
                Text(
                    text = "Nilai: Belum Diestimasi",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            val pcs = lead.estimatedPcs
            if (pcs != null) {
                ClayTag(
                    text = "$pcs pcs",
                    tint = WeMadeColors.Secondary,
                    fontSize = 10.sp
                )
            }
        }
    }

        Spacer(Modifier.height(ClaySpacing.Md))

        // Footer Kartu: Ikon Aktivitas (Kiri) & Avatar Bulat PIC (Kanan)
        val activityInteractionSource = remember { MutableInteractionSource() }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Tombol Ikon Komentar/Aktivitas dengan Counter
            Row(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Hairline
                    )
                    .clickable(
                        interactionSource = activityInteractionSource,
                        indication = null
                    ) {
                        onOpenActivities(lead)
                    }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconChat(modifier = Modifier.size(11.dp), color = WeMadeColors.OnSurfaceMuted)
                Text(
                    text = "${lead.activityCount} Aktivitas",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface
                )
            }

            // Avatar Bulat PIC dengan Inisial
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                        .size(26.dp)
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
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (owner != null) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}
}

