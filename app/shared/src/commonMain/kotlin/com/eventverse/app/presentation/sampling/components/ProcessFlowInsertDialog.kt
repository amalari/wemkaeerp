package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconCheckCircle
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.designsystem.clayFlat
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Menanyakan **di mana** sebuah proses opsional dikerjakan, sebelum ia disisipkan ke alur.
 *
 * Pada tahap penentuan alur, perancang hanya menentukan apakah proses dikerjakan di sini (internal)
 * atau vendor luar. Penunjukan nama vendor spesifik akan ditentukan oleh admin produksi saat order berjalan.
 */
@Composable
internal fun ProcessFlowInsertDialog(
    template: WorkStationSpec,
    onDismiss: () -> Unit,
    onConfirm: (executionMode: WorkExecutionMode, vendorRef: String?) -> Unit
) {
    var isSubcontracted by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 500.dp),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = template.displayName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        Text(
                            text = "Tentukan metode pelaksanaan proses pada alur produksi.",
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayBadge(text = "Proses Opsional", tint = WeMadeColors.Primary)
                }

                // Opsi Seleksi Berbentuk Kartu Radio Clay (Bukan Tombol Aksi)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ExecutionOptionCard(
                        title = "Dikerjakan di Sini",
                        subtitle = "Internal pabrik tanpa Surat Jalan",
                        icon = {
                            IconPackage(
                                modifier = Modifier.size(18.dp),
                                color = if (!isSubcontracted) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                            )
                        },
                        isSelected = !isSubcontracted,
                        modifier = Modifier.weight(1f),
                        onClick = { isSubcontracted = false }
                    )
                    ExecutionOptionCard(
                        title = "Vendor Luar",
                        subtitle = "Subkontrak (Makloon)",
                        icon = {
                            IconTruck(
                                modifier = Modifier.size(18.dp),
                                color = if (isSubcontracted) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                            )
                        },
                        isSelected = isSubcontracted,
                        modifier = Modifier.weight(1f),
                        onClick = { isSubcontracted = true }
                    )
                }

                // Penjelasan Dinamis Berdasarkan Opsi
                if (isSubcontracted) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.WarningBg,
                                outline = WeMadeColors.Warning.copy(alpha = 0.4f),
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconTruck(modifier = Modifier.size(15.dp), color = WeMadeColors.Warning)
                        Text(
                            text = "Proses dialokasikan ke vendor luar. Penunjukan nama vendor dan Surat Jalan akan diatur oleh admin produksi nanti.",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurface,
                            lineHeight = 14.sp
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clayFlat(
                                shape = ClayShapes.Tile,
                                background = WeMadeColors.SurfaceMuted,
                                outline = WeMadeColors.Outline,
                                borderWidth = ClayBorder.Hairline
                            )
                            .padding(ClaySpacing.Sm),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconPackage(modifier = Modifier.size(15.dp), color = WeMadeColors.Primary)
                        Text(
                            text = "Proses berjalan langsung di workstation internal pabrik sesuai jadwal antrean mesin.",
                            fontSize = 10.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            lineHeight = 14.sp
                        )
                    }
                }

                // Tombol Aksi di Footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    ClayButton(
                        text = "Sisipkan ke Alur",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onConfirm(
                                if (isSubcontracted) WorkExecutionMode.SUBCONTRACTED
                                else WorkExecutionMode.IN_HOUSE,
                                null
                            )
                        }
                    )
                }
            }
        }
    }
}

/**
 * Kartu seleksi opsi radio bergaya Claymorphism.
 */
@Composable
private fun ExecutionOptionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isSelected) WeMadeColors.Primary.copy(alpha = 0.08f) else WeMadeColors.Surface,
                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
                borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium
            )
            .clickable(onClick = onClick)
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            if (isSelected) {
                IconCheckCircle(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(ClayShapes.Pill)
                        .background(WeMadeColors.SurfaceMuted)
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                )
            }
        }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted,
            lineHeight = 13.sp
        )
    }
}

