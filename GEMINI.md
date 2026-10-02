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
