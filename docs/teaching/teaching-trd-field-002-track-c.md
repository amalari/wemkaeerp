# 🎓 Modul Pembelajaran: Field `FILE` — Track C (UI) TRD-FIELD-002

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Clay Design System, tipe field FILE, unggah byte
> mentah, kontrak endpoint fail-closed, pemisahan komponen buta-domain vs pembungkus fitur
> **Prasyarat**: Membaca `docs/trd/TRD-FIELD-002-file.md`, `.claude/rules/design-system-rules.md`,
> `.claude/rules/field-component-rules.md`, dan `.claude/rules/module-integration-rules.md`
> **Referensi Task**: Track C TRD-FIELD-002 (branch worktree `track-c-file-ui`; A0 merge `b51e1ccc`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah nyata

Sebelum C8, tipe field `FILE` "ada" tapi hanya sebagai kolom teks. Nilai selnya adalah string
`fields/...`, dan user melihat path mentah seperti
`fields/ten-demo-001/crm_sales/lead-x/berkas_uji-a1b2c3-laporan-po.pdf`. Tidak ada tombol unggah,
tidak ada unduh, tidak ada batas ukuran, tidak ada pesan galat. Ini contoh klasik tipe field yang
**terdaftar di kosakata tetapi tidak punya kontrol** — persis penyakit yang mendasari
`field-component-rules.md`.

### Analogi

Bayangkan loker berkas di kantor. Yang kita inginkan:

- Loker menampilkan **nama berkasnya**, bukan kode barcode gudang lengkap.
- Ada tombol "pilih berkas" / "ganti"; loker lama tetap bisa dibuka (unduh) dan dikosongkan (hapus).
- Kalau berkas terlalu besar atau tipenya salah, petugas bilang **kenapa** dalam bahasa manusia —
  bukan menyebut "HTTP 415".

### Hasil akhir yang diharapkan

1. Sebuah komponen dasar `ClayFileField` yang **buta domain** (menerima String + lambda) dan
   berbahasa visual Clay.
2. Form (prototype & CRM) mengunggah lewat komponen itu; tabel/kanban menampilkan chip nama berkas
   + aksi unduh; byte selalu tinggal di ObjectStorage, **tidak pernah** di sel.
3. Galat server 413/415/503 dipetakan ke pesan manusiawi di satu tempat.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Urutan ini sengaja **dari lapisan yang paling tidak bergantung ke yang paling bergantung**:

```
Step 0  Baca kontrak §4.4 (endpoint) & FR-3/FR-6 — jangan mengarang bentuk request.
Step 1  Komponen dasar BUTA DOMAIN   → presentation/designsystem/ClayFileField.kt
Step 2  Helper bersama (murni)        → presentation/common/FileFieldSupport.kt
Step 3  Klien infrastruktur (I/O)     → infrastructure/api/FieldFileApiClient.kt (+ CRM)
Step 4  Glue fitur prototype          → presentation/discovery/fields/FileFieldOps.kt
Step 5  Sambungkan ke kontrol input   → FieldInput.kt (satu pintu FieldInput)
Step 6  Konteks padat                 → TableCell.kt / KanbanDetailDialog.kt
Step 7  Rantai CRM                    → ViewModel → Pane → DetailTab → LeadCustomField
Step 8  Tes murni + kompilasi 4 target
Step 9  Jalankan & LIHAT dengan mata
```

**Kenapa komponen dasar dulu?** Karena begitulah Rule of Three ditegakkan: kalau kita menulis
tombol unggah langsung di layar fitur, salinannya akan muncul di form, tabel, kanban, dan CRM —
dan tiap salinan akan menyimpang. Komponen netral diangkat lebih dulu, baru dipakai.

---

## 🔬 3. Pembedahan Kode Blok per Blok

### 3.1 `ClayFileField.kt` — komponen netral yang buta domain

```kotlin
sealed interface ClayFileFieldState {
    data object Idle : ClayFileFieldState
    data class Uploading(val progress: Float?) : ClayFileFieldState  // null = tak terukur
    data object Ready : ClayFileFieldState
    data class Error(val message: String) : ClayFileFieldState
}
```

Mental model: komponen **tidak tahu** apa itu `FileRef`, `LeadId`, atau modul. Ia hanya tahu
"ada nama berkas, ada status, ada aksi". Itu sebabnya ia menerima `String`, `Color`, dan lambda —
bukan `PipelineNode`/`FieldSpec`. Aturan `design-system-rules.md` Kontrak 6 mengunci ini, dan
skrip review di §5 mengeceknya (`grep import ...domain... designsystem/` harus kosong).

`Uploading(progress: Float?)`: `null` dipilih, bukan `LinearProgressIndicator` Material. Mengapa?
Komponen M3 membawa warna ungu default yang bocor, dan kita butuh bar bergaya Clay. Saat progres
belum bisa diukur dari transport (Track B/E2E menyusul), kita menampilkan segmen bergerak —
**bukan** angka persentase palsu yang menyesatkan.

```kotlin
Row(modifier = Modifier.weight(1f, fill = false).clayFlat(...)) { /* chip nama berkas */ }
```

Baris ini menjawab Kontrak 13 design system: elemen yang boleh mengalah wajib
`weight(1f, fill = false)` + `maxLines` + `overflow`. Tanpa itu, nama berkas panjang akan mendorong
tombol unduh/hapus keluar layar atau memecah teks satu huruf per baris — bug yang **hanya** muncul
saat dijalankan dengan data nyata.

### 3.2 `FileFieldSupport.kt` — helper murni & pemetaan galat

```kotlin
fun fileRefDisplayName(ref: String): String =
    if (ref.contains('/')) ref.substringAfterLast('/').ifBlank { ref } else ref
```

Kontrak R4 v1: kita hanya bisa menyimpulkan nama dari ref (segmen terakhir). **Tidak** ada
fallback senyap ke "id" yang mengarang; bentuk tak dikenal dikembalikan apa adanya. Resolusi
metadata (nama asli, ukuran) menyusul — dicatat di KDoc agar utang ini terlihat.

```kotlin
fun fieldFileErrorMessage(error: Throwable): String = when ((error as? FieldFileHttpException)?.status) { ... }
```

Ini inti FR-3 dipetakan ke bahasa manusia:
`413 → "Ukuran berkas melebihi batas 10 MB."`, `415 → tipe tidak didukung`, `503 → env S3 belum siap`.

**Jebakan yang dihindari:** memetakan dari **string** `error.message` (mis. mem-parsing
"HTTP 413"). Itu rapuh. Sebagai gantinya klien melempar `FieldFileHttpException(status, body)`
sehingga UI berpijak pada **status terketik**, bukan regex.

### 3.3 `FieldFileApiClient.kt` — I/O sesuai kontrak §4.4

```kotlin
httpClient.post(resolveUrl("$MODULE_RECORDS_BASE/$moduleCode/records/$recordId/fields/$fieldKey/upload")) {
    authorize()
    parameter("fileName", fileName)     // metadata di query
    parameter("contentType", contentType)
    contentType(ContentType.Application.OctetStream)
    setBody(bytes)                       // byte mentah di body
}
```

Pola ini **bukan hal baru** — ia meniru `DealApiClient.uploadPurchaseOrder` dan
`FulfillmentTransferApiClient.uploadEvidence` yang sudah ada. Request tunggal tanpa plugin
multipart. Menyalin pola yang terbukti lebih aman daripada mengarang mekanisme baru (aturan repo).

`FieldFileHttpException` didefinisikan di sini dan **dipakai ulang** oleh `CrmApiClient` — satu
bentuk galat untuk dua varian endpoint (module-records & CRM-leads) yang berbagi kontrak sama.

### 3.4 `FileFieldOps.kt` — glue prototype yang jujur soal batas

```kotlin
val upload: suspend (fieldKey, fileName, mimeType, bytes, onDone) -> Unit
```

Bentuk callback `onDone` mengikuti pola bukti fulfilment: host menjalankan coroutine, komponen UI
tetap murni. Yang penting di sini adalah **kejujurannya**:

> Kontrak §4.4 mengunci `recordId` di path. Artinya unggah pertama hanya mungkin untuk record yang
> **sudah ada** (dialog detail kanban / edit sel). Blok Form pembuatan dan baris inline belum punya
> id server, jadi `fileOps` bernilai `null` — dan UI menampilkan penjelasan, bukan pemilih palsu.

Ini penerapan `field-component-rules.md` Kontrak 8: **komponen yang belum bisa jangan dipalsukan**.

### 3.5 `FieldInput.kt` — satu pintu kontrol input

```kotlin
FieldType.FILE -> FileFieldInput(field, value, onValueChange, enabled, isError, fileOps)
```

`FieldInput` adalah satu pintu (Kontrak 3) untuk form blok, sel tabel, dan dialog kanban. Karena
itu cabang FILE dipasang **di sini**, bukan disalin ke tiap layar. Alur unggah:

```
pickLocalFile(FIELD_FILE_ACCEPT) → jaga 10 MB di klien → upload → sukses = onValueChange(ref)
```

Ukuran dijaga **dua kali**: di klien (pesan cepat, tanpa bolak-balik jaringan) dan di server
(413, fail-closed). Klien bukan pengaman; ia hanya rahmat pertama.

### 3.6 `TableCell.kt` — chip untuk konteks padat

Di mode baca, sel FILE menampilkan `ClayFileChip` (ikon + nama) dengan aksi unduh; bukan teks
mentah `fields/...`. Di mode edit, sel memakai `FieldInput` yang sama dengan `fileOps`. Perhatikan
bahwa **`displayValue` FILE di `NumberFormatting.kt` juga diubah** untuk mengembalikan segmen
terakhir, sehingga jalur tampil mana pun (kanban card, teks fallback) konsisten.

Gagal unduh tidak boleh senyap: `TableCell` mengisi `state.transientMessage`, yang dirender
`InteractiveTable` di samping `state.message`.

### 3.7 Rantai CRM

`CrmUiState` menambah dua event: `UploadFieldFile` dan `OpenFieldFile`. `CrmViewModel` menaati
gerbang tulis yang sudah ada (`canWrite`) sebelum memanggil klien — TAPI gerbang sahnya tetap di
server (OPERATE modul CRM, fail-closed). `LeadCustomField` merender `FileEditor` di atas
`ClayFileField`; `LeadFieldFileActions` diteruskan satu objek lewat
`Workspace → Board → Pane → DetailTab → LeadCustomField` agar tidak menambah dua parameter di
setiap lapis.

---

## 🏗️ 4. "Why" — Keputusan Arsitektur & Trade-off

| Keputusan | Alasan | Alternatif yang ditolak |
|---|---|---|
| Komponen netral di `designsystem/` | Sekali benar, dipakai 4 konteks; Rule of Three | Tombol unggah per layar → salinan menyimpang |
| `FieldFileHttpException(status)` | Pemetaan pesan berdasarkan data terketik | Parsing string "HTTP 413" → rapuh |
| Byte lewat body mentah (bukan multipart) | Mengikuti pola PO/mockup yang sudah ada; satu request | Plugin multipart baru → dependensi & divergensi |
| `progress: Float?` (null = aktifitas) | Jujur soal kemampuan transport saat ini | Angka persentase palsu → menyesatkan |
| `fileOps = null` di form pembuatan | Endpoint butuh `recordId`; jangan palsukan | Memunculkan picker yang pasti gagal |
| Helper murni di `presentation/common/` | Dipakai prototype & CRM; tanpa Compose → bisa diuji unit | Menyalin di dua fitur |

---

## 🪤 5. Jebakan Umum yang Dihindari

1. **Literal warna / `Modifier.shadow` / `Card` mentah.** Semua gaya lewat token Clay
   (`WeMadeColors`, `ClayShapes`, `ClaySpacing`, `clayFlat`).
2. **`else ->` pada `when (FieldType)`.** Cabang FILE harus eksplisit di setiap `when`
   (kompilator yang mengingatkan titik pendaftaran).
3. **Nama berkas hilang di web.** Picker `pickLocalFile` versi Wasm/JS dulu menyintesis
   `design-mockup.png`. Untuk field FILE, nama adalah data user → actual web diperbaiki agar
   meneruskan `file.name` asli (perubahan minimal, additif bagi pemanggil lama).
4. **Menampilkan path `fields/...`.** Nama tampil = segmen terakhir; path tidak pernah ke layar.
5. **Kontrak endpoint diubah sendiri.** §4.4 terkunci; Track C hanya memanggil.

---

## ✅ 6. Verifikasi

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:jvmTest
```

Lalu **dijalankan dan dilihat**: server (`PORT=8091`) + web dev (`WEMADE_API_PORT=8091`),
login demo superadmin, buka CRM → lead → "Lihat Semua Properti" → tiap state:

- **Idle**: tombol "Pilih Berkas" + hint "Maks 10 MB · PDF, gambar, TXT, CSV".
- **Ready**: chip nama berkas + ikon unduh + ikon hapus.
- **Error**: outline chip merah + pesan "Data atau berkas tidak ditemukan — mungkin sudah dihapus."
  (404 dari server karena endpoint Track B belum merge — justru membuktikan pemetaan galat).

---

## 🔭 7. Tindak Lanjut (dicatat, bukan dikerjakan di sini)

- **R4 Track C lanjutan**: resolusi metadata nama/ukuran asli (sidecar/route meta) — saat ini nama
  = segmen terakhir ref.
- **Track B**: endpoint §4.4 (upload/download module-records & CRM) + ObjectStorage/MinIO.
  Integrasi end-to-end (unggah nyata + presigned) dikerjakan pasca-merge; UI v1 sudah siap.
- **Progres determinate**: saat transport melaporkan byte terkirim (`HttpRequestBuilder.onUpload`),
  isi `Uploading(progress)` dengan angka; komponen sudah mendukung keduanya.
- **Form pembuatan**: bila kelak kontrak membolehkan unggah sebelum record ber-id (mis. staging
  object), blok Form/baris inline tinggal diberi `fileOps`.
