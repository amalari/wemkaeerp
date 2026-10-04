package com.eventverse.app.presentation.discovery

import androidx.compose.runtime.Stable
import com.eventverse.app.domain.prototype.DashboardEvaluator
import com.eventverse.app.domain.prototype.InteractiveScreen
import com.eventverse.app.domain.prototype.PrototypeRow

/**
 * Dasbor prototype: ubinnya dihitung [DashboardEvaluator] dari baris layar lain lewat [rowsOf]
 * (sesi). Karena baris itu state Compose, angka dasbor ikut berubah saat kartu dipindah.
 */
@Stable
class InteractiveDashboardState(screen: InteractiveScreen, private val rowsOf: (String) -> List<PrototypeRow>?) : PlayableState {
    private val config = requireNotNull(screen.spec.screens.firstOrNull()?.dashboard) { "Layar bukan dasbor" }

    override val rows: List<PrototypeRow>? = null

    /** Pasangan label → nilai terkini; dibaca di komposisi, jadi bereaksi pada perubahan sumber. */
    fun tiles(): List<Pair<String, String>> = config.tiles.map { it.label to DashboardEvaluator.valueOf(it, rowsOf) }
}
