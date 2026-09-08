package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun ModuleMatrixRow(
    module: BusinessModule,
    config: ModuleAccessConfig,
    onAccessChanged: (AccessLevel, DataScope) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = WeMadeColors.Surface),
        border = BorderStroke(1.dp, WeMadeColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Left: Module Info & Description
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModuleCategoryBadge(category = module.category.displayName)

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = module.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface
                            )
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = module.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9), thickness = 1.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // Right/Bottom Controls: 4-Tier Segmented Access Selector & Scope
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 4-Tier Access Level Segmented Control
                SegmentedAccessControl(
                    currentLevel = config.level,
                    onSelectLevel = { newLevel ->
                        onAccessChanged(newLevel, config.scope)
                    }
                )

                // Data Scope Selector (Only visible if module is accessible)
                if (config.isAccessible) {
                    DataScopeSelector(
                        currentScope = config.scope,
                        onSelectScope = { newScope ->
                            onAccessChanged(config.level, newScope)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ModuleCategoryBadge(category: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(WeMadeColors.PrimaryContainer)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = category.take(3).uppercase(),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = 10.sp),
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.PrimaryDark
        )
    }
}

@Composable
private fun SegmentedAccessControl(
    currentLevel: AccessLevel,
    onSelectLevel: (AccessLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF1F5F9))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccessLevel.entries.forEach { level ->
            val isSelected = level == currentLevel

            val (activeBg, activeText) = when (level) {
                AccessLevel.NONE -> Color(0xFFE2E8F0) to Color(0xFF475569)
                AccessLevel.VIEW -> Color(0xFFE0F2FE) to Color(0xFF0369A1)
                AccessLevel.OPERATE -> Color(0xFFFEF3C7) to Color(0xFFB45309)
                AccessLevel.MANAGE -> Color(0xFFD1FAE5) to Color(0xFF047857)
            }

            val itemModifier = if (isSelected) {
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(activeBg)
                    .border(1.dp, activeText.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            } else {
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Transparent)
            }

            Box(
                modifier = itemModifier
                    .clickable { onSelectLevel(level) }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.displayName,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) activeText else Color(0xFF64748B)
                )
            }
        }
    }
}

@Composable
private fun DataScopeSelector(
    currentScope: DataScope,
    onSelectScope: (DataScope) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Jangkauan:",
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            fontWeight = FontWeight.Medium
        )

        DataScope.entries.forEach { scope ->
            val isSelected = scope == currentScope

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) WeMadeColors.PrimaryDark else Color(0xFFF1F5F9))
                    .clickable { onSelectScope(scope) }
                    .padding(horizontal = 8.dp, vertical = 5.dp)
            ) {
                Text(
                    text = scope.shortLabel,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else Color(0xFF475569)
                )
            }
        }
    }
}
