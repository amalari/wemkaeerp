# TRD-PLAT-003: Prototype Interaktif Berbasis Spec (Builder ala Lovable, ERP-Native)

## 1. Document Context and Administration

- **ID**: `TRD-PLAT-003` · **Status**: Draf 0.1 · **Tanggal**: 2026-10-03
- **Lokasi produk**: `/builder/prototype` (Jalur B, pack `garment` sebagai pack pertama)
- **Ringkasan**: Prototype hari ini adalah gambar statis (`PrototypeRenderer` menggambar
  `sampleRows: List<Map<String,String>>`, `onClick = {}`). Nilai jual builder ada di prototype yang
  **hidup**: kartu kanban bisa dipindah, tabel ikut berubah, dasbor terhitung ulang. Berbeda dari
  Lovable (kode bebas), kita menghasilkan **spec data dari kosakata tertutup** yang diinterpretasikan
  runtime, sehingga hasilnya otomatis sesuai standar (Clay, RBAC, port, scope) dan bisa dilanjutkan
  menjadi modul asli lewat `HandoffScaffoldGenerator`.

### Discovery Note (ringkas)

| # | Isi |
|---|---|
| 1 Kebutuhan | Siapa: prospek/owner pabrik yang menilai calon aplikasi, dan admin builder. Data milik: draf tenant (spec) — state prototype milik sesi klien. Berubah: spec per revisi draf; state per interaksi. |
| 2 Serupa | **Mirip**: drag kanban `CrmDragDropState` / `SamplingDragDropState` (pola overlay + drop target, `ClayKanbanColumn` di designsystem), `WidgetRegistry`/`ScreenSuggestion` (data pack), `DiscoveryDraftValidator` (kosakata tertutup, fail-closed), `HandoffScaffoldGenerator`. Bukan duplikat: belum ada model entitas/state di prototype. |
| 3 Jenis | **Fitur dalam modul induk Builder/Discovery** (bukan `BusinessModule` baru) — mewarisi gerbang builder yang ada. |
| 4 Variabilitas | Lihat tabel di bawah. |
| 5 Core | `core/.../domain/prototype/` (paket baru), titik extend: `ScreenSuggestion`, `WidgetKind`, `DomainPackCodec`. Jangan sentuh file di atas hard limit tanpa ratchet (`DiscoveryRoutes.kt`). |
| 6 I/O | Tahap 3: port keluar modul A → antrean entitas modul B memakai `PortType` pack. Bukan node kanvas Factory Flow. |
| 7 Governance | Gerbang builder yang ada. Prototype read-only terhadap data tenant (seed sintetis); tulis spec = fail-closed, test peran tak berwenang 403. |
| 8 Ukuran | Agregat baru, tanpa migrasi DB di tahap 1 (spec ikut JSON draf) → **TRD ini**. |

| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Entitas & field | ya | ya | ya | **Data** | template di pack, disalin ke spec draf |
| Kolom/status & transisi | ya | ya | ya | **Data** | idem; draf membeku saat dikirim |
| Seed data | ya | ya | — | **Data** (generator pack) | deterministik dari seed + spec |
| Jenis widget, tipe field, jenis aksi | tidak | tidak | tidak | **Kode** (kosakata sistem) | — |

## 2. Functional Requirements

- **F1 EntitySpec**: entitas punya `id`, field bertipe (`TEXT`, `NUMBER`, `DATE`, `ENUM`, `BOOL`,
  `REF`), dan opsional `StateMachine` (status → status yang boleh dituju).
- **F2 Seed**: pack menyediakan seed per entitas; nilai harus lolos skema; id baris stabil.
- **F3 ScreenSpec**: widget **terikat entitas**: KANBAN (`groupBy` field ENUM + daftar kolom *termasuk
  yang kosong*, `titleField`, `detailFields`), TABLE, FORM, CHECKLIST, DASHBOARD (agregat), PRINT.
- **F4 Actions** (reducer murni di core): `MoveCard`, `SetField`, `Create`, `Toggle`. Aksi ditolak bila
  melanggar transisi/skema → hasil `Result.failure` dengan pesan yang bisa ditampilkan.
- **F5 Runtime store**: `PrototypeSession` memegang koleksi entitas di memori; semua widget membaca
  dari sini; tidak disimpan ke server.
- **F6 Drag di 5 target** + fallback menu "Pindah ke…" (tanpa drag).
- **F7 Kompatibilitas**: layar lama berbasis `sampleRows` tetap tergambar (dikonversi ke spec
  sintetis), tidak ada draf rusak.
- Tahap lanjut: tabel/form/checklist/dasbor (T2), sambungan port lintas modul (T3), agent
  mengedit spec lewat operasi tervalidasi (T4), seed generator pack (T5).

## 3. Non-Functional Requirements

- Core murni Kotlin common, tanpa Compose/Ktor; reducer deterministik & teruji.
- Kosakata tertutup: spec di luar kosakata **ditolak**, tidak fallback senyap (Kontrak 4).
- Satu file ≤ batas lapisan (core 250/400, presentation 400/600); renderer dipecah per widget.
- Nol literal warna; komponen Clay saja.

## 4. System Architecture & Technical Design

```
core/domain/prototype/
  EntitySpec.kt        # EntitySpec, FieldSpec, FieldType, StateMachine
  PrototypeSpec.kt     # ScreenSpec (binding widget→entitas), PrototypeSpec + validasi
  PrototypeStore.kt    # PrototypeStore (koleksi baris), RowId
  PrototypeActions.kt  # PrototypeAction + reduce(store, action): Result<PrototypeStore>
  LegacyRowsAdapter.kt # sampleRows → spec+seed sintetis (F7)
app/shared/presentation/discovery/prototype/
  PrototypeSessionState.kt   # state hoisted; memanggil reduce
  KanbanWidget.kt (+ drag)   # meniru CrmDragDropState
  TableWidget.kt, …          # per widget, T2
```

- **Data flow**: `ScreenSuggestion` pack → (T1) memuat `entity`/`seed`/`spec` opsional → server
  mengirim spec di ringkasan draf → klien membuat `PrototypeStore` dari seed → widget membaca store →
  aksi UI → `reduce` → store baru.
- **Tradeoff**: spec data vs kode bebas (Lovable) — dipilih spec demi keamanan, standar, dan handoff.
  State di memori vs persisten — dipilih memori; seed deterministik sehingga bisa dibuat ulang.
- **Constraint**: `WidgetRegistry.sampleRowsFor` dan `DomainPackCodec` harus kompatibel mundur
  (field spec baru opsional; draf lama tetap terbaca).

## 5. Testing, Deployment, and Operations

- **AC T1**: (1) reducer `MoveCard` memindah kartu ke kolom kosong; (2) transisi terlarang ditolak
  dengan pesan; (3) spec dengan field/kolom tak dikenal ditolak validator; (4) test memakai pack
  **non-garment** (fixture) selain garment; (5) draf lama berbasis `sampleRows` tetap tergambar;
  (6) kanban di `/builder/prototype` bisa drag + fallback menu, dilihat dengan mata (login
  superadmin demo); (7) kompilasi 5 target hijau; (8) `scripts/audit-variability.sh` tanpa temuan baru.
- **Rollback**: perubahan aditif; field spec opsional, matikan dengan mengabaikannya di renderer.
- **Dokumentasi**: teaching doc per tahap di `docs/teaching/`.
