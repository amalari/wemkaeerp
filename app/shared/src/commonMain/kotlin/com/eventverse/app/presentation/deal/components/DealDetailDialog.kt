package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SamplingStatus
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.deal.DealDetailTab
import com.eventverse.app.presentation.deal.DealUiEvent
import com.eventverse.app.presentation.deal.DealUiState
import com.eventverse.app.presentation.deal.DealViewModel
import com.eventverse.app.presentation.invoicing.InvoicePrefillCoordinator
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Popup modal BESAR detail Deal (~92vw / maks 1150dp, tinggi 88vh) bergaya clay:
 * dual-tab Siklus Sampling vs Produksi Massal & PO dengan gerbang Golden Sample Lock —
 * Tab Produksi terkunci sampai seluruh desain sampling berstatus ACC.
 *
 * Meng-host [DealViewModel]-nya sendiri supaya bisa dibuka dari pane mana pun.
 */
@Composable
fun DealDetailDialog(
    tenantSlug: String,
    dealId: String? = null,
    sourceLeadId: String? = null,
    onDismiss: () -> Unit
) {
    val viewModel = remember(tenantSlug, dealId, sourceLeadId) {
        DealViewModel(tenantSlug = tenantSlug, dealId = dealId, sourceLeadId = sourceLeadId)
    }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) { viewModel.onEvent(DealUiEvent.Load) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 1150.dp)
                .fillMaxHeight(0.88f),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            when {
                state.isLoading -> Text(
                    text = "Memuat deal...",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                state.deal == null -> Column {
                    Text(
                        text = "Deal belum tersedia",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurface
                    )
                    Spacer(Modifier.height(ClaySpacing.Sm))
                    Text(
                        text = state.statusMessage ?: "Deal tidak ditemukan.",
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }

                else -> DealDetailContent(
                    state = state,
                    onEvent = viewModel::onEvent,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

@Composable
private fun DealDetailContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit,
    onDismiss: () -> Unit
) {
    val deal = state.deal ?: return

    Column(modifier = Modifier.fillMaxSize()) {

        // ── Header: judul deal + badge stage + tombol tutup ─────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = deal.title.value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(ClaySpacing.Md))
                ClayBadge(text = deal.stage.displayName, tint = deal.stage.tint())
                state.contactName?.let { name ->
                    Spacer(Modifier.width(ClaySpacing.Sm))
                    ClayTag(text = name, tint = WeMadeColors.OnSurfaceMuted)
                }
            }
            ClayIconButton(onClick = onDismiss) {
                IconClose(Modifier.size(16.dp))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Tab switcher: Siklus Sampling vs Produksi Massal (gerbang gembok) ─
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            DealTabButton(
                tab = DealDetailTab.SAMPLING,
                state = state,
                icon = { IconClipboard(Modifier.size(14.dp)) },
                onEvent = onEvent
            )
            DealTabButton(
                tab = DealDetailTab.MASS_PRODUCTION,
                state = state,
                icon = {
                    if (state.productionUnlocked) {
                        IconPackage(Modifier.size(14.dp))
                    } else {
                        IconLock(Modifier.size(14.dp))
                    }
                },
                onEvent = onEvent
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Konten tab (scrollable) ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            when (state.activeTab) {
                DealDetailTab.SAMPLING -> SamplingTabContent(state = state, onEvent = onEvent)
                DealDetailTab.MASS_PRODUCTION -> MassProductionTabContent(state = state, onEvent = onEvent)
            }
        }

        state.statusMessage?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Success)
        }
        state.error?.let { message ->
            Spacer(Modifier.height(ClaySpacing.Sm))
            Text(text = message, fontSize = 11.sp, color = WeMadeColors.Error)
        }
    }
}

@Composable
private fun DealTabButton(
    tab: DealDetailTab,
    state: DealUiState,
    icon: @Composable () -> Unit,
    onEvent: (DealUiEvent) -> Unit
) {
    val isSelected = state.activeTab == tab
    val locked = tab == DealDetailTab.MASS_PRODUCTION && !state.productionUnlocked
    val prefix = if (tab == DealDetailTab.SAMPLING) "Tab 1" else "Tab 2"
    val label = if (tab == DealDetailTab.SAMPLING) {
        "$prefix: ${tab.label} (${state.samplingOrders.size} Desain)"
    } else {
        "$prefix: ${tab.label}"
    }
    val tint = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted

    Column(
        modifier = Modifier
            .clickable { onEvent(DealUiEvent.SelectDealTab(tab)) }
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            icon()
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                maxLines = 1
            )
            if (locked) {
                ClayBadge(
                    text = "Menunggu ${state.activeDesigns.size} Desain ACC",
                    tint = WeMadeColors.OnSurfaceMuted,
                    fontSize = 10.sp,
                    leading = { IconLock(Modifier.size(11.dp)) }
                )
            }
        }
        Spacer(Modifier.height(ClaySpacing.Sm))
        // Garis bawah tab: satu-satunya penanda tab aktif (mockup), non-aktif dibiarkan kosong
        // sehingga lebarnya tidak melompat saat berpindah tab.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(if (isSelected) WeMadeColors.Primary else Color.Transparent, ClayShapes.Pill)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TAB 1: Siklus Sampling — grid accordion multi-desain (mockup-aligned)
// ─────────────────────────────────────────────────────────────────────────────

/** Kode desain tampilan (`DSG-01`, `DSG-02`, …) diturunkan dari urutan desain dalam deal. */
private fun designCodeOf(orders: List<SamplingOrder>, order: SamplingOrder): String {
    val index = orders.indexOfFirst { it.id == order.id }
    val number = if (index >= 0) index + 1 else orders.size
    return "DSG-" + number.toString().padStart(2, '0')
}

@Composable
private fun SamplingTabContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit
) {
    var isAddingDesign by remember { mutableStateOf(false) }
    var newDesignName by remember { mutableStateOf("" ) }
    var newDesignQty by remember { mutableStateOf(2) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = when {
                state.samplingOrders.isEmpty() ->
                    "Belum ada desain sampling. Tambahkan desain pertama di bawah."
                state.productionUnlocked ->
                    "Semua desain sudah di-ACC — Tab Produksi Massal terbuka."
                else ->
                    "${state.approvedDesigns.size} dari ${state.samplingOrders.size} Desain sudah di-ACC"
            },
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(Modifier.height(ClaySpacing.Lg))

        // Grid dua kolom: kartu desain berpasangan, sisa ganjil diberi spacer berbobot sama
        // supaya lebar kartu tidak berubah-ubah antar baris.
        state.samplingOrders.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
            ) {
                pair.forEach { order ->
                    SamplingDesignCard(
                        order = order,
                        designCode = designCodeOf(state.samplingOrders, order),
                        onEvent = onEvent,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(ClaySpacing.Lg))
        }

        // Tombol tambah di tengah, sesuai mockup; form baru muncul setelah diklik.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            ClayButton(
                text = if (isAddingDesign) "Batal" else "Tambah Desain Baru",
                onClick = { isAddingDesign = !isAddingDesign },
                style = ClayButtonStyle.Secondary,
                leading = {
                    if (isAddingDesign) {
                        IconClose(Modifier.size(13.dp), color = WeMadeColors.OnSurface)
                    } else {
                        IconPlus(Modifier.size(13.dp), color = WeMadeColors.Primary)
                    }
                }
            )
        }

        if (isAddingDesign) {
            Spacer(Modifier.height(ClaySpacing.Lg))
            ClayCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = WeMadeColors.SurfaceMuted,
                borderWidth = ClayBorder.Hairline,
                contentPadding = PaddingValues(ClaySpacing.Lg)
            ) {
                Text(
                    text = "Desain / varian warna baru",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(ClaySpacing.Sm))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayTextField(
                        value = newDesignName,
                        onValueChange = { newDesignName = it },
                        label = "Nama desain",
                        placeholder = "mis. Polo Navy Classic",
                        modifier = Modifier.weight(2f)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Jumlah sampel",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
                            listOf(1, 2, 3).forEach { qty ->
                                ClayActionSurface(
                                    onClick = { newDesignQty = qty },
                                    selected = newDesignQty == qty,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        text = "$qty",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (newDesignQty == qty) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                                    )
                                }
                            }
                        }
                    }
                    ClayButton(
                        text = "Tambah",
                        onClick = {
                            onEvent(
                                DealUiEvent.SaveSamplingOrder(
                                    samplingOrderId = null,
                                    styleName = newDesignName,
                                    sampleQuantity = newDesignQty,
                                    courierTracking = null,
                                    samplingFeeIdr = 0L,
                                    notes = ""
                                )
                            )
                            newDesignName = ""
                            newDesignQty = 2
                            isAddingDesign = false
                        },
                        enabled = newDesignName.isNotBlank(),
                        style = ClayButtonStyle.Accent,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SamplingDesignCard(
    order: SamplingOrder,
    designCode: String,
    onEvent: (DealUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember(order.id) { mutableStateOf(order.isActiveDesign) }
    var courierInput by remember(order.id) { mutableStateOf(order.courierTracking ?: "") }
    var revisionNotes by remember(order.id) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    // Server sudah mengganti key object storage menjadi URL siap tampil saat data dibaca;
    // entri yang masih berupa key mentah sengaja tidak dicoba dimuat.
    val mockupReference = order.knitSpec.mockupImageUrls.lastOrNull()
        ?.takeIf { it.startsWith("http") || it.startsWith("data:") }
    val mockupBitmap = rememberMockupBitmap(mockupReference)
    val tracking = order.courierTracking?.takeIf { it.isNotBlank() }

    ClayCard(
        modifier = modifier,
        outlineColor = when {
            order.isAccApproved -> WeMadeColors.Success
            order.status == SamplingStatus.REVISION -> WeMadeColors.Accent
            else -> WeMadeColors.Outline
        },
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // ── Header accordion: kode desain, nama, badge status, chevron ──────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f, fill = false),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .background(statusTint(order.status), CircleShape)
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                Text(
                    text = "$designCode: ${order.styleName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SamplingStatusBadge(status = order.status, revisionCount = order.revisionCount)
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayActionSurface(
                    onClick = { expanded = !expanded },
                    contentPadding = PaddingValues(ClaySpacing.Sm)
                ) {
                    if (expanded) {
                        IconChevronUp(Modifier.size(14.dp))
                    } else {
                        IconChevronDown(Modifier.size(14.dp))
                    }
                }
            }
        }

        if (!expanded) {
            return@ClayCard
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)) {
            // ── Slot foto mockup desain ─────────────────────────────────────
            DesignMockupSlot(
                bitmap = mockupBitmap,
                onUpload = { onEvent(DealUiEvent.UploadSamplingMockup(order.id.value)) }
            )

            // ── Kolom kanan: revisi, feedback, kurir, aksi ───────────────────
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Revision",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(ClaySpacing.Xs))

                RevisionPill(
                    text = "Rev 0 (Awal)",
                    active = order.revisionCount == 0 && !order.isAccApproved
                )
                for (revision in 1..order.revisionCount) {
                    Spacer(Modifier.height(ClaySpacing.Xs))
                    val isLatest = revision == order.revisionCount
                    RevisionPill(
                        text = "Rev $revision (" + when {
                            order.isAccApproved && isLatest -> "Final"
                            !order.isAccApproved && isLatest -> "Aktif"
                            else -> "Selesai"
                        } + ")",
                        active = isLatest && !order.isAccApproved
                    )
                }

                if (order.isAccApproved) {
                    Spacer(Modifier.height(ClaySpacing.Md))
                    Text(
                        text = "Approved",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    Text(
                        text = order.accNotes.ifBlank { "Spesifikasi disetujui buyer." },
                        fontSize = 12.sp,
                        color = WeMadeColors.OnSurface
                    )
                }

                if (order.status == SamplingStatus.REVISION && order.accNotes.isNotBlank()) {
                    Spacer(Modifier.height(ClaySpacing.Md))
                    RevisionFeedbackCallout(notes = order.accNotes)
                }

                Spacer(Modifier.height(ClaySpacing.Md))

                // ── Courier tracking + salin ─────────────────────────────────
                Text(
                    text = "Courier Tracking",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(ClaySpacing.Xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    Text(
                        text = tracking ?: "Belum ada resi",
                        fontSize = 12.sp,
                        fontWeight = if (tracking != null) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (tracking != null) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tracking != null) {
                        ClayIconButton(
                            onClick = { clipboard.setText(AnnotatedString(tracking)) }
                        ) {
                            IconCopy(Modifier.size(13.dp), color = WeMadeColors.OnSurfaceMuted)
                        }
                    }
                }

                Spacer(Modifier.height(ClaySpacing.Sm))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayTextField(
                        value = courierInput,
                        onValueChange = { courierInput = it },
                        placeholder = "Isi / ubah resi kurir",
                        leadingIcon = { IconTruck(Modifier.size(13.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "Simpan Resi",
                        onClick = {
                            onEvent(
                                DealUiEvent.SaveSamplingOrder(
                                    samplingOrderId = order.id.value,
                                    styleName = order.styleName,
                                    sampleQuantity = order.sampleQuantity,
                                    courierTracking = courierInput,
                                    samplingFeeIdr = order.samplingFeeIdr,
                                    notes = order.notes
                                )
                            )
                        },
                        style = ClayButtonStyle.Secondary,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(ClaySpacing.Md))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayTextField(
                        value = revisionNotes,
                        onValueChange = { revisionNotes = it },
                        placeholder = "Catatan revisi buyer…",
                        modifier = Modifier.weight(1f)
                    )
                    ClayButton(
                        text = "Ajukan Revisi",
                        onClick = {
                            onEvent(
                                DealUiEvent.ToggleSampleAcc(
                                    samplingId = order.id.value,
                                    isApproved = false,
                                    notes = revisionNotes
                                )
                            )
                            revisionNotes = ""
                        },
                        enabled = revisionNotes.isNotBlank(),
                        style = ClayButtonStyle.Ghost,
                        fontSize = 12.sp
                    )
                }

                if (order.status != SamplingStatus.CANCELLED) {
                    Spacer(Modifier.height(ClaySpacing.Md))
                    if (order.isAccApproved) {
                        ClayButton(
                            text = "Terbitkan Invoice Sampling",
                            onClick = {
                                InvoicePrefillCoordinator.setPending(
                                    InvoicePrefillData(
                                        kind = InvoiceKind.SAMPLE,
                                        clientName = order.clientName,
                                        sourceKind = InvoiceSourceKind.DEAL,
                                        sourceRef = order.id.value,
                                        lineDescription = "Biaya sampling ${order.styleName} (${order.spkNumber.value})",
                                        lineQty = 1.0,
                                        linePrice = order.samplingFeeIdr
                                    )
                                )
                            },
                            style = ClayButtonStyle.Accent,
                            fontSize = 12.sp
                        )
                    } else {
                        ClayButton(
                            text = "Tandai ACC Desain ${designCode.removePrefix("DSG-").toIntOrNull() ?: ""}",
                            onClick = {
                                onEvent(
                                    DealUiEvent.ToggleSampleAcc(
                                        samplingId = order.id.value,
                                        isApproved = true,
                                        notes = ""
                                    )
                                )
                            },
                            style = ClayButtonStyle.Success,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }
    }
}

/** Slot foto mockup: placeholder → foto terunggah, dan selalu bisa diklik untuk mengganti. */
@Composable
private fun DesignMockupSlot(
    bitmap: ImageBitmap?,
    onUpload: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(150.dp)
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Medium
            )
            .clickable(onClick = onUpload),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Foto mockup desain",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(ClaySpacing.Sm)
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconImage(Modifier.size(26.dp))
                Spacer(Modifier.height(ClaySpacing.Xs))
                Text(
                    text = "Upload Foto",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.Primary
                )
            }
        }
    }
}

/** Pil versi sampel: aktif = biru terisi, riwayat = outline netral. */
@Composable
private fun RevisionPill(text: String, active: Boolean) {
    val background = if (active) WeMadeColors.Primary.copy(alpha = 0.12f) else WeMadeColors.Surface
    val outline = if (active) WeMadeColors.Primary else WeMadeColors.Outline
    val label = if (active) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted

    Box(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = background,
                outline = outline,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(text = text, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = label)
    }
}

/** Kotak catatan evaluasi buyer — amber supaya terbaca sebagai sesuatu yang menuntut tindakan. */
@Composable
private fun RevisionFeedbackCallout(notes: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.Warning.copy(alpha = 0.12f),
                outline = WeMadeColors.Warning.copy(alpha = 0.55f),
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Md),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) {
        IconWarning(Modifier.size(15.dp), color = WeMadeColors.Warning)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Revisi Feedback",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Xxs))
            Text(text = notes, fontSize = 12.sp, color = WeMadeColors.OnSurface)
        }
    }
}

@Composable
private fun SamplingStatusBadge(status: SamplingStatus, revisionCount: Int) {
    val label = when (status) {
        SamplingStatus.DRAFT -> "Draft SPK"
        SamplingStatus.IN_PROGRESS -> "Sedang Jahit Sampel"
        SamplingStatus.REVISION -> "Perlu Revisi (Rev $revisionCount)"
        SamplingStatus.ACC_APPROVED -> "ACC Disetujui"
        SamplingStatus.CANCELLED -> "Dibatalkan"
    }
    ClayBadge(text = label, tint = statusTint(status), dot = true)
}

private fun statusTint(status: SamplingStatus): Color = when (status) {
    SamplingStatus.DRAFT -> WeMadeColors.OnSurfaceMuted
    SamplingStatus.IN_PROGRESS -> WeMadeColors.Warning
    SamplingStatus.REVISION -> WeMadeColors.Accent
    SamplingStatus.ACC_APPROVED -> WeMadeColors.Success
    SamplingStatus.CANCELLED -> WeMadeColors.Error
}

@Composable
private fun MassProductionTabContent(
    state: DealUiState,
    onEvent: (DealUiEvent) -> Unit
) {
    var poNumber by remember { mutableStateOf("") }
    var poDescription by remember { mutableStateOf("") }
    var poQuantity by remember { mutableStateOf("1") }
    var poUnitPrice by remember { mutableStateOf("") }
    val navigator = LocalAppNavigator.current

    val grandTotalIdr = state.purchaseOrders.sumOf { it.totalValueIdr }

    Column {

        // ── Banner verifikasi Golden Sample ──────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.Success.copy(alpha = 0.12f),
                    outline = WeMadeColors.Success.copy(alpha = 0.55f),
                    borderWidth = ClayBorder.Medium
                )
                .padding(ClaySpacing.Md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            IconCheckCircle(Modifier.size(16.dp))
            Text(
                text = "Mengacu pada Sampel ACC: " +
                    state.approvedDesigns.joinToString(" | ") { "#${it.spkNumber.value} (${it.styleName})" } +
                    " — pola & benang terkunci.",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Daftar PO terlampir ──────────────────────────────────────────────
        Text(
            text = "Dokumen PO Buyer (${state.purchaseOrders.size})",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        if (state.purchaseOrders.isEmpty()) {
            Text(
                text = "Belum ada PO. Tempelkan PO resmi buyer lewat upload berkas atau input manual di bawah.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        } else {
            state.purchaseOrders.forEach { po ->
                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f, fill = false),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = po.poNumber.value,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.width(ClaySpacing.Sm))
                            ClayBadge(
                                text = po.origin.displayName,
                                tint = if (po.origin == com.eventverse.app.domain.deal.PoOrigin.UPLOADED) {
                                    WeMadeColors.Info
                                } else {
                                    WeMadeColors.Success
                                }
                            )
                        }
                        Text(
                            text = formatIdr(po.totalValueIdr),
                            fontSize = 12.sp,
                            color = WeMadeColors.OnSurface
                        )
                    }
                    if (po.hasFile) {
                        Spacer(Modifier.height(ClaySpacing.Xs))
                        ClayActionSurface(onClick = { onEvent(DealUiEvent.OpenPoDownload(po.id.value)) }) {
                            Text(
                                text = "Unduh berkas PO (${po.fileName ?: "berkas"})",
                                fontSize = 11.sp,
                                color = WeMadeColors.Primary
                            )
                        }
                    }
                }
                Spacer(Modifier.height(ClaySpacing.Xs))
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Size breakdown massal (baris per item PO) ───────────────────────
        Text(
            text = "Rincian Pesanan Massal",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        val orderLines = state.purchaseOrders.flatMap { po ->
            po.lines.map { line -> Triple(line.description, line.quantity, line.unitPriceIdr) }
        }
        if (orderLines.isEmpty()) {
            Text(
                text = "Belum ada rincian ukuran — tambahkan lewat PO manual di bawah.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Chip,
                        background = WeMadeColors.Surface,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Medium
                    )
            ) {
                SizeBreakdownRow(
                    cells = listOf("Item", "Qty", "Harga Satuan", "Subtotal"),
                    header = true
                )
                orderLines.forEach { (desc, qty, price) ->
                    SizeBreakdownRow(
                        cells = listOf(desc, formatQty(qty), formatIdr(price), formatIdr((qty * price).toLong())),
                        header = false
                    )
                }
                SizeBreakdownRow(
                    cells = listOf("Total", "", "", formatIdr(grandTotalIdr)),
                    header = true
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Form PO manual + tombol upload ──────────────────────────────────
        Text(
            text = "Tempel PO Baru",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poNumber,
            onValueChange = { poNumber = it },
            label = "Nomor PO",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        ClayTextField(
            value = poDescription,
            onValueChange = { poDescription = it },
            label = "Deskripsi item (mis. Size M - Polo Navy)",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(ClaySpacing.Xs))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayTextField(
                value = poQuantity,
                onValueChange = { poQuantity = it },
                label = "Qty",
                modifier = Modifier.weight(1f)
            )
            ClayTextField(
                value = poUnitPrice,
                onValueChange = { input -> poUnitPrice = input.filter { it.isDigit() } },
                label = "Harga satuan (Rp)",
                modifier = Modifier.weight(2f)
            )
        }
        Spacer(Modifier.height(ClaySpacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Simpan PO Manual",
                onClick = {
                    onEvent(
                        DealUiEvent.AttachManualPo(
                            poNumber = poNumber,
                            description = poDescription,
                            quantity = poQuantity.toDoubleOrNull() ?: 1.0,
                            unitPriceIdr = poUnitPrice.toLongOrNull() ?: 0L
                        )
                    )
                    poNumber = ""
                    poDescription = ""
                    poQuantity = "1"
                    poUnitPrice = ""
                },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Upload Berkas PO",
                onClick = { onEvent(DealUiEvent.UploadPoFile) },
                style = ClayButtonStyle.Secondary,
                fontSize = 12.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Action footer: invoice DP + SPK massal ──────────────────────────
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayButton(
                text = "Terbitkan Invoice DP (50%)",
                onClick = {
                    val deal = state.deal ?: return@ClayButton
                    InvoicePrefillCoordinator.setPending(
                        InvoicePrefillData(
                            kind = InvoiceKind.DOWN_PAYMENT,
                            clientName = state.contactName ?: deal.title.value,
                            sourceKind = InvoiceSourceKind.DEAL,
                            sourceRef = deal.id.value,
                            lineDescription = "Uang muka 50% ${deal.title.value}",
                            lineQty = 1.0,
                            linePrice = grandTotalIdr / 2
                        )
                    )
                    navigator(AppNavScreen.INVOICING)
                },
                style = ClayButtonStyle.Success,
                fontSize = 12.sp
            )
            ClayButton(
                text = "Luncurkan SPK Massal",
                onClick = { onEvent(DealUiEvent.ChangeStage(DealStage.IN_PRODUCTION)) },
                style = ClayButtonStyle.Primary,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun SizeBreakdownRow(cells: List<String>, header: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (header) WeMadeColors.SurfaceMuted else Color.Transparent)
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        cells.forEachIndexed { index, cell ->
            Text(
                text = cell,
                fontSize = 12.sp,
                fontWeight = if (header) FontWeight.Bold else FontWeight.Medium,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = when (index) {
                    0 -> Modifier.weight(2f, fill = false)
                    else -> Modifier.weight(1f, fill = false)
                },
                textAlign = if (index == 0) TextAlign.Start else TextAlign.End
            )
        }
    }
}

private fun formatQty(quantity: Double): String =
    if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString()
