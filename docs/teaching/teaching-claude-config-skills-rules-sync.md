# 🎓 Modul Pembelajaran: Sinkronisasi Skills & Rules ke `.claude/` + Mandat Dokumentasi Teaching Otomatis

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Konfigurasi Claude Code (skills, rules), audit konten lintas-proyek, tata kelola dokumentasi teknis
> **Prasyarat**: Paham struktur direktori `.claude/` (skills, agents, commands, CLAUDE.md) dan konsep bahwa file konfigurasi AI-assistant bisa "membusuk" (drift) sama seperti kode
> **Referensi Task**: Permintaan user — "adjust rules dan skills yang belum ada di .claude agar ditambahkan dan rules-nya selalu setiap features buat teaching skill untuk buat documentation penjelasan"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Repo ini punya dua folder konfigurasi mirip: `.claude/` (dibaca Claude Code) dan `.agents/` (folder generik yang ternyata dipakai bergantian oleh tool AI lain — terlihat dari referensi `~/.gemini/config/mcp_config.json` di salah satu skill-nya). Karena `.agents/` dipakai lintas proyek, isinya perlahan **tercampur** dengan proyek LAIN milik user yang tidak berhubungan (portofolio 3D berbasis Three.js bernama "AchmadPorto"). Kalau kita asal `cp -r .agents/skills/* .claude/skills/`, kita akan menanam bom waktu: skill "DDD Kotlin Multiplatform" versi `.agents` isinya sudah berubah jadi panduan struktur folder `src/features/maps/barn/`, `RucksackModal`, `TvModal` — sama sekali tidak relevan untuk ERP garmen berbasis Ktor ini. Kalau skill itu ter-trigger, Claude akan menyarankan struktur folder yang salah total.

**Analogi Sederhana**: Bayangkan dua tim kantor berbagi satu lemari arsip bersama (`.agents/`) karena dulu kantornya satu. Sekarang mereka pindah ke gedung terpisah, tapi lemari itu masih dipakai bersama dan salah satu tim mulai menaruh dokumen proyek mereka di folder yang judulnya sama ("DDD Guide"). Kalau tim satunya asal fotokopi seluruh isi lemari ke arsip pribadi mereka (`.claude/`) tanpa membaca dulu, mereka akan ikut menyalin dokumen yang salah kantor.

**Hasil Akhir yang Diharapkan**: `.claude/skills/` berisi HANYA skill yang valid untuk proyek WeMade ERP ini (baik yang sudah lama ada, maupun yang baru ditambahkan dari `.agents/` setelah diverifikasi bersih), `.claude/rules/` berisi kontrak arsitektur modul operasional yang sebelumnya "yatim" (ada tapi tidak pernah terbaca Claude Code), dan `CLAUDE.md` (satu-satunya file yang **dijamin** ter-load otomatis di setiap sesi) punya aturan baru: **setiap fitur/task selesai → wajib bikin dokumentasi teaching**, tanpa perlu diminta ulang setiap kali.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Ini bukan menulis kode fitur, tapi tetap ada urutan yang benar dan salah:

1. **Langkah 0: Audit dulu, jangan copy dulu.**
   - `find .claude -maxdepth 2 -type d` vs `find .agents -maxdepth 2 -type d` → dapat daftar skill yang "belum ada di `.claude`".
   - Ini BUKAN langkah terakhir yang boleh dilewat. Kalau kita langsung `cp -r`, kita menaruh kepercayaan buta ke folder yang justru terbukti sudah tercampur (lihat Langkah 1).
2. **Langkah 1: Verifikasi setiap skill yang NAMANYA SAMA di kedua folder.**
   - `diff .claude/skills/ddd-kotlin-multiplatform/SKILL.md .agents/skills/ddd-kotlin-multiplatform/SKILL.md` — di sinilah ketahuan versi `.agents` sudah "AchmadPorto-ized". **Jangan timpa file `.claude` dengan versi ini.**
3. **Langkah 2: Untuk skill yang HANYA ada di `.agents` (belum ada padanan di `.claude`), baca isi lengkapnya satu per satu** — jangan hanya percaya nama filenya. Kelompokkan:
   - **Jelas proyek lain** (menyebut "AchmadPorto", Three.js, `src/features/maps/barn`) → skip.
   - **Toolkit generik tapi di luar stack proyek ini** (butuh Tailwind/shadcn/React, aset 3.7MB) → skip, catat alasannya.
   - **Generik & relevan untuk proses engineering apa pun** (teaching, task-resolution-doc, trd-generator, task-to-github-projects) → layak disalin.
4. **Langkah 3: Untuk skill yang layak disalin tapi menyebut infrastruktur tool lain** (mis. `task-to-github-projects` mengasumsikan GitHub MCP Server Gemini), **adaptasi**, jangan salin mentah — di sini kita ganti instruksinya ke `gh` CLI yang memang tersedia di environment Claude Code ini.
5. **Langkah 4: Cek apakah ada dokumen "rules" yang sebenarnya valid untuk proyek ini tapi selama ini tidak terbaca** karena hanya hidup di `.agents/rules/`, bukan di `.claude/`. Di sini ditemukan `module-integration-rules.md` — isinya murni tentang WeMade ERP (`ModuleArchetype`, `CostingBehavior`, dll., persis konsep yang dipakai di kode `OperationalModuleCatalog.kt`). Ini genuinely berharga dan harus dipindahkan ke `.claude/rules/`.
6. **Langkah 5: Tulis aturan baru di `CLAUDE.md`, bukan cuma di file rules terpisah.** `CLAUDE.md` adalah satu-satunya file yang dijamin ter-load setiap sesi Claude Code baru. File di `.claude/rules/*.md` **tidak** otomatis ter-baca kalau tidak ada yang merujuknya — jadi section baru di `CLAUDE.md` harus eksplisit menyebut path-nya sebagai bacaan wajib.
7. **Langkah 6: Tambahkan mandat "teaching" sebagai perilaku default**, lalu **buktikan langsung** dengan memanggil skill itu untuk task ini sendiri (dokumen yang sedang Anda baca ini adalah buktinya).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Deteksi kontaminasi lintas-proyek (audit, bukan kode Kotlin)

```bash
diff .claude/skills/ddd-kotlin-multiplatform/SKILL.md .agents/skills/ddd-kotlin-multiplatform/SKILL.md
```

**Mengapa blok ini penting?**
- File dengan nama identik di dua lokasi **bukan jaminan isinya identik**. `diff` adalah cara paling murah untuk membuktikan itu sebelum menimpa apa pun.
- Hasilnya menunjukkan `.agents` versi sudah menyebut `EventVerse / AchmadPorto`, `src/features/maps/barn/`, `RucksackModal` — sinyal jelas file itu sudah "diedit di tempat" untuk proyek lain yang kebetulan berbagi folder konfigurasi global.
- Pelajaran: **konfigurasi AI assistant butuh code review yang sama seriusnya dengan kode produksi.** Salah menyalin skill bisa membuat Claude menyarankan arsitektur yang salah pada proyek yang salah — bug yang jauh lebih mahal diperbaiki karena efeknya "menyesatkan developer", bukan sekadar test merah.

### Blok B: Menambahkan rules baru dengan penamaan kolom yang konsisten

```
.claude/
├── CLAUDE.md                          # satu-satunya file yang DIJAMIN ter-load
├── rules/
│   └── module-integration-rules.md    # detail lengkap, hanya terbaca kalau DIRUJUK
└── skills/
    ├── teaching/SKILL.md
    ├── task-resolution-doc/SKILL.md
    ├── trd-generator/SKILL.md
    └── task-to-github-projects/SKILL.md
```

**Mengapa struktur ini dipilih?**
- `CLAUDE.md` menyimpan **ringkasan** (8 poin kontrak modul) + **link eksplisit** ke `module-integration-rules.md` untuk detail lengkap (tabel Archetype Registry, checklist Definition of Done). Ini mengikuti prinsip yang sama seperti kode: **jangan duplikasi konten**, `CLAUDE.md` adalah "index/pointer", file rules adalah "sumber kebenaran" detail.
- Kalau seluruh 115 baris `module-integration-rules.md` ditempel mentah ke `CLAUDE.md`, filenya jadi terlalu panjang untuk dibaca cepat di awal setiap sesi. Memisahkannya menjaga `CLAUDE.md` tetap scan-able, sementara detail tetap ada untuk dibaca saat benar-benar relevan (sebelum membuat modul baru).

### Blok C: Adaptasi skill yang mengasumsikan tool lain

```markdown
<!-- SEBELUM (skill asli, ditulis untuk Gemini CLI) -->
## Prerequisites
1. **GitHub MCP Server** configured in `~/.gemini/config/mcp_config.json`

<!-- SESUDAH (diadaptasi untuk Claude Code di environment ini) -->
## Prerequisites
Execute via the **`gh` CLI** (already available in this environment...)
```

**Mengapa blok ini ditulis begini?**
- Skill `task-to-github-projects` isinya bagus (template issue, workflow 4 langkah, best practices) — tapi mekanisme eksekusinya (GitHub MCP Server Gemini) tidak tersedia di sesi Claude Code ini.
- Daripada membuang seluruh skill atau membiarkan instruksi yang salah (yang akan membuat Claude mencoba memanggil MCP tool yang tidak ada), bagian **Prerequisites** dan **Step 3** diganti ke `gh issue create` / `gh project item-add` yang memang sudah dikonfirmasi tersedia (`gh --version` sukses, `git remote` menunjuk ke `github.com:amalari/wemade-erp.git`).
- Pelajaran: **menyalin skill lintas-tool itu boleh, tapi bagian "cara eksekusi" harus disesuaikan ke tool yang benar-benar dipakai** — sisanya (template, struktur, best practice) sering kali tetap tool-agnostic dan aman disalin apa adanya.

### Blok D: Mandat otomatis di `CLAUDE.md`

```markdown
### 12. Dokumentasi Wajib Pasca-Fitur (Teaching Skill)

**Setiap kali sebuah task, issue, modul, atau fitur baru selesai diimplementasikan**
(termasuk perubahan signifikan pada fitur yang sudah ada), panggil skill `teaching`
untuk menghasilkan dokumentasi mentoring teknis di `docs/teaching/teaching-[slug].md`,
lalu tautkan file tersebut di respons akhir ke user. Ini berlaku otomatis — tidak perlu
menunggu user memintanya secara eksplisit.
```

**Mengapa blok ini ditulis begini?**
- Kata kunci "berlaku otomatis — tidak perlu menunggu user memintanya secara eksplisit" sengaja ditulis eksplisit karena skill `teaching` versi aslinya memang bisa dipicu manual ("buatkan dokumentasi teaching-nya") — tapi maksud user di task ini adalah menjadikannya **default behavior**, bukan opsional. Kalau tidak ditulis se-eksplisit itu, ada risiko aturan ini dibaca sebagai "tersedia kalau diminta" (perilaku lama), bukan "wajib setiap kali" (perilaku baru yang diminta).
- Section ini juga membedakan `teaching` (wajib otomatis) dari `task-resolution-doc` dan `trd-generator` (tersedia, dipakai sesuai konteks) — supaya Claude tidak membuat 3 dokumen berbeda untuk task yang sama tanpa alasan jelas.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Audit manual per-file sebelum copy** | `cp -r .agents/skills/* .claude/skills/` langsung | `diff` membuktikan `.agents` sudah tercampur proyek lain; copy langsung akan menanam instruksi arsitektur yang salah ke dalam skill project ini | Skill DDD versi salah bisa ter-trigger dan menyarankan struktur `src/features/maps/barn/` di proyek ERP Kotlin — membingungkan dan merusak konsistensi arsitektur |
| **Rules detail di file terpisah + ringkasan+link di `CLAUDE.md`** | Tempel semua 115 baris rules langsung ke `CLAUDE.md` | `CLAUDE.md` harus tetap ringkas karena ini file yang dibaca di **setiap** sesi baru; rules detail hanya perlu dibaca saat benar-benar membuat modul | `CLAUDE.md` jadi sangat panjang, menaikkan biaya konteks di setiap sesi walau sedang tidak mengerjakan modul operasional |
| **Skip skill yang scope-nya proyek lain / toolkit tak relevan** | Salin semua yang "belum ada di `.claude`" tanpa pandang bulu | `threejs-z-fighting-prevention` eksplisit judulnya "AchmadPorto Project"; `ui-ux-pro-max` sendirian 3.7MB berisi database style/font/ikon yang tak dipakai stack Compose Multiplatform proyek ini | Bloat repo tanpa manfaat, plus risiko skill ter-trigger di konteks yang salah (mis. Claude menyarankan Tailwind/shadcn di proyek yang pakai Compose Multiplatform) |
| **Adaptasi instruksi tool (GitHub MCP → `gh` CLI)** | Salin mentah instruksi MCP Gemini | MCP GitHub server tidak terpasang di sesi ini; `gh` CLI sudah terverifikasi ada dan sudah jadi rekomendasi default harness ini | Skill akan mencoba memanggil tool yang tidak ada dan gagal total saat dipakai |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asumsi "nama folder sama → isi sama, aman disalin timpa".**
   - *Kenapa bahaya*: Dua folder konfigurasi (`.claude/` dan `.agents/`) yang dipakai lintas proyek bisa drift diam-diam. Tanpa `diff` eksplisit, kita bisa menimpa versi yang sudah benar dengan versi yang sudah rusak, dan tidak ada test otomatis yang akan menangkapnya (ini konfigurasi Markdown, bukan kode yang di-compile).
   - *Solusi elegan kita*: Selalu `diff` dulu untuk setiap nama yang sama sebelum memutuskan menimpa, dan baca isi lengkap (bukan cuma judul) untuk setiap file yang "hanya ada di satu sisi".

2. **Jebakan 2: Menganggap semua yang "belum ada" otomatis layak ditambahkan.**
   - *Kenapa bahaya*: "Belum ada di `.claude`" bisa berarti dua hal sangat berbeda: (a) memang belum sempat disinkronkan, valid untuk proyek ini, atau (b) memang tidak relevan untuk proyek ini sejak awal. Menyamakan keduanya membuat kita menambahkan noise (bahkan berpotensi berbahaya) ke instruksi yang dibaca AI assistant di setiap sesi kerja.
   - *Solusi elegan kita*: Klasifikasikan setiap kandidat berdasarkan **konten aktualnya**, bukan hanya keberadaan file-nya, dan nyatakan alasan skip secara eksplisit ke user (transparansi keputusan, bukan silent-drop).

3. **Jebakan 3: Menulis aturan baru hanya di file "rules" terpisah, lupa merujuknya dari file yang benar-benar ter-load otomatis.**
   - *Kenapa bahaya*: Tidak semua file di `.claude/` dijamin ter-load otomatis oleh harness di setiap sesi baru — hanya `CLAUDE.md` yang eksplisit dikonfirmasi demikian (terlihat di `<system-reminder>` awal setiap sesi). File tambahan seperti `.claude/rules/*.md` butuh **dirujuk** dari `CLAUDE.md`, kalau tidak, isinya jadi "yatim" lagi persis seperti nasib `module-integration-rules.md` sebelumnya di `.agents/rules/`.
   - *Solusi elegan kita*: Selalu tambahkan pointer eksplisit (path + ringkasan poin penting) di `CLAUDE.md` untuk setiap file rules baru, bukan cuma menaruh filenya di folder yang "kelihatan benar".

---

## 🧪 6. Bagaimana Cara Membuktikan Perubahan Ini Bekerja?

Karena ini perubahan konfigurasi (bukan kode yang bisa di-unit-test), verifikasinya berbeda:

1. **Verifikasi struktural** — pastikan setiap skill baru punya frontmatter YAML yang valid (diapit `---` di baris pertama & terakhir blok, ada field `name` dan `description`) dengan `head -8` per file. Frontmatter yang rusak membuat skill gagal terdaftar.
2. **Verifikasi runtime langsung** — setelah `.claude/skills/teaching/SKILL.md` ditambahkan, `<system-reminder>` daftar skill yang tersedia di sesi ini **langsung** menampilkannya tanpa perlu restart sesi — ini bukti paling kuat bahwa skill benar-benar terdaftar dan bisa dipanggil (bukan cuma file mati).
3. **Verifikasi end-to-end** — memanggil `Skill(skill: "teaching", ...)` sungguhan untuk task ini sendiri, dan menghasilkan dokumen ini di `docs/teaching/`. Kalau skill-nya gagal ter-load atau argumen tidak diteruskan dengan benar, langkah ini akan gagal secara jelas (bukan silent).
4. **Verifikasi isi rules tidak tercampur** — baca ulang `.claude/rules/module-integration-rules.md` hasil salinan, pastikan tidak ada satu pun penyebutan istilah dari proyek lain (`AchmadPorto`, `Three.js`, `RucksackModal`, dll). Untuk proyek riil, cek ini bisa diotomasi sederhana: `grep -ril "AchmadPorto\|Three.js" .claude/` harus kosong.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Jalankan `grep -ril "AchmadPorto\|three.js\|Three.js" .claude/` di root repo ini. Pastikan hasilnya kosong (bukti tidak ada kontaminasi lolos ke `.claude/`). Kalau ada hasil, itu bug — cari tahu dari file mana asalnya.
- [ ] **Tantangan 2**: Baca `.claude/rules/module-integration-rules.md` penuh, lalu bandingkan Kontrak 1–8 di dalamnya dengan implementasi nyata di `core/src/commonMain/kotlin/com/eventverse/app/domain/pipeline/OperationalModuleCatalog.kt` dan `TenantModuleEntitlement.kt`. Temukan minimal satu kontrak yang **belum** sepenuhnya diimplementasikan di kode — itu jadi kandidat task/issue baru.
- [ ] **Tantangan 3 (opsional, lebih besar)**: Kalau nanti proyek ini butuh benar-benar tooling desain (banner, slide, dsb.), rancang bagaimana menambahkannya **tanpa** menimbulkan konflik nama dengan skill built-in Claude yang sudah ada (`design`, `dataviz`) — misalnya lewat penamaan dengan prefix/scope proyek eksplisit.
