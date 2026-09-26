package com.eventverse.app.presentation.crm.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconChevronUp
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Tab Detail Prospek ala Monday.com:
 * - Card Details utama hanya menampilkan properti penting yang sering diakses:
 *   1. Nama Brand / Perusahaan
 *   2. Nama Kontak
 *   3. No. WhatsApp / Telepon
 *   4. Email
 * - Sisa properti (Sumber, Estimasi pcs, Estimasi nilai, Closing, Custom properties)
 *   ditempatkan di dalam accordion "Lihat Semua Properti (See all)" yang dapat dibuka/tutup.
 */
@Composable
fun LeadInspectorDetailTab(
    lead: CrmLead,
    schema: List<LeadFieldDescriptor>,
    cells: Map<String, JsonValue.Obj?>,
    employees: List<OrgNode>,
    canWrite: Boolean,
    canManage: Boolean,
    onCommitField: (fieldId: String, value: JsonValue.Obj?) -> Unit,
    onAddField: () -> Unit,
    onDeleteField: ((fieldId: String) -> Unit)?,
    onArchive: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }
    var fieldPendingDeletion by remember { mutableStateOf<LeadFieldDescriptor?>(null) }

    val coreBrandId = LeadFieldDescriptor.coreFieldId("brand_name")
    val coreContactId = LeadFieldDescriptor.coreFieldId("contact_person")
    val corePhoneId = LeadFieldDescriptor.coreFieldId("whatsapp_number")
    val coreEmailId = LeadFieldDescriptor.coreFieldId("email")

    val primaryFieldIds = setOf(coreBrandId, coreContactId, corePhoneId, coreEmailId)

    // Field inti utama
    val primaryDescriptors = schema.filter { it.isCore && it.fieldId in primaryFieldIds }
        .sortedBy {
            when (it.fieldId) {
                coreBrandId -> 0
                coreContactId -> 1
                corePhoneId -> 2
                coreEmailId -> 3
                else -> 4
            }
        }

    // Field inti sekunder yang disembunyikan dalam accordion
    val secondaryCoreDescriptors = schema.filter {
        it.isCore &&
        it.fieldId !in primaryFieldIds &&
        it.fieldId != LeadFieldDescriptor.coreFieldId("stage") &&
        it.fieldId != LeadFieldDescriptor.coreFieldId("owner_employee_id")
    }

    val customFields = schema.filter { !it.isCore }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // CARD DETAILS UTAMA (ALA MONDAY.COM)
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Details",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Ringkasan Kontak",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            Spacer(Modifier.height(ClaySpacing.Sm))

            // 1. Properti Utama yang Selalu Terlihat
            primaryDescriptors.forEach { descriptor ->
                LeadCustomField(
                    descriptor = descriptor,
                    cell = cells[descriptor.fieldId],
                    editable = canWrite,
                    employees = employees,
                    onCommit = { value -> onCommitField(descriptor.fieldId, value) }
                )
            }

            Spacer(Modifier.height(ClaySpacing.Sm))

            // 2. Tombol Accordion Expander "See all" / "Lihat Semua Properti"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isExpanded) "Sembunyikan Properti" else "Lihat Semua Properti (See all)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.Primary
                )
                Spacer(Modifier.width(6.dp))
                if (isExpanded) {
                    IconChevronUp(Modifier.size(14.dp), color = WeMadeColors.Primary)
                } else {
                    IconChevronDown(Modifier.size(14.dp), color = WeMadeColors.Primary)
                }
            }

            // 3. Konten Accordion (Properti Inti Sekunder & Properti Kustom)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    // Garis Pemisah Halus
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROPERTI LAINNYA",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    // Field Inti Tambahan (Estimasi pcs, Nilai, Closing Date, Source)
                    secondaryCoreDescriptors.forEach { descriptor ->
                        LeadCustomField(
                            descriptor = descriptor,
                            cell = cells[descriptor.fieldId],
                            editable = canWrite,
                            employees = employees,
                            onCommit = { value -> onCommitField(descriptor.fieldId, value) }
                        )
                    }

                    Spacer(Modifier.height(ClaySpacing.Sm))

                    // Bagian Properti Kustom Tenant
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Properti Kustom",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        if (canManage) {
                            ClayButton(
                                text = "+ Kolom",
                                onClick = onAddField,
                                style = ClayButtonStyle.Secondary,
                                fontSize = 11.sp,
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    if (customFields.isEmpty()) {
                        Text(
                            text = "Belum ada properti kustom untuk modul ini.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            modifier = Modifier.padding(vertical = ClaySpacing.Xs)
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

                    Spacer(Modifier.height(ClaySpacing.Sm))

                    // Tombol Arsip di bagian bawah accordion
                    if (canWrite) {
                        ClayButton(
                            text = "Arsipkan Lead",
                            onClick = onArchive,
                            style = ClayButtonStyle.Danger,
                            fontSize = 12.sp,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }

    // Dialog Konfirmasi Hapus Kolom Kustom
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
