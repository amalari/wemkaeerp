package com.eventverse.app.presentation.builder.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconChevronUp
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.DiscoveryModuleUi
import com.eventverse.app.presentation.discovery.resolveClayAsset
import com.eventverse.app.presentation.discovery.resolveModuleIcon
import com.eventverse.app.presentation.discovery.resolveSectionStyle
import com.eventverse.app.presentation.theme.WeMadeColors
import org.jetbrains.compose.resources.painterResource

/**
 * Kartu Modul Terpilih (Selected Module Card) di Sidebar Chat:
 * Menempatkan konteks modul langsung di area percakapan pengguna bersama AI:
 * - Menampilkan nama modul, status aktif, divisi, dan dropdown pemilih modul.
 * - Accordion ringkas fitur modul yang dapat di-expand/collapse tepat di area chat.
 */
@Composable
internal fun SelectedModuleChatCard(
    draft: DiscoveryDraftUi?,
    selectedModuleId: String?,
    threads: List<ChatThread>,
    pendingByThread: Map<String?, Int>,
    onSelectThread: (String?) -> Unit,
    modifier: Modifier = Modifier,
    briefsBusy: Boolean = false,
    briefNotice: String? = null,
    onRefreshBriefs: (() -> Unit)? = null
) {
    val typography = rememberClayTypography()
    var dropdownExpanded by remember { mutableStateOf(false) }
    var featuresExpanded by remember(selectedModuleId) { mutableStateOf(false) }

    val selectedModule = selectedModuleId?.let { id -> draft?.modules?.firstOrNull { it.id == id } }
    val currentThread = threads.firstOrNull { it.moduleId == selectedModuleId } ?: threads.firstOrNull()
    val totalPending = pendingByThread.values.sum()
    val currentPending = pendingByThread[selectedModuleId]?.takeIf { it > 0 }
        ?: if (selectedModuleId == null && totalPending > 0) totalPending else null

    Box(modifier = modifier.fillMaxWidth()) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            shape = ClayShapes.Card,
            containerColor = WeMadeColors.Surface,
            outlineColor = WeMadeColors.Outline,
            offset = ClayOffset.Small
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(ClaySpacing.Md),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                // Header Baris 1: Label Konteks + Dropdown Pemilih
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Text(
                            text = "Modul Terpilih",
                            style = typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        if (currentPending != null && currentPending > 0) {
                            ClayBadge(
                                text = "$currentPending klarifikasi",
                                tint = WeMadeColors.Accent,
                                dot = true
                            )
                        }
                    }

                    // Tombol Dropdown Ganti Modul
                    Row(
                        modifier = Modifier
                            .clickable { dropdownExpanded = true }
                            .claySurface(
                                shape = ClayShapes.Pill,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.OutlineSoft,
                                offset = ClayOffset.Flat,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(horizontal = ClaySpacing.Sm, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = if (selectedModule == null) "Semua Modul" else "Ganti",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.Primary
                        )
                        if (dropdownExpanded) {
                            IconChevronUp(Modifier.size(10.dp))
                        } else {
                            IconChevronDown(Modifier.size(10.dp))
                        }
                    }
                }

                // Baris 2: Detail Modul yang Sedang Aktif
                if (selectedModule != null && draft != null) {
                    val sectionStyle = resolveSectionStyle(selectedModule.section, draft)
                    val clayRes = resolveClayAsset(selectedModule)
                    val iconRenderer = resolveModuleIcon(selectedModule)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        // Mini Icon Clay / Vektor
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .claySurface(
                                    shape = ClayShapes.Button,
                                    background = sectionStyle.background,
                                    outline = sectionStyle.color,
                                    offset = ClayOffset.Flat,
                                    borderWidth = ClayBorder.Hairline
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (clayRes != null) {
                                Image(
                                    painter = painterResource(clayRes),
                                    contentDescription = selectedModule.displayName,
                                    modifier = Modifier.size(28.dp)
                                )
                            } else {
                                iconRenderer(Modifier.size(18.dp), sectionStyle.color)
                            }
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = selectedModule.displayName,
                                style = typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                            ) {
                                ClayBadge(
                                    text = sectionStyle.title.uppercase(),
                                    tint = sectionStyle.color
                                )
                                ClayBadge(
                                    text = if (selectedModule.active) "AKTIF" else "Non-aktif",
                                    tint = if (selectedModule.active) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted,
                                    dot = selectedModule.active
                                )
                            }
                        }
                    }

                    // Baris 3: Accordion Fitur Modul (Kombinasi Opsi A)
                    val moduleScreens = draft.screens.filter { it.moduleId == selectedModule.id }
                    val features = buildList {
                        selectedModule.slotInput?.let { add("Terima alur: $it") }
                        selectedModule.slotOutput?.let { add("Keluarkan: $it") }
                        if (moduleScreens.isNotEmpty()) {
                            moduleScreens.forEach { add("Layar: ${it.title} (${it.widget})") }
                        } else {
                            add("Dukungan proses operasional alur")
                        }
                    }

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { featuresExpanded = !featuresExpanded }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Fitur Modul (${features.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            if (featuresExpanded) {
                                IconChevronUp(Modifier.size(10.dp))
                            } else {
                                IconChevronDown(Modifier.size(10.dp))
                            }
                        }

                        if (featuresExpanded) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp, bottom = 4.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                features.forEach { feat ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(4.dp)
                                                .background(sectionStyle.color, CircleShape)
                                        )
                                        Text(
                                            text = feat,
                                            fontSize = 11.sp,
                                            color = WeMadeColors.OnSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Ketika "Semua Modul" terpilih
                    Text(
                        text = "Fokus pada seluruh alur draf tenant (${draft?.modules?.size ?: 0} modul)",
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                if (onRefreshBriefs != null) {
                    ClayButton(
                        text = if (briefsBusy) "Menyegarkan..." else "Segarkan ringkasan",
                        onClick = onRefreshBriefs,
                        style = ClayButtonStyle.Secondary,
                        enabled = !briefsBusy,
                        maxLines = 1
                    )
                    briefNotice?.let {
                        Text(it, style = typography.bodySmall, color = WeMadeColors.OnSurfaceMuted)
                    }
                }
            }
        }

        // Dropdown Menu Pemilih Utas Modul
        DropdownMenu(
            expanded = dropdownExpanded,
            onDismissRequest = { dropdownExpanded = false },
            modifier = Modifier.background(WeMadeColors.Surface)
        ) {
            threads.forEach { t ->
                val isSelected = t.moduleId == selectedModuleId
                val count = pendingByThread[t.moduleId]?.takeIf { it > 0 }
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = t.label,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface,
                                fontSize = 12.sp
                            )
                            if (count != null) {
                                ClayBadge(text = count.toString(), tint = WeMadeColors.Accent)
                            }
                        }
                    },
                    onClick = {
                        dropdownExpanded = false
                        onSelectThread(t.moduleId)
                    }
                )
            }
        }
    }
}
