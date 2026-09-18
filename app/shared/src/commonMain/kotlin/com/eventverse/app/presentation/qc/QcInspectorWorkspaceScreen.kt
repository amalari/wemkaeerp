package com.eventverse.app.presentation.qc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eventverse.app.domain.rbac.AccessDecision
import com.eventverse.app.domain.rbac.TestingPersona
import com.eventverse.app.domain.sampling.QcInspectionKind
import com.eventverse.app.domain.sampling.SamplingOrderId
import com.eventverse.app.presentation.designsystem.ClayBadge
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClayShapes
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.qc.components.QcInspectionPane
import com.eventverse.app.presentation.qc.components.QcQueuePane
import com.eventverse.app.presentation.sampling.SamplingUiEvent
import com.eventverse.app.presentation.sampling.SamplingViewModel
import com.eventverse.app.presentation.theme.WeMadeColors
import kotlinx.datetime.Clock

/**
 * Stasiun kerja QC: antrean di kiri, lembar ukur satu pcs di kanan.
 *
 * Dua meja (rajut & finishing) berbagi layar ini lewat tab, bukan lewat dua modul terpisah —
 * wewenangnya sama dan perbedaannya hanya pada tahap mana barangnya diperiksa.
 *
 * File ini hanya merakit; antreannya dihitung di [buildQcQueue], panelnya merender sendiri.
 */
@Composable
fun QcInspectorWorkspaceScreen(
    tenantSlug: String,
    decision: AccessDecision,
    persona: TestingPersona?,
    modifier: Modifier = Modifier,
    viewModel: SamplingViewModel = remember(tenantSlug) { SamplingViewModel(tenantSlug) }
) {
    val state by viewModel.uiState.collectAsState()
    // Meja rajut yang lebih dulu tersentuh di alur produksi, jadi itu yang terbuka lebih dulu.
    var activeKind by remember { mutableStateOf(QcInspectionKind.KNITTING) }
    var selectedOrderId by remember { mutableStateOf<SamplingOrderId?>(null) }
    var searchQuery by remember { mutableStateOf("") }

    // Petugas diambil dari sesi, tidak diketik. Nama yang bisa diketik bisa diketik nama orang
    // lain, dan lembar QC kehilangan artinya sebagai tanda tangan.
    val inspectorName = persona?.name.orEmpty()

    // Jam dibaca sekali per perubahan daftar: lama tunggu tidak perlu berdetik-detik, dan
    // membaca Clock di dalam Composable akan membuatnya menghitung ulang tiap rekomposisi.
    val queue = remember(state.orders, activeKind) {
        buildQcQueue(state.orders, Clock.System.now(), activeKind)
    }

    val visibleQueue = remember(queue, searchQuery) {
        val needle = searchQuery.trim()
        if (needle.isBlank()) queue else queue.filter { it.matches(needle) }
    }

    // Pilihan mengikuti antrean: begitu ada yang bisa dikerjakan, yang teratas langsung terbuka.
    LaunchedEffect(visibleQueue) {
        val stillVisible = visibleQueue.any { it.order.id == selectedOrderId }
        if (!stillVisible) selectedOrderId = visibleQueue.firstOrNull()?.order?.id
    }

    val selectedItem = visibleQueue.firstOrNull { it.order.id == selectedOrderId }

    Column(
        modifier = modifier.fillMaxSize().padding(ClaySpacing.Xl),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        QcWorkspaceHeader(
            queue = queue,
            activeKind = activeKind,
            inspectorName = inspectorName,
            onKindChange = {
                activeKind = it
                selectedOrderId = null
            }
        )

        when {
            state.isLoading && state.orders.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = WeMadeColors.Primary)
                }
            }

            else -> BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val isCompact = maxWidth < ClayBreakpoints.MasterDetail
                val queuePane: @Composable (Modifier) -> Unit = { paneModifier ->
                    QcQueuePane(
                        items = visibleQueue,
                        selectedOrderId = selectedOrderId,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        onSelect = { selectedOrderId = it },
                        modifier = paneModifier
                    )
                }
                val inspectionPane: @Composable (Modifier) -> Unit = { paneModifier ->
                    QcInspectionPane(
                        item = selectedItem,
                        inspectorName = inspectorName,
                        isSubmitting = state.isSubmitting,
                        onSubmit = { report ->
                            selectedItem?.let {
                                viewModel.onEvent(SamplingUiEvent.SubmitQcInspection(it.order.id, report))
                            }
                        },
                        modifier = paneModifier
                    )
                }

                if (isCompact) {
                    // Di lebar sempit dua panel akan saling menghimpit; antrean dipendekkan
                    // menjadi pita di atas dan lembar ukur mengambil sisa tinggi.
                    Column(verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)) {
                        queuePane(Modifier.fillMaxWidth().heightIn(max = 280.dp))
                        inspectionPane(Modifier.fillMaxWidth().weight(1f))
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Xl)
                    ) {
                        queuePane(Modifier.width(ClayPaneWidth.List).fillMaxHeight())
                        inspectionPane(Modifier.weight(1f).fillMaxHeight())
                    }
                }
            }
        }
    }
}

/** Pencocokan pencarian dibuat longgar: inspektor mengetik "0011", bukan "SPK-SMP-0011". */
private fun QcQueueItem.matches(needle: String): Boolean =
    spk.contains(needle, ignoreCase = true) ||
        order.clientName.contains(needle, ignoreCase = true) ||
        order.styleName.contains(needle, ignoreCase = true)

@Composable
private fun QcWorkspaceHeader(
    queue: List<QcQueueItem>,
    activeKind: QcInspectionKind,
    inspectorName: String,
    onKindChange: (QcInspectionKind) -> Unit
) {
    // Dihitung dari antrean yang sama dengan yang dirender — angka badge yang tidak cocok
    // dengan jumlah baris terbaca sebagai sistem rusak.
    val waiting = queue.count { it.bucket == QcQueueBucket.WAITING }
    val rework = queue.count { it.bucket == QcQueueBucket.REWORK }

    ClayCard(
        modifier = Modifier.fillMaxWidth(),
        shape = ClayShapes.Card,
        contentPadding = PaddingValues(ClaySpacing.Lg)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = "KONTROL KUALITAS (QUALITY CONTROL)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (inspectorName.isBlank()) {
                        "Sesi tidak mengenali petugas — lembar tidak bisa ditandatangani."
                    } else {
                        "Petugas: $inspectorName  •  satu lembar = satu pcs"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (inspectorName.isBlank()) WeMadeColors.Error else WeMadeColors.OnSurfaceMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(ClaySpacing.Md))

            Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
                if (waiting > 0) {
                    ClayBadge(text = "$waiting Menunggu", tint = WeMadeColors.Warning, dot = true)
                }
                if (rework > 0) {
                    ClayBadge(text = "$rework Perlu Rework", tint = WeMadeColors.Accent)
                }
            }
        }

        Spacer(modifier = Modifier.height(ClaySpacing.Md))

        Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
            QcInspectionKind.entries.forEach { kind ->
                ClayButton(
                    text = kind.shortLabel,
                    style = if (kind == activeKind) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
                    onClick = { onKindChange(kind) }
                )
            }
        }
    }
}
