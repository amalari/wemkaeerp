package com.eventverse.app.presentation.builder.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayTabBar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.claySurface
import com.eventverse.app.presentation.designsystem.rememberClayTypography
import com.eventverse.app.presentation.theme.WeMadeColors

import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.dp
import com.eventverse.app.presentation.designsystem.IconChevronDown
import com.eventverse.app.presentation.designsystem.IconChevronUp

/** Satu utas chat: kode modul (null = Semua) dan label tampil. */
internal data class ChatThread(val moduleId: String?, val label: String)

/**
 * Pemilih utas chat ringkas (Dropdown) di dalam sidebar chat:
 * Menggantikan deretan pill panjang di atas layar agar tidak bertumpuk / acak-acakan.
 */
@Composable
internal fun ChatThreadDropdown(
    threads: List<ChatThread>,
    selected: String?,
    pending: Map<String?, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val typography = rememberClayTypography()
    val currentThread = threads.firstOrNull { it.moduleId == selected } ?: threads.firstOrNull()
    val totalPending = pending.values.sum()
    val currentPending = pending[selected]?.takeIf { it > 0 } ?: if (selected == null && totalPending > 0) totalPending else null

    Box(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = true }
                .claySurface(
                    shape = ClayShapes.Pill,
                    background = WeMadeColors.Surface,
                    outline = WeMadeColors.Outline,
                    offset = ClayOffset.Small,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Xs)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Text(
                        text = "Fokus Topik:",
                        style = typography.bodySmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        maxLines = 1
                    )
                    Text(
                        text = currentThread?.label ?: "Semua",
                        style = typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (currentPending != null && currentPending > 0) {
                        ClayBadge(
                            text = currentPending.toString(),
                            tint = WeMadeColors.Accent,
                            dot = true
                        )
                    }
                }
                if (expanded) {
                    IconChevronUp(Modifier.size(12.dp))
                } else {
                    IconChevronDown(Modifier.size(12.dp))
                }
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(WeMadeColors.Surface)
        ) {
            threads.forEach { t ->
                val isSelected = t.moduleId == selected
                val count = pending[t.moduleId]?.takeIf { it > 0 }
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
                        expanded = false
                        onSelect(t.moduleId)
                    }
                )
            }
        }
    }
}

/**
 * Tab utas chat (PLAN-builder-interview-chat K4): "Semua" (tanpa filter) lalu satu tab per modul. [pending] = jumlah
 * follow-up menunggu per utas (dari server) ditempel sebagai lencana. Buta domain: hanya label dan lambda.
 */
@Composable
internal fun ChatThreadTabs(
    threads: List<ChatThread>,
    selected: String?,
    pending: Map<String?, Int>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    ClayTabBar(
        tabs = threads.map { it.label },
        selectedIndex = threads.indexOfFirst { it.moduleId == selected }.coerceAtLeast(0),
        onSelect = { onSelect(threads[it].moduleId) },
        modifier = modifier,
        badges = threads.map { t -> pending[t.moduleId]?.takeIf { it > 0 }?.toString() }
    )
}

/** Utas tersedia dari draf: "Semua" + modul aktif (urutan draf). */
internal fun threadsOf(modules: List<Pair<String, String>>): List<ChatThread> =
    listOf(ChatThread(null, "Semua")) + modules.map { (id, name) -> ChatThread(id, name) }

/** Label status run (SSE `status.phase`) untuk indikator memuat; fase tak dikenal ditampilkan apa adanya. */
internal fun runPhaseLabel(phase: String?): String = when {
    phase?.startsWith("module:") == true -> "Menyiapkan ${phase.substringAfter(':')}..."
    phase?.startsWith("ready:") == true -> "${phase.substringAfter(':')} siap..."
    else -> when (phase) {
    "planning" -> "Menyusun alur..."
    "drafting" -> "Menyusun draf..."
    "validating" -> "Memeriksa hasil..."
    "editing" -> "Mengubah modul..."
    "waiting" -> "Menunggu jawaban Anda..."
    null, "" -> "Memproses..."
    else -> "$phase..."
    }
}
