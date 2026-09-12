package com.eventverse.app.presentation.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Satu entri menu di [ClayNavDrawer]. Netral terhadap domain — [icon] hanya menerima warna tint,
 * bukan tipe yang menjelaskan modul apa yang sedang diwakilinya.
 */
data class ClayNavItem(
    val key: String,
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val icon: @Composable (tint: Color) -> Unit,
    /** Teks pendek di sisi kanan baris, mis. status wewenang. Null = tanpa badge. */
    val badge: String? = null,
    /** Warna badge; null memakai warna netral. Tetap `Color`, bukan tipe domain — Kontrak 6. */
    val badgeTint: Color? = null,
    /** False membuat baris teredam dan tidak dapat diklik. */
    val enabled: Boolean = true
)

/** Lebar panel drawer. Sepadan dengan panel produk Google Cloud Console. */
private val DrawerWidth = 300.dp

/**
 * Drawer navigasi kiri mengikuti mekanisme panel produk Google Cloud Console: dibuka dari tombol
 * hamburger, melayang **di atas** konten dengan peredup di belakangnya, dan ditutup lewat tombol
 * ✕, klik di area peredup, atau setelah sebuah item dipilih.
 *
 * Dirender dalam bahasa clay: outline tebal + hard shadow ke kanan (panel menempel di tepi kiri,
 * jadi bayangannya jatuh ke arah konten dan terbaca sebagai benda padat yang menimpanya).
 *
 * Item dibedakan lewat **warna**, bukan ketebalan outline — lihat Kontrak 8 di
 * `design-system-rules.md`. Bentuk barisnya [ClayShapes.Pill] supaya sekeluarga dengan pil
 * navigasi GCP.
 *
 * Saat [open] false komponen ini tidak menggambar apa pun dan tidak memasang pointer input, jadi
 * konten di bawahnya tetap bisa diklik meski Box pembungkusnya memenuhi layar.
 */
@Composable
fun ClayNavDrawer(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    items: List<ClayNavItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sectionLabel: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    val scrimInteraction = remember { MutableInteractionSource() }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(animationSpec = tween(durationMillis = 160)),
            exit = fadeOut(animationSpec = tween(durationMillis = 160))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(WeMadeColors.Scrim.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = scrimInteraction,
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.TopStart),
            enter = slideInHorizontally(animationSpec = tween(durationMillis = 200)) { -it },
            exit = slideOutHorizontally(animationSpec = tween(durationMillis = 180)) { -it }
        ) {
            Column(
                modifier = Modifier
                    .width(DrawerWidth)
                    .fillMaxHeight()
                    .claySurface(
                        shape = RectangleShape,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        shadowX = ClayOffset.Rest,
                        shadowY = 0.dp,
                        borderWidth = ClayBorder.Thick,
                        innerShade = false
                    )
                    .padding(vertical = ClaySpacing.Xl, horizontal = ClaySpacing.Lg)
            ) {
                DrawerHeader(title = title, subtitle = subtitle, onDismiss = onDismiss)

                Spacer(modifier = Modifier.height(ClaySpacing.Xl))
                DrawerDivider()

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = ClaySpacing.Lg)
                ) {
                    if (sectionLabel != null) {
                        Text(
                            text = sectionLabel,
                            modifier = Modifier.padding(
                                start = ClaySpacing.Lg,
                                bottom = ClaySpacing.Md
                            ),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }

                    items.forEach { item ->
                        DrawerRow(item = item)
                        Spacer(modifier = Modifier.height(ClaySpacing.Xs))
                    }
                }

                if (footer != null) {
                    DrawerDivider()
                    Spacer(modifier = Modifier.height(ClaySpacing.Lg))
                    footer()
                }
            }
        }
    }
}

@Composable
private fun DrawerHeader(title: String, subtitle: String?, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayIconButton(
            onClick = onDismiss,
            shape = CircleShape,
            size = 30.dp,
            containerColor = WeMadeColors.SurfaceMuted
        ) {
            IconClose(modifier = Modifier.size(13.dp), color = WeMadeColors.OnSurface)
        }

        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DrawerDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ClayBorder.Medium)
            .background(WeMadeColors.Border)
    )
}

@Composable
private fun DrawerRow(item: ClayNavItem) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val highlighted = item.enabled && (item.selected || isPressed)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Pill,
                background = if (highlighted) WeMadeColors.PrimaryContainer else Color.Transparent,
                outline = if (highlighted) WeMadeColors.Primary else Color.Transparent,
                borderWidth = ClayBorder.Medium
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = item.enabled,
                onClick = item.onClick
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Label boleh mengecil, badge tidak. Tanpa weight(fill = false) di sisi kiri, label panjang
        // mengambil hampir seluruh lebar dan badge pecah satu huruf per baris — Kontrak 13.
        Row(
            modifier = Modifier.weight(1f, fill = false),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val tint = when {
                !item.enabled -> WeMadeColors.OnSurfaceDisabled
                item.selected -> WeMadeColors.Primary
                else -> WeMadeColors.OnSurfaceMuted
            }
            Box(modifier = Modifier.size(20.dp)) { item.icon(tint) }
            Text(
                text = item.label,
                modifier = Modifier.weight(1f, fill = false),
                fontSize = 13.sp,
                fontWeight = if (item.selected) FontWeight.Bold else FontWeight.Medium,
                color = when {
                    !item.enabled -> WeMadeColors.OnSurfaceDisabled
                    item.selected -> WeMadeColors.PrimaryDark
                    else -> WeMadeColors.OnSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (item.badge != null) {
            Spacer(modifier = Modifier.width(ClaySpacing.Sm))
            ClayTag(
                text = item.badge,
                tint = item.badgeTint ?: WeMadeColors.OnSurfaceMuted
            )
        }
    }
}
