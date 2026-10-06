package com.eventverse.app.presentation.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue

/**
 * Langkah-langkah [DiscoveryWizardScreen] (plan §5, Fase D) — shell hanya merakit, langkah yang
 * merender (file-size-rules §4). Pemecahan mengikuti batas tanggung jawab, bukan batas baris.
 */

@Composable
internal fun StepNarrative(
    narrative: String,
    industryHint: String,
    busy: Boolean,
    onNarrativeChange: (String) -> Unit,
    onHintChange: (String) -> Unit,
    onSubmit: () -> Unit
) {
    ClayCard(modifier = Modifier.fillMaxWidth()) {
        // Funnel ini platform, bukan garment (T9/A4): tenant bordir, potong-jahit, maupun klinik
        // masuk lewat pintu yang sama, jadi pertanyaannya harus tentang **bisnis**, bukan pabrik.
        Text("Ceritakan kebutuhan bisnis Anda", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        OutlinedTextField(
            value = narrative,
            onValueChange = onNarrativeChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            minLines = 4,
            placeholder = { Text("Contoh: konveksi makloon dengan SPK jahit, klinik dengan antrean pasien per poli…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        OutlinedTextField(
            value = industryHint,
            onValueChange = onHintChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            singleLine = true,
            placeholder = { Text("Industri (opsional): konveksi, bordir, sablon…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        ClayButton(
            text = if (busy) "Menyusun…" else "Susun Draf Sistem",
            onClick = onSubmit,
            enabled = !busy,
            modifier = Modifier.padding(top = ClaySpacing.Md)
        )
    }
}

@Composable
internal fun StepBuild(
    companyName: String,
    busy: Boolean,
    onNameChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    draft: DiscoveryDraftUi? = null
) {
    if (draft != null && draft.screens.isNotEmpty()) {
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Text("Tinjauan Usulan Antarmuka", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(
                "Ringkasan tampilan dan alur per modul yang diusulkan untuk bisnis Anda:",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted
            )
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                draft.screens.forEach { screen ->
                    val module = draft.modules.firstOrNull { it.id == screen.moduleId }
                    ModuleProposalSummaryCard(
                        screen = screen,
                        moduleName = module?.displayName ?: screen.moduleId
                    )
                }
            }
        }
    }

    ClayCard(modifier = Modifier.fillMaxWidth()) {
        Text("Bangun Sistem Ini", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(
            "Kunci draf lalu kirim permintaan pembangunan. Draf yang sudah dikunci tidak bisa diubah lagi.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            color = WeMadeColors.OnSurfaceMuted
        )
        OutlinedTextField(
            value = companyName,
            onValueChange = onNameChange,
            modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
            singleLine = true,
            placeholder = { Text("Nama perusahaan Anda") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = WeMadeColors.Primary,
                unfocusedBorderColor = WeMadeColors.Outline
            )
        )
        if (companyName.isBlank()) {
            Text(
                "Nama perusahaan wajib diisi — ia menjadi judul lead di ledger tim kami.",
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs)
            )
        }
    }
    StepActions(
        onBack = onBack,
        onBackLabel = "Kembali ke Estimasi",
        onNext = onSubmit,
        onNextLabel = if (busy) "Mengirim…" else "Kunci & Bangun Sistem Ini",
        nextEnabled = !busy && companyName.isNotBlank(),
        nextStyle = ClayButtonStyle.Accent
    )
}

@Composable
internal fun EstimasiPrice(p: JsonValue.Obj) {
    Text("Estimasi Langganan", style = androidx.compose.material3.MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
    val monthly = (p.entries["subscriptionMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
    val withheld = p.entries["withheld"] == JsonValue.Bool(true)
    if (withheld) {
        Text(
            "Untuk industri ini kami belum punya riwayat pembangunan yang cukup, jadi rentang biaya ditahan " +
                "sampai survei singkat bersama tim kami. Estimasi langganan: ${rupiah(monthly)}.",
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall
        )
    } else {
        val low = (p.entries["gapLowMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
        val high = (p.entries["gapHighMonthlyIdr"] as? JsonValue.Num)?.raw?.toDoubleOrNull()
        // Gap = jumlah harga modul yang **belum ada** di katalog. Nol berarti alur ini bisa
        // dirakit dari modul yang sudah tersedia, jadi "Rp 0 – Rp 0" bukan estimasi gagal —
        // tapi kalau ditulis apa adanya, prospek membacanya sebagai angka rusak.
        if ((low ?: 0.0) == 0.0 && (high ?: 0.0) == 0.0) {
            Text(
                "Semua modul pada alur ini sudah tersedia — tidak ada biaya pembangunan tambahan." +
                    " Langganan: ${rupiah(monthly)}/bulan",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        } else {
            Text(
                "Biaya pembangunan sekali: ${low?.let { rupiah(it) } ?: "—"} – ${high?.let { rupiah(it) } ?: "—"}" +
                    " • Langganan: ${rupiah(monthly)}/bulan",
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
            )
        }
    }
}

/** Format Rupiah tanpa `String.format` (tidak tersedia di commonMain KMP). */
internal fun rupiah(value: Double?): String {
    if (value == null) return "—"
    val n = value.toLong()
    val grouped = n.toString().reversed().chunked(3).joinToString(".").reversed()
    return "Rp $grouped"
}

@Composable
internal fun StepActions(
    onBack: () -> Unit,
    onBackLabel: String,
    onNext: () -> Unit,
    onNextLabel: String,
    nextEnabled: Boolean = true,
    nextStyle: ClayButtonStyle = ClayButtonStyle.Primary
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
        ClayButton(text = onBackLabel, onClick = onBack, style = ClayButtonStyle.Secondary)
        ClayButton(text = onNextLabel, onClick = onNext, enabled = nextEnabled, style = nextStyle)
    }
}

/**
 * Lanjutkan sesi interview sebelumnya (plan §6 E1). Satu-satunya tempat funnel mengingat
 * masa lalu — tanpa ini, reload browser menghapus jejak dan draf menumpuk tanpa pintu kembali.
 */
