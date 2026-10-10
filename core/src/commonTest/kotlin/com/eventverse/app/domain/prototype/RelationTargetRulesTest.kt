package com.eventverse.app.domain.prototype

import com.eventverse.app.domain.customfield.RelationTargetResolver
import com.eventverse.app.domain.orgchart.OrgNodeId
import com.eventverse.app.domain.tenant.TenantId
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * TRD-FIELD-004 B2 (FR-2.1/2.2): normalisasi target RELATION ke sumber daya resolver (`MODUL:entitas`) dan
 * validasi keberadaan sebelum reducer. Dua konteks: pack `layanan` (non-default) dan target lintas modul
 * ala garment (`quality_control:inspeksi`). Resolver palsu **sadar tenant** supaya isolasi terbukti.
 */
class RelationTargetRulesTest {

    private class FakeResolver(private val known: Set<Triple<String, String, String>>) : RelationTargetResolver {
        val asked = mutableListOf<Triple<String, String, String>>()
        override suspend fun exists(
            tenantId: TenantId,
            targetResource: String,
            targetRecordId: String,
            reachableOwnerIds: Set<OrgNodeId>?
        ): Boolean {
            asked += Triple(tenantId.value, targetResource, targetRecordId)
            return Triple(tenantId.value, targetResource, targetRecordId) in known
        }
    }

    private val ownerModule = "layanan_change_request"

    private fun entity(vararg targets: Pair<String, String?>) = EntitySpec(
        "change_request", "Permintaan",
        listOf(FieldSpec("judul", "Judul", FieldType.TEXT)) +
            targets.map { (key, target) -> FieldSpec(key, "Rujukan $key", FieldType.RELATION, target = target) }
    )

    @Test fun `relationTargetResource_targetWithoutColon_prefixesOwnerModule`() {
        assertEquals("layanan_change_request:change_request", relationTargetResource(ownerModule, "change_request"))
    }

    @Test fun `relationTargetResource_targetWithColon_keptAsIs`() {
        assertEquals("quality_control:inspeksi", relationTargetResource(ownerModule, "quality_control:inspeksi"))
    }

    @Test fun `relationTargetResource_malformedTarget_rejected`() {
        listOf("a b", "a:b:c", ":x", "x:").forEach { bad ->
            assertFailsWith<IllegalArgumentException>(bad) { relationTargetResource(ownerModule, bad) }
        }
    }

    @Test fun `relationTargetProblem_ownTenantExistingId_accepted_andResolverGetsNormalizedResource`() = runTest {
        val resolver = FakeResolver(setOf(Triple("ten-a", "layanan_change_request:change_request", "cr-1")))
        val problem = entity("rujukan" to "change_request")
            .relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("rujukan" to "cr-1"), resolver)
        assertNull(problem)
        assertEquals(listOf(Triple("ten-a", "layanan_change_request:change_request", "cr-1")), resolver.asked)
    }

    @Test fun `relationTargetProblem_idMissing_rejected`() = runTest {
        val resolver = FakeResolver(emptySet())
        val problem = entity("rujukan" to "change_request")
            .relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("rujukan" to "tak-ada"), resolver)
        assertNotNull(problem)
    }

    @Test fun `relationTargetProblem_idOfOtherTenant_rejectedWithSameMessageAsMissing`() = runTest {
        val resolver = FakeResolver(setOf(Triple("ten-b", "layanan_change_request:change_request", "cr-b")))
        val e = entity("rujukan" to "change_request")
        val otherTenant = e.relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("rujukan" to "cr-b"), resolver)
        val missing = e.relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("rujukan" to "zzz"), resolver)
        assertNotNull(otherTenant)
        assertEquals(missing, otherTenant, "pesan tak boleh membedakan 'tak ada' dan 'milik tenant lain' (tanpa oracle)")
    }

    @Test fun `relationTargetProblem_blankValue_skippedWithoutAskingResolver`() = runTest {
        val resolver = FakeResolver(emptySet())
        val problem = entity("rujukan" to "change_request")
            .relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("rujukan" to ""), resolver)
        assertNull(problem)
        assertEquals(emptyList(), resolver.asked)
    }

    @Test fun `relationTargetProblem_crossModuleTarget_usesGivenModule`() = runTest {
        val resolver = FakeResolver(setOf(Triple("ten-a", "quality_control:inspeksi", "q-1")))
        val e = entity("qc" to "quality_control:inspeksi")
        assertNull(e.relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("qc" to "q-1"), resolver))
        assertNotNull(e.relationTargetProblem(ownerModule, TenantId("ten-b"), mapOf("qc" to "q-1"), resolver))
    }

    @Test fun `relationTargetProblem_entityWithoutRelation_nullAndNoResolverCall`() = runTest {
        val resolver = FakeResolver(emptySet())
        val e = EntitySpec("x", "X", listOf(FieldSpec("judul", "Judul", FieldType.TEXT)))
        assertNull(e.relationTargetProblem(ownerModule, TenantId("ten-a"), mapOf("judul" to "a"), resolver))
        assertEquals(emptyList(), resolver.asked)
    }
}
