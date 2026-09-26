package com.eventverse.app.presentation.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Pita pesan hasil aksi (sukses/gagal) di bawah toolbar layar kerja.
 *
 * Pola ini sudah ditulis tangan di `MasterDataWorkspaceScreen`, `CostingWorkspaceScreen`, dan
 * `FactoryFlowScreen`; pemakaian keempat (Kontak Vendor) memakai komponen ini (Aturan Tiga Kali).
 */
@Composable
fun ClayStatusBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tint = if (isError) WeMadeColors.Error else WeMadeColors.Success
    Row(
        modifier = modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Chip,
                background = if (isError) WeMadeColors.ErrorBg else WeMadeColors.SuccessBg,
                outline = tint,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = tint,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(Modifier.width(ClaySpacing.Md))
        Text(
            text = "✕ Tutup",
            fontSize = 12.sp,
            color = WeMadeColors.OnSurfaceMuted,
            modifier = Modifier.clickable(onClick = onDismiss)
        )
    }
}
