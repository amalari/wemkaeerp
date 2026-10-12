# TRD-FUL-001: Packing Karton, Surat Jalan Eksternal & Konfirmasi Kirim (Modul FULFILLMENT)

## 1. Document Context and Administration

- **Title & ID**: TRD-FUL-001 — Packing & Pengiriman Eksternal di dalam modul `FULFILLMENT`
- **Status**: Draf 1 untuk persetujuan · **Tanggal**: 2026-10-12 · **Pemilik**: Achmad Jamaludin
- **Discovery Note**: [`docs/plannings/mvp/discovery-fulfillment-packing-shipping.md`](../../plannings/mvp/discovery-fulfillment-packing-shipping.md)
- **Referensi konsep bisnis**: [`planning-fulfillment-packing-and-shipping.md`](../../plannings/features/planning-fulfillment-packing-and-shipping.md) (era garment; konsep dipakai, rujukan teknis basi)
- **Stakeholders & Approvers**: Product (Achmad), Tech Lead, QA. Gerbang merge: verifikasi integrasi + cek visual Track D.

### Revision History

| Versi | Tanggal | Penulis | Catatan |
|---|---|---|---|
| 0.1 | 2026-10-12 | Kilo (atas persetujuan Achmad) | Draf dari Discovery Note |
| 0.2 | 2026-10-12 | Kilo | **Track 0 selesai.** VO + status + repository (termasuk `getNextCartonSequence`) + codec `PackingCartonCodec`/`DispatchShipmentCodec` + fixture & tes round-trip hijau. Keputusan format nomor (§4.5) diputuskan. |

### Summary & Business Context

Modul `FULFILLMENT` sudah menjadi node di kanvas dan punya kemampuan **transfer internal**
(`InternalTransfer`, `HandoverRoute`, `SuratJalanManifest` per-leg — TRD-FLOW-003). Yang belum ada:
aliran **keluar pabrik** — staf packing memverifikasi unit yang sudah lolos QC ke dalam karton,
mengsegelnya, menerbitkan Surat Jalan ke buyer/ekspedisi, dan mengonfirmasi pengiriman. Tanpa ini,
modul fulfillment berhenti di gerbang gudang dan pemicu invoice termin/pelunasan tidak pernah tersedia.

### Goals (In-Scope)

- Agregat `PackingCarton` dan `DispatchShipment` (SJ eksternal) di `core/.../domain/fulfillment/`
  dengan invariant: segel immutable + reopen hanya `MANAGE` + audit; karton wajib berisi unit yang
  sah diterima dari hulu (lolos QC via handover yang sudah ada).
- Persistensi: 3 tabel di schema `fulfillment` (`cartons`, `shipments`, `shipment_cartons`) + RLS
  per tenant, migrasi `V102`.
- 7 endpoint Ktor dengan gerbang `FULFILLMENT` fail-closed (VIEW/OPERATE/MANAGE sesuai operasi).
- Domain event `CartonSealed`, `ShipmentDispatched`, `ShipmentDelivered` (pemicu invoice = reaksi di
  modul invoicing, luar scope).
- Klien: API client + ViewModel + 2 layar Clay (Packing Bench, Surat Jalan Eksternal).
- Telemetri node: `wipPieces`, `cycleTimeHours`, `healthStatus` (Kontrak 6).
- Tes: domain unit, integrasi + RLS isolation, gerbang 403, paritas codec.

### Non-Goals (Out-of-Scope)

- Reaksi invoice (menerbitkan invoice otomatis saat `ShipmentDelivered`) — hanya event dikirim.
- Integrasi API tracking ekspedisi (kurir) — struktur `carrier` JSONB disiapkan, fase 2.
- Perubahan agregat internal (`InternalTransfer`, `SuratJalanManifest`) — lifecycle terpisah.
- Modul/divisi baru, kosakata pack baru, kontrak platform baru.
- Seed divisi `packing_shipping` (governance opsional; data tenant, admin mengatur via RBAC).

---

## 2. Functional Requirements

### 2.1 State machine

**Karton** (`PackingCarton`): `OPEN` → `SEALED` → `REOPENED` → `SEALED` (reopen memerlukan
`MANAGE` + alasan; jejak audit wajib). Transisi lain ditolak domain.

**Pengiriman** (`DispatchShipment`): `ISSUED` → `IN_TRANSIT` → `DELIVERED`. `CANCELLED` hanya dari
`ISSUED`, oleh `MANAGE`, dan melepaskan karton (karton kembali `SEALED`/terikat SJ lain).

### 2.2 Use cases (satu operasi bisnis = satu use case, `Result<T>`)

| # | Use case | Aturan domain |
|---|---|---|
| U1 | `GetPackingQueueUseCase` | Antrian = unit `InspectedAndGradedUnit` yang sudah diterima divisi (via handover/transfer existing) dan belum masuk karton mana pun; plus karton `OPEN` dan shipment `ISSUED/IN_TRANSIT`. |
| U2 | `CreatePackingCartonUseCase` | Setiap unit yang dimasukkan wajib ada di antrian (guard ditegakkan di domain, bukan UI). Rincian isi = peta item→qty (JSONB, key bebas). |
| U3 | `SealCartonUseCase` | Menolak karton kosong (qty=0) dan tanpa segel; setelah seal isi **terkunci**. |
| U4 | `ReopenCartonUseCase` | Hanya `SEALED`, hanya `MANAGE`, wajib alasan → event `CartonReopened` (audit). |
| U5 | `IssueShipmentUseCase` | Minimal 1 karton `SEALED`; generate nomor SJ unik per tenant; carrier & tujuan (JSONB) wajib non-kosong. |
| U6 | `MarkInTransitUseCase` | Hanya dari `ISSUED`; catat `shipped_at`. |
| U7 | `ConfirmDeliveredUseCase` | Hanya dari `IN_TRANSIT`; catat `delivered_at`; event `ShipmentDelivered`. |
| U8 | `CancelShipmentUseCase` | `MANAGE`, hanya `ISSUED`; karton dilepaskan. |

### 2.3 Endpoint API (semua di bawah `/api/tenant/fulfillment`, gate modul `FULFILLMENT`)

| Method | Path | Level | Fungsi |
|---|---|---|---|
| GET | `/packing-queue` | VIEW | U1: antrian unit + karton OPEN + shipment aktif |
| POST | `/cartons` | OPERATE | U2: buat karton (guard unit sah) |
| PATCH | `/cartons/{id}/seal` | OPERATE | U3 |
| PATCH | `/cartons/{id}/reopen` | MANAGE | U4 |
| POST | `/shipments` | OPERATE | U5: terbitkan SJ multi-karton |
| PATCH | `/shipments/{id}/in-transit` | OPERATE | U6 |
| PATCH | `/shipments/{id}/delivered` | OPERATE | U7 |
| DELETE | `/shipments/{id}` | MANAGE | U8: batalkan (ISSUED saja) |

Kesalahan: 400 validasi domain, 403 wewenang (fail-closed), 404 lintas-tenant/tak ada, 409 status
tak sah (mis. seal karton kosong, delivered sebelum in-transit).

### 2.4 Kepatuhan kontrak modul (module-integration-rules)

- **Kontrak 2 (port)**: masuk `InspectedAndGradedUnit`, keluar `DispatchedShipmentManifest` — keduanya
  sudah terdaftar di kosakata garment; kanvas menyambung dari port, bukan daftar tulisan tangan.
- **Kontrak 3 (stok)**: kepemilikan (`OWNED` vs `CONSIGNED`) dibaca dari parameter blueprint modul
  (`stockOwnershipFor`) dan dicatat di header SJ; fase 1 tanpa jurnal stok.
- **Kontrak 6 (telemetri)**: `wipPieces` = karton `OPEN` + unit antrian belum terpakai; `cycleTimeHours`
  = OPEN→SEALED dan SEALED→`IN_TRANSIT`; `healthStatus` dari batas antrian (konfigurasi tenant).
- **Kontrak 7 (fail-closed)**: keputusan RBAC tak terhitung → 403.

---

## 3. Non-Functional Requirements

| Category | Requirement & Target Metrics | Rationale / Mitigation |
| :--- | :--- | :--- |
| **Performance** | GET antrian < 300ms p95 pada 5.000 unit antrian; PATCH status < 200ms p95 | Indeks `(tenant_id, status, updated_at)`; pagination antrian bawaan |
| **Scalability** | Rincian isi & carrier sebagai JSONB; batas 500 karton aktif/tenant tanpa degradasi | JSONB menghindari perubahan skema untuk variasi item antar-industri (Kontrak 1 variability) |
| **Security** | RLS per tenant di ketiga tabel (`apply_tenant_rls_in`); gerbang fail-closed; input JSONB divalidasi skema ringan di route (panjang key ≤ 64, qty ≥ 0) | Pola RLS yang sudah berlaku di seluruh modul |
| **Availability & Reliability** | Mutasi idempoten untuk seal/in-transit/delivered (transisi status menolak duplikat → 409, bukan 500) | State machine menolak transisi ganda |
| **Maintainability & Observability** | Audit event reopen/cancel tercatat (actor, alasan, timestamp); log terstruktur untuk 403/409 | Kepatuhan jejak audit kontrak modul |

---

## 4. System Architecture & Technical Design

### 4.1 High-Level Architecture

```mermaid
flowchart LR
    QC[QUALITY_CONTROL] -- InspectedAndGradedUnit<br/>(handover existing) --> FF[Modul FULFILLMENT]
    subgraph FF
        PB[PackingCarton<br/>OPEN/SEALED/REOPENED]
        DS[DispatchShipment<br/>ISSUED/IN_TRANSIT/DELIVERED]
    end
    DS -- DispatchedShipmentManifest --> FIN[Finance / Invoicing (fase berikut)]
    DS --> EXT[(Buyer / Ekspedisi)]
    FF -. CartonSealed / ShipmentDispatched / ShipmentDelivered .-> BUS[Domain events]
```

### 4.2 Data Model & Schema (migrasi `V102`, schema `fulfillment` — sudah ada via V76)

```sql
CREATE TABLE IF NOT EXISTS fulfillment.cartons (
    id            VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    production_ref VARCHAR(64) NOT NULL,            -- referensi PO/SPK/batch (data)
    carton_number VARCHAR(40) NOT NULL,
    item_breakdown JSONB NOT NULL DEFAULT '{}',     -- {"S":40,...} key bebas per tenant/industri
    quantity_pcs  INT NOT NULL DEFAULT 0,
    gross_weight_kg DECIMAL(10,2) NOT NULL DEFAULT 0,
    unit_ticket_ids JSONB NOT NULL DEFAULT '[]',    -- bukti unit sah dari antrian (guard U2)
    seal_number   VARCHAR(40),
    status        VARCHAR(20) NOT NULL DEFAULT 'OPEN',  -- OPEN|SEALED|REOPENED
    created_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS fulfillment.shipments (
    id            VARCHAR(64) PRIMARY KEY,
    tenant_id     VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    shipment_number VARCHAR(40) NOT NULL,
    stock_ownership VARCHAR(30) NOT NULL,           -- dari blueprint modul (Kontrak 3)
    carrier       JSONB NOT NULL DEFAULT '{}',      -- mode/ekspedisi/kontainer/driver/resi
    destination   JSONB NOT NULL DEFAULT '{}',      -- buyer/alamat/kontak
    status        VARCHAR(20) NOT NULL DEFAULT 'ISSUED',
    shipped_at    TIMESTAMPTZ, delivered_at TIMESTAMPTZ,
    created_at / updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE IF NOT EXISTS fulfillment.shipment_cartons (
    shipment_id VARCHAR(64) NOT NULL REFERENCES fulfillment.shipments(id) ON DELETE CASCADE,
    carton_id   VARCHAR(64) NOT NULL REFERENCES fulfillment.cartons(id),
    PRIMARY KEY (shipment_id, carton_id)
);
SELECT apply_tenant_rls_in('fulfillment', 'cartons');
SELECT apply_tenant_rls_in('fulfillment', 'shipments');
SELECT apply_tenant_rls_in('fulfillment', 'shipment_cartons');
```

Tiga tabel didaftarkan di `ModuleSchemaMap` (kepemilikan schema `fulfillment`). Daftar di
`RouteOwnership` untuk prefix `/api/tenant/fulfillment` (bila belum tercakup pola transfer).

### 4.3 Komponen

- **Domain (`core`)**: `fulfillment/carton/{PackingCarton.kt, CartonValueObjects.kt,
  CartonRepository.kt}`, `fulfillment/shipment/{DispatchShipment.kt, ShipmentValueObjects.kt,
  ShipmentRepository.kt}`, event di `FulfillmentDomainEvents.kt`, use case U1–U8 di
  `fulfillment/usecases/`. Immutable copy, tanpa `var`, tanpa framework.
- **Server**: `FulfillmentPackingRepository` (Postgres, pola `PostgresSamplingOrderRepository`),
  `FulfillmentPackingRoutes.kt` (7 endpoint, gate `hasFulfillmentAccess` yang sudah ada), codec JSON
  reuse `PipelineGraphCodec`/Jackson sesuai pola modul.
- **Klien (`app/shared`)**: `FulfillmentPackingApiClient`, `PackingBenchViewModel` +
  `SuratJalanEksternalViewModel` (MVI), layar `PackingBenchScreen.kt` +
  `ShipmentListScreen.kt` (Clay; ikon `ClayIcons` — nol emoji), navigasi dari
  `AppNavScreen.FULFILLMENT` workspace.
- **Telemetri**: provider telemetri modul fulfillment memperluas `ModuleFeature` registry level 2.

### 4.4 Technology Usage & Tradeoff

| Keputusan | Alternatif | Alasan |
|---|---|---|
| Agregat `DispatchShipment` baru | Extend `SuratJalanManifest` internal | Lifecycle berbeda (leg internal vs kirim eksternal); campur = God aggregate |
| Rincian isi/carrier JSONB | Kolom per atribut | Variabilitas antar-industri (Kontrak 1); validasi skema ringan di route |
| Reuse handover untuk antrian QC | Tabel antrian packing sendiri | Satu sumber kebenaran unit sah; guard ditegakkan domain |
| Event `ShipmentDelivered` | Panggilan langsung ke invoicing | Satu arah dependensi; invoicing belum siap reaksi (non-goal) |

### 4.5 Assumptions, Constraints, Dependencies

- **Format nomor (diputuskan Track 0, 2026-10-12)**: karton `CTN-<tahun>-<seq>` dan SJ
  `SJ-<tahun>-<seq>`, seq 4 digit zero-padded, **unik per tenant** (bukan global). Format
  dikunci regex di value object (`CartonNumber`, `ShipmentNumber`) — payload di luar format
  ditolak saat parse/decode, bukan dinormalisasi. Seq diperoleh dari repository secara
  **transaksional** (`getNextCartonSequence` / `getNextShipmentSequence` per `tenantId`+tahun;
  implementasi Track B = counter tabel dalam transaksi, alternatif max+1). Bentrok unik pada
  konkurensi → unique index per tenant → 409 di lapisan server, bukan 500. Nomor tidak pernah
  didaur ulang, termasuk setelah cancel/reopen.
- Unit lolos QC tersedia lewat penerimaan handover yang sudah berjalan (TRD-FLOW-003).
- Schema `fulfillment` dan gerbang `hasFulfillmentAccess` sudah ada (V54/V61/V76).
- Nomor migrasi `V102` (terbaru saat ini `V101`); angka final ditetapkan saat implementasi.

---

## 5. Testing, Deployment, and Operations

### Effort Estimation

- **Rating**: **Medium** — 2 agregat + 3 tabel + 7 endpoint + 2 layar; tidak mengubah kontrak
  platform/kosakata pack; risiko utama di guard antrian dan integrasi telemetri.
- **Reasoning**: tiga lapisan penuh dan satu migrasi, tapi semua pola sudah ada di modul yang sama
  (transfer internal) — tinggal meniru. Estimasi 1 siklus Track 0 + paralel A/B/C + D.

### Parallel Execution Plan (Tracks)

- **Track 0 — Contract (serial, dibekukan dulu)**: VO + status enum + signature repository
  (`CartonRepository`, `ShipmentRepository`) + DTO JSON schema + fixture mock di `commonTest`.
  Output dibekukan sebelum A/B/C jalan.
- **Track A — Core & Domain**: agregat + invariant + U1–U8 + event + tes domain (merah→hijau).
- **Track B — Server & Persistence**: migrasi `V102` + `ModuleSchemaMap` + repository Postgres + RLS +
  7 route fail-closed + tes 403/409/RLS isolation.
- **Track C — Client & UI**: ApiClient + 2 ViewModel + 2 layar Clay + tes unit ViewModel.
- **Track D — Visual Check**: browser runtime di `wemade-demo` (superadmin) + `bordir-uji` (non-rajut)
  lebar ~1280dp; audit form states; kepatuhan Clay.

### Technical Acceptance Criteria

- AC1: `SealCarton` pada karton kosong → 409; reopen oleh OPERATE → 403; oleh MANAGE → 200 + audit.
- AC2: `CreateCarton` dengan unit tak ada di antrian → 400 (guard domain, bukan UI).
- AC3: Tenant A tidak pernah membaca karton/SJ tenant B (RLS; diverifikasi tes integrasi).
- AC4: Transisi ganda (delivered dua kali) → 409 idempoten.
- AC5: Codec round-trip `item_breakdown`, `carrier`, `destination`, status.
- AC6: Telemetri `wipPieces` = karton OPEN + unit antrian; tampil di node FULFILLMENT.
- AC7: Kompilasi 5 target + `audit-variability.sh` 0 temuan + cek visual Track D.

### Testing Strategy

- Domain: unit murni (state machine, guard, invariant) — pola `InternalTransferTest`.
- Server: integrasi Postgres scratch (RLS), gerbang 403 per level, 409 transisi; pola
  `PostgresSamplingOrderRepositoryIntegrationTest`.
- Klien: unit ViewModel (fake use case), codec test.
- Tenant kedua: uji di tenant non-garment bila pack-nya memuat modul fulfillment (bila belum, catat
  keterbatasan — fitur hidup di pack garment).

### Monitoring & Error Handling

- Log terstruktur: actor + alasan untuk reopen/cancel; metrik 403/409 per route.
- Pesan galat tidak membedakan "tak ada" vs "milik tenant lain" (404 sama).

### Deployment & Rollback

- Migrasi `V102` aditif (tabel baru di schema existing); rollback = drop tiga tabel (tanpa data
  produksi pada rilis pertama).
- Fitur di belakang entitlement modul yang sudah ada — bisa diaktifkan per tenant tanpa migrasi data.
