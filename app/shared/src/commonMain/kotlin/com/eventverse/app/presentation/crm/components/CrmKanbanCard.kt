package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.iconLabel
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
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
    return "Rp $builder"
}

/**
 * Kartu Kanban Lead untuk modul CRM Sales.
 *
 * Mengikuti bahasa visual Claymorphism WeMade:
 * - Outline tebal 3dp, hard shadow tanpa blur
 * - Menampilkan status kualifikasi, kuantiti, estimasi rupiah (nullable)
 * - Quick Action buttons untuk memindahkan lead antar kolom tanpa harus membuka dialog inspeksi
 */
@Composable
fun CrmKanbanCard(
    lead: CrmLead,
    employees: List<OrgNode>,
    selected: Boolean,
    canWrite: Boolean,
    onSelectLead: (LeadId) -> Unit,
    onUpdateStage: (LeadStage) -> Unit,
    modifier: Modifier = Modifier
) {
    val owner = lead.ownerEmployeeId?.let { id -> employees.firstOrNull { it.id == id } }

    val cardOutline = when (lead.stage) {
        LeadStage.NEW_LEAD -> WeMadeColors.Primary
        LeadStage.QUALIFIED -> WeMadeColors.Success
        LeadStage.UNQUALIFIED -> WeMadeColors.Error
    }

    ClayCard(
        modifier = modifier.fillMaxWidth(),
        outlineColor = cardOutline,
        borderWidth = ClayBorder.Medium,
        selected = selected,
        onClick = { onSelectLead(lead.id) },
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // Baris Atas: Title (Brand / Kontak / HP) & Badge Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = lead.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            ClayBadge(
                text = lead.stage.displayName,
                tint = lead.stage.tint(),
                fontSize = 10.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        // Detail Kontak & Nomor HP
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (lead.contactPerson.isNotBlank() && lead.brandName.value.isNotBlank()) {
                Text(
                    text = "👤 ${lead.contactPerson}",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
            } else if (lead.brandName.value.isNotBlank() && lead.contactPerson.isBlank()) {
                Text(
                    text = "Tanpa nama kontak",
                    fontSize = 11.sp,
                    fontStyle = FontStyle.Italic,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            val whatsapp = lead.whatsappNumber
            if (whatsapp != null) {
                ClayTag(
                    text = "📱 ${whatsapp.normalizedNumber}",
                    tint = WeMadeColors.Success,
                    fontSize = 9.sp
                )
            }
        }

        if (lead.email.isNotBlank()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "✉️ ${lead.email}",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // Estimasi Nilai & Kuantiti Pcs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Nilai Rupiah (nullable pada tahap New Lead)
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

            // Kuantiti Pcs (nullable)
            val pcs = lead.estimatedPcs
            if (pcs != null) {
                ClayTag(
                    text = "$pcs pcs",
                    tint = WeMadeColors.Secondary,
                    fontSize = 10.sp
                )
            }
        }

        // Owner Assigned PIC
        if (owner != null) {
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(
                text = "PIC: ${owner.name}",
                fontSize = 10.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Quick Stage Move Buttons (jika memiliki hak tulis canWrite)
        if (canWrite) {
            Spacer(Modifier.height(ClaySpacing.Md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (lead.stage) {
                    LeadStage.NEW_LEAD -> {
                        ClayButton(
                            text = "Kualifikasi",
                            style = ClayButtonStyle.Success,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.QUALIFIED) },
                            modifier = Modifier.weight(1f)
                        )
                        ClayButton(
                            text = "Unqualify",
                            style = ClayButtonStyle.Danger,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.UNQUALIFIED) }
                        )
                    }
                    LeadStage.QUALIFIED -> {
                        ClayButton(
                            text = "Unqualify",
                            style = ClayButtonStyle.Danger,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.UNQUALIFIED) },
                            modifier = Modifier.weight(1f)
                        )
                        ClayButton(
                            text = "Ke Inquiry",
                            style = ClayButtonStyle.Secondary,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.NEW_LEAD) }
                        )
                    }
                    LeadStage.UNQUALIFIED -> {
                        ClayButton(
                            text = "Buka Kembali (Qualified)",
                            style = ClayButtonStyle.Success,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.QUALIFIED) },
                            modifier = Modifier.weight(1f)
                        )
                        ClayButton(
                            text = "Ke Inquiry",
                            style = ClayButtonStyle.Ghost,
                            fontSize = 11.sp,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            onClick = { onUpdateStage(LeadStage.NEW_LEAD) }
                        )
                    }
                }
            }
        }
    }
}
