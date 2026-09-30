# 🎓 Modul Pembelajaran: AI Mengisi Form Lead CRM (TRD-HELP-002)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Batas "AI mengusulkan, manusia menyimpan", penyamaran data pribadi sebelum LLM, validasi dengan aturan yang sama, opt-in per tenant, form yang digerakkan skema tenant
> **Prasyarat**: [teaching-help-001](teaching-help-001-module-tutorials.md) (Koog, `ScriptedPromptExecutor`, gate RBAC), `CustomFieldDefinition` CRM
> **Referensi Task**: [`docs/trd/TRD-HELP-002-ai-prefill-crm-lead.md`](../trd/TRD-HELP-002-ai-prefill-crm-lead.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Sales menerima "Halo kak, saya Rina dari Batik Sekar, butuh 2 lusin polo bordir, WA 0812…" lalu mengetik ulang ke 6+ kolom.
- **Godaannya**: memberi AI endpoint untuk langsung membuat lead. Akibatnya: AI salah baca, lead salah tersimpan, dan tidak ada yang tahu siapa yang bertanggung jawab.
- **Analogi**: AI di sini adalah **juru tulis magang**. Ia boleh mengisi formulir dengan pensil, tapi yang menandatangani dan memasukkan ke lemari arsip tetap pegawai. Nomor telepon dan email pelanggan ditutup stiker sebelum formulir diserahkan ke juru tulis dari luar kantor.
- **Hasil akhir**:
  - Tombol "Isi dengan AI" di dialog Tambah Lead mengisi field, termasuk kolom kustom tenant, dengan tanda "diisi AI".
  - User memeriksa, lalu menekan **Simpan** lewat endpoint lama.
  - Lead tercatat `created_via = AI_DRAFT`, disimpan oleh user itu.

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0: Keputusan, bukan kode.** Empat keputusan (K1–K4) diambil di TRD **sebelum** baris pertama ditulis:
   - kirim data ke LLM atau tidak (dan bagaimana);
   - penanda asal;
   - pintu masuk;
   - field kustom mana yang didukung.

   Tanpa itu, kode akan mengunci pilihan yang seharusnya milik bisnis.
2. **Langkah 1: Domain murni** (`core/.../domain/crm/prefill/`), dalam urutan ini:
   1. `PiiMasker` (penyamaran)
   2. `LeadDraft` + port `LeadDraftExtractor`
   3. `LeadDraftSanitizer` (validasi dengan aturan lama)
   4. `ExtractLeadDraftUseCase` (merangkai urutan)
   5. `LeadCreationChannel` (asal lead)
3. **Langkah 2: Persistensi.** V84 aditif: `created_via` dengan default `MANUAL` plus tabel `crm_sales.crm_ai_settings` (RLS). Dicoba dulu di Postgres dev dalam `BEGIN … ROLLBACK`.
4. **Langkah 3: Server.**
   - `KoogLeadDraftExtractor`
   - `LeadDraftAgents.fromEnv()` sebagai saklar platform
   - `CrmLeadDraftRoutes`: gate CRM, opt-in 409, **tanpa** `CrmLeadRepository`
5. **Langkah 4: Klien.**
   - `LeadDraftApiClient`
   - `LeadDraftViewModel`, terpisah dari `CrmViewModel`
   - `LeadFormState` (diangkat dari dialog)
   - `LeadAiDraftSection` dan `LeadCustomFieldInputs`

---

## 🧱 3. Bedah Blok per Blok

### Blok A — Urutan use case adalah fitur privasi

```kotlin
val masked = PiiMasker.mask(input)                              // 1. samarkan
val primary = extractor.extract(masked.text, specs).getOrNull() // 2. LLM hanya melihat {TELP_1}/{EMAIL_1}
val restored = raw.mapValues { (_, v) -> masked.unmask(v) }     // 3. kembalikan di server kita
    .filterValues { !LEFTOVER_PLACEHOLDER.containsMatchIn(it) } //    placeholder karangan dibuang
LeadDraftSanitizer.sanitize(restored, definitions, …)           // 4. validasi aturan lama
```

- Pemetaan `{TELP_1} → 0812…` hanya hidup di objek `Masked` selama satu request. Tidak disimpan dan tidak dicatat.
- Kalau LLM mengarang `{TELP_9}`, nilainya dibuang, karena pemetaannya tidak ada. Jangan pernah "menebak" placeholder.

### Blok B — Sanitizer memakai aturan yang **sama** dengan form manual

| Field | Aturan |
|---|---|
| Nomor | `WhatsappNumber.parse` (parser yang sama dengan dialog) |
| Brand / kontak | ≤150 (sama dengan kolom DB) |
| Kategori | ≤100 |
| Pcs | ≥0 |

Opsi Pilihan dicocokkan **tepat** dengan label (tanpa membedakan huruf besar/kecil). "Bordir Laser" tidak pernah dipetakan ke "Bordir Komputer". Nilai yang gagal dikosongkan dan diberi `DraftIssue`, tidak pernah dipaksa masuk.

### Blok C — Route yang secara konstruksi tidak bisa menyimpan

```kotlin
fun Route.crmLeadDraftRoutes(roleRepository, moduleAssignmentRepository, customFieldRepository,
                             settingsRepository, extractor)   // ← tidak ada CrmLeadRepository
```

Route draf tidak **punya** repository lead. Karena itu "draf tidak pernah disimpan" dijamin oleh kompilator, bukan hanya oleh test. Gate berjalan sebelum body dibaca:

| Endpoint | Aturan |
|---|---|
| draf | `moduleDecision(CRM_SALES)` (termasuk entitlement) → OPERATE; belum opt-in → 409 |
| mengaktifkan opt-in | MANAGE, karena admin pabrik yang memutuskan nama pelanggan boleh dikirim ke penyedia AI |

### Blok D — Form yang digerakkan skema tenant

```kotlin
DraftFieldSpec(d.id.value, d.label, DraftFieldKind.SELECT, t.activeOptions.map { it.label })
```

Prompt dibangun dari `CustomFieldDefinition` tenant, bukan dari daftar kolom yang ditulis di prompt. Saat cek visual, tenant `wemade-demo` punya kolom "Kategori Pakaian" dan "Jenis Sablon". LLM mengisinya dengan **Polo** dan **Bordir** dari opsi yang ada, padahal tidak satu baris kode pun menyebut kedua kolom itu.

### Blok E — Asal yang jujur

`LeadFormState.usedAiDraft` tetap `true` walaupun user mengoreksi semua field. Lead itu **berawal** dari draf AI, dan kolom `created_via` mencatat asal, bukan mutu. Penandanya dibaca bersama `created_by_user_id`: "dibuat dari draf AI **oleh** X".

---

## ⚖️ 4. The "Why"

| Pilihan | Alternatif | Kenapa ini |
|---|---|---|
| Samarkan telepon/email dengan regex lokal | Kirim apa adanya | Regex lebih andal daripada LLM untuk pola ini, dan data paling sensitif tidak keluar. Preseden auto-map invoice memilih privasi |
| Kolom `created_via` di lead | Audit log tenant baru | Audit log yang ada khusus platform; audit tenant adalah proyek sendiri. Kolom aditif cukup untuk mengukur mutu AI |
| ViewModel draf terpisah | Menambah ke `CrmViewModel` (390 baris) | Alur simpan lead tidak berubah satu baris pun. Fitur bisa dicabut tanpa menyentuh CRM |
| Opt-in per tenant + saklar platform | Hanya saklar env | Keputusan mengirim data pelanggan ada di tangan pemilik data (pabrik), bukan di tangan kita |

---

## ⚠️ 5. Jebakan Pemula

1. **"Log kita bersih" belum berarti log bersih.** Cek visual menemukan nama pelanggan di log server. Penyebabnya bukan kode kita, melainkan `logback.xml` dengan root **TRACE**, yang membuat Koog dan klien HTTP Apache mencetak prompt serta jawaban LLM. Perbaikannya: `ai.koog` dan `org.apache.hc` dinaikkan ke INFO. Selalu `grep` log sungguhan dengan data uji, jangan hanya membaca kode logging sendiri.
2. **Menguji privasi dari hasil akhir saja.** Draf yang benar tidak membuktikan nomornya tidak terkirim. Test memeriksa **prompt** (`ScriptedPromptExecutor.lastPromptText()` tidak memuat `3456`), dan cek visual memeriksa log lalu lintas HTTP.
3. **Membiarkan AI memilih tahap atau pemilik lead.** Kunci di luar field yang ditawarkan (`stage`, `ownerEmployeeId`) dibuang di ekstraktor. Tahap draf selalu New Lead, karena Qualified harus lewat `QualifyLeadUseCase`.
4. **Menjalankan test server tanpa sadar ia memigrasi DB dev.** Test server repo ini memanggil `DatabaseFactory.init()` dengan Flyway aktif, jadi V84 langsung terpasang di DB dev bersama. Periksa nomor migrasi sesi lain sebelum menjalankan test; di sini sesi lain kemudian memakai V85.

---

## 🧪 6. Bukti

| Test | Jumlah | Isi |
|---|---|---|
| `PiiMaskerTest` | 3 | Telepon/email disamarkan lalu kembali utuh; nomor yang sama berbagi satu placeholder; angka biasa (500 pcs, SPK) tidak dianggap telepon |
| `ExtractLeadDraftUseCaseTest` (skema **bordir**) | 6 | Ekstraktor hanya melihat teks tersamar; spesifikasi field dari skema (DATE & arsip tidak ditawarkan); nilai tidak sah dibuang + issue; placeholder karangan dibuang; LLM gagal jatuh ke deterministik parsial ("2 lusin" = 24); teks kosong/terlalu panjang ditolak |
| `KoogLeadDraftExtractorTest` | 4 | Prompt tanpa nomor asli; kunci di luar field (stage, owner) dibuang; jawaban non-JSON jatuh ke deterministik; seleksi env |
| `CrmLeadDraftRoutesTest` (app Ktor mini) | 3 | Anonim ditolak; VIEW 403; opt-in default mati (409); OPERATE tidak bisa mengaktifkan (403); MANAGE bisa; 400 untuk body tidak sah |
| `LeadDraftViewModelTest` | 4 | Draf diterapkan dengan tanda AI; field yang sudah diketik tidak ditimpa; mengetik menghapus tanda; `createdVia` tetap AI_DRAFT; kolom wajib tenant memblokir Simpan |
| `RouteGateTest` | — | Probe mencakup `/crm/leads/draft` dan `/crm/ai-settings` |
| Regresi | 8 suite | Test CRM dan field kustom yang sudah ada tetap hijau |

**Cek visual** (`wemade-demo`, `LEAD_DRAFT_AGENT=koog`, 2026-09-30):
1. Aktifkan fitur.
2. Tempel chat.
3. DeepSeek mengisi brand, kontak, WA, email, kategori, 24 pcs, Kategori Pakaian = Polo, dan Jenis Sablon = Bordir.
4. Simpan: DB mencatat `created_via = AI_DRAFT`, `created_by_user_id = usr-superadmin-001`, `stage = NEW_LEAD`.
5. Log lalu lintas HTTP: `3456` dan `batiksekar` muncul **0 kali**.

Artefak uji (lead dan baris opt-in) sudah dihapus lagi.

---

## 🏆 7. Tantangan Mandiri

- [ ] Tampilkan "Dibuat dari draf AI oleh X" di detail lead (`LeadInspectorDetailTab`) dari `createdVia`.
- [ ] Hitung metrik mutu: berapa persen lead `AI_DRAFT` yang field-nya dikoreksi sebelum disimpan. Apa yang perlu dikirim klien agar ini bisa diukur tanpa menyimpan teks aslinya?
- [ ] Fase 5b: dari chat Tanya AI ("catat lead PT Maju…") buka dialog ini dengan teks sudah tertempel. Klasifikasi niat bertanya vs meminta input sebaiknya diletakkan di mana?

---

# Bagian 2 — Fase 5b: Dari Chat ke Form

## Alur

1. Di chat Tanya AI (dari layar mana pun), user menulis "catat lead Batik Sekar Solo, kontak Rina 0812…, polo bordir 2 lusin".
2. Server mengenali **perintah input** dan menjawab dengan aksi `PrefillLead`, tanpa memanggil LLM helper.
3. Tombol "Isi form lead dari pesan ini" menaruh aksi di `HelpActionRequests`, lalu membuka modul CRM.
4. `CrmWorkspaceScreen` mengambil aksi itu dan membuka dialog Tambah Lead dengan teks tertempel. Dialog meminta draf begitu pengaturan tenant termuat.
5. User memeriksa, lalu menyimpan sendiri.

## Keputusan desain

| Keputusan | Kenapa |
|---|---|
| Niat dideteksi **deterministik** (`LeadEntryIntent`), bukan oleh LLM | Pesan itu berisi data pelanggan. Mengirimnya ke LLM hanya untuk bertanya "ini perintah atau pertanyaan?" sama dengan membocorkan data demi klasifikasi. Aturannya ketat: kata perintah + kata benda lead + ada data, dan kalimat tanya tidak pernah lolos |
| Aksi ditawarkan dengan gerbang **yang sama** dengan `POST /crm/leads/draft` (CRM OPERATE + opt-in) | Tombol di chat tidak boleh membuka jalan yang endpoint-nya akan menolak. Peran VIEW tetap mendapat jawaban tutorial |
| `HelpActionResolver` dirakit di wiring (`leadPrefillActions`) | `HelpRoutes` dan `AskHelpUseCase` tetap buta modul. Aksi baru (misalnya kontak vendor) cukup menambah resolver |
| Kotak surat `HelpActionRequests`, bukan callback ke layar CRM | Arah ketergantungan satu jalan: modul → help. Layar CRM **mengamati** kotak surat, jadi aksi tetap jalan walau user sudah berada di CRM (navigasi tanpa perubahan layar) |
| `ExtractWhenReady` | Dialog baru dibuka dan pengaturan belum termuat. Ekstraksi ditunda sampai jelas; bila tenant belum opt-in, teks hanya tertempel dan tidak pernah dikirim |

## Celah yang ikut ditutup

Sebelum 5b, pertanyaan ke AI helper (`HELP_AGENT=koog`) dikirim **apa adanya**, jadi "cara bikin lead untuk budi@maju.co.id 0812…" sampai ke LLM dengan nomor aslinya. Sekarang `AskHelpUseCase` selalu menyamarkan pertanyaan dengan `PiiMasker` sebelum sampai ke agent (dikunci `agentSeesMaskedQuestion_noPhoneOrEmail`).

## Bukti

| Test | Isi |
|---|---|
| `LeadEntryIntentTest` (3) | Perintah + data terdeteksi. "gimana cara bikin lead", "buat lead baru di mana", "buat HPP…", dan "ada buyer baru chat WA…" (tanpa kata perintah) **tidak** terdeteksi |
| `AskHelpUseCaseTest` (+2) | Aksi melewati pencocok dan agent (agent tidak melihat pesan); agent hanya melihat pertanyaan tersamar |
| `LeadPrefillActionsTest` (1) | Belum opt-in, VIEW, atau tanpa keputusan = tanpa aksi; pertanyaan tetap tutorial |
| `HelpCodecTest` (+1) | Aksi bolak-balik; jenis aksi yang tidak dikenal dilewati |
| `HelpChatViewModelTest` (+1), `LeadDraftViewModelTest` (+1) | Aksi tersimpan di pesan dan dipancarkan sebagai efek; ekstraksi menunggu pengaturan dan tidak jalan bila fitur mati |

**Cek visual** (`wemade-demo`, 2026-10-01): dari layar Order Sampling, chat "catat lead Batik Sekar Solo, kontak Rina 0812…, pesan polo bordir 2 lusin" memunculkan tombol aksi. Setelah ditekan, CRM terbuka, dialog terisi (brand, kontak, nomor, "polo bordir", 24 pcs), dan email tetap kosong karena tidak ada di pesan. Log server mencatat `agent=intent/help-action-v1`, dan nomor maupun nama pelanggan muncul 0 kali di log.

## Tantangan

- [ ] Tambahkan resolver kedua (kontak vendor) tanpa menyentuh `HelpRoutes` maupun `AskHelpUseCase`.
- [ ] Apa yang terjadi bila user menekan tombol aksi di pesan lama setelah admin mematikan opt-in? Telusuri sampai ke `LeadDraftDisabledException`.
