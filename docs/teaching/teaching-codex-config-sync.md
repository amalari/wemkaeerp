# Modul Pembelajaran: Sinkronisasi Claude ke Codex

> Level: Junior–Mid Developer  
> Topik: generator konfigurasi, konversi skills/agents/hooks, verifikasi drift  
> Prasyarat: Git, shell, JSON, dasar Python  
> Task: melengkapi sinkronisasi `.claude/` ke Codex.

## 1. Konsep dasar dan masalah nyata

Konfigurasi lintas agent mirip satu buku prosedur yang diterjemahkan untuk beberapa
tim. Kontennya sama, tetapi formulir dan pintu masuk tiap tim berbeda. Menyalin
folder `.claude/agents` saja tidak mendaftarkan custom agent di Codex.

Sebelumnya `sync-agent-config.sh` hanya memperbarui dua skill `wemade-*`. Skill
lain bisa berbeda: DDD di `.agents/` menyebut proyek Three.js, sedangkan sumber
`.claude/` berisi aturan KMP. Ada pula skrip validasi dengan contoh path modul
yang tidak cocok dengan `core/src` dan `app/shared/src`.

Hasil sekarang: satu sumber yang dapat diedit, output yang dapat diregenerasi,
manifest hash untuk mendeteksi sumber yang dihapus, dan laporan kompatibilitas.
Keberadaan file konfigurasi tidak membuktikan hook sudah dipercaya oleh runtime.

## 2. Mulai dari mana — langkah 0 sampai selesai

0. Konsultasikan Graphify, kemudian periksa skrip sinkronisasi dan sumber konfigurasi.
1. Bedakan format yang bisa disalin (folder skill) dari format yang perlu konversi
   (command Markdown, agent Markdown, registrasi hooks dan MCP).
2. Implementasikan generator `scripts/sync_codex_config.py`; Python 3.9+ cukup
   untuk menjalankannya tanpa dependensi tambahan.
3. Tambahkan adapter di `.claude/codex/file-modified.py`. Adapter membaca JSON
   stdin dari Codex, termasuk patch yang menyentuh beberapa file.
4. Perbaiki pemilihan tugas Gradle di `.claude/hooks/validation-task.sh`.
5. Panggil generator melalui entry point lama, lalu periksa drift dan format output.
6. Mulai sesi Codex baru di repo ini dan review project trust serta `/hooks`.

Penggunaan harian:

```bash
scripts/sync-agent-config.sh
scripts/sync-agent-config.sh --check
python3.14 -B scripts/test_sync_codex_config.py
```

Tes juga bisa dijalankan dengan Python 3.9; pemeriksaan TOML akan dilewati karena
`tomllib` baru tersedia di Python 3.11+. Gunakan Python 3.11+ untuk seluruh tes.

## 3. Bedah implementasi

### A. Sumber dan tujuan

| Sumber yang diedit | Output |
|---|---|
| `.claude/CLAUDE.md`, `.claude/rules/` | `AGENTS.md`, `.agents/AGENTS.md`, `.agents/rules/` |
| `.claude/skills/<nama>/` | `.agents/skills/<nama>/`, lengkap dengan resource |
| `.claude/commands/*.md` | `.agents/skills/<nama>/SKILL.md`, salinan command legacy |
| `.claude/agents/*.md` | `.codex/agents/*.toml`, salinan `.agents/agents/` |
| `.claude/settings.json` hooks | `.codex/hooks.json` |
| `.claude/hooks/` | `.codex/hooks/`, `.agents/hooks/` |
| `.claude/codex/` | default konfigurasi dan adapter hook Codex |
| `.mcp.json` | tabel MCP dalam `.codex/config.toml` |

Skrip lama tetap mengelola Gemini/Cline. Converter menambahkan output Codex dan
mencatat output miliknya di `.codex/sync-manifest.json`. File ekstra di
`.agents/skills` dipertahankan; isinya bukan otomatis menjadi sumber resmi proyek.

### B. Generate dulu, bandingkan, baru tulis

`build_outputs(root)` membuat pemetaan path → bytes. `sync(root, check)` membandingkan
bytes dengan file tujuan. Mode `--check` mengembalikan status gagal jika ada drift,
tanpa memperbaikinya. Output dengan symlink ditolak agar generator tidak menulis
melalui link ke sumber lain.

Manifest menyimpan SHA-256 untuk tiap output converter. Jika sumber dihapus atau
diganti nama, converter melaporkan output lama sebagai stale dan berhenti. Arsipkan
output lama yang ditunjuk, lalu hapus entrinya dari manifest sebelum sync kembali.
Ini merupakan rekonsiliasi penghapusan manual, bukan garbage collection otomatis.

### C. Command menjadi skill dan agent menjadi TOML

Command `execute-ticket` mendapat frontmatter `name` dan `description`. Placeholder
`$ARGUMENTS` menjadi `TICKET_ID`, yang diambil dari prompt user sebagai data.
Referensi skills diarahkan ke `.agents/skills`; metrics/memory menjadi
`.codex/pipeline-*`. Instruksi menegaskan bahwa push/commit dan operasi eksternal
tetap memerlukan otorisasi dalam permintaan user.

Agent TOML berisi `name`, `description`, dan `developer_instructions`. Planner dan
reviewer memiliki default sandbox read-only. Model tidak dipaksakan: agent mengikuti
model parent. Batas sandbox aktif dari host tetap berlaku. Lima role yang dikonversi:
planner, implementer, validator, reviewer, fixer.

### D. Hook adapter dan validasi

Hook terdaftar di sumber saat ini hanya notifikasi setelah Edit/Write. Adapter
membaca `tool_input.file_path` atau marker Add/Update/Delete/Move dalam
`tool_input.command`, lalu menghasilkan JSON `systemMessage`. Path tidak dieksekusi.

Skrip compile/test disalin sebagai utilitas yang dipanggil eksplisit dengan `bash`:

```bash
bash .codex/hooks/validate-compile.sh app/shared/src/commonMain/Example.kt --print-task
# :app:shared:compileKotlinJvm
bash .codex/hooks/validate-tests.sh :core --print-task
# :core:jvmTest
```

Tanpa `--print-task`, skrip menjalankan Gradle dan meneruskan exit code aslinya.
Server menggunakan `:server:compileKotlin` / `:server:test`. Detekt belum dipasang;
skrip Detekt keluar dengan kode 2 dan pesan UNAVAILABLE. JVM compile bukan bukti
kelulusan lima target KMP; target tambahan tetap dipilih sesuai perubahan.

### E. Permissions, MCP dan ukuran instruksi

`AGENTS.md` sekitar 84 KiB, sehingga default Codex dinaikkan menjadi 128 KiB melalui
`.claude/codex/config.toml`. Generator menolak ukuran mendekati batas agar tidak
diam-diam menganggap seluruh instruksi termuat.

Permissions Claude tidak memiliki kesetaraan penuh dengan sandbox Codex. Generator
memakai `workspace-write` dan `on-request`, serta menyimpan permissions yang tidak
diterjemahkan dalam `.codex/sync-report.json`. Pola deny Claude tidak otomatis
ditegakkan oleh konversi ini. Review laporan sebelum mengandalkan kesetaraan akses.

MCP Graphify dan Playwright mengikuti `.mcp.json`. Path executable tetap spesifik
mesin; perbaiki sumber itu saat pindah komputer, lalu jalankan sync. Sinkronisasi
tidak melakukan autentikasi, koneksi jaringan, atau memberikan trust otomatis.

## 4. Mengapa pendekatan ini dipilih

| Keputusan | Alasan dan trade-off |
|---|---|
| Salin seluruh folder skill | Resource relatif tetap utuh; ada duplikasi disk yang dapat diperiksa Git |
| Generator Python tanpa dependency | Bisa berjalan di Python bawaan; parsing TOML untuk tes memakai 3.11+ |
| Custom agent TOML | Role tetap bisa didelegasikan; perlu Codex yang mendukung format terkini |
| Hanya aktifkan hook yang terdaftar | Menjaga perilaku sumber; compile/test tersedia sebagai utilitas |
| Laporkan permissions yang tak diterjemahkan | Menghindari klaim kesetaraan akses yang tidak benar |
| Preserve output stale | Tidak menghapus perubahan user; butuh rekonsiliasi manual saat menghapus sumber |

## 5. Jebakan yang perlu dihindari

1. Mengedit output langsung: perubahan akan ditimpa sync; edit `.claude/` atau
   `.mcp.json`, sedangkan logika konversi hidup di `scripts/sync_codex_config.py`.
2. Menganggap `--check` berarti runtime aktif: ia memeriksa drift, bukan trust hook.
3. Menjalankan automatic import dan generator pada output yang sama: pilih satu
   pengelola agar konfigurasi tidak saling menimpa.
4. Menganggap nama skill sama berarti isi sama: sumber `.claude/` menang untuk
   skill yang dikelola; skill tambahan di `.agents/` tetap perlu audit relevansi.
5. Menyamakan skip dengan sukses: Detekt UNAVAILABLE harus terlihat dalam laporan.

## 6. Bukti verifikasi

- Tujuh tes offline lulus dengan Python 3.14: idempotensi/drift, file tambahan,
  sumber dihapus, symlink, hook yang memerlukan adapter, parsing TOML/resource
  skill, serta path patch multi-file dan pemilihan tugas Gradle.
- `scripts/sync-agent-config.sh --check` lulus; `git diff --check` bersih.
- `codex mcp list` mengenali Graphify dan Playwright. Ini belum tes koneksi server.
- Validator skill bawaan tidak bisa berjalan karena PyYAML tidak tersedia.
  Frontmatter `execute-ticket` diverifikasi memakai Ruby YAML; placeholder lama
  juga diperiksa sudah hilang.
- Tidak ada build Kotlin dijalankan: perubahan berada di konfigurasi/tooling.
  Pilihan tugas Gradle diuji tanpa menjalankan build.
- Hook belum diuji dalam sesi baru setelah trust; langkah aktivasi dilakukan user
  melalui `/hooks`. Jangan mengganti review tersebut dengan bypass trust.

Referensi format: [skills](https://learn.chatgpt.com/docs/build-skills),
[agents](https://learn.chatgpt.com/docs/agent-configuration/subagents),
[hooks](https://learn.chatgpt.com/docs/hooks), dan
[batas AGENTS.md](https://learn.chatgpt.com/docs/agent-configuration/agents-md).

## 7. Tantangan mandiri

- Tambahkan resource kecil ke skill sumber, jalankan sync, dan buktikan `--check`
  mendeteksi perubahan jika output resource diedit manual.
- Uji hook dengan patch rename dua file; pastikan nama lama dan baru dilaporkan.
- Tambahkan tugas validasi untuk target lain setelah memeriksa task Gradle aktual,
  lalu tambahkan kasus `--print-task` agar mapping dapat diperiksa tanpa build mahal.
