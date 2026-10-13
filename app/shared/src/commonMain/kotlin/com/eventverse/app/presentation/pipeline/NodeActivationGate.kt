package com.eventverse.app.presentation.pipeline

import com.eventverse.app.domain.pipeline.TenantModuleCatalogSnapshot

/**
 * Apakah tombol "Aktivasi" boleh tampil untuk [moduleId] di kanvas (PLAN A2).
 *
 * Hanya cermin dari katalog tenant — **server tetap penegak** (`entitlement.validate` → 400). Modul yang tak ada di
 * katalog, tak termasuk paket, atau saat kuota paket sudah penuh tidak mendapat tombol.
 */
fun TenantModuleCatalogSnapshot.canActivateFromCanvas(moduleId: String): Boolean =
    modules.firstOrNull { it.moduleId == moduleId }
        ?.let { it.isGrantedByPlan && !hasReachedPlanLimit }
        ?: false
