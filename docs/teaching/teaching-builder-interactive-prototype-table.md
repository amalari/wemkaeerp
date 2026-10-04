# 🎓 Modul Pembelajaran: Blok Tabel Interaktif & Perapian Kanban (TRD-PLAT-003, tahap 2)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Blok data-driven, petunjuk perilaku dari pack, logika tampilan murni, Compose scroll horizontal
> **Prasyarat**: [teaching-builder-interactive-prototype-kanban.md](teaching-builder-interactive-prototype-kanban.md)

## 1. Masalah
Tabel prototype hanya gambar: sel terpotong ("PO-2026…") karena 5 kolom berbagi 360dp, tidak bisa dicari, diurut, atau diubah statusnya. Kartu kanban juga memotong judul, dan pesan penolakan memakai nama teknis ("'Kolom'").

## 2. Start dari mana
1. `TableConfig` + `ScreenSpec.table` (validasi di konstruktor: kolom harus field entitas, kolom status wajib ENUM).
2. `TableHints` di data pack (kolom status + opsi) dan `groupLabel` di `KanbanHints` (nama kolom status berbahasa pack).
3. `InteractiveScreenFactory.table` — baris lama berkunci sama ditafsirkan sebagai entitas; nilai status di luar opsi → `null` (jatuh ke gambar statis, tidak menebak).
4. `TableView` — sortir/filter **murni** di core, bisa dites tanpa UI.
5. Kabel: codec pack & codec layar, `WidgetRegistry.interactiveFor` melayani KANBAN dan TABLE.
6. UI: `InteractiveTableState` + `InteractiveTable`; `InteractiveBlock` memilih blok menurut widget.

## 3. Keputusan penting
- **Sortir/filter bukan mutasi.** Hanya cara melihat; store tak berubah. Ubah status tetap lewat `PrototypeReducer`, jadi opsi dan transisi dijaga spec.
- **Kolom lebar tetap + gulir ke samping** menggantikan `weight(1f)` + ellipsis: sel terbaca penuh. Header diketuk untuk mengurut.
- **Angka vs teks:** kolom diurut sebagai angka hanya bila *semua* nilainya angka murni. Nilai berunit ("420 kg") diurut sebagai teks — batasan yang disengaja dan terdokumentasi di `TableView`; kalau perlu, parser unit/format Indonesia jadi pekerjaan tersendiri.
- **Kanban:** judul/detail kartu boleh 3 baris; pesan penolakan memakai `groupLabel` dari pack.

## 4. Jebakan
1. Menyimpan hasil sortir ke store — membuat urutan "hilang" saat filter berubah.
2. Menaruh daftar opsi status di Composable — harus data pack.
3. Mengira angka berformat Indonesia ("5.000") bisa di-`toDouble()` — "5.000" terbaca 5,0 dan menipu.

## 5. Verifikasi
- Test core: `InteractiveTableTest` (status lewat reducer, opsi tak dikenal ditolak, baris tak seragam → null, sortir angka & filter, round-trip codec, tabel garment berhint interaktif).
- Dilihat dengan mata di `/builder/prototype`: kartu terbaca penuh, pencarian ("fleece"), urut kolom, menu "Ubah ke …" (Tersedia → Menipis) bekerja.
- Belum: lebar ~1280dp, tenant non-rajut, Android (CI).

## 6. Tantangan
- [ ] Tambahkan `transitions` pada `TableHints` BOM (Draft → Final satu arah) dan uji penolakannya.
- [ ] Parser angka berunit untuk `TableView` ("1.200 pcs" → 1200).
- [ ] Hint tabel untuk CRM (kolom Status bebas-teks perlu dirapikan dulu jadi opsi tertutup).
