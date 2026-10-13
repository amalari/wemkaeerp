package com.eventverse.app.domain.pack

import com.eventverse.app.domain.i18n.LocalizedText

/**
 * Judul baris menu pack garment (TRD-I18N-003): kode modul → (id, en). Teks `id` **persis** judul layar utama
 * modul yang tampil di menu sebelumnya (paritas dikunci `GarmentMenuLabelParityTest`); `en` memakai padanan
 * istilah industri. Terpisah dari `displayName` (nama penuh di header). Diterapkan oleh [withGarmentEnglish].
 */
internal object GarmentMenuLabels {
    val modules: Map<String, LocalizedText> = mapOf(
        "org_chart" to LocalizedText.of("Bagan Organisasi", "Organization Chart"),
        "dynamic_rbac" to LocalizedText.of("Hak Akses (RBAC)", "Access Rights (RBAC)"),
        "factory_flow" to LocalizedText.of("Alur Pabrik (Pipeline)", "Factory Flow (Pipeline)"),
        "crm_sales" to LocalizedText.of("Penjualan & Pelanggan", "Sales & Customers"),
        "sampling_order" to LocalizedText.of("Order Sampling", "Sampling Orders"),
        "inventory" to LocalizedText.of("Gudang & Bahan Baku", "Warehouse & Raw Materials"),
        "tech_pack_bom" to LocalizedText.of("Tech Pack & BOM", "Tech Pack & BOM"),
        "costing_hpp" to LocalizedText.of("Kalkulasi HPP", "Costing (COGS)"),
        "production_mrp" to LocalizedText.of("Jadwal Produksi (MRP)", "Production Schedule (MRP)"),
        "operator_exec" to LocalizedText.of("Lantai Produksi", "Production Floor"),
        "quality_control" to LocalizedText.of("Quality Control", "Quality Control"),
        "fulfillment" to LocalizedText.of("Packing & Pengiriman", "Packing & Shipping"),
        "master_data" to LocalizedText.of("Master Data Bahan & Harga", "Material & Price Master Data"),
        "vendor_contacts" to LocalizedText.of("Kontak Vendor", "Vendor Contacts"),
        "invoicing" to LocalizedText.of("Invoice & Penagihan", "Invoices & Billing")
    )
}
