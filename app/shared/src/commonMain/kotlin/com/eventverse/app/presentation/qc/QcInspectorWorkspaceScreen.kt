package com.eventverse.app.presentation.qc

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.eventverse.app.presentation.designsystem.ClayBreakpoints
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.IconArrowBack
import com.eventverse.app.presentation.qc.components.QcInspectionPane
import com.eventverse.app.presentation.qc.components.QcQueuePane
import com.eventverse.app.presentation.qc.components.QcWorkspaceHeader
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
 * Di bawah [ClayBreakpoints.MasterDetail] layar ini berganti jadi satu panel pada satu waktu:
 * antrean dulu, lalu lembar ukur setinggi layar penuh dengan bilah kembali. QC adalah satu-
 * satunya modul yang dipakai berdiri di meja ukur dengan telepon di tangan, jadi lebar sempit
 * di sini adalah bentuk utamanya, bukan penyesuaian.
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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < ClayBreakpoints.MasterDetail

        // Di telepon layar ini dipakai sambil berdiri di meja ukur: satu panel penuh pada satu
        // waktu, bukan dua panel yang sama-sama tidak cukup. Antrean adalah langkah memilih;
        // setelah SPK terpilih seluruh layar milik lembar ukur, dan tombol kembali yang
        // mengembalikan antrean.
        var showDetailOnCompact by remember { mutableStateOf(false) }
        val isViewingDetail = isCompact && showDetailOnCompact && selectedItem != null

        val queuePane: @Composable (Modifier) -> Unit = { paneModifier ->
            QcQueuePane(
                items = visibleQueue,
                // Di telepon tidak ada panel kanan yang perlu dicerminkan sorotannya; kartu
                // tersorot di sana hanya membingungkan karena tidak menunjuk apa pun.
                selectedOrderId = if (isCompact) null else selectedOrderId,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onSelect = {
                    selectedOrderId = it
                    showDetailOnCompact = true
                },
                modifier = paneModifier
            )
        }
        val inspectionPane: @Composable (Modifier) -> Unit = { paneModifier ->
            QcInspectionPane(
                item = selectedItem,
                inspectorName = inspectorName,
                isSubmitting = state.isSubmitting,
                isCompact = isCompact,
                onSubmit = { report ->
                    selectedItem?.let {
                        viewModel.onEvent(SamplingUiEvent.SubmitQcInspection(it.order.id, report))
                    }
                },
                modifier = paneModifier
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                // Padding Xl memakan 32dp dari 390dp — di telepon dipersempit supaya kotak cm
                // dan kolom catatan mendapat lebar yang sebenarnya mereka butuhkan.
                .padding(if (isCompact) ClaySpacing.Md else ClaySpacing.Xl),
            verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
        ) {
            // Kepala stasiun disembunyikan saat lembar ukur terbuka di telepon: identitas meja
            // sudah terbaca di bilah kembali, dan 11 titik ukur butuh tinggi layar lebih dari
            // ia butuh pengulangan judul.
            if (!isViewingDetail) {
                QcWorkspaceHeader(
                    queue = queue,
                    activeKind = activeKind,
                    inspectorName = inspectorName,
                    isCompact = isCompact,
                    onKindChange = {
                        activeKind = it
                        selectedOrderId = null
                        showDetailOnCompact = false
                    }
                )
            }

            when {
                state.isLoading && state.orders.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = WeMadeColors.Primary)
                    }
                }

                isCompact -> {
                    if (isViewingDetail) {
                        QcCompactDetailBackBar(
                            label = if (selectedItem?.designCode != null) {
                                "${selectedItem.spk} • ${selectedItem.designCode}"
                            } else {
                                selectedItem?.spk.orEmpty()
                            },
                            onBack = { showDetailOnCompact = false }
                        )
                        inspectionPane(Modifier.fillMaxWidth().weight(1f))
                    } else {
                        queuePane(Modifier.fillMaxWidth().weight(1f))
                    }
                }

                else -> Row(
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

/** Bilah kembali layar sempit: satu-satunya jalan pulang dari lembar ukur ke antrean. */
@Composable
private fun QcCompactDetailBackBar(label: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
    ) {
        ClayButton(
            text = "Antrean",
            style = ClayButtonStyle.Secondary,
            // Canvas ikon tidak punya ukuran intrinsik: tanpa .size() ia menciut jadi titik.
            leading = { IconArrowBack(Modifier.size(16.dp), color = WeMadeColors.OnSurface) },
            contentPadding = PaddingValues(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Lg),
            onClick = onBack
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Pencocokan pencarian dibuat longgar: inspektor mengetik "0011", bukan "SPK-SMP-0011". */
private fun QcQueueItem.matches(needle: String): Boolean =
    spk.contains(needle, ignoreCase = true) ||
        order.clientName.contains(needle, ignoreCase = true) ||
        order.styleName.contains(needle, ignoreCase = true) ||
        (designCode != null && designCode.contains(needle, ignoreCase = true))
