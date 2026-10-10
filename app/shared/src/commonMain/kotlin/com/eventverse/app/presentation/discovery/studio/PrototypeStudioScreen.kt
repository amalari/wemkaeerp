package com.eventverse.app.presentation.discovery.studio

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.eventverse.app.domain.discovery.WidgetKind
import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.infrastructure.api.DiscoveryApiClient
import com.eventverse.app.presentation.designsystem.ClayButton
import com.eventverse.app.presentation.designsystem.ClayButtonStyle
import com.eventverse.app.presentation.designsystem.ClayCard
import com.eventverse.app.presentation.designsystem.ClayChoiceGroup
import com.eventverse.app.presentation.designsystem.ClayPaneWidth
import com.eventverse.app.presentation.designsystem.ClaySpacing
import com.eventverse.app.presentation.designsystem.ClayStatusBanner
import com.eventverse.app.presentation.designsystem.ClayTag
import com.eventverse.app.presentation.designsystem.ClayTextField
import com.eventverse.app.presentation.discovery.PrototypeRenderer
import com.eventverse.app.presentation.theme.WeMadeColors
import com.eventverse.app.shared.json.JsonValue
import kotlinx.coroutines.launch

/**
 * `PrototypeStudioScreen` (plan §4, Fase C) — Studio **internal** pola prototype, mengikuti pola
 * `TemplateDesigner` pada faktur: kiri galeri pola tersimpan, kanan perancang dengan pratinjau hidup.
 *
 * Alurnya sengaja pendek karena yang disimpan bukan layar jadi, melainkan **resep susun**: pilih pack
 * → panen kerangka baris dari `WidgetRegistry` untuk modul yang dipilih → sunting → simpan ke
 * `ops.prototype_patterns` (V79). Pratinjaunya memakai [PrototypeRenderer] yang **sama** dengan
 * pratinjau draf prospek, jadi tidak ada renderer kedua yang bisa menyimpang (plan D2).
 *
 * Menulis pola adalah wewenang platform superadmin (server menolak peran lain dengan 403).
 * [canWrite] hanya menyembunyikan tombol agar pengguna tidak menabrak dinding; gerbang sebenarnya
 * tetap di rute.
 */
@Composable
fun PrototypeStudioScreen(
    canWrite: Boolean,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val client = remember { DiscoveryApiClient() }

    var loaded by remember { mutableStateOf(false) }
    var patterns by remember { mutableStateOf<List<PrototypePatternUi>>(emptyList()) }
    var query by remember { mutableStateOf("") }
    var draft by remember { mutableStateOf(PrototypePatternUi()) }
    var sourceModuleId by remember { mutableStateOf<String?>(null) }
    var moduleQuery by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    fun load() {
        scope.launch {
            busy = true
            client.listPrototypePatterns()
                .onSuccess { patterns = PrototypePatternUi.listFromJson(it) }
                .onFailure { error = it.message ?: "Gagal memuat daftar pola" }
            loaded = true
            busy = false
        }
    }

    LaunchedEffect(Unit) { load() }

    val packs = remember { DomainPackRegistry.all }
    val selectedPack = draft.packCode?.let { code -> packs.firstOrNull { it.code.value == code } }
    val moduleMatches = selectedPack?.modules.orEmpty().filter {
        moduleQuery.isBlank() ||
            it.id.value.contains(moduleQuery, ignoreCase = true) ||
            it.displayName.contains(moduleQuery, ignoreCase = true)
    }
    val shownModules = moduleMatches.take(MODULE_CHIPS)
    val sourceModule = sourceModuleId?.let { id -> selectedPack?.modules?.firstOrNull { it.id.value == id } }
    val previewModuleId = sourceModuleId ?: PREVIEW_MODULE_ID
    val previewModuleName = sourceModule?.displayName ?: draft.packLabel
    val previewSection = sourceModule?.section?.value ?: "studio"

    /**
     * Panen kerangka: baris diambil dari registry sampel yang **sama** dengan yang dipakai pratinjau
     * draf, jadi pola baru mulai dari bentuk yang sudah dikenal sistem, bukan dari kanvas kosong.
     * Kosong = kombinasi modul × widget tidak punya bentuk baku; pengguna diberi tahu, bukan
     * dibiarkan menebak kenapa tombolnya tidak berefek.
     */
    fun harvestSkeleton() {
        val pack = selectedPack
        val moduleId = sourceModuleId
        if (pack == null || moduleId == null) {
            notice = "Pilih pack dan modul asal dulu - kerangka dipanen dari kosakata modulnya."
            return
        }
        val harvested = PrototypePatternUi.skeletonFrom(pack, moduleId, draft.widgetCode)
        if (harvested.isEmpty()) {
            notice = "Widget '${draft.widgetLabel}' tidak punya bentuk baku untuk modul itu. Tambah baris manual."
        } else {
            draft = draft.copy(rows = harvested)
            notice = "Kerangka '${sourceModule?.displayName ?: moduleId}' dimuat - sunting seperlunya."
        }
    }

    fun save() {
        scope.launch {
            busy = true
            client.savePrototypePattern(
                name = draft.name.trim(),
                widgetCode = draft.widgetCode,
                packCode = draft.packCode,
                pattern = draft.patternJson(),
                id = draft.id.takeIf { it.isNotBlank() }
            ).onSuccess { body ->
                val saved = (body as? JsonValue.Obj)?.let(PrototypePatternUi::fromJson)
                if (saved == null) {
                    error = "Server tidak mengembalikan pola yang dikenali"
                } else {
                    patterns = patterns.filterNot { it.id == saved.id } + saved
                    draft = saved
                    notice = "Pola '${saved.name}' tersimpan."
                }
            }.onFailure { error = it.message ?: "Gagal menyimpan pola" }
            busy = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Lg)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    "Studio Pola Prototipe",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Pola adalah resep susun layar yang dipakai ulang antar draf prospek. Yang disimpan " +
                        "bentuknya (kolom & blok), bukan isinya.",
                    style = MaterialTheme.typography.labelSmall,
                    color = WeMadeColors.OnSurfaceMuted
                )
            }
            ClayTag(
                text = if (canWrite) "Boleh menyimpan" else "Hanya baca",
                tint = if (canWrite) WeMadeColors.Success else WeMadeColors.OnSurfaceMuted
            )
        }
        if (!canWrite) {
            Text(
                "Menyimpan pola adalah wewenang superadmin platform - server menolak peran lain (403). " +
                    "Isi pola tetap bisa dibaca dan disalin dari sini.",
                style = MaterialTheme.typography.labelSmall,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
        error?.let { message ->
            ClayStatusBanner(message = message, isError = true, onDismiss = { error = null })
        }
        notice?.let { message ->
            ClayStatusBanner(message = message, isError = false, onDismiss = { notice = null })
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Lg),
            verticalAlignment = Alignment.Top
        ) {
            PrototypePatternGallery(
                patterns = patterns,
                selectedId = draft.id.takeIf { !draft.isNew },
                query = query,
                onQueryChange = { query = it },
                onSelect = { selected ->
                    draft = selected
                    sourceModuleId = null
                    moduleQuery = ""
                    notice = "Menyunting pola '${selected.name}'."
                },
                onNew = {
                    draft = PrototypePatternUi()
                    sourceModuleId = null
                    moduleQuery = ""
                    notice = null
                },
                loaded = loaded,
                modifier = Modifier.width(ClayPaneWidth.List)
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (draft.isNew) "Pola baru" else "Menyunting ${draft.id}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        ClayTag(text = "${draft.rows.size} baris", tint = WeMadeColors.OnSurfaceMuted)
                    }
                    ClayTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = "Nama pola",
                        placeholder = "mis. Antrean pasien per poli"
                    )
                    ClayChoiceGroup(
                        label = "Widget",
                        options = WidgetKind.entries.map { it.code },
                        selected = draft.widgetCode,
                        onSelect = { draft = draft.copy(widgetCode = it) },
                        labelOf = { code -> WidgetKind.fromCode(code)?.displayName ?: code }
                    )
                    ClayChoiceGroup(
                        label = "Pack",
                        options = listOf(NO_PACK) + packs.map { it.code.value },
                        selected = draft.packCode ?: NO_PACK,
                        onSelect = { chosen ->
                            draft = draft.copy(packCode = chosen.takeIf { it != NO_PACK })
                            sourceModuleId = null
                        },
                        labelOf = { code ->
                            if (code == NO_PACK) "tanpa pack"
                            else packs.firstOrNull { it.code.value == code }?.displayName ?: code
                        },
                        hint = "Pack menentukan kosakata modul yang boleh dipanen; pola tanpa pack berlaku umum."
                    )
                }

                // Gerbang Rule of Three (plan D5) di titik keputusan widget — lihat DemandSignals.kt.
                WidgetDemandGateCard(canWrite = canWrite, modifier = Modifier.fillMaxWidth())

                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Panen kerangka baris", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Kerangka diambil dari registry sampel yang sama dengan pratinjau draf, jadi pola " +
                            "mulai dari bentuk yang sudah dikenal sistem.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    ClayTextField(
                        value = moduleQuery,
                        onValueChange = { moduleQuery = it },
                        label = "Cari modul",
                        placeholder = if (selectedPack == null) "pilih pack dulu" else "nama modul pack ini",
                        enabled = selectedPack != null
                    )
                    ClayChoiceGroup(
                        label = "Modul asal kerangka",
                        options = shownModules.map { it.id.value },
                        selected = sourceModuleId,
                        onSelect = { sourceModuleId = it },
                        labelOf = { id -> selectedPack?.modules?.firstOrNull { it.id.value == id }?.displayName ?: id }
                    )
                    if (moduleMatches.size > shownModules.size) {
                        Text(
                            "Menampilkan ${shownModules.size} dari ${moduleMatches.size} modul - persempit pencarian.",
                            style = MaterialTheme.typography.labelSmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    }
                    ClayButton(
                        text = if (draft.rows.isEmpty()) "Isi kerangka dari registry" else "Ganti baris dengan kerangka",
                        onClick = ::harvestSkeleton,
                        style = ClayButtonStyle.Secondary
                    )
                }

                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    PrototypeRowEditor(
                        rows = draft.rows,
                        widgetCode = draft.widgetCode,
                        onChange = { draft = draft.copy(rows = it) }
                    )
                }

                ClayCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Pratinjau", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Digambar renderer yang sama dengan pratinjau draf prospek - bukan renderer kedua.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted
                    )
                    if (draft.rows.isEmpty()) {
                        // Pesan renderer untuk baris kosong berbunyi "modulnya tidak ada di pak ini" —
                        // benar untuk draf, menyesatkan di sini: yang kosong adalah pola yang sedang
                        // disusun, bukan draf orang lain.
                        Text(
                            "Pratinjau muncul begitu pola punya baris: panen kerangka dari modul pack, " +
                                "atau tambah baris manual di atas.",
                            style = MaterialTheme.typography.bodySmall,
                            color = WeMadeColors.OnSurfaceMuted
                        )
                    } else {
                        PrototypeRenderer(
                            draft = draft.toPreviewDraft(previewModuleId, previewModuleName, previewSection)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ClayButton(
                        text = if (busy) "Menyimpan..." else "Simpan pola",
                        onClick = ::save,
                        enabled = canWrite && !busy && draft.name.isNotBlank() && draft.rows.isNotEmpty(),
                        style = ClayButtonStyle.Accent
                    )
                    ClayButton(
                        text = "Bersihkan",
                        onClick = {
                            draft = PrototypePatternUi()
                            sourceModuleId = null
                            moduleQuery = ""
                        },
                        style = ClayButtonStyle.Secondary
                    )
                    Text(
                        "Nama wajib diisi & minimal satu baris - pola tanpa bentuk tidak berguna saat dipakai.",
                        style = MaterialTheme.typography.labelSmall,
                        color = WeMadeColors.OnSurfaceMuted,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

/** Penanda "tanpa pack": `null` tidak bisa menjadi nilai terpilih sebuah pil. */
private const val NO_PACK = "__none__"

/** Modul netral untuk pratinjau pola yang barisnya belum pernah dipanen dari modul mana pun. */
private const val PREVIEW_MODULE_ID = "pattern_module"

/** Modul ditampilkan bertahap: pack garment punya 40+ modul dan daftar pil sepanjang itu menenggelamkan penyunting. */
private const val MODULE_CHIPS = 12
