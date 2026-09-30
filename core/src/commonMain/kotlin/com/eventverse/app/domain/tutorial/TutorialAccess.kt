package com.eventverse.app.domain.tutorial

import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.AccessDecision

/**
 * Gerbang baca tutorial (TRD-HELP-001 FR-2). **Fail-closed**: modul tanpa keputusan RBAC dan layar yang tidak
 * disebut di [allowedSurfaces] dianggap tertutup — tutorial modul yang tidak di-entitle tidak boleh membocorkan
 * keberadaannya, termasuk ke AI helper.
 */
object TutorialAccess {

    fun accessible(
        tutorials: List<ModuleTutorial>,
        decisions: Map<ModuleId, AccessDecision>,
        allowedSurfaces: Set<SurfaceCode> = emptySet(),
    ): List<ModuleTutorial> = tutorials.filter { it.isVisible(decisions, allowedSurfaces) }

    private fun ModuleTutorial.isVisible(decisions: Map<ModuleId, AccessDecision>, allowedSurfaces: Set<SurfaceCode>): Boolean =
        when (val s = scope) {
            is TutorialScope.Module -> decisions[s.moduleId]?.config?.level?.isAtLeast(requiredLevel) == true
            is TutorialScope.Surface -> s.code in allowedSurfaces
        }
}
