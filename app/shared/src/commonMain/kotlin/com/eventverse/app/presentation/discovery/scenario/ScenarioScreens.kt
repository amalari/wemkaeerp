package com.eventverse.app.presentation.discovery.scenario

import com.eventverse.app.domain.discovery.scenario.ScenarioSketch
import com.eventverse.app.domain.discovery.scenario.ScenarioSource
import com.eventverse.app.presentation.discovery.DiscoveryDraftUi
import com.eventverse.app.presentation.discovery.DiscoveryScreenUi

/** Awalan `screenId` kerangka: layar sintetis yang hanya hidup di pemutar, tidak ada di draf. */
internal const val SKELETON_SCREEN_PREFIX = "scenario-skeleton:"

/** Skenario terkonfirmasi saja yang boleh jadi spesifikasi (FR-8); dipakai tab Alur Data dan ekspor. */
fun confirmedScenarios(scenarios: List<ScenarioSketch>): List<ScenarioSketch> = scenarios.filter { it.confirmed }

/** Apakah [screen] hanya kerangka sintetis pemutar (bukan layar draf). */
fun isSkeletonScreen(screen: DiscoveryScreenUi): Boolean = screen.screenId.startsWith(SKELETON_SCREEN_PREFIX)

/**
 * Daftar layar yang diberikan ke navigator saat [scenario] dipilih: **satu entri per langkah, menurut urutan skenario**
 * (bukan urutan daftar layar draf). Judul layar diganti label langkah supaya tombol "Lanjut Alur: <label>" jujur.
 *
 * Langkah tanpa layar (`screenId == null`) **atau** yang menunjuk layar yang tidak ada di draf menjadi layar kerangka
 * non-interaktif (`CUSTOM_SCREEN`, pola SkeletonBlock) dengan alasan tertulis - tidak dipalsukan menjadi layar teks atau
 * layar modul lain.
 */
fun scenarioScreens(scenario: ScenarioSketch, screens: List<DiscoveryScreenUi>): List<DiscoveryScreenUi> =
    scenario.steps.mapIndexed { i, step ->
        val real = step.screenId?.let { id -> screens.firstOrNull { it.screenId == id } }
        real?.copy(title = step.label) ?: DiscoveryScreenUi(
            screenId = "$SKELETON_SCREEN_PREFIX${scenario.id.value}:$i",
            moduleId = step.moduleId.value,
            title = step.label,
            widget = "CUSTOM_SCREEN",
            sampleRows = listOf(
                mapOf("Blok" to step.label, "Lebar" to "penuh", "Petunjuk" to "formulir"),
                mapOf("Blok" to "Ringkasan", "Lebar" to "separuh", "Petunjuk" to "kartu angka"),
                mapOf("Blok" to "Aksi", "Lebar" to "separuh", "Petunjuk" to "aksi")
            ),
            rationale = if (step.screenId == null) "Langkah ini belum punya layar prototype: ini kerangka, bukan layar sungguhan."
            else "Layar langkah ini tidak ditemukan di draf: ditampilkan sebagai kerangka."
        )
    }

/** Teks lencana sumber skenario (ASCII; tampil jujur siapa yang menyusun). */
fun scenarioSourceLabel(source: ScenarioSource): String = when (source) {
    ScenarioSource.AI -> "AI"
    ScenarioSource.DETERMINISTIC -> "DETERMINISTIC"
    ScenarioSource.USER -> "USER"
}

fun scenarioStatusLabel(scenario: ScenarioSketch): String = if (scenario.confirmed) "Terkonfirmasi" else "Diusulkan"

/** Satu simpul rantai skenario di tab Alur Data. */
data class ScenarioChainStep(val moduleName: String, val label: String, val skeleton: Boolean)

/** Rantai satu skenario terkonfirmasi untuk tab Alur Data (tingkat kedua). */
data class ScenarioChain(val id: String, val title: String, val source: ScenarioSource, val steps: List<ScenarioChainStep>)

/**
 * Rantai Alur Data tingkat skenario: **hanya skenario terkonfirmasi**. Nama modul dari draf (`displayName`), jatuh ke kode
 * modul bila modul tak ada di draf - tidak mengarang nama.
 */
fun scenarioChains(draft: DiscoveryDraftUi): List<ScenarioChain> =
    confirmedScenarios(draft.scenarios).map { sc ->
        ScenarioChain(
            id = sc.id.value,
            title = sc.title,
            source = sc.source,
            steps = sc.steps.map { st ->
                ScenarioChainStep(
                    moduleName = draft.modules.firstOrNull { it.id == st.moduleId.value }?.displayName?.ifBlank { null } ?: st.moduleId.value,
                    label = st.label,
                    skeleton = st.screenId == null
                )
            }
        )
    }
