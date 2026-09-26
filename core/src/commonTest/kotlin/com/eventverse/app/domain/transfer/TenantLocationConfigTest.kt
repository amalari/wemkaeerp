package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import com.eventverse.app.domain.workqueue.WorkStationCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TenantLocationConfigTest {

    private val gedungA = LocationId("loc-rajut-01")
    private val gedungB = LocationId("loc-finishing-01")
    private val knitting = FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING)

    private fun locations() = listOf(
        PhysicalLocation(gedungA, "Gedung A"),
        PhysicalLocation(gedungB, "Gedung B")
    )

    @Test
    fun `config when multi site enabled with fewer than two locations should be rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TenantLocationConfig(
                tenantId = "demo-tenant",
                isMultiSiteEnabled = true,
                locations = listOf(PhysicalLocation(gedungA, "Gedung A"))
            )
        }
    }

    @Test
    fun `config when mapping points at unknown location should be rejected`() {
        assertFailsWith<IllegalArgumentException> {
            TenantLocationConfig(
                tenantId = "demo-tenant",
                locations = locations(),
                nodeLocations = mapOf(knitting to LocationId("loc-yang-tidak-ada"))
            )
        }
    }

    @Test
    fun `endpoint when node is in house and mapped should be the site with its name`() {
        val config = TenantLocationConfig(
            tenantId = "demo-tenant",
            locations = locations(),
            nodeLocations = mapOf(knitting to gedungA)
        )
        assertEquals(LegEndpoint.Site(gedungA, "Gedung A"), config.endpointFor(knitting))
    }

    @Test
    fun `endpoint when node is subcontracted should be the vendor even if a location is mapped`() {
        val config = TenantLocationConfig(
            tenantId = "demo-tenant",
            locations = locations(),
            nodeLocations = mapOf(knitting to gedungA)
        )
        val endpoint = config.endpointFor(
            node = knitting,
            executionMode = WorkExecutionMode.SUBCONTRACTED,
            vendorRef = "CV Bordir Jaya"
        )
        assertEquals(
            LegEndpoint.Vendor("CV Bordir Jaya"),
            endpoint,
            "Barang ada di vendor, bukan di gedung — pemetaan lokasi tidak boleh menang"
        )
    }

    @Test
    fun `endpoint when subcontracted without vendor ref should be unknown`() {
        val config = TenantLocationConfig(tenantId = "demo-tenant", locations = locations())
        assertNull(
            config.endpointFor(knitting, WorkExecutionMode.SUBCONTRACTED, vendorRef = null)
        )
    }

    @Test
    fun `endpoint when node unmapped should be unknown rather than a default site`() {
        val config = TenantLocationConfig(tenantId = "demo-tenant", locations = locations())
        assertNull(config.endpointFor(knitting))
    }

    @Test
    fun `station adapter should read the same map as node lookup`() {
        val obras = WorkStationCode("OBRAS")
        val config = TenantLocationConfig(
            tenantId = "demo-tenant",
            locations = locations(),
            nodeLocations = mapOf(FlowNodeRef.Station(obras) to gedungB)
        )
        assertEquals(gedungB, config.locationFor(obras))
    }

    @Test
    fun `inter site transfer when multi site disabled should be false despite different buildings`() {
        val cutting = WorkStationCode("CUTTING")
        val obras = WorkStationCode("OBRAS")
        val config = TenantLocationConfig(
            tenantId = "demo-tenant",
            isMultiSiteEnabled = false,
            locations = locations(),
            nodeLocations = mapOf(
                FlowNodeRef.Station(cutting) to gedungA,
                FlowNodeRef.Station(obras) to gedungB
            )
        )
        assertEquals(false, config.isInterSiteTransfer(cutting, obras))
    }

    @Test
    fun `location named when id is unknown should fall back to the raw id`() {
        val config = TenantLocationConfig(tenantId = "demo-tenant", locations = locations())
        assertEquals("Gedung A", config.locationNamed(gedungA))
        assertEquals("loc-entah", config.locationNamed(LocationId("loc-entah")))
    }
}
