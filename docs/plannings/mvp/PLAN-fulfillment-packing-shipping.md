# PLAN: FULFILLMENT Packing & Pengiriman Eksternal — Track 0/A/B/C/D

**Tanggal**: 2026-10-12 · **Penulis**: Achmad Jamaludin (dibantu Kilo)
**Spesifikasi**: [`TRD-FUL-001`](../../trd/features/TRD-FUL-001-packing-dan-pengiriman.md) · **Discovery**: [`discovery-fulfillment-packing-shipping.md`](discovery-fulfillment-packing-shipping.md)
**Status**: TRD disetujui · **Track 0 selesai 2026-10-12 (kontrak dibekukan)** — lanjut Track A/B/C.

---

## 0. Titik awal (terverifikasi di kode, 2026-10-12)

- Modul `FULFILLMENT` sudah lengkap sebagai modul: `GarmentModules.FULFILLMENT` + slot +
  node kanvas + entitlement + preset peran (`CustomRole.kt` OPERATE/VIEW/MANAGE/NONE).
- Kemampuan yang sudah jalan (jangan ditulis ulang): transfer internal `InternalTransfer` +
  `HandoverRoute` (TRD-FLOW-003), `SuratJalanManifest` per-leg internal, route
  `FulfillmentTransferRoutes`/`SuratJalanRoutes`, gerbang `hasFulfillmentAccess` (OPERATE/MANAGE),
  schema `fulfillment` (V54/V61/V76), layar `FulfillmentWorkspaceScreen`.
- Yang dibuat (baru): agregat `PackingCarton` + `DispatchShipment`, tabel `cartons`/`shipments`/
  `shipment_cartons` (V102), 7 route packing/shipment, API client + 2 layar, telemetri level 2.

## 1. Track 0 — Contract (serial, dibekukan dulu)

| # | Pekerjaan | Keluaran |
|---|---|---|
| 0.1 | VO + status: `CartonNumber`, `SealNumber`, `ShipmentNumber`, `CartonStatus(OPEN/SEALED/REOPENED)`, `ShipmentStatus(ISSUED/IN_TRANSIT/DELIVERED/CANCELLED)` di `core/.../fulfillment/` | File contract |
| 0.2 | Interface repository: `CartonRepository`, `ShipmentRepository` (signature U1–U8: antrian, CRUD + transisi) | Interface + KDoc |
| 0.3 | DTO JSON schema (codec karton/shipment) + fixture mock di `commonTest/resources` | Codec test merah→hijau round-trip |
| 0.4 | Keputusan format nomor: `CTN-<tahun>-<seq>`, `SJ-<tahun>-<seq>` unik per tenant (seq transaksional) | Catatan di TRD §4.5 |

**Selesai bila**: `:core:jvmTest` hijau untuk codec/fixture; kontrak dibekukan (A/B/C tidak mengubah
signature tanpa kembali ke Track 0).

**Hasil (2026-10-12, semua terpenuhi)**: 0.1–0.2 sudah ada dari commit `70380f4c` + penambahan
`getNextCartonSequence` pada `CartonRepository` (mirror `getNextShipmentSequence`). 0.3 =
`PackingCartonCodec` + `DispatchShipmentCodec` + `PackingShipmentCodecTest` (bentuk terkunci,
round-trip, fixture mock eksternal, penolakan payload korup) — `:core:jvmTest` hijau. 0.4 =
keputusan format nomor dicatat di TRD §4.5 (v0.2). **Kontrak dibekukan** — perubahan signature
kembali ke Track 0.

## 2. Track A — Core & Domain

| # | Pekerjaan | Keluaran |
|---|---|---|
| A1 | `PackingCarton` + invariant (seal menolak kosong; isi terkunci saat SEALED) + `CartonDomainEvents` | Domain + tes merah→hijau |
| A2 | `DispatchShipment` + state machine (§2.1 TRD) + event dispatched/delivered | Domain + tes |
| A3 | Use case U1–U8 (guard antrian U2 di domain) | Use cases + tes |
| A4 | Telemetri pure function: `wipPieces`, `cycleTimeHours`, `healthStatus` | Fungsi murni + tes |

**Selesai bila**: `:core:jvmTest` hijau; tes state machine mencakup transisi ganda (409) & reopen tanpa MANAGE.

## 3. Track B — Server & Persistence

| # | Pekerjaan | Keluaran |
|---|---|---|
| B1 | Migrasi `V102` (3 tabel + index + `apply_tenant_rls_in`) + daftar `ModuleSchemaMap` + `RouteOwnership` prefix | Migrasi teruji Flyway |
| B2 | `PostgresFulfillmentPackingRepository` (cartons+shipments+pivot, dbQuery RLS) | Repository + integrasi scratch |
| B3 | `FulfillmentPackingRoutes` 7 endpoint, gate `hasFulfillmentAccess` per level, fail-closed | Route + tes |
| B4 | Tes: 403 per level, 409 transisi, RLS tenant isolation, integrasi antrian (fixture dari Track 0) | Tes hijau segar |

**Selesai bila**: `:server:test` terarah hijau segar; `J3MigrationFenceTest` tetap hijau (V102 bukan migrasi J3 — tidak menyentuh schema lain selain `public.tenants` + schema sendiri).

## 4. Track C — Client & UI

| # | Pekerjaan | Keluaran |
|---|---|---|
| C1 | `FulfillmentPackingApiClient` + DTO mapping + error mapping (403/409 → UiEffect) | Client + tes |
| C2 | `PackingBenchViewModel` + `ShipmentListViewModel` (MVI; state Loading/Loaded/Denied/Failed) | VM + unit test |
| C3 | `PackingBenchScreen` (antrian + builder karton + seal/reopen) + `ShipmentListScreen` (SJ + status + cetak placeholder) — Clay, token, ikon `ClayIcons` | Layar + komponen |
| C4 | Navigasi dari workspace FULFILLMENT (level 2 fitur modul di registry TRD-FLOW-002) | Wiring |

**Selesai bila**: kompilasi JVM/JS/Wasm hijau; `:app:shared:jvmTest` hijau; nol literal warna/bentuk (DoD design system).

## 5. Track D — Visual Check & Verifikasi

1. Runtime: server `PORT=8091` + web `WEMADE_WEB_PORT=3011`, `WEMADE_DEMO_LOGIN=on`.
2. `wemade-demo` (superadmin): buka Packing Bench → buat karton dari antrian → segel → coba edit
   (tertolak) → reopen sebagai MANAGE.
3. `bordir-uji` (tenant non-rajut): verifikasi layar konsisten; `Sales`/peran NONE: 403 & kartu
   "Tanpa Akses" (PLAT-012 Q2).
4. Lebar ~1280dp: kartu tidak saling timpa; tier font tidak pecah per huruf.

## 6. Urutan & gerbang integrasi

```
Track 0 (dibekukan) ──► A / B / C (paralel, worktree masing-masing) ──► integrasi ──► D
```
- Track B/C membaca kontrak Track 0; konflik hanya diizinkan pada `FulfillmentDomainEvents.kt`
  (append event) — diselesaikan integrator.
- **Gerbang integrasi**: `:core:jvmTest`, `:server:test`, `:app:shared:jvmTest` segar di worktree
  bersih (`--rerun-tasks`); kompilasi 5 target; `scripts/audit-variability.sh` 0 temuan;
  `graphify update .`; teaching doc (§12).

## 7. Risiko & mitigasi

| Risiko | Mitigasi |
|---|---|
| Guard antrian (U2) bergantung pada data handover yang bentuknya beda per pack | Fixture Track 0 menetapkan bentuk; pack tanpa handover → antrian kosong (layar empty state, bukan error) |
| Nomor urut CTN/SJ bentrok di konkurensi | Seq dalam transaksi + unique index per tenant; bentrok → retry 409 |
| V102 menyentuh batas pagar migrasi J3 | V102 bukan migrasi J3 (schema garment) — `J3MigrationFenceTest` menjaga; daftar putih `public.tenants` sah |
| Scope creep ke invoicing/tracking ekspedisi | Non-goal tertulis; event `ShipmentDelivered` = satu-satunya jembatan |
| Migrasi `V102` bentrok dengan nomor migrasi sesi lain | Cek `ls db/migration` saat mulai Track B; pakai nomor kosong berikutnya dan catat di TRD |

## 8. Definition of Done (keseluruhan)

- [ ] AC1–AC7 TRD terpenuhi (tes + bukti) · [ ] 5 target kompilasi · [ ] audit 0 temuan
- [ ] Track D selesai (cek mata 2 tenant) · [ ] teaching doc `teaching-ful-001-*.md`
- [ ] TRD status → SELESAI dengan catatan revisi
