package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconInbox
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

/** Versi ringkas untuk pill sempit di footer kartu: `Rp 125 jt`, `Rp 4,5 M`. */
private fun formatRupiahCompact(amount: Long): String = when {
    amount >= 1_000_000_000L -> "Rp ${decimalOne(amount, 1_000_000_000L)} M"
    amount >= 1_000_000L -> "Rp ${decimalOne(amount, 1_000_000L)} jt"
    amount >= 1_000L -> "Rp ${amount / 1_000L} rb"
    else -> "Rp $amount"
}

private fun decimalOne(amount: Long, unit: Long): String {
    val whole = amount / unit
    val tenth = (amount % unit) * 10 / unit
    return if (tenth == 0L || whole >= 100) "$whole" else "$whole,$tenth"
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
 * Kartu Kanban Lead untuk modul CRM Sales — tata letak "tag di atas, judul, deskripsi, footer
 * pill" ala papan tugas, dengan bahasa visual clay WeMade:
 * - Baris atas: tag sumber channel + tombol pindah stage
 * - Judul lead (maks. 2 baris) dan deskripsi satu baris (pcs · kategori · PIC klien)
 * - Footer: avatar PIC sales di kiri, pill nilai deal / WhatsApp / aktivitas di kanan
 *
 * Warna stage sudah dibawa oleh kolom (header & tint), jadi kartunya sendiri netral.
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
    val owner = lead.ownerEmployeeId?.let { id -> employees.firstOrNull { it.id == id } }

    val dragDropState = LocalCrmDragDropState.current
    val isBeingDragged = canWrite && dragDropState?.isDragging == true && dragDropState.draggedLead?.id == lead.id
    var cardWindowOffset by remember { mutableStateOf(Offset.Zero) }
    var cardSize by remember { mutableStateOf(Size.Zero) }

    ClayCard(
        modifier = modifier
            .fillMaxWidth()
            .pointerHoverIcon(PointerIcon.Hand)
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    cardWindowOffset = coords.positionInWindow()
                    cardSize = coords.size.toSize()
                }
            }
            .then(
                if (canWrite && dragDropState != null) {
                    Modifier.pointerInput(lead.id, lead.stage) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var drag: PointerInputChange? = null
                            var overSlop = Offset.Zero
                            do {
                                drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                    change.consume()
                                    overSlop = over
                                }
                            } while (drag != null && !drag.isConsumed)

                            if (drag != null) {
                                dragDropState.onDragStart(lead, cardWindowOffset, cardSize, drag.position)
                                dragDropState.onDrag(overSlop)
                                val success = drag(drag.id) { change ->
                                    dragDropState.onDrag(change.positionChange())
                                    change.consume()
                                }
                                if (success) {
                                    dragDropState.onDragEnd { targetStage -> onUpdateStage(targetStage) }
                                } else {
                                    dragDropState.onDragCancel()
                                }
                            } else if (!down.isConsumed) {
                                onSelectLead(lead.id)
                            }
                        }
                    }
                } else Modifier
            ),
        outlineColor = when {
            isBeingDragged -> WeMadeColors.OutlineSoft
            selected -> WeMadeColors.Primary
            else -> WeMadeColors.Outline
        },
        containerColor = if (isBeingDragged) WeMadeColors.SurfaceMuted else WeMadeColors.Surface,
        borderWidth = ClayBorder.Medium,
        offset = ClayOffset.Small,
        selected = selected,
        onClick = { onSelectLead(lead.id) },
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        if (isBeingDragged) {
            DraggedPlaceholder(title = lead.title, heightPx = cardSize.height)
        } else {
            // Baris atas: tag sumber channel + tombol pindah stage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val sourceLabel = lead.source.value.ifBlank { "Inquiry Langsung" }
                Box(modifier = Modifier.weight(1f, fill = false)) {
                    ClayBadge(text = sourceLabel, tint = WeMadeColors.Primary, fontSize = 11.sp)
                }
                if (canWrite) {
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    LeadStageMenuButton(currentStage = lead.stage, onUpdateStage = onUpdateStage)
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Judul & deskripsi satu baris
            Text(
                text = lead.title,
                style = MaterialTheme.typography.titleSmall,
                color = WeMadeColors.OnSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(ClaySpacing.Xxs))
            Text(
                text = leadDescription(lead),
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            LeadCardFooter(lead = lead, ownerName = owner?.name, onOpenActivities = onOpenActivities)
        }
    }
}

private fun leadDescription(lead: CrmLead): String {
    val parts = buildList {
        val pcs = lead.estimatedPcs
        if (pcs != null && pcs > 0) add("$pcs pcs")
        if (lead.productCategory.value.isNotBlank()) add(lead.productCategory.value)
        add(lead.contactPerson.ifBlank { "PIC klien belum diisi" })
    }
    return parts.joinToString(" · ")
}

@Composable
private fun LeadCardFooter(
    lead: CrmLead,
    ownerName: String?,
    onOpenActivities: (CrmLead) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        OwnerAvatar(name = ownerName)
        Spacer(Modifier.weight(1f))

        // Pill nilai yang mengalah duluan saat kolom sempit (4 kolom di layar ~1280dp).
        val estValue = lead.estimatedValue
        Box(modifier = Modifier.weight(1f, fill = false)) {
            ClayBadge(
                text = if (estValue != null) formatRupiahCompact(estValue.amount) else "Belum estimasi",
                tint = if (estValue != null) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                fontSize = 11.sp
            )
        }

        lead.whatsappNumber?.let { whatsapp ->
            ClayBadge(
                text = "WA",
                tint = WeMadeColors.Success,
                fontSize = 11.sp,
                leading = { IconPhone(Modifier.size(10.dp), color = WeMadeColors.Success) },
                modifier = Modifier.clickable { runCatching { uriHandler.openUri(whatsapp.waLink) } }
            )
        }

        val hasActivities = lead.activityCount > 0
        val activityTint = if (hasActivities) WeMadeColors.Purple else WeMadeColors.Warning
        ClayBadge(
            text = if (hasActivities) "${lead.activityCount}" else "Follow-up",
            tint = activityTint,
            fontSize = 11.sp,
            leading = { IconChat(Modifier.size(10.dp), color = activityTint) },
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onOpenActivities(lead) }
        )
    }
}

@Composable
private fun OwnerAvatar(name: String?) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clayFlat(
                shape = CircleShape,
                background = if (name != null) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium
            ),
        contentAlignment = Alignment.Center
    ) {
        if (name != null) {
            Text(
                text = getInitials(name),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Surface
            )
        } else {
            IconUser(
                modifier = Modifier.size(14.dp),
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
}

@Composable
private fun DraggedPlaceholder(title: String, heightPx: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(with(LocalDensity.current) { (heightPx - 32f).coerceAtLeast(64f).toDp() }),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            IconInbox(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
            Text(
                text = "Memindahkan $title…",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
