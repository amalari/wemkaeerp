# 🎓 Modul Pembelajaran: Direct Canvas Invoice Designer — AI Auto-Mapping & Auto-Connect CRM

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design (Pure Kotlin Engine), Compose Multiplatform (Wasm/Desktop),
> MVI State Holder, Heuristik AI berbasis Geometri & Kata Kunci, Zero-Emoji Vector Icons
> **Prasyarat**: Dasar Kotlin (data class, sealed interface, value class), dasar Compose (`UiState` + `onEvent`)
> **Referensi File**:
> - [`InvoiceAiMappingEngine.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/template/InvoiceAiMappingEngine.kt)
> - [`TemplateDesignerViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateDesignerViewModel.kt)
> - [`DesignerPropertyInspector.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/DesignerPropertyInspector.kt)
> - [`InvoicePrefillCoordinator.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/InvoicePrefillCoordinator.kt)
> - [`LeadInspectorPane.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadInspectorPane.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Sebelum task ini, alur "buat faktur untuk prospek CRM" berbunyi seperti ini:

1. Sales membuka kartu prospek di CRM, klik **Generate Invoice Sampling**.
2. Aplikasi melompat ke modul Invoicing dan memunculkan **modal form statis** (`CreateInvoiceDialog`).
3. Sales mengisi form, faktur terbit dengan tata letak template default — selamanya.

Yang hilang: **hubungan antara data prospek dan kanvas dokumen**. Sales tidak bisa menaruh nomor SPK di
kolom yang benar, tidak bisa mengganti susunan kop faktur, dan setiap kali ingin melihat hasilnya harus
menyimpan dulu. Akibatnya tim finance meng-export PDF lalu menempel ulang kop di luar sistem.

Bayangkan sebaliknya: **Canva untuk faktur**. Anda mengetik "DITAGIHKAN KEPADA:" di kanvas A4, lalu
aplikasi tahu sendiri bahwa baris di bawahnya seharusnya berisi nama klien dari prospek yang sedang dibuka.

### Analogi Sederhana

- **Kanvas A4** = kertas bertitik (grid 10 mm); setiap elemen punya koordinat dalam **1/10 mm**.
- **Token data** (`billTo.name`, `invoice.total`) = **slot puzzle**. Kertas menampung slot; isinya datang
  dari dokumen nyata saat render.
- **AI Auto-Map** = **penerjemah**. Ia membaca tulisan di kertas, menebak "ini pasti nama klien", lalu
  mengubah coretan statis menjadi slot puzzle.
- **CRM Auto-Connect** = **pintu geser**: klik tombol di CRM, pintu langsung terbuka ke kanvas yang sudah
  berisi data prospek — bukan ke formulir isian.

### Hasil Akhir yang Diharapkan

Satu klik di CRM → kanvas drag-and-drop A4 terbuka lengkap dengan nama template, data klien, dan rincian
pekerjaan → user merapikan tata letak → klik **AI Auto-Map** untuk menyulap tulisan statis menjadi token
dinamis → klik **Simpan & Pratinjau Faktur** → template tersimpan, draft faktur terbuat di backend, dan
modal pratinjau PDF terbuka otomatis.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Mulailah **dari kontrak data**, bukan dari tombol.

1. **Langkah 0 — Kunci kontrak lintas modul (`InvoicePrefillData`)**: tentukan bentuk data yang menyeberang
   dari CRM ke Invoicing. Tanpa ini, UI tidak tahu harus menerima apa.
2. **Langkah 1 — Mesin keputusan sebagai Pure Kotlin (`core/InvoiceAiMappingEngine.kt`)**: keputusan
   "teks ini nama klien" adalah **aturan domain**, bukan urusan tampilan. Di `core/` ia bisa diuji tanpa
   Compose, tanpa jaringan, dalam milidetik — dan dipakai ulang oleh kanvas Wasm, Desktop, Android, iOS.
3. **Langkah 2 — State holder (`TemplateDesignerViewModel`)**: merakit template awal, invoice draft live,
   ringkasan hasil AI, dan efek sekali jalan (membuka pratinjau PDF).
4. **Langkah 3 — Rendering & interaksi (`DesignerPropertyInspector`, `DesignerToolbar`, screen)**: tab
   *Tata Letak & Token* dan *Isi Data Faktur Live*.
5. **Langkah 4 — Wiring antar-modul (`InvoicePrefillCoordinator` + `InvoiceWorkspaceScreen`)**: CRM tidak
   boleh mengimpor ViewModel Invoicing.
6. **Langkah 5 — Bukti end-to-end (`server/.../InvoicingApiTest.kt`)**: kirim **persis** payload klien.

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Saklar Navigasi: `openDesignerDirectly`

```kotlin
// InvoiceWorkspaceScreen.kt
LaunchedEffect(tenantSlug) {
    viewModel.onEvent(InvoiceUiEvent.Load)
    if (InvoicePrefillCoordinator.hasPending()) {
        val pending = InvoicePrefillCoordinator.consumePending()
        activePrefill = pending
        if (pending?.openDesignerDirectly == true) {
            viewModel.onEvent(InvoiceUiEvent.OpenDesigner(templateId = null))  // → kanvas
        } else {
            viewModel.onEvent(InvoiceUiEvent.OpenCreateInvoiceDialog())        // → modal lama
        }
    }
}
```

**Mental model**: `openDesignerDirectly` bukan sekadar flag tampilan — ia menyatakan **jenis transaksi**.
Dari CRM, user ingin *merancang sambil mengisi*; dari tombol "+ Buat Tagihan Baru", user ingin *form cepat*.
Satu pintu masuk, dua kebutuhan, tanpa if-else yang tersebar.

`LaunchedEffect(tenantSlug)` aman karena shell aplikasi (`App.kt`) merender layar lewat `when (screen)`:
meninggalkan halaman Invoicing membuang composable-nya, sehingga kembali ke halaman itu selalu membuat
komposisi baru dan blok ini dievaluasi ulang.

### 3.2 Mesin AI: Memisahkan Label dari Nilai

**Lokasi**: `core/.../InvoiceAiMappingEngine.kt`

Keputusan desain terpentingnya bukan soal regex, tapi soal **menahan diri**:

```kotlin
val keywordMatch = matchKeyword(lower, yMm)
val raw = keywordMatch ?: if (labelOnly) null else matchPosition(rect, yMm)
if (raw == null) return null

return if (labelOnly) {
    raw.copy(
        confidence = minOf(raw.confidence, LABEL_ONLY_CONFIDENCE_CEILING), // 0.60
        explanation = "${raw.explanation} — terdeteksi sebagai label statis, bukan nilai data.",
        suggestedPrefix = labelPrefixOf(text),
        isStaticLabelOnly = true
    )
} else {
    raw.copy(suggestedPrefix = dataPrefixOf(text))
}
```

Kenapa penting? Template standar kita memisahkan label dan nilai:

| Elemen | Teks | Peran |
|---|---|---|
| `bill-to-label` | `"DITAGIHKAN KEPADA:"` | **label** |
| `bill-to-name` | — bound ke `billTo.name` — | **nilai** |
| `bank-payment-header` | `"PEMBAYARAN DITRANSFER KE:"` | **label** |
| `bank-details` | — bound ke `issuer.bankName` — | **nilai** |

Kalau tombol AI ikut mengonversi labelnya, kertas berubah menjadi "DITAGIHKAN KEPADA: **PT Mitra**" di
baris label **dan** "**PT Mitra**" lagi di baris nilai. Dokumen rusak, dan user menekan tombol itu sekali
lalu tidak pernah memakainya lagi.

Karena itu ada **ambang dua lapis**:

| Lapis | Batas | Perilaku |
|---|---|---|
| `LABEL_ONLY_CONFIDENCE_CEILING` | `0.60` | Label statis → hanya saran di inspektur, **tidak** dikonversi massal |
| `DEFAULT_MIN_CONFIDENCE` | `0.65` | Nilai data → dikonversi otomatis |
| Fallback geometris | `0.55–0.60` | Tebakan zona kertas — tidak pernah otomatis |

### 3.3 "Ini Label atau Nilai?": Kosakata, Bukan Magi

```kotlin
fun isStaticLabelOnly(text: String): Boolean {
    if (text.contains('@')) return false
    val withoutParenthesis = PARENTHESIS_GROUP.replace(text.lowercase(), " ")
    val tokens = LABEL_TOKEN_SPLIT.split(withoutParenthesis).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return true
    if (tokens.any { token -> token.any { char -> char.isDigit() } }) return false
    return tokens.all { token -> token in LABEL_VOCABULARY }
}
```

Baca sebagai pertanyaan berurutan:

1. **Ada `@`?** → email, pasti nilai. Selesai.
2. **Buang isi kurung, lalu pecah jadi kata.** Kenapa? Karena `"Total Tagihan (Grand Total)"` dan
   `"PPN (11%)"` menyisipkan keterangan dalam kurung. Setelah dibuang, `PPN (11%)` menjadi `PPN` — tetap
   dikenali sebagai label, bukan sebagai angka 11.
3. **Ada kata berangka?** → nomor faktur, telepon, blok alamat `C-4`/`MM2100`. Nilai.
4. **Sisanya: semua kata ada di kosakata label?** → label murni.

`LABEL_VOCABULARY` ditulis eksplisit (bukan "kata apa pun yang bukan angka") supaya `"Dekorasi Border"`
tidak ikut dianggap label, dan supaya reviewer bisa membacanya sebagai **daftar istilah dokumen yang sah**.

### 3.4 Menjaga Label Tetap Hidup: `dataPrefixOf` & `labelPrefixOf`

```kotlin
private fun dataPrefixOf(text: String): String {
    val trimmed = text.trim()
    val colonIndex = trimmed.indexOf(':')
    if (colonIndex in 1 until trimmed.lastIndex) {
        val tail = trimmed.substring(colonIndex + 1).trim()
        if (tail.isNotEmpty()) return trimmed.substring(0, colonIndex + 1) + " "
    }
    val lowered = trimmed.lowercase()
    val salutation = SALUTATION_PREFIXES.firstOrNull { lowered.startsWith(it) && lowered.length > it.length }
    if (salutation != null) return trimmed.substring(0, salutation.length) + " "
    return ""
}
```

Inilah yang membuat hasil AI terasa "mengerti", bukan "merusak":

| Teks di kanvas | Hasil setelah AI Auto-Map |
|---|---|
| `Telp / WA: 08123456789` | `prefix = "Telp / WA: "` + `billTo.phone` |
| `Kepada Yth. PT Mitra Usaha Mandiri` | `prefix = "Kepada Yth. "` + `billTo.name` |
| `Catatan: Bahan Katun Combed 30s` | `prefix = "Catatan: "` + `invoice.notes` |
| `PT Adhi Garmen Sejahtera` | `prefix = ""` + `billTo.name` |

### 3.5 Satu Konversi, Dua Pemakai: `toBoundField`

```kotlin
fun toBoundField(
    element: TemplateElement.StaticText,
    suggestion: AiMappingSuggestion
): TemplateElement.BoundField = TemplateElement.BoundField(
    elementId = element.elementId,
    rect = element.rect,
    zOrder = element.zOrder,
    anchorBelowTable = element.anchorBelowTable,
    binding = suggestion.token,
    prefix = suggestion.suggestedPrefix,
    style = element.style
)
```

Sebelumnya logika ini disalin di dua tempat (pemetaan massal dan tombol "Hubungkan Token Ini" di
inspektur). Sekarang satu fungsi dipakai dua-duanya, jadi perubahan aturan konversi hanya disentuh sekali.

### 3.6 ID Template Unik: Menyelamatkan Template Default

```kotlin
val existingTemplateId = initialTemplateId?.takeIf { it.isNotBlank() }
var tpl = if (existingTemplateId != null) {
    standard.copy(id = InvoiceTemplateId(existingTemplateId))
} else {
    standard.copy(
        id = InvoiceTemplateId("tpl-${now.toEpochMilliseconds()}"),
        name = "Template Faktur Baru",
        isDefault = false
    )
}
```

**Ini bug yang hampir lolos.** `InvoiceTemplateFactory.standardIndonesianInvoice()` mengembalikan template
ber-ID `tpl-std-id-001` — persis ID template default yang di-seed migrasi `V32` untuk setiap tenant.
Endpoint `POST /api/tenant/invoicing/templates` melakukan **upsert berdasarkan ID**. Membuat "Template
Faktur Sampling - PT Mitra" tanpa mengganti ID akan **menimpa template standar seluruh tenant**, dan
karena `isDefault` ikut tersimpan, template prospek mendadak jadi template default semua faktur.
Sekarang ID-nya unik, `isDefault = false`, dan `applicableKinds` dipersempit ke jenis faktur terkait.

### 3.7 Data Live Mengalir ke Invoice Draft

```kotlin
private fun updateLiveItem(event: TemplateDesignerUiEvent.UpdateLiveItem) {
    _uiState.update { current ->
        val qtyInt = (event.quantity * 1_000_000).toLong().coerceAtLeast(1_000_000L)
        val updatedLine = InvoiceLine(
            id = InvoiceLineId("line-live-01"),
            description = event.description.ifBlank { "Rincian Pekerjaan Garmen" },
            quantity = Quantity(qtyInt, UnitOfMeasure.PIECE),
            unitPrice = Money.idr(event.unitPrice.coerceAtLeast(0L)),
            discount = Ratio.ZERO,
            sortOrder = 1
        )
        current.copy(
            previewInvoice = current.previewInvoice.copy(
                lines = listOf(updatedLine),
                taxRatio = Ratio.percent(event.taxPercent.coerceIn(0.0, 100.0)),
                contractValue = updatedLine.amount      // ← dihitung domain, bukan diketik ulang
            )
        )
    }
}
```

Dua detail yang layak diperhatikan:

- `Quantity` menyimpan **micros** (`qty × 1_000_000`), jadi `100.0` menjadi `100_000_000`. Mengirim `100`
  mentah akan menampilkan pecahan yang mustahil di kanvas.
- `contractValue` memakai `updatedLine.amount` — **hasil hitungan domain**, bukan `unitPrice` mentah.
  Menulisnya ulang berarti ada dua rumus untuk satu fakta; begitu aturan diskon berubah, keduanya berbeda
  dan tidak ada yang tahu mana yang benar.

### 3.8 Inspector Dua Tab: Tata Letak vs Data Live

```kotlin
enum class DesignerInspectorTab(val label: String) {
    LAYOUT("Tata Letak & Token"),
    LIVE_DATA("Isi Data Faktur Live")
}
```

Labelnya **tanpa emoji**; ikonnya dirender sebagai slot, bukan karakter:

```kotlin
when (tab) {
    DesignerInspectorTab.LAYOUT -> IconRuler(Modifier.size(12.dp), color = tint)
    DesignerInspectorTab.LIVE_DATA -> IconNote(Modifier.size(12.dp), color = tint)
}
Text(text = tab.label, maxLines = 1, overflow = TextOverflow.Ellipsis, color = tint)
```

`maxLines` + `overflow` bukan hiasan: Kontrak 13 repo ini mewajibkan elemen yang boleh menyusut dinyatakan
eksplisit, kalau tidak teks pecah satu huruf per baris saat panel menyempit.

### 3.9 Simpan → Buat Faktur → Pratinjau: Rantai Efek Sekali Jalan

```kotlin
private fun saveAndCreateInvoice(onSuccess: (InvoiceId) -> Unit) {
    scope.launch {
        _uiState.update { it.copy(isSaving = true) }

        val templateResult = remoteDataSource.saveTemplate(tenantSlug, _uiState.value.template)
        if (templateResult.isFailure) {
            // …tampilkan error, STOP. Jangan buat faktur yang menunjuk template hantu.
            return@launch
        }
        val savedTemplate = templateResult.getOrThrow()
        val liveInvoice = _uiState.value.previewInvoice
        val command = CreateInvoiceCommand(/* … */, templateId = savedTemplate.id)

        remoteDataSource.createInvoice(tenantSlug, command).onSuccess { created ->
            _uiState.update {
                it.copy(isPdfPreviewOpen = true, createdInvoiceId = created.id, previewInvoice = created)
            }
            onSuccess(created.id)
        }.onFailure { err ->
            _uiState.update { it.copy(isSaving = false, error = "Gagal menerbitkan faktur: ${err.message}") }
        }
    }
}
```

Urutannya **wajib**: template dulu, baru faktur. Kalau dibalik dan penyimpanan template gagal, Anda punya
faktur yang menunjuk template yang tidak pernah ada — dan renderer PDF diam-diam jatuh ke template default,
sehingga dokumen yang diunduh user berbeda dari yang ia lihat di kanvas.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Keputusan | Alternatif | Mengapa Dipilih | Risiko Alternatif |
|---|---|---|---|
| Heuristik kata kunci + geometri di **pure Kotlin** (`core/`) | Panggil LLM / API AI sungguhan | Deterministik, offline, gratis, bisa di-test, data prospek tidak keluar pabrik | Biaya per panggilan, latency, hasil tidak bisa diregresi-test, risiko privasi data klien |
| **Confidence score** + ambang ganda | Konversi apa pun yang cocok | User tetap pegang kendali; label tidak ikut hancur | Kanvas rusak otomatis tanpa jalan pulang yang jelas |
| `maxLines`/`overflow` di tab | Biarkan teks membungkus | Mencegah bug teks pecah per huruf (Kontrak 13) | Panel menyempit → teks tak terbaca |
| **Vektor Canvas** (`ClayIcons`) | Emoji / Unicode glyph | Font bundel (Fredoka/Nunito) tak punya glyph emoji → tofu `▯` di Wasm | Kotak kosong di browser, tak bisa diwarnai per state |
| ID template **unik** saat membuat baru | Pakai ID template standar | Mencegah menimpa template default tenant | Seluruh tata letak faktur tenant berubah sekaligus |
| Simpan template **sebelum** buat faktur | Buat faktur lebih dulu | Faktur selalu menunjuk template yang benar-benar ada | Snapshot PDF tak sesuai kanvas user |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Membuat contoh `Invoice` berstatus `ISSUED` tanpa `renderedTemplate`.** Domain langsung melempar
   `IllegalArgumentException` dari `Invoice.init`. Test ViewModel menemukan ini; tanpa test, gejalanya
   tampak seperti "aplikasi crash saat membuka desainer dari tab Template".
2. **Menganggap `assertTrue(x is T)` tidak men-smart-cast.** Compiler memberi tahu lewat warning
   `No cast needed` — hapus cast-nya, jangan diabaikan.
3. **Menaruh logika pemetaan token di Composable.** Ia terpanggil ulang setiap recomposition dan tak bisa
   diuji. Semua keputusan ada di `core/`; Composable hanya menampilkan.
4. **Lupa satuan internal domain.** `Quantity` = micros, `Money` = minor units. Mengirim angka mentah ke
   payload JSON membuat nominal salah 10^6×.
5. **Menyimpan template hasil CRM dengan ID standar.** Endpoint template melakukan upsert by ID, jadi ini
   menimpa template default tenant.
6. **Menambah emoji baru ke string UI.** Di Wasm emoji menjadi `▯`; selalu pakai slot `leading` dengan ikon
   dari `ClayIcons.kt` (`IconZap`, `IconReceipt`, `IconRuler`, `IconNote`, `IconClose`, `IconArrowBack`).
7. **Menaruh `drawBehind` bayangan setelah `clip` pada `claySurface`.** Bayangan terpotong habis; urutan
   modifier clay tidak boleh ditukar.

---

## 🧪 6. Verifikasi & Cara Membuktikannya Sendiri

### 6.1 Test domain murni (12 test)

```bash
./gradlew :core:jvmTest --tests '*InvoiceAiMappingEngineTest*'
```

Yang dijamin: pemisahan label/nilai, penyisipan prefix, prioritas nomor rekening sebelum nama bank,
`toBoundField` mempertahankan geometri + gaya, dan `AiMappingSuggestion` menolak skor di luar `0.0..1.0`.

### 6.2 Test state holder (8 test)

```bash
./gradlew :app:shared:jvmTest --tests '*TemplateDesignerViewModelTest*'
```

Yang dijamin: penamaan template per jenis alur (Sampling vs DP), `applicableKinds`, jatuh tempo = terbit + 14
hari, AI memetakan teks bernilai sambil membiarkan label, data live mengalir ke draft
(subtotal/PPN/total/contractValue), dan jalur gagal (simpan template gagal → faktur **tidak** dibuat;
buat faktur gagal → pratinjau **tidak** terbuka).

### 6.3 Test API end-to-end

```bash
./gradlew :server:test --tests '*InvoicingApiTest*'
```

`crmOriginInvoiceFlow_…_withPdfPreview` mengirim payload **persis** seperti klien (`sourceKind: "CRM_LEAD"`),
lalu memastikan draft terbuat, PDF bisa diambil (header `%PDF-`), dan template default tenant tetap utuh.

### 6.4 Kompilasi 5 target + verifikasi visual

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :app:shared:jvmTest
```

Lalu jalankan `./dev.sh`, buka `http://localhost:3000/crm`, pilih prospek QUALIFIED → tab **Invoice & Alur**
→ **Generate Invoice Sampling** → pastikan:

- Kanvas A4 terbuka dengan nama template *"Template Faktur Sampling - [Nama Klien]"* dan data prospek terisi.
- Tab inspektur menampilkan **ikon vektor** (bukan kotak `▯`) dan tombol **Simpan & Pratinjau Faktur** aktif.
- Tambahkan teks `Catatan: Bahan Katun Combed 30s` → klik **AI Auto-Map** → teks berubah menjadi kolom
  dinamis dengan prefix `Catatan: `, sementara label `DITAGIHKAN KEPADA:` tetap statis.
- Klik **Simpan & Pratinjau Faktur** → modal pratinjau PDF A4 muncul otomatis.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Pertegas aturan NPWP penerbit vs NPWP klien di `matchKeyword` (saat ini hanya memakai
      ambang `yMm < 60`), lalu tambahkan dua test-nya.
- [ ] **Tantangan 2**: Tambahkan elemen dekoratif `RectShape` pembungkus tabel item dengan
      `anchorBelowTable = true`, lalu buktikan di kanvas bahwa ia ikut turun saat baris item ditambah.
- [ ] **Tantangan 3**: Buat tombol "Batalkan Pemetaan AI" yang mengembalikan `BoundField` menjadi
      `StaticText`, dan jelaskan mengapa teks aslinya tidak bisa dipulihkan persis (petunjuk: `prefix`
      disimpan, sedangkan nilai statis aslinya sudah dilepas saat konversi).
