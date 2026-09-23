package com.eventverse.app.domain.vendor

import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.vendor.usecases.AssignVendorToProcessCommand
import com.eventverse.app.domain.vendor.usecases.AssignVendorToProcessUseCase
import com.eventverse.app.domain.vendor.usecases.CancelVendorAssignmentCommand
import com.eventverse.app.domain.vendor.usecases.CancelVendorAssignmentUseCase
import com.eventverse.app.domain.vendor.usecases.RegisterVendorCommand
import com.eventverse.app.domain.vendor.usecases.RegisterVendorUseCase
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VendorAssignmentUseCaseTest {

    private val tenant = TenantId("ten-demo-001")
    private val today = LocalDate(2026, 9, 24)
    private val now = Instant.parse("2026-09-01T00:00:00Z")

    private class FakeVendors : VendorRepository {
        val store = mutableMapOf<VendorId, Vendor>()
        override suspend fun findById(tenantId: TenantId, id: VendorId) = store[id]?.takeIf { it.tenantId == tenantId }
        override suspend fun listContacts(tenantId: TenantId, includeInactive: Boolean) =
            store.values.filter { it.tenantId == tenantId && (includeInactive || it.isActive) }
        override suspend fun save(vendor: Vendor) = vendor.also { store[it.id] = it }
    }

    private class FakeAssignments : VendorAssignmentRepository {
        val store = mutableMapOf<VendorAssignmentId, VendorAssignment>()
        override suspend fun findById(tenantId: TenantId, id: VendorAssignmentId) = store[id]
        override suspend fun findActive(tenantId: TenantId) = store.values.filter { it.isActive }
        override suspend fun findByVendor(tenantId: TenantId, vendorId: VendorId) = store.values.filter { it.vendorId == vendorId }
        override suspend fun save(assignment: VendorAssignment) = assignment.also { store[it.id] = it }
    }

    private class FakeFlow(var dispatched: Boolean = false) : SubcontractFlowGateway {
        val links = mutableMapOf<String, String?>()
        val need = SubcontractNeed("smp-1", "SPK-0123", "Buyer A", "Kaos Seragam", "SABLON", "Sablon", 200)
        override suspend fun openNeeds(tenantId: TenantId) = listOf(need)
        override suspend fun findNeed(tenantId: TenantId, subjectId: String, processCode: String) =
            need.takeIf { it.subjectId == subjectId && it.processCode.equals(processCode, ignoreCase = true) }
        override suspend fun isDispatched(tenantId: TenantId, subjectId: String, processCode: String) = dispatched
        override suspend fun linkVendor(tenantId: TenantId, subjectId: String, processCode: String, vendorRef: String?) {
            links["$subjectId/$processCode"] = vendorRef
        }
    }

    private val vendors = FakeVendors()
    private val assignments = FakeAssignments()
    private val flow = FakeFlow()
    private var seq = 0
    private val assign = AssignVendorToProcessUseCase(vendors, assignments, flow)

    private fun seedVendor(id: String, name: String, price: Long? = 3_500) {
        val base = Vendor(VendorId(id), tenant, VendorName(name), phone = "0812", createdAt = now, updatedAt = now)
        vendors.store[base.id] = price?.let {
            base.setRate(
                VendorServiceRate("SABLON", "Sablon", it, VendorPriceUnit.PER_PRINT_POINT, effectiveFrom = LocalDate(2026, 9, 1)),
                now
            )
        } ?: base
    }

    private fun command(vendorId: String, price: Long? = null, unit: VendorPriceUnit? = null) = AssignVendorToProcessCommand(
        tenantId = tenant, subjectId = "smp-1", processCode = "sablon", vendorId = VendorId(vendorId),
        pricePerUnitIdr = price, unit = unit, unitsPerPiece = 2, today = today,
        idGenerator = { "vas-${++seq}" }
    )

    @Test
    fun `assign without price should use price list and link vendor to flow`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")

        val result = assign(command("vnd-1")).getOrThrow()

        assertEquals(VendorPriceSource.PRICE_LIST, result.priceSource)
        assertEquals(1_400_000L, result.totalIdr) // 3.500 x 2 titik x 200 pcs
        assertEquals("CV Sablon Jaya", flow.links["smp-1/SABLON"])
    }

    @Test
    fun `assign with different price should be recorded as negotiated`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        val result = assign(command("vnd-1", price = 3_000)).getOrThrow()
        assertEquals(VendorPriceSource.NEGOTIATED, result.priceSource)
    }

    @Test
    fun `assign vendor without price list and without manual price should fail`() = runTest {
        seedVendor("vnd-2", "Bordir Mandiri", price = null)
        assertTrue(assign(command("vnd-2")).isFailure)
        assertTrue(flow.links.isEmpty())
    }

    @Test
    fun `reassign before dispatch should cancel previous assignment`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        seedVendor("vnd-3", "Sablon Makmur")

        val first = assign(command("vnd-1")).getOrThrow()
        assign(command("vnd-3")).getOrThrow()

        assertEquals(VendorAssignmentStatus.CANCELLED, assignments.store.getValue(first.id).status)
        assertEquals(1, assignments.findActive(tenant).size)
        assertEquals("Sablon Makmur", flow.links["smp-1/SABLON"])
    }

    @Test
    fun `assign after surat jalan issued should be rejected`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        flow.dispatched = true
        assertTrue(assign(command("vnd-1")).isFailure)
    }

    @Test
    fun `assign inactive vendor should be rejected`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        vendors.store[VendorId("vnd-1")] = vendors.store.getValue(VendorId("vnd-1")).deactivate(now)
        assertTrue(assign(command("vnd-1")).isFailure)
    }

    @Test
    fun `cancel assignment should unlink vendor from flow`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        val assigned = assign(command("vnd-1")).getOrThrow()

        CancelVendorAssignmentUseCase(assignments, flow)(CancelVendorAssignmentCommand(tenant, assigned.id)).getOrThrow()

        assertNull(flow.links["smp-1/SABLON"])
        assertTrue(assignments.findActive(tenant).isEmpty())
    }

    @Test
    fun `register vendor with duplicate name should fail`() = runTest {
        seedVendor("vnd-1", "CV Sablon Jaya")
        val result = RegisterVendorUseCase(vendors)(RegisterVendorCommand(tenant, name = "cv sablon jaya"))
        assertTrue(result.isFailure)
    }
}
