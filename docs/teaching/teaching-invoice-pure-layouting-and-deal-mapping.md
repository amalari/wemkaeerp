# Mentoring: Pure Layouting & Direct Deal Transaction Mapping di Desainer Faktur

> **Tingkat**: Menengah ke Lanjut  
> **Modul**: `app/shared/presentation/invoicing/template/`  
> **Konsep Kunci**: Domain-Driven Design, Decoupling Abstractions, Separation of Concerns, Document Rendering Engine

---

## 1. Start Dari Mana? (Order of Operations)

Ketika seorang pengguna atau stakeholder mengatakan:
> *"crm module gausah ada jadinya specific aja kan nanti ada deal nah langsung aja connect ke deal. jadi disini itu pure hanya untuk layouting datanya udah jadi tinggal taro gausah ada modul. langsung aja misal element transaksi: 1. Client Name, Contact, No Invoice Sample, Table Product. Langsung yang gitu aja jadi ini pure untuk tata letak dan mapping data ke pdf gimana?"*

Sebagai engineer, langkah awal kita bukanlah langsung menghapus sembarang kode. Urutan berpikir dan langkah kerja yang benar:

```
Step 0: Kenali Masalah "Leaky Abstraction" & Cognitive Load
   ↓
Step 1: Identifikasi Tanggung Jawab Sebenarnya (Single Responsibility)
   ↓
Step 2: Restrukturisasi Perpustakaan Elemen (Flat Document Functional Slices)
   ↓
Step 3: Putus Dependensi Runtime Otomatis ke Modul Luar (Zero Leaky Fetches)
   ↓
Step 4: Sinkronisasi Data Pratinjau Kanvas Kontekstual Berdasarkan Jenis Dokumen
   ↓
Step 5: Verifikasi Kompilasi Multiplatform (JVM, WasmJS) & E2E Visual Browser
```

---

## 2. Mengapa Konsep "Modul" di Desainer Adalah Anti-Pattern? (The "Why")

Sebelum perubahan ini, panel samping desainer memiliki kelompok bernama `MODUL` dengan accordion `CRM — Klien & Prospek`. Selain itu, saat desainer dibuka, sistem otomatis memanggil endpoint CRM (`GET /api/tenant/crm/leads`), mengambil lead pertama yang ditemukan, dan menimpa invoice preview menjadi "Invoice DP / Uang Muka".

### Masalahnya:
1. **Leaky Abstraction**: Desainer faktur seharusnya tidak peduli dari tabel/modul database mana sebuah data berasal saat runtime. Tanggung jawab desainer hanyalah:
   - **Di mana letak kotak teks ini (X, Y, Lebar, Tinggi)?**
   - **Berapa ukuran font-nya dan bagaimana perataan teksnya (Left, Center, Right)?**
   - **Field transaksi apa yang harus diisi ke dalam kotak ini saat dokumen PDF dicetak?**
2. **Cognitive Load Pengguna**: Pengguna akhir tidak berpikir dalam struktur kode atau nama modul internal kita ("CRM", "Lead", "Pipeline"). Pengguna berpikir dalam konteks **faktur transaksi**: *"Saya butuh menaruh Nama Klien di sini, No Faktur di kanan atas, Tanggal di bawahnya, dan Tabel Barang di tengah."*
3. **Patahnya Konteks Jenis Dokumen**: Saat pengguna membuka template faktur sampel garmen (`SAMPLE INVOICE`), fetch CRM otomatis menimpa kanvas menjadi faktur uang muka (DP). Ini membuat pengguna bingung mengapa template sampel mereka berubah menjadi DP.

Dengan menjadikannya **Pure Layouting & Direct Mapping**, desainer faktur menjadi engine independen yang siap menerima data dari **Deal** apa pun.

---

## 3. Bedah Kode Blok per Blok

### A. Restrukturisasi `ElementPalette.kt`

Sebelumnya:
```kotlin
// ❌ Rumit dan membingungkan pengguna
PaletteSectionTitle("Modul")
InvoiceBindingRegistry.standaloneModules().forEach { module ->
    ModuleGroup(module, ...) // Accordion CRM yang harus dibuka-tutup
}
```

Sesudah:
```kotlin
// ✅ Langsung to-the-point: Elemen Transaksi & Elemen Tata Letak
PaletteSectionTitle("Elemen Transaksi (Data Deal)")

val descriptorName = InvoiceBindingRegistry.descriptorFor("billTo.name")
if (descriptorName != null) {
    PaletteRow(
        label = "Nama Klien / Perusahaan",
        hint = "Nama brand/klien pemesan dari transaksi Deal.",
        onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorName))) },
        leading = { IconUser(Modifier.size(13.dp), color = WeMadeColors.Primary) }
    )
}

val descriptorInvNumber = InvoiceBindingRegistry.descriptorFor("invoice.number")
if (descriptorInvNumber != null) {
    val labelText = if (state.template.targetKind == InvoiceKind.SAMPLE) {
        "No. Invoice Sample"
    } else {
        "Nomor Faktur / Invoice"
    }
    PaletteRow(
        label = labelText,
        hint = "Nomor faktur unik terbitan sistem.",
        onClick = { onEvent(TemplateDesignerUiEvent.InsertPreset(TemplateElementPreset.ModuleField(descriptorInvNumber))) },
        leading = { IconReceipt(Modifier.size(13.dp), color = WeMadeColors.Primary) }
    )
}
```
**Mental Model**:
Setiap baris di palet adalah sebuah **kontrak data (BindingDescriptor)** yang memiliki token unik (misal `billTo.name`, `invoice.number`, `invoice.total`). Saat dicetak ke PDF oleh server PDFBox (`InvoicePdfRenderer`), token ini dievaluasi terhadap entitas `Invoice` nyata yang dibuat dari transaksi `Deal`.

---

### B. Memutus Side-Effect Otomatis di `TemplateDesignerViewModel.kt`

Sebelumnya:
```kotlin
init {
    if (!initialTemplateId.isNullOrBlank()) {
        loadTemplate(InvoiceTemplateId(initialTemplateId))
    }
    // ❌ Anti-pattern: side effect otomatis menimpa kanvas dengan sembarang CRM lead
    if (initialPrefill == null) {
        loadLiveCrmPreview()
    }
}
```

Sesudah:
```kotlin
init {
    if (!initialTemplateId.isNullOrBlank()) {
        loadTemplate(InvoiceTemplateId(initialTemplateId))
    }
    // ✅ Bersih: Desainer tidak lagi memaksakan fetch modul luar saat dibuka
}

private fun loadTemplate(id: InvoiceTemplateId) {
    scope.launch {
        remoteDataSource.getTemplate(tenantSlug, id).onSuccess { tpl ->
            // ✅ Pratinjau kanvas konsisten dengan jenis tagihan template (targetKind)
            val isOnlySample = tpl.targetKind == InvoiceKind.SAMPLE
            val preview = if (isOnlySample && _uiState.value.prefillData == null) {
                TemplateDesignerUiState.createDummySamplePreviewInvoice(now)
            } else if (_uiState.value.prefillData == null) {
                TemplateDesignerUiState.createDummyPreviewInvoice()
            } else {
                _uiState.value.previewInvoice
            }
            _uiState.update { current ->
                current.copy(
                    template = measured(tpl, preview),
                    previewInvoice = preview
                )
            }
        }
    }
}
```

---

## 4. Jebakan Pemula (Common Pitfalls yang Dihindari)

1. **Membiarkan Fetch Asinkron Menimpa State yang Sedang Didesain**:
   Pemula sering menaruh `viewModelScope.launch { fetchSomething() }` di `init` tanpa menyadari bahwa response jaringan yang datang terlambat bisa menimpa apa yang baru saja dibuka atau dimuat oleh user.
2. **Hardcoding Nama Modul di Lapisan Presentasi**:
   Menyebut "Modul CRM" di dalam layar desainer dokumen membuat coupling yang rapuh. Jika besok data pemesan datang dari modul POS kasir, atau modul E-Commerce, nama "CRM" menjadi salah dan menyesatkan.
3. **Mengabaikan Karakteristik Dokumen Tertentu**:
   Faktur Sample garmen memiliki komponen khusus: jasa pola/grading, kain sampel katun flanel, revisi fitting. Menampilkan barang produksi 1000 pcs pada template sample membingungkan desainer tata letak.

---

## 5. Verifikasi Mandiri

Untuk memverifikasi kebenaran implementasi:
1. Jalankan unit test JVM:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:jvmTest
   ```
2. Pastikan test `designerOpenedWithoutPrefill_sampleTemplate_loadsSampleDealPreviewData` dan `designerSwitchKind_updatesPreviewMatchingTargetKind` lolos 100%.
3. Buka browser pada `http://localhost:3000/invoicing/templates/tpl-std-id-001` dan pastikan:
   - Panel kiri hanya menampilkan grup **Elemen Transaksi (Data Deal)** dan **Elemen Tata Letak**.
   - Tidak ada accordion lipat `MODUL` atau `CRM`.
   - Infobar di atas kanvas menunjukkan `[Invoice Sample] • MAPPING PDF AKTIF`.
   - Kanvas langsung menampilkan data transaksi sampel garmen nyata (*Erigo Apparel Studio*, *Jasa Pembuatan Pola*, dll.).
