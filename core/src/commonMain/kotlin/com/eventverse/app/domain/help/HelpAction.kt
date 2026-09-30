package com.eventverse.app.domain.help

import com.eventverse.app.domain.pack.ModuleId

/**
 * Aksi yang ditawarkan AI helper selain tutorial (TRD-HELP-002 Fase 5b). Menawarkan, **bukan** menjalankan:
 * klien membuka layar [module] dan user yang meneruskan. Gerbang layar & endpoint tujuan tetap berlaku.
 */
sealed interface HelpAction {
    val module: ModuleId

    /** Buka form lead baru di [module] dengan [text] tertempel untuk draf AI. */
    data class PrefillLead(override val module: ModuleId, val text: String) : HelpAction
}

/**
 * Penentu aksi untuk satu pertanyaan. Dibangun route **setelah** wewenang & opt-in diperiksa — use case tidak
 * memutuskan akses. `null` = pertanyaan biasa (tutorial).
 */
fun interface HelpActionResolver {
    fun resolve(question: String): HelpAction?
}
