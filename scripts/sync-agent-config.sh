#!/usr/bin/env bash
# Sinkronisasi konfigurasi AI: .claude/ → Codex, Cline & Gemini/Antigravity.
#   scripts/sync-agent-config.sh          → tulis
#   scripts/sync-agent-config.sh --check  → hanya periksa; exit 1 bila ada yang menyimpang
set -euo pipefail
cd "$(dirname "$0")/.."
CHECK=${1:-}
case "$CHECK" in ''|--check) ;; *) echo 'Usage: scripts/sync-agent-config.sh [--check]' >&2; exit 2 ;; esac
drift=0
tmp=$(mktemp -d)
trap 'rm -f "$tmp/AGENTS.md" "$tmp/GEMINI.md"; rmdir "$tmp"' EXIT

# Skill milik proyek yang dibagikan ke .agents/ (Gemini). Skill lain di .agents/ sengaja TIDAK
# disentuh: sebagian tercampur proyek lain (lihat docs/teaching/teaching-claude-config-skills-rules-sync.md).
# Seluruh skill sumber, termasuk references/scripts/assets, dikelola oleh sinkronisasi.

generate_agents_md() {
  {
    echo "<!-- FILE HASIL GENERATE oleh scripts/sync-agent-config.sh — JANGAN disunting langsung."
    echo "     Sunting .claude/CLAUDE.md atau .claude/rules/*.md, lalu jalankan skrip itu. -->"
    echo
    cat .claude/CLAUDE.md
    for rule in .claude/rules/*.md; do
      echo; echo "---"; echo; cat "$rule"
    done
  } > "$1"
}

put() { # $1 sumber, $2 tujuan
  if [ -n "$CHECK" ]; then
    if ! cmp -s "$1" "$2" 2>/dev/null; then echo "MENYIMPANG: $2"; drift=1; fi
  else
    mkdir -p "$(dirname "$2")"; cp "$1" "$2"
  fi
}

# 1. AGENTS.md (root: Cline/Gemini CLI) & .agents/AGENTS.md (Antigravity)
generate_agents_md "$tmp/AGENTS.md"
put "$tmp/AGENTS.md" AGENTS.md
put "$tmp/AGENTS.md" .agents/AGENTS.md

# 2. GEMINI.md — Gemini CLI membaca GEMINI.md secara default
cat > "$tmp/GEMINI.md" <<'MD'
# WeMade ERP — Konteks untuk Gemini

Seluruh aturan proyek ada di [`AGENTS.md`](AGENTS.md) (hasil generate dari `.claude/`).
Baca itu dulu. Sebelum membuat fitur/modul: ikuti skill `wemade-feature-discovery` lalu
`wemade-feature-workflow` (di `.agents/skills/` atau `.claude/skills/`).

## graphify — Selalu Cek Graphify Terlebih Dahulu (Graphify First)

Repo ini memiliki knowledge graph Graphify di `graphify-out/` (termasuk `graphify-out/graph.json`, symbol nodes, dependency links, dan komunitas).

### Aturan Utama: Wajib Cek Graphify Sebelum Cara Lain
Bila `graphify-out/graph.json` ada, **WAJIB SELALU memeriksa Graphify terlebih dahulu** sebelum mencari kode menggunakan cara lain (`grep_search`, `find`, `list_dir`, atau browsing file mentah).

Rules:
- Untuk pertanyaan kode atau arsitektur, pertama jalankan `graphify query "<pertanyaan>"` (CLI) atau `query_graph` (MCP). Gunakan `graphify path "<A>" "<B>"` / `shortest_path` untuk relasi dan `graphify explain "<konsep>"` / `get_node` untuk konsep/simbol tertentu.
- **DILARANG** menggunakan `grep_search`, `find`, atau scanning direktori mentah sebagai langkah pertama saat graph tersedia. Gunakan grep/find HANYA setelah Graphify dicek atau bila mencari string literal exact (teks config/error literal).
- Bila `graphify-out/wiki/index.md` ada, gunakan untuk navigasi daripada membaca file mentah langsung.
- Baca `graphify-out/GRAPH_REPORT.md` hanya untuk review arsitektur global bila query/path belum cukup.
- Setelah memodifikasi kode, jalankan `graphify update .` untuk memperbarui graph (AST-only, tanpa biaya API).
MD
put "$tmp/GEMINI.md" GEMINI.md

# 3. Rules → .agents/rules (Antigravity). .clinerules sudah symlink ke .claude/rules.
for rule in .claude/rules/*.md; do put "$rule" ".agents/rules/$(basename "$rule")"; done

# 4. Skill proyek → .agents/skills
# Converter di langkah 6 mengelola seluruh skills dengan manifest.

# 5. Symlink Cline
check_link() { # $1 path, $2 target
  if [ "$(readlink "$1" 2>/dev/null)" != "$2" ]; then
    if [ -n "$CHECK" ]; then echo "MENYIMPANG: $1 → $(readlink "$1" 2>/dev/null || echo '(tidak ada)') (harus $2)"; drift=1
    else ln -sfn "$2" "$1"; fi
  fi
}
check_link .clinerules .claude/rules
[ -n "$CHECK" ] || mkdir -p .cline
check_link .cline/skills ../.claude/skills

# 6. Konversi command, custom agents, hooks, MCP dan laporan kompatibilitas Codex.
python3 scripts/sync_codex_config.py ${CHECK:+"$CHECK"} || drift=1

if [ -n "$CHECK" ]; then
  [ $drift -eq 0 ] && echo "Konfigurasi AI sinkron." || { echo "Jalankan: scripts/sync-agent-config.sh"; exit 1; }
else
  [ $drift -eq 0 ] || exit 1
  echo "Sinkron: AGENTS.md, seluruh skills, commands, agents, hooks, MCP, Gemini & Cline."
fi
