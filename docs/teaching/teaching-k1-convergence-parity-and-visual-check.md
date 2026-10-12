# Mentoring Teknis: Verifikasi Konvergensi K1 (Paritas Hak Akses B6 & Pengecekan Visual Alur Rajut-Bordir)

Dokumen ini mendokumentasikan proses verifikasi kriteria konvergensi **K1** sesuai dengan [`docs/plannings/mvp/PLAN-dual-track-garment-and-general-platform.md`](../plannings/mvp/PLAN-dual-track-garment-and-general-platform.md) §5 butir 1:
> *"B6 selesai dan paritas konveksi A = B pada seluruh test + cek visual tenant rajut & bordir."*

---

## 1. Konteks Bisnis & Arsitektur

Sebagai bagian dari evolusi **Jalur B (WeMade Flow Platform)** yang mengubah ERP vertikal konveksi menjadi platform orkestrasi modular multi-industri:
1. Enum kaku konveksi (`PipelineStage`, `ModuleArchetype`, `BusinessModule`, `GarmentBusinessPreset`) telah sepenuhnya dimigrasikan menjadi data dinamis berbasis **Domain Pack** (`PhaseCode`, `SlotCode`, `ModuleId`, `Blueprint`).
2. Sebelum beralih sepenuhnya dari Jalur A ke Jalur B, **paritas fungsional dan keamanan wajib dibuktikan 100%**:
   - **Keamanan (RBAC & Entitlement)**: Tidak boleh ada satupun peran/pengguna yang kehilangan atau mendapatkan hak akses secara tidak sah akibat refactoring enum menjadi data.
   - **Tampilan Visual (Compose Multiplatform Wasm)**: Alur tenant konveksi bawaan (**Rajut** / `wemade-demo`) maupun varian industri spesifik (**Bordir** / `bordir-uji`) harus tetap ter-render presisi di kanvas Factory Flow dengan bahasa desain Claymorphism.

---

## 2. Pengujian Paritas: `AccessSnapshotB6Test`

### A. Mekanisme Snapshot Opt-in
`AccessSnapshotB6Test` mengunci 690 kombinasi keputusan wewenang (Prinsipal × Modul × Tingkat Akses × Cakupan Data × Sumber Izin):
- Memeriksa setiap pengguna riil (`users`), peran kustom tenant (`custom_roles`), dan penugasan divisi (`department_module_assignments`).
- Melakukan query langsung ke endpoint evaluasi akses: `GET /api/tenant/me/access`.

### B. Prosedur Eksekusi
1. Membuat database isolasi scratch dari dump data dev:
   ```bash
   docker exec wemade-postgres sh -c "dropdb -U postgres --if-exists wemake_erp_scratch_b6 && createdb -U postgres wemake_erp_scratch_b6 && pg_dump -U postgres wemade_erp | psql -U postgres -q wemake_erp_scratch_b6"
   ```
2. Menjalankan test suite berpagar `RUN_ACCESS_SNAPSHOT=1`:
   ```bash
   DB_NAME=wemake_erp_scratch_b6 RUN_ACCESS_SNAPSHOT=1 ./gradlew :server:test --tests '*AccessSnapshotB6Test*'
   ```
3. **Hasil**: **100% Lulus (0 kegagalan, 0 selisih)**. Seluruh 690 entri snapshot identik baris per baris dengan [`server/src/test/resources/access-snapshot-b6.txt`](../../server/src/test/resources/access-snapshot-b6.txt).

---

## 3. Pengecekan Visual di Browser Nyata (Wasm Compose)

Pengujian visual dilakukan langsung pada aplikasi Web Compose (Wasm) yang berjalan di `http://localhost:3001` dengan backend Ktor di `http://localhost:8081`.

### A. Tenant Rajut (`wemade-demo` / PT WeMade Garmen Ekspor)
- **Halaman Login (`/login`)**:
  - Kartu Claymorphism dengan tombol demo masuk cepat (Owner & Superadmin).
  - Berhasil login sebagai `superadmin_apps` (`PLATFORM_SUPERADMIN`).
- **Dashboard Builder (`/builder`)**:
  - Ringkasan deployment live: Domain Pack `GARMENT (v1)`, status `IMPORTED`.
  - Mini canvas pipeline: `Procurement → Cutting → Sewing → QC & Pack`.
  - Kartu statistik 4 kolom Neo-brutalist: Modul Operasional (8 Modul), Kesehatan Pipeline (100% Optimal), Paket PRO.
- **Factory Flow Canvas (`/factory-flow`)**:
  - 5 fase terpetakan: Komersial & Sampling, Spesifikasi & HPP, Rantai Pasok & Bahan Baku, Lantai Produksi, Mutu & Pengiriman.
  - Node lantai produksi menampilkan WIP pieces, cycle time, indikator kesehatan (Stagnan, Bottleneck, Normal), dan port input/output yang terhubung dengan kurva bezier interaktif.

### B. Tenant Bordir (`bordir-uji` / CV Bordir Uji)
- **Peralihan Tenant**:
  - Berhasil berganti konteks ke `bordir-uji` via otentikasi demo superadmin multi-tenant.
- **Factory Flow Canvas (`/factory-flow`)**:
  - Header membaca tenant spesifik: `Alur Operasional Pabrik · bordir-uji (FOB Full Package)`.
  - Node Rantai Pasok & Bahan Baku memuat penyesuaian alur bordir: `Bahan Baku & Stok Kain` lengkap dengan loop rework/titik balik: *Penerimaan Retur Kain dari QC*.
  - Node Lantai Produksi memuat `Jadwal Mesin & SPK Massal` serta `Catatan Kerja Operator` dengan status bottleneck terpetakan tanpa kerusakan layout.

---

## 4. Pelajaran Arsitektur & Best Practices (What We Learned)

1. **Aturan Shadowing Parameter pada DSL Exposed**:
   - Saat mendefinisikan lambda `Table.insert { ... }`, hindari penggunaan nama identifier yang sama persis antara parameter fungsi luar (`occurredAt`, `shipmentId`, `tenantId`) dengan kolom tabel tanpa kualifikasi objek tabel.
   - **Selalu gunakan kualifikasi eksplisit**: `it[ShipmentEventsTable.occurredAt] = occurredAt.toString()`.
2. **Pola RLS Helper `apply_tenant_rls_in`**:
   - Fungsi migrasi database `apply_tenant_rls_in('schema', 'tabel')` mengharuskan tabel target memiliki kolom `tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE`. Setiap tabel pivot atau relasi relasional dalam skema modul wajib menyertakan kolom ini agar isolasi per-tenant berjalan fail-closed di level Postgres RLS.
3. **Konfigurasi Environment Server Dev vs Runner Test**:
   - Endpoint login demo (`/api/public/auth/demo`) menerapkan kebijakan *fail-closed* via `DemoLoginPolicy`: secara default dinonaktifkan kecuali environment `WEMADE_DEMO_LOGIN=on` disetel, dan tenant target terdaftar di `WEMADE_DEMO_TENANTS`. Hal ini mencegah celah bypass otentikasi di lingkungan non-dev.

---

## 5. Ringkasan Status Kriteria Konvergensi §5

| Kriteria §5 | Status | Bukti / Catatan |
|---|---|---|
| **1. B6 Selesai + Paritas A=B + Cek Visual Rajut & Bordir (K1)** | ✅ **Selesai** | `AccessSnapshotB6Test` lulus 100% (690 snapshot identik) + visual cek browser Wasm Compose terverifikasi untuk `wemade-demo` & `bordir-uji` |
| **2. Vertikal kedua berjalan dengan klien nyata** | ⏳ **Pending Klien** | Pack `layanan` / dinamis sudah tersedia secara arsitektural (B7); menunggu kesiapan deployment klien non-konveksi |
| **3. Migrasi data A→B teruji di salinan DB produksi (K2)** | ⏳ **Terblokir K2** | Menunggu tersedianya salinan database snapshot produksi aktual |
