package com.eventverse.app.domain.customfield

import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import kotlin.jvm.JvmInline

/**
 * Identifies one tenant-defined custom field, shared across every operational module via
 * `custom_field_definitions.owner_resource` (see V20).
 *
 * This is the key stored inside a consuming entity's `custom_attributes` JSONB blob — never
 * the field's [label]. That single rule is what makes renaming a field free: a rename
 * touches exactly one row in `custom_field_definitions` and zero rows of entity data.
 */
@JvmInline
value class CustomFieldId(val value: String) {
    init {
        require(value.isNotBlank()) { "CustomFieldId cannot be blank" }
        require(value.length <= 64) { "CustomFieldId must be at most 64 characters" }
    }
}

/**
 * The module (or tenant plugin) a custom field belongs to.
 *
 * A plain string, not `BusinessModule`, deliberately: it is the same string space as
 * `ModuleCatalogEntry.moduleId`, `CustomPipelineNode.moduleId` and
 * `granted_custom_module_ids` (e.g. `"sablon_bordir_custom"`). `BusinessModule` is a hard
 * enum that cannot grow without a deploy — keying the newest extensibility mechanism in the
 * system to that enum would bake its limitation into this table too.
 */
@JvmInline
value class OwnerResource(val value: String) {
    init {
        require(value.isNotBlank()) { "OwnerResource cannot be blank" }
        require(value.length <= 64) { "OwnerResource must be at most 64 characters" }
    }

    companion object {
        /** The CRM sales owner resource. */
        val CRM_SALES = OwnerResource("crm_sales")
        /** Master data material owner resource for tenant custom attributes. */
        val MASTER_DATA_MATERIAL = OwnerResource("master_data_material")
    }
}

/**
 * Immutable slug derived from a field's first label, e.g. `"jenis_sablon"`.
 *
 * Exists only for a human-readable, stable API/debugging handle — [CustomFieldId] remains
 * the actual storage key. Never regenerated after creation: doing so on rename would be
 * exactly the mistake this whole design avoids for [CustomFieldId].
 */
@JvmInline
value class FieldKey(val value: String) {
    init {
        require(value.isNotBlank()) { "FieldKey cannot be blank" }
        require(value.length <= 64) { "FieldKey must be at most 64 characters" }
        require(KEY_REGEX.matches(value)) {
            "FieldKey must be lowercase snake_case: $value"
        }
    }

    companion object {
        private val KEY_REGEX = Regex("^[a-z][a-z0-9_]*$")

        /** Slugifies a label into a valid key, deduplicating against [existing] if needed. */
        fun fromLabel(label: String, existing: Set<String> = emptySet()): FieldKey {
            val base = label.trim().lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')
                .ifBlank { "field" }
                .let { if (it.first().isDigit()) "f_$it" else it }
                .take(56)

            var candidate = base
            var suffix = 2
            while (candidate in existing) {
                candidate = "${base}_$suffix"
                suffix++
            }
            return FieldKey(candidate)
        }
    }
}

/** A single selectable value of an ENUM ([com.eventverse.app.domain.prototype.FieldType.ENUM]) / MULTI_SELECT field. */
@JvmInline
value class SelectOptionId(val value: String) {
    init {
        require(value.isNotBlank()) { "SelectOptionId cannot be blank" }
        require(value.length <= 64) { "SelectOptionId must be at most 64 characters" }
    }
}
