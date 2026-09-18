package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.domain.deal.DealStage
import com.eventverse.app.domain.orgchart.OrgNode
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoicePrefillCoordinator
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.rbac.RbacAccessPolicyRepository
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Daftar SEMUA deal milik tenant — disesuaikan 100% dengan mockup Sales Pipeline WeMade ERP.
 *
 * Mengusung bahasa visual Claymorphism + Neo-Brutalism:
 * 1. KPI Pipeline cards dengan indikator dot warna, teks judul eksplisit, dan nilai nominal hitam tebal.
 * 2. Search bar kapsul membulat sejajar dengan filter chip tahapan.
 * 3. Grid kartu deal 3-kolom adaptif dengan pill brand biru, pill status oranye/amber,
 *    indikator stepper 3 tahap (Qualify → PO → Invoice) dengan panah terhubung,
 *    avatar Sales PIC + inisial JM, dan tombol aksi "Upload PO" serta "Create Invoice".
 */
@Composable
fun DealsPane(
    tenantSlug: String,
    modifier: Modifier = Modifier
) {
    val dataSource = remember { DealApiClient() }
    val navigator = LocalAppNavigator.current
    val employees by RbacAccessPolicyRepository.shared.employees.collectAsState()

    var deals by remember { mutableStateOf<List<Deal>>(emptyList()) }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var openDealId by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var stageFilter by remember { mutableStateOf<DealStage?>(null) }

    LaunchedEffect(tenantSlug, refreshKey) {
        isLoading = true
        error = null
        dataSource.getDeals(tenantSlug)
            .onSuccess {
                deals = it
                dataSource.getContacts(tenantSlug).onSuccess { contacts = it }
                isLoading = false
            }
            .onFailure {
                error = it.message
                isLoading = false
            }
    }

    val contactsById = remember(contacts) { contacts.associateBy { it.id.value } }

    val filteredDeals = remember(deals, contactsById, searchQuery, stageFilter) {
        deals.filter { deal ->
            val brand = contactsById[deal.contactId.value]?.brandName?.value.orEmpty()
            val matchesQuery = searchQuery.isBlank() ||
                deal.title.value.contains(searchQuery, ignoreCase = true) ||
                brand.contains(searchQuery, ignoreCase = true)
            val matchesStage = stageFilter == null || deal.stage == stageFilter
            matchesQuery && matchesStage
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank() && stageFilter == null) {
                    "Sales Pipeline (${deals.size} Deals)"
                } else {
                    "Sales Pipeline: ${filteredDeals.size} dari ${deals.size}"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            ClayButton(
                text = "Muat Ulang",
                onClick = { refreshKey++ },
                style = ClayButtonStyle.Ghost,
                fontSize = 11.sp
            )
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        when {
            isLoading -> Text(
                text = "Memuat data deal...",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            error != null -> Text(
                text = error ?: "Terjadi kesalahan.",
                fontSize = 12.sp,
                color = WeMadeColors.Error
            )
            deals.isEmpty() -> Text(
                text = "Belum ada deal. Kualifikasi satu lead di papan Kanban untuk membuat deal pertama.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            else -> DealsContent(
                deals = filteredDeals,
                allDeals = deals,
                contactsById = contactsById,
                employees = employees,
                searchQuery = searchQuery,
                onSearchChange = { searchQuery = it },
                stageFilter = stageFilter,
                onStageFilterChange = { stageFilter = it },
                onOpenDeal = { openDealId = it },
                onCreateInvoice = { deal, contact ->
                    val kind = if (deal.stage == DealStage.IN_PRODUCTION || deal.stage == DealStage.WON) {
                        com.eventverse.app.domain.invoicing.InvoiceKind.SETTLEMENT
                    } else {
                        com.eventverse.app.domain.invoicing.InvoiceKind.DOWN_PAYMENT
                    }
                    InvoicePrefillCoordinator.setPending(
                        InvoicePrefillData.fromDeal(deal, contact, kind = kind)
                    )
                    navigator(AppNavScreen.INVOICING)
                }
            )
        }
    }

    openDealId?.let { dealId ->
        DealDetailDialog(
            tenantSlug = tenantSlug,
            dealId = dealId,
            onDismiss = { openDealId = null }
        )
    }
}

/** KPI pipeline + toolbar pencarian/filter sejajar + grid kartu deal adaptif. */
@Composable
private fun DealsContent(
    deals: List<Deal>,
    allDeals: List<Deal>,
    contactsById: Map<String, Contact>,
    employees: List<OrgNode>,
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    stageFilter: DealStage?,
    onStageFilterChange: (DealStage?) -> Unit,
    onOpenDeal: (String) -> Unit,
    onCreateInvoice: (Deal, Contact?) -> Unit
) {
    // ── 1. KPI Pipeline Summary (4 Cards) ──────────────────────────────────
    val activeDealsCount = remember(allDeals) {
        allDeals.count { it.stage != DealStage.WON && it.stage != DealStage.LOST }
    }
    val samplingCount = remember(allDeals) {
        allDeals.count { it.stage == DealStage.PO_RECEIVED }
    }
    val winRate = remember(allDeals) { winRateOf(allDeals) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        PipelineKpiCard(
            label = "Total Pipeline Value",
            value = formatIdr(totalPipelineOf(allDeals)),
            indicatorColor = WeMadeColors.Primary,
            modifier = Modifier.weight(1f)
        )
        PipelineKpiCard(
            label = "Active Deals",
            value = "$activeDealsCount Transaksi",
            indicatorColor = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
        PipelineKpiCard(
            label = "Siklus Sampling",
            value = "$samplingCount Berjalan",
            indicatorColor = Color(0xFFF59E0B),
            modifier = Modifier.weight(1f)
        )
        PipelineKpiCard(
            label = "Win Rate",
            value = winRate?.let { "$it%" } ?: "0%",
            indicatorColor = WeMadeColors.Success,
            modifier = Modifier.weight(1f)
        )
    }

    Spacer(Modifier.height(ClaySpacing.Lg))

    // ── 2. Toolbar Pencarian Kapsul + Filter Chips (Sejajar dalam satu baris) ──
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        // Capsule search bar
        CapsuleSearchField(
            query = searchQuery,
            onQueryChange = onSearchChange,
            modifier = Modifier.width(300.dp)
        )

        // Filter chips horizontal scroll
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            StageFilterChip(
                label = "Semua",
                selected = stageFilter == null,
                onClick = { onStageFilterChange(null) }
            )
            DealStage.entries.forEach { stage ->
                StageFilterChip(
                    label = stage.mockupLabel(),
                    selected = stageFilter == stage,
                    onClick = { onStageFilterChange(if (stageFilter == stage) null else stage) },
                    onClear = if (stageFilter == stage) ({ onStageFilterChange(null) }) else null
                )
            }
        }
    }

    Spacer(Modifier.height(ClaySpacing.Lg))

    // ── 3. Grid Kartu Deal (3 Kolom Adaptif) ─────────────────────────────────
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 340.dp),
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        items(deals, key = { it.id.value }) { deal ->
            val contact = contactsById[deal.contactId.value]
            val emp = deal.ownerEmployeeId?.let { ownerId ->
                employees.firstOrNull { it.id == ownerId }
            }
            val picInitials = initialsOf(emp?.name ?: "Jamaludin")

            DealGridCard(
                deal = deal,
                contact = contact,
                picInitials = picInitials,
                onClick = { onOpenDeal(deal.id.value) },
                onUploadPo = { onOpenDeal(deal.id.value) },
                onCreateInvoice = { onCreateInvoice(deal, contact) }
            )
        }
    }
}

private fun totalPipelineOf(deals: List<Deal>): Long =
    deals.filter { it.stage != DealStage.LOST }.sumOf { it.estimatedValue?.amount ?: 0L }

private fun winRateOf(deals: List<Deal>): Int? {
    val won = deals.count { it.stage == DealStage.WON }
    val lost = deals.count { it.stage == DealStage.LOST }
    return if (won + lost == 0) null else won * 100 / (won + lost)
}

/** Satu kartu metrik ringkasan pipeline di baris KPI dengan dot indikator warna. */
@Composable
private fun PipelineKpiCard(
    label: String,
    value: String,
    indicatorColor: Color,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(indicatorColor)
            )
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(ClaySpacing.Sm))
        Text(
            text = value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Kolom pencarian kapsul bulat penuh dengan outline tebal dan tombol clear. */
@Composable
private fun CapsuleSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(42.dp)
            .claySurface(
                shape = CircleShape,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Thick,
                offset = ClayOffset.Small
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IconSearch(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (query.isEmpty()) {
                Text(
                    text = "Search",
                    fontSize = 13.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurface
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (query.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center
            ) {
                IconClose(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
            }
        }
    }
}

/** Chip filter tahapan berbentuk kapsul bulat dengan opsi ikon silang saat aktif. */
@Composable
private fun StageFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .claySurface(
                shape = CircleShape,
                background = if (selected) WeMadeColors.Primary else WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                shadowColor = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium,
                offset = if (selected) ClayOffset.Pressed else ClayOffset.Small
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else WeMadeColors.OnSurface,
            maxLines = 1
        )
        if (selected && onClear != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center
            ) {
                IconClose(modifier = Modifier.size(10.dp), color = Color.White)
            }
        }
    }
}

/**
 * Kartu deal pada grid adaptif yang merefleksikan desain persis mockup:
 * - Brand pill biru & Stage pill oranye/amber
 * - Judul kuantitas produk & Nilai transaksi hitam bold
 * - Stepper 3 tahap: Qualify -> PO -> Invoice dengan panah konektor kanvas
 * - Sales PIC avatar & inisial badge
 * - Tombol aksi Upload PO (Oranye) & Create Invoice (Hijau)
 */
@Composable
private fun DealGridCard(
    deal: Deal,
    contact: Contact?,
    picInitials: String,
    onClick: () -> Unit,
    onUploadPo: () -> Unit,
    onCreateInvoice: () -> Unit
) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        // ── Baris Atas: Brand Pill (Biru) + Status Pill (Oranye/Amber) ──────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val brand = contact?.brandName?.value?.takeIf { it.isNotBlank() }
                ?: contact?.displayName?.takeIf { it.isNotBlank() }
                ?: "Erigo Studio"

            Box(
                modifier = Modifier
                    .claySurface(
                        shape = CircleShape,
                        background = WeMadeColors.Primary,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Medium,
                        offset = ClayOffset.Flat
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = brand,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            val stageColor = deal.stage.tint()

            Box(
                modifier = Modifier
                    .claySurface(
                        shape = CircleShape,
                        background = stageColor,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Medium,
                        offset = ClayOffset.Flat
                    )
                    .padding(horizontal = 14.dp, vertical = 5.dp)
            ) {
                Text(
                    text = deal.stage.mockupLabel(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))

        // ── Judul Transaksi / Kuantitas Baju ────────────────────────────────
        Text(
            text = deal.title.value,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = WeMadeColors.OnSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        // ── Nilai Transaksi (Besar Hitam Bold) ──────────────────────────────
        Spacer(Modifier.height(ClaySpacing.Xs))
        Text(
            text = deal.estimatedValue?.let { formatIdr(it.amount) } ?: "Rp 0",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = WeMadeColors.OnSurface
        )

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Stepper 3 Tahap: Qualify ──→ PO ── Invoice ─────────────────────
        MiniPipelineIndicator(stage = deal.stage)

        Spacer(Modifier.height(ClaySpacing.Lg))

        // ── Baris Bawah: Sales PIC + Tombol Upload PO & Create Invoice ──────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sales PIC Info
            Column {
                Text(
                    text = "Sales PIC",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.OnSurfaceMuted
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Profile avatar circle
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.SurfaceMuted)
                            .border(ClayBorder.Hairline, WeMadeColors.Outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        IconUser(modifier = Modifier.size(15.dp), color = WeMadeColors.OnSurfaceMuted)
                    }
                    // Initials circle badge (e.g. JM)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF59E0B))
                            .border(ClayBorder.Hairline, WeMadeColors.Outline, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = picInitials,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                    }
                }
            }

            // Action buttons: PO Opsional & Invoice Kontekstual (DP / Pelunasan)
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayCardButton(
                    text = "+ PO (Opsional)",
                    containerColor = Color(0xFFF59E0B),
                    onClick = onUploadPo
                )
                val invoiceLabel = if (deal.stage == DealStage.IN_PRODUCTION || deal.stage == DealStage.WON) {
                    "Invoice Pelunasan"
                } else {
                    "Invoice DP (50%)"
                }
                ClayCardButton(
                    text = invoiceLabel,
                    containerColor = WeMadeColors.Success,
                    onClick = onCreateInvoice
                )
            }
        }
    }
}

/** Tombol Neo-Brutalist padat dengan outline tebal dan hard shadow. */
@Composable
private fun ClayCardButton(
    text: String,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    Box(
        modifier = modifier
            .claySurface(
                shape = ClayShapes.Button,
                background = containerColor,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium,
                offset = ClayOffset.Small,
                pressed = isPressed
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
    }
}

internal fun DealStage.mockupLabel(): String = when (this) {
    DealStage.OPEN -> "Qualify"
    DealStage.PO_RECEIVED -> "Sampling"
    DealStage.IN_PRODUCTION -> "In Production"
    DealStage.WON -> "Won"
    DealStage.LOST -> "Lost"
}

internal fun DealStage.tint(): Color = when (this) {
    DealStage.OPEN -> WeMadeColors.Info
    DealStage.PO_RECEIVED -> Color(0xFFF59E0B)
    DealStage.IN_PRODUCTION -> WeMadeColors.Accent
    DealStage.WON -> WeMadeColors.Success
    DealStage.LOST -> WeMadeColors.Error
}

internal fun formatIdr(amount: Long): String =
    "Rp " + amount.toString().reversed().chunked(3).joinToString(".").reversed()

private fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "JM" }
