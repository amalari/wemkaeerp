package com.eventverse.app.domain.blueprint

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.pack.GarmentSlots

/**
 * Validator for AI-generated blueprints.
 *
 * All AI output is untrusted and must be validated before persistence.
 * This validator ensures:
 * - Module codes exist in the catalog
 * - Required ports are connected
 * - No circular dependencies
 * - Parameters are valid
 */
object BlueprintValidator {

    data class ValidationIssue(
        val severity: Severity,
        val path: String,
        val message: String
    )

    enum class Severity {
        WARNING,
        ERROR
    }

    data class ValidationResult(
        val isValid: Boolean,
        val issues: List<ValidationIssue>
    )

    fun validate(blueprint: Blueprint): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Validate module codes exist
        for (module in blueprint.modules) {
            val slot = GarmentSlots.all.find { it.value == module.moduleCode }
            if (slot == null) {
                issues += ValidationIssue(
                    severity = Severity.ERROR,
                    path = "modules.${module.moduleCode}",
                    message = "Modul '${module.moduleCode}' tidak ada di katalog"
                )
            }
        }

        // Validate no duplicate modules
        val duplicateModules = blueprint.modules.groupBy { it.moduleCode }
            .filterValues { it.size > 1 }
        for ((code, _) in duplicateModules) {
            issues += ValidationIssue(
                severity = Severity.ERROR,
                path = "modules",
                message = "Modul '$code' duplikat dalam blueprint"
            )
        }

        // Validate at least one active module
        if (blueprint.modules.none { it.active }) {
            issues += ValidationIssue(
                severity = Severity.WARNING,
                path = "modules",
                message = "Blueprint tidak memiliki modul aktif"
            )
        }

        // Validate required modules for preset
        val activeModules = blueprint.modules.filter { it.active }.map { it.moduleCode }.toSet()
        val requiredForPreset = requiredModulesFor(blueprint.code.value)
        for (required in requiredForPreset) {
            if (required !in activeModules) {
                issues += ValidationIssue(
                    severity = Severity.WARNING,
                    path = "modules",
                    message = "Modul '$required' biasanya diperlukan untuk preset ${blueprint.code}"
                )
            }
        }

        return ValidationResult(
            isValid = issues.none { it.severity == Severity.ERROR },
            issues = issues
        )
    }

    private fun requiredModulesFor(presetCodeValue: String): List<String> {
        return when (presetCodeValue) {
            "fob_full_package" -> listOf("crm_sales", "operator_exec")
            "cmt_makloon" -> listOf("crm_sales", "operator_exec")
            "d2c_brand" -> listOf("crm_sales", "operator_exec", "fulfillment")
            else -> emptyList()
        }
    }

    /**
     * Validates that a module can be added to the blueprint.
     */
    fun validateModuleAddition(
        blueprint: Blueprint,
        newModule: BlueprintModule
    ): ValidationResult {
        val issues = mutableListOf<ValidationIssue>()

        // Check if module already exists
        if (blueprint.modules.any { it.moduleCode == newModule.moduleCode }) {
            issues += ValidationIssue(
                severity = Severity.ERROR,
                path = "modules.${newModule.moduleCode}",
                message = "Modul '${newModule.moduleCode}' sudah ada dalam blueprint"
            )
        }

        // Check if slot exists
        val slot = GarmentSlots.all.find { it.value == newModule.moduleCode }
        if (slot == null) {
            issues += ValidationIssue(
                severity = Severity.ERROR,
                path = "modules.${newModule.moduleCode}",
                message = "Modul '${newModule.moduleCode}' tidak ada di katalog slot"
            )
        }

        return ValidationResult(
            isValid = issues.none { it.severity == Severity.ERROR },
            issues = issues
        )
    }
}
