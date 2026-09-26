package com.eventverse.app.domain.sampling

import com.eventverse.app.domain.rbac.AccessLevel
import com.eventverse.app.domain.rbac.ModuleAccessConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OperatorDeskAccessTest {

    private val deptAll = ModuleAccessConfig(AccessLevel.OPERATE)
    private val deptQcOnly = ModuleAccessConfig(
        AccessLevel.OPERATE,
        allowedDesks = setOf("QC_FINISHING")
    )
    private val deptNone = ModuleAccessConfig(AccessLevel.NONE)

    @Test
    fun `resolve desks when bypass should open all desks`() {
        assertNull(resolveAccessibleOperatorDesks(bypass = true, departmentAccess = deptQcOnly))
    }

    @Test
    fun `resolve desks when department unrestricted should open all desks`() {
        assertNull(resolveAccessibleOperatorDesks(bypass = false, departmentAccess = deptAll))
    }

    @Test
    fun `resolve desks when department axis closed should not restrict desks`() {
        assertNull(resolveAccessibleOperatorDesks(bypass = false, departmentAccess = deptNone))
    }

    @Test
    fun `resolve desks when department restricts should return only allowed desks`() {
        val desks = resolveAccessibleOperatorDesks(bypass = false, departmentAccess = deptQcOnly)
        assertEquals(setOf(SamplingPipelineStage.QC_FINISHING), desks)
    }

    @Test
    fun `resolve desks when allowed code unknown should ignore the unknown code`() {
        val dept = ModuleAccessConfig(AccessLevel.OPERATE, allowedDesks = setOf("NEW_INTAKE", "QC_FINISHING"))
        val desks = resolveAccessibleOperatorDesks(bypass = false, departmentAccess = dept)!!

        assertEquals(setOf(SamplingPipelineStage.QC_FINISHING), desks)
    }

    @Test
    fun `to allowed operator desks when null should stay null`() {
        assertNull(null.toAllowedOperatorDesks())
    }
}