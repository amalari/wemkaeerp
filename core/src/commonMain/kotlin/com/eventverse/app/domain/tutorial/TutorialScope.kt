package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId

/**
 * Tempat sebuah tutorial berlaku, sekaligus penentu gerbang aksesnya (TRD-HELP-001 FR-2).
 *
 * Uji Variabilitas: dua jenis ini milik sistem (cara RBAC diputuskan), bukan konsep tenant → sealed, bukan data.
 */
sealed interface TutorialScope {
    /** Tutorial modul: terlihat bila keputusan RBAC modul ≥ `requiredLevel`. */
    data class Module(val moduleId: ModuleId) : TutorialScope

    /** Tutorial layar non-modul (Builder, Discovery): terlihat bila layar itu diizinkan untuk pemanggil. */
    data class Surface(val code: SurfaceCode) : TutorialScope
}
