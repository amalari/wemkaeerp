# PLAN: Ringkasan & Follow-Up per Modul di Chat Builder

**Tanggal**: 2026-10-14
**Status**: Sebagian (2026-10-17) — Track 0/A/B/C, temuan 1–14 (5 diputuskan opsi b, 11 diperbaiki) dan teaching doc selesai di `main`; sisa: D3 (Android + graphify update), SC3. Flag server `BUILDER_MODULE_BRIEFS_ENABLED` default MATI
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
- [x] D2 (2026-10-17) Agent model mati (tanpa `DISCOVERY_AGENT`/`INTERVIEW_AGENT`), tenant `wemade-demo`: setelah Terapkan, `POST /chat/briefs` membuat brief `briefSource=DETERMINISTIC` + satu follow-up; UI menampilkan bubble Ringkasan berlabel "Disusun otomatis dari data" dan badge 3 klarifikasi
- [~] D3 (2026-10-17) `audit-variability.sh` 0 temuan; kompilasi JVM + WasmJs + JS bersih; `:app:shared:jvmTest` + `:core:jvmTest` hijau. **Belum**: Android (SDK tak terpasang) dan `graphify update .`
- [x] D4 Teaching doc [`teaching-builder-module-briefs-and-scenarios.md`](../../teaching/teaching-builder-module-briefs-and-scenarios.md) + `TASKS-SUMMARY.md` diperbarui (2026-10-17)

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
- [ ] SC3 Pertanyaan tingkat skenario di utas Semua (FR-9, <= 2 per skenario, badge ikut) — **BELUM**: Discovery Note selesai 2026-10-17 di [`PLAN-builder-scenario-questions.md`](PLAN-builder-scenario-questions.md) (menunggu keputusan D1–D3); belum ada kode
- [x] S-integrasi (2026-10-16, koordinator): wiring `DiscoveryScenarioService` di `builderRoutes`; kontrak galat final server<->service (`ScenarioInvalidException` -> 422, `LockedException` -> 423, id tak ada -> 404 berbadan JSON `{error}`; heuristik regex pada pesan DIHAPUS); klien memakai `DefaultScenarioCarryRule` (pengganti sementara `StraightCarryRule` dihapus)
- [~] SD1 Cek visual (2026-10-16, **model sungguhan `deepseek-flash`**, tenant `wemade-demo`, DB scratch; lihat juga "Cek visual draf klinik" di bawah): bubble BRIEF, baris Skenario, pemutar 7 langkah berurutan skenario, chip "Skenario (1)" hanya menghitung yang terkonfirmasi, rantai skenario di Alur Data, 404 `{error}` untuk id tak ada. **Belum**: `bordir-uji` (tidak ada di seed), klik "Lanjut Alur" sampai membawa data (lihat temuan 3), 2 tenant
- [ ] SD2 nol HTTP saat simulasi **di UI nyata** (terbukti hanya lewat tes refleksi) · [x] SD2 (2026-10-17, UI nyata) nol request `/api/` saat memilih skenario, Lanjut Alur, mundur, maju (log resource browser; kontrol: klik Segarkan ringkasan tertangkap sebagai `POST /chat/briefs`); skenario disisipkan di DB scratch karena pembangun deterministik tak mengarang tanpa handoff wawancara · [x] SD3 teaching doc + `TASKS-SUMMARY` (2026-10-17)

### Temuan cek visual dengan model sungguhan (2026-10-16) — 1, 2, 4, 6, 7 DIPERBAIKI (`979ddd04`, `32c29147`, `a5dfb635`, `ed4fc126`, `fd497891`); 3 ditunda (keputusan: uji dulu di tenant berproposal); 5 belum
Hasil run nyata: brief `AI` terbentuk untuk 5 modul (22 dtk), 2 skenario `AI` diusulkan (10 dtk), keduanya lolos validator dan tersimpan `confirmed=false`.
1. ~~**Pemicu otomatis brief salah momen (bug Fase 1).**~~ **DIPERBAIKI**: pemicu pindah ke `ApplyDraftPatchUseCase` (setelah Terapkan), di server dibuat di latar. Bukti: tes `BriefTriggerTest` (urutan "wawancara sudah tersimpan saat brief dipicu" terbukti merah bila pemicu dipindah ke sebelum simpan) + run model sungguhan: Terapkan 200 dalam 0 dtk, 5 brief `AI` muncul otomatis tanpa `POST /briefs` (BRIEF sebelum Terapkan = 0). Masalah awal: Perencana wawancara berjalan, tetapi hasilnya hanya ada di *usulan perubahan draf* yang belum diterapkan; `triggerQuietly` membaca draf TERSIMPAN, jadi semua modul `skipped`. Brief baru terbentuk setelah "Terapkan" lalu `POST /briefs` manual. Perbaikan: picu setelah patch diterapkan (`ApplyDraftPatchUseCase`) atau pakai draf hasil rencana; perbarui `BriefTriggerTest`.
2. ~~**Kebocoran lingkup di brief.**~~ **DIPERBAIKI**: `ModuleBriefRequest` dapat `upstreamModules/downstreamModules/otherModules` (default kosong) dan prompt melarang langkah modul lain. Bukti: tes `ModuleBriefScopeTest` + `KoogModuleBrieferTest` (prompt memuat batas) dan run model sungguhan: Tahap `sampling_order` kini Pesanan Sampel Dibuat, Pembuatan Pola, Jahit Sampel, Bordir (opsional), Kirim Sampel ke Buyer, Persetujuan Sampel (rework ke Pembuatan Pola) — tanpa "Catat Lead"/"Invoice". Satu run, model non-deterministik: belum jaminan. Masalah awal: Brief `sampling_order` memuat Tahap "Catat Lead" dan "Invoice Sampel & DP" serta fitur sales/invoice — milik modul lain. Prompt `KoogModuleBriefer` hanya memberi cerita utuh + nama modul, tanpa batas lingkup/tetangga. Perbaikan: sertakan modul tetangga (serah-terima masuk/keluar) dan larang langkah modul lain; tambahkan pengecekan di validator bila memungkinkan.
3. **Simulasi di tenant garment demo hanya menampilkan kerangka.** Semua langkah skenario `screenId = null` dan `carries` kosong karena layar demo (`default-<modul>`) tidak punya `ScreenProposal`; pengusul hanya diberi layar berproposal. Nilai utama (data terbawa antar layar) baru teruji di tenant dengan layar hasil agent (mis. klinik). Perbaikan: uji di tenant berproposal; pertimbangkan memberi pengusul layar pack beserta field-nya.
4. ~~**Follow-up AI tidak muncul.**~~ **DIPERBAIKI** (`a5dfb635`): kebijakan "AI menggantikan template". Hanya pertanyaan AI yang menunggu yang menahan pertanyaan baru; modul yang pernah diberi pertanyaan AI menyembunyikan template-nya (tidak dihapus, tidak bangkit setelah AI dijawab); AI tidak dicampur template; `AskModuleFollowUps` tidak membuat template untuk modul ber-brief. Penyebab terbukti (guard `pendingFollowUps(id).isEmpty()`, tes merah bila dikembalikan). Bukti run nyata (9 template di-seed lebih dulu): pertanyaan AI tetap terbentuk untuk `operator_exec` (3), `fulfillment` (2), `production_mrp` (2) dan kontekstual ("Apakah pekerjaan bordir dicatat terpisah dari pekerjaan menjahit sampel?"); server menampilkan **hanya AI** untuk modul itu (gap 0), modul lain tetap template; utas Semua 25 menunggu = 7 AI + 18 template (27 - 9 digantikan). Catatan: `sampling_order` dan `crm_sales` tidak mendapat pertanyaan AI karena model memilih kosong (sah) -> template sebagai cadangan.
5. ~~**Pilihan modul oleh model kurang tepat**~~ **DITUTUP: BUKAN BUG** (2026-10-16, Jalur R; bukti diverifikasi koordinator). `invoicing` bertipe `FOUNDATION` (`GarmentModules.kt`) dan tidak ada di blueprint garment mana pun; `ScenarioRequests.of` (`DiscoveryScenarioService.kt`) hanya menawarkan modul `OPERATIONAL` yang aktif di blueprint, jadi model memilih yang terdekat dari yang ditawarkan. Deskripsi modul pack SUDAH dikirim ke model (`ScenarioModuleInput.summary`). Konsekuensi produk (bukan perbaikan bug): langkah skenario tidak bisa menyebut modul FOUNDATION/tak aktif, mis. "invoice DP" di garment; **KEPUTUSAN 2026-10-17: opsi (b) diterapkan** (`ScenarioRules.eligibleModules`: OPERATIONAL aktif + FOUNDATION berdeskripsi; GOVERNANCE tidak; tes `ScenarioFoundationModuleTest`, core+server hijau; belum diuji dengan model sungguhan). Pilihan awal: (a) aktifkan modul penagihan di blueprint draf, atau (b) ikutkan modul FOUNDATION berdeskripsi dalam tawaran pengusul (menyentuh paritas). Catatan asli:: "invoice sampel & DP" dipetakan ke `costing_hpp` padahal ada modul `invoicing`; "bordir" ke `tech_pack_bom`. Perlu diberi deskripsi modul yang lebih jelas di masukan pengusul.

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
6. ~~**(baru) Kegagalan perencana wawancara ditelan tanpa jejak.**~~ **DIPERBAIKI** (`32c29147`): `attemptPlan` mengklasifikasi `PLANNER_ERROR`/`INVALID_PLAN`/`UNEXPECTED_SECOND_ROUND`, server mencatat `interview_plan_rejected` (tanpa isi narasi); perilaku chat tidak berubah; tes merah saat pelaporan dimatikan. Bukti run nyata: log menjelaskan kegagalan percobaan pertama.
7. ~~**(baru, dari log temuan 6) Perencana model gagal menghasilkan handoff sah.**~~ **DIPERBAIKI** (`ed4fc126`, `fd497891`). Koreksi atas dugaan awal ("model mengarang port"): nama port di galat (`ProductionOrd...`) mirip port sah; penyebab sebenarnya validator menuntut portType handoff = **keluaran modul asal ATAU masukan modul tujuan**, sedangkan katalog yang dilihat model hanya memuat `wiredPorts` global (tanpa port per modul) dan `PLAN_INSTRUCTION` tidak menyebut aturannya, sehingga model menebak dan gagal 3 putaran. Perbaikan: katalog `interview_catalog` memuat `input`/`output` tiap modul (dari `slotPorts`, sumber yang sama dengan validator) + aturan port di prompt sistem dan instruksi rencana; batas detail log 300->900 (memotong penyebab). Bukti: 3 tes merah bila katalog/prompt dikembalikan. Uji model sungguhan, 6 percobaan tanpa Terapkan: **6/6 menghasilkan wawancara, 0 penolakan di log** (sebelumnya sekitar separuh run gagal di percobaan pertama; anekdot dari 4 run, bukan baseline terukur). **Uji A/B**: kalimat tambahan "lebih baik tanpa handoff itu" membuat model terlalu hati-hati: handoff 4,1,1,1,3,4 (rata-rata 2,3, min 1) vs 6,6,3,3,6,6 (rata-rata 5,0, min 3) tanpa kalimat itu, keduanya 6/6 sah; kalimat dihapus. Batas: n=6 per varian, satu cerita, satu tenant; jumlah handoff bukan skor mutu (belum dinilai manusia benar tidaknya).

### Cek visual draf klinik non-garment dengan model sungguhan (2026-10-16)
`bordir-uji` ternyata bukan seed di repo (tenant garment bertemplate bordir yang dibuat manual di DB seseorang), jadi bukan tenant non-garment. Pengganti yang lebih jujur: cerita **klinik gigi** dikirim ke Builder tenant demo; agent menyusun **pack `klinik` (6 modul, 8-9 layar, semuanya berproposal)**.
- **Terbukti**: brief `AI` otomatis setelah Terapkan dalam bahasa klinik (Tahap + tag "opsional - rework ke ..."); `POST /scenarios/propose` menghasilkan **3 skenario `AI` dalam 24 dtk dengan id layar nyata dan pemetaan field nyata** (`nama>pasien`, `pasien>pasien`, `tindakan>nama`, `tarif>total`) + loop rework, lolos validator; konfirmasi 200; UI: 3 chip skenario, navigator 5 langkah berurutan skenario tanpa lencana kerangka, formulir "Daftar Pasien Baru" bisa diisi dan disimpan (data masuk). Menjawab temuan 3: nilai skenario terlihat di tenant berlayar berproposal, bukan di garment demo.
- ~~**Temuan 9 (nyata, dilihat di UI): simulasi berhenti di langkah Antrean.**~~ **DIPERBAIKI** (`e197a3f1`, lanjutan `d2ac0a60`; keputusan pemilik: nilai contoh). `SampleValues` (core) mengisi field wajib yang tak tercakup pembawa dengan nilai contoh yang selalu lolos `FieldSpec.accepts`; RELATION/USER_REF/FILE tidak dikarang (tetap memblokir); `Moved.sampled` memberi tahu pengguna ("Diisi contoh karena wajib dan belum ada datanya: Nomor antrean. Ganti sesuai kebutuhan."). Masalah awal: "Lanjut Alur" tidak berpindah; muncul `'Nomor antrean' wajib diisi`. Entitas `antrean` menuntut `nomor*`, `pasien*`, `status*` (ENUM), sedangkan skenario hanya membawa `nama>pasien`. `nomor` dan `status` adalah data baru yang lahir di langkah itu, jadi pembawa data tidak bisa mengisinya, dan aturan Track C ("layar menolak baris -> Blocked tanpa jejak") menghentikan alur. Untuk simulasi yang tujuannya melihat alur, ini menghalangi. **Perlu keputusan produk** (usulan: isi field wajib yang tak tercakup carries dengan nilai contoh yang ditandai "contoh"; alternatif: buka layar berikutnya dengan formulir terisi sebagian).
- **Temuan 8 (dugaan, belum terbukti): perencana berjalan terhadap pack LAMA saat industri berubah.** Percobaan 1 (garment -> klinik) menghasilkan patch ber-pack klinik tanpa wawancara (links=0) dan **tanpa** log penolakan; percobaan sesudah pack klinik tersimpan menghasilkan links=7. Akibat bila benar: pesan pertama di industri baru tidak membawa wawancara/brief sampai ada pesan kedua. Belum diselidiki (keluaran perencana tidak terlihat).
- **Temuan 10 (UI kecil)**: tag "opsional - rework ke ..." di Tahap membungkus 3 baris di kolom sempit; pesan sukses "Data 'Pasien' berhasil ditambahkan!" tetap tampil setelah percobaan Lanjut yang diblokir; alasan pemblokiran hanya teks kecil abu-abu di atas artboard, mudah terlewat.
- **Temuan 11 (SELESAI 2026-10-17, `a5e5c3c8`)**: Terapkan membalas 409. Terbukti lewat log `patch_apply_rejected` (model sungguhan): port handoff wawancara lama (`ANTREAN`/`PEMBAYARAN`) tak cocok slot baru (`PASIEN`/`TAGIHAN`) karena adapter menyalin wawancara lama bila `pack.code` sama. Perbaikan: wawancara basi di usulan direncanakan ulang, bila gagal dibuang+dilaporkan (`StaleInterviewReplanTest`). Verifikasi model sungguhan: 2 siklus revisi (pesan 1 -> Terapkan -> pesan 2 -> Terapkan), semua 200, 0 `patch_apply_rejected`. Batas kepastian: 4 Terapkan; 409 tidak muncul lagi, tetapi sampel kecil.
- **Verifikasi temuan 9 di UI nyata (run klinik kedua, pack `klinik_gigi`, build baru):** "Lanjut Alur" Pendaftaran -> Antrean kini **berpindah** dan papan menampilkan kartu baru "(contoh)" berisi nama dan keluhan yang terbawa; langkah 3 (Pemeriksaan) juga berpindah dengan pesan "Diisi contoh ...: Diagnosa". 
- **Temuan 13 (ditemukan karena dilihat di UI, bukan oleh tes) — DIPERBAIKI** (`d2ac0a60`): setelah perbaikan 9, "Lanjut" berhasil tetapi kartu **tidak tampil** di papan: papan mengelompokkan baris menurut field status, dan `status` bermesin-status itu tidak wajib sehingga baris tanpa status tak masuk kolom mana pun. `SampleValues.initialState/withInitialState` (keadaan sumber-saja; cadangan opsi pertama) dipakai sebelum nilai contoh. Bukti: 2 tes merah bila aturan dimatikan + UI: Menunggu 1 -> 2 dengan kartu baru.
- **Temuan 8 terulang (2 dari 2 run klinik independen):** pesan pertama di industri baru menghasilkan patch tanpa wawancara dan tanpa log penolakan; wawancara baru ada di percobaan kedua. Dugaan perencana memakai pack lama tetap belum dibuktikan, tetapi pola kini konsisten.
- **Temuan 12 (baru): klarifikasi menumpuk setiap generasi brief.** Badge "klarifikasi" naik ke **46** (sebelumnya 17) setelah dua pesan cerita, karena brief baru (basis berubah, FR-7) membuat pertanyaan AI baru sementara pertanyaan AI lama yang belum dijawab tidak digantikan. Perlu aturan: pertanyaan AI generasi baru menggantikan yang lama yang belum dijawab untuk modul yang sama.
- **Temuan 14 (baru, UX): layar tujuan berbentuk FORMULIR tidak menampilkan baris yang terbawa.** Pada langkah "Dokter periksa pasien" (formulir) pesan "Diisi contoh: Diagnosa" muncul tetapi formulir tampil kosong; baris ada di memori sesi namun formulir hanya untuk entri baru. Usulan: untuk target formulir, isi formulir dengan nilai terbawa + contoh (tanpa membuat baris sampai disimpan), atau tampilkan strip "baris terbawa".
- **Temuan 12 DIPERBAIKI** (generasi pertanyaan AI terbaru menggantikan yang lama). `pendingFollowUps` hanya menghitung pesan pertanyaan AI TERBARU per modul (yang lama disembunyikan, bukan dihapus, tak bangkit lagi); pertanyaan AI lama yang belum dijawab tidak dikirim ke model sebagai "sudah ditanyakan" (yang masih relevan boleh ditanyakan ulang, id sama); pesan baru hanya dibuat bila ada pertanyaan yang belum tampil (himpunan sama tidak digandakan). Bukti: tes skenario "cerita berubah dua kali" + 4 mutasi terpisah memerahkan tes yang tepat (penyaringan generasi, penahan lama, pengecualian `asked`, penggandaan). **Belum diverifikasi dengan run model baru**: badge 46 -> angka lebih kecil hanya terbukti lewat tes dengan briefer berskrip, bukan lewat UI.

### Jalur paralel P/Q/R (2026-10-16): temuan 5, 8, 10, 11, 14 — ringkasan dan verifikasi koordinator
- **Temuan 8 DIPERBAIKI** (Jalur Q, `a7d0df66`, merge `185ac125`). Akar terbukti dengan tes merah (`PackChangePlanTest`, 2 dari 3 merah sebelum perbaikan): perencana hanya berjalan terhadap draf tersimpan (pack LAMA), dan `DiscoveryBackedBuilderAgent` menyalin wawancara hanya bila `isPlanned` **dan** `pack.code` sama, sehingga saat pack berganti wawancara terbuang. Perbaikan: bila usulan agent ber-pack berbeda dan belum berwawancara, perencana dijalankan SEKALI LAGI terhadap pack baru (`answeredRound = true`, kegagalan dilaporkan lewat `onPlanRejected`). Kaveat: satu panggilan model terbuang saat industri berganti. **Bukti model sungguhan**: 2 trial segar (draf garment, cerita klinik sebagai pesan pertama): patch PERTAMA membawa wawancara pada 2 dari 2 (links=7, handoffs=5), 0 baris `interview_plan_rejected`; sebelumnya 0 dari 2 membawa wawancara.
- **Temuan 14 DIPERBAIKI** (Jalur P, `c5c3db2f`, merge `4803a853`). Bila layar tujuan berbentuk formulir, "Lanjut Alur" mengisi isian formulir (nilai terbawa + status awal + contoh) TANPA membuat baris; baris lahir saat pengguna menyimpan; non-formulir tidak berubah. **Terlihat di UI nyata**: formulir "Dokter periksa & catat" tampil terisi ("Sari Wulandari" terbawa) dengan kartu "Periksa dulu: Formulir sudah terisi dari langkah sebelumnya. Periksa, lalu simpan."
- **Temuan 10 DIPERBAIKI** (Jalur P). (a) tag Tahap kini dua tag terpisah ("opsional", "rework ke X"), **terlihat rapi di UI nyata**; (b) pesan sukses basi hilang saat berpindah (terlihat: tidak ada "Data ... berhasil ditambahkan!" setelah "Lanjut"); (c) alasan/peringatan skenario kini kartu `ScenarioNoticeBar` bernada (kuning "Periksa dulu", biru "Info"; merah "Tidak bisa lanjut" untuk blokir), **terlihat di UI nyata**. Kartu merah "Tidak bisa lanjut / Simpan dulu" **belum dilihat di UI** (hanya tes unit `ScenarioFormPrefillTest`): pada skenario uji, tujuan langkah 4 adalah dokumen cetak tanpa entitas sehingga "Lanjut" dari formulir yang belum disimpan berpindah dengan kartu Info, bukan memblokir (perilaku konsisten dengan kode).
- ~~**Temuan 11 TIDAK TERBUKTI** (Jalur Q)~~ (digugurkan: terbukti dan diperbaiki 2026-10-17, lihat atas): validator tidak menolak draf klinik berwawancara dengan layar lebih sedikit dari modul (`ApplyRejectionTest`); kandidat terkuat wawancara basi (adapter menyalin wawancara lama bila `pack.code` sama) tidak bisa dibuktikan tanpa keluaran model. Ditambah log `patch_apply_rejected tenant= alasan=` (logger `builder.apply`) agar penyebab terlihat bila terulang; klien sudah menampilkan badan 409. Run koordinator: Terapkan 200 pada kedua trial, tidak ada penolakan, jadi belum ada data. **Tetap terbuka sebagai "pantau"**: bila 409 terulang, ambil baris `patch_apply_rejected` (path di `alasan` menentukan penyebab: `$.interview.links[..]` = wawancara basi; "sudah diterapkan" = klik ganda; `DraftLockedException` = draf terkunci).
- **Temuan 5 DITUTUP** (Jalur R) — lihat butir 5 di atas.
- Verifikasi gabungan di `main` (`4803a853`): core `jvmTest` hijau penuh, `app:shared` `jvmTest` hijau penuh, tes server Builder/Koog/Interview hijau, kompilasi JVM/JS/WasmJs/server bersih; `MoqupsWireframeCanvas.kt` tidak berubah (560 baris).
