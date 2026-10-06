# PLAN — Wawancara Discovery: Divisi → Peran → Modul + Fitur (+ modul bersama)

**Tanggal:** 2026-10-07 · **Status:** rencana induk 3 agen (belum ada kode) · rencana per agen: [parallel4/](parallel4/) · **Induk:** [PLAN-screen-proposal-contract-koog](PLAN-screen-proposal-contract-koog.md)
**Asal:** arahan produk 2026-10-07 — dari cerita pengguna, tentukan dulu divisi apa saja, ubah alurnya menjadi modul dan fitur, tanyakan peran mana menangani modul mana (untuk menghubungkan), perkirakan sendiri lalu minta konfirmasi (wawancara), dan pakai ulang modul yang sudah ada bila cocok (mis. keuangan, data port bisa berbeda).

---

## 0. Discovery Note (template `wemade-feature-discovery`)

### 1. Kebutuhan
- **Siapa memakai:** calon klien / pemilik usaha di Studio Discovery (`/discovery`); superadmin untuk meninjau.
- **Data milik:** draf discovery (`ops.discovery_drafts`, platform-global, per pemilik) — **bukan** data tenant produksi.
- **Berubah kapan:** sekali per draf, berulang selama sesi wawancara; hasil beku saat draf dikunci (Kontrak 5).

### 2. Fitur serupa
- `scripts/find-similar-feature.sh wawancara interview divisi department role` + graphify (`Department`, `DepartmentModuleAssignment`, `CustomRole`, `DiscoveryDraft`, `ScreenProposal`).
- **Sudah ada (dipakai ulang, bukan dibuat paralel):** `Department` + `DepartmentTier` (divisi per tenant), `DepartmentModuleAssignment` (divisi/jabatan ↔ modul, akses, scope), `CustomRole` (akses modul per jabatan), buku demand (`DiscoveryDemand`: modul terwakili vs istilah belum), agent Koog + validator + `ScreenProposal`.
- **Belum ada:** percakapan bertahap (wawancara); divisi/peran sebagai bagian **draf** discovery; asal modul (pakai ulang / kembangkan / baru); referensi modul platform dengan adaptor port.
- **Keputusan:** **Mirip → tiru pola** `DiscoveryDraft` + codec + validator (draf tetap satu dokumen berversi) dan `DepartmentModuleAssignment` (bentuk hasil wawancara).

### 3. Jenis
**Governance/Foundation untuk data hasilnya; fitur di dalam Studio Discovery untuk layarnya.** Bukan modul operasional baru: tidak dijual, tidak dihitung kuota, tidak di kanvas Factory Flow. Alasan: wawancara hanya menghasilkan keputusan (draf); modul operasional yang dihasilkan baru lahir saat draf dibangun.

### 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Template & titik beku |
|---|---|---|---|---|---|
| Daftar divisi hasil wawancara | ya | ya | ya | **Data** (di draf) | draf beku saat `LOCKED`; disalin ke `Department` saat dibangun |
| Peran/jabatan per divisi | ya | ya | ya | **Data** | idem, ke `CustomRole`/`DepartmentTier` |
| Pemetaan peran → modul | ya | ya | ya | **Data** (`RoleModuleLink`) | idem, ke `DepartmentModuleAssignment` |
| Asal modul (`REUSE_PLATFORM` / `REUSE_PACK` / `NEW`) | tidak | tidak | tidak | **Kode** (kosakata tertutup, milik sistem) | — |
| Tebakan peran → modul ("operator rajut → modul operator mesin") | ya | ya | — | **Data pack** (kamus peran per pack, seperti `defaultWidget`) | ikut pack |
| Daftar modul platform yang boleh dirujuk | tidak | tidak | tidak | **Kode** (registri platform) | — |
| Pemetaan port saat modul dipakai ulang | ya | ya | ya | **Data** (`portMapping` di referensi) | beku bersama draf |
| Jumlah/urutan pertanyaan | — | — | — | **Kode** (batas sistem, mis. ≤ 8 pertanyaan) | — |

### 5. Core & extend
- **Core (baru):** paket `core/.../domain/discovery/interview/` — `InterviewSession`, `DivisionDraft`, `RoleDraft`, `RoleModuleLink`, `ModuleOrigin`, `InterviewQuestion`, aturan "apa pertanyaan berikutnya" sebagai fungsi murni.
- **Titik extend:** `DiscoveryDraft` (kunci baru opsional `interview`, kompatibel mundur seperti `proposal`), `DiscoveryDraftValidator` (aturan wawancara berpath), `DiscoveryDraftCodec`, agent Koog (prompt + alat baru), `DeterministicDiscoveryAgent` (kamus peran → modul sebagai data pack).
- **Contoh yang ditiru:** `ScreenProposal` + `ScreenProposalValidator` (kontrak + validator tunggal + galat berpath), `PackScreenProposer`/`DeterministicScreenProposer` (acuan + deterministik), `DepartmentModuleAssignment` (bentuk hasil).
- **Jangan disentuh:** `DiscoveryRoutes.kt` (di atas batas lunak — tambah route di file baru), tabel utang file-size.

### 6. I/O & kanvas
- **Masuk:** narasi + jawaban pengguna per giliran. **Keluar:** `InterviewSession` terkonfirmasi → draf (pack + blueprint + layar) **dan** seed `Department`/`DepartmentModuleAssignment`/`CustomRole` saat dibangun.
- **Kanvas:** tidak ada (bukan node). Hasilnya mengisi kanvas lewat modul yang terbentuk.
- **Telemetri:** jumlah giliran, % tebakan yang dikonfirmasi tanpa ubah (ukuran mutu tebakan), waktu sampai draf kunci.

### 7. Governance
| Operasi | Level minimum | Peran yang ditolak (dites 403) |
|---|---|---|
| Mulai/jawab wawancara pada draf sendiri | pemilik draf | pengguna lain (403); tanpa login (401) |
| Lihat wawancara draf orang lain | superadmin platform | semua selain superadmin (403) |
| Kunci draf (membekukan hasil wawancara) | pemilik draf | pengguna lain (403) |
- **Gate:** sama dengan `DiscoveryRoutes` (`mayAccess`); **tulis fail-closed** (Kontrak 7). `ScopeCapability`: tidak berlaku (bukan modul). Entitlement: ikut funnel discovery (bukan paket tenant).

### 8. Ukuran → TRD?
Agregat baru + kunci dokumen + route + langkah wizard + prompt/alat + fase eval ⇒ **TRD perlu** (`trd-generator`) sebelum kode. Migrasi DB: **tidak perlu** bila wawancara disimpan di dokumen draf (JSONB); perlu bila sesi dipisah.

---

## 1. Alur wawancara (workflow)

Prinsip: **tebak dulu, tanya untuk konfirmasi** — jangan menanyakan yang bisa disimpulkan dari narasi. Satu giliran = satu kelompok keputusan (bukan satu pertanyaan per modul). Batas ≤ 8 giliran; selalu ada tombol "lewati / terima semua tebakan".

```
Narasi ─► G1 Divisi ─► G2 Peran per divisi ─► G3 Modul & fitur per peran ─► G4 Sambungan ─► G5 Ringkasan & kunci
```

| Giliran | Sistem menebak | Pengguna mengonfirmasi | Hasil (data) |
|---|---|---|---|
| **G1 Divisi** | divisi dari cerita ("potong, jahit, QC" → Potong, Jahit, QC; "pesanan masuk" → Penjualan) | benar / tambah / hapus / ganti nama | `DivisionDraft[]` |
| **G2 Peran** | jabatan per divisi dari kata kerja & pelaku ("operator rajut", "admin gudang"); kamus peran dari pack | benar / tambah / hapus; siapa kepala divisi | `RoleDraft[]` |
| **G3 Modul & fitur** | peran → modul: operator rajut → modul operator mesin rajut. Setiap tebakan membawa **asal**: **pakai ulang** modul platform/pack, **kembangkan** (pack ada, fitur kurang), atau **baru** | setuju / pindahkan ke modul lain / ubah asal; fitur apa saja di tiap modul | `RoleModuleLink[]` + `ModuleOrigin` + fitur |
| **G4 Sambungan** | serah-terima antar modul dari urutan cerita ("kain datang, lalu dipotong") | benar / ubah urutan; siapa menyerahkan ke siapa | sambungan port antar modul |
| **G5 Ringkasan** | ringkasan 1 halaman: divisi → peran → modul (asal) → fitur → sambungan | kunci / kembali ke giliran X | draf siap dikunci |

**Fallback tanpa LLM:** G1–G5 memakai kamus peran → modul sebagai **data pack** (pola `defaultWidget`); pack tanpa kamus ⇒ tanya terbuka, bukan tebak.

**Konfirmasi dicatat:** tiap tebakan menyimpan `confidence` dan `confirmed = true/false/diubah` supaya mutu tebakan bisa diukur dan dibawa ke buku demand (istilah yang selalu diubah = kandidat kamus baru).

## 2. Model data (usulan)

```kotlin
data class InterviewSession(val divisions: List<DivisionDraft>, val roles: List<RoleDraft>,
    val links: List<RoleModuleLink>, val handoffs: List<ModuleHandoff>, val step: InterviewStep)
data class RoleModuleLink(val roleKey: String, val moduleId: ModuleId, val origin: ModuleOrigin,
    val features: List<String>, val confirmed: Confirmation)
enum class ModuleOrigin { REUSE_PLATFORM, REUSE_PACK, EXTEND, NEW }      // kosakata tertutup milik sistem
data class ModuleReference(val platformModuleId: String, val portMapping: Map<PortType, PortType>)   // modul bersama
```

Bentuk hasil yang sudah ada dipakai ulang: divisi → `Department`; peran → `DepartmentTier`/`CustomRole`; peran ↔ modul → `DepartmentModuleAssignment` (kunci NAME enum, bukan code — lihat `module-integration-rules` §5.5).

## 3. Modul bersama (mis. keuangan) — arah diputuskan, rincian invarian terbuka

**Arahan 2026-10-07:** pack **tidak baku**; ia dirakit dari modul yang dihasilkan dari alur pengguna, dan modul yang bisa dipakai ulang (mis. keuangan, data port berbeda) dipakai ulang. Karena itu modul bersama naik dari "opsional" menjadi tahap yang pasti dikerjakan (I5); yang masih terbuka hanya rincian di bawah.

- **Sekarang:** modul pack baru wajib berawalan kode pack dan id platform dilarang dipakai ⇒ pack baru **tidak bisa** merujuk modul keuangan platform.
- **Usulan:** `ModuleReference` ke modul platform yang terdaftar (`OperationalModuleCatalog`) + **adaptor port** (kosakata port pack ↔ kosakata platform). Validator: rujukan hanya ke id yang terdaftar, `portMapping` lengkap dan tipenya kompatibel (kontrak input/output modul).
- **Dampak:** estimasi harga membedakan asal (pakai ulang < kembangkan < baru); prompt Koog diberi katalog modul platform + asalnya; eval menambah kriteria "asal modul masuk akal".
- **Risiko:** pack pewaris/komposisi pack mengubah invarian "pack bawaan identik" — butuh keputusan sebelum kode.

## 4. Tahap pengerjaan (usulan)

| Tahap | Isi | Gerbang |
|---|---|---|
| **I0** | TRD + kontrak `InterviewSession` + validator + codec (kunci opsional di draf), fixture non-garment | draf lama terbaca; tes per aturan; fixture 2 industri |
| **I1** | Pembuat tebakan deterministik (kamus peran → modul sebagai data pack) + aturan "pertanyaan berikutnya" (fungsi murni) | deterministik byte-per-byte; pack tanpa kamus ⇒ tanya terbuka |
| **I2** | Route wawancara (file baru, bukan `DiscoveryRoutes.kt`), gate + tes 403/401, fail-closed | tes peran tak berwenang |
| **I3** | Langkah wizard "Wawancara" (A) — konfirmasi per giliran, ringkasan, tombol terima semua | cek mata di tenant non-garment |
| **I4** | Koog: prompt + alat `interview_state`; LLM menebak, validator menegakkan; eval baru (≥ 10 kasus) + kriteria **berdasar-cerita** (lihat §4.1) | skor tebakan terkonfirmasi ≥ target yang disepakati; 0 modul tanpa dasar |
| **I5** | Modul bersama (`ModuleReference` + adaptor port) | keputusan §3 selesai dulu |

### 4.1 Aturan "berdasar cerita" (DIPUTUSKAN 2026-10-07, tagline "ERP untukmu")

ERP dibentuk dari bisnis pengguna, bukan dari daftar ERP standar. Konsekuensinya mengikat I1 dan I4:

1. **Modul/fitur hanya boleh masuk draf bila punya dasar**: kutipan dari narasi, atau jawaban pengguna di giliran wawancara. Tiap `RoleModuleLink` menyimpan `basis` (kutipan/id jawaban); tanpa `basis` → ditolak validator (galat berpath), bukan dilonggarkan.
2. **Daftar ERP umum** (pembelian, keuangan, upah, dst.) boleh dipakai agent hanya sebagai **bahan pertanyaan** ("apakah Anda juga mengurus ini?"), tidak pernah langsung menjadi isi draf.
3. **Eval:** tambah kriteria `berdasar_cerita` (setiap modul dapat ditelusuri ke cerita/jawaban) dan kasus negatif (cerita kecil → draf kecil; modul "lazim" yang tak disebut tidak boleh muncul).
4. **Ukuran mutu:** utamakan % tebakan diterima pemilik, bukan jumlah modul tercakup.

### 4.2 Persona pewawancara: konsultan bisnis (DIPUTUSKAN 2026-10-07)

Prompt sistem agent wawancara (`KoogDiscoveryPrompt.kt`, bagian baru khusus wawancara) menempatkan agent sebagai **konsultan digitalisasi usaha**, bukan formulir. Tujuannya membantu pemilik yang belum tahu apa yang ia butuhkan.

**Urutan percakapan** (menggantikan pembuka G1 yang langsung menebak divisi):

| Fase | Pertanyaan konsultan | Hasil |
|---|---|---|
| F0 Bisnis | "Usahanya apa? Produknya, pelanggannya, skalanya?" | profil bisnis (narasi) |
| F1 Tujuan | "Sistem seperti apa yang ingin dibuat? Apa yang paling merepotkan sekarang?" | tujuan + titik sakit |
| F2 Spesifikasi kebutuhan | per area yang muncul: "siapa yang mengisi, apa yang dicatat, siapa yang perlu melihat, kapan dianggap selesai?" | requirement spec per area (input untuk modul + fitur) |
| F3 Terjemahan | Koog menerjemahkan F0–F2 ke divisi → peran → modul + fitur (G1–G5 yang sudah direncanakan) | draf |

**Perilaku konsultan:**

1. **Menyarankan bila pengguna bingung** ("belum tahu", jawaban kosong, atau meminta saran): menawarkan 2–3 pilihan berdasarkan narasinya sendiri, dengan alasan singkat, mis. "Anda menyebut sering salah hitung upah. Mau kita catat hasil per operator per hari?".
2. **Mengajukan pertanyaan dari pengetahuannya** tentang modul/proses yang lazim untuk bisnis serupa, sebagai **pertanyaan**, bukan sebagai isi draf ("Biasanya usaha seperti ini juga mengurus pembelian bahan. Apakah itu juga Anda lakukan?").
3. **Satu kelompok pertanyaan per giliran**, bahasa awam, tanpa istilah teknis internal (archetype, port, pack).
4. **Tidak memaksa:** selalu ada "lewati" dan "terima semua tebakan".

**Mendamaikan dengan §4.1 (berdasar cerita).** Saran konsultan **boleh** menjadi isi draf, tetapi hanya setelah pengguna menerimanya. Jenis `basis` yang sah:

| `basis` | Arti | Boleh masuk draf? |
|---|---|---|
| `NARASI` | kutipan dari cerita pengguna | ya |
| `JAWABAN` | jawaban pengguna atas pertanyaan wawancara | ya |
| `SARAN_DITERIMA` | usulan konsultan yang dikonfirmasi pengguna (`Confirmation.CONFIRMED`/`CHANGED`) | ya |
| `SARAN_BELUM_DIJAWAB` | usulan konsultan yang belum dijawab / ditolak | **tidak** (validator menolak) |

Dengan begitu konsultan membantu tanpa menggiring: pengetahuan konsultan memperkaya *pertanyaan dan pilihan*, tetapi keputusan tetap milik pemilik usaha. Saran yang sering ditolak/diubah dicatat ke buku demand sebagai sinyal mutu saran.

**Dampak ke tahap:** I1 (aturan pertanyaan berikutnya mengenal F0–F2), I4 (prompt + alat; eval menambah skenario "pengguna bingung" → agent memberi saran berdasar narasi, bukan menebak liar), dan kontrak `RoleModuleLink.basis` bertambah jenis di atas. Koog memakai **spec F2 sebagai masukan terjemahan**, bukan hanya narasi awal.

## 5. Pertanyaan terbuka (asumsi bawaan di §12)

1. ~~Sablon/bordir: pack sendiri atau bagian garment?~~ **DIPUTUSKAN 2026-10-07:** sablon dan bordir **tidak punya pack baku** — pack bergantung modul yang dihasilkan dari alur pengguna, dan modul yang bisa dipakai ulang dipakai ulang. Akibatnya sudah diterapkan (commit `1b4e7b8`): kata sablon/bordir/kain/tekstil/potong bukan lagi kebocoran konveksi, agent deterministik tidak mengarahkan sablon/bordir ke pack garment, kasus eval `sablon-bordir` dinilai dari kemampuan.
2. ~~Wawancara wajib atau opsional?~~ **DIPUTUSKAN 2026-10-07:** opsional, dengan "lewati" dan "terima semua tebakan" (dicatat `SKIPPED`).
3. ~~Batas giliran~~ **DIPUTUSKAN 2026-10-07:** ≤ 8 giliran **pada fase terjemahan (G1–G5)**; fase F0–F2 (§4.2) memakai batas sendiri ≤ 6 giliran; tiap giliran boleh berisi beberapa keputusan sekelompok.
4. **Modul bersama (§3):** boleh mengubah invarian "pack bawaan identik" dan "id platform dilarang"?
5. ~~Hasil wawancara ke tenant~~ **DIPUTUSKAN 2026-10-07:** hanya **usulan yang ditinjau konsultan/superadmin**; tidak ada pembuatan otomatis `Department`/`DepartmentModuleAssignment` pada tahap ini.

---

## 6. Kontrak (diterbitkan B pada G0; A dan C membaca, tidak menyunting)

```kotlin
// core/.../domain/discovery/interview/  (baru; semua kunci tersimpan = value object string, parser tunggal, tolak bukan fallback)
data class InterviewSession(
    val step: InterviewStep,                       // G1_DIVISI … G5_RINGKASAN, DONE
    val divisions: List<DivisionDraft>,            // kode slug + nama + sumber (tebakan/jawaban)
    val roles: List<RoleDraft>,                    // roleKey + label + divisionCode
    val links: List<RoleModuleLink>,               // peran ↔ modul + fitur + asal + status konfirmasi
    val handoffs: List<ModuleHandoff>,             // modul A → modul B (tipe port)
    val answers: List<InterviewAnswer>             // jejak giliran (untuk ukur mutu tebakan & buku demand)
)
enum class ModuleOrigin { REUSE_PLATFORM, REUSE_PACK, EXTEND, NEW }   // kosakata tertutup milik sistem
enum class Confirmation { GUESSED, CONFIRMED, CHANGED, SKIPPED }       // SKIPPED = "terima semua tebakan" tercatat jujur
data class InterviewQuestion(val id: String, val step: InterviewStep, val prompt: String, val guesses: List<Guess>)   // pertanyaan = data, bukan teks tempel
fun InterviewSession.nextQuestion(draft: DiscoveryDraft): InterviewQuestion?   // murni; null = selesai
```

- **Draf:** `DiscoveryDraft.interview: InterviewSession?` (opsional; draf lama tetap terbaca byte-per-byte), kunci JSON `interview`.
- **Validator:** `InterviewValidator` (galat berpath `$.interview.links[2].moduleId`): divisi unik, setiap peran punya divisi yang ada, setiap tautan menunjuk modul di pack draf, `origin` konsisten (REUSE_PACK ⇒ modul ada di pack bawaan), batas ukuran (divisi ≤ 12, peran ≤ 40, tautan ≤ 60, giliran ≤ 8), kemurnian vertikal untuk pack non-garment.
- **Kamus tebakan = data pack:** `DomainPack.roleHints: List<RoleHint>` (kata peran → `moduleId`/slot), opsional dan kompatibel mundur; pack tanpa kamus ⇒ **tanya terbuka, bukan tebak** (aturan "tak ada tebakan" seperti `defaultWidget`).
- **Ringkasan ke klien:** `routes/DiscoverySummary.kt` mengirim `interview`, `nextQuestion`, dan `origin` per modul — **B memilikinya** (pelajaran: tanpa pemilik, proposal SP tidak sampai ke layar sampai cek visual).
- **Route:** `POST /api/discovery/drafts/{id}/interview` (kirim jawaban, terima ringkasan + pertanyaan berikutnya). **Fail-closed**; pemilik draf saja; tes 401/403.
- **Pembuat tebakan (port):** `InterviewGuesser { suspend fun guess(step, pack, draft, narrative): Result<List<Guess>> }` — `DeterministicInterviewGuesser` (B, kamus pack) dan `AgentInterviewGuesser` (C, Koog). Keduanya hanya **usulan**; validator menegakkan.

### 6.1 Tambahan kontrak (2026-10-07: berdasar cerita + persona konsultan; §4.1–4.2)

```kotlin
enum class Basis { NARASI, JAWABAN, SARAN_DITERIMA, SARAN_BELUM_DIJAWAB }   // kosakata tertutup; SARAN_BELUM_DIJAWAB ditolak validator
data class BasisRef(val basis: Basis, val quote: String?, val answerId: String?)   // kutipan narasi atau id jawaban
// RoleModuleLink, DivisionDraft, RoleDraft, ModuleHandoff masing-masing bertambah: val basisRef: BasisRef
data class BusinessProfile(val summary: String, val goals: List<String>, val painPoints: List<String>)           // F0-F1
data class RequirementSpec(val areaKey: String, val whoFills: String?, val whatRecorded: String?,
                           val whoSees: String?, val doneWhen: String?, val basisRef: BasisRef)               // F2, satu per area
// InterviewSession bertambah: val profile: BusinessProfile?, val specs: List<RequirementSpec>
// InterviewStep bertambah di depan: F0_BISNIS, F1_TUJUAN, F2_SPEK (batas <= 6 giliran); G1..G5 tetap
```

- Validator: tiap divisi/peran/tautan/sambungan **wajib** `basisRef`; `SARAN_BELUM_DIJAWAB` ditolak berpath; `NARASI` wajib `quote` yang benar-benar substring narasi; `JAWABAN` wajib `answerId` yang ada di `answers`.
- Draf lama (tanpa `profile`/`specs`/`basisRef`) tetap terbaca; wawancara lama dianggap `basis` tak berlaku (migrasi dibaca, bukan ditulis ulang).
- Spesifikasi F2 dikirim ke Koog sebagai masukan terjemahan (C); B memilikinya.

## 7. Kepemilikan File (satu pemilik per file)

| Agent | Jalur | Memiliki |
|---|---|---|
| **A** | Wawancara di UI | `app/shared/.../presentation/discovery/**` (langkah wizard "Wawancara", kartu konfirmasi, ringkasan), `DiscoveryUiModel.kt`, tes `app/shared/src/jvmTest/**`; **satu-satunya yang memegang browser (3001/8081)** |
| **B** | Kontrak, validator, tebakan deterministik, ringkasan & route | `core/.../domain/discovery/interview/**` (baru), `DiscoveryDraft.kt`, `DiscoveryDraftValidator.kt`, `shared/discovery/DiscoveryDraftCodec.kt`, `domain/pack/{DomainPack,GarmentRoleHints}.kt` (kunci `roleHints`), `server/.../routes/DiscoverySummary.kt`, **file baru** `routes/DiscoveryInterviewRoutes.kt` + tesnya, tes core |
| **C** | Agent Koog & evaluasi | `server/.../infrastructure/discovery/**` (prompt, alat `interview_state`, `AgentInterviewGuesser`), `server/src/test/**/DiscoveryEval*`, `KoogDiscoveryLive*`, berkas eval baru, dokumen eval |
| Koordinator | — | `.claude/**`, `AGENTS.md`, `docs/plannings/PLAN-*.md`, merge ke `main`, keputusan G3 |

**Hotspot:** `DiscoveryRoutes.kt` — **jangan ditambah** (sudah di atas batas lunak; route baru di file baru). `DiscoveryDraftCodec.kt`/`DiscoveryDraftValidator.kt` = B (A dan C meminta lewat kontrak).

## 8. Gelombang & Gerbang

```
G0  Kontrak (B, ±1,5 hari)  ── InterviewSession + validator + codec draf + kamus peran garment (acuan), dimerge ke main
                               A: spike layar konfirmasi dari fixture; C: rancang kasus eval wawancara (offline)
G1  Paralel (A ∥ B ∥ C)     ── B: tebakan deterministik + route + ringkasan; A: UI G1–G5; C: prompt/alat/eval
G2  Integrasi               ── merge B → C → A; eval deterministik 100%; cek mata A (tenant non-garment)
G3  Eval live (opt-in)      ── batas biaya dicetak dulu; keputusan koordinator apakah Koog dipakai untuk tebakan
G4  Modul bersama (I5)      ── hanya setelah keputusan §5.4 dan bila G2 hijau
```
| Gerbang | Syarat |
|---|---|
| **G0** | kontrak §6 sebagai kode+tes; **pack garment diekspresikan sebagai wawancara acuan** lolos validator; draf lama terbaca; A dan C menandatangani |
| **G1** | DoD jalur hijau di worktree sendiri |
| **G2** | `:core:jvmTest`, `:server:test` (scratch DB), kompilasi 3 target klien; **layar wawancara tampil dan terisi dari ringkasan server untuk pack non-garment (bukan hanya lulus tes)** |
| **G3** | skor tebakan terkonfirmasi per langkah (G1–G5) dilaporkan; keputusan eksplisit koordinator |

### 8.1 Jadwal hari kerja (perkiraan 2026-10-07; AI ikut demo)

| Hari | B (kontrak/server) | C (Koog) | A (UI) |
|---|---|---|---|
| 1-2 | **G0:** kontrak §6 + §6.1, validator, codec, kamus peran garment; merge | kasus eval wawancara (offline): bisnis kecil, bingung, negatif | spike layar konfirmasi dari fixture |
| 3-5 | tebakan deterministik, route `interview` (401/403), ringkasan | prompt persona F0-F2, alat `interview_state`, `AgentInterviewGuesser`, eval sintetis | layar percakapan F0-F2 + UI G1-G5 |
| 6-7 | tutup celah integrasi | spek F2 -> terjemahan, kriteria `berdasar_cerita`, skenario bingung | ringkasan + "terima semua tebakan" + tampil `basis` |
| 8 | **G2** merge B -> C -> A, tes penuh, cek mata tenant non-garment | | |
| 9-11 | perbaikan temuan | **G3** eval live + putaran akurasi | perbaikan dari cek mata |
| 12-14 | cadangan | akurasi lanjutan, laporan eval | latihan demo dengan cerita rajut |

Target latensi eval: tiap giliran Koog di bawah ~15-20 detik (baseline terburuk 108 detik); I5 (modul bersama) di luar demo pertama. Perkiraan, bukan jaminan.

## 9. Aturan Kerja Bersama (semua agent)

**Cabang & isolasi (WAJIB):** `git worktree add ../wemkaeerp-wt-<huruf> -b feat/iv-<huruf>-<slug> main`. **Sebelum merge:** `git branch --show-current` di folder utama — folder utama pernah berpindah ke cabang agent lain sehingga merge mendarat di tempat salah; bila bukan `main`, merge dari worktree `main` sendiri. Jangan commit ke `main` langsung; PR kecil; rebase sebelum PR; konflik = berhenti dan lapor. Commit Indonesia ringkas + `Co-Authored-By`.
**Standar repo:** Graphify dulu; DDD, tanpa `!!`; variabilitas (beda per industri → **data**; tes pack non-garment wajib; `scripts/audit-variability.sh` 0 temuan baru); ukuran file core 250/400, presentation 400/600, server 300/500, test 500/800; design system (nol literal warna, komponen `designsystem/` buta domain); teaching doc per jalur `docs/teaching/teaching-iv-<huruf>-<slug>.md`.
**Verifikasi:** `./gradlew :core:jvmTest` · kompilasi `:app:shared` JVM/WasmJS/JS + `:server:compileKotlin` · `:app:shared:jvmTest` · server: `DB_NAME=<db-scratch> ./gradlew :server:test --tests '<yang disentuh>'`.
**DB:** hanya database ber-nama `scratch` (mis. `wemake_erp_scratch_<huruf>`; buat sendiri dengan `createdb`); jangan `wemake_erp`; kredensial lewat env sistem, `.env` tidak disalin ke worktree. Test `TechPackApiTest`/`CostingEstimatorTuningApiTest`/`MasterDataApiTest`/`AccessSnapshotB6Test` sudah gagal di baseline — bukan regresi (lihat memory `run-app-local-scratch-db`).
**LLM:** tes otomatis **tidak** memanggil LLM; kunci API tidak pernah masuk repo/log; eval live hanya skrip opt-in dengan estimasi biaya dicetak **dan diverifikasi** (estimasi test sebelumnya meleset ±4× — gandakan margin; cek saldo sebelum/sesudah).
**Cek mata hanya A.** **Jebakan yang sudah terjadi:** (1) font Nunito tak punya glyph non-ASCII (→, ▯) — teks yang tampil wajib Latin-1, ada test regresi; (2) **alat/jembatan harus konsisten dengan jawaban akhir** (validate_draft pernah menolak jembatan `useShipped`); (3) data tersimpan benar ≠ klien menerimanya — periksa ringkasan server (`DiscoverySummary`) dan layar nyata; (4) lencana/teks sempit patah per huruf — `weight(1f, fill=false)` + `maxLines`; (5) penilai eval terlalu kaku bukan kesalahan model — kalibrasi terbuka, catat di laporan; (6) cache inkremental Kotlin rusak → hapus `build/` worktree sendiri; (7) input otomatis ke canvas Compose tidak andal — verifikasi data tersimpan di DB.

## 10. Risiko
| Risiko | Mitigasi |
|---|---|
| Wawancara terlalu panjang → prospek keluar | ≤ 8 giliran, "terima semua tebakan", tebak dulu baru tanya |
| Tebakan peran → modul salah | kamus = data pack; `Confirmation` dicatat; ukur % tebakan diubah; pack tanpa kamus tanya terbuka |
| Kontrak meleset → kerja ulang | G0 pendek; fixture dua industri dulu; perubahan kontrak = versi baru |
| LLM mengarang modul/divisi | validator + katalog tertutup; asal modul diverifikasi terhadap registri |
| Modul bersama merusak invarian pack bawaan | I5 ditunda sampai keputusan §5.4; tidak dikerjakan di G0–G3 |
| Bocor kosakata garment | validator kemurnian + kriteria eval |
| Hasil tak sampai ke layar | B memiliki `DiscoverySummary.kt`; G2 mensyaratkan cek mata |

## 11. Skenario Penerimaan (G2/G3)
1. **Deterministik:** narasi klinik ⇒ G1–G5 dengan divisi (Pendaftaran, Poli, Kasir), peran (perawat, kasir), modul berasal dari kamus, **tanpa LLM**; "terima semua" menghasilkan draf valid.
2. **Konfirmasi mengubah hasil:** pengguna memindahkan peran ke modul lain ⇒ `Confirmation.CHANGED` tercatat dan draf mengikuti.
3. **Tanpa kamus:** pack tanpa `roleHints` ⇒ pertanyaan terbuka, tidak ada tebakan karangan.
4. **Koog (G3, opt-in):** narasi baru ⇒ tebakan lolos validator; skor terkonfirmasi per langkah; perbandingan dengan baseline deterministik.
5. **Gagal aman:** LLM mengeluarkan modul/divisi tak sah ⇒ galat berpath, dikoreksi atau jatuh ke deterministik; tak ada layar rusak.
6. **Draf lama:** draf tanpa `interview` tetap tampil dan tidak berubah perilaku.
7. **Hak akses:** pengguna lain menjawab wawancara draf orang ⇒ 403; tanpa login ⇒ 401.

## 12. Asumsi bawaan sampai Anda memutuskan (ubah di sini bila berbeda)
Wawancara **opsional**; ≤ 8 giliran; hasil ke tenant = **usulan yang ditinjau** (tidak otomatis membuat `Department`); modul bersama (I5) **dikerjakan setelah G2** tetapi arahnya sudah diputuskan (pakai ulang diinginkan; tinggal rincian invarian §3); sablon/bordir **diputuskan 2026-10-07** (tidak punya pack baku, lihat §5.1). Daftar kemurnian vertikal sebaiknya jadi **data pack** (kosakata cadangan per pack) alih-alih daftar kode.
