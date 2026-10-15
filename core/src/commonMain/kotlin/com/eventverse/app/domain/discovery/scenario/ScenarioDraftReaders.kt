package com.eventverse.app.domain.discovery.scenario

import com.eventverse.app.domain.discovery.DiscoveryDraft

/**
 * Satu-satunya pembaca skenario untuk ekspor brief/handoff (FR-8): hanya yang `confirmed`. Tebakan AI/deterministik tidak
 * menjadi spesifikasi sebelum pengguna menekan Konfirmasi. Titik ekspor yang ada belum menyebut skenario; pembaca baru
 * WAJIB lewat fungsi ini (dikunci tes).
 */
fun DiscoveryDraft.confirmedScenarios(): List<ScenarioSketch> = scenarios.filter { it.confirmed }
