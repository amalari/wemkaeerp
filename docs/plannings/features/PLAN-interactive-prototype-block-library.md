# PLAN — Prototype Interaktif & Perpustakaan Blok Data-Driven (Builder ala Jotform, ERP-Native)

**Status**: Draf 1 · **Tanggal**: 2026-10-03 · **Induk**: [TRD-PLAT-003](../trd/TRD-PLAT-003-interactive-prototype.md) (tahap 1 sudah ada) · Terkait: [PLAN-discovery-blueprint-prototype-studio](PLAN-discovery-blueprint-prototype-studio.md), [TRD-PLAT-002](../trd/TRD-PLAT-002-builder.md)

---

## 0. Ringkasan Keputusan (sudah disepakati dalam diskusi)

1. **Nilai jual builder = prototype yang hidup**, bukan gambar. Kanban bisa dipindah, tabel ikut berubah, dasbor terhitung.
2. **Bukan kode bebas (Lovable), melainkan spec data dari kosakata tertutup** yang diinterpretasikan runtime. AI hanya *menyusun* dan *mengonfigurasi* blok.
3. **Satu perpustakaan blok bersama (ala Jotform)** dipakai prototype **dan** aplikasi asli. Pembedanya hanya dua port yang disuntikkan: **Data** (memori ↔ Postgres) dan **Efek** (tiruan ↔ sungguhan).
4. **Spec berversi.** Draf bebas diubah; aplikasi live menunjuk ke **versi beku**. Perubahan aditif otomatis aman; perubahan merusak wajib migrasi eksplisit (v1 hanya mengizinkan aditif).
5. **Kustomisasi tanpa forking blok**: kebutuhan di luar blok masuk lewat blok `CUSTOM_EXTENSION`.
6. **Modul live tidak ditulis ulang.** Migrasi Strangler Fig, satu modul per PR, dengan test paritas.
7. **State prototype di memori sesi**; yang disimpan hanya spec (seed deterministik).

## 1. Baseline Terverifikasi (apa yang sudah ada, 2026-10-03)

| Aset | Keadaan |
|---|---|
| `core/domain/prototype/` | `EntitySpec`, `FieldSpec`(TEXT/NUMBER/DATE/ENUM/BOOL), `StateMachine`, `PrototypeSpec`/`ScreenSpec`(hanya KANBAN terikat), `PrototypeStore`, `PrototypeReducer`(SetField, Create), `InteractiveScreenFactory`(adaptor `sampleRows` kanban), `KanbanHints`. Teruji (fixture non-garment). |
| Kabel | `InteractiveScreenCodec`, `WidgetRegistry.interactiveFor`, field `interactive` di ringkasan draf; `kanbanHints` di `DomainPackCodec`. |
| UI | `InteractiveKanban` + `InteractiveKanbanState` di package `discovery` (drag + menu "Pindah ke…"). **Belum diverifikasi dengan mata** (browser Playwright dipakai proses lain). |
| Widget lain | Tabel, form, checklist, dasbor, cetak, custom: **masih statis** (`PrototypeRenderer.WidgetBody`). |
| Agent | `DiscoveryAgent` (Koog + `DeterministicDiscoveryAgent`) menghasilkan **draf utuh** + validator; belum ada mode "edit lewat operasi". |
| Handoff | `HandoffScaffoldGenerator` menghasilkan **kandidat PR** (migrasi `CREATE SCHEMA` + tabel **stub** + RLS + katalog + stub route). Belum menurunkan tabel/route dari entitas. |
| Utang | Drag kanban ditulis **4×** (CRM, Sampling, operator, prototype) — melanggar Aturan Tiga Kali. `DiscoveryRoutes.kt` 499 baris (batas keras 500). Android SDK tidak ada di mesin dev (target Android tak bisa dikompilasi lokal). |

## 2. Arsitektur Target

```
                      ┌──────────────────────────────┐
  Narasi / chat  ───► │ AI Composer (SpecOps)        │  menyusun & mengonfigurasi
                      └──────────────┬───────────────┘
                                     ▼ operasi tervalidasi
        ┌───────────────────────────────────────────────┐
        │ Spec (data)  ── draf bebas ── publish ──► versi beku (vN) │
        │  entities · screens(blocks) · bindings · transitions │
        └───────────────┬───────────────────────────────┘
                        ▼
        ┌───────────────────────────────────────────────┐
        │ Perpustakaan Blok (Compose, buta domain)       │
        │ Kanban · Table · Form · Checklist · Dashboard  │
        │ Record · Print · CustomExtension               │
        └───────┬─────────────────────────┬─────────────┘
          BlockDataPort             BlockEffectPort
        ┌───────┴───────┐         ┌───────┴────────┐
        │ Memori (proto)│         │ Tiruan (proto) │
        │ Postgres/RLS  │         │ RBAC·audit·port│
        └───────────────┘         └────────────────┘
        Aturan bersama: PrototypeReducer + StateMachine + validator (core)
```

### 2.1 Kosakata tertutup (milik sistem = kode; lolos Uji Variabilitas)
- **Blok**: `KANBAN, TABLE, FORM, CHECKLIST, DASHBOARD, RECORD(detail), PRINT, CUSTOM_EXTENSION`.
- **Tipe field**: `TEXT, NUMBER, DATE, ENUM, BOOL, REF`(relasi ke entitas lain). Tambahan nanti = keputusan kode.
- **Aksi**: `SetField, Create, Delete(opsional), Transition(=SetField pada field mesin status)`.
- **Agregat dasbor**: `COUNT, SUM, AVG` (+ `where` sederhana, `groupBy`).
- Yang **data** (per pack/tenant/draf): nama entitas & field, opsi enum, kolom, transisi, binding blok→entitas, judul, seed.

### 2.2 Port
```kotlin
interface BlockDataPort   { rows(entityId); apply(action): Result<…>; observe() }   // memori | Postgres
interface BlockEffectPort { may(action): Decision; emit(event) }                    // tiruan | RBAC+audit+handoff antarmodul
```
Blok hanya bicara ke port; tidak tahu mode. Reducer tetap di `core`, dipanggil implementasi port.

### 2.3 Daur hidup spec
`Draf` (bebas, dipakai prototype & chat) → `publish` → `Versi vN` (tak berubah, disalin) → aplikasi live menunjuk `vN`. Klasifikasi diff antar versi: **aditif** (field/kolom/transisi/blok baru) vs **merusak** (hapus/ganti nama status/field, ubah tipe). v1: merusak = ditolak (migrasi eksplisit menyusul, F10).

### 2.4 Komposisi oleh AI
AI tidak menulis spec utuh sekali jalan, melainkan **daftar operasi** pada spec: `AddEntity, AddField, AddColumn, SetTransition, AddScreen(block, entity, config), ChangeBlock, BindAggregate, RemoveX`. Tiap operasi lolos `SpecOpValidator` (kosakata tertutup, fail-closed) lalu diterapkan → spec baru → prototype ter-render ulang. Keuntungan: bisa di-undo, di-diff, diaudit, dan LLM yang salah hanya menggagalkan satu operasi.

### 2.5 Sambungan lintas modul
Entitas keluaran modul A → antrean entitas modul B lewat `PortType` pack (mis. kartu SPK `Selesai` memunculkan item di QC). Diimplementasikan sebagai **efek** (`emit(event)`) yang di prototype diterapkan ke store tetangga, di aplikasi asli lewat handoff nyata.

## 3. Fase Implementasi

Ukuran: **S** ≤ 1 hari, **M** 2–4 hari, **L** 1–2 minggu (estimasi kasar, untuk urutan bukan komitmen).

### F0 — Penertiban (S)
- Commit pekerjaan tertunda (DataFlow*, WidgetRegistry, GarmentScreenSuggestions, TRD/teaching tahap 1) agar diff bersih.
- **Verifikasi mata** kanban tahap 1 di `app.lvh.me:3001/builder/prototype` (login superadmin demo), termasuk lebar sempit ~1280dp & restart server dev.
- Ratchet: pindahkan `summaryObj` dari `DiscoveryRoutes.kt` ke file baru (`DiscoverySummary.kt`) → file ≤ 499.
- **AC**: kanban terlihat dan bisa dipindah di 3 papan garment; `wc -l` DiscoveryRoutes turun.

### F1 — Fondasi Blok & Kanban Generik (M)
- `designsystem/ClayKanbanBoard` **buta domain** (kolom, kartu, `onMove`, `canMove`, slot kartu), drag + menu fallback; menggantikan `InteractiveKanban`.
- Definisikan `BlockDataPort` + `BlockEffectPort` (core) dengan implementasi memori/tiruan.
- **Bukti paritas**: ganti papan **Sampling** tulis-tangan dengan blok generik (`SamplingDragDropState` & board diganti), perilaku identik; test paritas + cek mata.
- **AC**: 5 target (kecuali Android lokal) hijau; Sampling live tak berubah perilaku; drag kanban kini 2 salinan (CRM, operator) bukan 4.
- **Risiko**: gestur drag beda per target → uji manual web + desktop.

### F2 — Spec Model v2 (M)
- `ScreenSpec` jadi sealed `BlockConfig` (Kanban/Table/Form/…); lepas hardcode kunci `"Kolom"` dari adaptor (adaptor lama tetap untuk draf lama).
- `FieldType.REF`, validasi wajib/rentang, label per field, urutan tampilan.
- `PrototypeSpecCodec` penuh (bukan hanya kanban) + versi skema (`specSchemaVersion`) agar draf lama terbaca.
- **AC**: round-trip codec; draf lama (`sampleRows`) tetap tergambar; spec tak koheren ditolak; test dengan template **non-garment** (+ fixture bordir).

### F3 — Blok Lain (L)
- **Table** (sortir, filter, ubah status inline lewat reducer), **Form** (Create + validasi tipe, pesan galat), **Checklist** (toggle BOOL), **Dashboard** (agregat dari store yang sama — angka ikut berubah saat kartu dipindah), **Record** (detail satu baris), **Print** (templat dari field).
- Tiap blok: file sendiri di `presentation/discovery/blocks/` (≤ 400 baris, aturan file-size), komponen Clay saja, nol literal warna.
- `PrototypeRenderer` jadi dispatcher tipis; blok lama statis dihapus bertahap.
- **AC**: tiap blok punya test reducer/agregat + cek mata; dasbor HPP/stok ikut terhitung dari data layar lain.

### F4 — Sambungan Lintas Modul via Port (M)
- Binding entitas↔`PortType` dari pack; efek `emit` menerapkan hasil ke entitas hilir di sesi prototype.
- Tampilan di `DataFlow*`: aliran data hidup, bukan statis.
- **AC**: memindahkan SPK ke `Selesai` memunculkan item di antrean modul hilir; test pada dua modul pack garment + satu pack non-garment.

### F5 — Komposisi AI lewat SpecOps (L)
- `SpecOp` sealed + `SpecOpValidator` + `SpecOpApplier` (core, murni, teruji).
- Alat agent (Koog) yang mengeluarkan **operasi** (bukan spec utuh); `DeterministicDiscoveryAgent` tetap sebagai fallback tanpa kunci API.
- UI chat builder: pesan → operasi → **prototype ter-render ulang langsung**, riwayat + undo/redo, tampilan diff.
- Pagar: operasi di luar kosakata ditolak dengan pesan; batas jumlah operasi per giliran; seluruh operasi tercatat (audit).
- Evaluasi: set narasi emas (garment, bordir, sablon, jasa non-konveksi) dengan asersi struktur spec.
- **AC**: narasi "tambah status Revisi di papan sampling setelah Dikerjakan" menghasilkan operasi sah dan kolom baru tampil; operasi jahat/ngawur ditolak tanpa merusak spec.

### F6 — Versi & Publish (M)
- Migrasi `V8x__prototype_spec_versions` (schema platform, owner-only, pola V79): `spec_json`, `version`, `parent_version`, `classification` (ADDITIVE/BREAKING), `published_by`, `published_at`.
- `PublishSpecUseCase`: hitung diff; **BREAKING ditolak di v1**; versi beku tak bisa diubah.
- Endpoint tulis **fail-closed**; test **403** untuk peran tak berwenang, wajib.
- **AC**: draf diubah setelah publish tidak mengubah versi yang dirujuk aplikasi; diff aditif lolos, merusak ditolak dengan alasan.

### F7 — Seed Realistis (S–M)
- Pack menyediakan **generator seed deterministik** (nama buyer, nomor PO, pcs, tanggal) per entitas; entitas saling merujuk (satu PO konsisten di semua layar).
- LLM boleh memperkaya seed **di dalam skema**, divalidasi `PrototypeStore.seeded`.
- **AC**: seed sama → keluaran sama byte-per-byte; seed invalid menggagalkan pembuatan (tidak dilewati diam-diam).

### F8 — Runtime Aplikasi Asli (L) — keputusan arsitektur terbesar, TRD sendiri (TRD-PLAT-004)
- `PostgresBlockDataPort`: tabel di schema modul (`<kode>.<tabel>`), `ModuleSchemaMap`, RLS `apply_tenant_rls_in`, grant `wemade_app`.
- `RbacEffectPort`: `requireModuleAccess`/`moduleDecision`, fail-closed, scope `GLOBAL_ONLY/HIERARCHICAL`.
- `HandoffScaffoldGenerator` v2: turunkan **tabel & route dari `EntitySpec`** (bukan stub), tetap sebagai kandidat PR yang ditinjau manusia.
- **Pilot**: satu modul (Sampling) dihasilkan dari spec dan dibandingkan dengan versi tulis-tangan (paritas data & perilaku).
- **AC**: modul pilot lolos checklist `module-integration-rules.md` §4/§5; peran tak berwenang 403.

### F9 — Strangler Migrasi Modul Live (berkelanjutan)
- CRM, meja operator, dll. diganti ke blok generik **hanya saat disentuh**, satu modul per PR, test paritas, `scripts/audit-variability.sh` tanpa temuan baru. Tidak ada penulisan ulang massal.

### F10 — Migrasi Perubahan Merusak (nanti)
- Pemetaan data eksplisit ("Dikerjakan → Diproses"), pratinjau dampak, eksekusi per tenant dengan jalur mundur. Tidak masuk v1.

### Urutan & ketergantungan
`F0 → F1 → F2 → F3 → (F4 ∥ F5) → F6 → F7 → F8 → F9`. F7 boleh dikerjakan lebih awal bila demo butuh data nyata; F8 tidak boleh dimulai sebelum F6 (aplikasi live wajib menunjuk versi beku).

## 4. Governance, Standar, Ukuran
- **Jenis**: fitur dalam modul induk Builder (bukan `BusinessModule` baru) untuk F1–F7; F8 menyentuh pendaftaran modul sesuai anatomi §5 `module-integration-rules.md`.
- **Gerbang**: gerbang builder yang ada; endpoint tulis fail-closed + test 403 (Kontrak 7).
- **Variabilitas**: konsep per tenant/industri (entitas, kolom, transisi, seed) = **data**; blok & tipe field = kode (alasan di KDoc). Kunci tersimpan = value object string, parser tunggal, tolak bukan fallback (Kontrak 4).
- **Design system**: nol literal warna, token Clay, komponen `designsystem/` buta domain; Aturan Tiga Kali dipenuhi (F1 melunasi utang kanban).
- **Ukuran file**: core ≤ 250/400, presentation ≤ 400/600, server ≤ 300/500; ratchet untuk file yang sudah besar. Satu blok = satu file.
- **Dokumentasi**: teaching doc per fase di `docs/teaching/`; TRD-PLAT-003 diperbarui, TRD-PLAT-004 (F8) dibuat sebelum kodenya.

## 5. Strategi Uji
- **Domain murni**: reducer, validator, `SpecOp`, diff aditif/merusak, agregat.
- **Tenant kedua wajib** (Kontrak 6): fixture non-garment + cek mata di `bordir-uji`.
- **Paritas**: setiap modul yang diganti blok generik (F1 Sampling, F9) punya test perilaku identik.
- **Golden agent**: narasi emas → struktur spec yang diharapkan (deterministik; LLM tanpa kunci API memakai agent deterministik).
- **Kompilasi**: JVM, WasmJS, JS, server di lokal; **Android hanya di CI** (SDK tak ada di mesin dev).
- **Mata**: setiap fase UI dijalankan dan dilihat (login superadmin demo), lebar sempit ~1280dp; bug layout/gestur tidak tertangkap test.

## 6. Risiko & Mitigasi
| Risiko | Dampak | Mitigasi |
|---|---|---|
| Blok generik tak cukup untuk kebutuhan tenant | Dorongan fork per tenant | Blok `CUSTOM_EXTENSION`; evaluasi blok baru lewat Uji Variabilitas |
| LLM mengusulkan operasi ngawur | Prototype rusak | Operasi tervalidasi satu per satu, fail-closed, undo, batas per giliran |
| Perubahan spec merusak aplikasi live | Kehilangan data/operasi terganggu | Versi beku, v1 hanya aditif, F10 sebelum mengizinkan merusak |
| Gestur drag tak seragam 5 target | UX buruk di satu target | Menu "Pindah ke…" sebagai jalur setara; uji manual per target |
| Paritas modul live meleset | Regresi di produksi | Strangler, satu modul per PR, test paritas, mulai dari Sampling |
| Lingkup F8 membengkak | Jadwal molor | TRD-PLAT-004 terpisah; pilot satu modul dulu |
| Handoff generator hanya stub saat ini | Janji "draf → aplikasi asli" terlalu dini | Jangan menjanjikan sebelum F8 pilot lulus |

## 7. Pertanyaan Terbuka
1. Papan paritas pertama: **Sampling** (disarankan, lebih sederhana) atau CRM?
2. Apakah prototype perlu **dibagikan ke prospek lewat link** (spec saja disimpan; seed deterministik) — sudah diputuskan memori-saja, konfirmasi bila kebutuhan berubah.
3. Batas operasi AI per giliran dan siapa yang berhak **publish** (Owner pabrik vs superadmin builder).
4. Kapan F10 (migrasi perubahan merusak) menjadi kebutuhan nyata — tergantung jumlah tenant live yang memakai modul hasil generate.

## 8. Definition of Done per Fase
- [ ] Discovery Note/TRD diperbarui bila agregat atau migrasi baru
- [ ] Test domain (termasuk tenant non-default) hijau; test 403 untuk endpoint tulis
- [ ] Kompilasi JVM/WasmJS/JS/server hijau; Android di CI
- [ ] Cek mata di `/builder/prototype` (dan `bordir-uji` bila menyentuh UI)
- [ ] `wc -l` file yang disentuh sesuai batas/ratchet; `scripts/audit-variability.sh` tanpa temuan baru
- [ ] `graphify update .`
- [ ] Teaching doc di `docs/teaching/`
