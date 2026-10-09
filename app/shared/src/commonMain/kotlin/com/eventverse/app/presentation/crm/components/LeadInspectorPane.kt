package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadActivity
import com.eventverse.app.domain.crm.LeadFieldDescriptor
import com.eventverse.app.domain.crm.LeadStage
import com.eventverse.app.domain.customfield.CustomAttributes
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.presentation.crm.LeadFieldProjection
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.domain.crm.LeadCreationChannel
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconBan
import com.eventverse.app.presentation.designsystem.IconChat
import com.eventverse.app.presentation.designsystem.IconCheck
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconInbox
import com.eventverse.app.presentation.designsystem.IconNote
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

enum class LeadInspectorTab(val title: String) {
    DETAIL("Detail"),
    UPDATE("Updates"),
    INVOICE("Invoice")
}

/**
 * Panel detail & inspeksi prospek (Lead Inspector) ala Monday.com:
 * - Header Hero Profile: Squircle Avatar inisial, Nama Lead utama, Sub-judul
 * - Baris Status & Penanggung Jawab (PIC yang dapat dicari)
 * - Tiga Tab: [ Detail ], [ Updates ], dan [ Invoice ]
 * - Tab Detail: Properti utama terlihat langsung, sisanya dalam accordion expander
 * - Tab Updates: Riwayat catatan sales dan form posting update
 * - Tab Invoice: Pilihan langsung Invoice Sampling vs Invoice DP (dengan popup persentase)
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
    fieldFileActions: LeadFieldFileActions? = null,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(LeadInspectorTab.DETAIL) }
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
        // Tombol Tutup di Sudut Kanan Atas
        if (onClose != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                ClayButton(text = "Tutup", onClick = onClose, style = ClayButtonStyle.Ghost)
            }
        }

        // HERO PROFILE SECTION ALA MONDAY.COM
        val initialLetter = (lead.brandName.value.firstOrNull() ?: lead.contactPerson.firstOrNull() ?: 'L').uppercaseChar().toString()
        val mainTitle = lead.contactPerson.ifBlank { lead.brandName.value.ifBlank { "Detail Lead" } }
        val subTitle = if (lead.brandName.value.isNotBlank() && lead.contactPerson.isNotBlank()) {
            lead.brandName.value
        } else if (lead.brandName.value.isNotBlank()) {
            "Perusahaan / Brand"
        } else {
            "No Title / Company"
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Sm),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Squircle Avatar dengan Inisial
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clayFlat(
                        shape = RoundedCornerShape(16.dp),
                        background = WeMadeColors.Primary,
                        outline = WeMadeColors.Outline,
                        borderWidth = 1.dp
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialLetter,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Surface
                )
            }

            Spacer(Modifier.height(ClaySpacing.Sm))

            // Nama Utama (Kontak / Brand)
            Text(
                text = mainTitle,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(2.dp))

            // Sub-judul (Perusahaan / Jabatan)
            Text(
                text = subTitle,
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(ClaySpacing.Sm))

        // BARIS SEJAJAR: Tahap di Kiri & PIC Selector ala Monday.com di Kanan
        ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Kiri: Tahap / Stage Selector
                StageSelector(current = lead.stage, canWrite = canWrite, onUpdateStage = onUpdateStage, fromAiDraft = lead.createdVia == LeadCreationChannel.AI_DRAFT)

                // Kanan: Selector PIC ala Monday.com (searchable)
                PicAssigneeSelector(
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

        // TAB BAR: [ Detail ] & [ Updates ] & [ Invoice ]
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
                LeadInspectorDetailTab(
                    lead = lead,
                    schema = schema,
                    cells = cells,
                    employees = employees,
                    canWrite = canWrite,
                    canManage = canManage,
                    onCommitField = onCommitField,
                    onAddField = onAddField,
                    onDeleteField = onDeleteField,
                    onArchive = onArchive,
                    fieldFileActions = fieldFileActions,
                    modifier = Modifier.weight(1f)
                )
            }

            LeadInspectorTab.UPDATE -> {
                // Tab Updates: Timeline aktivitas dan form input komentar
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
                                    textAlign = TextAlign.Center
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
                LeadInspectorInvoiceTab(
                    lead = lead,
                    onClose = onClose,
                    modifier = Modifier.weight(1f)
                )
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
    onUpdateStage: (LeadStage) -> Unit,
    fromAiDraft: Boolean = false // TRD-HELP-002 K2: field awalnya diisi draf AI, disimpan manusia
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
        if (fromAiDraft) ClayTag(text = "Dibuat dari draf AI", tint = WeMadeColors.Info, modifier = Modifier.padding(top = ClaySpacing.Xs))

        if (canWrite) {
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                LeadStage.entries.filter { it != current }.forEach { stage ->
                    val color = stage.tint()
                    DropdownMenuItem(
                        leadingIcon = {
                            when (stage) {
                                LeadStage.QUALIFIED -> IconCheck(Modifier.size(16.dp), color = color)
                                LeadStage.UNQUALIFIED -> IconBan(Modifier.size(16.dp), color = color)
                                LeadStage.NEW_LEAD -> IconInbox(Modifier.size(16.dp), color = color)
                                LeadStage.FOLLOW_UP -> IconChat(Modifier.size(16.dp), color = color)
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
