# Teaching — Katalog Proses Opsional Dinamis Per-Tenant (Adjust Flow Sampling)

> Task: Flow produksi per tenant dengan tahapan opsional (Bordir/Sablon/Laundry) yang bisa
> disisipkan divisi sampling di mana saja — lewat tombol `+` di celah flow maupun drag-and-drop.

## 1. Mulai dari Mana? (Urutan Menulis dari Nol)

1. **Domain dulu, selalu** (`core/domain/process/`): `TenantOptionalProcess` (entity) →
   `TenantProcessCatalog` (agregat + invarian) → `TenantProcessCatalogRepository` (interface) →
   5 use case (`Add/Remove/Reposition/Get/Resolve`).
2. **Sambungkan ke workqueue**: tambah field `insertAfterCode` di `WorkStationSpec`, ubah
   `WorkStationCatalog.line()` agar stasiun tambahan **disisipkan setelah jangkarnya**, bukan
   di-append. Tambah template stasiun opsional (`BORDIR`, `SABLON`, `LAUNDRY`).
3. **Persistensi** (`server/`): migrasi Flyway `V56` → tabel Exposed `TenantOptionalProcessesTable`
   → `PostgresTenantProcessRepository` → `TenantProcessRoutes` → wiring di `ServerRouteWiring`.
4. **Client** (`app/shared/`): codec bersama `ProcessCatalogCodec` (satu kontrak wire untuk
   server & client) → `ProcessCatalogApiClient` → `ProcessFlowViewModel` (state holder kecil
   terpisah) → `ProcessFlowAdjusterPanel` + `ProcessFlowDragState` → pasang di
   `SamplingWorkspaceScreen`.

## 2. Bedah Kode Blok per Blok

### Konsep jangkar (anchor) — mental model utamanya

```
Flow tenant   : [Intake] [CAM] [Rajut] [Linking] [Bordir] [QC] [Finishing] [Kirim]
                                        anchor-nya LINKING_ASSEMBLY (tahap di KIRI celah)
```

Tim sampling **tidak pernah mengisi field "setelah mana"** — mereka klik `+` di celah atau
melepas drag di celah. Celah itu *adalah* jangkarnya; sistem yang menyimpan
`samplingAnchorAfter = tahap di kiri celah`. Satu proses boleh punya dua jangkar sekaligus:
`samplingAnchorAfter` (flow sampling) dan `stationAnchorAfter` (line workqueue) — jadi bordir
muncul konsisten di dua lapisan flow dari **satu** baris data.

### `TenantProcessCatalog` — invarian yang mencegah flow rusak

Satu proses = satu posisi per tenant (id deterministik `proc-<kode>`). Tanpa invarian kode
unik, bordir bisa muncul dua kali di flow dan `nextAfter()` jadi ambigu. `reposition()` menolak
kedua jangkar `null` sekaligus — proses tidak boleh "melayang" tanpa tempat.

### `WorkStationCatalog.line()` — sisip berantai

Sisipan berikutnya melihat hasil sisipan sebelumnya — jadi BORDIR setelah QC_FINAL lalu SABLON
setelah BORDIR otomatis berurutan. Tanpa jangkar = append (backward compatible, test ada).

### `ResolveActiveProcessesUseCase` — dua lapis: template vs aktivasi

Katalog tenant = **template flow** (posisi bawaan). `activeProcessCodes` = **aktivasi per SPK**
(SPK kaos polos → bordir dilewati). Query `activeProcessCodes = null` artinya mode template.
Output `customStations` tinggal dilempar ke `WorkStationCatalog.line()` — seam yang sudah ada.

### Drag-and-drop (`ProcessFlowDragState`) — pola yang sama dengan kanban

Titik pointer dihitung dalam koordinat window: `posisi chip saat mulai + offset pointer +
akumulasi dragAmount`, lalu dicocokkan ke bounds tiap celah (`onGloballyPositioned`).
Ini persis pola `SamplingDragDropState` — kami tidak menemukan mekanisme baru.

## 3. Teknologi & Pendekatan ("The Why")

- **Data-driven, bukan enum baru**: `SamplingPipelineStage` tetap enum sebagai *kerangka wajib*.
  Tahapan opsional = baris data per tenant (Kontrak 7 isolasi multi-tenant). Mengubah enum jadi
  data penuh = refactor puluhan file; menyisipkan overlay data = 1 agregat baru.
- **Codec bersama di `core/shared/`**: server dan client memakai `ProcessCatalogCodec` yang sama,
  sehingga kontrak JSON tidak mungkin drift (pelajaran dari `PipelineGraphCodec`).
- **ViewModel terpisah (`ProcessFlowViewModel`)**: `SamplingViewModel` sudah 425 baris (ratchet);
  satu concern = satu state holder, dan file besar tidak bertambah.
- **`SqlExpressionBuilder.run {}` di `deleteWhere`**: receiver lambda `deleteWhere` Exposed adalah
  tabel, bukan expression builder — `and`/`inList` hanya resolve di dalam scope builder.

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menyimpan posisi sebagai index angka** — begitu tahap wajib berubah, semua index rusak.
   Selalu simpan *jangkar berupa tahap*, bukan urutan.
2. **Menambah stasiun kustom dengan append** — bordir di ujung flow (setelah packaging) jelas
   salah; itulah kenapa `line()` harus mengerti `insertAfterCode`.
3. **`deleteWhere { a eq x and b eq y }`** — kompilasi gagal "Unresolved reference 'and'".
   Bungkus dengan `SqlExpressionBuilder.run { ... }`.
4. **Dua jangkar `null` setelah reposisi** — proses jadi hilang dari semua flow. Invariant di
   agregat menolaknya; jangan longgarkan.
5. **Menanam logika flow di Composable** — panel hanya mengirim event; keputusan ada di
   use case. Kalau ada `if` bisnis di panel, itu tanda salah tempat.

## 5. Verifikasi & Tantangan Mandiri

```bash
./gradlew :core:jvmTest        # 20 test katalog + insertAfter (semuanya hijau)
./gradlew :server:compileKotlin :app:shared:compileKotlinJvm
```

Kasus uji kunci yang sudah lulus:
- `line` dengan BORDIR@setelah-QC_FINAL → `nextAfter(QC_FINAL) = BORDIR`, `nextAfter(BORDIR) = PACKAGING`
- `nextAfter` dengan `activeStations` tetap menghormati sisipan
- Katalog menolak kode/ID duplikat, proses tanpa jangkar, proses tenant lain
- `Resolve` mengecualikan proses tidak aktif & mengurutkan sampling steps per urutan tahap

Tantangan mandiri: (a) tambahkan drag-and-drop reposisi untuk chip workqueue di papan stasiun;
(b) buat reposisi bersyarat — proses yang sudah punya kartu WIP aktif menolak dipindah;
(c) tambah endpoint aktivasi per SPK yang memakai `ResolveActiveProcessesQuery.activeProcessCodes`.

