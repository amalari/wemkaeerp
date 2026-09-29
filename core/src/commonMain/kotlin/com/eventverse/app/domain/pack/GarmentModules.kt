package com.eventverse.app.domain.pack

import com.eventverse.app.domain.rbac.BusinessModule
import com.eventverse.app.domain.rbac.ModuleCategory

/**
 * Modul & seksi menu pack konveksi. **B6a**: dibangun dari `enum BusinessModule` / `ModuleCategory` supaya identik
 * secara konstruksi; tabel emas di `GarmentModulesParityTest` membekukannya sebelum pembaca dipindah (B6c–B6f).
 */
object GarmentModules {

    val sections: List<ModuleSection> by lazy {
        ModuleCategory.entries.mapIndexed { i, c -> ModuleSection(ModuleSectionCode(c.name), c.displayName, i + 1) }
    }

    val modules: List<ModuleDefinition> by lazy {
        BusinessModule.entries.map { m ->
            ModuleDefinition(
                id = ModuleId(m.code),
                displayName = m.displayName,
                description = m.description,
                section = ModuleSectionCode(m.category.name),
                kind = m.kind,
                iconKey = m.iconKey,
                scopeCapability = m.scopeCapability,
                supportedScopes = m.supportedScopes,
                slot = GarmentSlots.forModule(m)
            )
        }
    }
}
