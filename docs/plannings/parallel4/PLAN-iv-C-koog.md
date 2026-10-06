# PLAN — Agent C: Agent Koog Menebak Wawancara & Evaluasi

**Agent:** C · **Tanggal:** 2026-10-07 · **Induk:** [PLAN-discovery-interview-role-module](../PLAN-discovery-interview-role-module.md)

> Berdiri sendiri untuk satu agent. Bila selisih dengan plan induk, **plan induk (kontrak §6) yang berlaku**.

---

## Misi & Lingkupmu — Agent C

**Misi:** membuat agent Koog **menebak** tiap giliran wawancara (divisi, peran, peran → modul + asal, sambungan) di bawah kontrak yang sama dengan pembuat deterministik, lalu **mengukurnya** lewat eval berstruktur dengan batas biaya yang benar.

**Kamu memiliki:** `server/.../infrastructure/discovery/**` (prompt, alat `interview_state`, `AgentInterviewGuesser`), `server/src/test/**/DiscoveryEval*` + berkas eval baru, skrip eval live, dokumen eval.
**Dilarang:** `app/**`, `core/**` (kontrak = B; minta perubahan lewat laporan), `DiscoveryRoutes.kt`.

### Butir kerja
**C0 — Kasus eval & penilai wawancara (G0, offline, ±1 hari).** Rancang ≥ 10 kasus emas **dengan kunci jawaban per langkah** (divisi/peran/tautan yang diharapkan) dan penilai berstruktur (kriteria: valid, divisi masuk akal, peran→divisi benar, tautan modul benar, **asal modul masuk akal**, kemurnian vertikal, jumlah giliran). **Penilai dites sendiri** (kasus lulus/gagal buatan tangan) dan **baseline deterministik wajib 100%**; kalibrasi penilai dicatat terbuka. **AC:** penilai lulus tesnya sendiri; tidak ada panggilan LLM.

**C1 — Prompt wawancara.** Contoh dokumen **dirakit dari kode** (`DiscoveryDraftCodec`) dan dites lolos validator; aturan: tebak dulu baru tanya, jangan mengarang modul/divisi di luar katalog, `origin` jujur. **AC:** contoh lolos validator penuh.

**C2 — Alat.** `interview_state` (keadaan sesi saat ini) dan katalog modul platform + asalnya; **alat dan jawaban akhir memakai jembatan/dekoder yang sama** (pelajaran: `validate_draft` pernah menolak `useShipped`). **AC:** tes alat dan dekoder sepakat pada dokumen berjembatan.

**C3 — `AgentInterviewGuesser`.** Keluaran hanya **usulan**; galat berpath dikembalikan pada putaran koreksi berbatas; provenance dibubuhkan server. **AC:** tes dengan `ScriptedPromptExecutor` (tanpa jaringan): sampah → galat berpath; keluaran sah → lolos.

**C4 — Eval live (G3, opt-in).** Skrip dengan **estimasi biaya dicetak dan diverifikasi** (estimasi lama meleset ±4× — gandakan margin), konfirmasi eksplisit, ulangan 1 dulu baru 3; periksa saldo sebelum/sesudah; laporan `docs/plannings/eval-iv-<tanggal>.md` (jangan menimpa laporan lama — tanggal dalam UTC). Kegagalan penilai vs model dipisah di laporan.

**C5 — Perbaikan dari eval.** Prompt/alat/penilai diperbaiki berdasar pola gagal; kalibrasi penilai ditandai terbuka.

### Urutan & ketergantungan
`C0 (offline, G0) → [setelah B0] C1 → C2 → C3 → [G2] C4 → C5`. C tidak menunggu A.

### Definition of Done — C
- [ ] AC C0–C5; **tidak ada panggilan LLM di tes otomatis**; kunci API tak pernah di repo/log
- [ ] Baseline deterministik 100%; penilai dites sendiri
- [ ] Kompilasi server hijau; tes server di DB scratch
- [ ] Teaching doc `docs/teaching/teaching-iv-c-<slug>.md`

---

## Rujukan bersama (salinan dari plan induk)

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

## 3. Modul bersama (mis. keuangan) — keputusan desain terbuka

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
| **I4** | Koog: prompt + alat `interview_state`; LLM menebak, validator menegakkan; eval baru (≥ 10 kasus) | skor tebakan terkonfirmasi ≥ target yang disepakati |
| **I5** | Modul bersama (`ModuleReference` + adaptor port) | keputusan §3 selesai dulu |

## 5. Pertanyaan terbuka (asumsi bawaan di §12)

1. **Sablon/bordir:** pack sendiri atau bagian garment? (menentukan daftar kemurnian vertikal dan kasus eval — lihat laporan eval ronde 2.)
2. **Wawancara wajib atau opsional?** Usul: opsional, dengan "terima semua tebakan".
3. **Batas giliran** (usul ≤ 8) dan apakah tiap giliran boleh berisi beberapa keputusan sekaligus.
4. **Modul bersama (§3):** boleh mengubah invarian "pack bawaan identik" dan "id platform dilarang"?
5. **Hasil wawancara ke tenant:** otomatis membuat `Department`/`DepartmentModuleAssignment` saat draf dibangun, atau hanya usulan yang ditinjau superadmin?

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
Wawancara **opsional**; ≤ 8 giliran; hasil ke tenant = **usulan yang ditinjau** (tidak otomatis membuat `Department`); modul bersama (I5) **ditunda**; sablon/bordir **belum diputuskan** (tidak memblokir G0–G2; menentukan daftar kemurnian vertikal dan kasus eval).

## Format laporan ke koordinator (setiap PR / akhir gelombang)
1. Butir selesai + cabang/PR. 2. Hasil perintah verifikasi (sertakan kegagalan apa adanya). 3. `wc -l` sebelum → sesudah untuk file di atas batas lunak. 4. Yang belum diverifikasi + temuan/keputusan terbuka. 5. Perubahan kontrak yang kamu butuhkan — jangan menyunting berkas milik agent lain.
