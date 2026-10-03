package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Bilah tab segmen: satu tab terpilih, sisanya redup. Dirakit dari [ClayChoiceChip] sehingga bahasa
 * visualnya sama dengan chip pilihan lain; buta domain — hanya menerima label, indeks, dan lambda.
 *
 * [badges] opsional (mis. jumlah item) ditempel ke label: `"Modul · 9"`.
 */
@Composable
fun ClayTabBar(
    tabs: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    badges: List<String?> = emptyList(),
    tint: Color = WeMadeColors.Primary
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEachIndexed { index, label ->
            val badge = badges.getOrNull(index)
            ClayChoiceChip(
                text = if (badge != null) "$label · $badge" else label,
                selected = index == selectedIndex,
                onClick = { onSelect(index) },
                tint = tint
            )
        }
    }
}
