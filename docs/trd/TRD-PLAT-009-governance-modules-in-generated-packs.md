# TRD-PLAT-009: Modul Tata Kelola Wajib di Pack Hasil Handoff

## 1. Konteks dan Administrasi

- **ID**: TRD-PLAT-009 — Modul tata kelola di pack hasil jalur generatif
- **Status**: **Disetujui dan di-merge** (2026-10-09; keputusan 1–6 ditinjau pengguna). Catatan: urutan seksi/modul governance di depan daftar pack belum dilihat di UI; handoff ulang pack hasil handoff lama kini 409 sampai ada versi pack baru.
- **Riwayat**: 0.1 — 2026-10-09 — Claude (riset dari kode). Temuan cek visual butir 4.
- **Rujukan**: TRD-PLAT-004/005 (kepemilikan pack), TRD-PLAT-008 (pola kerja), `module-integration-rules.md` §5.2/§5.6,
  `tenant-variability-rules.md` Kontrak 5/7.

### Masalah (diverifikasi dari kode dan DB scratch)

1. Pack hasil handoff (`klinik`, `bordir`) tidak memuat `org_chart` maupun `dynamic_rbac`
   (`domain_packs.definition::text like '%dynamic_rbac%'` = false). Pack tulisan tangan `docs/packs/klinik-uji.pack.json` memuat keduanya.
2. `/api/tenant/roles` dan `/api/tenant/module-assignments` dijaga `moduleGate(DYNAMIC_RBAC)`; `AccessDecisionEngine.explain`
   memeriksa entitlement (`grantedModules`) **sebelum** bypass Owner; `grantedModules` bawaan = modul pack tenant.
   Pack tanpa modul itu → `NOT_ENTITLED` → 403 beruntun bagi Owner. Mesin benar; datanya yang kurang.
3. `DomainPack.sharedModules` mewajibkan modul bersama bersifat operasional, jadi governance tidak otomatis ikut pack
   baru; rujukan (`moduleReferences`) hanya untuk modul bersama berslot (aturan R1: tata kelola = **salin identik**).

## 2. Persyaratan

- **FR-1** Pack non-bawaan yang dihandoff **wajib** memuat seksi `GOVERNANCE` dan modul `org_chart` + `dynamic_rbac`
  sebagai salinan identik definisi di `GarmentModules` (jenis `GOVERNANCE`, `slot = null`).
- **FR-2** Idempoten: pack yang sudah memuatnya (identik) tidak berubah dan tidak menggandakan; definisi berbeda
  tetap ditolak oleh `DomainPackRegistry.identityViolations` (tidak ditimpa).
- **FR-3** Pack bawaan garment tidak disentuh (byte-identik); `DomainPackCodec`/`GET /api/tenant/pack` tak berubah.
- **FR-4** Modul governance tidak di kanvas, tidak dihitung kuota; peran preset dan fail-closed tak berubah.
- **NFR** Domain murni di `core`, tanpa `!!`/`else` baru; ukuran file core 250/400.

## 3. Titik Pendaftaran (grep segar 2026-10-09)

| Titik | Tindakan |
|---|---|
| `domain/pack/GovernanceModules.kt` (baru) | fungsi murni `DomainPack.withGovernanceModules()` |
| `DiscoveryHandoffUseCases.kt` | terapkan pada pack non-bawaan sebelum bandingkan/simpan/kunci |
| `DomainPack.kt`, `DomainPackCodec.kt`, `ShippedPackIdentity.kt`, `AccessDecisionEngine`, `TenantResolutionPlugin`, `ModuleGate` | **tidak disentuh** |
| Migrasi | tidak ada |

## 4. Keputusan dan Opsi yang Ditolak

**K1 — Disuntik di handoff (`HandoffDiscoveryDraftUseCase`), pada salinan pack yang disimpan/dikunci; draf tidak diubah.**
Handoff adalah satu-satunya titik tempat pack hasil generatif menjadi pack **runtime** tenant (pack disimpan, dikunci,
dipasang); semua pembuat (wawancara, deterministik, LLM, UI, fixture) lewat sini. Draf adalah catatan hasil wawancara
(domain bisnis klien) dan draf LOCKED tidak boleh berubah (Kontrak 5), jadi tata kelola — milik platform, bukan hasil
wawancara — ditambahkan saat pack runtime dibentuk. Ditolak: (a) di pembuat draf/agent — banyak pembuat, LLM bisa
lupa, draf terkunci lama tak tertangani; (b) validator yang memaksa — menggagalkan draf lama dan fixture, serta
mengubah kontrak agent tanpa perlu; (c) saat tenant dimuat (memperkaya pack di memori) — dua sumber kebenaran, pack
di DB berbeda dengan yang dipakai, dan menyentuh jalur resolusi tenant (dilarang).

**K2 — Salinan identik dari `GarmentModules`, bukan rujukan.** Aturan R1 mewajibkan tata kelola disalin identik;
identitas global `ModuleId` menjamin definisi sama lintas pack. Seksi `GOVERNANCE` ditambahkan bila belum ada
(invarian `DomainPack`: modul menunjuk seksi yang ada). `factory_flow` **tidak** ikut: ia bukan syarat bagian ini
dan menyalinnya mengubah kanvas; hanya dua modul yang diminta.

**K3 — Pack lama hasil handoff tidak ditimpa diam-diam.** Handoff ulang pack data yang versi tersimpannya tak memuat
governance menghasilkan pack berbeda dari yang efektif → 409 "butuh review manual" (aturan "versi sama wajib
identik" tetap). Perbaikan data (tak ada di DB dev `wemake_erp`, semua tenant garment): simpan ulang pack dengan
`withGovernanceModules()` sebagai **versi baru**, kunci, lalu pin tenant ke versi itu.

**K4 — Entitlement tersimpan tidak disentuh.** `grantedModules` bawaan = modul pack tenant; baris
`tenant_module_entitlements` hanya menyimpan grant bila **berbeda** dari katalog (`null` = semua). Tenant hasil handoff
yang baru tak punya grant sempit sehingga mewarisi governance otomatis. Tenant lama yang grant-nya sudah disempitkan
manual perlu grant ulang oleh superadmin (dialog entitlement), bukan migrasi.

**K5 — `sharedModules` tak berubah.** Governance tidak ditawarkan sebagai modul bersama (invarian: harus operasional);
ia tersalin identik.

## 5. Kompatibilitas

Garment: nol perubahan (pack bawaan tak melewati jalur penyuntikan; tes byte-identik). Pack tulisan tangan yang sudah
memuatnya: idempoten. Tenant hasil handoff lama: lihat K3/K4.

## 6. Risiko

| Risiko | Mitigasi |
|---|---|
| Draf yang menamai modul sendiri `org_chart` dengan definisi lain | Ditolak registry (identitas global), bukan ditimpa |
| Governance muncul di kanvas/kuota | Tes: `slot == null`, bukan di katalog operasional, kuota tak berubah |
