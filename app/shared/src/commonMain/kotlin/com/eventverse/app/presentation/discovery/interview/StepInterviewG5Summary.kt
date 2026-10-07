package com.eventverse.app.presentation.discovery.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.discovery.interview.Basis
import com.eventverse.app.domain.discovery.interview.InterviewStep
import com.eventverse.app.domain.discovery.interview.ModuleOrigin
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.discovery.displayName
import com.eventverse.app.presentation.theme.WeMadeColors

/**
 * Langkah G5: Ringkasan Satu Halaman (Divisi -> Peran -> Modul -> Fitur -> Sambungan).
 * Memuat dasar usulan tiap modul dan tombol navigasi kembali ke tiap giliran.
 */
@Composable
fun StepInterviewG5Summary(
    state: InterviewSessionState,
    onJumpToStep: (InterviewStep) -> Unit,
    onLockAndProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayCard(modifier = Modifier.fillMaxWidth(), outlineColor = WeMadeColors.Success) {
            Text(
                text = "Ringkasan Rancangan Operasional",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = WeMadeColors.Success
            )
            Text(
                text = "Semua keputusan divisi, peran, modul, fitur, dan sambungan terkumpul di sini. Anda dapat meninjau atau kembali ke giliran mana pun sebelum melanjutkan.",
                style = MaterialTheme.typography.bodySmall,
                color = WeMadeColors.OnSurfaceMuted,
                modifier = Modifier.padding(top = ClaySpacing.Xs)
            )
        }

        // 0. Profil Usaha & Sasaran (F0 & F1 Konsultan)
        state.profile?.let { prof ->
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Profil Usaha & Sasaran",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = prof.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurface,
                    modifier = Modifier.padding(top = ClaySpacing.Xs)
                )
                if (prof.goals.isNotEmpty()) {
                    Text(
                        text = "Tujuan Operasional:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Primary,
                        modifier = Modifier.padding(top = ClaySpacing.Sm)
                    )
                    prof.goals.forEach { goal ->
                        Text(
                            text = "- $goal",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
                if (prof.painPoints.isNotEmpty()) {
                    Text(
                        text = "Kendala Saat Ini:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = WeMadeColors.Warning,
                        modifier = Modifier.padding(top = ClaySpacing.Sm)
                    )
                    prof.painPoints.forEach { point ->
                        Text(
                            text = "- $point",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // 0b. Spesifikasi Kebutuhan Area Kerja (F2 Konsultan)
        if (state.specs.isNotEmpty()) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Spesifikasi Area Kerja",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
                ) {
                    state.specs.forEach { spec ->
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                        ) {
                            Text(
                                text = "Area: ${spec.areaKey.value}",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            val details = listOfNotNull(
                                spec.whoFills?.let { "Pengisi: $it" },
                                spec.whatRecorded?.let { "Mencatat: $it" },
                                spec.whoSees?.let { "Pelihat: $it" },
                                spec.doneWhen?.let { "Selesai: $it" }
                            )
                            if (details.isNotEmpty()) {
                                Text(
                                    text = details.joinToString(" | "),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = WeMadeColors.OnSurfaceMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // 1. Divisi & Kepala Divisi
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "1. Divisi & Kepala Divisi",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ClayButton(
                    text = "Ubah (G1)",
                    onClick = { onJumpToStep(InterviewStep.G1_DIVISI) },
                    style = ClayButtonStyle.Ghost
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                state.divisions.forEach { div ->
                    val head = state.roles.firstOrNull { it.divisionCode == div.code && it.isHead }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(div.name, fontWeight = FontWeight.SemiBold)
                        ClayBadge(
                            text = if (head != null) "Kepala: ${head.label}" else "Tanpa Kepala",
                            tint = if (head != null) WeMadeColors.Primary else WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // 2. Peran per Divisi
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "2. Peran Operasional",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ClayButton(
                    text = "Ubah (G2)",
                    onClick = { onJumpToStep(InterviewStep.G2_PERAN) },
                    style = ClayButtonStyle.Ghost
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
            ) {
                state.roles.forEach { role ->
                    val div = state.divisions.firstOrNull { it.code == role.divisionCode }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${role.label} (${div?.name ?: role.divisionCode.value})")
                        if (role.isHead) {
                            ClayBadge(text = "Kepala Divisi", tint = WeMadeColors.Accent)
                        }
                    }
                }
            }
        }

        // 3. Modul & Fitur (Beserta Asal & Dasar)
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "3. Modul & Fitur",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ClayButton(
                    text = "Ubah (G3)",
                    onClick = { onJumpToStep(InterviewStep.G3_MODUL) },
                    style = ClayButtonStyle.Ghost
                )
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
            ) {
                state.links.forEach { link ->
                    val role = state.roles.firstOrNull { it.roleKey == link.roleKey }
                    val originTint = when (link.origin) {
                        ModuleOrigin.REUSE_PLATFORM -> WeMadeColors.Primary
                        ModuleOrigin.REUSE_PACK -> WeMadeColors.Success
                        ModuleOrigin.EXTEND -> WeMadeColors.Warning
                        ModuleOrigin.NEW -> WeMadeColors.Accent
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${role?.label ?: link.roleKey.value} -> ${link.moduleId.value}",
                                fontWeight = FontWeight.SemiBold
                            )
                            ClayBadge(text = link.origin.displayName, tint = originTint)
                        }

                        if (link.features.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                            ) {
                                link.features.forEach { feat ->
                                    ClayBadge(text = feat, tint = WeMadeColors.Primary)
                                }
                            }
                        }

                        val ref = link.basisRef
                        val basisText = when (ref?.basis) {
                            Basis.NARASI -> "Dasar: Kutipan cerita \"${ref.quote.orEmpty()}\""
                            Basis.JAWABAN -> "Dasar: Jawaban Anda pada pertanyaan wawancara (${ref.answerId ?: "wawancara"})"
                            Basis.SARAN_DITERIMA -> "Dasar: Saran konsultan yang Anda terima"
                            Basis.SARAN_BELUM_DIJAWAB -> "Dasar: Saran konsultan (belum dikonfirmasi)"
                            null -> "Dasar: Terhubung dari narasi kebutuhan dan peran operasional Anda"
                        }
                        Text(
                            text = basisText,
                            style = MaterialTheme.typography.labelSmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // 4. Sambungan Alur Kerja
        ClayCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "4. Sambungan Serah-Terima",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                ClayButton(
                    text = "Ubah (G4)",
                    onClick = { onJumpToStep(InterviewStep.G4_SAMBUNGAN) },
                    style = ClayButtonStyle.Ghost
                )
            }

            if (state.handoffs.isEmpty()) {
                Text(
                    text = "Tidak ada sambungan khusus yang didefinisikan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = WeMadeColors.OnSurfaceMuted,
                    modifier = Modifier.padding(top = ClaySpacing.Xs)
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    state.handoffs.forEach { h ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${h.from.value} menyerahkan ke ${h.to.value}")
                            ClayBadge(text = h.portType.value, tint = WeMadeColors.Primary)
                        }
                    }
                }
            }
        }

        // Saran Konsultan Diterima
        val acceptedSuggestions = state.consultantSuggestions.filter { it.status == ConsultantSuggestionStatus.ACCEPTED }
        if (acceptedSuggestions.isNotEmpty()) {
            ClayCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Saran Konsultan yang Diterima",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = ClaySpacing.Sm),
                    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
                ) {
                    acceptedSuggestions.forEach { s ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.title, fontWeight = FontWeight.SemiBold)
                            ClayBadge(text = "Diterima", tint = WeMadeColors.Success)
                        }
                        Text(
                            text = s.rationale,
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                }
            }
        }

        // Tombol Utama Selesai
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            ClayButton(
                text = "Kunci Usulan & Lanjut ke Draf Blueprint",
                onClick = onLockAndProceed,
                style = ClayButtonStyle.Accent
            )
        }
    }
}
