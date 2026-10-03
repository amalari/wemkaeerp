package com.eventverse.app.presentation.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Uji peta aliran data port ([buildDataFlowMap]): fixture memakai modul aktif blueprint
 * `fob_full_package` — paritas dengan draf tenant `wemade-demo` — plus modul kustom di luar
 * katalog untuk jalur fallback slot.
 */
class DataFlowModelTest {

    private fun module(
        id: String,
        section: String = "PRODUCTION",
        kind: String = "OPERATIONAL",
        slot: String? = null,
        slotInput: String? = null,
        slotOutput: String? = null
    ) = DiscoveryModuleUi(
        id = id,
        displayName = id,
        section = section,
        kind = kind,
        iconKey = null,
        slot = slot,
        slotInput = slotInput,
        slotOutput = slotOutput,
        active = true
    )

    private fun draft(vararg extra: DiscoveryModuleUi): DiscoveryDraftUi {
        val modules = listOf(
            module("org_chart", section = "GOVERNANCE", kind = "GOVERNANCE"),
            module("crm_sales", section = "SALES", slot = "order_ingestion", slotInput = "CommercialInquiry", slotOutput = "ProductionOrderDraft"),
            module("sampling_order", section = "SALES", slot = "order_ingestion", slotInput = "CommercialInquiry", slotOutput = "ProductionOrderDraft"),
            module("tech_pack_bom", section = "TECHNICAL", slot = "product_engineering", slotInput = "ApprovedSampleSpecification", slotOutput = "TechPackAndYieldData"),
            module("inventory", section = "LOGISTICS", slot = "raw_material", slotInput = "MaterialRequisition", slotOutput = "VerifiedMaterialStock"),
            module("costing_hpp", section = "TECHNICAL", slot = "costing_hpp", slotInput = "TechPackAndYieldData", slotOutput = "CostingCalculationResult"),
            module("production_mrp", section = "PRODUCTION", slot = "cutting", slotInput = "CuttingOrderWithFabric", slotOutput = "CutPiecesBundle"),
            module("operator_exec", section = "PRODUCTION", slot = "sewing", slotInput = "CutPiecesBundle", slotOutput = "AssembledGarmentBundle"),
            module("quality_control", section = "QUALITY", slot = "quality_control", slotInput = "FinishedGarmentUnit", slotOutput = "InspectedAndGradedUnit"),
            module("fulfillment", section = "LOGISTICS", slot = "fulfillment", slotInput = "InspectedAndGradedUnit", slotOutput = "DispatchedShipmentManifest")
        ) + extra
        return DiscoveryDraftUi(
            id = "d1",
            status = "DRAFT",
            narrative = null,
            packCode = "garment",
            packDisplayName = "Konveksi & Garmen",
            blueprintCode = "fob_full_package",
            blueprintDescription = "",
            modules = modules,
            activeModuleCodes = modules.filter { it.kind == "OPERATIONAL" }.map { it.id },
            screens = emptyList()
        )
    }

    private fun DataFlowMap.flowOf(id: String) = flows.first { it.module.id == id }

    @Test
    fun `rantai produksi terpetakan dari katalog domain`() {
        val map = buildDataFlowMap(draft())

        assertEquals(9, map.flows.size)

        // CRM adalah titik masuk alur — kontrak domainnya tidak mendeklarasikan port masuk.
        val crm = map.flowOf("crm_sales")
        assertEquals(0, crm.incoming.size)

        val sampling = map.flowOf("sampling_order")
        assertTrue(sampling.incoming.any { it.from?.id == "crm_sales" && it.payload == "ProductionOrderDraft" })

        val inventory = map.flowOf("inventory")
        assertTrue(inventory.incoming.any { it.from?.id == "tech_pack_bom" && it.payload == "MaterialRequisition" })

        val mrp = map.flowOf("production_mrp")
        assertTrue(mrp.incoming.any { it.from?.id == "costing_hpp" && it.payload == "CostingCalculationResult" })
        assertTrue(mrp.incoming.any { it.from?.id == "inventory" && it.payload == "VerifiedMaterialStock" })

        val operator = map.flowOf("operator_exec")
        assertTrue(operator.incoming.any { it.from?.id == "production_mrp" && it.payload == "CutPiecesBundle" })

        val fulfillment = map.flowOf("fulfillment")
        assertTrue(fulfillment.incoming.any { it.from?.id == "quality_control" && it.payload == "InspectedAndGradedUnit" })
    }

    @Test
    fun `masukan rujukan qc dibaca dari tech pack tanpa mengalirkan barang`() {
        val reference = buildDataFlowMap(draft())
            .flowOf("quality_control")
            .incoming
            .filter { it.isReference }

        assertEquals(1, reference.size)
        assertEquals("TechPackAndYieldData", reference.single().payload)
        assertEquals("tech_pack_bom", reference.single().from?.id)
    }

    @Test
    fun `keluaran sampling diterima tech pack dan dispatch jadi ujung alur`() {
        val map = buildDataFlowMap(draft())

        val dispatch = map.flowOf("fulfillment").outgoing.single()
        assertEquals("DispatchedShipmentManifest", dispatch.payload)
        assertNull(dispatch.to)

        val approved = map.flowOf("sampling_order").outgoing.single()
        assertEquals("ApprovedSampleSpecification", approved.payload)
        assertEquals("tech_pack_bom", approved.to?.id)
    }

    @Test
    fun `ringkasan peta menghitung sambungan eksternal dan ujung alur`() {
        val map = buildDataFlowMap(draft())

        assertEquals(11, map.connectionCount)
        // Rantai katalog tertutup penuh: setiap port masuk punya penghasil di antara modul aktif.
        assertEquals(0, map.externalInputCount)
        assertEquals(1, map.endOutputCount)
        assertEquals(map.handoffs.size, map.connectionCount + map.externalInputCount + map.endOutputCount)
    }

    @Test
    fun `port dan slot tampil dengan label manusiawi dari kosakata pack`() {
        val map = buildDataFlowMap(draft())

        // Kode tetap identitas kontrak; label hanya lapisan tampilan dari DomainPack.portLabels.
        val po = map.flowOf("sampling_order").incoming.first { it.payload == "ProductionOrderDraft" }
        assertEquals("Draf Pesanan Produksi (PO)", po.payloadLabel)

        val reference = map.flowOf("quality_control").incoming.first { it.isReference }
        assertEquals("Tech Pack & Kebutuhan Bahan", reference.payloadLabel)

        // Slot juga memakai nama tampilan pack, bukan kode tersimpannya.
        assertEquals("Penerimaan Pesanan / PO / Sales Ingestion", map.flowOf("crm_sales").slotLabel)
    }

    @Test
    fun `draf pra-handoff berlabel dari payload sendiri meski pack tak terdaftar`() {
        // Pack hasil chat builder belum di-assign ke tenant → tidak ada di registry klien.
        // Label tetap tampil karena ikut ringkasan draf (portLabels/slotLabels), bukan dari registry.
        val modules = listOf(
            module("klinik_antrean", section = "LAYANAN", slot = "klinik_antrean", slotInput = "PatientVisit", slotOutput = "QueueTicket"),
            module("klinik_rekam", section = "LAYANAN", slot = "klinik_antrean", slotInput = "PatientVisit", slotOutput = "MedicalRecord")
        )
        val draft = DiscoveryDraftUi(
            id = "d2",
            status = "DRAFT",
            narrative = null,
            packCode = "klinik_uji",
            packDisplayName = "Klinik Uji",
            blueprintCode = "klinik_sederhana",
            blueprintDescription = "",
            modules = modules,
            activeModuleCodes = modules.map { it.id },
            screens = emptyList(),
            portLabels = mapOf("PatientVisit" to "Kunjungan Pasien", "QueueTicket" to "Nomor Antrean"),
            slotLabels = mapOf("klinik_antrean" to "Antrean Pasien")
        )
        val map = buildDataFlowMap(draft)

        val incoming = map.flowOf("klinik_rekam").incoming.single()
        assertEquals("PatientVisit", incoming.payload)
        assertEquals("Kunjungan Pasien", incoming.payloadLabel)
        assertEquals("Antrean Pasien", map.flowOf("klinik_antrean").slotLabel)
    }

    @Test
    fun `modul governance tidak ikut peta aliran`() {
        val map = buildDataFlowMap(draft())

        assertTrue(map.flows.none { it.module.kind != "OPERATIONAL" })
    }

    @Test
    fun `modul di luar katalog jatuh ke port slot bawaan draf`() {
        val custom = module(
            id = "custom_plugin",
            slot = "custom_extension",
            slotInput = "AnyOperationalPayload",
            slotOutput = "AnyOperationalPayload"
        )
        val map = buildDataFlowMap(draft(custom))

        val flow = map.flowOf("custom_plugin")
        assertEquals(listOf("AnyOperationalPayload"), flow.incoming.map { it.payload })
        assertEquals("Data Operasional Modul Kustom", flow.incoming.single().payloadLabel)
        assertNull(flow.incoming.single().from)
        // Tipenya dipakai sendiri → pemakaian internal, bukan keluaran akhir.
        assertEquals(flow.module.id, flow.outgoing.single().to?.id)
    }
}

