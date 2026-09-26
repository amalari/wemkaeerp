package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.transfer.FlowLegStatus
import com.eventverse.app.domain.transfer.FlowLegView
import com.eventverse.app.domain.transfer.TransferType
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Rincian satu perpindahan barang di alur, dibuka dengan mengetuk konektornya.
 *
 * Penerbitan dan penerimaan Surat Jalan dilakukan di workspace Surat Jalan, bukan di sini —
 * dokumen itu butuh sopir, kendaraan, dan rincian muatan yang tidak muat di dialog sesempit
 * ini, dan menyalin formulirnya ke dua tempat berarti dua tempat yang harus dijaga sama.
 * Dialog ini menjelaskan *kenapa* alurnya tertahan dan ke mana harus pergi.
 */
@Composable
internal fun TransferLegDetailDialog(
    view: FlowLegView,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(
                    text = view.leg.transferType.headline(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = view.leg.summary,
                    fontSize = 12.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayBadge(
                        text = view.status.displayName,
                        tint = when (view.status) {
                            FlowLegStatus.BELUM_TERBIT -> WeMadeColors.Error
                            FlowLegStatus.DIKIRIM -> WeMadeColors.Warning
                            FlowLegStatus.DITERIMA -> WeMadeColors.Success
                        }
                    )
                    view.manifest?.let { manifest ->
                        ClayBadge(text = manifest.sjNumber.value, tint = WeMadeColors.Primary)
                    }
                }

                if (view.isLegacyMatch) {
                    Text(
                        text = "Dokumen ini dipasangkan berdasarkan kecocokan asal-tujuan, bukan " +
                            "tautan langsung — ia terbit sebelum alur mencatat tautannya. Periksa " +
                            "ulang bila SPK ini melewati gedung yang sama lebih dari sekali.",
                        fontSize = 10.sp,
                        color = WeMadeColors.Warning
                    )
                }

                Text(
                    text = view.status.guidance(),
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                ClayButton(
                    text = "Tutup",
                    style = ClayButtonStyle.Secondary,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onDismiss
                )
            }
        }
    }
}

private fun TransferType.headline(): String = when (this) {
    TransferType.INTERNAL_SITE_TRANSFER -> "Mutasi Antar-Gedung"
    TransferType.SUBCONTRACT_OUTBOUND -> "Kirim ke Vendor Makloon"
    TransferType.SUBCONTRACT_INBOUND -> "Terima Kembali dari Vendor"
    TransferType.CUSTOMER_DISPATCH -> "Pengiriman ke Pembeli"
}

private fun FlowLegStatus.guidance(): String = when (this) {
    FlowLegStatus.BELUM_TERBIT ->
        "Tahap berikutnya tertahan sampai Surat Jalan untuk perpindahan ini terbit. " +
            "Buka menu Surat Jalan & Transfer untuk menerbitkannya."
    FlowLegStatus.DIKIRIM ->
        "Barang sudah berangkat. Tahap berikutnya terbuka setelah Surat Jalan ditandai " +
            "diterima di tujuan."
    FlowLegStatus.DITERIMA ->
        "Barang sudah diterima di tujuan. Pekerjaan di sana boleh dimulai."
}
