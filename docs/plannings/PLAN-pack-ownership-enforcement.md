# PLAN: Penegakan Kepemilikan Pack — Track A / B

Spesifikasi: [`TRD-PLAT-005`](../trd/TRD-PLAT-005-pack-ownership-enforcement.md). Pekerjaan kecil (±2 hari), jadi dua track,
dipisahkan berdasarkan direktori yang dimiliki. Tidak ada track C: tidak ada UI.

## 0. Titik awal

Branch `feat/plat-004-p1-enforce-pack-owner` sudah memuat sebagian Track A (belum di-commit, **belum ditinjau**):
- `AssignTenantDomainPackUseCase` menegakkan pemilik, plus `AssignTenantDomainPackOwnerTest` (6 tes, lulus).
- Cabang reuse → bersama di `HandoffDiscoveryDraftUseCase` dan `HandoffResult.packBecameShared` (**belum diuji**).
- `DomainPackRoutes` dan `HandoffDiscoveryDraftUseCase` memanggil konstruktor baru (`packRepository`).
- Tes lama `handoff kedua dengan pack identik dipakai ulang…` **masih gagal** sampai diperbarui (tabrakan yang melahirkan TRD ini).

## 1. Track A — Core (`core/**`)

| # | Pekerjaan | Keluaran |
|---|---|---|
| A1 | Tinjau dan rapikan cabang reuse di `DiscoveryHandoffUseCases.kt` (pemilik dari versi tertinggi, `packBecameShared`) | Kode ditinjau |
| A2 | Perbarui `DiscoveryHandoffUseCasesTest`: handoff pertama `owner = tenant A`, `packBecameShared = false`; handoff kedua identik oleh B → berhasil, `packBecameShared = true`, `owner = null`, tenant B memakai pack; handoff ulang oleh pemilik → tidak dibagikan | Tes AC-5, AC-6 |
| A3 | Tes tambahan: draf tidak identik tetap 409 dan tidak membuat tenant yatim (AC-7); pelepasan lalu `assign` tenant C berhasil | Tes AC-7 |
| A4 | `AuditAction.TENANT_DOMAIN_PACK_SHARED` | Entri enum audit |
| A5 | Periksa ukuran `DomainPackUseCases.kt` dan `DiscoveryHandoffUseCases.kt` (soft 250 / hard 400) | `wc -l` sebelum dan sesudah |

**Selesai bila**: `:core:jvmTest` hijau dan segar; `:core:compileKotlinJs`/`WasmJs` bersih.

## 2. Track B — Server (`server/**`)

| # | Pekerjaan | Keluaran |
|---|---|---|
| B1 | `DiscoveryRoutes.kt`: tambahkan `packBecameShared` ke respons handoff | Respons 201 |
| B2 | Catat audit `TENANT_DOMAIN_PACK_SHARED` pada pelepasan (periksa dulu jangkauan `AuditLogRepository` dari `DiscoveryRouteFactory`) | Entri audit |
| B3 | `DomainPackApiTest`: 409 untuk pack milik tenant lain, 200 untuk pemilik, 200 untuk pack bersama; non-superadmin → 403 | Tes AC-8 |
| B4 | Tes respons handoff memuat `packBecameShared` dan audit tercatat | Tes AC-9 |
| B5 | Jalankan kueri pemeriksaan di DB produksi (di luar CI, oleh pemilik akses) dan tempel hasilnya di PR | Hasil kueri |

**Selesai bila**: `:server:compileTestKotlin` bersih; tes server yang relevan hijau dan segar.

## 3. Urutan dan ketergantungan

```
A1 → A2 → A3 ─┐
A4 ───────────┼──► B1 → B2 → B3 → B4 ──► gerbang integrasi ──► merge ──► B5 sebelum rilis
A5 ───────────┘
```
- Track B memakai `HandoffResult.packBecameShared` dan `AuditAction` dari Track A, jadi **A1 dan A4 lebih dulu**; A2/A3/A5 dapat berjalan sejajar dengan B1–B2.
- Merge A dulu, lalu B. Jangan merge A saja: tes handoff harus sudah diperbarui (A2) bersamaan dengan perilaku barunya.

**Gerbang integrasi**: kompilasi core JVM/JS/Wasm, `app:shared` JVM/JS/Wasm, `server` main+test; `:core:jvmTest` dan `:app:shared:jvmTest`
segar; tes server `*DomainPack*`, `*Discovery*`, `*RouteOwnership*`, `*ModuleSchema*`; `scripts/audit-variability.sh` tanpa temuan baru.

## 4. Risiko

| Risiko | Pencegah |
|---|---|
| Tenant lama sudah memakai pack milik tenant lain, jadi tidak bisa dipasang ulang setelah rilis | B5 sebelum rilis; DB dev bersih (0) |
| Pelepasan menjadi bersama dilakukan tanpa disadari | `packBecameShared` di respons + audit (FR-4) |
| Revisi pack bersama dengan `?ownerSlug=` memprivatkannya lagi | Didokumentasikan D4; di luar scope |
| `HandoffResult` punya konsumen lain | Diperiksa di B1 (default `false`, jadi kompatibel) |

## 5. Di luar rencana ini
- Fork per tenant (dibatalkan, TRD-PLAT-005 D1).
- Penegakan pada request-time (D3), setelah pengecualian sandbox dirancang.
- Tes arsitektur dan pemindai migrasi (TRD-PLAT-004 U2–U4).

## 6. Status Track A (2026-10-08, worktree `../wemkaeerp-pack-owner`, branch `feat/plat-005-pack-ownership`)

**A1–A5 selesai.**
- A1: cabang reuse ditinjau; ditambah **pengembalian kepemilikan bila `assign` gagal** setelah pack dilepas (sebelumnya handoff yang gagal bisa meninggalkan pack A menjadi bersama; kini dikompensasi, ditunjukkan tes).
- A2/A3: `DiscoveryHandoffUseCasesTest` — reuse oleh tenant lain berhasil dan dibagikan; pemilik sendiri tidak dibagikan; tenant ketiga memakai pack yang sudah bersama tanpa pelepasan baru; assign gagal mengembalikan pemilik; draf menyimpang tetap 409 tanpa tenant yatim.
- A4: `AuditAction.TENANT_DOMAIN_PACK_SHARED`.
- A5: `DomainPackUseCases.kt` 106→113, `DiscoveryHandoffUseCases.kt` 157→176, tes 197→237 baris (semua di bawah soft limit).

**Bukti** (segar, ±11:54): `:core:jvmTest` 1617 tes / 0 gagal; kompilasi core JS+Wasm, `app:shared` JVM/JS/Wasm, `server` main+test bersih.

**Sudah masuk dari Track B karena kompilasi menuntut atau murah**: `DomainPackRoutes` memakai konstruktor baru (wajib); `DiscoveryRoutes` memancarkan `packBecameShared` (B1). **Belum**: audit (B2), tes server 409/200/403 (B3), tes respons handoff (B4), kueri produksi (B5). `:server:test` dan `:app:shared:jvmTest` tidak dijalankan di worktree ini.

## 7. Status Track B (2026-10-08, worktree yang sama)

**B1–B4 selesai.**
- B2: `DiscoveryRoutes` mencatat `TENANT_DOMAIN_PACK_SHARED` saat `packBecameShared`; kegagalan menulis audit tidak membuat handoff yang berhasil terlihat gagal (`runCatching`). `auditLogRepository` dialirkan lewat `DiscoveryRouteFactory` dan `DomainRouteWiring` (satu baris diubah, tanpa menambah baris).
- B3: `DomainPackApiTest` — pack milik tenant lain → 409 (pesan tanpa id/slug pemilik, tenant tidak berubah); non-superadmin → 403; pemilik → 200; revisi tanpa `ownerSlug` mempertahankan pemilik.
- B4: `DiscoveryApiTest` — handoff pertama `packBecameShared=false`; kedua oleh tenant lain `true` + satu entri audit (ringkasan tidak menyebut pemilik lama); ketiga `false` tanpa audit baru.

**Koreksi desain yang ditemukan tes**: `SaveDomainPackDraftUseCase` mempertahankan pemilik lama (`ownerTenantId ?: latest?.ownerTenantId`). TRD D4/FR-5 semula salah menulis "revisi tanpa ownerSlug = bersama"; sudah dikoreksi. Satu-satunya jalan menuju pack bersama adalah handoff.

**Bukti** (segar, ±12:01): `:core:jvmTest` 1617/0; `:app:shared:jvmTest` 264/0; `:server:test` untuk `*DiscoveryApiTest*`, `*DomainPackApiTest*`, `*RouteOwnership*` 15/0; kompilasi core JS/Wasm, `app:shared` JS/Wasm, `server` main+test bersih.

**Tidak hijau, bukan dari perubahan ini**: `ModuleSchemaOwnershipTest` gagal karena membaca Postgres dev bersama yang kini memuat `fulfillment.fulfillment_routes` (migrasi Track B agent lain, worktree `wemkaeerp-track-b`); tabel itu tidak ada di pohon ini. `:server:test` penuh tidak dijalankan.

**Sisa**: B5 — kueri pemeriksaan di DB **produksi** sebelum rilis (belum dijalankan; DB dev bersih).
