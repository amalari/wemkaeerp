package com.eventverse.app.presentation.help

import com.eventverse.app.domain.help.HelpAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kotak surat aksi dari AI helper ke layar modul (TRD-HELP-002 Fase 5b). Chat menaruh aksi, lalu navigasi ke
 * `action.module`; layar modul yang **mengambil** ([take]) aksi miliknya — sekali pakai, supaya membuka ulang layar
 * tidak memicu form lagi. Arah ketergantungan: modul → help, tidak pernah help → modul.
 */
object HelpActionRequests {
    private val state = MutableStateFlow<HelpAction?>(null)
    val pending: StateFlow<HelpAction?> = state.asStateFlow()

    fun post(action: HelpAction) { state.value = action }

    /** Mengambil aksi bila jenisnya [T]; aksi lain dibiarkan untuk layarnya sendiri. */
    inline fun <reified T : HelpAction> take(): T? = (pending.value as? T)?.also { clear() }

    fun clear() { state.value = null }
}
