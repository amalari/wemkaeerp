package com.eventverse.app.presentation.orgchart

/**
 * Isi awal [OrgChartViewModel] sebelum server menjawab (TRD-PLAT-010 K8).
 *
 * Produksi memakai [None] (default): tenant yang datanya kosong TIDAK boleh melihat contoh. [GarmentSample]
 * hanya untuk tes paritas dengan perilaku lama; ia sengaja bukan default supaya pemanggil yang lupa
 * mengisinya tidak mengulang bug sampel-otomatis.
 */
enum class OrgChartSeed {
    /** Tanpa isi: keadaan awal `Loading` bila ada klien, `Empty` bila tidak. */
    None,

    /** Divisi dan karyawan contoh konveksi (`Department.defaultPresets`, `OrgNode.createSampleEmployees`). Khusus tes. */
    GarmentSample
}
