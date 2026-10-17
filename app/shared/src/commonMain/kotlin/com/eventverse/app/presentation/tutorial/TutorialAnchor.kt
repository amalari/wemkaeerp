package com.eventverse.app.presentation.tutorial

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
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

    /**
     * Sinyal "anchor diketuk" (FR-3) + apakah sedang ada yang mendengarkan. `TutorialLayer` menyalakannya hanya
     * selama langkah aktif menunggu [com.eventverse.app.domain.tutorial.AdvanceTrigger.UserClick], sehingga elemen
     * yang di-anchor **tidak** memasang observer pointer permanen di luar tutorial.
     */
    var onAction: (TutorialAnchorId) -> Unit = {}
    var isObserving by mutableStateOf(false)
        private set

    fun beginObserving(onAction: (TutorialAnchorId) -> Unit) {
        this.onAction = onAction
        isObserving = true
    }

    fun endObserving() {
        isObserving = false
        onAction = {}
    }

    fun boundsOf(id: TutorialAnchorId): Rect? = bounds[id]

    suspend fun bringIntoView(id: TutorialAnchorId) {
        requesters[id]?.bringIntoView()
    }

    internal fun attach(id: TutorialAnchorId, requester: BringIntoViewRequester) { requesters[id] = requester }
    internal fun update(id: TutorialAnchorId, rect: Rect) { if (bounds[id] != rect) bounds[id] = rect }
    internal fun detach(id: TutorialAnchorId) { bounds.remove(id); requesters.remove(id) }
    internal fun notifyAction(id: TutorialAnchorId) = onAction(id)
}

/** `null` di luar `App` (preview, test) — anchor lalu tidak melakukan apa pun. */
val LocalTutorialAnchors = staticCompositionLocalOf<TutorialAnchorRegistry?> { null }

/**
 * Menandai elemen ini sebagai titik sorot [id]. Pakai hanya konstanta anchor pack (`GarmentTutorialAnchors`),
 * bukan string literal — test katalog hanya bisa menjaga anchor yang terdaftar.
 *
 * [onAction] (opsional, TRD-TUT-002 FR-3): dipanggil saat elemen ini menerima ketukan. `null` (default) = sinyal
 * diteruskan ke `TutorialAnchorRegistry.onAction` yang dipasang `TutorialLayer` — jadi SEMUA pemanggil lama tanpa
 * argumen tetap melaporkan aksi. Observasi terjadi di `PointerEventPass.Initial` **tanpa consume** — `onClick` asli
 * elemen tetap berjalan, sinyal ini hanya efek samping (mis. `TutorialController.notifyAction`). Sinyal diabaikan
 * kecuali langkah aktif menunggu anchor ini.
 */
fun Modifier.tutorialAnchor(
    id: TutorialAnchorId,
    onAction: ((TutorialAnchorId) -> Unit)? = null,
): Modifier = composed {
    val registry = LocalTutorialAnchors.current ?: return@composed Modifier
    val requester = remember { BringIntoViewRequester() }
    DisposableEffect(registry, id) {
        registry.attach(id, requester)
        onDispose { registry.detach(id) }
    }
    val report = onAction ?: registry::notifyAction
    // Observer pointer hanya terpasang selatih tutorial mendengarkan (isObserving) — di luar itu elemen bebas.
    val observing = onAction != null || registry.isObserving
    Modifier
        .bringIntoViewRequester(requester)
        .onGloballyPositioned { if (it.isAttached) registry.update(id, it.boundsInWindow()) }
        .then(
            if (!observing) Modifier
            else Modifier.pointerInput(id, report) {
                // Initial pass: sinyal terkirim SEBELUM handler elemen (Main) — tidak consume, jadi onClick asli aman.
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.changedToDown() }) report(id)
                    }
                }
            }
        )
}
