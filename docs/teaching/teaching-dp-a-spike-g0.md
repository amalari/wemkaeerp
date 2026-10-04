# Laporan Spike A0 (Gelombang 0): UI & State Blok (Tabel Inline, Kanban Kaya, Port Data)

**Dokumen:** Mentoring & Spike Arsitektural A0  
**Jalur:** Agent A (UI & State Blok)  
**Cabang:** `feat/dp-a-ui-rich-blocks` (Worktree: `../wemkaeerp-wt-dpa`)  
**Induk Rencana:** `docs/plannings/parallel2/PLAN-dp-A-ui.md` & `PLAN-prototype-data-port-rich-blocks.md`  

---

## 1. Ringkasan Eksekutif Spike A0

Dalam Gelombang 0 (G0), Agent A bertugas mengevaluasi kelayakan teknis implementasi Compose Multiplatform untuk tiga titik kritis antarmuka sebelum kode controller dan komponen visual ditulis:
1. **Input baris tabel inline** pada kontainer yang digulir ke samping (`Modifier.horizontalScroll`, lebar kolom tetap 118dp) tanpa merusak fokus atau scroll.
2. **Form detail kartu kanban** dalam dialog pada wadah yang adaptif terhadap tinggi terbatas maupun tak terbatas, tanpa konflik gestur *drag & drop*.
3. **Siklus hidup `PrototypeSession` dan pemilihan port** dari `DataBinding` agar sesi tidak dibuat ulang (*recreated*) saat spesifikasi layar dimutasi melalui *chat edit* (menghindari jebakan kunci `remember`).

Seluruh investigasi kode telah diselesaikan terhadap basis kode `main` (`595cf80`), dan solusi arsitektural telah diputuskan secara definitif di bawah ini.

---

## 2. Bedah Keputusan Teknis Spike A0

### 2.1 Spike 1: Input Baris Inline pada Tabel Bergulir Horizontal (118dp)

#### Tantangan Arsitektural:
Tabel prototipe (`InteractiveTable.kt`) menggunakan `ColumnWidth = 118.dp` di dalam kontainer `horizontalScroll(rememberScrollState())`. Isu yang harus diantisipasi adalah:
- Apakah input teks (`ClayTextField`) muat di dalam 118dp?
- Apakah fokus keyboard berpindah-pindah atau scroll melompat saat pengguna mengetik (*recomposition churn*)?
- Bagaimana navigasi keyboard antar kolom (Tab, Enter untuk simpan, Esc untuk batal)?

#### Temuan & Keputusan:
1. **Mekanisme Auto-Scroll Compose (`BringIntoViewRequester`)**:
   Compose Multiplatform secara *built-in* memiliki integrasi antara `FocusRequester` dan `ScrollState`. Ketika suatu sel input memperoleh fokus (misalnya pengguna menekan tombol Tab), kontainer `horizontalScroll` secara otomatis menggulir kolom tersebut ke dalam *viewport*.
2. **Desain `InlineRowEditor` untuk `TableConfig.inlineCreate`**:
   - Baris input diletakkan di baris teratas data (`visibleRows`).
   - Setiap kolom dialokasikan lebar tetap `118.dp` (konsisten dengan baris data).
   - State draf isian baris (`draftValues: MutableMap<String, String>`) dipegang secara lokal pada tingkat baris (`remember { mutableStateMapOf() }`), bukan diangkat ke level screen setiap ketukan tombol. Ini mencegah rekomposisi seluruh tabel saat pengguna mengetik.
   - Kolom aksi di ujung kanan baris memuat tombol ringkas: **Simpan** (`Enter`) dan **Batal** (`Esc`).
   - Bila validasi gagal, baris isian tetap terbuka dan pesan galat spesifik ditampilkan tepat di bawah baris atau via tooltip.
3. **Editor Sel In-Place (`editableFields`) & Fallback**:
   - Untuk kolom teks pendek, angka, atau tanggal: ketuk sel mengaktifkan mode edit sel in-place dengan `FocusRequester.requestFocus()` di dalam `LaunchedEffect(Unit)`.
   - Untuk kolom berstatus/tipe ENUM: tidak menggunakan teks bebas, melainkan dropdown/menu opsi (mengikuti pola `StatusCell` yang sudah ada menggunakan `DropdownMenu`).
   - **Fallback Editor Sel**: Jika teks melebihi kapasitas visual 118dp atau memerlukan input multi-baris/keterangan panjang, sistem menyediakan fallback popover/dialog editor sel mini agar pengguna tidak terpotong saat mengedit konten panjang.

---

### 2.2 Spike 2: Form Detail Kartu Kanban & Bebas Konflik Drag vs Tap

#### Tantangan Arsitektural:
- Wadah kanban dapat berada pada kontainer dengan tinggi terbatas (layar penuh) maupun tinggi tak terbatas (misalnya di dalam kartu bingkai prototipe yang bisa digulir).
- Dialog detail kartu tidak boleh terpotong (*clipped*) oleh batas kolom kanban.
- Gestur mengetuk kartu (*tap to open detail*) tidak boleh bertabrakan dengan gestur menyeret kartu (*drag to move column*).

#### Temuan & Keputusan:
1. **Lapisan Overlay Dialog Terpisah**:
   Komponen `AlertDialog` di Compose Multiplatform dirender pada layer *window/popup overlay* di tingkat *root view* (bukan di dalam hierarki kolom lokal). Karena itu, dialog detail kartu **secara inheren tidak akan terpotong** oleh kontainer kolom kanban.
2. **Penyusunan Konten `KanbanDetailDialog`**:
   - Di dalam dialog, form detail kartu diisi oleh `detailForm.fields` yang dirender menggunakan komponen bersama `FieldInput`.
   - Konten form dibungkus dalam kontainer `Modifier.verticalScroll(rememberScrollState())` dengan batas tinggi maksimum (adaptif terhadap layar ponsel 360dp maupun desktop 1280dp).
   - Dialog menyediakan 4 aksi: **Simpan**, **Pindah ke Kolom…**, **Hapus**, dan **Tutup**.
3. **Separasi Gestur Drag vs Tap**:
   Komponen `ClayKanbanBoard.kt` (baris 260–285) telah memisahkan gestur dengan bersih:
   - `detectTapGestures(onTap = { onCardClick(item) }, onLongPress = { menuOpen = true })`
   - `detectDragGestures(onDragStart = { ... }, onDrag = { ... })`
   Dengan mengoper `onCardClick = { card -> state.openDetail(card) }`, ketukan langsung membuka dialog detail tanpa memicu *drag*, sedangkan tarikan pointer langsung menginisiasi *drag & drop* mengambang (*floating overlay*).

---

### 2.3 Spike 3: Manajemen Siklus Hidup `PrototypeSession` & Port Binding

#### Tantangan Arsitektural:
Pada iterasi sebelumnya, `session` pernah dibuat ulang setiap kali spesifikasi layar berubah (*chat edit*), yang menyebabkan data di memori terhapus, tumpukan *undo/redo* hilang, dan log tangkapan (*capture log*) ter-reset. Bagaimana memastikan `PrototypeSession` memilih port dari `binding` tanpa dibuat ulang saat spec berubah?

#### Temuan & Keputusan:
1. **Kunci `remember` Harus Stabil**:
   - Kunci `remember` untuk `PrototypeSession` wajib menggunakan **`draft.id`**, **BUKAN** `draft.screens` atau daftar objek layar yang bermutasi.
   - Di `PrototypeRenderer.kt` dan `BuilderDesignPanes.kt`, parameter default sesi harus dijamin tidak mengikat pada referensi list layar yang berubah setiap operasi edit.
2. **Pemilihan Port dari `DataBinding`**:
   - `PrototypeSession` menyimpan registry port per layar: `portsByScreen: MutableMap<String, BlockDataPort>`.
   - Saat inisialisasi awal, sesi membaca `screen.binding`:
     - `DataBinding.Memory` -> membuat `InMemoryBlockDataPort(spec, entityId, seed)`.
     - `DataBinding.Api(basePath)` -> membuat `ApiBlockDataPort(basePath)` via fabrik port.
3. **Pembaruan Spec Tanpa Mengganti Port (`updateScreenSpec`)**:
   - Ketika chat edit memicu pembaruan spesifikasi layar melalui `session.updateScreenSpec(screenId, oldScreen, updatedScreen)`, controller memperbarui referensi `PrototypeSpec`, namun **mempertahankan instance port yang sama**.
   - Dengan demikian, baris data yang sudah ada di memori/server tidak hilang, koneksi tetap persisten, dan *undo stack* tetap dapat memulihkan konfigurasi sebelumnya.

---

## 3. Rencana Kerja Berikutnya (Menunggu B0)

Berdasarkan kesepakatan koordinasi antar-agent:
1. **A0 (Spike G0)** selesai.
2. **Menunggu Kontrak B0** dimerge ke branch `main`:
   - `BlockDataPort` & `PortError` (core)
   - `DataBinding` (`Memory` & `Api`)
   - Perluasan `KanbanConfig` (`card`, `columnMeta`, `detailForm`)
   - Perluasan `TableConfig` (`inlineCreate`, `editableFields`)
3. Begitu B0 masuk ke `main`:
   - Rebase `feat/dp-a-ui-rich-blocks` ke `main`.
   - Mulai implementasi **A1** (`BlockDataController.kt` dengan pengujian port gagal/lambat di `jvmTest`).
   - Lanjut ke **A2** (integrasi controller ke `InteractiveKanbanState`, `InteractiveTableState`, dll.), **A3** (`FieldInput.kt`), **A4** (tabel inline), **A5** (kanban kaya + dialog detail), **A6**, dan **A7** (verifikasi visual di browser).
