package com.eventverse.app.domain.discovery.handoff

import com.eventverse.app.domain.pack.DomainPackRegistry
import com.eventverse.app.domain.pack.ModuleId
import com.eventverse.app.domain.rbac.ScopeCapability

/**
 * Kebijakan v1 target RELATION lintas modul (TRD-FIELD-004 Q3, disetujui): modul hasil generate **belum** boleh
 * merujuk modul `HIERARCHICAL` (mis. `crm_sales`). Gerbang target (VIEW + DataScope pemanggil atas modul target,
 * FR-3.x) belum ada di rute generate, jadi rujukan seperti itu membuka oracle keberadaan di luar jangkauan data.
 * Ditolak **di build** (generator dan validator usulan), bukan diam-diam di server. Dicabut bila FR-3.x selesai.
 *
 * Modul yang tidak dikenal registri tidak dinilai di sini: resolver server fail-closed untuknya, dan
 * keberadaannya di pack dijaga validator usulan (`packModuleIds`).
 */
internal object RelationTargetPolicy {

    /** Pesan galat bila [targetModuleCode] = modul HIERARCHICAL; `null` bila boleh / tak dikenal. */
    fun hierarchicalTargetProblem(targetModuleCode: String, fieldKey: String): String? {
        val scope = DomainPackRegistry.moduleDefinition(ModuleId(targetModuleCode))?.scopeCapability ?: return null
        return if (scope == ScopeCapability.HIERARCHICAL) {
            "Field RELATION '$fieldKey' menunjuk modul HIERARCHICAL '$targetModuleCode'; rujukan ke modul hierarkis " +
                "belum didukung modul hasil generate (menunggu gerbang target FR-3.x TRD-FIELD-004)."
        } else null
    }
}
