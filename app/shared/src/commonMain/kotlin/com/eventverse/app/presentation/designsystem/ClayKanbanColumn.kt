package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu kolom papan kanban: bar judul clay berwarna [tint] + gelembung jumlah, badan ber-tint
 * lembut, dan isi kartu yang di-scroll.
 *
 * Diangkat ke design system sebagai pemakaian ketiga pola yang sama (Kanban Sampling, Kanban CRM,
 * meja operator) — Aturan Tiga Kali. Buta domain: pemanggil yang menerjemahkan statusnya ke
 * [title]/[tint] dan merender kartunya sendiri lewat [content].
 */
@Composable
fun ClayKanbanColumn(
    title: String,
    count: Int,
    tint: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    emptyText: String = "Kosong",
    content: LazyListScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Panel,
                background = tint.copy(alpha = 0.07f),
                outline = tint.copy(alpha = 0.30f),
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md)
    ) {
        ClayKanbanColumnHeader(title = title, count = count, tint = tint)
        if (subtitle != null) {
            Text(
                text = subtitle,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = ClaySpacing.Sm, vertical = ClaySpacing.Md)
            )
        } else {
            Spacer(Modifier.size(ClaySpacing.Md))
        }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (count == 0) {
                Text(
                    text = emptyText,
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).padding(ClaySpacing.Lg)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    // Ruang bayangan clay kartu terakhir supaya tidak terpotong tepi kolom.
                    contentPadding = PaddingValues(bottom = ClaySpacing.Md, end = ClaySpacing.Sm),
                    content = content
                )
            }
        }
    }
}

@Composable
private fun ClayKanbanColumnHeader(title: String, count: Int, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Button,
                background = tint,
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = WeMadeColors.Surface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(ClaySpacing.Sm))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clayFlat(
                    shape = CircleShape,
                    background = WeMadeColors.Surface.copy(alpha = 0.25f),
                    outline = WeMadeColors.Surface.copy(alpha = 0.55f),
                    borderWidth = ClayBorder.Medium
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "$count", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.Surface)
        }
    }
}
