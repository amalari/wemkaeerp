# Teaching: Validasi Gerbang ACC Desain Sampling di CRM Deal

> **Level Target**: Junior to Mid Fullstack Kotlin Multiplatform Developer
> **Topik Utama**: Domain Validation Gate, Defense in Depth (UI + Server), Value dari Pure Function, MVI State Derivation, Carry-over Revisi
> **Referensi Task**: Validasi form sampling di `http://localhost:3000/crm-sales/deals` → modal Deal Detail, Tab Siklus Sampling
> **Prasyarat**: Paham struktur DDD `core/` vs `app/shared/`, pola MVI (`UiState`/`UiEvent`), dan unit test murni Kotlin

---

## 1. Start dari Mana? (Order of Operations)

Urutan menulis dari nol — **domain dulu, UI terakhir**:

```
1. core/  SamplingSizeMatrix.kt      ← firstCompleteSizeColumn()  (fungsi murni)
2. core/  SamplingOrder.kt           ← missingApprovalRequirements() + isReadyForAcc
3. core/  ApproveSamplingFromDealUseCase.kt ← guard server-side
4. core/  commonTest/                ← unit test murni (paling murah, paling cepat)
5. app/shared DealDetailDialog.kt    ← disable tombol + callout + fee default 0
6. Kompilasi multi-target + jalankan test
```

**Kenapa domain dulu?** Kalau aturan bisnis ("kapan boleh ACC?") hidup di UI, dia menyebar ke
composable yang sulit dites. Kalau dia fungsi murni di `core/`, dia bisa dites tanpa Compose,
tanpa server, tanpa DB — dan UI + server sama-sama memakainya (satu sumber kebenaran).

## 2. Bedah Kode Blok per Blok

### 2.1 `firstCompleteSizeColumn` — definisi "ukuran lengkap"

```kotlin
fun firstCompleteSizeColumn(matrix: List<SizeChartRow>): String? {
    val qtyRow = matrix.firstOrNull { it.isQtyRow }
    return STANDARD_SAMPLING_SIZE_COLUMNS.firstOrNull { col ->
        val qty = qtyRow?.values?.get(col)?.trim()?.toIntOrNull() ?: 0
        isSizeColumnActive(matrix, col) && qty >= 1
    }
}
```

- **Mental model**: satu kolom ukuran (mis. "S") dianggap *siap dipesan* hanya jika SEMUA baris
  POM (Point of Measurement) terisi **dan** alokasi qty-nya ≥ 1. Ini menjawab requirement
  *"misal ada 2 row itu harus keisi"* — `isSizeColumnActive()` yang lama sudah mengimplementasikan
  "semua row harus terisi", kita tinggal menambahkan syarat qty.
- Return `String?` (bukan Boolean) supaya bisa dipakai untuk pesan error yang spesifik di masa depan
  ("ukuran S sudah lengkap, M belum").

### 2.2 `missingApprovalRequirements` — aturan sebagai DATA, bukan boolean

```kotlin
fun missingApprovalRequirements(sizeMatrix: List<SizeChartRow> = this.sizeMatrix): List<String> = buildList {
    if (mockupFrontKey.isNullOrBlank()) add("Foto mockup Tampak Depan wajib diunggah ...")
    if (firstCompleteSizeColumn(sizeMatrix) == null) add("Size chart wajib punya minimal 1 ukuran ...")
    if (calculateTotalSampleQuantity(sizeMatrix) < 1) add("Jumlah sampel minimal 1 pcs.")
}
```

- **Kenapa `List<String>` bukan `Boolean`?** Boolean cuma bisa bilang "gagal". List pesan langsung
  jadi checklist UI (callout merah di atas tombol ACC) tanpa mapping kedua. Satu fungsi, dua pemakai.
- **Parameter `sizeMatrix` opsional**: UI mengirim `sizeMatrixInput` (nilai yang baru diketik user,
  mungkin belum ter-autosave 800ms) — validasi harus mengikuti *kondisi layar*, bukan kondisi DB.
  Default `this.sizeMatrix` menjaga pemanggil server tetap satu argumen.

### 2.3 Guard server-side — Defense in Depth

```kotlin
if (command.isApproved) {
    val issues = existing.missingApprovalRequirements()
    require(issues.isEmpty()) { "Desain belum memenuhi syarat ACC: ${issues.joinToString(" ")}" }
}
```

- Disable tombol di UI itu **UX**, bukan **keamanan**. Request bisa dikirim via curl/Postman;
  server wajib menolak juga. Pola ini disebut *defense in depth*: lapisan UI + lapisan use case.
- `require()` melempar `IllegalArgumentException` → route sudah punya handler
  `onFailure { respondFailure(BadRequest) }` — nol perubahan route.

### 2.4 UI — derive state, jangan simpan state

```kotlin
val approvalIssues = if (isHistoricRevision || order.isAccApproved || order.status == SamplingStatus.CANCELLED) {
    emptyList()
} else {
    order.missingApprovalRequirements(sizeMatrixInput)
}
```

- **Mental model MVI**: validasi BUKAN state yang disimpan (`mutableStateOf`), melainkan
  **turunan** dari state lain — dihitung ulang setiap recomposition. Kalau disimpan, kita harus
  men-sync-nya di 5 tempat (ketik matrix, upload foto, autosave selesai, ganti revisi, …) dan
  pasti ada satu yang kelupaan → bug "tombol nyala-nyala mati".
- Tombol ACC: `enabled = approvalIssues.isEmpty()`. Callout `ApprovalRequirementsCallout` hanya
  muncul saat list tidak kosong — user tahu *apa yang kurang*, bukan cuma "tombolnya mati".

### 2.5 Sampling Fee default 0 yang tidak bisa dihapus

```kotlin
var feeInput by remember(order.id) { mutableStateOf(order.samplingFeeIdr.toString()) }
// ...
feeInput = input.filter { it.isDigit() }.ifBlank { "0" }
```

- Triknya: `ifBlank { "0" }`. User menghapus semua digit → value kembali "0". Field **tidak pernah
  kosong**, dan `toLongOrNull() ?: 0L` di jalur autosave tetap aman sebagai jaring pengaman kedua.
- Ini perbaikan klasik *stringly-typed input*: jangan validasi "jangan kosong" saat submit;
  **buat mustahil kosong**.

### 2.6 Carry-over revisi — sudah ada, kita bikin *terlihat*

`SamplingOrder.requestRevision()` TIDAK menghapus apa pun: dia mengarsipkan snapshot lama ke
`revisionHistory` dan order tetap membawa mockup/matrix/fee/notes ke revisi berikutnya. Yang kita
tambahkan hanyalah banner `RevisionCarryOverCallout` agar admin *tahu* datanya sudah ditarik dan
cukup mengubah bagian yang perlu. **Pelajaran**: sebelum menulis fitur, cek dulu apakah domainnya
sudah melakukannya — sering kali yang kurang hanya *affordance*-nya.

## 3. Technology & Approach ("The Why")

| Keputusan | Kenapa | Risiko kalau cara lain |
|---|---|---|
| Validasi di `core/` (pure Kotlin) | Bisa dipakai UI *dan* server; dites tanpa framework | Validasi dobel yang drift: UI boleh, server tolak (atau sebaliknya) |
| `List<String>` issues | Checklist error gratis untuk UI | Boolean memaksa duplikasi logika pesan di Composable |
| Derive di recomposition | Selalu konsisten dengan input terkini | State ter-sync manual = sumber bug klasik |
| `ifBlank { "0" }` di input fee | Mustahil invalid, bukan validasi saat submit | Pesan error saat submit = UX buruk + kode validasi tambahan |
| Snapshot arsip revisi (sudah ada) | Audit trail "kenapa desain berubah" antar revisi | Menimpa data lama = kehilangan bukti komplain buyer |

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menaruh validasi ACC hanya di `enabled =` tombol.** Itu bukan gerbang — request tetap bisa
   dipalsukan. Selalu pasang guard di use case server.
2. **Memvalidasi dari `order.sizeMatrix` di UI.** Autosave debounce 800ms berarti DB *telat*
   dari layar. Gunakan nilai input live (`sizeMatrixInput`).
3. **Lupa mengecualikan kartu arsip/ACC/cancelled.** Kartu historik (Rev lampau, read-only) tidak
   boleh kena validasi — foto lama mungkin sudah tidak memenuhi syarat baru, itu bukan urusan user.
4. **Ekspektasi test "1 issue" padahal issue saling berkaskade.** Qty 0 → tidak ada ukuran lengkap
   → DUA issue muncul. Issue validasi sering bukan ortogonal; assert dengan `any { it.contains(...) }`.
5. **Menanam warna literal `Color(0xFF...)` untuk callout baru.** Selalu token: `WeMadeColors.Error/
   Primary.copy(alpha = …)` + `clayFlat` — sesuai §12 Design System.

## 5. Verifikasi & Tantangan Mandiri

**Cara menguji kebenarannya (sudah dijalankan):**

```bash
./gradlew :core:jvmTest --tests 'com.eventverse.app.domain.sampling.*'   # 9 test lulus
./gradlew :app:shared:compileKotlinJvm :app:shared:jvmTest               # lulus
./gradlew :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs    # lulus
```

**Uji manual di browser** (`localhost:3000/crm-sales` → buka deal → Tab Siklus Sampling):
- [ ] Kartu desain baru: tombol "Tandai ACC" mati + checklist merah 3 poin.
- [ ] Upload foto DEPAN saja → 1 issue berkurang. (Upload BELAKANG saja tidak mengurangi issue.)
- [ ] Isi Lebar Dada kolom S saja (Panjang Baju kosong) → size chart masih dianggap belum lengkap.
- [ ] Lengkapi kedua baris + qty S=1 → checklist hilang, tombol ACC nyala.
- [ ] Kosongkan Sampling Fee → field kembali "0".
- [ ] Ajukan Revisi → banner biru "Revisi N dimulai dari data revisi sebelumnya" + semua data masih ada.

**Tantangan mandiri:**
1. Tambahkan pesan spesifik per ukuran: "Ukuran S sudah lengkap — lanjutkan M" (petunjuk:
   `firstCompleteSizeColumn` sudah mengembalikan nama kolomnya).
2. Terapkan pola yang sama ke gerbang "Terbitkan Invoice Sampling" — bisakah dia dipinjam
   langsung tanpa mengubah `core/`?
3. Tulis test untuk `ApproveSamplingFromDealUseCase` dengan fake repository: pastikan ACC order
   kosong ditolak dengan `IllegalArgumentException`.
