# PLAN — Portal Developer (Opsi C): dari prototype ke software house

**Tanggal**: 2026-10-08 · **Jalur**: B · **Status**: rencana untuk dipikirkan (belum disetujui, belum dikerjakan)
**Konteks**: tujuan akhir pemilik produk adalah **membangun software house** yang menangani pembuatan modul hasil prototype.
Prototype di Builder hanya mempercepat; manusia (developer) yang memastikan semuanya jalan dan sesuai. Rencana ini menjawab: *bagaimana
developer — termasuk yang bukan karyawan — menerima, mengerjakan, dan menyerahkan pekerjaan lewat platform.*
**Dasar**: opsi B yang sudah jadi (brief beku pada Antrian Pembuatan), [`PLAN-builder-interview-chat.md`](PLAN-builder-interview-chat.md),
`discovery-M2-builder-deploy.md`.

---

## 1. Titik berangkat (dibaca dari kode, 2026-10-08)

| Yang sudah ada | Keterangan | Batasnya untuk software house |
|---|---|---|
| **Antrian Pembuatan** | Daftar `BuildRequest` lintas tenant; status `QUEUED, QUOTED, APPROVED, IN_PROGRESS, SHIPPED, REJECTED`; hanya `PLATFORM_SUPERADMIN` | Tidak ada **penugasan** (siapa yang mengerjakan), tidak ada peran selain superadmin, UI hanya daftar |
| **Brief beku** (opsi B) | Markdown + JSON yang dibekukan saat permintaan lahir; tombol "Lihat brief" | Satu snapshot; belum ada versi baru bila lingkup berubah, belum ada selisih |
| **Ledger pengerjaan modul** | `module_build_records` + entri effort per peran/fase + `discoveredScopeDelta`; API `/api/admin/module-dev/...` | Hanya superadmin; belum terhubung ke penugasan dan pembayaran developer |
| **Penawaran harga** | `ModulePricingQuote`, estimator, katalog modul | Penawaran ke klien ada; **tarif dan pembayaran ke developer** tidak ada |
| **Penagihan klien** | Tagihan platform + iPaymu | Sisi klien saja |
| **Deploy + pack terkunci** | M2: versi pack, rollback | Serah-terima hasil developer ke deploy belum ada |
| **RBAC tenant** | Peran per tenant + izin per modul | **Tidak ada peran platform** selain superadmin |

**Temuan penting saat opsi B dikerjakan:** deploy pack kustom ternyata *selalu gagal* di `main` (versi pack tidak diisi pada deployment
`BLOCKED_ON_BUILD`), sehingga antrian ini tidak pernah terisi di praktik. Sudah diperbaiki dan dites (commit `e1749d7`), tetapi mengingatkan
bahwa **alur ini belum pernah dipakai nyata** — Fase C0 menyertakan uji coba ujung ke ujung dengan satu klien sungguhan sebelum membangun portal.

---

## 2. Prinsip yang tidak boleh dilanggar

1. **Brief beku itu kontrak.** Perubahan lingkup = brief **versi baru** (snapshot baru + selisih), bukan menimpa. Developer selalu tahu persis apa yang disepakati.
2. **Akses per penugasan, fail-closed.** Developer hanya melihat permintaan yang ditugaskan kepadanya; peran tak dikenal = ditolak. Test wajib mencakup developer yang **tidak** ditugaskan (harus 403/404).
3. **Minimisasi data klien.** Developer luar tidak boleh melihat data operasional tenant — hanya brief dan sandbox bersintetis. Brief sendiri memuat cerita klien; perlu keputusan redaksi (bagian 6).
4. **Setiap akses dan perubahan tercatat** (linimasa audit per permintaan).
5. **Kode vs data**: status, peran, dan tahap kerja developer adalah *data per software house* jika kelak lebih dari satu; enum hanya untuk mekanik platform.
6. **Manusia di gerbang kualitas.** Tidak ada "SHIPPED" otomatis: review dan penerimaan klien adalah langkah eksplisit.

---

## 3. Fase

### C0 — Keputusan bisnis dan uji coba tanpa kode baru *(prasyarat, murah)*
- Putuskan model kerja (bagian 6): internal, freelancer, atau mitra; tarif; IP; NDA; siapa yang boleh melihat apa.
- **Uji coba manual** dengan opsi B yang ada: satu klien nyata → prototype → deploy → antrian → superadmin membaca brief dan mengerjakan. Catat apa yang kurang dari brief (hasilnya mengisi C2 dan C4).
- Tulis SOP singkat: definisi "selesai", cara menanyakan hal yang kurang jelas, cara menangani perubahan lingkup.
- **Keluaran**: daftar celah brief berdasar pengalaman nyata, bukan tebakan.

### C1 — Identitas dan akses developer
- Peran platform baru, mis. `DEVELOPER` (dan `TECH_LEAD` bila perlu review). Pisah tegas dari peran tenant. Undangan lewat email; sesi terpisah dari tenant.
- Tabel penugasan `build_request_assignments (request_id, developer_id, assigned_by, assigned_at, status)`. **Gerbang**: developer membaca permintaan hanya bila punya penugasan aktif.
- Rute baca baru khusus developer (`/api/dev/...`), terpisah dari `/api/builder/...` agar aturan akses tidak bercampur.
- Audit baca brief (siapa, kapan).
- **Tes**: developer tak ditugaskan → 403/404; developer dicabut → akses langsung hilang; tenant admin tidak bisa memakai rute developer; **tenant kedua** (tidak bocor lintas tenant).

### C2 — Penugasan dan siklus kerja
- Status diperluas sebagai data: `QUEUED → ASSIGNED → IN_PROGRESS → IN_REVIEW → CHANGES_REQUESTED → ACCEPTED → SHIPPED` (+ `BLOCKED_ON_CLIENT`, `REJECTED`). Transisi yang sah ditegakkan domain (pola `TenantStageFlow`).
- Linimasa peristiwa per permintaan (`build_request_events`, append-only): penugasan, perubahan status, komentar, brief versi baru.
- Estimasi vs aktual: hubungkan ke ledger `module_build_records` (jam per peran/fase, `discoveredScopeDelta`) sehingga selisih estimasi terlihat per developer.
- SLA/jatuh tempo per permintaan; peringatan bila terlambat.

### C3 — Portal developer (UI)
- Layar "Tugas saya": daftar permintaan ditugaskan, status, jatuh tempo.
- Layar permintaan: brief (Markdown + bagian **Belum jelas** disorot), riwayat versi brief + selisih, linimasa, komentar, tautan artefak (repo/PR/build).
- Pencatatan jam langsung ke ledger.
- Tampilan sempit/mobile (developer sering membaca di ponsel).

### C4 — Loop umpan balik developer ↔ klien
- Developer mengajukan **pertanyaan** pada permintaan → muncul sebagai follow-up di **utas modul** pada chat Builder klien (mekanisme `QUESTION` yang sudah ada), jawabannya kembali ke developer.
- Perubahan lingkup dari klien (chat berlanjut setelah deploy) memicu **brief versi baru** dan menandai permintaan `CHANGES_REQUESTED`/perlu persetujuan ulang.
- Perantara (PM) opsional: pertanyaan lewat PM dahulu bila klien tidak boleh dihubungi langsung oleh developer luar.

### C5 — Keuangan software house
- Penawaran ke klien (sudah ada) ←→ **biaya developer**: tarif per peran/jam atau harga tetap per tugas, tersimpan sebagai data.
- Persetujuan klien atas penawaran sebelum `IN_PROGRESS` (status `QUOTED → APPROVED` sudah ada di enum).
- Catatan utang ke developer (payable) + riwayat pembayaran; laporan margin per permintaan/klien/developer dari ledger (jam aktual × tarif vs harga jual).
- Penagihan klien memakai alur tagihan yang sudah ada.

### C6 — Kualitas, lingkungan, dan serah-terima
- **Sandbox** per permintaan dengan data sintetis (tanpa data klien); akses repo/lingkungan diatur dan dicabut otomatis saat penugasan selesai.
- Gerbang rilis: tes penerimaan terhadap brief, review oleh `TECH_LEAD`, persetujuan klien → aktifkan lewat deploy M2 (rollback sudah ada).
- Garansi/perbaikan: permintaan lanjutan menaut ke permintaan asal.

---

## 4. Urutan dan ukuran kasar

| Fase | Ukuran | Bergantung pada | Catatan |
|---|---|---|---|
| C0 | S | — | Murni keputusan + uji manual; **jangan dilewati** |
| C1 | M | C0 | Fondasi keamanan; paling berisiko bila salah |
| C2 | M | C1 | Domain status + linimasa |
| C3 | M–L | C1, C2 | UI terbesar |
| C4 | M | C2, chat Builder | Memanfaatkan follow-up yang sudah ada |
| C5 | L | C2 | Perlu keputusan hukum/pajak |
| C6 | L | C1–C3 | Tergantung infrastruktur sandbox |

Jalur tercepat menuju nilai: **C0 → C1 → C2 → C3** (developer bisa bekerja di portal). C4 mengurangi bolak-balik; C5 dan C6 menyusul bila skala bertambah.

---

## 5. Risiko

| Risiko | Mitigasi |
|---|---|
| Developer luar melihat data klien | Brief saja + sandbox sintetis; redaksi di brief; audit akses; NDA |
| Brief memuat cerita klien apa adanya (mengandung detail bisnis) | Keputusan redaksi (bagian 6); opsi: developer luar hanya melihat versi diringkas |
| Alur belum pernah dipakai nyata (bug deploy pack kustom ditemukan baru-baru ini) | C0 uji coba ujung ke ujung dengan klien nyata sebelum membangun portal |
| Perubahan lingkup tanpa kontrol | Brief versi baru + persetujuan ulang (C4) |
| Developer "menghilang" di tengah tugas | SLA, penugasan ulang, linimasa yang jelas, jam dicatat berkala |
| Pembayaran/pajak/kontrak kerja | Keputusan hukum sebelum C5; jangan membangun payout sebelum ada kepastian |
| Kualitas tidak merata | Review `TECH_LEAD` + tes penerimaan di C6 |

---

## 6. Keputusan yang dibutuhkan dari pemilik produk (untuk dipikirkan)

1. **Siapa developernya?** Karyawan internal saja, freelancer, atau mitra software house lain? Menentukan seberapa ketat C1 dan C6.
2. **Satu software house atau banyak?** Bila kelak platform dipakai software house lain, peran/status/tarif harus menjadi *data per organisasi* (multi-tenant di sisi developer), bukan satu set global.
3. **Apa yang boleh dilihat developer luar?** Brief penuh (termasuk cerita klien), brief diringkas, atau hanya spesifikasi teknis?
4. **Komunikasi:** boleh developer menghubungi klien langsung, atau selalu lewat PM/platform?
5. **Model harga ke developer:** per jam, harga tetap per tugas, atau bagi hasil?
6. **Kepemilikan IP dan lisensi** hasil kerja (klien, software house, atau platform)?
7. **Sandbox:** apakah cukup lingkungan bersama dengan data sintetis, atau tiap permintaan perlu lingkungan terpisah?
8. **Tahap awal:** apakah C0 (uji manual dengan satu klien) bisa dijadwalkan sebelum menulis kode C1?

---

## 7. Yang sengaja tidak masuk rencana ini
- Pembuatan kode otomatis oleh LLM (pendekatan "Hercules"): di luar tujuan; manusia yang membangun.
- Marketplace publik developer: terlalu dini; mulai dari developer yang diundang.
- Runtime data nyata untuk prototype: bukan bagian serah-terima (prototype tetap data memori).
