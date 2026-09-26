# 🎓 Modul Pembelajaran: Redesain Lead Detail ala Monday.com, Accordion Properti & Popup Persentase Invoice DP

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, UI Information Architecture (Monday.com Pattern), Progressive Disclosure (Accordion), Invoicing Prefill Coordinator  
> **Prasyarat**: Pemahaman dasar Compose state (`remember`, `mutableStateOf`, `AnimatedVisibility`), Design System Claymorphism, dan alur CRM ke Invoicing  
> **Referensi Task**: Redesain Lead Inspector CRM Sales ala Monday.com dengan Accordion & Dialog DP Invoice

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat tim sales membuka detail prospek di CRM, membuka puluhan field formulir sekaligus (sumber, estimasi pcs, estimasi nilai IDR, target closing date, hingga kolom-kolom kustom tenant) membuat layar menjadi sangat padat (cognitive overload). Sales yang sedang menelepon atau chatting lewat WhatsApp hanya butuh melihat **3 informasi esensial**:
1. **Nama Brand / Perusahaan**
2. **Nama Kontak Person**
3. **Nomor WhatsApp / Telepon dan Email**

Selain itu, ketika prospek mencapai kesepakatan komersial, sales membutuhkan tindakan cepat untuk menerbitkan tagihan. Menampilkan alur rumit antrean PO produksi sebelum tagihan dibuat hanya membingungkan sales. Sales hanya ingin memilih secara lugas:
- **Invoice Sampling** (biaya prototype/sample 100%), atau
- **Invoice DP** (uang muka produksi massal dengan termin persentase tertentu, misal 30% atau 50%).

### Analogi Sederhana (Progressive Disclosure)
Bayangkan kartu nama vs buku portofolio:
- Kartu nama memberikan info cepat yang paling krusial: siapa namanya, perusahaannya apa, dan cara menghubunginya.
- Jika calon klien ingin tahu detail spesifikasi teknis dan rincian kerja sama, mereka membuka buku portofolio (**Accordion "See all"**).
- Pendekatan ini disebut **Progressive Disclosure** (penyingkapan informasi bertahap). Pengguna tidak dibebani informasi yang belum mereka butuhkan saat itu juga.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan fitur ini dari nol, ikuti urutan berikut:

1. **Langkah 0: Tinjau Kontrak Aturan File Size (§14)**
   - Periksa `LeadInspectorPane.kt` sebelum diedit: panjangnya sudah mencapai **734 baris** (melebihi batas toleransi hard 600 baris).
   - Aturan Ratchet menyatakan: jangan menumpuk kode baru ke dalam file yang sudah kegemukan. Dekomposisi fitur menjadi sub-komponen terfokus:
     - `LeadInspectorDetailTab.kt`
     - `LeadInspectorInvoiceTab.kt`
     - `LeadInspectorPane.kt` (orchestrator utama)

2. **Langkah 1: Rancang Tab Invoice & Popup Persentase DP (`LeadInspectorInvoiceTab.kt`)**
   - Buat 2 opsi kartu tagihan: Invoice Sampling dan Invoice DP.
   - Buat popup dialog persentase DP dengan preset praktis (30%, 50%, 70%) dan input teks custom.
   - Sambungkan ke `InvoicePrefillCoordinator.setPending(...)` dan arahkan navigasi ke `AppNavScreen.INVOICING`.

3. **Langkah 2: Rancang Tab Detail Berbasis Progressive Disclosure (`LeadInspectorDetailTab.kt`)**
   - Pisahkan descriptor kolom menjadi:
     - **Primary Descriptors**: `brand_name`, `contact_person`, `whatsapp_number`, `email`.
     - **Secondary Descriptors**: sisa properti core (`source`, `estimated_pcs`, `estimated_value_idr`, `expected_close_date`) dan custom attributes.
   - Buat toggle tombol `"Lihat Semua Properti (See all)"` dengan `AnimatedVisibility` untuk membuka sisa properti.

4. **Langkah 3: Rancang Header Hero Profile ala Monday.com (`LeadInspectorPane.kt`)**
   - Buat avatar squircle berinisial (52dp) di tengah.
   - Tampilkan nama kontak/brand besar (20sp bold) dan subjudul.
   - Susun 3 Tab bersih: `[ Detail ]`, `[ Updates ]`, dan `[ Invoice ]`.

5. **Langkah 4: Kompilasi & Verifikasi Multiplatform**
   - Pastikan tidak ada pelanggaran smart-cast lintas modul (`estimatedValue`).
   - Uji kompilasi `./gradlew app:shared:compileKotlinJvm` dan pastikan file size berada di bawah soft limit (< 400 baris).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Hero Profile Section ala Monday.com (`LeadInspectorPane.kt`)

```kotlin
// Squircle Avatar dengan Inisial
val initialLetter = (lead.brandName.value.firstOrNull() ?: lead.contactPerson.firstOrNull() ?: 'L').uppercaseChar().toString()
val mainTitle = lead.contactPerson.ifBlank { lead.brandName.value.ifBlank { "Detail Lead" } }
val subTitle = if (lead.brandName.value.isNotBlank() && lead.contactPerson.isNotBlank()) {
    lead.brandName.value
} else if (lead.brandName.value.isNotBlank()) {
    "Perusahaan / Brand"
} else {
    "No Title / Company"
}

Column(
    modifier = Modifier.fillMaxWidth().padding(vertical = ClaySpacing.Sm),
    horizontalAlignment = Alignment.CenterHorizontally
) {
    Box(
        modifier = Modifier
            .size(52.dp)
            .clayFlat(
                shape = RoundedCornerShape(16.dp),
                background = WeMadeColors.Primary,
                outline = WeMadeColors.Outline,
                borderWidth = 1.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initialLetter,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.Surface
        )
    }

    Spacer(Modifier.height(ClaySpacing.Sm))

    Text(
        text = mainTitle,
        fontSize = 20.sp,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface,
        textAlign = TextAlign.Center
    )

    Spacer(Modifier.height(2.dp))

    Text(
        text = subTitle,
        fontSize = 12.sp,
        color = WeMadeColors.OnSurfaceMuted,
        textAlign = TextAlign.Center
    )
}
```

**Mengapa blok ini ditulis begini?**
- `RoundedCornerShape(16.dp)` pada box 52dp menghasilkan bentuk **Squircle** khas Monday.com dan iOS/macOS, bukan sekadar lingkaran lingkaran polos.
- Inisial dihitung secara aman (`firstOrNull()`) dengan fallback ke `'L'` (Lead), sehingga tidak akan crash jika string kosong.
- Judul mengutamakan `contactPerson` lalu `brandName` untuk merefleksikan hubungan personal antar manusia dalam penjualan B2B.

---

### Blok B: Progressive Disclosure / Accordion Expander (`LeadInspectorDetailTab.kt`)

```kotlin
val coreBrandId = LeadFieldDescriptor.coreFieldId("brand_name")
val coreContactId = LeadFieldDescriptor.coreFieldId("contact_person")
val corePhoneId = LeadFieldDescriptor.coreFieldId("whatsapp_number")
val coreEmailId = LeadFieldDescriptor.coreFieldId("email")

val primaryFieldIds = setOf(coreBrandId, coreContactId, corePhoneId, coreEmailId)

// Field inti utama (terlihat langsung)
val primaryDescriptors = schema.filter { it.isCore && it.fieldId in primaryFieldIds }
    .sortedBy {
        when (it.fieldId) {
            coreBrandId -> 0
            coreContactId -> 1
            corePhoneId -> 2
            coreEmailId -> 3
            else -> 4
        }
    }

// Field inti sekunder yang disembunyikan dalam accordion
val secondaryCoreDescriptors = schema.filter {
    it.isCore &&
    it.fieldId !in primaryFieldIds &&
    it.fieldId != LeadFieldDescriptor.coreFieldId("stage") &&
    it.fieldId != LeadFieldDescriptor.coreFieldId("owner_employee_id")
}
```

**Mengapa blok ini ditulis begini?**
- Pemisahan dilakukan berdasarkan ID deskriptor kolom schema. Dengan cara ini, komponen input editor `LeadCustomField` tetap digunakan secara konsisten (mematuhi Rule of Three).
- Stage dan PIC sengaja disaring keluar dari list form karena sudah diletakkan di Status Bar bagian atas.

```kotlin
// Tombol Accordion Expander
Row(
    modifier = Modifier
        .fillMaxWidth()
        .clickable { isExpanded = !isExpanded }
        .padding(vertical = ClaySpacing.Sm),
    horizontalArrangement = Arrangement.Center,
    verticalAlignment = Alignment.CenterVertically
) {
    Text(
        text = if (isExpanded) "Sembunyikan Properti" else "Lihat Semua Properti (See all)",
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = WeMadeColors.Primary
    )
    Spacer(Modifier.width(6.dp))
    if (isExpanded) {
        IconChevronUp(Modifier.size(14.dp), color = WeMadeColors.Primary)
    } else {
        IconChevronDown(Modifier.size(14.dp), color = WeMadeColors.Primary)
    }
}

// Konten Accordion
AnimatedVisibility(
    visible = isExpanded,
    enter = expandVertically() + fadeIn(),
    exit = shrinkVertically() + fadeOut()
) {
    Column(...) {
        // Render secondaryCoreDescriptors & customFields
    }
}
```

**Mengapa memakai `AnimatedVisibility`?**
- Jika langsung menggunakan `if (isExpanded)`, transisi akan patah (snap) secara instan.
- `expandVertically() + fadeIn()` memberikan efek buka-tutup akordion yang mulus dan nyaman dilihat pengguna.

---

### Blok C: Popup Persentase DP & Invoicing Bridge (`LeadInspectorInvoiceTab.kt`)

```kotlin
InvoiceDpPercentageDialog(
    lead = lead,
    onDismiss = { isDpDialogOpen = false },
    onConfirm = { dpPercent ->
        isDpDialogOpen = false
        val clientDisplayName = lead.brandName.display(fallback = lead.contactPerson.ifBlank { "Prospek Lead" })
        val qty = (lead.estimatedPcs ?: 100).toDouble()
        val estValue = lead.estimatedValue
        val unitPrice = if (estValue != null && qty > 0) {
            ((estValue.amount * dpPercent) / (100 * qty.toLong())).coerceAtLeast(1L)
        } else {
            150000L
        }

        InvoicePrefillCoordinator.setPending(
            InvoicePrefillData(
                kind = InvoiceKind.DOWN_PAYMENT,
                clientName = clientDisplayName,
                contactPerson = lead.contactPerson,
                phone = lead.whatsappNumber?.value ?: "",
                email = lead.email,
                sourceKind = InvoiceSourceKind.CRM_LEAD,
                sourceRef = lead.id.value,
                lineDescription = "Uang Muka Produksi (DP $dpPercent%) - $clientDisplayName",
                lineQty = qty,
                linePrice = unitPrice,
                notes = "Termin Pembayaran: Uang Muka (DP) sebesar $dpPercent% sebelum proses produksi dimulai. Sisa pelunasan dibayar sebelum pesanan dikirim."
            )
        )
        onClose?.invoke()
        navigator(AppNavScreen.INVOICING)
    }
)
```

**Mengapa blok ini ditulis begini?**
- `val estValue = lead.estimatedValue`: Menyimpan properti modul eksternal ke variabel lokal adalah aturan wajib di Kotlin Multiplatform untuk menghindari error compiler `Smart cast to 'T' is impossible, because property is declared in different module`.
- `InvoicePrefillCoordinator`: Memisahkan antarmuka CRM dari Invoicing. CRM tidak perlu mengimpor ViewModel Invoice atau merakit DTO database. CRM cukup menitipkan data prefill ke coordinator, lalu meminta navigator berpindah layar.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan God File**:
   - *Kesalahan*: Menambahkan fitur baru langsung di dalam `LeadInspectorPane.kt`. File yang tadinya 734 baris membengkak menjadi 900+ baris.
   - *Solusi*: Terapkan pemecahan per tanggung jawab domain (Detail Tab, Invoice Tab, PIC Selector). Hasilnya: ketiga file baru masing-masing hanya ~300 baris.

2. **Jebakan Smart-Cast Cross-Module**:
   - *Kesalahan*: Menulis `if (lead.estimatedValue != null) lead.estimatedValue.amount`.
   - *Akibat*: Compiler error karena Kotlin tidak bisa menjamin properti dari modul `core` tidak memiliki custom getter saat diakses dari modul `app:shared`.
   - *Solusi*: Simpan ke lokal `val estValue = lead.estimatedValue`, lalu periksa `estValue != null`.

3. **Jebakan Hardcoded String Literal Emojis**:
   - *Kesalahan*: Memakai panah unicode `▾` / `▴` atau emoji `📄` di teks accordion.
   - *Akibat*: Tofu (`▯`) saat dijalankan di browser (Compose Wasm / Canvas Skiko).
   - *Solusi*: Selalu gunakan ikon berbasis Canvas: `IconChevronDown` dan `IconChevronUp` dari `ClayIcons.kt`.

---

## 🎯 5. Verifikasi & Tantangan Mandiri

### Cara Menguji
1. Jalankan aplikasi web lokal: `http://localhost:3100/crm-sales/leads`.
2. Klik kartu salah satu prospek.
3. **Uji Detail**:
   - Pastikan avatar squircle inisial dan nama tampil di bagian atas.
   - Pastikan hanya Nama Brand, Kontak, No. HP, dan Email yang terlihat pertama kali.
   - Klik `"Lihat Semua Properti (See all)"`: pastikan accordion membuka kolom lain dan custom fields secara mulus.
4. **Uji Invoice**:
   - Klik tab `"Invoice"`.
   - Klik `"Buat Invoice Sampling"`: pastikan dialihkan ke modul Invoice dengan jenis Sample.
   - Buka kembali lead, pilih tab `"Invoice"`, klik `"Buat Invoice DP..."`:
   - Pastikan dialog popup muncul. Pilih chip `50%` atau ketik `40%`.
   - Klik `"Lanjut Buat Invoice DP"`: pastikan invoice DP terbuka dengan deskripsi DP yang sesuai.

### Tantangan Mandiri untuk Junior Dev
- Coba tambahkan validasi agar input persentase DP tidak boleh lebih dari 100% atau 0%, dengan memberikan indikator pesan error jika user salah mengetik angka.
