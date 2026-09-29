# 🎓 Modul Pembelajaran: Alur Wajib Fitur/Modul & Sinkronisasi Konfigurasi AI (TRD-FLOW-002 Fase 1)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Mencegah refactor besar lewat keputusan awal, anatomi pendaftaran modul, satu sumber kebenaran konfigurasi AI
> **Prasyarat**: Pernah membaca `.claude/CLAUDE.md` dan ketiga rules di `.claude/rules/`
> **Referensi Task**: Rencana `dengan-perubahan-sebanyak-ini` — Fase 1

---

## 💡 1. Konsep Dasar

TRD-FLOW-001 membongkar ±70 file karena satu keputusan di hari pertama: kerangka tahap ditulis
sebagai enum rajut. Rules yang ada (modul, design system, ukuran file) tidak pernah menanyakan
"apakah ini bisa beda per tenant?". Fase 1 menambahkan **pertanyaan-pertanyaan itu sebagai langkah
wajib**, dan membuatnya terbaca oleh ketiga asisten AI yang dipakai di repo ini.

---

## 🧭 2. Apa yang Ditambahkan

| Artefak | Fungsi |
|---|---|
| `.claude/rules/tenant-variability-rules.md` | 8 kontrak "kode vs data" |
| `module-integration-rules.md` §5 | Anatomi pendaftaran per jenis: operasional, governance, foundation, fitur; kontrak I/O; NAME vs code |
| Skill `wemade-feature-discovery` | 8 langkah sebelum kode: kebutuhan → fitur serupa → jenis → variabilitas → core/extend → I/O & kanvas → governance → Discovery Note |
| Skill `wemade-feature-workflow` | 9 gerbang dengan output wajib, mengorkestrasi skill yang sudah ada |
| `scripts/find-similar-feature.sh` | Menjawab "sudah ada belum?" dari paket domain, modul, menu, route, migrasi, teaching doc |
| `scripts/audit-variability.sh` | Melapor enum domain baru, tabel `when` per tahap, `.entries` sebagai urutan, literal warna, route mutasi |
| `scripts/sync-agent-config.sh` | `.claude/` → Cline & Gemini, dengan `--check` |
| `CLAUDE.md` §15 | Pintu masuk: Discovery → Workflow |

---

## 🧱 3. Bedah Keputusan

### Blok A — Dua skill, bukan satu

Discovery **tidak boleh** menulis kode; Workflow **boleh**. Memisahkannya membuat "berpikir dulu"
menjadi langkah yang bisa diperiksa (ada Discovery Note atau tidak), bukan niat baik.

### Blok B — Audit yang divalidasi mundur

Skrip audit hanya berguna kalau ia **pasti** menangkap kesalahan yang sudah pernah terjadi. Maka ia
diuji pada snapshot sebelum TRD-FLOW-001 (`git archive 534f4c3~1`): ia menandai
`SamplingPipelineStage.entries` di `SamplingRoute`, `OperatorDeskAccess`, `SamplingStageWork`, dan
tabel `when` di `SamplingTimelineCalculator` — persis tempat yang harus dibongkar. Di HEAD: 0 temuan;
terhadap commit sebelum 3b: 5 route mutasi `TenantStageFlowRoutes` (semuanya sudah fail-closed —
itulah gunanya "melapor": setiap temuan harus bisa dijelaskan).

### Blok C — Satu sumber kebenaran, tiga pembaca

| Tool | Membaca | Cara sinkron |
|---|---|---|
| Claude Code | `.claude/` | sumber |
| Cline | `.clinerules` (symlink), `.cline/skills` (symlink), `AGENTS.md` | symlink diperbaiki: dulu menunjuk `/Projects/wemade/…` yang tidak ada |
| Gemini / Antigravity | `.agents/`, `AGENTS.md`, `GEMINI.md` | salinan + generate |

`.agents/` disalin, bukan di-symlink, karena tidak pasti setiap tool mengikuti symlink. Salinan
aman karena `--check` menangkap penyimpangan — termasuk yang **sudah ada**: `design-system-rules.md`
di `.agents/` ternyata basi. Skill `.agents/` lain tidak disentuh karena sebagian tercampur proyek
lain (pelajaran `teaching-claude-config-skills-rules-sync.md`).

---

## ⚠️ 4. Jebakan

1. **Menyunting `AGENTS.md` langsung** — akan tertimpa. Sunting `.claude/`, lalu sinkron.
2. **Regex audit dengan `^`** — baris yang dipindai berformat `file:baris:isi`; jangkar harus
   `:[0-9]+:`. Dua pemeriksaan sempat diam karena ini, dan hanya ketahuan lewat uji mundur.
3. **Mengira "0 temuan" berarti bersih** tanpa pernah melihat skrip menemukan sesuatu.

---

## 🧪 5. Pembuktian

- `find-similar-feature.sh washing cuci` → `domain/workqueue/WashingBatch*`, `WashingBatchRoutes`,
  V70, dua teaching doc, dan `WashingBatchWorkbenchScreen`.
- Audit mundur menandai enum rajut; HEAD 0.
- `sync-agent-config.sh --check` bersih; `.cline/skills/wemade-feature-workflow/SKILL.md` terbaca;
  `AGENTS.md` memuat §15, tanpa "AchmadPorto"; skill `.agents` lain tidak berubah.
