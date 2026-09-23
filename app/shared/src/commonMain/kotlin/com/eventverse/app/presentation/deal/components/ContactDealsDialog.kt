package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.Contact
import com.eventverse.app.domain.deal.Deal
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayLetterSpacing
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Ringkasan read-only seluruh deal milik satu kontak — sengaja TANPA formulir PO,
 * karena pengelolaan PO tetap tinggal di [DealDetailDialog] lewat tab Deal.
 */
@Composable
internal fun ContactDealsDialog(
    contact: Contact,
    deals: List<Deal>,
    onDismiss: () -> Unit
) {
    val brand = contact.brandName.value.takeIf { it.isNotBlank() }

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

            Spacer(Modifier.height(ClaySpacing.Xs))
            if (brand != null) {
                BrandPill(text = brand, tint = brandColor(brand, contact.displayName))
            } else {
                BrandPill(
                    text = "Tanpa Perusahaan",
                    tint = WeMadeColors.SurfaceMuted,
                    textColor = WeMadeColors.OnSurfaceMuted,
                    outline = WeMadeColors.Border
                )
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
