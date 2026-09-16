package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.infrastructure.api.DealApiClient
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.deal.openInBrowser
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlin.math.abs

/**
 * Master data pelanggan (Contact) — daftar semua kontak tenant beserta jejak lead
 * asalnya. Kontak di sini tidak bisa diedit langsung: ia dikelola lewat kualifikasi
 * lead (find-or-create) supaya tidak lahir duplikat di luar jalur kualifikasi.
 *
 * Tampilan clay penuh: toolbar pencarian (nama PIC, brand, WhatsApp, email), lalu
 * grid direktori adaptif dengan avatar inisial, ikon vektor, chip ringkasan transaksi
 * (dari data deal), dan tombol aksi cepat Chat WhatsApp / Lihat Deal.
 */
@Composable
fun ContactsPane(
    tenantSlug: String,
    modifier: Modifier = Modifier
) {
    val dataSource = remember { DealApiClient() }
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var deals by remember { mutableStateOf<List<Deal>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedBrands by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onlyActiveClients by remember { mutableStateOf(false) }
    var dealsDialogContactId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(tenantSlug, refreshKey) {
        isLoading = true
        error = null
        dataSource.getContacts(tenantSlug)
            .onSuccess {
                contacts = it
                // Deal hanya pelengkap (ringkasan transaksi & pintu Lihat Deal); tidak fatal.
                dataSource.getDeals(tenantSlug).onSuccess { deals = it }
                isLoading = false
            }
            .onFailure {
                error = it.message
                isLoading = false
            }
    }

    // contactId -> (jumlah deal, total estimasi nilai rupiah)
    val dealSummaries = remember(deals) {
        deals.groupBy { it.contactId.value }.mapValues { (_, contactDeals) ->
            contactDeals.size to contactDeals.sumOf { d -> d.estimatedValue?.amount ?: 0L }
        }
    }
    val uniqueBrands = remember(contacts) {
        contacts.mapNotNull { c -> c.brandName.value.takeIf { it.isNotBlank() } }.distinct().sorted()
    }
    val filteredContacts = remember(contacts, dealSummaries, searchQuery, selectedBrands, onlyActiveClients) {
        contacts.filter { c ->
            val matchesQuery = searchQuery.isBlank() ||
                c.displayName.contains(searchQuery, ignoreCase = true) ||
                c.brandName.value.contains(searchQuery, ignoreCase = true) ||
                c.phone?.localDisplay?.contains(searchQuery) == true ||
                c.email.contains(searchQuery, ignoreCase = true)
            val matchesBrand = selectedBrands.isEmpty() || c.brandName.value in selectedBrands
            val matchesStatus = !onlyActiveClients || (dealSummaries[c.id.value]?.first ?: 0) > 0
            matchesQuery && matchesBrand && matchesStatus
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (searchQuery.isBlank()) {
                    "Kontak (${contacts.size})"
                } else {
                    "Kontak: ${filteredContacts.size} dari ${contacts.size}"
                },
                fontSize = 16.sp,
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
                text = "Memuat kontak...",
                fontSize = 13.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            error != null -> Text(
                text = error ?: "Terjadi kesalahan.",
                fontSize = 12.sp,
                color = WeMadeColors.Error
            )
            contacts.isEmpty() -> Text(
                text = "Belum ada kontak. Kontak lahir otomatis saat sebuah lead di-qualify.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            else -> {
                ContactsToolbar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    brands = uniqueBrands,
                    selectedBrands = selectedBrands,
                    onToggleBrand = { brand ->
                        selectedBrands = if (brand in selectedBrands) {
                            selectedBrands - brand
                        } else {
                            selectedBrands + brand
                        }
                    },
                    onlyActiveClients = onlyActiveClients,
                    onToggleActiveClients = { onlyActiveClients = !onlyActiveClients }
                )

                Spacer(Modifier.height(ClaySpacing.Md))

                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    items(filteredContacts, key = { it.id.value }) { contact ->
                        ContactGridCard(
                            contact = contact,
                            dealSummary = dealSummaries[contact.id.value],
                            canOpenDeal = deals.any { it.contactId.value == contact.id.value },
                            onOpenDeal = { dealsDialogContactId = contact.id.value },
                            onChatWhatsApp = {
                                contact.phone?.let { phone -> openInBrowser(phone.waLink) }
                            }
                        )
                    }
                }
            }
        }
    }

    dealsDialogContactId?.let { contactId ->
        contacts.firstOrNull { it.id.value == contactId }?.let { contact ->
            ContactDealsDialog(
                contact = contact,
                deals = deals.filter { it.contactId.value == contactId },
                onDismiss = { dealsDialogContactId = null }
            )
        }
    }
}

/** Kartu kontak pada grid direktori — avatar solid, pill brand, tile ikon, aksi cepat. */
@Composable
private fun ContactGridCard(
    contact: Contact,
    dealSummary: Pair<Int, Long>?,
    canOpenDeal: Boolean,
    onOpenDeal: () -> Unit,
    onChatWhatsApp: () -> Unit
) {
    val brand = contact.brandName.value.takeIf { it.isNotBlank() }
    val tint = brandColor(brand)

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .claySurface(
                        shape = CircleShape,
                        background = tint,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Thick,
                        offset = ClayOffset.Small,
                        innerShade = false
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initialsOf(contact.displayName),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Spacer(Modifier.width(ClaySpacing.Lg))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "PIC",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurfaceMuted,
                    letterSpacing = ClayLetterSpacing.Label
                )
                Text(
                    text = contact.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                brand?.let {
                    Spacer(Modifier.height(ClaySpacing.Xs))
                    BrandPill(text = it, tint = tint)
                }
            }
        }

        Spacer(Modifier.height(ClaySpacing.Md))
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
            ContactInfoColumn(
                label = "WhatsApp",
                value = contact.phone?.localDisplay ?: "-",
                icon = { IconPhone(modifier = Modifier.size(12.dp), color = Color.White) },
                tileColor = WeMadeColors.Success,
                modifier = Modifier.weight(1f)
            )
            ContactInfoColumn(
                label = "Email",
                value = contact.email.ifBlank { "-" },
                icon = { IconMail(modifier = Modifier.size(12.dp), color = Color.White) },
                tileColor = WeMadeColors.OnSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (dealSummary != null && dealSummary.first > 0) {
            Spacer(Modifier.height(ClaySpacing.Md))
            Row(
                modifier = Modifier
                    .clayFlat(
                        shape = ClayShapes.Pill,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(horizontal = 12.dp, vertical = 5.dp)
            ) {
                Text(
                    text = "${dealSummary.first} Deals - ${formatIdr(dealSummary.second)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1
                )
            }
        }

        Spacer(Modifier.height(ClaySpacing.Lg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
        ) {
            ClayButton(
                text = "Chat WhatsApp",
                onClick = onChatWhatsApp,
                style = ClayButtonStyle.Primary,
                fontSize = 11.sp,
                enabled = contact.hasPhone,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp),
                leading = { IconChat(modifier = Modifier.size(13.dp), color = Color.White) }
            )
            ClayButton(
                text = "Lihat Deal",
                onClick = onOpenDeal,
                style = ClayButtonStyle.Secondary,
                fontSize = 11.sp,
                enabled = canOpenDeal,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 7.dp)
            )
        }
    }
}

/** Dua huruf inisial untuk avatar clay (ASCII aman lintas platform). */
private fun initialsOf(name: String): String =
    name.trim().split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .take(2)
        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
        .joinToString("")
        .ifBlank { "+" }

private val brandPalette = listOf(
    WeMadeColors.Primary,
    WeMadeColors.Accent,
    WeMadeColors.Success,
    WeMadeColors.Teal,
    WeMadeColors.Purple,
    WeMadeColors.Info
)

/**
 * Warna stabil per nama brand — avatar, pill brand, dan chip filter berbagi warna yang sama
 * (mockup: avatar biru untuk brand biru, oranye untuk brand oranye, dst).
 * Kontak tanpa brand memakai fallback Teal.
 */
private fun brandColor(brand: String?): Color =
    brand?.takeIf { it.isNotBlank() }
        ?.let { brandPalette[abs(it.hashCode()) % brandPalette.size] }
        ?: WeMadeColors.Teal

/** Kolom info kontak: tile ikon pekat + label kecil + nilai tebal (mockup WhatsApp/Email). */
@Composable
private fun ContactInfoColumn(
    label: String,
    value: String,
    icon: @Composable () -> Unit,
    tileColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clayFlat(
                        shape = ClayShapes.Tile,
                        background = tileColor,
                        outline = WeMadeColors.Outline,
                        borderWidth = ClayBorder.Hairline
                    ),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(ClaySpacing.Xs))
            Text(
                text = label,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(ClaySpacing.Xxs))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Pill brand padat berwarna dengan teks putih — dipakai di kartu dan dialog ringkasan. */
@Composable
private fun BrandPill(text: String, tint: Color) {
    Row(
        modifier = Modifier
            .clayFlat(
                shape = ClayShapes.Pill,
                background = tint,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Hairline
            )
            .padding(horizontal = 10.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1
        )
    }
}

/** Panel toolbar gabungan: pencarian + filter Brand (pill warna) + Client Status. */
@Composable
private fun ContactsToolbar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    brands: List<String>,
    selectedBrands: Set<String>,
    onToggleBrand: (String) -> Unit,
    onlyActiveClients: Boolean,
    onToggleActiveClients: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Panel,
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                borderWidth = ClayBorder.Medium,
                offset = ClayOffset.Small
            )
            .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Md),
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClayTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier.weight(1f),
            placeholder = "Cari nama kontak, brand, nomor WhatsApp...",
            leadingIcon = { IconSearch(modifier = Modifier.size(16.dp)) }
        )
        Row(
            modifier = Modifier
                .weight(1.4f, fill = false)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Brand",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            brands.forEach { brand ->
                ToolbarPill(
                    label = brand,
                    tint = brandColor(brand),
                    selected = brand in selectedBrands,
                    onClick = { onToggleBrand(brand) }
                )
            }
            Text(
                text = "Client Status",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            ToolbarPill(
                label = "Semua",
                tint = WeMadeColors.Success,
                selected = !onlyActiveClients,
                onClick = onToggleActiveClients
            )
            ToolbarPill(
                label = "Aktif",
                tint = WeMadeColors.Success,
                selected = onlyActiveClients,
                onClick = onToggleActiveClients
            )
        }
    }
}

/** Pill filter toolbar: terpilih = isian pekat warna brand/status + teks putih. */
@Composable
private fun ToolbarPill(
    label: String,
    tint: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    ClayActionSurface(
        onClick = onClick,
        selected = selected,
        containerColor = if (selected) tint else WeMadeColors.Surface,
        outlineColor = WeMadeColors.Outline,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) Color.White else WeMadeColors.OnSurface,
            maxLines = 1
        )
    }
}

/**
 * Ringkasan read-only seluruh deal milik satu kontak — sengaja TANPA formulir PO,
 * karena pengelolaan PO tetap tinggal di [DealDetailDialog] lewat tab Deal.
 */
@Composable
private fun ContactDealsDialog(
    contact: Contact,
    deals: List<Deal>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.widthIn(min = 420.dp, max = 540.dp),
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = "PIC",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = WeMadeColors.OnSurfaceMuted,
                        letterSpacing = ClayLetterSpacing.Label
                    )
                    Text(
                        text = contact.displayName,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = WeMadeColors.OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                ClayButton(
                    text = "Tutup",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Secondary,
                    fontSize = 11.sp
                )
            }
            contact.brandName.value.takeIf { it.isNotBlank() }?.let { brand ->
                Spacer(Modifier.height(ClaySpacing.Xs))
                BrandPill(text = brand, tint = brandColor(brand))
            }

            Spacer(Modifier.height(ClaySpacing.Lg))
            if (deals.isEmpty()) {
                Text(
                    text = "Kontak ini belum memiliki deal.",
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )
            } else {
                Text(
                    text = "Deal (${deals.size})",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Spacer(Modifier.height(ClaySpacing.Sm))
                deals.forEach { deal ->
                    DealSummaryRow(deal = deal)
                    Spacer(Modifier.height(ClaySpacing.Sm))
                }
            }
        }
    }
}

/** Satu baris deal pada dialog ringkasan: judul + nilai + badge tahap. */
@Composable
private fun DealSummaryRow(deal: Deal) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Tile,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border,
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Md),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = deal.title.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            deal.estimatedValue?.let { value ->
                Spacer(Modifier.height(ClaySpacing.Xxs))
                Text(
                    text = formatIdr(value.amount),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = WeMadeColors.Primary
                )
            }
        }
        Spacer(Modifier.width(ClaySpacing.Sm))
        ClayBadge(text = deal.stage.displayName, tint = deal.stage.tint(), dot = true)
    }
}
