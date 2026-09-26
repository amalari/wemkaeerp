package com.eventverse.app.presentation.vendor.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.vendor.Vendor
import com.eventverse.app.domain.vendor.VendorAssignment
import com.eventverse.app.domain.vendor.VendorServiceRate
import com.eventverse.app.presentation.crm.components.formatRupiah
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.LocalDate

/**
 * Detail satu kontak vendor: profil, daftar harga yang berlaku, riwayat harga, dan order yang
 * pernah dikerjakan. Tombol ubah hanya muncul untuk [canManage].
 */
@Composable
internal fun VendorContactDetail(
    vendor: Vendor,
    history: List<VendorAssignment>,
    today: LocalDate,
    canManage: Boolean,
    onEdit: () -> Unit,
    onAddRate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        DetailSection(
            title = vendor.name.value,
            action = if (canManage) ({ ClayButton(text = "Ubah Kontak", style = ClayButtonStyle.Secondary, onClick = onEdit) }) else null
        ) {
            LabeledValue("Nomor WA", vendor.phone.ifBlank { "-" })
            LabeledValue("Alamat", vendor.address.ifBlank { "-" })
            if (vendor.notes.isNotBlank()) LabeledValue("Catatan", vendor.notes)
            if (!vendor.isActive) ClayTag(text = "Nonaktif — tidak bisa ditunjuk ke order baru", tint = WeMadeColors.Error)
        }

        val current = vendor.currentRates(today)
        DetailSection(
            title = "Harga Layanan Berlaku",
            action = if (canManage) ({ ClayButton(text = "+ Harga", onClick = onAddRate) }) else null
        ) {
            if (current.isEmpty()) {
                Text("Belum ada harga berlaku. Harga tetap bisa diisi manual saat menunjuk vendor.", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
            }
            current.forEach { RateRow(it) }
        }

        val past = vendor.rates.filter { !it.isEffectiveOn(today) }.sortedByDescending { it.effectiveFrom }
        if (past.isNotEmpty()) {
            DetailSection(title = "Riwayat & Harga Terjadwal") { past.forEach { RateRow(it) } }
        }

        DetailSection(title = "Riwayat Order di Vendor Ini") {
            if (history.isEmpty()) {
                Text("Belum pernah ditunjuk ke order mana pun.", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
            }
            history.forEach { HistoryRow(it) }
        }
    }
}

@Composable
private fun DetailSection(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(ClaySpacing.Lg)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (action != null) {
                Spacer(Modifier.width(ClaySpacing.Sm))
                action()
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm), modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

@Composable
private fun LabeledValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = WeMadeColors.OnSurfaceMuted)
        Text(value, fontSize = 13.sp, color = WeMadeColors.OnSurface)
    }
}

@Composable
private fun RateRow(rate: VendorServiceRate) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(rate.serviceName, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
            Text(
                text = "Berlaku ${rate.effectiveFrom}" + (rate.effectiveTo?.let { " s/d $it" } ?: "") +
                    (if (rate.minQuantity > 0) " · min ${rate.minQuantity} pcs" else ""),
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        Spacer(Modifier.width(ClaySpacing.Sm))
        Text(
            text = "${formatRupiah(rate.priceIdr)} / ${rate.unit.shortLabel}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
    }
}

@Composable
private fun HistoryRow(assignment: VendorAssignment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f, fill = false)) {
            Text(
                text = "${assignment.subjectLabel} · ${assignment.processName}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Text(
                text = "${assignment.quantityPcs} pcs · ${formatRupiah(assignment.pricePerUnitIdr)} / ${assignment.unit.shortLabel}",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        Spacer(Modifier.width(ClaySpacing.Sm))
        ClayTag(
            text = assignment.status.displayName,
            tint = if (assignment.isActive) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
        )
    }
}
