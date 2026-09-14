package com.eventverse.app.presentation.invoicing.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.common.CurrencyCode
import com.eventverse.app.domain.common.Money
import com.eventverse.app.domain.common.Quantity
import com.eventverse.app.domain.common.Ratio
import com.eventverse.app.domain.common.UnitOfMeasure
import com.eventverse.app.domain.invoicing.*
import com.eventverse.app.domain.invoicing.usecases.CreateInvoiceCommand
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.presentation.designsystem.*
import com.eventverse.app.presentation.invoicing.InvoiceUiEvent
import com.eventverse.app.presentation.invoicing.InvoiceUiState
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate

@Composable
fun CreateInvoiceDialog(
    state: InvoiceUiState,
    onEvent: (InvoiceUiEvent) -> Unit
) {
    var selectedKind by remember { mutableStateOf(InvoiceKind.DOWN_PAYMENT) }
    var clientName by remember { mutableStateOf("") }
    var contactPerson by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var taxId by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var terms by remember { mutableStateOf("Pembayaran via transfer bank. Mohon sertakan nomor invoice pada berita transfer.") }

    // Line items state
    var lineDescription by remember { mutableStateOf("Produksi Garmen") }
    var lineQtyStr by remember { mutableStateOf("100") }
    var linePriceStr by remember { mutableStateOf("150000") }
    var taxPercentStr by remember { mutableStateOf("11") }

    val defaultTplId = state.defaultTemplate?.id ?: state.templates.firstOrNull()?.id ?: InvoiceTemplateId("tpl-std-id-001")

    Dialog(onDismissRequest = { onEvent(InvoiceUiEvent.CloseCreateInvoiceDialog) }) {
        ClayCard(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            containerColor = WeMadeColors.Surface,
            borderWidth = ClayBorder.Thick,
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Buat Faktur Tagihan Baru",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black,
                        color = WeMadeColors.OnSurface
                    )
                    ClayButton(
                        text = "✕",
                        onClick = { onEvent(InvoiceUiEvent.CloseCreateInvoiceDialog) },
                        style = ClayButtonStyle.Ghost,
                        fontSize = 14.sp
                    )
                }

                // Jenis Faktur Chips
                Text(text = "Pilih Jenis Tagihan", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurfaceMuted)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    InvoiceKind.entries.forEach { kind ->
                        val isSelected = selectedKind == kind
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clayFlat(
                                    shape = ClayShapes.Card,
                                    background = if (isSelected) WeMadeColors.PrimaryContainer else WeMadeColors.SurfaceMuted,
                                    outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Border,
                                    borderWidth = if (isSelected) ClayBorder.Medium else ClayBorder.Hairline
                                )
                                .clickable { selectedKind = kind }
                                .padding(vertical = ClaySpacing.Sm),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = kindLabel(kind),
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Medium,
                                color = if (isSelected) WeMadeColors.PrimaryDark else WeMadeColors.OnSurface
                            )
                        }
                    }
                }

                // Informasi Klien
                Text(text = "Data Klien / Pembeli", fontSize = 13.sp, fontWeight = FontWeight.Black, color = WeMadeColors.OnSurface)
                ClayTextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    label = "Nama Klien / Perusahaan *",
                    placeholder = "mis. PT Harapan Maju Garmen"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClayTextField(
                        value = contactPerson,
                        onValueChange = { contactPerson = it },
                        label = "PIC / Kontak",
                        placeholder = "Pak Budi",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = "No. Telepon / WA",
                        placeholder = "08123456789",
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClayTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email Tagihan",
                        placeholder = "billing@klien.com",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = taxId,
                        onValueChange = { taxId = it },
                        label = "NPWP Klien",
                        placeholder = "01.234.567.8-000.000",
                        modifier = Modifier.weight(1f)
                    )
                }

                ClayTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = "Alamat Pengiriman / Penagihan",
                    placeholder = "Jl. Raya Industri No. 45, Bandung"
                )

                // Rincian Item Tagihan
                Text(text = "Rincian Item Faktur", fontSize = 13.sp, fontWeight = FontWeight.Black, color = WeMadeColors.OnSurface)
                ClayTextField(
                    value = lineDescription,
                    onValueChange = { lineDescription = it },
                    label = "Deskripsi Pekerjaan / Produk *",
                    placeholder = "Produksi Kemeja Drill 100 pcs (Termin DP 50%)"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    ClayTextField(
                        value = lineQtyStr,
                        onValueChange = { lineQtyStr = it },
                        label = "Jumlah (Qty) *",
                        placeholder = "100",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = linePriceStr,
                        onValueChange = { linePriceStr = it },
                        label = "Harga Satuan (Rp) *",
                        placeholder = "75000",
                        modifier = Modifier.weight(1f)
                    )
                    ClayTextField(
                        value = taxPercentStr,
                        onValueChange = { taxPercentStr = it },
                        label = "PPN (%)",
                        placeholder = "11",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Kalkulasi Cepat
                val qtyInt = lineQtyStr.toIntOrNull() ?: 1
                val priceLong = linePriceStr.toLongOrNull() ?: 0L
                val taxDouble = taxPercentStr.toDoubleOrNull() ?: 0.0
                val subtotalMinor = qtyInt * priceLong * 100L
                val taxMinor = (subtotalMinor * (taxDouble / 100.0)).toLong()
                val totalMinor = subtotalMinor + taxMinor

                ClayCard(
                    modifier = Modifier.fillMaxWidth(),
                    containerColor = WeMadeColors.SurfaceMuted,
                    borderWidth = ClayBorder.Hairline,
                    contentPadding = PaddingValues(ClaySpacing.Md)
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Estimasi Subtotal:", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = Money(subtotalMinor, CurrencyCode.IDR).formatted(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "PPN ($taxPercentStr%):", fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)
                        Text(text = Money(taxMinor, CurrencyCode.IDR).formatted(), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(text = "Total Faktur:", fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Text(text = Money(totalMinor, CurrencyCode.IDR).formatted(), fontSize = 14.sp, fontWeight = FontWeight.Black, color = WeMadeColors.Primary)
                    }
                }

                // Catatan & Syarat
                ClayTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = "Catatan Khusus",
                    placeholder = "mis. DP 50% sebelum potong kain."
                )

                ClayTextField(
                    value = terms,
                    onValueChange = { terms = it },
                    label = "Syarat & Ketentuan Pembayaran",
                    placeholder = "Pembayaran via transfer BCA."
                )

                // Tombol Aksi
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = "Batal",
                        onClick = { onEvent(InvoiceUiEvent.CloseCreateInvoiceDialog) },
                        style = ClayButtonStyle.Ghost
                    )
                    Spacer(modifier = Modifier.width(ClaySpacing.Sm))
                    ClayButton(
                        text = "Simpan Draf Faktur",
                        enabled = clientName.isNotBlank() && lineDescription.isNotBlank(),
                        onClick = {
                            val line = InvoiceLine(
                                id = InvoiceLineId("line-01"),
                                description = lineDescription,
                                quantity = Quantity(qtyInt * 1_000_000L, UnitOfMeasure.PIECE),
                                unitPrice = Money(priceLong * 100L, CurrencyCode.IDR),
                                discount = Ratio.ZERO,
                                sortOrder = 1
                            )
                            val billTo = BillToParty(
                                name = clientName,
                                contactPerson = contactPerson,
                                phone = phone,
                                email = email,
                                address = address,
                                taxId = taxId
                            )
                            val today = Clock.System.now().let {
                                val s = it.toString().substringBefore('T')
                                val p = s.split('-')
                                LocalDate(p[0].toInt(), p[1].toInt(), p[2].toInt())
                            }
                            val cmd = CreateInvoiceCommand(
                                tenantId = TenantId("current"),
                                kind = selectedKind,
                                billTo = billTo,
                                lines = listOf(line),
                                taxRatio = Ratio.percent(taxDouble),
                                globalDiscount = Ratio.ZERO,
                                currency = CurrencyCode.IDR,
                                issueDate = today,
                                dueDate = null,
                                templateId = defaultTplId,
                                notes = notes,
                                terms = terms,
                                createdBy = "admin"
                            )
                            onEvent(InvoiceUiEvent.SubmitCreateInvoice(cmd))
                        },
                        style = ClayButtonStyle.Primary
                    )
                }
            }
        }
    }
}
