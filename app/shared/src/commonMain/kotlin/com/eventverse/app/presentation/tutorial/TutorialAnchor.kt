package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import com.eventverse.app.domain.tutorial.TutorialAnchorId

/**
 * Posisi (koordinat window) setiap elemen yang bisa disorot coach mark, beserta cara menggulirnya ke layar.
 * Reaktif: overlay ikut bergeser bila layout berubah selama tutorial berjalan.
 */
class TutorialAnchorRegistry {
    private val bounds = mutableStateMapOf<TutorialAnchorId, Rect>()
    private val requesters = mutableMapOf<TutorialAnchorId, BringIntoViewRequester>()

    fun boundsOf(id: TutorialAnchorId): Rect? = bounds[id]

    suspend fun bringIntoView(id: TutorialAnchorId) {
        requesters[id]?.bringIntoView()
    }

    internal fun attach(id: TutorialAnchorId, requester: BringIntoViewRequester) { requesters[id] = requester }
    internal fun update(id: TutorialAnchorId, rect: Rect) { if (bounds[id] != rect) bounds[id] = rect }
    internal fun detach(id: TutorialAnchorId) { bounds.remove(id); requesters.remove(id) }
}

/** `null` di luar `App` (preview, test) — anchor lalu tidak melakukan apa pun. */
val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchorRegistry?> { null }

/**
 * Menandai elemen ini sebagai titik sorot [id]. Pakai hanya konstanta anchor pack (`GarmentTutorialAnchors`),
 * bukan string literal — test katalog hanya bisa menjaga anchor yang terdaftar.
 */
fun Modifier.tutorialAnchor(id: TutorialAnchorId): Modifier = composed {
    val registry = LocalTutorialAnchors.current ?: return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(registry, id) {
        registry.attach(id, requester)
        onDispose { registry.detach(id) }
    }
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { if (it.isAttached) registry.update(id, it.boundsInWindow()) }
}
