package com.eventverse.app.domain.pack

import com.eventverse.app.domain.pack.GarmentDomainPack
import com.eventverse.app.domain.rbac.moduleIds
import com.eventverse.app.domain.rbac.isScopeSupported

import com.eventverse.app.domain.rbac.isFoundation

import com.eventverse.app.domain.rbac.isOperational

import com.eventverse.app.domain.rbac.isGovernance

import com.eventverse.app.domain.rbac.isHierarchical

import com.eventverse.app.domain.rbac.isGlobalOnly


import com.eventverse.app.domain.rbac.supportedScopes

import com.eventverse.app.domain.rbac.kind

import com.eventverse.app.domain.rbac.scopeCapability

import com.eventverse.app.domain.rbac.iconKey

import com.eventverse.app.domain.rbac.description

import com.eventverse.app.domain.rbac.displayName

import com.eventverse.app.domain.rbac.name

import com.eventverse.app.domain.rbac.code

import com.eventverse.app.domain.rbac.BusinessModules

import com.eventverse.app.domain.rbac.BusinessModule
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Tabel emas B6a: seksi menu & 15 modul pack garment = `enum ModuleCategory` / `enum BusinessModule` persis
 * (commit 9359c20), termasuk **urutan** (urutan menu). Baris modul:
 * `id|nama|seksi|kind|ikon|cakupan|scope yang didukung|slot|deskripsi`.
 */
class GarmentModulesParityTest {

    private val LEGACY_SECTIONS = listOf(
        "GOVERNANCE|Sistem & Struktur|1",
        "FOUNDATION|Data Induk & Referensi|2",
        "SALES|Penjualan & Relasi Pelanggan|3",
        "LOGISTICS|Gudang, Bahan Baku & Logistik|4",
        "TECHNICAL|Desain, Pola & Biaya HPP|5",
        "PRODUCTION|Lantai Produksi & Operator|6",
        "QUALITY|Kualitas & Pengawasan|7",
        "FINANCE|Keuangan & Penagihan|8"
    )

    private val LEGACY_MODULES = listOf(
        "org_chart|Bagan Struktur Organisasi & Karyawan|GOVERNANCE|GOVERNANCE|users|GLOBAL_ONLY|ALL_TENANT_DATA|-|Struktur divisi, jenjang jabatan, dan data karyawan pabrik.",
        "dynamic_rbac|Hak Akses & Jabatan (RBAC)|GOVERNANCE|GOVERNANCE|shield|GLOBAL_ONLY|ALL_TENANT_DATA|-|Matriks wewenang per jabatan, penugasan modul ke divisi, dan pengujian persona.",
        "factory_flow|Alur Pabrik (Pipeline)|GOVERNANCE|GOVERNANCE|flow_graph|GLOBAL_ONLY|ALL_TENANT_DATA|-|Kanvas alur operasional tenant: urutan modul, penggantian nama, dan bypass.",
        "master_data|Master Data Bahan & Harga|FOUNDATION|FOUNDATION|database|GLOBAL_ONLY|ALL_TENANT_DATA|-|Katalog benang, kain, aksesoris, satuan kemasan, dan tarif acuan HPP point-in-time.",
        "vendor_contacts|Kontak Vendor & Makloon|FOUNDATION|FOUNDATION|truck|GLOBAL_ONLY|ALL_TENANT_DATA|-|Buku kontak vendor subkon, daftar harga layanan per vendor, dan penunjukan vendor ke proses Vendor Luar.",
        "crm_sales|Pelanggan & Prospek Sales|SALES|OPERATIONAL|handshake|HIERARCHICAL|ALL_TENANT_DATA,OWN_DATA_ONLY,SUBORDINATE_DATA|order_ingestion|Pencatatan prospek, riwayat follow-up negosiasi, dan kontak pelanggan konveksi.",
        "sampling_order|Pola & Sampling Order|SALES|OPERATIONAL|ruler|HIERARCHICAL|ALL_TENANT_DATA,OWN_DATA_ONLY,SUBORDINATE_DATA|order_ingestion|Pembuatan SPK sampling prototipe baju, pola potong awal, dan persetujuan sample.",
        "inventory|Bahan Baku & Stok Kain|LOGISTICS|OPERATIONAL|package|GLOBAL_ONLY|ALL_TENANT_DATA|raw_material|Penerimaan kain rol, stok benang, kancing, zipper, dan multi-satuan (Yard/Kg/Pcs).",
        "tech_pack_bom|Spesifikasi BOM & Tech Pack|TECHNICAL|OPERATIONAL|clipboard|GLOBAL_ONLY|ALL_TENANT_DATA|product_engineering|Lembar kerja spesifikasi jahitan, Bill of Materials (BOM), dan panduan ukuran.",
        "costing_hpp|Kalkulasi HPP & Biaya|TECHNICAL|OPERATIONAL|calculator|GLOBAL_ONLY|ALL_TENANT_DATA|costing_hpp|Perhitungan HPP otomatis: bahan baku + ongkos jahit per menit + finishing & margin laba rahasia.",
        "production_mrp|Jadwal Mesin & SPK Massal|PRODUCTION|OPERATIONAL|calendar|GLOBAL_ONLY|ALL_TENANT_DATA|cutting|Alokasi antrean 10 mesin jahit, target jam kerja harian, dan penerbitan SPK potong/jahit.",
        "operator_exec|Catatan Kerja Operator|PRODUCTION|OPERATIONAL|activity|HIERARCHICAL|ALL_TENANT_DATA,OWN_DATA_ONLY,SUBORDINATE_DATA|sewing|Antarmuka ringkas operator jahit untuk input output potong, jahit, dan progres harian.",
        "quality_control|Inspeksi QC & Defect|QUALITY|OPERATIONAL|check_circle|GLOBAL_ONLY|ALL_TENANT_DATA|quality_control|Pencatatan baju cacat (reject/scrap), cetak label barcode lolos inspeksi, dan grading.",
        "fulfillment|Packing & Surat Jalan|LOGISTICS|OPERATIONAL|truck|GLOBAL_ONLY|ALL_TENANT_DATA|fulfillment|Finishing setrika uap, verifikasi kuantitas per karton, dan cetak Surat Jalan ekspedisi.",
        "invoicing|Invoice & Penagihan|FINANCE|FOUNDATION|receipt|HIERARCHICAL|ALL_TENANT_DATA,OWN_DATA_ONLY,SUBORDINATE_DATA|-|Penerbitan faktur tagihan, termin uang muka (DP), dan pelunasan."
    )

    private val pack = GarmentDomainPack.pack

    @Test
    fun sections_equalLegacyModuleCategory_inOrder() {
        assertEquals(LEGACY_SECTIONS, pack.sections.map { "${it.code.value}|${it.displayName}|${it.order}" })
    }

    /** Aksen & latar seksi = token yang dulu dipetakan `when (ModuleCategory)` di ModuleCardView/dialog entitlement. */
    private val LEGACY_SECTION_COLORS = listOf(
        "GOVERNANCE|FF7C3AED|FFF5F3FF", "FOUNDATION|FF0D9488|FFF0FDFA", "SALES|FF2563EB|FFEFF6FF", "LOGISTICS|FFD97706|FFFFFBEB",
        "TECHNICAL|FF0284C7|FFF0FDFA", "PRODUCTION|FFEA580C|FFFFF7ED", "QUALITY|FF16A34A|FFF0FDF4", "FINANCE|FF16A34A|FFF0FDF4"
    )

    @Test
    fun sectionColors_equalLegacyTokenMapping() {
        assertEquals(LEGACY_SECTION_COLORS, pack.sections.map {
            "${it.code.value}|${it.colorHex.toString(16).uppercase()}|${it.tintHex.toString(16).uppercase()}"
        })
    }

    @Test
    fun modules_equalLegacyBusinessModule_inDeclarationOrder() {
        assertEquals(LEGACY_MODULES, pack.modules.map { m ->
            listOf(m.id.value, m.displayName, m.section.value, m.kind.name, m.iconKey, m.scopeCapability.name,
                m.supportedScopes.map { it.name }.sorted().joinToString(","), m.slot?.value ?: "-", m.description).joinToString("|")
        })
    }

    /** Dasar "nol migrasi" (TRD FR-1): kunci NAME tersimpan = code.uppercase() untuk setiap modul. */
    @Test
    fun storedName_isCodeUppercased_forEveryModule() {
        GarmentDomainPack.pack.moduleIds.forEach { assertEquals(it.name, ModuleId(it.code).storedName, it.code) }
        assertEquals(GarmentDomainPack.pack.moduleIds.map { it.code }, pack.modules.map { it.id.value })
    }

    /**
     * A4: label aksi & istilah chrome adalah **data pack**, dan pack garment menyalinnya persis dari layar
     * generik B6e — tenant garment tidak boleh melihat perbedaan sedikit pun. Tabel emas ini yang mengunci
     * katanya, supaya perubahan berikutnya harus disengaja.
     */
    @Test
    fun actionsAndVocabulary_equalLegacyChromeWording() {
        assertEquals(
            listOf("ADD|Tambah Pesanan", "EDIT|Input Progres", "APPROVE|Setujui SPK", "DELETE|Hapus Data"),
            pack.actions.map { "${it.code.name}|${it.label}" }
        )
        assertEquals("pabrik", pack.term(VocabularyKey.WORKPLACE))
        assertEquals("Dokumen", pack.term(VocabularyKey.DOCUMENT))
    }
}
