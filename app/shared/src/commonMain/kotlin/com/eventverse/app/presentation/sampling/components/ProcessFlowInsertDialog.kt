package com.eventverse.app.presentation.sampling.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationSpec
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Menanyakan **di mana** sebuah proses opsional dikerjakan, sebelum ia disisipkan ke alur.
 *
 * Ini satu-satunya input manusia di seluruh rantai pengiriman, dan sengaja begitu: yang
 * ditanyakan adalah *konfigurasi* ("Bordir dikerjakan sendiri atau dimakloonkan?"), bukan
 * pengirimannya. Surat Jalan tetap diturunkan dari jawaban itu, tidak diketik.
 *
 * Pertanyaannya tidak bisa dijawab di muka oleh katalog: Bordir Komputer in-house di satu
 * pabrik dan makloon di pabrik lain, dan jawaban itulah yang menentukan apakah barangnya keluar
 * pabrik.
 */
@Composable
internal fun ProcessFlowInsertDialog(
    template: WorkStationSpec,
    onDismiss: () -> Unit,
    onConfirm: (executionMode: WorkExecutionMode, vendorRef: String?) -> Unit
) {
    var isSubcontracted by remember { mutableStateOf(false) }
    var vendorRef by remember { mutableStateOf("") }

    val canConfirm = !isSubcontracted || vendorRef.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        ClayCard(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(ClaySpacing.Lg)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                Text(
                    text = template.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface
                )
                Text(
                    text = "Dikerjakan di mana? Jawaban ini menentukan apakah barang keluar " +
                        "pabrik dan perlu Surat Jalan.",
                    fontSize = 11.sp,
                    color = WeMadeColors.OnSurfaceMuted
                )

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayButton(
                        text = "Dikerjakan Sendiri",
                        style = if (!isSubcontracted) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { isSubcontracted = false }
                    )
                    ClayButton(
                        text = "Vendor Makloon",
                        style = if (isSubcontracted) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = { isSubcontracted = true }
                    )
                }

                if (isSubcontracted) {
                    OutlinedTextField(
                        value = vendorRef,
                        onValueChange = { vendorRef = it },
                        label = { Text("Nama vendor", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "Barang berangkat ke vendor dan kembali lagi — dua Surat Jalan, " +
                            "bukan satu. Perjalanan pulang itulah tempat jumlah direkonsiliasi.",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                } else {
                    Text(
                        text = "Gedung tempat proses ini berjalan diambil dari pengaturan lokasi " +
                            "pabrik, bukan diisi di sini.",
                        fontSize = 10.sp,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                    ClayButton(
                        text = "Batal",
                        style = ClayButtonStyle.Secondary,
                        modifier = Modifier.weight(1f),
                        onClick = onDismiss
                    )
                    ClayButton(
                        text = "Sisipkan",
                        style = ClayButtonStyle.Primary,
                        modifier = Modifier.weight(1f),
                        enabled = canConfirm,
                        onClick = {
                            onConfirm(
                                if (isSubcontracted) WorkExecutionMode.SUBCONTRACTED
                                else WorkExecutionMode.IN_HOUSE,
                                vendorRef.takeIf { isSubcontracted && it.isNotBlank() }
                            )
                        }
                    )
                }
            }
        }
    }
}
