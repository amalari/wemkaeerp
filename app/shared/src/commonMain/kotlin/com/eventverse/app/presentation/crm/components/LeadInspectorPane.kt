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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.LeadFieldProjection
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconNote
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconReceipt
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.designsystem.IconUser
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.invoicing.InvoicePrefillCoordinator
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

enum class LeadInspectorTab(val title: String) {
    DETAIL("Detail"),
    UPDATE("Update"),
    INVOICE("Invoice & Alur")
}

/**
 * Panel detail & inspeksi prospek (Lead Inspector) dengan:
 * - Baris atas sejajar: Tahap (Stage selector) di kiri & Avatar PIC ala Jira di kanan
 * - Tab navigasi [ Detail ] dan [ Update ]
 * - Tab [ Detail ]: properti inti, custom field, dan opsi alur Sampling vs Order Langsung saat Qualified
 * - Tab [ Update ]: riwayat update sales dan kotak input update baru langsung di dalam panel
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
    activities: List<LeadActivity> = emptyList(),
    isLoadingActivities: Boolean = false,
    isSubmittingActivity: Boolean = false,
    onSubmitActivity: ((content: String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LeadInspectorTab.DETAIL) }
    var fieldPendingDeletion by remember { mutableStateOf<LeadFieldDescriptor?>(null) }
    var newCommentText by remember { mutableStateOf("") }

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
    val owner = lead.ownerEmployeeId?.let { id -> employees.firstOrNull { it.id == id } }

    Column(
        modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)
    ) {
        // Header Atas: Nama Brand / Kontak & Tombol Tutup
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Detail Lead" }),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
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

        Spacer(Modifier.height(ClaySpacing.Md))

        // BARIS SEJAJAR: Tahap di Kiri & PIC Avatar Jira di Kanan
        ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kiri: Tahap / Stage Selector
                StageSelector(current = lead.stage, canWrite = canWrite, onUpdateStage = onUpdateStage)

                // Kanan: Avatar PIC ala Jira
                JiraPicAvatar(
                    owner = owner,
                    employees = employees,
                    canWrite = canWrite,
                    onAssign = { newOwnerId ->
                        val cellVal = newOwnerId?.let { CustomAttributes.textCell(it) }
                        onCommitField(LeadFieldDescriptor.coreFieldId("owner_employee_id"), cellVal)
                    }
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // TAB BAR: [ Detail ] & [ Update ] & [ Invoice & Alur ]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ClayButton(
                text = LeadInspectorTab.DETAIL.title,
                style = if (selectedTab == LeadInspectorTab.DETAIL) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                onClick = { selectedTab = LeadInspectorTab.DETAIL }
            )
            ClayButton(
                text = "${LeadInspectorTab.UPDATE.title} (${activities.size.coerceAtLeast(lead.activityCount)})",
                style = if (selectedTab == LeadInspectorTab.UPDATE) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                onClick = { selectedTab = LeadInspectorTab.UPDATE }
            )
            ClayButton(
                text = LeadInspectorTab.INVOICE.title,
                style = if (selectedTab == LeadInspectorTab.INVOICE) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                onClick = { selectedTab = LeadInspectorTab.INVOICE }
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ISI KONTEN BERDASARKAN TAB AKTIF
        when (selectedTab) {
            LeadInspectorTab.DETAIL -> {
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    // Detail Inti (Core Fields tanpa Stage dan PIC karena sudah ada di header)
                    ClayCard(modifier = Modifier.fillMaxWidth()) {
                        Text(text = "Detail Inti", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                        schema.filter {
                            it.isCore &&
                            it.fieldId != LeadFieldDescriptor.coreFieldId("stage") &&
                            it.fieldId != LeadFieldDescriptor.coreFieldId("owner_employee_id")
                        }.forEach { descriptor ->
                            LeadCustomField(
                                descriptor = descriptor,
                                cell = cells[descriptor.fieldId],
                                editable = canWrite,
                                employees = employees,
                                onCommit = { value -> onCommitField(descriptor.fieldId, value) }
                            )
                        }
                    }

                    // Properti Kustom
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
            }

            LeadInspectorTab.UPDATE -> {
                // Tab Update: Timeline aktivitas dan input form
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (isLoadingActivities) {
                            Text(
                                text = "Memuat riwayat update…",
                                fontSize = 13.sp,
                                color = WeMadeColors.OnSurfaceMuted,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else if (activities.isEmpty()) {
                            Column(
                                modifier = Modifier.align(Alignment.Center).padding(ClaySpacing.Lg),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                            ) {
                                IconNote(modifier = Modifier.size(40.dp), color = WeMadeColors.OnSurfaceMuted)
                                Text(
                                    text = "Belum Ada Catatan Update",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                Text(
                                    text = "Tulis update progres sales (misal: follow-up WA, negosiasi harga, kirim foto bahan).",
                                    fontSize = 11.sp,
                                    color = WeMadeColors.OnSurfaceMuted,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                            ) {
                                items(activities) { activity ->
                                    ActivityCommentCard(activity = activity)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(ClaySpacing.Md))

                    // Input Update Baru
                    if (canWrite && onSubmitActivity != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ClayTextField(
                                value = newCommentText,
                                onValueChange = { newCommentText = it },
                                placeholder = "Tulis update sales terbaru…",
                                modifier = Modifier.weight(1f)
                            )
                            ClayButton(
                                text = if (isSubmittingActivity) "Kirim…" else "Kirim",
                                style = ClayButtonStyle.Primary,
                                enabled = newCommentText.isNotBlank() && !isSubmittingActivity,
                                onClick = {
                                    val textToSend = newCommentText.trim()
                                    if (textToSend.isNotBlank()) {
                                        newCommentText = ""
                                        onSubmitActivity(textToSend)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            LeadInspectorTab.INVOICE -> {
                val navigator = LocalAppNavigator.current
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
                ) {
                    // Header Card Penjelasan Alur
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = WeMadeColors.SurfaceMuted,
                        contentPadding = PaddingValues(ClaySpacing.Md)
                    ) {
                        Text(
                            text = "PILIH ALUR KUALIFIKASI & PENAGIHAN",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        Text(
                            text = "Pilih alur komersial untuk menerbitkan faktur tagihan resmi ke prospek:",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    // Dua Kartu Alur Komersial Berdampingan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                    ) {
                        // KARTU 1: Alur Sampling
                        ClayCard(
                            modifier = Modifier.weight(1f),
                            outlineColor = WeMadeColors.Success,
                            borderWidth = ClayBorder.Medium,
                            contentPadding = PaddingValues(ClaySpacing.Md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Alur Sampling",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                ClayBadge(
                                    text = "INVOICE SAMPLE",
                                    tint = WeMadeColors.Success
                                )
                            }

                            Spacer(Modifier.height(ClaySpacing.Sm))

                            Text(
                                text = "Pembuatan prototype sample 1-3 pcs untuk approval buyer sebelum rilis massal.",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )

                            Spacer(Modifier.height(ClaySpacing.Md))

                            ClayTag(
                                text = "Status Tagihan: Belum Dibuat",
                                tint = WeMadeColors.OnSurfaceMuted
                            )

                            Spacer(Modifier.height(ClaySpacing.Lg))

                            ClayButton(
                                text = "+ Generate Invoice Sampling",
                                style = ClayButtonStyle.Primary,
                                fontSize = 11.sp,
                                leading = { IconRuler(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                                onClick = {
                                    InvoicePrefillCoordinator.setPending(
                                        InvoicePrefillData(
                                            kind = InvoiceKind.SAMPLE,
                                            clientName = lead.brandName.display(fallback = lead.contactPerson),
                                            contactPerson = lead.contactPerson,
                                            phone = lead.whatsappNumber?.value ?: "",
                                            email = lead.email,
                                            sourceKind = InvoiceSourceKind.CRM_LEAD,
                                            sourceRef = lead.id.value,
                                            lineDescription = "Jasa Pembuatan Prototype Sample Baju - ${lead.brandName.display(fallback = lead.contactPerson)}",
                                            lineQty = 1.0,
                                            linePrice = 150000L
                                        )
                                    )
                                    onClose?.invoke()
                                    navigator(AppNavScreen.INVOICING)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // KARTU 2: Alur Order Langsung
                        ClayCard(
                            modifier = Modifier.weight(1f),
                            outlineColor = WeMadeColors.Primary,
                            borderWidth = ClayBorder.Medium,
                            contentPadding = PaddingValues(ClaySpacing.Md)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Alur Order Langsung",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.OnSurface
                                )
                                ClayBadge(
                                    text = "INVOICE DP",
                                    tint = WeMadeColors.Primary
                                )
                            }

                            Spacer(Modifier.height(ClaySpacing.Sm))

                            Text(
                                text = "Langsung masuk antrean PO produksi massal dengan termin DP 30% - 50%.",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )

                            Spacer(Modifier.height(ClaySpacing.Md))

                            ClayTag(
                                text = "Status Tagihan: Belum Dibuat",
                                tint = WeMadeColors.OnSurfaceMuted
                            )

                            Spacer(Modifier.height(ClaySpacing.Lg))

                            ClayButton(
                                text = "+ Generate Invoice DP",
                                style = ClayButtonStyle.Accent,
                                fontSize = 11.sp,
                                leading = { IconPackage(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                                onClick = {
                                    InvoicePrefillCoordinator.setPending(
                                        InvoicePrefillData(
                                            kind = InvoiceKind.DOWN_PAYMENT,
                                            clientName = lead.brandName.display(fallback = lead.contactPerson),
                                            contactPerson = lead.contactPerson,
                                            phone = lead.whatsappNumber?.value ?: "",
                                            email = lead.email,
                                            sourceKind = InvoiceSourceKind.CRM_LEAD,
                                            sourceRef = lead.id.value,
                                            lineDescription = "Uang Muka (DP) Produksi Pakaian - ${lead.brandName.display(fallback = lead.contactPerson)}",
                                            lineQty = 100.0,
                                            linePrice = 150000L
                                        )
                                    )
                                    onClose?.invoke()
                                    navigator(AppNavScreen.INVOICING)
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // Info Footnote Banner
                    ClayCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = WeMadeColors.Info.copy(alpha = 0.08f),
                        outlineColor = WeMadeColors.Info.copy(alpha = 0.3f),
                        borderWidth = ClayBorder.Hairline,
                        contentPadding = PaddingValues(ClaySpacing.Sm)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                        ) {
                            IconReceipt(Modifier.size(14.dp), color = WeMadeColors.Info)
                            Text(
                                text = "Mengklik salah satu opsi akan membuka formulir Custom Invoice dengan data prospek terisi otomatis.",
                                fontSize = 11.sp,
                                color = WeMadeColors.OnSurface
                            )
                        }
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

/**
 * Komponen Pemilih Tahap (Stage Selector) dengan Dropdown berwarna
 */
@Composable
private fun StageSelector(
    current: LeadStage,
    canWrite: Boolean,
    onUpdateStage: (LeadStage) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(text = "Tahap Lead", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ClayBadge(
                text = current.displayName,
                tint = current.tint(),
                fontSize = 11.sp,
                trailing = if (canWrite) {
                    { IconChevronDown(Modifier.size(10.dp), color = current.tint()) }
                } else null,
                modifier = if (canWrite) Modifier.clickable { expanded = true } else Modifier
            )
        }

        if (canWrite) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LeadStage.entries.filter { it != current }.forEach { stage ->
                    val color = when (stage) {
                        LeadStage.QUALIFIED -> WeMadeColors.Success
                        LeadStage.UNQUALIFIED -> WeMadeColors.Error
                        LeadStage.NEW_LEAD -> WeMadeColors.Primary
                    }
                    DropdownMenuItem(
                        leadingIcon = {
                            when (stage) {
                                LeadStage.QUALIFIED -> IconCheck(Modifier.size(16.dp), color = WeMadeColors.Success)
                                LeadStage.UNQUALIFIED -> IconBan(Modifier.size(16.dp), color = WeMadeColors.Error)
                                LeadStage.NEW_LEAD -> IconInbox(Modifier.size(16.dp), color = WeMadeColors.Primary)
                            }
                        },
                        text = {
                            Text(
                                text = stage.displayName,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = color
                            )
                        },
                        onClick = {
                            expanded = false
                            onUpdateStage(stage)
                        }
                    )
                }
            }
        }
    }
}

/**
 * Avatar PIC ala Jira:
 * - Jika sudah ada PIC: Lingkaran berwarna dengan inisial karyawan, nama di sebelahnya
 * - Jika belum ada: Lingkaran siluet user abu-abu bertuliskan "Tugaskan PIC" yang dapat diklik
 */
@Composable
private fun JiraPicAvatar(
    owner: OrgNode?,
    employees: List<OrgNode>,
    canWrite: Boolean,
    onAssign: (String?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(horizontalAlignment = Alignment.End) {
        Text(text = "Penanggung Jawab (PIC)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
        Spacer(Modifier.height(2.dp))

        Box {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = if (canWrite) Modifier.clickable { expanded = true } else Modifier
            ) {
                if (owner != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clayFlat(
                                shape = CircleShape,
                                background = WeMadeColors.Primary,
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = getAuthorInitials(owner.name),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Surface
                        )
                    }
                    Text(
                        text = owner.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurface
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clayFlat(
                                shape = CircleShape,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.OnSurfaceMuted,
                                borderWidth = ClayBorder.Hairline
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        IconUser(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
                    }
                    Text(
                        text = "Tugaskan PIC",
                        fontSize = 11.sp,
                        color = WeMadeColors.Primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            if (canWrite) {
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(
                        leadingIcon = { IconBan(Modifier.size(14.dp), color = WeMadeColors.OnSurfaceMuted) },
                        text = { Text("Tanpa PIC", fontSize = 12.sp) },
                        onClick = {
                            expanded = false
                            onAssign(null)
                        }
                    )
                    employees.forEach { emp ->
                        DropdownMenuItem(
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clayFlat(shape = CircleShape, background = WeMadeColors.Primary, outline = WeMadeColors.Outline, borderWidth = ClayBorder.Hairline),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = getAuthorInitials(emp.name), fontSize = 8.sp, color = WeMadeColors.Surface)
                                }
                            },
                            text = { Text(emp.name, fontSize = 12.sp, fontWeight = FontWeight.Medium) },
                            onClick = {
                                expanded = false
                                onAssign(emp.id.value)
                            }
                        )
                    }
                }
            }
        }
    }
}

