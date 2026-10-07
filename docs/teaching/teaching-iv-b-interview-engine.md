# 🎓 Modul Pembelajaran: Mesin Wawancara — Acuan, Kamus, Tebakan, Route, Validator, Berdasar Cerita, Modul Bersama (B1–B7)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Strangler Fig & test paritas, kamus sebagai data pack, fungsi murni deterministik, route fail-closed, validator berpath, migrasi dokumen tanpa menulis ulang
> **Prasyarat**: baca dulu [`teaching-iv-b-contract.md`](teaching-iv-b-contract.md) (B0: kontrak `InterviewSession`)
> **Referensi Task**: `docs/plannings/parallel4/PLAN-iv-B-contract.md` butir B1–B7; plan induk §4.1, §4.2, §6.1; [`PROPOSAL-iv-B6-shared-module.md`](../plannings/parallel4/PROPOSAL-iv-B6-shared-module.md)

---

## 💡 1. Konsep & Masalah
Pemilik usaha bercerita; sistem menebak divisi → peran → modul → sambungan, lalu pemilik hanya **mengonfirmasi**. Tanpa disiplin, ini jadi tiga bencana: tebakan konveksi bocor ke klinik, LLM mengarang modul, dan "ERP untukmu" berubah jadi daftar ERP standar.

**Analogi:** B0 adalah denah. B1–B7 adalah *pengawas bangunan*: ada gambar acuan (B1), buku istilah tukang (B2), mandor yang menebak dari denah (B3), pintu masuk berpetugas (B4), pemeriksa mutu (B5), dan syarat "setiap ruangan harus punya alasan dari cerita penghuninya" (B7).

## 🧭 2. Start dari Mana?
Urutan ini sengaja; tiap langkah bisa diuji sendiri sebelum langkah berikutnya ada.
1. **B1 — acuan:** nyatakan pack garment (yang sudah jadi) sebagai satu wawancara. Kalau kontrak tak sanggup mengekspresikannya, kontraknya yang salah.
2. **B2 — kamus peran → modul** di `DomainPack.roleHints`, sebelum penebak.
3. **B3 — penebak murni + `nextQuestion`**: input narasi + pack, output usulan.
4. **B4 — use case, route, ringkasan**: hasil harus *sampai ke layar*.
5. **B5 — perluas validator**: kemurnian vertikal jadi data; aturan lintas-bagian.
6. **B7 — berdasar cerita + fase konsultan**: ketat, tapi dokumen lama tetap terbaca.
7. **B6 — modul bersama**: *karakterisasi dulu* perilaku sekarang, tulis usulan, minta keputusan, baru implementasi.

## 🧱 3. Bedah Kode
**B1 — paritas Strangler Fig.** `GarmentReferenceInterview` + tes yang *mengiterasi* `pack.modules`:
```kotlin
val missing = pack.modules.map { it.id }.filter { it !in linked }
assertTrue(missing.isEmpty(), "Modul garment tanpa padanan: ${missing.map { it.value }}")
```
Modul garment baru tanpa padanan → tes gagal. Bonus: B5 kemudian menangkap kesalahan acuan sendiri (sambungan ke `invoicing` yang tak punya slot) — acuan bukan kitab suci, ia ikut diperiksa aturan.

**B2 — kamus = data pack.** `RoleHint(word, label, moduleId)`; `guessRoleHint` mencocokkan per kata utuh, frasa terpanjang menang. Pack tanpa kamus → `null`, **tidak** meminjam kamus garment. Kunci `roleHints` ditulis hanya bila ada → pack lama ter-encode identik.

**B3 — fungsi murni.** `propose(pack, narasi)`: kamus mengenali *peran*; peran menunjuk *modul*; nama modul = divisi; sambungan mengikuti urutan peran disebut. Tak ada acak/waktu → deterministik byte-per-byte (ada tesnya). `nextQuestion` melewati langkah yang tak perlu ditanya.

**B4 — route fail-closed.** `POST /api/discovery/drafts/{id}/interview`. Urutan penjagaan: 401 → 404 → **403 bila bukan pemilik (superadmin pun tidak)** → baru parse body. Penyimpanan lewat `UpdateDiscoveryDraftUseCase`, jadi LOCKED ditolak dan validator selalu jalan. Langkah & jejak giliran dihitung **server**, bukan klien:
```kotlin
val base = (revised ?: this).copy(version = version, narrative = narrative)
```
Ringkasan menambah kunci hanya bila draf punya sesi → draf lama tak berubah (dikunci tes yang membandingkan teks ringkasan sebelum/sesudah).

**B5 — kemurnian sebagai data.** Dulu `VerticalPurity` = daftar kode. Kini `DomainPack.reservedTerms`; validator menolak istilah yang dicadangkan pack **lain** di registri. `VerticalPurity.leak(text)` lama dipertahankan dan dikunci paritas (Strangler Fig lagi).

**B7 — berdasar cerita.** `BasisRef` di tiap butir; `NARASI` wajib `quote` yang *substring* narasi; `SARAN_BELUM_DIJAWAB` ditolak. Dilema: spec bilang "wajib", tapi dokumen lama tak punya. Jawabannya `version`: sesi lama `1` (tak wajib, dibaca apa adanya), sesi baru `2` (wajib). Cerita disalin ke sesi supaya validator draf bisa memeriksa kutipan tanpa buku demand.

**B6 — modul bersama (`ModuleReference`).** Masalahnya bukan "id dilarang", tapi *apa yang ikut terseret*: pack katering yang menyalin modul operasional platform terpaksa membawa slot, fase, dan port garment. Urutan kerja yang dipakai:
1. **Karakterisasi** (`SharedModuleInvariantsCharacterizationTest`): tes hijau yang mengunci perilaku sekarang, termasuk pencemaran itu. Usulan jadi bertumpu pada fakta, bukan kesan.
2. **Usulan tertulis** + keputusan pemilik produk (opsi a: hanya modul berslot).
3. **Implementasi**: `DomainPack.moduleReferences` (`platformModuleId`, `label`, `portMapping`), aturan murni `ModuleReferenceRules` (R1–R5, galat berpath), port modul dibaca dalam **kosakata pack** lewat pemetaan terbalik (`slotPorts`).
Dua koreksi atas usulan sendiri muncul saat menulis kode: `sharedModules` *opt-in* per modul (usulan awal "semua modul operasional" terlalu longgar), dan `label` per rujukan (nama platform "Costing HPP" mengandung istilah cadangan garment sehingga divisi tebakan ditolak validator kemurnian). Data rujukan **tersimpan** di dokumen pack, tetapi sengaja belum ke RBAC/kanvas sampai disetujui.

**Regresi yang kita buat sendiri.** Menambah kolom aditif ke pack bawaan (`roleHints`, `reservedTerms`) membuat draf garment yang tersimpan *sebelumnya* ditolak "dokumen wajib identik" saat direvisi. Ditemukan dengan tes sekali pakai sebelum menulis B6, lalu diperbaiki di `ShippedPackIdentity`: kolom aditif boleh sama dengan pack bawaan **atau kosong**, nilai lain tetap ditolak.

## ⚖️ 4. Keputusan & Alasan
| Pilihan | Alternatif | Alasan |
|---|---|---|
| `version` di sesi | `basisRef` wajib langsung | Dokumen tersimpan tak boleh dirusak/ditulis ulang |
| Kutipan = substring *asli* (regex IGNORE_CASE pada teks asli) | `lowercase()` lalu potong | `lowercase()` bisa mengubah panjang → indeks meleset |
| `answerId` = `questionId` | Tambah kolom id | Tak menambah bentuk dokumen |
| Hanya pemilik menjawab | Pemilik atau superadmin | Superadmin tak boleh menjawab atas nama prospek |
| Fase F "tidak ada perubahan" di Koog | Prompt asal | Persona konsultan = C6; jangan menebak liar |
| `sharedModules` opt-in per modul | Semua modul operasional bisa dirujuk | Hanya yang disetujui (`costing_hpp`) yang jadi modul bersama |
| `label` pack pada rujukan | Pakai nama platform | Nama platform bisa memuat istilah vertikal lain |
| Kolom aditif: sama ATAU kosong | Wajib identik penuh | Draf lama yang tersimpan tidak boleh ikut rusak |

## ⚠️ 5. Jebakan (yang benar-benar terjadi)
1. **Merge ke cabang salah.** Folder utama sedang di cabang agent lain; merge saya mendarat di sana. Periksa `git branch --show-current` di langkah *terpisah* sebelum merge, dan merge dari worktree yang pasti benar.
2. **Kompilasi hijau yang menipu.** Menambah nilai enum membuat `when` eksaustif di kode orang lain gagal; cek ulang di `main` *setelah* merge, bukan hanya di cabang.
3. **Fixture hanya garment** tak membuktikan apa-apa; semua fitur diuji dengan klinik/bengkel/katering/retail/gudang.
4. **Narasi uji tak memuat kata kunci agent** → pack terlalu kecil → asersi gagal. Gagal karena data uji, bukan kode.
5. **Fallback senyap** (kode enum asing → default) mengubah data diam-diam; semua parser menolak berpath.
6. **Menambah kolom ke data yang sudah tersimpan.** Setiap kolom baru di dokumen bawaan yang divalidasi dengan *kesamaan* merusak dokumen lama diam-diam. Uji dengan dokumen lama (buang kolom barunya) setiap kali menambah kolom.
7. **Keputusan produk tersirat di spec.** "Modul keuangan" di plan ternyata tidak ada sebagai modul berslot (`invoicing` = fondasi tanpa port). Temukan lewat membaca kode sebelum menulis tes, lalu tanyakan.

## 🧪 6. Pembuktian
`GarmentReferenceInterviewParityTest`, `RoleHintTest`, `DeterministicInterviewGuesserTest`, `InterviewTurnTest`, `InterviewValidatorB5Test`, `InterviewBasisTest`, `ModuleReferenceTest`, `ShippedPackIdentityTest`, `SharedModuleInvariantsCharacterizationTest` (core); `DiscoveryInterviewApiTest` (server, DB scratch: 401/403/404/409/400, draf lama tak berubah, mode konsultan).
Jalankan: `./gradlew :core:jvmTest` dan `DB_NAME=<scratch> ./gradlew :server:test --tests '*DiscoveryInterviewApiTest*'`.

## 🏆 7. Tantangan
- [ ] Isi `roleHints` dan `reservedTerms` untuk satu pack baru (mis. katering), lalu tulis narasi yang membuktikan tidak ada kebocoran ke klinik.
- [ ] Tambah aturan: `SARAN_DITERIMA` hanya sah bila giliran `answerId`-nya berstatus `CONFIRMED`/`CHANGED`. Apa yang berubah di `InterviewAnswer`?
- [ ] Jadikan `invoicing` modul operasional berslot dan tawarkan sebagai modul bersama kedua. Invarian paritas garment apa yang harus tetap hijau, dan apa saja yang harus ikut berubah (§5.1 rules)?
- [ ] Tambah kolom aditif baru ke `DomainPack`, lalu tulis tes yang membuktikan draf garment lama (tanpa kolom itu) tetap sah.
