# 🎓 Modul Pembelajaran: Quick-Add Desain Sampling, Detail Fee/Catatan & Popup Revisi

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform Dialog, MVI Event Flow, Full-Stack field propagation (UI → ViewModel → API → Route → UseCase → Domain)
> **Prasyarat**: Memahami alur `DealUiEvent` → `DealViewModel` → `DealApiClient` → `DealRoutes` → `CreateSamplingOrderFromDealUseCase`

---

## 💡 1. Konsep Dasar & Masalah Nyata

**Masalah**: Admin menambah desain sampling harus mengisi form nama dulu, padahal kode desain
(`DSG-01`, `DSG-02`, …) sebenarnya sudah cukup sebagai identitas awal. Field Sampling Fee dan
catatan (penempatan bahan dll) tidak ada di kartu. Catatan revisi buyer diisi di textfield
satu baris yang mudah terlewat.

**Prinsip yang dipakai**: *"Identitas teknis ≠ nama bisnis."* Kode `DSG-XX` adalah identitas
yang boleh ditentukan mesin; nama ("Polo Navy Classic") adalah keputusan manusia yang boleh
ditunda. Jangan minta manusia mengisi yang bisa digenerate.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Full-stack change kecil pun harus menyusuri 5 lapis, dari dalam ke luar:

1. **Domain dulu** (`core/.../CreateSamplingOrderFromDealUseCase.kt`) — karena paling murah
   diuji dan mendefinisikan kontrak: jalur update kini juga menyimpan `styleName` (rename),
   dan `notes` dikirim langsung (bisa dikosongkan).
2. **Route server** — tidak berubah! `DealRoutes` sudah mem-parse `styleName`,
   `samplingFeeIdr`, `notes` sejak awal. Ini bukti manfaat Command pattern: field baru di
   command lama langsung terpakai.
3. **DTO & client** — `SaveSamplingOrderFromDealRequest` juga sudah punya semuanya. Nol perubahan.
4. **ViewModel & Event** — `DealUiEvent.SaveSamplingOrder` sudah membawa semua field. Nol perubahan.
5. **UI** (`DealDetailDialog.kt`) — satu-satunya lapisan yang banyak disentuh.

**Pelajaran besar**: ketika kontrak data sudah dirancang benar di awal (semua field ada di
Command/DTO), perubahan UX sebesar apa pun sering kali hanya menyentuh presentation.

---

## 🔬 3. Bedah Kode Blok per Blok

### Blok A — Use case: rename di jalur update

```kotlin
if (existingId != null) {
    // styleName boleh kosong (berarti tidak di-rename)
    ...
    .copy(
        styleName = command.styleName.trim().ifBlank { existing.styleName },
        notes = command.notes,   // tanpa fallback ifBlank → catatan bisa dikosongkan
        ...
    )
}
require(command.styleName.isNotBlank())  // pindah ke jalur create saja
```

**Mental model**: validasi wajib dibedakan per jalur. `styleName` wajib saat *menciptakan*
identitas (tidak boleh lahir tanpa nama), tapi opsional saat *memperbarui*. Jebakan klasik:
satu `require` di atas fungsi untuk dua jalur berbeda semantik.

### Blok B — Quick-add tanpa form (UI)

```kotlin
ClayButton(
    text = "Tambah Desain Baru",
    onClick = {
        onEvent(DealUiEvent.SaveSamplingOrder(
            samplingOrderId = null,
            styleName = nextDesignCode(state.samplingOrders), // "DSG-04"
            ...
        ))
    }
)
```

Kode berikutnya dihitung dari ukuran daftar, dan header kartu cerdas:

```kotlin
text = if (order.styleName == designCode) designCode else "$designCode: ${order.styleName}"
```

Selama nama belum di-rename (= masih sama dengan kodenya), kartu cukup menampilkan `DSG-04`
saja — tidak ada label ganda "DSG-04: DSG-04" yang jelek.

### Blok C — Dialog revisi sebagai "moment of intent"

Input inline dicopot, diganti `RevisionNotesDialog`:

```kotlin
ClayButton(text = "Ajukan Revisi", onClick = { isRevisionDialogOpen = true }, ...)
...
Dialog(onDismissRequest = onDismiss) {
    ClayCard(...) {
        ClayTextField(..., singleLine = false, minLines = 4)  // textarea
        ClayButton("Ajukan Revisi", enabled = notes.isNotBlank(), ...)
    }
}
```

**Kenapa popup lebih baik daripada inline?** (1) Revisi adalah aksi berdampak tinggi —
memunculkan dialog memaksa pengguna *sengaja* mengisi; (2) textarea multi-baris memberi ruang
untuk alasan yang layak dibaca tim sampling; (3) state form tidak menumpuk di kartu yang sudah
padat. `enabled = notes.isNotBlank()` adalah validasi gerbang: revisi tanpa alasan ditolak UI.

### Blok D — Autosave, POST/PUT yang spesifik, dan merge state

Autosave punya **dua pemicu** (commit rename via ikon pensil + debounce 800ms fee/catatan) yang
bisa nyaris bersamaan. Tiga lapis pertahanannya:

1. **Semantik HTTP dipisah**: create = `POST /deals/{id}/sampling-orders` (tanpa id; body yang
   membawa `samplingOrderId` ditolak 400), update = `PUT /deals/{id}/sampling-orders/{samplingId}`
   (id lewat **path**). Klien memecah `saveSamplingOrderFromDeal` menjadi
   `createSamplingOrderFromDeal` + `updateSamplingOrderFromDeal` — mustahil payload update yang
   salah id diam-diam membuat lembar baru.
2. **Serialisasi di ViewModel**: `samplingSaveMutex` menyatukan dua PUT yang balapan — tanpa itu,
   dua *read-modify-write* bersamaan saling menimpa (*lost update*).
3. **Merge yang menjaga posisi**: response disimpan dengan ganti-di-tempat
   (`replaceOrAppendById`), BUKAN `filterNot + saved` yang memindahkan kartu ke akhir list —
   kode desain (`DSG-01/02/…`) dihitung dari posisi kartu, jadi merge sembarangan membuat kode
   saling bertukar di layar.

Semua penulis tetap mengirim **snapshot field terkini** (`styleNameInput`, `feeInput`,
`notesInput`), bukan nilai `order.*` lama.

---

## 🪤 5. Jebakan Pemula (Common Pitfalls)

1. **Satu `require` untuk dua jalur** — validasi create dipakai paksa ke update (atau
   sebaliknya). Pisahkan validasi per semantik jalur.
2. **Fallback `.ifBlank { existing.x }` untuk field yang harus bisa dikosongkan** — catatan
   yang dihapus admin akan "hidup lagi" diam-diam. Fallback hanya untuk field yang memang
   tidak boleh kosong (seperti `styleName`).
3. **Auto-code lalu menampilkan "DSG-04: DSG-04"** — selalu bandingkan nama dengan kode; kalau
   identik, tampilkan satu saja.
4. **Form baru lupa membersihkan state lama** — saat menghapus form, hapus juga variabel
   `remember`-nya; variabel mati di Compose tidak error tapi jadi sampah pembacaan.
5. **Dialog composed di dalam list tanpa state per item** — kunci state dialog dengan
   `remember(order.id)` supaya tiap kartu punya popup-nya sendiri dan tidak saling menerawang.
6. **Create & update berbagi satu endpoint POST** — id lewat body membuat server tidak bisa
   membedakan niat; salah kirim id = record baru diam-diam. Pisahkan POST (create) dan PUT
   dengan id di path.
7. **Merge list `filterNot { it.id == saved.id } + saved`** — memindahkan item yang disave ke
   akhir list. Fatal jika UI menurunkan identitas dari posisi (kode `DSG-01/02/…`): kode
   saling bertukar tiap save.
8. **Dua autosave balapan tanpa mutex** — rename dan debounce fee/catatan menyentuh record
   yang sama; bungkus network call dengan `Mutex` di ViewModel.

---

## ✅ 6. Verifikasi yang Dilakukan

- `./gradlew :server:compileKotlin :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:jvmTest` — BUILD SUCCESSFUL.
- Server Ktor direstart (wajib! perubahan route/domain tidak ter-pick oleh server yang sedang
  jalan); watcher Wasm tetap hidup di `:3000`.
- **Uji REST end-to-end via curl (terverifikasi):**
  1. `POST .../sampling-orders` dengan `samplingOrderId` di body → **400** dengan pesan
     arahan ke PUT. ✔
  2. `POST .../sampling-orders` tanpa id → **200**, id baru `SPK-SMP-0003` dibuat. ✔
  3. `PUT .../sampling-orders/smp_..._SPKSMP0001` → **200**, styleName/fee/notes ter-update
     di record yang tepat. ✔
  4. `GET` final → urutan & isi ketiga lembar sampling konsisten. ✔

**Uji manual yang disarankan** (butuh login admin di browser):
1. Buka deal → Tab Siklus Sampling → "Tambah Desain Baru" → kartu baru dengan kode berikutnya.
2. Rename via ikon pensil → autosave; edit Sampling Fee + Catatan → autosave 800ms; pastikan
   **kode kartu (DSG-01/02/…) tidak bertukar** setelah setiap save.
3. Klik "Ajukan Revisi" → popup muncul → kosong = tombol kirim mati; kirim → badge REVISION.
4. Di Network tab browser: create harus `POST`, semua pembaruan harus `PUT .../{samplingId}`.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Saat ini jumlah sampel desain baru selalu 2. Tambahkan selector qty
      (1/2/3) di *dalam kartu* (bukan di form tambah) yang tersimpan lewat event yang sama.
- [ ] **Tantangan 2**: Formatting "Rp 350.000" pada field Sampling Fee (tampil berformat,
      tersimpan digit) — hati-hati posisi kursor di `BasicTextField`.
- [ ] **Tantangan 3**: Catat riwayat revisi (`revisionCount` + `accNotes`) sebagai daftar
      berlambar, bukan menimpa satu string — apakah skema DB & codec perlu berubah?

---

## 🔄 Iterasi Lanjutan: Layout Anti-"Ompong", Cropper Kotak, & Selector Riwayat Revisi

> Tantangan 3 di atas SUDAH dijawab di iterasi ini — lihat blok E dan F.

### Blok E — Kartu ter-expand = satu baris penuh (layout hoisting)

Dulu semua kartu dipaksa `chunked(2)` dua kolom. Saat satu kartu ter-expand, tinggi barisnya
mengikuti kartu tertinggi — kartu lipat di sebelahnya jadi "ompong" menggantung di atas ruang
kosong. Solusinya dua lapis:

1. **State expand diangkat** ke `SamplingTabContent` (`expandedOverride: Map<String, Boolean>`)
   — layout HARUS tahu kartu mana yang ter-expand, padahal dulu state itu terkurung di dalam
   `SamplingDesignCard`.
2. **Loop manual berbasis kursor** menggantikan `chunked(2)`: kartu ter-expand dirender
   `fillMaxWidth()` sendirian; kartu lipat dipasangkan hanya dengan kartu lipat berikutnya.
   Chevrons kini membawa `onToggleExpanded` dari parent.

### Blok F — Riwayat revisi per nomor (menjawab Tantangan 3)

Sebelumnya `accNotes` ditimpa setiap revisi — feedback Rev 1 hilang begitu Rev 2 diajukan.
Kini:

1. **Domain**: `RevisionFeedback(revision, notes, at)` + `SamplingOrder.revisionHistory`;
   `requestRevision()` mengarsipkan feedback per nomor revisi (dan tetap menulis `accNotes`
   demi kompatibilitas pembaca lama).
2. **Persistensi**: kolom jsonb `sampling_orders.revision_history` (migrasi `V39__sampling_revision_history.sql`)
   — dipilih jsonb karena datanya dokumen kecil read-mostly, bukan relasi yang perlu di-query
   terbalik.
3. **UI**: badge hanya "Perlu Revisi" (tanpa "(Rev N)"); pil header jadi **selector** — klik
   Rev 1 / Rev 2 untuk membuka arsip feedback revisi itu (`feedbackFor()` dengan fallback
   `accNotes` untuk data lama yang belum punya history).

### Blok G — Cropper kotak (`MockupCropDialog.kt`)

Foto mockup kini WAJIB lewat cropper 1:1 sebelum upload, jadi slot foto kartu seragam kotak:

- **Picker pindah ke UI** (`pickLocalFile` dipanggil dari kartu, bukan ViewModel) — bytes hasil
  crop dilewatkan lewat `DealUiEvent.UploadSamplingMockup(samplingId, fileName, mimeType, bytes)`.
- **Geometri crop**: skala display `f = cover × zoom`; offset di-clamp agar gambar selalu
  menutup viewport persegi; region sumber = persegi `previewPx / f` pada koordinat citra.
  Konfirmasi memotong via Skia (`Canvas.drawImageRect` + `Surface.makeImageSnapshot()
  .encodeToData(PNG)`) menjadi PNG 1024×1024.
- **Jebakan skiko 0.144.6**: `drawImageRect` 4-arg ada tapi sampling lewat overload
  6-arg (`SamplingMode.LINEAR`); `FilterQuality` BUKAN `SamplingMode`; `encodeToData`
  bernilai nullable di Kotlin.

**Verifikasi iterasi ini**: `:server:compileKotlin`, `:app:shared:compileKotlinJvm`,
`:app:shared:compileKotlinWasmJs`, `:app:shared:jvmTest` — BUILD SUCCESSFUL; migrasi v39
ter-apply; uji API: dua kali "Ajukan Revisi" pada satu desain → `revisionHistory` berisi 2
entri dengan notes berbeda, `revCount = 2`. Uji manual browser: expand kartu samping tidak
lagi membuat kartu lipat "ompong"; upload foto memunculkan dialog cropper; klik pil Rev 1
memunculkan arsip feedback revisi 1.

