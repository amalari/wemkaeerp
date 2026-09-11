package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.CustomRole
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun RoleListSidebar(
    roles: List<CustomRole>,
    selectedRoleId: String?,
    onSelectRole: (String) -> Unit,
    onOpenCreateModal: () -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxHeight(),
        shape = ClayShapes.Panel,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = ClayOffset.Rest,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // Header: Section title & Add button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Daftar Jabatan",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "${roles.size} jabatan terdaftar",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }

            ClayButton(
                text = "Tambah",
                onClick = onOpenCreateModal,
                style = ClayButtonStyle.Primary,
                contentPadding = PaddingValues(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                leading = { IconPlus(modifier = Modifier.size(13.dp), color = Color.White) }
            )
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

        // Role items list
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            items(roles, key = { it.id.value }) { role ->
                val isSelected = role.id.value == selectedRoleId

                RoleItemCard(
                    role = role,
                    isSelected = isSelected,
                    onClick = { onSelectRole(role.id.value) }
                )
            }
        }
    }
}

@Composable
private fun RoleItemCard(
    role: CustomRole,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium
            )
            .clickable { onClick() }
            .padding(ClaySpacing.Md)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = role.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (role.isSystemDefault) {
                    ClayTag(
                        text = "Bawaan",
                        tint = WeMadeColors.OnSurfaceMuted,
                        fontSize = 9.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = role.description,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(ClaySpacing.Sm))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (role.userCount > 0) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted)
                )
                Text(
                    text = "${role.userCount} staf ditugaskan",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
