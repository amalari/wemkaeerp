package com.eventverse.app.presentation.deal.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eventverse.app.domain.sampling.SamplingOrder
import com.eventverse.app.domain.sampling.SizeChartRow
import com.eventverse.app.domain.sampling.calculateTotalSampleQuantity
import com.eventverse.app.domain.sampling.isQtyRow
import com.eventverse.app.domain.sampling.isSizeColumnActive
import com.eventverse.app.domain.sampling.missingSpkRequirements
import com.eventverse.app.domain.sampling.spkValidationWarnings
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Popup "Ajukan Revisi": textarea multi-baris biasa untuk menuliskan catatan revisi buyer.
 * Tombol kirim aktif hanya ketika catatan tidak kosong — revisi tanpa alasan ditolak UI.
 */
@Composable
internal fun RevisionNotesDialog(
    designCode: String,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit
) {
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.55f)
                .widthIn(min = 420.dp, max = 560.dp),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Xxl)
        ) {
            Text(
                text = "Ajukan Revisi - $designCode",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(
                text = "Tuliskan catatan revisi buyer untuk desain ini.",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
            Spacer(Modifier.height(ClaySpacing.Md))
            ClayTextField(
                value = notes,
                onValueChange = { notes = it },
                placeholder = "Catatan revisi... (mis. warna terlalu gelap, ganti ke Navy Tua)",
                singleLine = false,
                minLines = 4
            )
            Spacer(Modifier.height(ClaySpacing.Lg))
            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 12.sp
                )
                ClayButton(
                    text = "Ajukan Revisi",
                    onClick = { onSubmit(notes.trim()) },
                    enabled = notes.isNotBlank(),
                    style = ClayButtonStyle.Accent,
                    fontSize = 12.sp
                )
            }
        }
    }
}

/**
 * Popup konfirmasi sebelum SPK diterbitkan ke antrean kerja Divisi Sampling.
 * Menampilkan ringkasan spesifikasi, target deadline, validasi kelengkapan data (nama, qty, foto, deadline),
 * serta edukasi perubahan status alur kerja produksi.
 */
@Composable
internal fun ConfirmSpkDialog(
    order: SamplingOrder,
    sizeMatrix: List<SizeChartRow>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val totalQty = calculateTotalSampleQuantity(sizeMatrix, order.sampleQuantity)
    val missingReqs = order.missingSpkRequirements(sizeMatrix)
    val warnings = order.spkValidationWarnings()

    // Rincian alokasi kuantitas per ukuran yang aktif
    val qtyRow = sizeMatrix.firstOrNull { it.isQtyRow }
    val sizeAllocations = qtyRow?.values?.entries
        ?.mapNotNull { (col, v) ->
            val count = v.trim().toIntOrNull() ?: 0
            if (count > 0 && isSizeColumnActive(sizeMatrix, col)) Pair(col, count) else null
        } ?: emptyList()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.42f)
                .widthIn(min = 440.dp, max = 520.dp),
            shape = ClayShapes.Panel,
            contentPadding = PaddingValues(ClaySpacing.Xl)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                    ) {
                        Text(
                            text = "Konfirmasi Terbitkan SPK",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface
                        )
                        ClayTag(
                            text = order.styleName.ifBlank { "Tanpa Nama" },
                            tint = WeMadeColors.Primary
                        )
                    }
                    Spacer(Modifier.height(ClaySpacing.Xxs))
                    Text(
                        text = "Pastikan data pesanan benar karena SPK ini akan diteruskan ke Divisi Sampling.",
                        fontSize = 13.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                }
                ClayActionSurface(
                    onClick = onDismiss,
                    contentPadding = PaddingValues(ClaySpacing.Xs)
                ) {
                    IconClose(Modifier.size(18.dp), color = WeMadeColors.OnSurfaceMuted)
                }
            }

            Spacer(Modifier.height(ClaySpacing.Lg))

            // Callout Edukasi Alur
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Primary.copy(alpha = 0.35f),
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Md)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                    verticalAlignment = Alignment.Top
                ) {
                    IconClipboard(Modifier.size(20.dp), color = WeMadeColors.Primary)
                    Text(
                        text = if (sizeAllocations.size > 1) {
                            "Setelah diterbitkan, sistem akan menerbitkan ${sizeAllocations.size} SPK Sampling terpisah (1 SPK per ukuran: ${sizeAllocations.joinToString { "${it.first} (${it.second} pcs)" }}). Setiap SPK akan masuk ke antrean kerja Divisi Sampling untuk dikerjakan secara independen."
                        } else {
                            "Setelah diterbitkan, SPK akan langsung masuk ke antrean kerja Divisi Sampling pada tahap Pemrograman Mesin (CAM). Tim sampling akan merajut/membuat sampel fisik sesuai spesifikasi ini."
                        },
                        fontSize = 12.5.sp,
                        color = WeMadeColors.OnSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            Spacer(Modifier.height(ClaySpacing.Lg))

            // Ringkasan Data yang Akan Diteruskan
            Text(
                text = "Ringkasan Data SPK:",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )
            Spacer(Modifier.height(ClaySpacing.Xs))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clayFlat(
                        shape = ClayShapes.Card,
                        background = WeMadeColors.SurfaceMuted,
                        outline = WeMadeColors.Border,
                        borderWidth = ClayBorder.Hairline
                    )
                    .padding(ClaySpacing.Lg)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                    // Nama Desain
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nama Desain",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = order.styleName.ifBlank { "-" },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WeMadeColors.OnSurface
                        )
                    }

                    // Target Deadline Selesai Sampel
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target Deadline Selesai",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        if (order.deadlineDelivery != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconCalendarGrid(Modifier.size(15.dp), color = WeMadeColors.Success)
                                Text(
                                    text = order.deadlineDelivery.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Success
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconBan(Modifier.size(15.dp), color = WeMadeColors.Error)
                                Text(
                                    text = "Belum Diisi (Wajib)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Error
                                )
                            }
                        }
                    }

                    // Total Sampel & Rincian Ukuran
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Jumlah Sampel",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            Text(
                                text = "$totalQty pcs",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (totalQty > 0) WeMadeColors.Primary else WeMadeColors.Error
                            )
                            if (sizeAllocations.isNotEmpty()) {
                                sizeAllocations.forEach { (sizeName, count) ->
                                    ClayTag(
                                        text = "$sizeName: $count",
                                        tint = WeMadeColors.Primary
                                    )
                                }
                            }
                        }
                    }

                    // Foto Mockup
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Foto Mockup Visual",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        if (!order.mockupFrontKey.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconCheckCircle(Modifier.size(15.dp), color = WeMadeColors.Success)
                                Text(
                                    text = "Tampak Depan Terlampir" + if (!order.mockupBackKey.isNullOrBlank()) " (+ Belakang)" else "",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = WeMadeColors.Success
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                IconBan(Modifier.size(15.dp), color = WeMadeColors.Error)
                                Text(
                                    text = "Belum Diunggah (Wajib)",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = WeMadeColors.Error
                                )
                            }
                        }
                    }

                    // Biaya Sampling
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Biaya Sampling",
                            fontSize = 13.sp,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                        Text(
                            text = if (order.samplingFeeIdr > 0L) formatIdr(order.samplingFeeIdr) else "Gratis / Termasuk Deal",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = WeMadeColors.OnSurface
                        )
                    }

                    // Catatan Khusus
                    if (order.notes.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "Catatan Khusus",
                                fontSize = 13.sp,
                                color = WeMadeColors.OnSurfaceMuted
                            )
                            Text(
                                text = order.notes,
                                fontSize = 13.sp,
                                color = WeMadeColors.OnSurface,
                                modifier = Modifier.fillMaxWidth(0.65f)
                            )
                        }
                    }
                }
            }

            // Validasi: Error fatal atau Peringatan
            if (missingReqs.isNotEmpty()) {
                Spacer(Modifier.height(ClaySpacing.Md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Error,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconBan(Modifier.size(18.dp), color = WeMadeColors.Error)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Data Belum Lengkap (SPK belum bisa diterbitkan):",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Error
                            )
                            missingReqs.forEach { req ->
                                Text(
                                    text = "· $req",
                                    fontSize = 12.sp,
                                    color = WeMadeColors.Error
                                )
                            }
                        }
                    }
                }
            } else if (warnings.isNotEmpty()) {
                Spacer(Modifier.height(ClaySpacing.Md))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clayFlat(
                            shape = ClayShapes.Card,
                            background = WeMadeColors.WarningBg,
                            outline = WeMadeColors.Warning,
                            borderWidth = ClayBorder.Hairline
                        )
                        .padding(ClaySpacing.Md)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
                        verticalAlignment = Alignment.Top
                    ) {
                        IconWarning(Modifier.size(18.dp), color = WeMadeColors.Warning)
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "Perhatian Sebelum Menerbitkan:",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = WeMadeColors.Warning
                            )
                            warnings.forEach { warn ->
                                Text(
                                    text = "· $warn",
                                    fontSize = 12.sp,
                                    color = WeMadeColors.OnSurface
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(ClaySpacing.Xl))

            // Tombol Aksi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ClayButton(
                    text = "Batal / Cek Kembali",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Ghost,
                    fontSize = 13.sp
                )
                Spacer(Modifier.width(ClaySpacing.Sm))
                ClayButton(
                    text = if (sizeAllocations.size > 1) "Ya, Terbitkan ${sizeAllocations.size} SPK Sampling" else "Ya, Terbitkan SPK",
                    onClick = onConfirm,
                    enabled = missingReqs.isEmpty(),
                    style = ClayButtonStyle.Primary,
                    leading = { IconCheck(Modifier.size(14.dp), color = WeMadeColors.Surface) },
                    fontSize = 13.sp
                )
            }
        }
    }
}
