# 🎓 Modul Pembelajaran: De-coupling Alur Operasional dari Preset FOB/CMT ke Kepemilikan Murni Tenant

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (Bounded Context), Tenant Ownership Pattern, UI Terminology Sanitization, Multi-Tenant Architecture  
> **Prasyarat**: Dasar Kotlin Multiplatform (KMP), Compose Multiplatform, SQL & Multi-Tenancy  
> **Referensi Task**: Issue #21 / Tenant-Centric Pipeline Workflow

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Seringkali saat membangun software B2B/SaaS, engineer terjebak memasukkan istilah teknis industri internal (seperti *FOB*, *CMT*, *Brand D2C*) langsung ke UI pengguna dan menjadikannya entitas database primer. Akibatnya:
1. **Pengguna SaaS Bingung**: Pemilik pabrik tidak mendaftar ke SaaS dengan mengidentifikasikan dirinya sebagai "Saya FOB" atau "Saya CMT". Mereka hanya ingin mendaftarkan pabrik/perusahaan mereka (`PT XYZ`) dan melihat **alur kerja modul pabrik mereka**.
2. **Keterikatan Arsitektur yang Kaku (Coupling)**: Alur kerja modul (pipeline) dikaitkan ke sebuah enum preset (`GarmentBusinessPreset`), bukan ke entitas pemilik sebenarnya yaitu **Tenant** (`TenantId`). Jika tenant ingin mengubah rumus HPP atau men-disable modul QC tanpa mengubah kategori bisnisnya, sistem menjadi rapuh.

### Analogi Sederhana
Bayangkan sebuah platform logistik kargo. Sebuah perusahaan pengiriman truk tidak ingin dipaksa memilih *"Anda adalah Truk FTL atau LTL"*, lalu di seluruh dashboard tertulis label besar *"FTL MODE"*. Yang mereka butuhkan adalah: **Ini armada milik PT Angkut Kilat, dan inilah rute perjalanan titik A ke titik B-nya**.

### Hasil Akhir yang Diharapkan
- Alur kerja operasional (`CustomTenantPipeline`) **nempel langsung ke Tenant**, bukan ke preset FOB/CMT.
- Dropdown di pojok kanan atas (GCP-style) murni berfungsi sebagai **Company Switcher** untuk memilih perusahaan/tenant aktif.
- Seluruh istilah *FOB*, *CMT*, *Brand D2C* dihapus dari tampilan UI dan judul pipeline. Layar kanvas murni memvisualisasikan **alur kerja modul operasional pabrik**.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta mengimplementasikan arsitektur ini dari nol:

1. **Langkah 0: Letakkan Kepemilikan di Core Domain (`CustomTenantPipeline`)**
   - Pastikan entitas `CustomTenantPipeline` memiliki foreign key logis `tenantId: TenantId` dan `pipelineName: String`.
   - Preset hanyalah *initial factory seed* saat pertama kali tenant onboarding, bukan identitas permanen.

2. **Langkah 1: Skema Database Berbasis Tenant (`tenant_pipelines`)**
   - Relasi: `tenant_id VARCHAR(64) REFERENCES tenants(id) ON DELETE CASCADE`.
   - `CONSTRAINT uq_tenant_pipeline UNIQUE (tenant_id)`.
   - Setiap baris mewakili konfigurasi graph alur kerja milik tepat satu tenant.

3. **Langkah 2: Bersihkan State Presentation (`CompanyTenantProfile`)**
   - Hapus properti `badge: "FOB Full Package"` dari profile perusahaan di frontend.
   - Ganti subtitle teknis dengan informasi operasional pabrik yang sesungguhnya (misal: *Pabrik Utama • 12 Line Produksi*).

4. **Langkah 3: Transformasi Toolbar Pipeline (`PresetSelectorBar` -> `PipelineControlBar`)**
   - Hapus teks `selectedPreset.displayName` dan `selectedPreset.shortBadge`.
   - Tampilkan nama perusahaan aktif (`Alur Operasional: PT WeMade Garmen Ekspor`) dan fungsi kontrol operasional (Live Stream monitoring, Mode Presentasi, Legenda Jalur Bezier).

5. **Langkah 4: Kompilasi & Cache Invalidation Wasm/Compose**
   - Pastikan seluruh referensi kode terkompilasi bersih tanpa *unresolved reference* agar Webpack dev server meng-emit bundle Wasm terbaru ke browser.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Profil Tenant Bersih di Navigasi (`CompanySwitcherDropdown.kt`)
```kotlin
data class CompanyTenantProfile(
    val id: String,
    val slug: String,
    val name: String,
    val preset: GarmentBusinessPreset,
    val iconColor: Color,
    val subtitle: String
) {
    companion object {
        val ALL = listOf(
            CompanyTenantProfile(
                id = "ten-demo-001",
                slug = "wemade-demo",
                name = "PT WeMade Garmen Ekspor",
                preset = GarmentBusinessPreset.FOB_FULL_PACKAGE,
                iconColor = Color(0xFF2563EB),
                subtitle = "Pabrik Utama • 12 Line Produksi"
            ),
            // ...
        )
    }
}
```
**Mengapa blok ini ditulis begini?**
- Menghilangkan `badge = "FOB Full Package"`. User yang berganti tenant melihat nama perusahaan dan kapasitas fasilitas fisiknya, bukan istilah singkatan kargo.

### Blok B: Indikator Header Dinamis Mengikuti Perusahaan Aktif (`PresetSelectorBar.kt`)
```kotlin
Column {
    Text(
        text = if (companyName.isNotBlank()) "Alur Operasional: $companyName" else "Alur Modul Operasional",
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        color = if (isPresentationMode) Color.White else WeMadeColors.Primary
    )
    Text(
        text = "Monitoring aliran modul, kontrak data antar divisi, dan status antrean",
        fontSize = 10.sp,
        color = if (isPresentationMode) Color(0xFF94A3B8) else WeMadeColors.OnSurfaceMuted
    )
}
```
**Mengapa blok ini ditulis begini?**
- Menghubungkan identitas visual kanvas langsung ke entitas **Perusahaan (Tenant)** yang sedang dipilih oleh Superadmin/User, menciptakan kejelasan konteks kerja.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Tenant-Owned Pipeline (`tenant_pipelines`)** | Hardcoded Preset Enum di Client | Setiap tenant bebas menyesuaikan modul, rumus HPP, dan routing tanpa menyentuh kode aplikasi. | Klien B tidak bisa kustomisasi alur modul tanpa mengacaukan klien A. |
| **Pembersihan Jargon UI (User-Centric Language)** | Menampilkan FOB/CMT Badge di setiap kartu | UI fokus pada nilai fungsional: modul, input, output, WIP, bottleneck. | Pengguna non-ekspor atau operator pabrik kebingungan dan merasa software tidak cocok untuk mereka. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Continuous Watcher Macet karena Error Kompilasi Sementara**
   - *Kenapa bahaya*: Ketika kamu mengubah deklarasi tipe (misal menghapus field `badge`), jika ada 1 file yang belum di-update (`AuthViewModel.kt`), continuous compiler Kotlin/WasmJs gagal diam-diam dan Webpack tetap menyajikan bundle binary lama. Developer mengira perubahan kodenya tidak bekerja.
   - *Solusi elegan*: Selalu jalankan `./gradlew :app:webApp:compileKotlinWasmJs` di terminal untuk memastikan tidak ada kesalahan kompilasi yang menyandera dev server.

2. **Jebakan 2: Hardcoding Preset Strings di Deskripsi Modul**
   - *Kenapa bahaya*: Di modul `CRM_SALES`, tertulis *"Negosiasi awal kontrak FOB"*. Begitu dibuka oleh perusahaan Makloon, deskripsinya menjadi aneh dan salah konteks.
   - *Solusi elegan*: Gunakan bahasa operasional umum: *"Negosiasi pesanan produksi, penentuan kuota minimum order (MOQ)..."*.
