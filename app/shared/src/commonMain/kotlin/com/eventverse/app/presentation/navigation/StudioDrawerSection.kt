package com.eventverse.app.presentation.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.eventverse.app.presentation.designsystem.ClayNavItem
import com.eventverse.app.presentation.designsystem.ClayNavSection
import com.eventverse.app.presentation.designsystem.IconEdit
import com.eventverse.app.presentation.designsystem.IconLayers

/**
 * Section drawer **Studio**: funnel discovery (Fase D) dan Studio pola prototype (Fase C).
 *
 * Dua-duanya sengaja tidak lewat `buildNavMenu`, karena keduanya **bukan** `BusinessModule`: datanya
 * milik platform — draf prospek per pengguna dan resep layout internal — bukan aset tenant yang
 * di-RBAC per jabatan, jadi keduanya tidak masuk matriks modul maupun kuota paket.
 *
 * Dipisah dari `App.kt` karena ia satu kesatuan yang jelas (dua baris + alasan gerbangnya), bukan
 * karena `App.kt` perlu dipecah: shell aplikasi tumbuh setiap kali ada layar platform baru, dan
 * bagian yang berdiri sendiri seperti ini adalah tempat termurah untuk menahannya.
 */
fun studioDrawerSection(
    currentScreen: AppNavScreen,
    showDemandLedger: Boolean = false,
    onOpen: (AppNavScreen) -> Unit
): ClayNavSection = ClayNavSection(
    title = "Studio",
    items = buildList {
        add(
            ClayNavItem(
            key = AppNavScreen.DISCOVERY.route,
            label = AppNavScreen.DISCOVERY.title,
            selected = currentScreen == AppNavScreen.DISCOVERY,
            onClick = { onOpen(AppNavScreen.DISCOVERY) },
            icon = { tint -> IconLayers(modifier = Modifier.fillMaxSize(), color = tint) }
            )
        )
        add(
            ClayNavItem(
            key = AppNavScreen.DISCOVERY_STUDIO.route,
            label = AppNavScreen.DISCOVERY_STUDIO.title,
            selected = currentScreen == AppNavScreen.DISCOVERY_STUDIO,
            onClick = { onOpen(AppNavScreen.DISCOVERY_STUDIO) },
            icon = { tint -> IconEdit(modifier = Modifier.fillMaxSize(), color = tint) }
            )
        )
        if (showDemandLedger) {
            add(
                ClayNavItem(
                key = AppNavScreen.DISCOVERY_DEMANDS.route,
                label = AppNavScreen.DISCOVERY_DEMANDS.title,
                selected = currentScreen == AppNavScreen.DISCOVERY_DEMANDS,
                onClick = { onOpen(AppNavScreen.DISCOVERY_DEMANDS) },
                icon = { tint -> IconLayers(modifier = Modifier.fillMaxSize(), color = tint) }
                )
            )
        }
    }
)
