package com.eventverse.app.domain.orgchart

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DepartmentTest {

    @Test
    fun defaultPresets_containsStandardGarmentDepartments() {
        val presets = Department.defaultPresets()
        assertEquals(5, presets.size)

        val codes = presets.map { it.code }
        assertTrue(codes.contains("sales"))
        assertTrue(codes.contains("production_ppic"))
        assertTrue(codes.contains("warehouse"))
        assertTrue(codes.contains("qc"))
        assertTrue(codes.contains("finance_executive"))
    }

    @Test
    fun createCustom_generatesValidDepartmentWithSlugAndIsCustomFlag() {
        val customDept = Department.createCustom(
            name = "Bordir & Sablon Printing",
            shortName = "Bordir",
            colorHex = 0xFFEC4899
        )

        assertEquals("Bordir & Sablon Printing", customDept.displayName)
        assertEquals("Bordir", customDept.shortName)
        assertEquals(0xFFEC4899, customDept.colorHex)
        assertEquals("bordir", customDept.code)
        assertTrue(customDept.isCustom)
        assertTrue(customDept.id.value.startsWith("dept-bordir-"))
    }

    @Test
    fun availableColors_filtersOutAlreadyUsedColors() {
        val presets = Department.defaultPresets()
        val available = Department.availableColors(presets)

        // The 5 preset colors must NOT be in available colors
        val presetHexes = presets.map { it.colorHex }.toSet()
        val availableHexes = available.map { it.hex }.toSet()

        presetHexes.forEach { usedHex ->
            assertFalse(availableHexes.contains(usedHex), "Used color $usedHex should not appear in available colors")
        }

        // Available count should be total minus 5
        assertEquals(Department.AVAILABLE_COLORS.size - 5, available.size)
    }

    @Test
    fun availableColors_whenEmptyDepartments_returnsAllColors() {
        val available = Department.availableColors(emptyList())
        assertEquals(Department.AVAILABLE_COLORS.size, available.size)
    }

    @Test
    fun departmentId_blankThrowsException() {
        assertFailsWith<IllegalArgumentException> {
            DepartmentId("   ")
        }
    }
}
