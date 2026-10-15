# PLAN: Ringkasan & Follow-Up per Modul di Chat Builder

**Tanggal**: 2026-10-14
**Status**: Sebagian — Track 0/A/B/C selesai dan di `main` (2026-10-14); Track D (cek visual, teaching doc) belum. Flag server `BUILDER_MODULE_BRIEFS_ENABLED` default MATI
**TRD**: [`TRD-BLDR-001-module-briefs.md`](../../trd/features/TRD-BLDR-001-module-briefs.md) (Fase 1) ·
[`TRD-BLDR-002-scenario-simulation.md`](../../trd/features/TRD-BLDR-002-scenario-simulation.md) (Fase 2)
**Induk**: [`PLAN-builder-interview-chat.md`](PLAN-builder-interview-chat.md)

---

## 0. Discovery Note (ringkas)

| Pertanyaan | Jawaban |
|---|---|
| Kebutuhan bisnis | Pengguna cukup bercerita sekali; tiap modul otomatis punya ringkasan + fitur + pertanyaan lanjutan sendiri, dan riwayat chat sendiri |
| Fitur serupa sudah ada? | Sebagian (graphify gagal pada kueri pertama; dilanjut grep/baca file): utas per modul, `ModuleGapAnalyzer` (follow-up template), `EditModuleFromChat`, `InterviewPlanner` (`RoleModuleLink.features`). **Belum ada**: ringkasan AI per modul & follow-up kontekstual |
| Jenis | **Fitur di dalam modul/platform Builder** — bukan `BusinessModule` baru (mewarisi gerbang `MANAGE_BUILDER`) |
| Uji Variabilitas | Isi ringkasan = data tenant/pack. `ChatMessageKind.BRIEF` = mekanik platform → enum sah |
| Core & extend | `core/domain/builder` (port + use case), `KoogModuleEditor` sebagai pola, `BuilderChatRepository` |
| Posisi kanvas Factory Flow | Tidak ada (bukan modul operasional) |
| Governance | Gerbang Builder yang ada; tulis fail-closed + tes 403 |
| Menu (§5.7) | **Tidak ada menu baru** — hidup di layar `/builder/chat` yang sudah ada |

Perbaikan terkait yang **sudah dikerjakan** (2026-10-14, belum di-commit): modal follow-up hanya memuat pertanyaan utas aktif +
"Lewati" diingat + modal di-reset saat ganti utas; badge semua modul dihitung dari utas Semua dan follow-up modul
diseed saat draf termuat; ringkasan lokal `openingEntryForModule` untuk utas kosong
(`BuilderChatPane.kt`, `ChatModel.kt`). Plan ini menggantikan ringkasan lokal itu dengan hasil AI.

---

## 1. Keputusan Desain

1. **BRIEF = `ChatMessage` ber-`kind` baru**, bukan tabel baru (RLS, utas, API ikut gratis).
2. **Satu panggilan model per modul**, konkurensi 3, idempoten via `basisRef`.
3. **AI hanya mengusulkan teks**; validator domain memutuskan; gagal → fallback deterministik bertanda `source`.
4. **Follow-up AI + celah deterministik berbagi kuota 3**; follow-up yang sudah ditanyakan tidak diulang.
5. **Flag server** `builder.moduleBriefs.enabled` selama stabilisasi.

## 2. Track & Tugas

### Track 0 — Kontrak (serial, wajib selesai dulu)
- [x] T0.1 `ChatMessageKind.BRIEF`; field `features`, `stages` (`StageSketch`), `basisRef`, `briefSource` pada `ChatMessage` (+ `init` guard)
- [x] T0.2 `ModuleBrief`, `ModuleBriefRequest`, `ModuleBriefer` (port), `BriefSource`
- [x] T0.3 Bentuk JSON `BRIEF` dibekukan: `ModuleBriefCodec` (parser tunggal, tolak nilai tak dikenal) + fixture garment/non-garment sebagai konstanta Kotlin di `commonTest` (`BriefContractFixtures`; tidak memakai `resources`). DTO `POST /chat/briefs` mengikuti TRD §4 dan `ModuleBriefOutcome`
- [x] T0.5 Kontrak pendukung: `ModuleBriefValidator`, `ModuleBriefService` (pintu rute/pemicu; Track B memakai fake), `BriefKeys` (`followUpId`, `basisRef`, `fingerprint` FNV-1a 64 — rumus beku), `BriefIssue`, `ModuleBriefOutcome`
- [x] T0.4 Nomor migrasi dipastikan (`ls … | sort -V | tail -1`, kini V104 → V105; terverifikasi 2026-10-14)

### Track A — Core (paralel setelah T0)
- [x] A1 `ModuleBriefValidator` + tes tiap aturan (berpath)
- [x] A2 `DeterministicModuleBriefer` + tes (peran/tautan tak ada → aman)
- [x] A3 `GenerateModuleBriefsUseCase`: idempoten, basis berubah, retry 1x, fallback; tes dengan template **non-default**
- [x] A4 Gabung kuota follow-up AI + `ModuleGapAnalyzer` (maks 3, tanpa duplikat); `AskModuleFollowUpsUseCase` jadi pengisi sisa
- [x] A5 Tes paritas: iterasi `ChatMessageKind.entries` (tiap kind punya parser klien + kolom DB)

### Track B — Server (paralel)
- [x] B1 Migrasi aditif (kolom `features`, `basis_ref`, `brief_source`, constraint kind diperlebar, indeks)
- [x] B2 `PostgresBuilderChatRepository`: baca/tulis kolom baru; tes integrasi (pesan lama tetap terbaca)
- [x] B3 `KoogModuleBriefer` + prompt + parser JSON tunggal (tolak bentuk tak dikenal)
- [x] B4 `POST /api/builder/chat/briefs` (gate `MANAGE_BUILDER`, 403/400/409) + tes peran tak berwenang
- [x] B5 Pemicu otomatis di `SendBuilderMessageUseCase` setelah rencana wawancara; SSE `module_started/ready`
- [x] B6 Flag `builder.moduleBriefs.enabled`; log terstruktur + rasio fallback

### Track C — Klien (paralel)
- [x] C1 `ChatModel`: parse `BRIEF`, `features`, `briefSource`; tolak kind tak dikenal
- [x] C2 (kode + tes parser; **tampilan belum dilihat mata** → D1) `ChatMessageBubble` varian ringkasan (token clay, nol literal warna); versi lama dilipat
- [x] C3 Tombol "Segarkan ringkasan" memanggil `POST /briefs`
- [x] C4 Hapus ketergantungan `openingEntryForModule` kecuali untuk kosong-state saat flag off
- [x] C5 Jaga `BuilderChatPane.kt` ≤ 400 baris (kini 363) — pecah ke file bertema bila perlu (mis. `ChatFollowUpState`)

### Track D — Verifikasi (serial, akhir)
- [~] D1 (sebagian, 2026-10-14: **hanya tenant `wemade-demo`** pada ~1280dp; `bordir-uji` tidak ada di migrasi/seed lokal) `wemade-demo` (superadmin demo) + `bordir-uji` ~1280dp: ringkasan & follow-up per modul, badge sejak awal
- [ ] D2 Skenario AI mati → brief `DETERMINISTIC` terlihat
- [~] D3 (sebagian: core/server builder + jvmTest brief hijau; 5 target & audit-variability belum) Kompilasi 5 target + `jvmTest`; `scripts/audit-variability.sh`; `graphify update .`
- [ ] D4 Teaching doc (`docs/teaching/teaching-builder-module-briefs.md`) + tutup di `TASKS-SUMMARY.md` (§18)

## 2b. Fase 2 — Skenario & Simulasi (TRD-BLDR-002)

Keputusan pemilik 2026-10-14: **simulasi tampilan** (memori, tidak tersimpan). Mulai setelah Fase 1 Track 0 beku.
Verifikasi awal sudah dilakukan (lihat TRD-BLDR-002 §1): `PrototypeSession` sudah bersama untuk semua layar;
`TenantStageFlow` terikat sampling → Tahap di brief hanya sketsa.

- [x] S-align Penyelarasan dengan Alur Data, `MoqupsFlowNavigator`, dan TRD-BUILDER-007 (2026-10-14): `FeatureFlow` tidak dibuat, navigator dipakai ulang, panel JSON tidak jadi jalur utama
- [x] S0 Kontrak (2026-10-15, worktree `worktree-builder-scenarios-t0`): `ScenarioSketch/Step/FieldCarry/ScenarioId/ScenarioSource/ScenarioLimits`, `ScenarioValidator`, `ScenarioProposer`, `ScenarioCarryRule`/`CarryOutcome`, `ScenarioService`, `ScenarioSketchCodec` (ketat, tolak kunci/nilai tak dikenal) + `DiscoveryDraft.scenarios` opsional (kunci tertulis hanya bila ada; draf lama byte-identik) + fixture garment dan klinik. Bukti: `ScenarioContractTest` (7) + `ScenarioSketchCodecTest` (13) hijau; core 2054 tes, 15 gagal **yang sama persis dengan baseline `fd1236c4`** (bukan dari fitur ini; lihat catatan); kompilasi JS/WasmJs/server/klien-JVM bersih
  - Catatan baseline: 15 tes pack (`ShippedPackIdentityTest`, `DomainPackCodecTest`, dst.) gagal di `fd1236c4` karena `GarmentDomainPack` kini punya `theme` non-null sedangkan `DomainPackCodec` belum meng-encode/decode `theme` -> validator "dokumen wajib identik" menolak. Milik pekerjaan tema (TRD-PLAT-013), tidak disentuh di sini.
- [x] SA1 `ScenarioRules` (validator FR-2 + `validateAll`) · SA2 `DeterministicScenarioBuilder` dari rantai `ModuleHandoff`
- [x] SA3 `DefaultScenarioCarryRule` (koersi tipe konservatif; `FieldTypeConversion` CRM tidak dipakai ulang, alasan di KDoc `ScenarioCarryTypes`) · SA4 `DiscoveryScenarioService` (propose/confirm/edit; LOCKED menolak tulis) · SA5 tes paritas `ScenarioSource.entries` + `DiscoveryDraft.confirmedScenarios()`
- [x] SB1 `KoogScenarioProposer` + `ScenarioReplyParser` (model tak boleh menentukan `source`/`confirmed`) · SB2 rute propose/confirm (403/404/409/422/423) · SB3 flag `BUILDER_SCENARIOS_ENABLED` (default mati)
- [x] SC1 `ScenarioRun` murni (nol dependensi HTTP, dibuktikan tes refleksi)
- [x] SC2 `ScenarioPicker` + `ScenarioPrototypeHost` memakai ulang `MoqupsFlowNavigator` (`startIndex`/`onAdvance` opsional, `MoqupsWireframeCanvas.kt` tetap 560 baris) + rantai skenario di tab Alur Data (kode + tes; **tampilan belum dilihat mata** -> SD1)
- [x] SC4 Klien: `BuilderApiClient.proposeScenarios/confirmScenario`, notifikasi murni, aksi Usulkan/Konfirmasi
- [ ] SC3 Pertanyaan tingkat skenario di utas Semua (FR-9, <= 2 per skenario, badge ikut) — **BELUM**: tidak termasuk service Track A, perlu pekerjaan lanjutan
- [x] S-integrasi (2026-10-16, koordinator): wiring `DiscoveryScenarioService` di `builderRoutes`; kontrak galat final server<->service (`ScenarioInvalidException` -> 422, `LockedException` -> 423, id tak ada -> 404 berbadan JSON `{error}`; heuristik regex pada pesan DIHAPUS); klien memakai `DefaultScenarioCarryRule` (pengganti sementara `StraightCarryRule` dihapus)
- [~] SD1 Cek visual (2026-10-16, **model sungguhan `deepseek-flash`**, tenant `wemade-demo`, DB scratch): bubble BRIEF, baris Skenario, pemutar 7 langkah berurutan skenario, chip "Skenario (1)" hanya menghitung yang terkonfirmasi, rantai skenario di Alur Data, 404 `{error}` untuk id tak ada. **Belum**: `bordir-uji` (tidak ada di seed), klik "Lanjut Alur" sampai membawa data (lihat temuan 3), 2 tenant
- [ ] SD2 nol HTTP saat simulasi **di UI nyata** (terbukti hanya lewat tes refleksi) · SD3 teaching doc + `TASKS-SUMMARY`

### Temuan cek visual dengan model sungguhan (2026-10-16) — 1 dan 2 DIPERBAIKI (2026-10-16, `979ddd04`); 3-5 + 6 belum
Hasil run nyata: brief `AI` terbentuk untuk 5 modul (22 dtk), 2 skenario `AI` diusulkan (10 dtk), keduanya lolos validator dan tersimpan `confirmed=false`.
1. ~~**Pemicu otomatis brief salah momen (bug Fase 1).**~~ **DIPERBAIKI**: pemicu pindah ke `ApplyDraftPatchUseCase` (setelah Terapkan), di server dibuat di latar. Bukti: tes `BriefTriggerTest` (urutan "wawancara sudah tersimpan saat brief dipicu" terbukti merah bila pemicu dipindah ke sebelum simpan) + run model sungguhan: Terapkan 200 dalam 0 dtk, 5 brief `AI` muncul otomatis tanpa `POST /briefs` (BRIEF sebelum Terapkan = 0). Masalah awal: Perencana wawancara berjalan, tetapi hasilnya hanya ada di *usulan perubahan draf* yang belum diterapkan; `triggerQuietly` membaca draf TERSIMPAN, jadi semua modul `skipped`. Brief baru terbentuk setelah "Terapkan" lalu `POST /briefs` manual. Perbaikan: picu setelah patch diterapkan (`ApplyDraftPatchUseCase`) atau pakai draf hasil rencana; perbarui `BriefTriggerTest`.
2. ~~**Kebocoran lingkup di brief.**~~ **DIPERBAIKI**: `ModuleBriefRequest` dapat `upstreamModules/downstreamModules/otherModules` (default kosong) dan prompt melarang langkah modul lain. Bukti: tes `ModuleBriefScopeTest` + `KoogModuleBrieferTest` (prompt memuat batas) dan run model sungguhan: Tahap `sampling_order` kini Pesanan Sampel Dibuat, Pembuatan Pola, Jahit Sampel, Bordir (opsional), Kirim Sampel ke Buyer, Persetujuan Sampel (rework ke Pembuatan Pola) — tanpa "Catat Lead"/"Invoice". Satu run, model non-deterministik: belum jaminan. Masalah awal: Brief `sampling_order` memuat Tahap "Catat Lead" dan "Invoice Sampel & DP" serta fitur sales/invoice — milik modul lain. Prompt `KoogModuleBriefer` hanya memberi cerita utuh + nama modul, tanpa batas lingkup/tetangga. Perbaikan: sertakan modul tetangga (serah-terima masuk/keluar) dan larang langkah modul lain; tambahkan pengecekan di validator bila memungkinkan.
3. **Simulasi di tenant garment demo hanya menampilkan kerangka.** Semua langkah skenario `screenId = null` dan `carries` kosong karena layar demo (`default-<modul>`) tidak punya `ScreenProposal`; pengusul hanya diberi layar berproposal. Nilai utama (data terbawa antar layar) baru teruji di tenant dengan layar hasil agent (mis. klinik). Perbaikan: uji di tenant berproposal; pertimbangkan memberi pengusul layar pack beserta field-nya.
4. **Follow-up AI tidak muncul.** Tidak ada pertanyaan berid `ai:`; kemungkinan besar karena follow-up template yang di-seed klien saat halaman dimuat masih menunggu jawaban, dan aturan "tidak membuat QUESTION baru selama ada yang menunggu" menahannya (belum dibuktikan dengan log). Perbaikan: biarkan follow-up AI menggantikan celah template yang belum dijawab.
5. **Pilihan modul oleh model kurang tepat**: "invoice sampel & DP" dipetakan ke `costing_hpp` padahal ada modul `invoicing`; "bordir" ke `tech_pack_bom`. Perlu diberi deskripsi modul yang lebih jelas di masukan pengusul.

Bukti Fase 2 (2026-10-16): core `jvmTest` hijau penuh (2153+ tes, 0 gagal; 76 tes paket skenario; merah-hijau terbukti untuk aturan `reworkTo` mundur dan pemaksaan `confirmed=false`); server `BuilderScenarioRoutesTest` 9 + `KoogScenarioProposerTest` 5 + `DiscoveryScenarioSummaryApiTest` 1 hijau; klien tes skenario baru 24 hijau; kompilasi JVM/JS/WasmJs bersih. Satu merah di `app/shared` `UiTextLatin1GuardTest` **sudah ada sebelumnya** (karakter non-Latin-1 di `MoqupsWireframeCanvas.kt:218`, `InteractiveTable.kt`, `ModuleDetailPane.kt`, ditulis 14 Okt oleh sesi lain; diverifikasi lewat `git blame`). Belum: Android, cek visual, tes "kunci" selain dua mutasi di atas hanya terbukti hijau.

Estimasi Fase 2: ≈ 4–5 hari (**Medium**) setelah penyelarasan 2026-10-14 (TRD-BLDR-002 §0).

### Bukti penutupan Fase 1 (2026-10-14)
- Core: `ModuleBriefRulesTest`, `GenerateModuleBriefsUseCaseTest`, `ChatMessageKindParityTest`, `ModuleBriefContractTest`, `ModuleBriefCodecTest`, `BriefTriggerTest` (core jvmTest 2029+ hijau, JS/WasmJs terkompilasi).
- Server: `BuilderModuleBriefRoutesTest` (termasuk 403 peran SALES), `KoogModuleBrieferTest`; `PostgresBuilderChatBriefIntegrationTest` **dijalankan pada DB scratch**: V105 terterap, 4 kolom + constraint ada, 1 tes lulus (0 dilewati).
- Klien: `ChatBriefModelTest`, `ChatBriefOpeningTest`; kompilasi JVM/WasmJs/JS. `jvmTest` 1 merah **sudah ada sebelumnya**: `UiTextLatin1GuardTest` (karakter non-Latin-1 di `MoqupsWireframeCanvas.kt`, `InteractiveTable.kt`, `ModuleDetailPane.kt`, bukan dari fitur ini).
- Belum: Android target, cek visual (D1/D2), FR-10 progres SSE per modul (kontrak `generate` tanpa callback — hanya hasil akhir), tes `shownEntries` dengan draf sungguhan.

### Hasil cek visual Track D (2026-10-14, `wemade-demo`, login superadmin demo, DB scratch)
- **Terbukti dengan mata**: (1) utas modul hanya memuat follow-up modulnya sendiri (modal 1/3 berlabel kode modul); (2) badge follow-up semua modul terisi sejak halaman dimuat, tanpa membuka modul; (3) bubble BRIEF tampil benar (badge Ringkasan + AI, Tahap bernomor, tag "opsional", tag "rework ke X", daftar fitur).
- **Catatan jujur**: bubble BRIEF yang dilihat berasal dari **baris yang di-seed lewat SQL** (bukan hasil pembuatan otomatis), karena draf demo tidak punya tautan wawancara/layar berproposal (status `skipped`) dan jalur nyata butuh LLM berbayar. Alur pembuatan end-to-end hanya terbukti lewat tes unit/integrasi, **belum** lewat run nyata dengan model.
- **Bug ditemukan & diperbaiki saat cek visual**: (a) tabrakan PK id pesan follow-up -> 500 (`2300a506`; tes memakai repository ketat karena `InMemoryBuilderChatRepository` menempelkan nomor urut dan menyembunyikan bug sejenis); (b) pesan "sudah terbaru" untuk status `skipped` menyesatkan (`e822ee30`).
- **Belum**: tenant non-garment (`bordir-uji`) secara visual; D2 (AI mati vs AI hidup terlihat berbeda); Android; modal klarifikasi menutup sebagian bubble di tinggi ~900px (observasi UX, belum ditangani).

## 3. Urutan & Estimasi

Fase 1: `T0 (0,5 hari) → A ∥ B ∥ C (≈2 hari paralel) → D (0,5–1 hari)` ≈ 3–4 hari, **Medium**.
Fase 2 menyusul setelah Fase 1 (konteks brief dipakai pengusul skenario).

## 4. Risiko

| Risiko | Mitigasi |
|---|---|
| Mutu ringkasan/pertanyaan AI rendah | Validator ketat + evaluasi deterministik + metrik rasio fallback |
| Biaya model naik (N modul × tiap perubahan cerita) | Idempotensi `basisRef`; hanya regenerasi bila basis berubah; konkurensi 3 |
| Prompt-injection dari cerita | Narasi = data; keluaran dibatasi panjang/format; tidak menulis draf |
| Klien dan server tak sinkron (kind baru) | Dirilis bersamaan; parser klien menolak kind tak dikenal dengan pesan jelas |
| `BuilderChatPane` melewati batas ukuran | C5 |

## 5. Definisi Selesai

Semua AC di TRD §5 terpenuhi dan dicentang **hanya dengan bukti** (nama tes / cek visual); `TASKS-SUMMARY.md` diperbarui.
6. **(baru) Kegagalan perencana wawancara ditelan tanpa jejak.** Pada run verifikasi pertama, perencana tidak menghasilkan wawancara yang lolos validasi, sehingga Terapkan tidak membawa wawancara dan semua modul `skipped`; run kedua dengan cerita yang sama berhasil (11 tautan). Penyebabnya `catch`/`takeIf(validate)` di `SendBuilderMessageUseCase` mengembalikan `null` tanpa log. Perbaikan: catat alasan penolakan (validator berpath) agar kegagalan model terlihat dan terukur; pertimbangkan satu percobaan ulang.
