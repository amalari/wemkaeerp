# Modul Pembelajaran: Generate Konfigurasi MCP Kilo dari `.mcp.json`

> Level: Junior–Mid Developer
> Topik: satu sumber MCP lintas-tool, konversi bentuk `mcpServers` → `mcp` Kilo, output generate + manifest
> Prasyarat: Git, shell, JSON/JSONC, dasar Python; baca dulu [`teaching-codex-config-sync.md`](teaching-codex-config-sync.md)
> Task: menutup celah MCP di Kilo dengan menjadikan `.kilo/kilo.jsonc` output `scripts/sync-agent-config.sh`.

## 1. Konsep dasar dan masalah nyata

Setiap agent punya formulir MCP sendiri-sendiri, tetapi isinya harus sama. Di repo ini sumber
tunggalnya `.mcp.json` (bentuk `mcpServers`, gaya Claude). `scripts/sync-agent-config.sh` sudah
menerjemahkannya untuk **Codex** (`.codex/config.toml`) dan **Cline** (`.cline/mcp_settings.json`).

**Kilo tertinggal.** Kilo tidak membaca `.mcp.json`, dan bentuk config-nya berbeda: ia memakai kunci
`mcp` (bukan `mcpServers`), `command` berupa **array** yang sudah menggabungkan command + args, serta
`environment` (bukan `env`). Akibatnya `playwright` dan `graphify` tidak muncul di `/mcps` Kilo.

Menambal dengan tangan terasa cepat, tetapi salah: itu membuat sumber kebenaran kedua. Begitu
`.mcp.json` berubah (tambah server, ganti path), Kilo diam-diam basi. Karena itu Kilo dijadikan
**output generate** seperti `.codex/config.toml` — diedit di `.mcp.json`, dihasilkan ulang oleh sync.

## 2. Mulai dari mana — langkah 0 sampai selesai

0. Konsultasikan Graphify, lalu baca `scripts/sync_codex_config.py` dan `scripts/sync-agent-config.sh`.
1. Inventaris MCP yang ada: `.mcp.json`, `.cline/mcp_settings.json`, `.codex/config.toml`. Cari juga
   config Kilo (`.kilo/kilo.jsonc`) — di sini hanya berisi `skills.paths`, tanpa `mcp`.
2. Bandingkan bentuk kedua skema (Claude `mcpServers` vs Kilo `mcp`) dan tulis pemetaannya.
3. Tambahkan `kilo_config(servers)` di converter dan daftarkan output `.kilo/kilo.jsonc`.
4. Perbarui pesan/komentar skrip sinkronisasi agar menyebut Kilo.
5. Ubah **sumber** `.claude/CLAUDE.md` (bukan `AGENTS.md`) agar daftar output menyebut `.kilo/kilo.jsonc`.
6. Jalankan sync, lalu `--check` untuk membuktikan idempoten.

Penggunaan harian:

```bash
scripts/sync-agent-config.sh          # tulis ulang output
scripts/sync-agent-config.sh --check  # periksa drift; exit 1 bila menyimpang
```

## 3. Bedah implementasi

### A. Sumber dan tujuan (bagian MCP)

| Sumber yang diedit | Output |
|---|---|
| `.mcp.json` (`mcpServers`) | tabel `[mcp_servers.*]` di `.codex/config.toml` (Codex) |
| `.mcp.json` (`mcpServers`) | peta `mcp` di `.kilo/kilo.jsonc` (Kilo) |
| `.claude/skills/` | `skills.paths` di `.kilo/kilo.jsonc` |

Satu sumber (`.mcp.json`) mengalir ke dua bentuk berbeda. Tidak ada file yang disalin mentah;
keduanya adalah hasil konversi.

### B. Pemetaan bentuk

```python
def kilo_config(servers):
    entries = {}
    for name, server in sorted(servers.items()):
        if server.get('url'):
            entries[name] = {'type': 'remote', 'url': server['url'], 'enabled': True}
            continue
        entry = {'type': 'local', 'command': [server['command'], *server.get('args', [])], 'enabled': True}
        if server.get('env'):
            entry['environment'] = dict(server['env'])
        entries[name] = entry
    ...
```

Perhatikan tiga perbedaan yang wajib dijembatani:

| `.mcp.json` (Claude) | `.kilo/kilo.jsonc` (Kilo) |
|---|---|
| `command: "npx"` + `args: ["-y", …]` | `command: ["npx", "-y", …]` (array tunggal) |
| `env: { … }` | `environment: { … }` |
| tanpa `type` (stdio implisit), atau `url` | `type: "local"`, atau `type: "remote"` + `url` |

Server diurutkan (`sorted`) dan `enabled: true` selalu ditulis agar keluaran **deterministik** —
penting karena `--check` membandingkan byte, bukan makna.

### C. Output generate dan manifest

Converter mengembalikan pemetaan `path → bytes`. `sync()` membandingkan dengan file di disk:

- beda → masuk daftar drift;
- mode `--check` → cetak `MENYIMPANG: …` dan keluar dengan kode 1, tanpa menulis;
- mode tulis → tulis file yang drift dan perbarui `.codex/sync-manifest.json` (SHA-256 per output).

Manifest inilah yang menjaga **satu sumber kebenaran**: menambah entri baru aman, tetapi file yang
pernah di-generate lalu hilang dari daftar output dianggap *stale* dan menghentikan sync — sehingga
tidak ada output yatim yang diam-diam tertinggal.

### D. Kenapa `.kilo/kilo.jsonc` juga memuat `skills.paths`

Kilo memuat skill dari `{skill,skills}/<nama>/SKILL.md` **di dalam direktori config** (`.kilo/`,
`.kilocode/`). `.claude/` bukan direktori config Kilo, jadi skill `.claude/skills` hanya terbaca
karena ditunjuk eksplisit lewat `skills.paths`. Karena itu `kilo_config()` menuliskan blok `skills`
yang sama dengan versi tangan sebelumnya — bila tidak, meng-generate file ini akan mematikan skill
proyek di Kilo.

### E. Header file generate

```jsonc
// FILE HASIL GENERATE oleh scripts/sync-agent-config.sh — JANGAN disunting langsung.
// Sunting .mcp.json (server MCP) atau .claude/ (aturan/skill), lalu jalankan skrip itu.
{
  "$schema": "https://app.kilo.ai/config.json",
  "skills": { "paths": ["./.claude/skills"] },
  "mcp": { "graphify": { … }, "playwright": { … } }
}
```

Kilo menerima JSONC (komentar `//`), sehingga header penanda bisa ditulis langsung. `.jsonc` dipilih
sejak awal; generator hanya menambahkan dua baris komentar penanda di atas JSON.

## 4. Mengapa pendekatan ini dipilih

| Keputusan | Alasan dan trade-off |
|---|---|
| Kilo jadi **output generate**, bukan file tangan | Satu sumber (`.mcp.json`); mengorbankan kemampuan menambah setting khusus Kilo langsung di file itu — harus lewat generator |
| Generate satu file `.kilo/kilo.jsonc` (bukan fragmen `kilo.json` terpisah) | Kilo memuat/menimpa beberapa file config dengan urutan yang halus; menaruh semuanya di satu file menghindari ambiguitas merge |
| Tulis `skills.paths` dari generator | File ini dimiliki sync; bila skill path ditulis di tempat lain ia akan tertimpa |
| `enabled: true` eksplisit + `sorted` | Determinisme byte untuk `--check` |
| Dukung `url` → `type: "remote"` | Bentuk umum sudah ada di tabel MCP, biar tidak perlu ubah generator saat server remote masuk |

## 5. Jebakan yang perlu dihindari

1. **Mengedit `.kilo/kilo.jsonc` langsung.** Ia kini output generate; setiap edit akan hilang pada
   sync berikutnya. Ubah `.mcp.json` lalu jalankan sync.
2. **Mengira `.mcp.json` dibaca Kilo.** Kilo sama sekali tidak membacanya; hanya lewat generator ini.
3. **Membalik pesan edit.** `AGENTS.md` adalah hasil generate dari `.claude/CLAUDE.md` — mengubah
   `AGENTS.md` langsung akan ditimpa. Ubah sumbernya.
4. **Menghapus `skills.paths` dari generator.** Meng-generate file akan mematikan seluruh skill
   proyek di Kilo tanpa error; gejalanya "skill tiba-tiba hilang".
5. **Menganggap `--check` hijau berarti MCP tersambung.** Ia hanya membuktikan file tidak drift,
   bukan bahwa server bisa dijalankan atau di-autentikasi. Path executable tetap spesifik mesin.
6. **Menyamakan bentuk `command`.** Di Kilo `command` adalah array; menulis string command + args
   terpisah (gaya Claude/Codex) membuat server gagal start.

## 6. Bukti verifikasi

- `scripts/sync-agent-config.sh` → `Agent config: 2 outputs updated`, lalu
  `--check` → `Agent config: 58 outputs verified` / `Konfigurasi AI sinkron` (idempoten).
- `.codex/sync-manifest.json` memuat entri `.kilo/kilo.jsonc` (hash per output).
- Parsing `.kilo/kilo.jsonc` (komentar `//` dilepas): `mcp` berisi `graphify` + `playwright`;
  `skills.paths == ["./.claude/skills"]`; `playwright.type == "local"`.
- Kedua target MCP ada: `/Users/achmadbackup/.local/bin/graphify-mcp` (OK) dan
  `graphify-out/graph.json` (71 MB); `npx` versi 11.19.0.
- Tidak ada build Kotlin: perubahan murni tooling/konfigurasi.

## 7. Tantangan mandiri

- Tambahkan server `url`-based ke `.mcp.json`, jalankan sync, dan buktikan `.kilo/kilo.jsonc`
  menghasilkan entri `type: "remote"` di Codex **dan** Kilo.
- Ubah `args` satu server, jalankan `--check`, dan pastikan ia menandai `.kilo/kilo.jsonc` menyimpang
  sebelum `sync` memperbaikinya.
- Jelaskan mengapa menghapus blok `skills` dari generator tidak memecahkan kompilasi apa pun tetapi
  tetap regresi serius bagi pengguna Kilo.
