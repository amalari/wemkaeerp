# PLAN: Jalur Kepemilikan Modul — P2–P6 (Track A / B / C)

Spesifikasi: [`TRD-PLAT-004`](../trd/TRD-PLAT-004-module-ownership-lanes.md) §4.3 dan §4.5 (P1–P6 **diputuskan 2026-10-08**, sesuai rekomendasi).
P1 sudah selesai (TRD-PLAT-005, di `main`). Setiap track dikerjakan di **worktree sendiri dari `main`** supaya tidak bentrok dengan agent lain.

## 0. Jejak kode "khusus tenant" saat ini (terverifikasi dengan grep, 2026-10-08)

Satu-satunya modul J3 adalah pilot `layanan_change_request`. Jejaknya:
- `core/.../domain/pack/LayananPilotPack.kt`
- `server/.../routes/LayananChangeRequestRoutes.kt`, `infrastructure/PostgresLayananChangeRequestRepository.kt`, `infrastructure/tables/LayananChangeRequestTables.kt`
- Migrasi `V90__layanan_change_request_module.sql` (schema `layanan_change_request`; satu-satunya rujukan keluar: `tenants(id)`)
- Seeder `cli/PilotTenantSeeder.kt`
- **Tiga impor dari mesin ke kode J3**: `RouteOwnership.kt`, `ModuleSchemaMap.kt`, `PilotTenantSeeder.kt` (mengimpor `LayananPilotPack`); plus `DomainRouteWiring.kt` memanggil `layananChangeRequestRoutes(...)`.

(Pencarian kata "layanan" juga mengenai vendor, org chart, dsb.; itu kata biasa "layanan", bukan jejak J3.)

## 1. Track A — Kebijakan dan Discovery (P3, P5, P6) — dokumen saja

| # | Pekerjaan | Keluaran |
|---|---|---|
| A1 | Tambah ke skill `wemade-feature-discovery` pertanyaan: **"Mungkin dipakai tenant lain? Jika ya, lahirkan di pack bersama sejak awal."** Letakkan di Langkah 3 (Jenisnya apa?) dan sebagai baris di `discovery-note-template.md` | SKILL.md + template |
| A2 | Tulis kebijakan P5 di `module-integration-rules.md` §5: promosi J3 → J2 = **salin ke pack baru** (kode dan prefiks baru), jangan ganti nama modul yang sudah punya tabel; `derivedFrom` ditunda sampai promosi pertama | Aturan tertulis |
| A3 | Tulis P3 di aturan yang sama: kode khusus tenant tetap di pohon sumber dan binary yang sama selama pagar P2 dan P4 hijau; kapan itu ditinjau ulang (tenant menuntut kode tertutup) | Aturan tertulis |
| A4 | `scripts/sync-agent-config.sh`, lalu `--check` | Sinkron lintas tool |

**Selesai bila**: `scripts/sync-agent-config.sh --check` keluar 0. Tanpa kode, jadi tanpa kompilasi.

## 2. Track B — Pagar impor (P2)

Tujuan: mesin (J0) dan pack bawaan (J1) tidak mengimpor kode J3 secara langsung; J3 masuk lewat **satu registri**.

| # | Pekerjaan | Keluaran |
|---|---|---|
| B1 | Kumpulkan J3 per pack di satu paket: `…/domain/pack/tenant/layanan/` (pindah `LayananPilotPack`); sisi server `…/tenant/layanan/` (routes, repository, tables). Hanya pindah paket, tanpa mengubah perilaku, id modul, schema, atau migrasi | Paket J3 |
| B2 | Registri `TenantPackContributions` (satu file, satu-satunya pemilik impor J3): tiap J3 menyumbang `pack`, `tabel per schema`, dan `awalan route` | Registri |
| B3 | `RouteOwnership`, `ModuleSchemaMap`, `PilotTenantSeeder`, dan `DomainRouteWiring` membaca registri; impor langsung `LayananPilotPack` dihapus | Tiga impor hilang |
| B4 | **Tes arsitektur yang memblokir**: pemindai sumber gagal bila berkas di luar paket J3 dan di luar registri mengimpor `…pack.tenant.` atau `…tenant.<pack>.`. Uji dengan fixture pelanggar (tes harus gagal bila ada impor liar) | Tes memblokir |
| B5 | Pastikan `RouteOwnershipTest`, `ModuleSchemaOwnershipTest`, dan tes gerbang `layanan` tetap hijau, dan tenant `layanan-demo` masih jalan (id modul, schema, dan V90 tidak berubah) | Paritas |

**Selesai bila**: core dan server kompilasi (JVM/JS/Wasm untuk core), tes terkait hijau segar, B4 gagal pada fixture pelanggar dan lulus pada kode asli.

## 3. Track C — Pagar migrasi (P4)

Tujuan: migrasi modul J3 tidak boleh mengunci diri ke skema modul lain.

| # | Pekerjaan | Keluaran |
|---|---|---|
| C1 | Tentukan "migrasi J3" secara mekanis: migrasi yang membuat atau mengubah schema milik modul J3 yang terdaftar (kontrak `ModuleSchemaMap`/registri B2). Sampai B2 selesai, pakai awalan kode pack `layanan_` | Aturan deteksi |
| C2 | Pemindai SQL (tes server, membaca `db/migration/*.sql`): referensi `REFERENCES` / `JOIN` / `FROM` lintas schema dari migrasi J3 hanya boleh ke **daftar putih**: `public.tenants`, `public.users`, schema miliknya sendiri, dan schema modul yang dirujuk lewat `moduleReferences` | Tes memblokir |
| C3 | Fixture pelanggar: migrasi J3 palsu yang mereferensikan `crm_sales.*` harus membuat tes gagal; V90 asli harus lolos | Bukti pagar bekerja |
| C4 | Dokumentasikan di `ModuleSchemaMap` KDoc bahwa B8 (FK/JOIN lintas schema) tetap berlaku untuk modul garment; pembatasan ini khusus J3 | Dokumentasi |

**Selesai bila**: tes server hijau segar; V90 lolos; fixture pelanggar gagal.

## 4. Urutan dan ketergantungan

```
Track A ──────────────────────────────► (mandiri, boleh kapan saja)
Track B: B1 → B2 → B3 → B4 → B5
Track C: C1 ─ (pakai awalan `layanan_` dulu) → C2 → C3 → C4
              └──► setelah B2 merge: ganti deteksi C1 ke registri (satu perubahan kecil)
```
- A mandiri. B dan C dapat jalan paralel karena C1 sementara memakai awalan pack, bukan registri; satu tindak lanjut kecil menyambungkannya ke registri setelah B2 masuk.
- Merge A, lalu B, lalu C. Tiap track lulus tesnya sendiri sebelum merge.
- **Gerbang integrasi**: kompilasi core JVM/JS/Wasm, `app:shared` JVM/JS/Wasm, server main+test; `:core:jvmTest`, `:app:shared:jvmTest`, dan tes server terkait segar; `scripts/audit-variability.sh` tanpa temuan baru.

## 5. Risiko

| Risiko | Pencegah |
|---|---|
| Memindah paket `LayananPilotPack` memutus pack yang tersimpan (`domain_packs`) atau tes yang mengimpornya | Hanya paket Kotlin yang berpindah; kode pack `layanan`, id modul, schema, dan V90 tidak berubah; tes yang mengimpor ikut diperbarui |
| Pemindai impor memberi positif palsu pada tes atau dokumentasi | Pemindai hanya membaca `src/main` dan `src/commonMain`; tes dikecualikan dengan alasan tertulis |
| Daftar putih SQL terlalu ketat bagi modul J3 yang sah di masa depan | Daftar putih bisa diperluas lewat `moduleReferences` (sudah dalam desain); keputusan tiap perluasan dicatat di PR |
| Pagar B4 hanya menjaga pola impor, bukan akses lewat refleksi atau string | Diterima; pagar menangkap kesalahan lazim, bukan sabotase. Dicatat sebagai batas |
| `scripts/audit-variability.sh` "melapor, tidak memblokir" membuat orang menganggap pagar ini opsional | B4 dan C2 adalah **tes**, bukan skrip audit, sehingga memblokir CI |

## 6. Di luar rencana ini
- Memecah binary atau repo per tenant (P3 memutuskan tidak sekarang).
- Penegakan pemilik pack pada request-time (TRD-PLAT-005 D3).
- Jalur narasi ke konfigurasi lewat builder (TRD tersendiri).
- Field `derivedFrom` dan alat promosi (menunggu promosi pertama).

## 7. Status Track A (2026-10-08, branch `docs/plat-004-decisions`)

**A1–A4 selesai.** A1: pertanyaan "Mungkin dipakai tenant lain?" di Langkah 3 skill Discovery dan di §3 template Discovery Note. A2/A3: §5.6 baru di `module-integration-rules.md` (jalur kepemilikan, promosi = salin, kode tenant satu binary selama pagar hijau, batasan migrasi modul tenant, pack berpemilik). A4: `scripts/sync-agent-config.sh` lalu `--check` keluar 0.
Dokumen saja; tidak ada kode yang dikompilasi atau dites. Pagar yang dijanjikan di §5.6 (impor dan migrasi) **belum ada** — itu Track B dan C.
