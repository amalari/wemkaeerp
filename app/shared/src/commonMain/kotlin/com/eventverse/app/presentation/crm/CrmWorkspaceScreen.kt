package com.eventverse.app.presentation.crm

import com.eventverse.app.domain.pack.GarmentTutorialAnchors
import com.eventverse.app.presentation.tutorial.tutorialAnchor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.infrastructure.navigation.PlatformNavigation
import com.eventverse.app.presentation.crm.components.AddCustomFieldDialog
import com.eventverse.app.presentation.crm.components.CreateLeadDialog
import com.eventverse.app.presentation.crm.components.CrmKanbanBoard
import com.eventverse.app.presentation.crm.components.CrmMobileKanbanView
import com.eventverse.app.presentation.crm.components.LeadActivitiesDialog
import com.eventverse.app.presentation.deal.components.ContactsPane
import com.eventverse.app.presentation.deal.components.DealsPane
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * The CRM Leads workspace — the first operational module to replace the generic
 * `ModuleWorkspaceScreen` placeholder with a real screen. Adaptive: a two-pane
 * master-detail layout at [ClayBreakpoints.MasterDetail] and above, a single-column card
 * feed with a bottom-sheet inspector below it. No spreadsheet grid — see the plan's
 * rationale for rejecting a dense `ClayDataGrid` in favour of this pattern.
 */
@Composable
fun CrmWorkspaceScreen(
    tenantSlug: String,
    access: ModuleAccessConfig,
    modifier: Modifier = Modifier
) {
    val viewModel = remember(tenantSlug) { CrmViewModel(tenantSlug = tenantSlug, access = access) }
    val state by viewModel.uiState.collectAsState()
    val employees by RbacAccessPolicyRepository.shared.employees.collectAsState()

    // Tab direktori hidup di URL, bukan hanya di state Compose: memuat ulang halaman saat
    // sedang membuka Deal harus kembali ke Deal, dan tombol Back browser harus memindahkan
    // tab, bukan melempar keluar dari modul. Polanya sama dengan sub-rute Invoicing.
    val initialPath = remember { PlatformNavigation.getCurrentPath() }
    var directoryTab by remember { mutableStateOf(resolveCrmDirectoryTab(initialPath) ?: CrmDirectoryTab.LEADS) }

    LaunchedEffect(Unit) {
        // URL telanjang "/crm-sales" dinormalkan ke sub-rutenya supaya alamat yang tersalin
        // selalu menyebut tab yang sedang dilihat. replace, bukan push: ini bukan langkah baru.
        if (resolveCrmDirectoryTab(PlatformNavigation.getCurrentPath()) == null) {
            PlatformNavigation.replacePath(directoryTab.route)
        }

        PlatformNavigation.listenToPathChanges { newPath ->
            if (AppNavScreen.fromPath(newPath) == AppNavScreen.CRM_SALES) {
                resolveCrmDirectoryTab(newPath)?.let { tab ->
                    if (tab != directoryTab) directoryTab = tab
                }
            }
        }
    }

    LaunchedEffect(tenantSlug) { viewModel.onEvent(CrmUiEvent.Load) }

    if (state.isLoading) {
        Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
            Text(text = "Memuat data CRM…", color = WeMadeColors.OnSurfaceMuted)
        }
        return
    }

    if (state.error != null && state.leads.isEmpty()) {
        Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
            Text(text = "Gagal memuat: ${state.error}", color = WeMadeColors.Error)
        }
        return
    }

    Column(modifier = modifier.fillMaxSize()) {
        state.error?.let { err ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = ClaySpacing.Xxl, vertical = ClaySpacing.Sm)
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.ErrorBg,
                        outline = WeMadeColors.Error,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = err,
                    color = WeMadeColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "Tutup",
                    onClick = { viewModel.onEvent(CrmUiEvent.DismissError) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        // Tab direktori CRM: Leads (papan Kanban) | Deal | Kontak
        Row(
            modifier = Modifier
                .padding(horizontal = ClaySpacing.Xxl, vertical = ClaySpacing.Sm)
                .tutorialAnchor(GarmentTutorialAnchors.CRM_DIRECTORY_TABS),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            CrmDirectoryTab.entries.forEach { tab ->
                val isSelected = directoryTab == tab
                ClayButton(
                    text = tab.label,
                    onClick = {
                        if (directoryTab != tab) {
                            directoryTab = tab
                            PlatformNavigation.pushPath(tab.route)
                        }
                    },
                    style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        val isDesktop = maxWidth >= ClayBreakpoints.MasterDetail

        if (directoryTab == CrmDirectoryTab.DEALS) {
            DealsPane(tenantSlug = tenantSlug, modifier = Modifier.fillMaxSize())
        } else if (directoryTab == CrmDirectoryTab.CONTACTS) {
            ContactsPane(tenantSlug = tenantSlug, modifier = Modifier.fillMaxSize())
        } else {
            if (isDesktop) {
                CrmKanbanBoard(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ stage -> viewModel.onEvent(CrmUiEvent.OpenCreateDialog(stage)) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { leadId, stage -> viewModel.onEvent(CrmUiEvent.UpdateStage(leadId, stage)) },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    onOpenActivities = { viewModel.onEvent(CrmUiEvent.OpenActivities(it)) },
                    activities = state.leadActivities,
                    isLoadingActivities = state.isLoadingActivities,
                    isSubmittingActivity = state.isSubmittingActivity,
                    onSubmitActivity = { content ->
                        state.selectedLeadId?.let { leadId ->
                            viewModel.onEvent(CrmUiEvent.SubmitActivity(leadId, content))
                        }
                    },
                    kpiMetrics = state.kpiMetrics,
                    selectedEmployeeId = state.selectedEmployeeId,
                    selectedSource = state.selectedSource,
                    onFilterEmployee = { viewModel.onEvent(CrmUiEvent.FilterByEmployee(it)) },
                    onFilterSource = { viewModel.onEvent(CrmUiEvent.FilterBySource(it)) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                CrmMobileKanbanView(
                    leads = state.visibleLeads,
                    schema = state.schema,
                    employees = employees,
                    selectedLeadId = state.selectedLeadId,
                    searchQuery = state.searchQuery,
                    activeStage = state.activeMobileStage,
                    canWrite = state.canWrite,
                    canManage = state.canManage,
                    onSelectStage = { viewModel.onEvent(CrmUiEvent.SetMobileStage(it)) },
                    onSearchQueryChange = { viewModel.onEvent(CrmUiEvent.UpdateSearchQuery(it)) },
                    onSelectLead = { viewModel.onEvent(CrmUiEvent.SelectLead(it)) },
                    onAddLead = if (state.canWrite) ({ stage -> viewModel.onEvent(CrmUiEvent.OpenCreateDialog(stage)) }) else null,
                    onCommitField = { fieldId, value -> viewModel.onEvent(CrmUiEvent.CommitField(fieldId, value)) },
                    onUpdateStage = { leadId, stage -> viewModel.onEvent(CrmUiEvent.UpdateStage(leadId, stage)) },
                    onArchive = { viewModel.onEvent(CrmUiEvent.ArchiveLead(it)) },
                    onAddField = { viewModel.onEvent(CrmUiEvent.OpenAddFieldDialog) },
                    onDeleteField = { viewModel.onEvent(CrmUiEvent.DeleteCustomField(it)) },
                    onOpenActivities = { viewModel.onEvent(CrmUiEvent.OpenActivities(it)) },
                    activities = state.leadActivities,
                    isLoadingActivities = state.isLoadingActivities,
                    isSubmittingActivity = state.isSubmittingActivity,
                    onSubmitActivity = { content ->
                        state.selectedLeadId?.let { leadId ->
                            viewModel.onEvent(CrmUiEvent.SubmitActivity(leadId, content))
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

    if (state.isCreateDialogOpen) {
        CreateLeadDialog(
            initialStage = state.createDialogInitialStage,
            customSchema = state.customFieldSchema,
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseCreateDialog) },
            onCreate = { viewModel.onEvent(it) }
        )
    }

    if (state.isAddFieldDialogOpen) {
        AddCustomFieldDialog(
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseAddFieldDialog) },
            onAdd = { label, type, isRequired ->
                viewModel.onEvent(CrmUiEvent.AddCustomField(label, type, isRequired))
            }
        )
    }

    state.activeLeadForActivities?.let { lead ->
        LeadActivitiesDialog(
            lead = lead,
            activities = state.leadActivities,
            isLoading = state.isLoadingActivities,
            isSubmitting = state.isSubmittingActivity,
            onDismiss = { viewModel.onEvent(CrmUiEvent.CloseActivities) },
            onSubmit = { content ->
                viewModel.onEvent(CrmUiEvent.SubmitActivity(lead.id, content))
            }
        )
    }
}

/**
 * Tab direktori di atas layar CRM: papan lead (default), daftar seluruh deal,
 * dan master data kontak. Deal/Kontak sengaja bukan menu modul tersendiri —
 * keduanya bagian dari bounded context CRM_SALES yang sama (RBAC & entitlement ikut CRM).
 */
private enum class CrmDirectoryTab(val label: String, val slug: String) {
    LEADS("Leads", "leads"),
    DEALS("Deal", "deals"),
    CONTACTS("Kontak", "contacts");

    /** Sub-rute kanonik tab ini; dipakai untuk push/replace URL dan deep link. */
    val route: String get() = "${AppNavScreen.CRM_SALES.route}/$slug"
}

/**
 * Membaca tab direktori dari URL. Mencocokkan segmen terakhir saja agar semua alias
 * modul CRM ("/crm-sales", "/crm", "/sales") ikut bekerja tanpa didaftar satu per satu.
 * Mengembalikan null bila URL belum menyebut sub-rute apa pun.
 */
private fun resolveCrmDirectoryTab(rawPath: String): CrmDirectoryTab? {
    val normalized = rawPath.trim()
        .removePrefix("#")
        .substringBefore("?")
        .substringBefore("#")
        .removeSuffix("/")
        .lowercase()
    val segment = normalized.substringAfterLast('/')
    return CrmDirectoryTab.entries.firstOrNull { it.slug == segment }
}
