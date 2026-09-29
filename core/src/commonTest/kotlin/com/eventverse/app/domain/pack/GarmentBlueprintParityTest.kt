package com.eventverse.app.domain.pack

import com.eventverse.app.domain.blueprint.BlueprintCode
import com.eventverse.app.domain.pipeline.GarmentBusinessPreset
import com.eventverse.app.domain.pipeline.OperationalModuleCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tabel emas B4a: perilaku `GarmentBusinessPreset` × spec modul **sebelum** B4b memindahkan pembaca
 * (commit 4d399d2). Satu baris = `starter|modul|aktif|parameter`. Mengubah starter konveksi berarti mengubah
 * tabel ini dengan sengaja.
 */
class GarmentBlueprintParityTest {

    private val LEGACY = """
        fob_full_package|crm_sales|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=INDIRECT_OVERHEAD
        fob_full_package|sampling_order|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=SERVICE_FEE_ONLY
        fob_full_package|tech_pack_bom|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=FULL_PACKAGE_COGS
        fob_full_package|inventory|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=FULL_PACKAGE_COGS
        fob_full_package|costing_hpp|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=FULL_PACKAGE_COGS
        fob_full_package|production_mrp|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=INDIRECT_OVERHEAD
        fob_full_package|operator_exec|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=SERVICE_FEE_ONLY
        fob_full_package|quality_control|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=INDIRECT_OVERHEAD;defectLiability=SUPPLIER_VENDOR_DEFECT
        fob_full_package|fulfillment|true|stockOwnership=INTERNAL_FINISHED_GOODS;costingBehavior=RETAIL_VALUATION_WITH_FEES
        cmt_makloon|crm_sales|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=INDIRECT_OVERHEAD
        cmt_makloon|sampling_order|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=SERVICE_FEE_ONLY
        cmt_makloon|tech_pack_bom|false|stockOwnership=NON_STOCK_SERVICE;costingBehavior=FULL_PACKAGE_COGS
        cmt_makloon|inventory|false|stockOwnership=CONSIGNED_CLIENT_MATERIAL;costingBehavior=FULL_PACKAGE_COGS
        cmt_makloon|costing_hpp|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=SERVICE_FEE_ONLY
        cmt_makloon|production_mrp|true|stockOwnership=CONSIGNED_CLIENT_MATERIAL;costingBehavior=INDIRECT_OVERHEAD
        cmt_makloon|operator_exec|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=SERVICE_FEE_ONLY
        cmt_makloon|quality_control|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=INDIRECT_OVERHEAD;defectLiability=CLIENT_SUPPLIED_DEFECT
        cmt_makloon|fulfillment|true|stockOwnership=CONSIGNED_CLIENT_MATERIAL;costingBehavior=RETAIL_VALUATION_WITH_FEES
        brand_d2c|crm_sales|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=INDIRECT_OVERHEAD
        brand_d2c|sampling_order|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=SERVICE_FEE_ONLY
        brand_d2c|tech_pack_bom|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=FULL_PACKAGE_COGS
        brand_d2c|inventory|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=FULL_PACKAGE_COGS
        brand_d2c|costing_hpp|true|stockOwnership=NON_STOCK_SERVICE;costingBehavior=RETAIL_VALUATION_WITH_FEES
        brand_d2c|production_mrp|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=INDIRECT_OVERHEAD
        brand_d2c|operator_exec|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=SERVICE_FEE_ONLY
        brand_d2c|quality_control|true|stockOwnership=OWNED_RAW_MATERIAL;costingBehavior=INDIRECT_OVERHEAD;defectLiability=FACTORY_WORKMANSHIP
        brand_d2c|fulfillment|true|stockOwnership=INTERNAL_FINISHED_GOODS;costingBehavior=RETAIL_VALUATION_WITH_FEES
    """.trimIndent().lines()

    @Test
    fun starters_equalLegacyPresetBehaviourExactly_inCatalogOrder() {
        val actual = GarmentBlueprints.all.flatMap { b ->
            b.modules.map { m -> "${b.code.value}|${m.moduleCode}|${m.active}|${m.parameters.entries.joinToString(";") { "${it.key}=${it.value}" }}" }
        }
        assertEquals(LEGACY, actual)
    }

    @Test
    fun everyCatalogModule_isInEveryStarter() {
        val catalog = OperationalModuleCatalog.all.map { it.module.code }
        GarmentBlueprints.all.forEach { assertEquals(catalog, it.modules.map { m -> m.moduleCode }, it.code.value) }
    }

    @Test
    fun starterDisplay_matchesLegacyPreset_withoutExampleCompany() {
        GarmentBusinessPreset.entries.forEach { p ->
            val b = requireNotNull(GarmentBlueprints.find(BlueprintCode(p.code)))
            assertEquals(listOf(p.displayName, p.shortBadge, p.description, p.targetClientProfile),
                listOf(b.displayName, b.shortBadge, b.description, b.targetClientProfile))
            assertEquals(GarmentDomainPack.CODE, b.pack)
        }
        assertNull(GarmentBlueprints.find(BlueprintCode("sablon_manual")), "kode tak dikenal → null, bukan FOB")
    }
}
