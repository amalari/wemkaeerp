# PLAN: Rute Serah Terima sebagai Data — Eksekusi Paralel Track A / B / C

Spesifikasi: [`TRD-FLOW-003`](../trd/TRD-FLOW-003-handover-routes-as-data.md). Aturan: Strangler Fig
(`tenant-variability-rules.md` Kontrak 8), satu paket per PR, tiap tahap lulus test paritas.

## 0. Kontrak yang dibekukan lebih dulu (serial, kecil — gerbang pembuka paralel)

**PR-0 (±0,5 hari, dikerjakan satu orang, merge dulu):**
- `HandoverRouteCode` (VO + parser tunggal), `HandoverRoute`, `TenantHandoverRoutes`,
  `SackRoute.toRouteCode()`; `DomainPack.handoverRouteTemplate` (default kosong).
- Antarmuka `HandoverRouteRepository` (port) di `core`.
- Kontrak JSON `route-settings` & `routes` (TRD §4.4) beserta fixture JSON di `commonTest/resources`.
- Jawab tiga hal "belum diverifikasi" TRD §4.6 dan tulis jawabannya di TRD.

Setelah PR-0 merge, A/B/C tidak lagi saling menunggu: B memakai port + fixture, C memakai fixture JSON.

## 1. Pembagian track (batas = kepemilikan file, supaya tidak bentrok)

| | **Track A — Core & Pack** | **Track B — Server & Persistensi** | **Track C — Klien & UI** |
|---|---|---|---|
| **Memiliki** | `core/**/domain/fulfillment/**`, `core/**/domain/pack/**`, `core/**/shared/fulfillment/**` | `server/src/main/**`, migrasi V96, `ModuleSchemaMap`, `RouteOwnership` | `app/shared/**/infrastructure/api/Fulfillment*`, `app/shared/**/presentation/fulfillment/**` |
| **Tidak boleh menyentuh** | server, app | core, app | core, server |
| **Bergantung pada** | PR-0 | PR-0 (port + fixture) | PR-0 (fixture JSON) |

### Track A — Core & Pack
1. A1 Template `garment`: dua rute = `SackRoute` (`code == name`, `label == displayName`).
2. A2 **Test paritas** iterasi `SackRoute.entries` + fixture template non-default (`bordir-uji`).
3. A3 `FulfillmentRouteConfig`: `modes` berkunci kode; `modeFor` (dikenal tanpa entri → `ADMIN_HUB`,
   tak dikenal → error); `hasAdminHubRoute`, `routesAccepting` atas rute aktif. Jembatan menjaga
   pemanggil lama tetap kompilasi.
4. A4 `InternalTransfer.leg: SackRoute` → `route: HandoverRouteCode`; `TransferLifecycleUseCases`;
   `InternalTransferDomainEvents`. **Ratchet**: `InternalTransfer.kt` 262 baris tidak boleh bertambah.
5. A5 Codec ketat: `InternalTransferCodec` menolak kode tak dikenal (hapus
   `?: SackRoute.QC_RAJUT_TO_FINISHING`), `FulfillmentRouteConfigCodec` tidak `mapNotNull` diam-diam.
- **Selesai bila**: test core hijau di 3 target (`compileKotlinJvm/Js/WasmJs` + `jvmTest`); paritas hijau.

### Track B — Server & Persistensi
1. B1 V96 aditif (`fulfillment_routes`, RLS, `CHECK` kode); lepas `CHECK` pada kolom `leg` bila ada.
   Uji dalam `BEGIN … ROLLBACK`. Daftarkan di `ModuleSchemaMap`.
2. B2 `PostgresHandoverRouteRepository`; `PostgresFulfillmentRouteConfigRepository` dan
   `PostgresInternalTransferRepository` membaca/menulis kode (string) tanpa enum.
3. B3 `FulfillmentRouteRoutes.kt` (baru): `GET/PUT /routes`; `PUT /route-settings` menolak kode
   tak dikenal (400). Route lama dicicil keluar dari `FulfillmentTransferRoutes.kt` (365 baris), tidak ditambah.
4. B4 Test gerbang: **peran tak berwenang → 403**, RBAC tak terhitung → 403, berwenang → 200;
   test rute non-garment; integrasi Postgres; `ModuleSchemaOwnershipTest`, `RouteOwnershipTest`.
- **Selesai bila**: `:server:compileTestKotlin` + test relevan hijau segar; migrasi teruji.
- **Catatan**: sebelum A3–A5 merge, B menjembatani lewat `SackRoute.toRouteCode()`; setelah merge,
  jembatan di sisi server dihapus pada PR B yang sama dengan rebase.

### Track C — Klien & UI
1. C1 **Pecah `TransferForms.kt` dulu** (392 baris, soft 400) per section sebelum diubah; cek apakah
   chip rute yang berulang layak naik ke `designsystem/` (Aturan Tiga Kali).
2. C2 `FulfillmentTransferApiClient` & `FulfillmentViewModel`: rute dari respons server;
   `SackRoute.entries` → daftar rute efektif; keadaan kosong untuk tenant tanpa rute.
3. C3 Layar konfigurasi rute/mode dari data; tanpa literal warna; token Clay; gate cabang
   `App.kt` memeriksa `accessDecisions`.
4. C4 Test ViewModel dengan API palsu non-garment.
5. C5 Cek visual `wemade-demo` dan `bordir-uji` (~1280dp); login superadmin via tombol demo.
- **Selesai bila**: 3 target app/shared terkompilasi, test hijau, dilihat dengan mata.

## 2. Urutan merge dan gerbang integrasi

```
PR-0 ──┬── A1→A2→A3→A4→A5 ──┐
       ├── B1→B2→B3→B4 ─────┼── integrasi ── S3 (hapus SackRoute + jembatan)
       └── C1→C2→C3→C4→C5 ──┘
```
- Tiap track merge ke `main` sendiri-sendiri, **urutan A → B → C** untuk bagian yang saling mengait
  (A3–A5 sebelum B menghapus jembatannya; B3 sebelum C2 diuji terhadap server nyata).
- **Gerbang integrasi** (setelah A, B, C merge): kompilasi 5 target + test core/app/server segar +
  `scripts/audit-variability.sh` + pemindai `SackRoute` + cek visual dua tenant.
- **S3**: hanya bila pemindai kosong; hapus enum, jembatan, dan test paritas versi iterasi enum
  (diganti test paritas terhadap fixture beku).

## 3. Risiko dan pencegahnya

| Risiko | Pencegah |
|---|---|
| Kontrak bergeser saat paralel | PR-0 membekukan VO, port, dan fixture JSON; perubahan kontrak = PR-0b yang diberitahukan ke ketiga track |
| Bentrok file antar track | Kepemilikan per direktori (tabel §1); tidak ada track yang menyentuh direktori lain |
| `InternalTransfer.kt` membengkak (262, di atas soft 250) | Ratchet; pecah aturan bukti ke file sendiri bila perlu |
| Tenant lama berubah perilaku | A2 + AC-2 TRD: JSON identik; tanpa seed |
| Fallback senyap kembali | A5 + AC-4; test menolak kode tak dikenal di tiga lapisan |
| Gerbang modul fulfillment ternyata tidak lengkap | Dijawab di PR-0; B4 menambah test 403 yang sekarang mungkin belum ada |

## 4. Di luar rencana ini (sengaja)
- Jalur narasi/builder → rute (`SpecOp` atau sejenisnya): TRD terpisah setelah S3, karena sink-nya
  (spec prototype vs konfigurasi tenant) belum diverifikasi.
- Penyatuan `InternalTransfer` dan `SuratJalanManifest`: ditolak (TRD §1 Non-Goals).

## 5. Status PR-0 (2026-10-08, branch `feat/flow-003-pr0-handover-route-contract`, belum di-commit)

**Selesai**: `HandoverRouteCode`/`HandoverRoute`/`TenantHandoverRoutes` (+ `resolve`),
`HandoverRouteRepository` (port, termasuk `codesInUse`), `SackRoute.toRouteCode()`,
`DomainPack.handoverRouteTemplate` (aditif, default kosong; codec + `matchesShipped` mengikuti pola
`roleHints`), `HandoverRouteSettingsView`, dan kontrak JSON `HandoverRouteSettingsCodec`
(commonMain, jadi server dan klien memakai kode yang sama — pengganti fixture berkas).
Tiga hal "belum diverifikasi" terjawab (TRD §4.6). 19 tes baru.

**Bukti** (segar, 2026-10-08 ±10:00): `:core:jvmTest` 1591 tes/0 gagal; `:app:shared:jvmTest` 264/0;
tes server `*DomainPack*`, `RouteOwnershipTest`, `ModuleSchemaOwnershipTest` 5/0; kompilasi
`core` JS+Wasm, `app:shared` Jvm/JS/Wasm, `server` main+test: bersih.

**Belum / bukan klaim**: `:server:test` penuh tidak dijalankan. `TechPackApiTest` (5 tes) timeout 1 menit
saat `--tests '*Pack*'`; tidak menyentuh kode ini, tetapi saya **belum membuktikan** bahwa ia gagal juga di
`main`. Tidak ada cek visual (PR-0 tanpa UI). `DomainPack.kt` kini 274 baris (soft 250, hard 400; +4).

## 6. Status Track A (2026-10-08, branch `feat/flow-003-track-a-garment-template`)

**A1 + A2 selesai**: `GarmentHandoverRoutes.template` (data literal, dua rute = `SackRoute`), dipasang di
`GarmentDomainPack`; `GarmentHandoverRoutesParityTest` mengiterasi `SackRoute.entries` (kode, label, urutan),
memastikan tidak ada pack lain yang meminjam rute garment, membandingkan mode efektif dan JSON dengan
`FulfillmentRouteConfig`/`FulfillmentRouteConfigCodec` lama. **Belum**: A3–A5 (config berkunci kode,
`InternalTransfer.route`, codec ketat) — `SackRoute` masih dipakai semua pembaca.
