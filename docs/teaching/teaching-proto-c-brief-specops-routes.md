# 🎓 Modul Pembelajaran: Endpoint Brief & Spec-Ops, dan Tiga Bug yang Hanya Terlihat dengan Mata (C4, C6)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Endpoint baca-saja di belakang gerbang, perakit data murni, kontrak klien-server, fallback yang jujur, state Compose yang kunci `remember`-nya salah
> **Prasyarat**: [teaching-proto-c-handoff-pilot](teaching-proto-c-handoff-pilot.md), [teaching-proto-b-spec-ops-brief](teaching-proto-b-spec-ops-brief.md), [teaching-proto-a-interactive-ui](teaching-proto-a-interactive-ui.md)

## 1. Dua endpoint (kontrak §3.3 dan §3.3b)
| Endpoint | Isi | Sifat |
|---|---|---|
| `POST /api/builder/draft/brief` | body `{included, changes}` → `{markdown, brief}`; cakupan dan harga dari `PriceDiscoveryDraftUseCase.lines`, kebutuhan kustom otomatis dari modul yang belum ada di katalog | **tidak menulis**; log perubahan dikirim klien (state prototype = memori sesi) |
| `POST /api/builder/draft/spec-ops` | body `{message, screenId, spec}` → `{ops, reply}` lewat port `SpecOpProposer` (default deterministik) | **hanya usulan**; klien menerapkan lewat `SpecOpApplier` yang memvalidasi ulang |

Keduanya di belakang `gate()` builder: tanpa kredensial 401, peran tak berwenang 403 **sebelum body dibaca**. Brief: 404 tanpa draf (tidak membuat draf), 400 untuk modul asing atau entri perubahan rusak — **ditolak, tidak diabaikan diam-diam**. Kalimat yang tak dikenal pada spec-ops dijawab 200 dengan `ops` kosong dan `reply` berisi contoh: itu jawaban sah, bukan galat.

Perakitan brief dari draf ada di `core` (`RequirementsBriefAssembler`), murni dan deterministik, sehingga dites tanpa HTTP.

## 2. Tiga bug yang lolos semua test, ditemukan saat dilihat dengan mata
1. **Brief menulis "Perubahan klien: 0" padahal sudah ada perubahan.** `PrototypeSession` dibuat dengan `remember(currentScreens)`; setiap perubahan spec mengganti daftar layar, sehingga **sesi dibuat ulang** dan log perubahan, riwayat undo, serta posisi kartu hilang (tombol Undo selalu mati). *Kunci `remember` harus identitas yang stabil (id draf), bukan data yang berubah karena aksi pengguna.*
2. **Label dropdown "Layar:" kembali ke layar pertama** setelah edit — alasan yang sama: `remember(screens)` ter-reset. Diganti kunci berupa daftar id layar.
3. **Karakter kotak di brief** (`→`): font Nunito tidak punya glyph panah. Renderer memakai `->` ASCII; golden test diperbarui.

Perbaikan terkait: modul yang dilepas di panel harga kini memengaruhi dasbor lewat `PrototypeSession.includedModuleIds` (state reaktif), bukan lewat membuat sesi baru.

## 3. Fallback yang berbohong
Dialog brief punya jalur lokal bila server gagal. Versi awal memalsukan cakupan ("semua modul sudah ada, harga Rp 0") — tampak rapi tetapi menyesatkan. Kini jalur lokal **tidak mengklaim** cakupan dan menulis catatan eksplisit di Kebutuhan Kustom. *Fallback boleh menurunkan kelengkapan, tidak boleh mengarang fakta.*

## 4. Pembuktian
- `RequirementsBriefAssemblerTest` (4): modul terpilih saja, spec sama dengan prototype, urutan perubahan, modul belum-ada → kebutuhan kustom, deterministik.
- `BuilderBriefSpecOpRoutesTest` (10): 401/403/400/404, kalimat dikenal dan tak dikenal, **jalur sukses brief dengan cakupan katalog nyata** (hanya di database uji).
- Dilihat di `/builder/prototype`: chat edit menambah kolom Revisi, Undo menghapusnya, label layar bertahan, brief memuat "1 diterapkan", panah normal, dan tidak ada lagi 404 ke kedua endpoint.

## 5. Yang belum
- Adaptor LLM (Koog) di belakang `SpecOpProposer`: belum dibuat (opsional C6).
- **Blok Form tidak bisa didemokan**: pack mengizinkan satu usulan layar per modul, jadi layar FORM tak punya tempat selain menggantikan tabel. Perlu keputusan produk.
- Lebar kolom kanban 240dp masih menampilkan ±1,3 kolom di bingkai 360dp.
