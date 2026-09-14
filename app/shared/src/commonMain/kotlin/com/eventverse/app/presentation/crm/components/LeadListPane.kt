package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.crm.LeadId
import com.eventverse.app.presentation.crm.tint
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * The "master" list of leads. Shared by the desktop two-pane layout (in a fixed-width
 * column) and the mobile feed (full width) — one component, two containers, per
 * [com.eventverse.app.presentation.crm.CrmWorkspaceScreen]'s breakpoint branch.
 */
@Composable
fun LeadListPane(
    leads: List<CrmLead>,
    selectedLeadId: LeadId?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelectLead: (LeadId) -> Unit,
    onAddLead: (() -> Unit)?,
    viewMode: com.eventverse.app.presentation.crm.CrmViewMode = com.eventverse.app.presentation.crm.CrmViewMode.LIST,
    onViewModeChange: ((com.eventverse.app.presentation.crm.CrmViewMode) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Lg),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = "Cari brand atau kontak…",
                modifier = Modifier.weight(1f, fill = true)
            )
            if (onViewModeChange != null) {
                CrmViewToggle(
                    currentMode = viewMode,
                    onModeChange = onViewModeChange
                )
            }
        }

        if (onAddLead != null) {
            com.eventverse.app.presentation.designsystem.ClayButton(
                text = "+ Tambah Lead",
                onClick = onAddLead,
                modifier = Modifier.fillMaxWidth().padding(bottom = ClaySpacing.Lg)
            )
        }

        if (leads.isEmpty()) {
            Text(
                text = "Belum ada lead. Tambahkan yang pertama.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xl)
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            items(leads, key = { it.id.value }) { lead ->
                LeadListItem(
                    lead = lead,
                    selected = lead.id == selectedLeadId,
                    onClick = { onSelectLead(lead.id) }
                )
            }
        }
    }
}

@Composable
private fun LeadListItem(lead: CrmLead, selected: Boolean, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        selected = selected,
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = lead.brandName.value,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (lead.contactPerson.isNotBlank()) {
                    Text(
                        text = lead.contactPerson,
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ClayBadge(text = lead.stage.displayName.take(18), tint = lead.stage.tint())
        }
    }
}
