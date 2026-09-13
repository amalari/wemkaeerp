package com.eventverse.app.presentation.module

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.eventverse.app.presentation.designsystem.IconActivity
import com.eventverse.app.presentation.designsystem.IconCalculator
import com.eventverse.app.presentation.designsystem.IconCalendarGrid
import com.eventverse.app.presentation.designsystem.IconCheckCircle
import com.eventverse.app.presentation.designsystem.IconClipboard
import com.eventverse.app.presentation.designsystem.IconHandshake
import com.eventverse.app.presentation.designsystem.IconLayers
import com.eventverse.app.presentation.designsystem.IconPackage
import com.eventverse.app.presentation.designsystem.IconRuler
import com.eventverse.app.presentation.designsystem.IconShield
import com.eventverse.app.presentation.designsystem.IconTruck
import com.eventverse.app.presentation.pipeline.components.IconFlowGraph
import com.eventverse.app.presentation.pipeline.components.IconUsers

/**
 * Memetakan `BusinessModule.iconKey` ke ikon Canvas di katalog clay.
 *
 * Tinggal di lapisan fitur, bukan di `designsystem/`: kosakata kuncinya (`"handshake"`,
 * `"check_circle"`, …) berasal dari domain, dan Kontrak 6 melarang design system mengenalnya.
 *
 * Dipakai bersama oleh kartu modul RBAC dan drawer navigasi — sebelumnya `when` ini `private` di
 * `ModuleCardView.kt`, dan menyalinnya untuk pemakaian kedua persis pola yang dilarang Kontrak 4.
 *
 * Menerima `iconKey: String`, bukan `BusinessModule`, supaya pemanggil yang hanya memegang
 * konfigurasi tenant tetap bisa memakainya.
 */
@Composable
fun ModuleIcon(iconKey: String, modifier: Modifier = Modifier, color: Color) {
    when (iconKey) {
        "handshake" -> IconHandshake(modifier = modifier, color = color)
        "ruler" -> IconRuler(modifier = modifier, color = color)
        "package" -> IconPackage(modifier = modifier, color = color)
        "clipboard" -> IconClipboard(modifier = modifier, color = color)
        "calculator" -> IconCalculator(modifier = modifier, color = color)
        "calendar" -> IconCalendarGrid(modifier = modifier, color = color)
        "activity" -> IconActivity(modifier = modifier, color = color)
        "check_circle" -> IconCheckCircle(modifier = modifier, color = color)
        "truck" -> IconTruck(modifier = modifier, color = color)
        // Modul tata kelola. Ikonnya dulu dipilih lewat `AdminScreenIcon` di App.kt berdasarkan
        // AppNavScreen; setelah ketiganya menjadi modul, sumber ikonnya menyatu di sini bersama
        // yang lain.
        "users" -> IconUsers(modifier = modifier, color = color)
        "shield" -> IconShield(modifier = modifier, color = color)
        "flow_graph" -> IconFlowGraph(modifier = modifier, color = color)
        else -> IconLayers(modifier = modifier, color = color)
    }
}
