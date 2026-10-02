# WeMade ERP — Aturan Graphify (Graphify First)

Repo ini memiliki knowledge graph Graphify di `graphify-out/` (termasuk `graphify-out/graph.json`, nodes, god nodes, relasi antar-file, dan komunitas).

> **Status Cline**: MCP server `graphify` sudah terpasang di `.cline/mcp_settings.json` dan
> melayani graph repo ini (±15 ribu node, 57 ribu edge). Tool-nya tersedia langsung di setiap
> sesi Cline — tidak perlu install apa pun.

---

## MANDATORY DIRECTIVE: Selalu Cek Graphify Terlebih Dahulu Sebelum Cara Lain

Ketika mencari kode, memahami arsitektur, melacak relasi/dependensi, atau menemukan letak implementasi:
**WAJIB SELALU memeriksa Graphify terlebih dahulu sebelum menggunakan cara pencarian lain (seperti `grep_search`, `search_codebase`, ripgrep/`rg`, `find`, `list_dir`, glob, atau penelusuran file mentah).**

### 1. Urutan Pencarian Wajib (Graphify First)
Bila `graphify-out/graph.json` tersedia:
1. **Via MCP (tersedia di Cline — selalu langkah pertama)**:
   - `query_graph(query="...")` — pertanyaan konsep, alur kerja, atau pencarian fitur.
   - `get_node(name="...")` — simbol/kelas/fungsi tertentu.
   - `shortest_path(source="...", target="...")` atau `get_neighbors(node_id="...")` — relasi & dependensi.
   - `graph_stats` / `god_nodes` — gambaran struktur sebelum menyelam lebih dalam.
2. **Via CLI / Shell** (bila MCP tool belum memangkas cukup):
   - `graphify query "<pertanyaan>"` — subgraph terfokus (jauh lebih hemat token dan akurat dibanding grep mentah).
   - `graphify path "<A>" "<B>"` — relasi antar dua konsep/komponen.
   - `graphify explain "<konsep>"` — penjelasan konsep terfokus.
3. **Navigasi Luas**:
   - Bila `graphify-out/wiki/index.md` ada, telusuri wiki tersebut daripada membaca file mentah satu per satu.
   - Baca `graphify-out/GRAPH_REPORT.md` hanya untuk konteks arsitektur global bila query/path/explain belum cukup.

### 2. Larangan (Anti-Pattern)
- **DILARANG** memanggil `grep_search`, `search_codebase`, `find`, `list_dir`, atau scanning direktori mentah sebagai langkah pertama saat knowledge graph tersedia.
- **DILARANG** membaca berkas kode sembarangan sebelum mengidentifikasi titik masuk lewat Graphify.
- **DILARANG** menyimpulkan "Graphify tidak punya informasinya" tanpa pernah memanggil tool Graphify di sesi tersebut.

### 3. Kapan Boleh Menggunakan Cara Lain (Fallback)
Pencarian konvensional (`grep_search`, `rg`, `find`, pembacaan file langsung) HANYA diperbolehkan jika:
1. Graphify sudah dicek dan hasilnya tidak mencukupi atau konsep belum terindeks — sebutkan query Graphify yang sudah dicoba, ATAU
2. Mencari string literal spesifik (misal: config key exact, regex token, pesan error exact, typo text) yang bukan merupakan simbol AST.

### 4. Pemeliharaan Graph
- Setelah memodifikasi atau menambah file kode, jalankan `graphify update .` untuk menjaga graph tetap mutakhir (hanya AST, cepat, tanpa biaya API).
