package com.eventverse.app.domain.transfer

import com.eventverse.app.domain.pack.GarmentSlots

import com.eventverse.app.domain.pipeline.ModuleArchetype
import com.eventverse.app.domain.process.TenantOptionalProcess
import com.eventverse.app.domain.sampling.SamplingPipelineStage
import com.eventverse.app.domain.sampling.toStageCode
import com.eventverse.app.domain.tenant.TenantId
import com.eventverse.app.domain.workqueue.WorkExecutionMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Regresi terpenting di berkas ini adalah kasus pertama: pabrik satu atap tanpa makloon harus
 * menghasilkan nol leg. Fitur lintas-lokasi tidak boleh membebani tenant yang tidak punya
 * perpindahan sama sekali.
 */
class FlowLegDerivationTest {

    private val tenant = TenantId("demo-tenant")
    private val gedungA = LocationId("loc-rajut-01")
    private val gedungB = LocationId("loc-finishing-01")

    private val stages = listOf(
        SamplingPipelineStage.NEW_INTAKE,
        SamplingPipelineStage.CAM_PROGRAMMING,
        SamplingPipelineStage.MACHINE_KNITTING,
        SamplingPipelineStage.LINKING_ASSEMBLY,
        SamplingPipelineStage.CUCI_SOFTENER,
        SamplingPipelineStage.IN_DELIVERY
    )

    private fun locations() = listOf(
        PhysicalLocation(gedungA, "Gedung A"),
        PhysicalLocation(gedungB, "Gedung B")
    )

    private fun config(
        multiSite: Boolean = false,
        customerDispatch: Boolean = false,
        mappings: Map<FlowNodeRef, LocationId> = emptyMap()
    ) = TenantLocationConfig(
        tenantId = tenant.value,
        isMultiSiteEnabled = multiSite,
        requireCustomerDispatchSj = customerDispatch,
        locations = locations(),
        nodeLocations = mappings
    )

    private fun twoBuildingMapping() = mapOf<FlowNodeRef, LocationId>(
        FlowNodeRef.Stage(SamplingPipelineStage.CAM_PROGRAMMING) to gedungA,
        FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
        FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungB,
        FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungB
    )

    private fun process(
        code: String,
        anchor: SamplingPipelineStage,
        mode: WorkExecutionMode = WorkExecutionMode.IN_HOUSE,
        vendor: String? = null
    ) = TenantOptionalProcess(
        processId = "proc-${code.lowercase()}",
        tenantId = tenant,
        code = code,
        displayName = code,
        archetype = GarmentSlots.CUSTOM_EXTENSION,
        samplingAnchorAfter = anchor?.toStageCode(),
        executionMode = mode,
        vendorRef = vendor
    )

    private fun derive(
        processes: List<TenantOptionalProcess> = emptyList(),
        config: TenantLocationConfig,
        customerName: String? = null
    ): List<FlowTransferLeg> = FlowLegDerivation.deriveLegs(
        nodes = FlowLegDerivation.resolveNodes(stages, processes),
        processes = processes,
        config = config,
        customerName = customerName
    )

    @Test
    fun `derive legs when single site without subcontract should produce none`() {
        val legs = derive(config = config())
        assertEquals(emptyList(), legs, "Pabrik satu atap tidak boleh melihat konektor apa pun")
    }

    @Test
    fun `derive legs when mappings exist but multi site disabled should produce none`() {
        val legs = derive(config = config(multiSite = false, mappings = twoBuildingMapping()))
        assertEquals(
            emptyList(),
            legs,
            "Pemetaan sisa percobaan tidak boleh diam-diam menyalakan gerbang"
        )
    }

    @Test
    fun `derive legs when two buildings should produce one internal transfer with correct direction`() {
        val legs = derive(config = config(multiSite = true, mappings = twoBuildingMapping()))

        assertEquals(1, legs.size, "Hanya satu titik pergantian gedung di alur ini")
        val leg = legs.single()
        assertEquals(TransferType.INTERNAL_SITE_TRANSFER, leg.transferType)
        assertEquals(FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING), leg.fromNode)
        assertEquals(FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY), leg.toNode)
        assertEquals("Gedung A → Gedung B", leg.summary)
    }

    @Test
    fun `derive legs when node unmapped between two buildings should still bridge across it`() {
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            // LINKING_ASSEMBLY sengaja tidak dipetakan
            FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungB
        )
        val legs = derive(config = config(multiSite = true, mappings = mappings))

        assertEquals(1, legs.size)
        assertEquals(FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER), legs.single().toNode)
    }

    @Test
    fun `derive legs when nothing mapped should fail open with no legs`() {
        val legs = derive(config = config(multiSite = true))
        assertEquals(
            emptyList(),
            legs,
            "Tanpa satu pun pemetaan, gerbang harus terbuka — bukan mengunci pabrik"
        )
    }

    @Test
    fun `derive legs when one subcontracted process should produce outbound and inbound`() {
        val bordir = process(
            "BORDIR",
            SamplingPipelineStage.MACHINE_KNITTING,
            WorkExecutionMode.SUBCONTRACTED,
            "CV Bordir Jaya"
        )
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungA
        )
        val legs = derive(listOf(bordir), config(multiSite = true, mappings = mappings))

        assertEquals(2, legs.size, "Perjalanan pulang dari vendor adalah leg tersendiri")
        assertEquals(TransferType.SUBCONTRACT_OUTBOUND, legs[0].transferType)
        assertEquals(TransferType.SUBCONTRACT_INBOUND, legs[1].transferType)
        assertEquals(LegEndpoint.Vendor("CV Bordir Jaya"), legs[0].destination)
        assertEquals(LegEndpoint.Vendor("CV Bordir Jaya"), legs[1].origin)
    }

    @Test
    fun `derive legs when subcontract active should not need multi site`() {
        val bordir = process(
            "BORDIR",
            SamplingPipelineStage.MACHINE_KNITTING,
            WorkExecutionMode.SUBCONTRACTED,
            "CV Bordir Jaya"
        )
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungA
        )
        val legs = derive(listOf(bordir), config(multiSite = false, mappings = mappings))

        assertEquals(2, legs.size, "Barang keluar pabrik tetap butuh surat jalan meski satu atap")
    }

    @Test
    fun `derive legs when two consecutive processes share vendor should merge into one round trip`() {
        val processes = listOf(
            process("BORDIR", SamplingPipelineStage.MACHINE_KNITTING, WorkExecutionMode.SUBCONTRACTED, "CV Jaya"),
            process("SABLON", SamplingPipelineStage.MACHINE_KNITTING, WorkExecutionMode.SUBCONTRACTED, "CV Jaya")
        )
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungA
        )
        val legs = derive(processes, config(mappings = mappings))

        assertEquals(2, legs.size, "Satu vendor yang sama berarti barang dikirim sekali, bukan dua kali")
    }

    @Test
    fun `derive legs when two consecutive processes use different vendors should hand over directly`() {
        val processes = listOf(
            process("BORDIR", SamplingPipelineStage.MACHINE_KNITTING, WorkExecutionMode.SUBCONTRACTED, "CV Jaya"),
            process("SABLON", SamplingPipelineStage.MACHINE_KNITTING, WorkExecutionMode.SUBCONTRACTED, "CV Warna")
        )
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungA
        )
        val legs = derive(processes, config(mappings = mappings))

        // Tiga leg, bukan empat: alur tidak menyebut barang mampir balik ke pabrik di antara
        // kedua vendor, jadi penurunan tidak boleh mengarang singgahan itu. Kalau pabrik
        // memang ingin barangnya kembali dulu, itu dinyatakan dengan menaruh simpul in-house
        // di antara keduanya — keputusan tenant, bukan asumsi mesin.
        assertEquals(3, legs.size)
        assertEquals(
            listOf(
                TransferType.SUBCONTRACT_OUTBOUND,
                TransferType.SUBCONTRACT_OUTBOUND,
                TransferType.SUBCONTRACT_INBOUND
            ),
            legs.map { it.transferType },
            "Serah terima langsung antar vendor tetap tercatat sebagai pengiriman keluar"
        )
        assertEquals("CV Jaya → CV Warna", legs[1].summary)
    }

    @Test
    fun `derive legs when subcontracted process has no vendor ref should stay transparent`() {
        val broken = process(
            "BORDIR",
            SamplingPipelineStage.MACHINE_KNITTING,
            WorkExecutionMode.SUBCONTRACTED,
            vendor = null
        )
        val legs = derive(listOf(broken), config(multiSite = true, mappings = twoBuildingMapping()))

        assertEquals(1, legs.size, "Proses subkon tanpa vendor tidak boleh melahirkan leg palsu")
        assertEquals(TransferType.INTERNAL_SITE_TRANSFER, legs.single().transferType)
    }

    @Test
    fun `derive legs when customer dispatch required should add tail leg even for single site`() {
        val legs = derive(
            config = config(
                customerDispatch = true,
                mappings = mapOf(FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungA)
            ),
            customerName = "PT Buyer Sejahtera"
        )

        assertEquals(1, legs.size)
        val tail = legs.single()
        assertEquals(TransferType.CUSTOMER_DISPATCH, tail.transferType)
        assertEquals(LegEndpoint.Customer("PT Buyer Sejahtera"), tail.destination)
    }

    @Test
    fun `derive legs when customer dispatch disabled should omit tail leg`() {
        val legs = derive(
            config = config(
                customerDispatch = false,
                mappings = mapOf(FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungA)
            ),
            customerName = "PT Buyer Sejahtera"
        )
        assertEquals(emptyList(), legs)
    }

    @Test
    fun `derive legs when customer name missing should omit tail leg`() {
        val legs = derive(
            config = config(
                customerDispatch = true,
                mappings = mapOf(FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungA)
            ),
            customerName = null
        )
        assertEquals(emptyList(), legs)
    }

    @Test
    fun `derive legs when subcontract sits at tail should produce inbound then customer dispatch`() {
        val laundry = process(
            "LAUNDRY",
            SamplingPipelineStage.CUCI_SOFTENER,
            WorkExecutionMode.SUBCONTRACTED,
            "Laundry Bersih"
        )
        val legs = derive(
            processes = listOf(laundry),
            config = config(
                customerDispatch = true,
                mappings = mapOf(
                    FlowNodeRef.Stage(SamplingPipelineStage.CUCI_SOFTENER) to gedungA,
                    FlowNodeRef.Stage(SamplingPipelineStage.IN_DELIVERY) to gedungA
                )
            ),
            customerName = "PT Buyer Sejahtera"
        )

        assertEquals(
            listOf(
                TransferType.SUBCONTRACT_OUTBOUND,
                TransferType.SUBCONTRACT_INBOUND,
                TransferType.CUSTOMER_DISPATCH
            ),
            legs.map { it.transferType }
        )
    }

    @Test
    fun `leg key when derived twice should stay identical`() {
        val cfg = config(multiSite = true, mappings = twoBuildingMapping())
        val first = derive(config = cfg).map { it.legKey }
        val second = derive(config = cfg).map { it.legKey }

        assertEquals(first, second, "Kunci leg harus deterministik — ia jembatan ke dokumen tersimpan")
        assertTrue(first.all { it.length <= FlowTransferLeg.MAX_LEG_KEY_LENGTH })
    }

    @Test
    fun `leg key when direction differs should differ too`() {
        val bordir = process(
            "BORDIR",
            SamplingPipelineStage.MACHINE_KNITTING,
            WorkExecutionMode.SUBCONTRACTED,
            "CV Jaya"
        )
        val mappings = mapOf<FlowNodeRef, LocationId>(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING) to gedungA,
            FlowNodeRef.Stage(SamplingPipelineStage.LINKING_ASSEMBLY) to gedungA
        )
        val legs = derive(listOf(bordir), config(mappings = mappings))

        assertEquals(2, legs.map { it.legKey }.distinct().size, "Berangkat dan pulang harus berbeda kunci")
    }

    @Test
    fun `resolve nodes when process anchored should sit right after its stage`() {
        val bordir = process("BORDIR", SamplingPipelineStage.MACHINE_KNITTING)
        val nodes = FlowLegDerivation.resolveNodes(stages, listOf(bordir))

        val knittingIndex = nodes.indexOf(FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING))
        assertEquals(FlowNodeRef.Process("BORDIR"), nodes[knittingIndex + 1])
    }

    @Test
    fun `flow node ref key when parsed back should round trip`() {
        val refs = listOf(
            FlowNodeRef.Stage(SamplingPipelineStage.MACHINE_KNITTING),
            FlowNodeRef.Process("BORDIR"),
            FlowNodeRef.Station(com.eventverse.app.domain.workqueue.WorkStationCode("OBRAS"))
        )
        refs.forEach { ref ->
            assertEquals(ref, FlowNodeRef.parse(ref.key), "Round-trip gagal untuk ${ref.key}")
        }
    }

    @Test
    fun `flow node ref parse when key unknown should return null instead of throwing`() {
        assertNull(FlowNodeRef.parse("STAGE:TAHAP_YANG_SUDAH_DIHAPUS"))
        assertNull(FlowNodeRef.parse("BUKAN_JENIS:APA_PUN"))
        assertNull(FlowNodeRef.parse("tanpa-pemisah"))
    }

    @Test
    fun `storage mapped to another building should yield internal transfer from packing`() {
        val stages = listOf(SamplingPipelineStage.PENGEMASAN, SamplingPipelineStage.STORAGE_HOLDING)
        val legs = FlowLegDerivation.deriveLegs(
            nodes = FlowLegDerivation.resolveNodes(stages, emptyList()),
            processes = emptyList(),
            config = config(
                multiSite = true,
                mappings = mapOf(
                    FlowNodeRef.Stage(SamplingPipelineStage.PENGEMASAN) to gedungA,
                    FlowNodeRef.Stage(SamplingPipelineStage.STORAGE_HOLDING) to gedungB
                )
            )
        )
        assertEquals(1, legs.size)
        assertEquals(TransferType.INTERNAL_SITE_TRANSFER, legs.single().transferType)
        assertEquals(FlowNodeRef.Stage(SamplingPipelineStage.STORAGE_HOLDING), legs.single().toNode)
    }

    @Test
    fun `storage on packing rack in same building should yield no leg`() {
        val stages = listOf(SamplingPipelineStage.PENGEMASAN, SamplingPipelineStage.STORAGE_HOLDING)
        val legs = FlowLegDerivation.deriveLegs(
            nodes = FlowLegDerivation.resolveNodes(stages, emptyList()),
            processes = emptyList(),
            config = config(
                multiSite = true,
                mappings = mapOf(
                    FlowNodeRef.Stage(SamplingPipelineStage.PENGEMASAN) to gedungA,
                    FlowNodeRef.Stage(SamplingPipelineStage.STORAGE_HOLDING) to gedungA
                )
            )
        )
        assertTrue(legs.isEmpty())
    }
}
