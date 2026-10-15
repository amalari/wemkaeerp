package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.discovery.proposal.ProposalSource
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.designsystem.IconArrowForward
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Modifier untuk menggambar pola dot grid kanvas ala Moqups.
 */
fun Modifier.moqupsDotGrid(): Modifier = drawBehind {
    val dotRadius = 1.1.dp.toPx()
    val spacing = 20.dp.toPx()
    val dotColor = WeMadeColors.WireframeGrid.copy(alpha = 0.65f)
    var x = spacing / 2
    while (x < size.width) {
        var y = spacing / 2
        while (y < size.height) {
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = Offset(x, y)
            )
            y += spacing
        }
        x += spacing
    }
}

/**
 * Tombol bergaya wireframe Moqups hitam-putih.
 */
@Composable
fun WireframeButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    enabled: Boolean = true,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(6.dp)
    val bgColor = when {
        !enabled -> WeMadeColors.WireframeBadgeBg
        filled -> WeMadeColors.WireframeBorder
        else -> WeMadeColors.WireframeSurface
    }
    val borderColor = if (enabled) WeMadeColors.WireframeBorder else WeMadeColors.WireframeBorderSubtle
    val contentColor = when {
        !enabled -> WeMadeColors.WireframeOnSurfaceMuted
        filled -> Color.White
        else -> WeMadeColors.WireframeOnSurface
    }

    Row(
        modifier = modifier
            .background(bgColor, shape)
            .border(1.5.dp, borderColor, shape)
            .then(
                if (enabled) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        leadingIcon?.invoke()
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        trailingIcon?.invoke()
    }
}

/**
 * Tombol ikon bergaya wireframe Moqups hitam-putih.
 */
@Composable
fun WireframeIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val shape = RoundedCornerShape(6.dp)
    val borderColor = if (enabled) WeMadeColors.WireframeBorder else WeMadeColors.WireframeBorderSubtle

    Box(
        modifier = modifier
            .background(WeMadeColors.WireframeSurface, shape)
            .border(1.5.dp, borderColor, shape)
            .then(
                if (enabled) Modifier
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Bilah navigasi alur antarmuka ("Bisa Nyambung"):
 * Menghubungkan layar-layar draf dalam rantai alur kerja visual (pipeline sequence)
 * dengan indikator panah konektor ala diagram alur Moqups.
 */
@Composable
fun MoqupsFlowNavigator(
    screens: List<DiscoveryScreenUi>,
    selectedIndex: Int,
    onSelectScreen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    draftModules: List<DiscoveryModuleUi> = emptyList(),
    isAllScope: Boolean = true,
    onToggleScope: (() -> Unit)? = null,
    totalDraftScreens: Int = screens.size
) {
    val scrollState = rememberScrollState()

    // Otomatis gulir bila layar terpilih berada di luar viewport alur
    LaunchedEffect(selectedIndex, screens.size) {
        if (screens.isNotEmpty()) {
            val approxNodeWidth = 140
            val targetScroll = (selectedIndex * approxNodeWidth - 100).coerceAtLeast(0)
            scrollState.animateScrollTo(targetScroll)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WeMadeColors.WireframeSurface, RoundedCornerShape(8.dp))
            .border(1.5.dp, WeMadeColors.WireframeBorder, RoundedCornerShape(8.dp))
            .padding(ClaySpacing.Sm),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        // Baris Header: Label Alur + Kontrol Navigasi
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                // Ikon titik sambungan alur
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(WeMadeColors.WireframeBorder, CircleShape)
                )
                Text(
                    text = "ALUR PROTOTYPE TERHUBUNG (FLOW)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = WeMadeColors.WireframeOnSurface
                )
                Text(
                    text = "• ${selectedIndex + 1} dari ${screens.size} Layar",
                    fontSize = 11.sp,
                    color = WeMadeColors.WireframeOnSurfaceMuted
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                // Toggle Filter: Semua Alur vs Modul Ini
                if (onToggleScope != null && totalDraftScreens > screens.size) {
                    WireframeButton(
                        text = if (isAllScope) "Semua Alur ($totalDraftScreens)" else "Modul Ini (${screens.size})",
                        onClick = onToggleScope,
                        filled = false
                    )
                }

                // Tombol Kiri-Kanan Cepat
                WireframeIconButton(
                    onClick = { if (selectedIndex > 0) onSelectScreen(selectedIndex - 1) },
                    enabled = selectedIndex > 0
                ) {
                    IconArrowBack(
                        modifier = Modifier.size(12.dp),
                        color = if (selectedIndex > 0) WeMadeColors.WireframeOnSurface else WeMadeColors.WireframeOnSurfaceMuted
                    )
                }
                WireframeIconButton(
                    onClick = { if (selectedIndex < screens.lastIndex) onSelectScreen(selectedIndex + 1) },
                    enabled = selectedIndex < screens.lastIndex
                ) {
                    IconArrowForward(
                        modifier = Modifier.size(12.dp),
                        color = if (selectedIndex < screens.lastIndex) WeMadeColors.WireframeOnSurface else WeMadeColors.WireframeOnSurfaceMuted
                    )
                }
            }
        }

        HorizontalDivider(color = WeMadeColors.WireframeBorderSubtle)

        // Rantai Konektor Alur Antar Layar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
        ) {
            screens.forEachIndexed { index, s ->
                val isSelected = index == selectedIndex
                val module = draftModules.firstOrNull { it.id == s.moduleId }
                val moduleLabel = module?.displayName ?: s.moduleId

                // Node Layar
                Row(
                    modifier = Modifier
                        .background(
                            if (isSelected) WeMadeColors.WireframeBorder else WeMadeColors.WireframeSurface,
                            RoundedCornerShape(6.dp)
                        )
                        .border(
                            1.5.dp,
                            WeMadeColors.WireframeBorder,
                            RoundedCornerShape(6.dp)
                        )
                        .pointerHoverIcon(PointerIcon.Hand)
                        .clickable { onSelectScreen(index) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Nomor urut alur
                    Text(
                        text = "${index + 1}",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isSelected) WeMadeColors.WireframeBorder else Color.White,
                        modifier = Modifier
                            .background(if (isSelected) Color.White else WeMadeColors.WireframeBorder, CircleShape)
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                    Column {
                        Text(
                            text = s.title,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.White else WeMadeColors.WireframeOnSurface,
                            maxLines = 1,
                            softWrap = false
                        )
                        Text(
                            text = moduleLabel,
                            fontSize = 9.sp,
                            color = if (isSelected) WeMadeColors.WireframeGrid else WeMadeColors.WireframeOnSurfaceMuted,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                // Panah Konektor ke Layar Berikutnya (Vector Arrow)
                if (index < screens.lastIndex) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(horizontal = 3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(10.dp)
                                .height(1.5.dp)
                                .background(WeMadeColors.WireframeBorder)
                        )
                        IconArrowForward(
                            modifier = Modifier.size(10.dp),
                            color = WeMadeColors.WireframeBorder
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bingkai jendela Artboard Moqups:
 * Menampilkan pratinjau wireframe di atas kanvas bertitik dengan window controls khas Moqups.
 */
@Composable
fun MoqupsArtboardFrame(
    title: String,
    widget: String,
    moduleName: String,
    modifier: Modifier = Modifier,
    rationale: String? = null,
    source: ProposalSource? = null,
    nextScreenTitle: String? = null,
    onNavigateNext: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val frameShape = RoundedCornerShape(10.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(WeMadeColors.WireframeSurface, frameShape)
            .border(1.5.dp, WeMadeColors.WireframeBorder, frameShape)
    ) {
        // Bilah Jendela Moqups (Window Header)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(WeMadeColors.WireframeBadgeBg, RoundedCornerShape(topStart = 10.dp, topEnd = 10.dp))
                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Kontrol Jendela (3 Dots)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(modifier = Modifier.size(9.dp).border(1.2.dp, WeMadeColors.WireframeBorder, CircleShape))
                Box(modifier = Modifier.size(9.dp).border(1.2.dp, WeMadeColors.WireframeBorder, CircleShape))
                Box(modifier = Modifier.size(9.dp).border(1.2.dp, WeMadeColors.WireframeBorder, CircleShape))

                Spacer(modifier = Modifier.width(8.dp))

                // Judul Artboard & Modul
                Text(
                    text = "wireframe / $moduleName / $title",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.WireframeOnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Stensil Lencana Tipe Widget (Hitam Putih)
            val kind = WidgetKind.fromCode(widget)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                Text(
                    text = "[ ${kind?.displayName?.uppercase() ?: widget.uppercase()} ]",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace,
                    color = WeMadeColors.WireframeOnSurface,
                    modifier = Modifier
                        .background(Color.White, RoundedCornerShape(4.dp))
                        .border(1.2.dp, WeMadeColors.WireframeBorder, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        HorizontalDivider(color = WeMadeColors.WireframeBorder, thickness = 1.5.dp)

        // Konten Utama Artboard
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(ClaySpacing.Md)
        ) {
            content()
        }

        // Bilah Kaki Alur (Footer: Sambungan ke langkah selanjutnya)
        if (nextScreenTitle != null && onNavigateNext != null) {
            HorizontalDivider(color = WeMadeColors.WireframeBorderSubtle)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(WeMadeColors.WireframeBadgeBg, RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
                    .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Koneksi Alur: Layar ini terhubung ke langkah berikutnya.",
                    fontSize = 11.sp,
                    color = WeMadeColors.WireframeOnSurfaceMuted
                )
                WireframeButton(
                    text = "Lanjut Alur: $nextScreenTitle",
                    onClick = onNavigateNext,
                    filled = true,
                    trailingIcon = {
                        IconArrowForward(
                            modifier = Modifier.size(11.dp),
                            color = Color.White
                        )
                    }
                )
            }
        }
    }
}

/**
 * Renderer utama kanvas wireframe ala Moqups:
 * Mengintegrasikan kanvas dot grid, navigasi rantai alur (User Flow Stepper),
 * dan bingkai artboard interaktif yang bisa diklik untuk berpindah layar serta input data.
 */
@Composable
fun MoqupsWireframeRenderer(
    draft: DiscoveryDraftUi,
    screens: List<DiscoveryScreenUi>,
    session: PrototypeSession,
    modifier: Modifier = Modifier,
    initialModuleId: String? = null,
    startIndex: Int = 0, // layar awal bila initialModuleId null (dipakai pemutar skenario)
    onAdvance: ((from: Int) -> Unit)? = null // null = "Lanjut Alur" hanya ganti indeks; non-null = pemanggil yang memutuskan (skenario)
) {
    var isAllScope by remember { mutableStateOf(initialModuleId == null) }
    // Daftar layar aktif sesuai filter alur
    val activeScreens = remember(screens, isAllScope, initialModuleId) {
        if (!isAllScope && initialModuleId != null) {
            val filtered = screens.filter { it.moduleId == initialModuleId }
            if (filtered.isNotEmpty()) filtered else screens
        } else {
            screens
        }
    }
    var currentIndex by remember(activeScreens, initialModuleId) {
        val initialIdx = if (initialModuleId != null) {
            val idx = activeScreens.indexOfFirst { it.moduleId == initialModuleId }
            if (idx >= 0) idx else 0
        } else startIndex
        mutableStateOf(initialIdx)
    }

    val safeIndex = currentIndex.coerceIn(0, (activeScreens.size - 1).coerceAtLeast(0))
    val currentScreen = activeScreens.getOrNull(safeIndex) ?: return
    val currentModule = draft.modules.firstOrNull { it.id == currentScreen.moduleId }
    val nextScreen = activeScreens.getOrNull(safeIndex + 1)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WeMadeColors.WireframeCanvas, RoundedCornerShape(12.dp))
            .border(1.5.dp, WeMadeColors.WireframeBorderSubtle, RoundedCornerShape(12.dp))
            .moqupsDotGrid()
            .padding(ClaySpacing.Md)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Rantai Alur Antar Layar (Moqups Flow Navigator)
            MoqupsFlowNavigator(
                screens = activeScreens,
                selectedIndex = safeIndex,
                onSelectScreen = { currentIndex = it },
                draftModules = draft.modules,
                isAllScope = isAllScope,
                onToggleScope = if (initialModuleId != null && screens.size > 1) {
                    { isAllScope = !isAllScope }
                } else null,
                totalDraftScreens = screens.size
            )

            // Bingkai Artboard Layar Aktif
            MoqupsArtboardFrame(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .fillMaxHeight(),
                title = currentScreen.title,
                widget = currentScreen.widget,
                moduleName = currentModule?.displayName ?: currentScreen.moduleId,
                rationale = currentScreen.rationale,
                source = currentScreen.source,
                nextScreenTitle = nextScreen?.title,
                onNavigateNext = if (nextScreen != null) {
                    { if (onAdvance != null) onAdvance(safeIndex) else currentIndex = safeIndex + 1 }
                } else null
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    val block = session.block(currentScreen.screenId)
                    if (block != null) {
                        InteractiveBlock(block, wireframe = true)
                    } else {
                        WidgetBody(widget = currentScreen.widget, rows = currentScreen.sampleRows)
                    }
                }
            }
        }
    }
}

