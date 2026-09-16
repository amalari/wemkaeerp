package com.eventverse.app.presentation.invoicing.template

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.template.*
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

@Composable
fun DesignerToolbar(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    onClose: () -> Unit,
    fitZoomPercent: Int? = null,
    modifier: Modifier = Modifier
) {
    ClayCard(
        modifier = modifier.fillMaxWidth(),
        containerColor = WeMadeColors.Surface,
        borderWidth = ClayBorder.Medium,
        contentPadding = PaddingValues(ClaySpacing.Md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Title & Template Name
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Kembali",
                    onClick = onClose,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp,
                    leading = { IconArrowBack(Modifier.size(13.dp), color = WeMadeColors.OnSurfaceMuted) }
                )

                Column {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = state.template.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            color = WeMadeColors.OnSurface
                        )
                        if (state.template.applicableKinds.contains(InvoiceKind.SAMPLE)) {
                            ClayBadge(
                                text = "SAMPLE INVOICE",
                                tint = WeMadeColors.Primary,
                                fontSize = 10.sp
                            )
                        } else {
                            val primaryKind = state.template.applicableKinds.firstOrNull() ?: InvoiceKind.FULL
                            ClayBadge(
                                text = primaryKind.displayName.uppercase(),
                                tint = WeMadeColors.Accent,
                                fontSize = 10.sp
                            )
                        }
                    }
                    Text(
                        text = "Kertas: ${state.template.paperSize.name} (${state.template.paperSize.widthMm10 / 10} × ${state.template.paperSize.heightMm10 / 10} mm)",
                        fontSize = 11.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
            }

            // Center: pemilih alat kanvas.
            //
            // Tombol tambah elemen tidak lagi di sini: seluruh elemen disisipkan dari perpustakaan di
            // panel kiri. Sebelumnya baris ini juga menampung `+ Teks`, `+ Garis`, `+ Kotak`, dan
            // `AI Auto-Map`, sehingga toolbar adalah satu-satunya tempat yang bertambah panjang setiap
            // kali ada jenis elemen baru.
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pemilih alat kanvas. Dipisah dari tombol tambah elemen karena ini mengubah
                // arti tarikan mouse, bukan menambah isi kertas — dan tanpa mode Geser tidak ada
                // satu pun titik di kanvas yang bisa dipakai untuk memindahkan tampilan A4.
                Row(
                    modifier = Modifier
                        .clayFlat(
                            shape = ClayShapes.Button,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Border,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Xxs),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xxs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CanvasTool.entries.forEach { tool ->
                        val isActive = state.canvasTool == tool
                        val tint = if (isActive) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                        Row(
                            modifier = Modifier
                                .claySurface(
                                    shape = ClayShapes.Chip,
                                    background = if (isActive) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                    outline = if (isActive) WeMadeColors.Primary else WeMadeColors.SurfaceMuted,
                                    borderWidth = ClayBorder.Hairline,
                                    offset = if (isActive) ClayOffset.Pressed else ClayOffset.Flat
                                )
                                .clickable { onEvent(TemplateDesignerUiEvent.SetCanvasTool(tool)) }
                                .padding(horizontal = ClaySpacing.Md, vertical = ClaySpacing.Sm),
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            when (tool) {
                                CanvasTool.SELECT -> IconCursor(Modifier.size(13.dp), color = tint)
                                CanvasTool.PAN -> IconHandMove(Modifier.size(13.dp), color = tint)
                            }
                            Text(
                                text = tool.label,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                                color = if (isActive) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                            )
                        }
                    }
                }
            }

            // Right: Zoom & Save
            Row(
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Zoom controls
                ClayButton(
                    text = "-",
                    onClick = { onEvent(TemplateDesignerUiEvent.SetZoom(state.zoomPercent - 15)) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )
                Text(
                    text = "${state.zoomPercent}%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayButton(
                    text = "+",
                    onClick = { onEvent(TemplateDesignerUiEvent.SetZoom(state.zoomPercent + 15)) },
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )

                if (fitZoomPercent != null) {
                    // "Muat Layar" menyelamatkan pengguna dari kertas yang tiba-tiba keluar dari
                    // pandangan setelah panel kiri masuk: pada zoom 100%, A4 selebar 630dp sering tidak
                    // muat lagi di area kanvas yang tersisa.
                    ClayButton(
                        text = "Muat Layar",
                        onClick = { onEvent(TemplateDesignerUiEvent.SetZoom(fitZoomPercent)) },
                        style = ClayButtonStyle.Ghost,
                        fontSize = 11.sp
                    )
                }

                Spacer(modifier = Modifier.width(ClaySpacing.Sm))

                if (state.prefillData != null) {
                    ClayButton(
                        text = if (state.isSaving) "Menerbitkan…" else "Simpan & Pratinjau Faktur",
                        enabled = !state.isSaving,
                        onClick = { onEvent(TemplateDesignerUiEvent.SaveAndCreateInvoice()) },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp,
                        leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Surface) }
                    )
                } else {
                    ClayButton(
                        text = if (state.isSaving) "Menyimpan…" else "Simpan Template",
                        enabled = !state.isSaving,
                        onClick = { onEvent(TemplateDesignerUiEvent.SaveTemplate) },
                        style = ClayButtonStyle.Primary,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
