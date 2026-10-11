# Planning — SPK Split per Size (1 PO Multi-Size → N SPK, 1 Ukuran per SPK)

> **Status**: DRAFT — menunggu persetujuan implementasi per fase.
> **Prinsip**: PO di Deal boleh multi-size, tetapi begitu turun jadi SPK (sampling maupun
> produksi massal), **1 SPK = 1 ukuran**. Alasan bisnisnya: seluruh data turunan — program CAM,
> gramasi panel, waktu rajut (`PanelSizeSpec`), bundel traceability, hingga laporan progres —
> semuanya dimiliki per ukuran. Menumpuk banyak ukuran dalam satu SPK memaksa setiap lapisan di
> bawahnya menebak "ukuran mana yang sedang dikerjakan".

---

## 1. Kondisi Saat Ini (Gap Analysis)

| Titik alur | Perilaku sekarang | File bukti |
|---|---|---|
| PO di Deal | Multi-size via `PurchaseOrderLine(description, quantity)` | `core/.../deal/DealValueObjects.kt:70` |
| Launch SPK massal | `deriveSizeBreakdown()` flatmap **semua** baris PO → **satu** `BulkWorkOrder` dengan `sizeBreakdown: List<BulkSizeLine>` | `core/.../production/usecases/LaunchBulkWorkOrderFromDealUseCase.kt:99` |
| Idempotensi launch | "Satu deal hanya boleh punya satu SPK massal aktif" (klik kedua = return SPK lama) | idem, baris 36–37 |
| Route | `POST /production/work-orders/launch-from-deal` mengembalikan **satu** objek SPK | `server/.../routes/ProductionRoutes.kt:79` |
| Client | `ProductionApiClient.launchFromDeal(...)` mengembalikan `Result<BulkWorkOrder>` tunggal | `app/shared/.../api/ProductionApiClient.kt:24` |
| Sampling SPK | 1 desain = 1 SPK dengan `sizeMatrix` qty per ukuran (`MULTI_SIZE`) | `core/.../sampling/SamplingSizeMatrix.kt` |

## 2. Target

```
Deal (PO: 100 S, 200 M, 150 L)
        │  launch
        ├─► SPK-BLK-0001 ── size: S ── 100 pcs
        ├─► SPK-BLK-0002 ── size: M ── 200 pcs
        └─► SPK-BLK-0003 ── size: L ── 150 pcs
```

- Progres tahap (potong/jahit/finishing), WIP, alokasi lini, dan traceability **otomatis jadi per
  ukuran** karena semua mekanisme itu sudah bekerja per-SPK — tidak perlu diubah.
- Data turunan per ukuran (program CAM, `PanelSizeSpec`, label karung) menempel langsung ke SPK
  ukurannya, bukan ke "salah satu baris dalam list".

---

## 3. Pilar 1 — Database & Persistence

- **Migrasi baru** `server/src/main/resources/db/migration/V??__bulk_work_order_size_label.sql`:
  ```sql
  ALTER TABLE bulk_work_orders ADD COLUMN size_label VARCHAR(60) NULL;
  -- Backfill untuk SPK lama yang kebetulan sudah single-line (ambil sizeLabel pertama).
  CREATE INDEX idx_bulk_work_orders_deal_size ON bulk_work_orders (tenant_id, deal_id, size_label);
  ```
- `size_label` **nullable** agar baris legacy multi-size tetap valid (lihat §8).
- Tabel Exposed `BulkWorkOrdersTable` + `PostgresBulkWorkOrderRepository` ikut memetakan kolom baru.
- Unik per `(tenant_id, deal_id, size_label)` **tidak** dipaksakan di DB (SPK bisa dibatalkan lalu
  diterbitkan ulang untuk ukuran sama); justru dijaga di use case (§4).

---

## 4. Pilar 2 — Pure Domain (`core/`)

**`BulkWorkOrder`** (`domain/production/BulkWorkOrder.kt`):
- Tambah field `val sizeLabel: String? = null` — `null` berarti SPK legacy multi-size.
- Invarian baru: jika `sizeLabel != null` maka `sizeBreakdown` wajib berisi **tepat satu** baris
  dengan label yang sama (`require` di `init`).
- Angka turunan (`totalOrderedPcs`, `wipPieces`) tidak berubah.

**`LaunchBulkWorkOrderFromDealUseCase`** — perubahan inti:
1. Return type jadi `Result<List<BulkWorkOrder>>`.
2. `deriveSizeBreakdown(purchaseOrders)` tetap dipakai, lalu di-`groupBy { sizeLabel }`.
3. Untuk tiap ukuran: cek `findByDealId` — jika SPK aktif dengan `sizeLabel` sama sudah ada,
   **skip** (idempotensi pindah dari per-deal ke per-`(dealId, sizeLabel)`); jika belum, terbitkan
   satu SPK dengan satu `BulkSizeLine`.
4. Klik kedua pada tombol launch = "lengkapi ukuran yang belum terbit" — bukan error, bukan duplikat.
5. Golden sample tetap satu per deal, tapi divalidasi: matriks sampel ACC harus punya kolom ukuran
   yang sedang diterbitkan (`isSizeColumnActive`), kalau tidak → gagal dengan pesan per ukuran.

**Sampling (Fase 2, terpisah)**: 1 SPK sampling per **desain × ukuran** saat qty matriks >1 kolom.
Ini menyentuh konsep inti sampling (kanban, stage work, QC queue) — direncanakan terpisah, lihat §9.

## 5. Pilar 3 — Backend API (`server/`)

- `POST /production/work-orders/launch-from-deal`: response body berubah dari satu objek menjadi
  **array** SPK hasil launch (`[{...}, {...}]`). Route tetap idempotent.
- `GET /production/work-orders?dealId=...` sudah mengembalikan list — tidak berubah.
- Codec `BulkWorkOrderCodec` tambah `sizeLabel` (read/write, null-safe).

## 6. Pilar 4 — Client-Server Integration (`app/shared/`)

- `ProductionApiClient.launchFromDeal` → `Result<List<BulkWorkOrder>>`; decode array + fallback
  objek tunggal agar aman saat server lama/baru bercampur.
- Mapping DTO → domain: `sizeLabel` diteruskan; `ProductionUiState` tidak berubah strukturnya
  (list SPK sudah ada), hanya isinya kini berisi satu SPK per ukuran.
- Error per ukuran dari use case (mis. golden sample tak punya kolom M) ditampilkan apa adanya
  di snackbar/error pane Deal Tab 2.

## 7. Pilar 5 — Presentation (`app/shared/presentation/`)

- **Deal Tab 2**: setelah launch, tampilkan ringkasan "3 SPK terbit (S/M/L)" — list kartu kecil,
  bukan satu kartu. Ikon `ClayIcons` saja, tanpa emoji.
- **ProductionWorkspace / BulkWorkOrderDetailPane**: badge ukuran (`ClayTag`) di header kartu SPK
  saat `sizeLabel != null`; tabel size breakdown disembunyikan untuk SPK per-ukuran (isinya
  redundan — satu baris).
- Densitas ditinjau (Kontrak 12): jumlah kartu naik ×N, daftar wajib tetap `LazyColumn`.

## 8. Data Lama (Legacy) — Keputusan

Precedent repo: `mockupImageUrls` dipertahankan read-only untuk data lama. Diterapkan sama:
- SPK lama multi-size dibiarkan apa adanya (`sizeLabel = null`), tetap bisa dipantau sampai selesai.
- **Tidak** ada auto-split data lama — memotong SPK yang sedang berjalan mengacaukan progres tahap
  dan surat jalan yang sudah terbit.
- UI menandai SPK `sizeLabel == null` dengan tag "Legacy multi-size".

## 9. Fase & Urutan Kerja

| Fase | Isi |
|---|---|
| **1** | Bulk split: migrasi DB, domain (`sizeLabel`, launch split + test), route array, client, badge UI, seed V43/V44 konsisten |
| **2** | Sampling split per desain × ukuran (kanban, stage work, QC, seed antrean) — perlu desain ulang tersendiri karena size matrix adalah inti SPK sampling |
| **3** | Data turunan per ukuran menempel ke SPK ukuran: program CAM per size, label karung, invoice grouping |

## 10. Open Questions

1. SPK number: counter global berurutan (rekomendasi) atau ber-suffix ukuran (`...-0001-M`)?
2. Fase 2 sampling: revisi (Rev 0 → Rev 1) per ukuran, atau revisi per desain menaikkan semua
   SPK ukurannya sekaligus?
3. Rekonsiliasi sisa kain (waste) tetap per deal — perlu lapisan agregasi ulang di laporan.

## 11. Definition of Done (per fase)

- [ ] Kompilasi 5 target hijau (`compileKotlinJvm/WasmJs/Js`, `assembleAndroidMain`, `jvmTest`).
- [ ] Test domain baru: launch multi-size → N SPK; launch ulang → tidak ada duplikat;
      golden sample tanpa kolom ukuran → error per ukuran.
- [ ] Zero literal `Color(0xFF…)` baru; komponen memakai `ClayTag`/`ClayCard`.
- [ ] File tersentuh tidak melampaui hard limit file-size rules.
- [ ] Teaching doc di `docs/teaching/` setelah fase selesai.

