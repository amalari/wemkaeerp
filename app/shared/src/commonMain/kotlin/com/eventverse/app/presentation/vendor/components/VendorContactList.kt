package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceChip
import com.eventverse.app.presentation.designsystem.ClayFlowRow
import com.eventverse.app.presentation.designsystem.ClayOffset
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconPhone
import com.eventverse.app.presentation.designsystem.IconSearch
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.LocalDate

/** Daftar kontak vendor bergaya buku kontak: nama, WA, dan layanan yang sedang punya harga. */
@Composable
internal fun VendorContactList(
    vendors: List<Vendor>,
    selectedVendor: Vendor?,
    searchQuery: String,
    showInactive: Boolean,
    today: LocalDate,
    onSearch: (String) -> Unit,
    onToggleInactive: (Boolean) -> Unit,
    onSelect: (Vendor) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayTextField(
            value = searchQuery,
            onValueChange = onSearch,
            placeholder = "Cari nama, nomor WA, atau layanan…",
            leadingIcon = { IconSearch(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted) },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            ClayChoiceChip(text = "Aktif", selected = !showInactive, onClick = { onToggleInactive(false) })
            ClayChoiceChip(text = "Termasuk nonaktif", selected = showInactive, onClick = { onToggleInactive(true) })
        }

        if (vendors.isEmpty()) {
            VendorEmptyState(
                title = "Belum ada kontak vendor",
                body = "Tambahkan vendor makloon rekanan (sablon, bordir, jahit, laundry) beserta harga layanannya.",
                modifier = Modifier.weight(1f)
            )
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
            contentPadding = PaddingValues(bottom = ClaySpacing.Xl, end = ClaySpacing.Sm)
        ) {
            items(vendors, key = { it.id.value }) { vendor ->
                VendorContactTile(
                    vendor = vendor,
                    today = today,
                    isSelected = vendor.id == selectedVendor?.id,
                    onClick = { onSelect(vendor) }
                )
            }
        }
    }
}

@Composable
private fun VendorContactTile(vendor: Vendor, today: LocalDate, isSelected: Boolean, onClick: () -> Unit) {
    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Tile,
        containerColor = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.Surface,
        outlineColor = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
        offset = ClayOffset.Small,
        contentPadding = PaddingValues(ClaySpacing.Md),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = vendor.name.value,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (!vendor.isActive) {
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayTag(text = "Nonaktif", tint = WeMadeColors.OnSurfaceMuted)
            }
        }
        if (vendor.phone.isNotBlank()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs), verticalAlignment = Alignment.CenterVertically) {
                IconPhone(modifier = Modifier.size(12.dp), color = WeMadeColors.OnSurfaceMuted)
                Text(vendor.phone, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
            }
        }
        val services = vendor.currentRates(today).map { it.serviceName }.distinct()
        if (services.isNotEmpty()) {
            Spacer(Modifier.size(ClaySpacing.Xs))
            ClayFlowRow(spacing = ClaySpacing.Xs) {
                services.forEach { ClayTag(text = it, tint = WeMadeColors.Primary) }
            }
        }
    }
}
