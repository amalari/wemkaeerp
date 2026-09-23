package com.eventverse.app.presentation.crm.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.crm.CrmLead
import com.eventverse.app.domain.invoicing.InvoiceKind
import com.eventverse.app.domain.invoicing.InvoiceSourceKind
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBorder
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconReceipt
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.invoicing.InvoicePrefillCoordinator
import com.eventverse.app.presentation.invoicing.InvoicePrefillData
import com.eventverse.app.presentation.navigation.AppNavScreen
import com.eventverse.app.presentation.navigation.LocalAppNavigator
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Tab Invoice pada Lead Inspector:
 * Memberikan 2 opsi langsung penerbitan faktur tagihan:
 * 1. Invoice Sampling (biaya prototype/sample 100%)
 * 2. Invoice DP (uang muka produksi dengan popup persentase)
 */
@Composable
fun LeadInspectorInvoiceTab(
    lead: CrmLead,
    onClose: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val navigator = LocalAppNavigator.current
    var isDpDialogOpen by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        // Banner Penjelasan Pilihan Invoice
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = WeMadeColors.SurfaceMuted,
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            Text(
                text = "PILIH JENIS INVOICE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Primary
            )
            Spacer(Modifier.height(ClaySpacing.Xs))
            Text(
                text = "Pilih jenis invoice resmi yang ingin diterbitkan untuk prospek ini:",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Pilihan 1: Invoice Sampling
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            outlineColor = WeMadeColors.Success,
            borderWidth = ClayBorder.Medium,
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Invoice Sampling",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "SAMPLE 100%",
                    tint = WeMadeColors.Success
                )
            }

            Spacer(Modifier.height(ClaySpacing.Sm))

            Text(
                text = "Tagihan pembuatan prototype sample 1-3 pcs untuk persetujuan buyer sebelum order massal.",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(Modifier.height(ClaySpacing.Md))

            ClayTag(
                text = "Status: Siap Diterbitkan",
                tint = WeMadeColors.Success
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            ClayButton(
                text = "Buat Invoice Sampling",
                style = ClayButtonStyle.Primary,
                fontSize = 11.sp,
                leading = { IconRuler(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                onClick = {
                    val clientDisplayName = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Prospek Lead" })
                    InvoicePrefillCoordinator.setPending(
                        InvoicePrefillData(
                            kind = InvoiceKind.SAMPLE,
                            clientName = clientDisplayName,
                            contactPerson = lead.contactPerson,
                            phone = lead.whatsappNumber?.value ?: "",
                            email = lead.email,
                            sourceKind = InvoiceSourceKind.CRM_LEAD,
                            sourceRef = lead.id.value,
                            lineDescription = "Jasa Pembuatan Prototype Sample Baju - $clientDisplayName",
                            lineQty = 1.0,
                            linePrice = 150000L,
                            notes = "Tagihan pembuatan sample / prototype 100% di muka."
                        )
                    )
                    onClose?.invoke()
                    navigator(AppNavScreen.INVOICING)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Pilihan 2: Invoice DP (Down Payment)
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            outlineColor = WeMadeColors.Primary,
            borderWidth = ClayBorder.Medium,
            contentPadding = PaddingValues(ClaySpacing.Md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Invoice DP (Down Payment)",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                ClayBadge(
                    text = "TERMIN DP",
                    tint = WeMadeColors.Primary
                )
            }

            Spacer(Modifier.height(ClaySpacing.Sm))

            Text(
                text = "Tagihan uang muka pesanan produksi massal dengan termin persentase (misal DP 30%, 50%, atau custom).",
                fontSize = 11.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(Modifier.height(ClaySpacing.Md))

            ClayTag(
                text = "Termin Pembayaran: Fleksibel",
                tint = WeMadeColors.Primary
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            ClayButton(
                text = "Buat Invoice DP...",
                style = ClayButtonStyle.Accent,
                fontSize = 11.sp,
                leading = { IconPackage(Modifier.size(13.dp), color = WeMadeColors.Surface) },
                onClick = { isDpDialogOpen = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Info Banner
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            containerColor = WeMadeColors.Info.copy(alpha = 0.08f),
            outlineColor = WeMadeColors.Info.copy(alpha = 0.3f),
            borderWidth = ClayBorder.Hairline,
            contentPadding = PaddingValues(ClaySpacing.Sm)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                IconReceipt(Modifier.size(14.dp), color = WeMadeColors.Info)
                Text(
                    text = "Memilih invoice akan langsung membuka modul Invoice dengan data prospek terisi otomatis.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurface
                )
            }
        }
    }

    // Popup Dialog Berapa Persen DP
    if (isDpDialogOpen) {
        InvoiceDpPercentageDialog(
            lead = lead,
            onDismiss = { isDpDialogOpen = false },
            onConfirm = { dpPercent ->
                isDpDialogOpen = false
                val clientDisplayName = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Prospek Lead" })
                val qty = (lead.estimatedPcs ?: 100).toDouble()
                val estValue = lead.estimatedValue
                val unitPrice = if (estValue != null && qty > 0) {
                    ((estValue.amount * dpPercent) / (100 * qty.toLong())).coerceAtLeast(1L)
                } else {
                    150000L
                }

                InvoicePrefillCoordinator.setPending(
                    InvoicePrefillData(
                        kind = InvoiceKind.DOWN_PAYMENT,
                        clientName = clientDisplayName,
                        contactPerson = lead.contactPerson,
                        phone = lead.whatsappNumber?.value ?: "",
                        email = lead.email,
                        sourceKind = InvoiceSourceKind.CRM_LEAD,
                        sourceRef = lead.id.value,
                        lineDescription = "Uang Muka Produksi (DP $dpPercent%) - $clientDisplayName",
                        lineQty = qty,
                        linePrice = unitPrice,
                        notes = "Termin Pembayaran: Uang Muka (DP) sebesar $dpPercent% sebelum proses produksi dimulai. Sisa pelunasan dibayar sebelum pesanan dikirim."
                    )
                )
                onClose?.invoke()
                navigator(AppNavScreen.INVOICING)
            }
        )
    }
}

/**
 * Popup Dialog untuk menentukan persentase DP sebelum invoice diterbitkan.
 */
@Composable
fun InvoiceDpPercentageDialog(
    lead: CrmLead,
    onDismiss: () -> Unit,
    onConfirm: (percent: Int) -> Unit
) {
    var percentText by remember { mutableStateOf("50") }
    val currentPercent = percentText.toIntOrNull()?.coerceIn(1, 100) ?: 50
    val clientDisplayName = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Prospek" })

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.width(380.dp),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Text(
                text = "Tentukan Persentase DP",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurface
            )

            Spacer(Modifier.height(ClaySpacing.Xs))

            Text(
                text = "Berapa persen uang muka untuk pesanan $clientDisplayName?",
                fontSize = 12.sp,
                color = WeMadeColors.OnSurfaceMuted
            )

            Spacer(Modifier.height(ClaySpacing.Md))

            // Preset Quick Buttons (30%, 50%, 70%)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                listOf(30, 50, 70).forEach { preset ->
                    val isSelected = currentPercent == preset
                    ClayButton(
                        text = "$preset%",
                        style = if (isSelected) ClayButtonStyle.Primary else ClayButtonStyle.Ghost,
                        fontSize = 12.sp,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        onClick = { percentText = preset.toString() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(Modifier.height(ClaySpacing.Md))

            // Kolom Input Manual
            Text(
                text = "Persentase DP (%):",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.OnSurfaceMuted
            )
            Spacer(Modifier.height(ClaySpacing.Xs))
            ClayTextField(
                value = percentText,
                onValueChange = { input ->
                    val filtered = input.filter { it.isDigit() }.take(3)
                    percentText = filtered
                },
                placeholder = "Contoh: 50",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(ClaySpacing.Sm))

            Text(
                text = "Termin yang akan tercatat: DP $currentPercent% di muka, sisa ${100 - currentPercent}% pelunasan.",
                fontSize = 11.sp,
                color = WeMadeColors.Primary,
                fontWeight = FontWeight.Medium
            )

            Spacer(Modifier.height(ClaySpacing.Lg))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayButton(
                    text = "Batal",
                    onClick = onDismiss,
                    style = ClayButtonStyle.Ghost,
                    modifier = Modifier.weight(1f)
                )
                ClayButton(
                    text = "Lanjut Buat Invoice DP",
                    onClick = { onConfirm(currentPercent) },
                    style = ClayButtonStyle.Primary,
                    enabled = percentText.isNotBlank() && (percentText.toIntOrNull() ?: 0) > 0,
                    modifier = Modifier.weight(1.4f)
                )
            }
        }
    }
}
