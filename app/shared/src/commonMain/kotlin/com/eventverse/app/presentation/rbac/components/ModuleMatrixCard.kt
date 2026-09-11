package com.eventverse.app.presentation.rbac.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.DataScope
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun ModuleMatrixRow(
    module: BusinessModule,
    config: ModuleAccessConfig,
    onAccessChanged: (AccessLevel, DataScope) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        containerColor = WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        shadowColor = WeMadeColors.Outline,
        offset = ClayOffset.Small,
        borderWidth = ClayBorder.Thick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            // Left: Module Info & Description
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayTag(
                    text = module.category.displayName.take(3).uppercase(),
                    tint = WeMadeColors.Primary,
                    fontSize = 10.sp
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = module.description,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = WeMadeColors.OnSurfaceMuted,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))
        HorizontalDivider(color = WeMadeColors.Border, thickness = ClayBorder.Hairline)
        Spacer(modifier = Modifier.height(ClaySpacing.Sm))

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
                DynamicDataScopeSelector(
                    module = module,
                    currentScope = config.scope,
                    onSelectScope = { newScope ->
                        onAccessChanged(config.level, newScope)
                    }
                )
            }
        }
    }
}

@Composable
private fun SegmentedAccessControl(
    currentLevel: AccessLevel,
    onSelectLevel: (AccessLevel) -> Unit
) {
    Row(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AccessLevel.entries.forEach { level ->
            val isSelected = level == currentLevel

            val activeBg: Color
            val activeTint: Color
            when (level) {
                AccessLevel.NONE -> {
                    activeBg = WeMadeColors.Surface
                    activeTint = WeMadeColors.OnSurfaceMuted
                }
                AccessLevel.VIEW -> {
                    activeBg = WeMadeColors.PrimaryContainer
                    activeTint = WeMadeColors.Primary
                }
                AccessLevel.OPERATE -> {
                    activeBg = WeMadeColors.WarningBg
                    activeTint = WeMadeColors.Warning
                }
                AccessLevel.MANAGE -> {
                    activeBg = WeMadeColors.SuccessBg
                    activeTint = WeMadeColors.Success
                }
            }

            Box(
                modifier = Modifier
                    .then(
                        if (isSelected) {
                            Modifier.clayFlat(
                                shape = ClayShapes.Chip,
                                background = activeBg,
                                outline = activeTint,
                                borderWidth = ClayBorder.Medium
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable { onSelectLevel(level) }
                    .padding(horizontal = ClaySpacing.Md, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.displayName,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) activeTint else WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}

@Composable
private fun DynamicDataScopeSelector(
    module: BusinessModule,
    currentScope: DataScope,
    onSelectScope: (DataScope) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        Text(
            text = "Jangkauan:",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = WeMadeColors.OnSurfaceMuted,
            fontWeight = FontWeight.SemiBold
        )

        if (module.isGlobalOnly) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconGlobe(modifier = Modifier.size(13.dp), color = WeMadeColors.Primary)
                ClayTag(
                    text = "Seluruh Pabrik (Data Bersama)",
                    tint = WeMadeColors.Primary,
                    fontSize = 10.sp
                )
            }
        } else {
            // Hierarchical selectable scopes (Sendiri, Bawahan, Semua)
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                module.supportedScopes.forEach { scope ->
                    val isSelected = scope == currentScope

                    Box(
                        modifier = Modifier
                            .clayFlat(
                                shape = ClayShapes.Chip,
                                background = if (isSelected) WeMadeColors.Primary else WeMadeColors.Surface,
                                outline = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.Border,
                                borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Hairline
                            )
                            .clickable { onSelectScope(scope) }
                            .padding(horizontal = ClaySpacing.Sm, vertical = 5.dp)
                    ) {
                        Text(
                            text = scope.shortLabel,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else WeMadeColors.OnSurface
                        )
                    }
                }
            }
        }
    }
}
