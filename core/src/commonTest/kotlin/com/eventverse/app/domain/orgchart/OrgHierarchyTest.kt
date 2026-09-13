package com.eventverse.app.domain.orgchart

import kotlin.test.*

class OrgHierarchyTest {

    private val sampleEmployees = OrgNode.createSampleEmployees()

    @Test
    fun head_of_department_t_shape_resolution_should_match_specification() {
        val budi = sampleEmployees.first { it.id.value == "emp-budi" }

        val result = OrgNode.resolveTShapeView(sampleEmployees, budi)

        // 1. Superior must be 1 level up (Direktur Hendra)
        assertNotNull(result.superior)
        assertEquals("emp-hendra", result.superior.id.value)
        assertEquals(HierarchyLevel.EXECUTIVE, result.superior.level)

        // 2. Peer Heads must contain Joko, Siti, and Anton (excluding Budi himself)
        assertEquals(3, result.peerHeads.size)
        val peerIds = result.peerHeads.map { it.id.value }
        assertTrue(peerIds.contains("emp-joko"))
        assertTrue(peerIds.contains("emp-siti"))
        assertTrue(peerIds.contains("emp-anton"))
        assertFalse(peerIds.contains("emp-budi"))

        // 3. Subordinates must contain all 3 sales staff under Budi
        assertEquals(3, result.subordinates.size)
        val subIds = result.subordinates.map { it.id.value }
        assertTrue(subIds.contains("emp-rian"))
        assertTrue(subIds.contains("emp-dedi"))
        assertTrue(subIds.contains("emp-maya"))
    }

    @Test
    fun staff_operator_view_should_show_head_superior_and_peers() {
        val rian = sampleEmployees.first { it.id.value == "emp-rian" }

        val result = OrgNode.resolveTShapeView(sampleEmployees, rian)

        // 1. Superior is Budi (Head of Sales)
        assertNotNull(result.superior)
        assertEquals("emp-budi", result.superior.id.value)

        // 2. Peers in department are Dedi & Maya (excluding Rian)
        assertEquals(2, result.peersInDepartment.size)
        val peerIds = result.peersInDepartment.map { it.id.value }
        assertTrue(peerIds.contains("emp-dedi"))
        assertTrue(peerIds.contains("emp-maya"))
        assertFalse(peerIds.contains("emp-rian"))

        // 3. Staff has 0 subordinates
        assertTrue(result.subordinates.isEmpty())
    }

    @Test
    fun executive_view_should_show_all_department_heads_underneath() {
        val hendra = sampleEmployees.first { it.id.value == "emp-hendra" }

        val result = OrgNode.resolveTShapeView(sampleEmployees, hendra)

        // 1. No superior for owner/executive
        assertNull(result.superior)

        // 2. Subordinates are all 4 department heads
        assertEquals(4, result.subordinates.size)
        assertTrue(result.subordinates.all { it.level == HierarchyLevel.HEAD_OF_DEPARTMENT })
    }

    @Test
    fun avatar_initials_should_generate_cleanly() {
        val node1 = OrgNode(
            id = OrgNodeId("1"),
            name = "Budi Santoso",
            email = "b@w.id",
            department = Department.SALES,
            level = HierarchyLevel.STAFF_OPERATOR,
            roleTitle = "Sales"
        )
        assertEquals("BS", node1.avatarInitial)

        val node2 = OrgNode(
            id = OrgNodeId("2"),
            name = "Anton",
            email = "a@w.id",
            department = Department.QUALITY_CONTROL,
            level = HierarchyLevel.HEAD_OF_DEPARTMENT,
            roleTitle = "QC"
        )
        assertEquals("A", node2.avatarInitial)
    }

    @Test
    fun head_succession_demote_to_staff_should_move_old_head_to_subordinates() {
        val dimasNewHead = OrgNode(
            id = OrgNodeId("emp-dimas"),
            name = "Dimas Pratama",
            email = "dimas@wemade.id",
            department = Department.SALES,
            level = HierarchyLevel.HEAD_OF_DEPARTMENT,
            roleTitle = "Head of Sales Baru"
        )

        // Budi Santoso is the existing head of Sales
        val result = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = dimasNewHead,
            isDraft = true,
            successionAction = HeadSuccessionAction.DEMOTE_TO_STAFF
        )

        // 1. Peer heads should NOT contain Budi (Budi is in Sales, not another department)
        assertFalse(result.peerHeads.any { it.id.value == "emp-budi" })

        // 2. Subordinates of Dimas should contain Budi Santoso (demoted to staff) + existing 3 staff = 4
        assertEquals(4, result.subordinates.size)
        val demotedBudi = result.subordinates.find { it.id.value == "emp-budi" }
        assertNotNull(demotedBudi)
        assertEquals(HierarchyLevel.STAFF_OPERATOR, demotedBudi.level)
        assertEquals(dimasNewHead.id, demotedBudi.reportsToId)
    }

    @Test
    fun head_succession_deactivate_should_exclude_old_head() {
        val dimasNewHead = OrgNode(
            id = OrgNodeId("emp-dimas"),
            name = "Dimas Pratama",
            email = "dimas@wemade.id",
            department = Department.SALES,
            level = HierarchyLevel.HEAD_OF_DEPARTMENT,
            roleTitle = "Head of Sales Baru"
        )

        val result = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = dimasNewHead,
            isDraft = true,
            successionAction = HeadSuccessionAction.DEACTIVATE
        )

        // 1. Budi Santoso should NOT be in subordinates
        assertFalse(result.subordinates.any { it.id.value == "emp-budi" })
        assertEquals(3, result.subordinates.size)
    }

    @Test
    fun existing_staff_selection_should_keep_original_card_position_in_orderedDepartmentMembers() {
        // Sample sales staff in order: [emp-rian, emp-dedi, emp-maya]
        val dedi = sampleEmployees.first { it.id.value == "emp-dedi" }

        val resultDedi = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = dedi,
            isDraft = false
        )

        // Dedi must stay at index 1 in orderedDepartmentMembers, NOT moved to the end
        assertEquals(3, resultDedi.orderedDepartmentMembers.size)
        assertEquals("emp-rian", resultDedi.orderedDepartmentMembers[0].id.value)
        assertEquals("emp-dedi", resultDedi.orderedDepartmentMembers[1].id.value)
        assertEquals("emp-maya", resultDedi.orderedDepartmentMembers[2].id.value)
        assertEquals(dedi.id, resultDedi.focusNode.id)

        // Select Maya (index 2) - order must remain [emp-rian, emp-dedi, emp-maya]
        val maya = sampleEmployees.first { it.id.value == "emp-maya" }
        val resultMaya = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = maya,
            isDraft = false
        )
        assertEquals(3, resultMaya.orderedDepartmentMembers.size)
        assertEquals("emp-rian", resultMaya.orderedDepartmentMembers[0].id.value)
        assertEquals("emp-dedi", resultMaya.orderedDepartmentMembers[1].id.value)
        assertEquals("emp-maya", resultMaya.orderedDepartmentMembers[2].id.value)
        assertEquals(maya.id, resultMaya.focusNode.id)

        // Select Rian (index 0) - order must remain [emp-rian, emp-dedi, emp-maya]
        val rian = sampleEmployees.first { it.id.value == "emp-rian" }
        val resultRian = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = rian,
            isDraft = false
        )
        assertEquals(3, resultRian.orderedDepartmentMembers.size)
        assertEquals("emp-rian", resultRian.orderedDepartmentMembers[0].id.value)
        assertEquals("emp-dedi", resultRian.orderedDepartmentMembers[1].id.value)
        assertEquals("emp-maya", resultRian.orderedDepartmentMembers[2].id.value)
        assertEquals(rian.id, resultRian.focusNode.id)
    }

    @Test
    fun new_draft_staff_creation_should_append_draft_node_at_the_end_on_the_right() {
        val newDraft = OrgNode(
            id = OrgNodeId("emp-new-draft"),
            name = "Karyawan Baru",
            email = "baru@wemade.id",
            department = Department.SALES,
            level = HierarchyLevel.STAFF_OPERATOR,
            roleTitle = "Staf Pelaksana Baru"
        )

        val result = OrgNode.resolveTShapeView(
            nodes = sampleEmployees,
            focusNode = newDraft,
            isDraft = true
        )

        // Existing 3 staff in order, and new draft node appended at the end (index 3, far right)
        assertEquals(4, result.orderedDepartmentMembers.size)
        assertEquals("emp-rian", result.orderedDepartmentMembers[0].id.value)
        assertEquals("emp-dedi", result.orderedDepartmentMembers[1].id.value)
        assertEquals("emp-maya", result.orderedDepartmentMembers[2].id.value)
        assertEquals("emp-new-draft", result.orderedDepartmentMembers[3].id.value)
        assertTrue(result.isDraft)
    }
}
