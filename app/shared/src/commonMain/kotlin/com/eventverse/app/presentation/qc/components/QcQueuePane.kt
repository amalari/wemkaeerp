package com.eventverse.app.presentation.qc.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.qc.QcQueueBucket
import com.eventverse.app.presentation.qc.QcQueueItem
import com.eventverse.app.presentation.qc.isStale
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Panel kiri master-detail: antrean kerja QC.
 *
 * Perannya hanya memilih — seluruh lembar inspeksi ada di panel kanan. Karena itu kartunya
 * sengaja rapat: yang perlu terbaca sekilas cuma nomor SPK, klien, dan sudah berapa lama
 * sampel ini menganggur.
 */
@Composable
fun QcQueuePane(
    items: List<QcQueueItem>,
    selectedOrderId: SamplingOrderId?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (SamplingOrderId) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = "Cari SPK / klien / style",
            modifier = Modifier.fillMaxWidth()
        )

        if (items.isEmpty()) {
            Text(
                text = if (searchQuery.isBlank()) {
                    "Belum ada sampel jadi dari finishing yang siap diinspeksi."
                } else {
                    "Tidak ada SPK yang cocok dengan pencarian."
                },
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            QcQueueBucket.entries.forEach { bucket ->
                val bucketItems = items.filter { it.bucket == bucket }
                if (bucketItems.isEmpty()) return@forEach

                item(key = "header-${bucket.name}") {
                    QcBucketHeader(bucket = bucket, count = bucketItems.size)
                }

                items(bucketItems, key = { it.order.id.value }) { queueItem ->
                    QcQueueCard(
                        item = queueItem,
                        isSelected = queueItem.order.id == selectedOrderId,
                        onClick = { onSelect(queueItem.order.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun QcBucketHeader(bucket: QcQueueBucket, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm, bottom = ClaySpacing.Xxs),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = bucket.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}

@Composable
private fun QcQueueCard(
    item: QcQueueItem,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    // Satu ketebalan outline untuk semua state; yang membedakan warnanya (design system Kontrak 8).
    val outline = when {
        isSelected -> WeMadeColors.Primary
        item.isStale -> WeMadeColors.Warning
        else -> WeMadeColors.Outline
    }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        outlineColor = outline,
        containerColor = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        selected = isSelected,
        contentPadding = PaddingValues(ClaySpacing.Lg),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = item.spk,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.designCode != null) {
                    ClayBadge(
                        text = item.designCode,
                        tint = WeMadeColors.Accent
                    )
                }
            }
            Spacer(modifier = Modifier.width(ClaySpacing.Sm))
            QcQueueTimingBadge(item = item)
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Xxs))

        Text(
            text = item.order.clientName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "${item.order.styleName} • ${item.order.sampleQuantity} Pcs",
            style = MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(ClaySpacing.Sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ClayBadge(
                text = "${item.inspectedQty}/${item.targetQty} pcs diperiksa",
                tint = if (item.isFullyInspected) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
            )
            if (item.totalDealDesigns > 1) {
                Text(
                    text = "Desain ${item.designNumber}/${item.totalDealDesigns}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Accent
                )
            }
        }
    }
}

@Composable
private fun QcQueueTimingBadge(item: QcQueueItem) {
    if (item.bucket == QcQueueBucket.SETTLED) {
        val report = item.latestReport ?: return
        ClayBadge(text = report.qcResult.displayName, tint = WeMadeColors.OnSurfaceMuted)
        return
    }

    ClayBadge(
        text = item.waitingLabel,
        tint = if (item.isStale) WeMadeColors.Warning else WeMadeColors.OnSurfaceMuted,
        dot = item.isStale
    )
}
