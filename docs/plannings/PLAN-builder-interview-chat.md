# PLAN — Builder Chat Interview (alur penuh dulu, tanya per modul kemudian)

**Tanggal**: 2026-10-07 · **Jalur**: B · **Status**: disetujui 2026-10-08, Fase A berjalan
**Dasar**: [`PLAN-builder-console.md`](PLAN-builder-console.md), [`discovery-M1-builder-chat.md`](discovery-M1-builder-chat.md),
[`PLAN-discovery-interview-role-module.md`](PLAN-discovery-interview-role-module.md); acuan perilaku: Hercules
(chat di kiri, tampilan hasil di kanan).

## 1. Tujuan

Pengguna bercerita di **chat**. Sistem (1) bertanya hanya bila ceritanya benar-benar kurang jelas, (2) menyusun **alur
penuh + modul** sekaligus dengan model besar (`deepseek-v4-pro`), lalu (3) per modul bisa **ditanya lanjutan** dan
diedit (input ditambah, dikurangi, diganti) dengan model kecil (`deepseek-flash`).

Keputusan yang sudah diambil:

| # | Keputusan |
|---|---|
| K1 | Permukaan kerja = **Builder console** (`BuilderChatPane`), bukan wizard Studio Discovery. Wizard lama dibiarkan. |
| K2 | Interaksi ke server lewat **SSE** supaya bisa asinkron dan menampilkan loading/progres per modul. |
| K3 | **Riwayat chat tersimpan**; setiap kali riwayat dimuat, server memeriksa apakah ada **follow-up** yang menunggu. |
| K4 | Ada dua jenis utas: **Semua** (tanpa filter modul; follow-up mencakup seluruh alur) dan **per modul** (riwayat dan follow-up difilter ke modul itu). |
| K5 | Model besar untuk alur penuh; model kecil untuk follow-up dan edit per modul. |
| K6 | Agent hanya **mengusulkan**; draf berubah lewat aksi manusia (Terapkan) dan selalu lewat validator. Fail-closed, gerbang `MANAGE_BUILDER`. |

## 2. Fakta kode (dibaca 2026-10-07)

**Sudah ada, dipakai ulang**
- `BuilderChatPane` + `ChatMessageBubble`/`ChatResultPanel`: chat kiri, "Hasil request" kanan.
- `BuilderAgent.proposePatch(currentDraft, history, userMessage)`, `BuilderChatRepository`, tabel `builder.chat_messages` (V82), `ChatMessage` (+`proposedDraftJson`, `appliedDraftId`).
- Rute `GET /api/builder/chat`, `POST /api/builder/chat`, `POST /api/builder/chat/apply` (`BuilderRoutes.kt`, 259 baris).
- `SpecOp`/`SpecOpApplier` + `BuilderSpecOpRoutes` (edit field layar: tambah, kurangi, ganti).
- `ScreenProposal`/`EntityProposal.fields` = "input apa saja" sebuah modul; `PrototypeRenderer` untuk preview.
- `InterviewPlanner`, `Clarification`, `AgentInterviewPlanner` (commit `9c345a3`, `16d39f6`): perencana pro yang boleh bertanya satu putaran. **Terbukti live** menghasilkan rencana klinik (divisi, peran, tautan modul, sambungan) pada 2026-10-07.

**Belum ada**
- **SSE**: tidak ada dependensi `ktor-server-sse` di `server/build.gradle.kts`, dan tidak ada pemakaian SSE di klien. Dukungan SSE klien di target Wasm/JS/Android/iOS/Desktop **belum diverifikasi** (risiko R1).
- Utas per modul, jenis pesan "pertanyaan", dan pemeriksaan follow-up saat riwayat dimuat.
- Panel kanan bertab.

## 3. Kontrak

### 3.1 Utas dan pesan
- `ChatMessage` mendapat dua kolom aditif: `moduleId: String?` (null = utas **Semua**) dan `kind: ChatMessageKind` (`TEXT`, `QUESTION`, `ANSWER`). Migrasi **V93** (cek bentrok nomor saat merge), kolom nullable, data lama tidak berubah.
- Pesan `QUESTION` membawa daftar `Clarification` (id, pertanyaan, jawaban). Pesan `ANSWER` merujuk id pertanyaan.
- `ChatMessageKind` adalah enum sah (mekanik chat dimiliki platform, bukan kosakata industri); `moduleId` adalah **data** (kode modul pack), bukan enum.

### 3.2 Follow-up saat riwayat dimuat (K3, K4)
`GET /api/builder/chat?module=<id>` mengembalikan envelope lama ditambah:
```json
{ "conversationId": "...", "messages": [ ... ],
  "followUp": { "scope": "all" | "module", "moduleId": "...",
                "questions": [ { "id": "c1", "question": "..." } ] } }
```
- `module` kosong → utas Semua: pesan **tidak difilter**, follow-up dihitung atas seluruh alur.
- `module` terisi → pesan difilter ke modul itu, follow-up hanya untuk modul itu.
- Follow-up **dihitung dari data** (pertanyaan yang belum terjawab di riwayat + bagian yang belum jelas dari narasi/spesifikasi modul); model hanya dipakai merumuskan kalimat. Tidak ada follow-up → `questions: []`.

### 3.3 SSE (K2)
Riwayat tetap sumber kebenaran; SSE hanya **progres**.
1. `POST /api/builder/chat` → `202 {runId, messageId}` (pesan pengguna disimpan dulu).
2. `GET /api/builder/runs/{runId}/events` (SSE, dukung `Last-Event-ID` agar sambung ulang tidak kehilangan peristiwa).
3. Peristiwa: `status` (`planning`, `drafting`, `validating`, `editing`), `question` (pesan pertanyaan tersimpan), `module_started {moduleId,title}`, `module_ready {moduleId}`, `message` (pesan agent tersimpan), `done`, `error {message}`.
4. Setelah `done`/`error`, klien **memuat ulang riwayat** — jadi bila SSE terputus, tidak ada data hilang.
5. Run milik tenant pemanggil; `runId` tenant lain → 404/403. Run dibatasi satu aktif per utas.
6. Bila SSE tidak tersedia di suatu target (R1), cadangan: polling `GET /runs/{runId}` dengan kontrak peristiwa yang sama.

## 4. Fase

### Fase A — Fondasi: SSE, utas, riwayat, tab (tanpa perubahan AI)
**Hasil**: chat Builder bisa menampilkan loading asinkron; panel kanan bertab Semua + per modul; riwayat difilter per utas; follow-up saat muat bekerja dengan sumber deterministik.

1. **Spike SSE (hari pertama, gerbang)**: tambah `ktor-server-sse`; sambung satu peristiwa dari server ke klien di **Wasm, JS, JVM**; uji Android/iOS dengan kompilasi. Hasil spike menentukan SSE penuh atau cadangan polling (R1). Bila gagal di Wasm (target utama web), berhenti dan putuskan sebelum lanjut.
2. Migrasi V93 + `ChatMessage.moduleId/kind` + repository + codec; `GET /chat?module=` dengan filter.
3. Registri run (`BuilderRunRegistry`, memori + buffer peristiwa untuk replay), `POST /chat` → 202, `GET /runs/{id}/events`. Rute baru di berkas baru `BuilderChatStreamRoutes.kt` (ratchet: `BuilderRoutes.kt` tidak bertambah).
4. Follow-up deterministik di core (`FollowUpPlanner`): pertanyaan yang belum terjawab di riwayat utas itu.
5. Klien: `BuilderChatPane` memakai alur 202+SSE, menampilkan status/loading; panel kanan `ModuleTabs` (Semua + satu tab per modul draf); memilih tab memuat ulang riwayat dengan `?module=`. Pecah file bila `BuilderChatPane` melewati 400 baris.
6. **Tes**: core (filter utas, follow-up murni, `kind`); server (202, SSE berurutan, `Last-Event-ID`, **403 tenant/peran tak berwenang**, run tenant lain 404, satu run aktif per utas); klien (state tab, loading, sambung ulang). **Tenant kedua**: fixture non-garment.
7. **DoD**: 5 target terkompilasi; dijalankan dan **dilihat di browser** (login Superadmin); `scripts/audit-variability.sh` tanpa temuan baru.
8. **Pengalihan wizard (K7)**: daftar kesenjangan fitur wizard vs Builder (ekspor brief, estimasi harga, pilih modul manual), lalu rute `/discovery` dialihkan ke Builder chat.

### Fase B — Alur penuh: bertanya dulu, lalu generate dengan model besar
**Hasil**: pesan pertama → (bila bingung) pertanyaan di chat → jawaban → alur + modul di-generate dengan pro, **progres per modul** tampil, hasil muncul di tab.

1. Agent Builder memakai `AgentInterviewPlanner` (pro): bingung → pesan `QUESTION` + peristiwa `question`; jawaban lewat `POST /chat` (`ANSWER`) → perencana dipanggil lagi, **tidak boleh bertanya lagi** (aturan sudah ada).
2. Setelah rencana: agen draf (pro, `DISCOVERY_AGENT_MODEL`) menyusun draf; peristiwa `module_started`/`module_ready` per modul agar kanan terisi bertahap; patch tetap **usulan** (Terapkan manusia).
3. Hidrasi: tiap modul mendapat `ScreenProposal` awal (Pack/Deterministik/Agent) sehingga tab modul langsung punya preview.
4. Gagal/timeout model → pesan `error` yang jujur + jalur deterministik; riwayat tidak rusak.
5. Follow-up saat memuat utas Semua kini mencakup pertanyaan perencana yang belum dijawab.
6. **Tes**: `ScriptedPromptExecutor` (tanpa biaya) untuk bertanya → jawab → rencana; urutan peristiwa SSE; model gagal; 403. **Eval live** (biaya dicetak dan dikonfirmasi dulu): cerita jelas vs sengaja kabur, **latensi** pro, pemicuan pertanyaan.
7. **DoD**: seperti Fase A, plus cek visual dengan model sungguhan dan saldo API dicatat sebelum/sesudah.

### Fase C — Per modul: follow-up, edit input, preview
**Hasil**: membuka tab modul → server menanyakan follow-up modul itu (flash) → jawaban dan perintah mengedit input diterapkan → preview diperbarui.

1. **Celah modul** dihitung dari data di core (`ModuleGapAnalyzer`): empat bidang spesifikasi (siapa mengisi, apa dicatat, siapa melihat, kapan selesai) yang belum ada dasarnya di narasi/jawaban, plus field `required` tanpa penjelasan. Flash hanya merumuskan pertanyaan dan menafsirkan jawaban.
2. `GET /chat?module=` mengembalikan `followUp` modul; klien menampilkannya sebagai pesan `QUESTION` di utas modul.
3. Jawaban/permintaan ("tambah input tanggal kirim", "ganti X jadi pilihan") → flash menghasilkan `SpecOp` → `SpecOpApplier` + validator → peristiwa `editing`/`module_ready` → preview kanan segar. Penambahan/pengurangan/penggantian input memakai `SpecOp` yang sudah ada, tidak ada format baru.
4. Edit modul tidak memengaruhi modul lain; draf `LOCKED` ditolak (409).
5. **Tes**: celah murni (tenant kedua); flash dengan `ScriptedPromptExecutor`; `SpecOp` ditolak validator; 403; utas Semua vs modul tidak bocor satu sama lain. **Eval live** flash: kualitas pertanyaan dan akurasi `SpecOp`.
6. **DoD**: seperti A, plus dokumen teaching (`docs/teaching/teaching-builder-interview-chat.md`) lewat skill `teaching`.

## 5. Model dan biaya

| Tahap | Model | Env |
|---|---|---|
| Perencana + draf awal | `deepseek-v4-pro` | `DISCOVERY_AGENT_MODEL_PLAN` (jatuh ke `DISCOVERY_AGENT_MODEL`) |
| Follow-up + edit per modul | `deepseek-flash` | `INTERVIEW_AGENT_MODEL` |
| Timeout | rencana 90 dtk, per giliran 20 dtk | `INTERVIEW_PLAN_TIMEOUT_MS`, `INTERVIEW_AGENT_TIMEOUT_MS` |

Pengukuran 2026-10-07: satu sesi (draf pro ± 90 panggilan + perencana) menurunkan saldo ± 0,20 USD. Eval live selalu mencetak estimasi dan minta konfirmasi sebelum jalan.

## 6. Risiko

| ID | Risiko | Mitigasi |
|---|---|---|
| R1 | SSE klien belum terbukti di Wasm/JS/mobile | Spike Fase A langkah 1 sebagai gerbang; cadangan polling dengan kontrak peristiwa sama |
| R2 | Draf pro ± 90 panggilan terasa lama | Progres per modul lewat SSE; ukur latensi di eval Fase B; pertimbangkan batas iterasi |
| R3 | Pro bertanya terlalu sering | Satu putaran tanya, maks 3 pertanyaan, aturan "jangan bertanya lagi" sudah ditegakkan validator |
| R4 | Run berjalan lama saat server restart | Riwayat sumber kebenaran; run hilang = klien memuat ulang riwayat; tidak ada data yang hanya hidup di SSE |
| R5 | Nomor migrasi V93 bentrok | Cek `main` saat merge |
| R6 | `BuilderChatPane` membengkak | Pecah per tanggung jawab (tab, daftar pesan, komposer) sebelum melewati 400 baris |

## 7. Keputusan atas pertanyaan terbuka (2026-10-08)

1. **Utas Semua = tanpa filter modul**: riwayat dan follow-up mencakup seluruh alur. Dikonfirmasi.
2. Jawaban follow-up utas **modul hanya tampil di utas modulnya**. Disetujui.
3. **Wizard lama dialihkan segera ke Builder** (bukan dibiarkan). Konsekuensi: Fase A menambahkan pengalihan
   `/discovery` → Builder chat, dan jalur wizard (`DiscoveryWizardScreen` + `presentation/discovery/interview/*`) ditandai
   usang. Penghapusan kode wizard **tidak** dilakukan di Fase A–C; hanya pengalihan rute, supaya mudah dibatalkan.
   Fitur wizard yang belum ada padanannya di Builder (ekspor brief, estimasi harga, pilih modul manual) dicatat di
   Fase A langkah 8 sebagai daftar kesenjangan sebelum pengalihan diaktifkan.
