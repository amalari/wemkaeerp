# 🎓 Modul Pembelajaran: Modul INVOICING & Designer Template Berkanvas (Full-Stack DDD)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Domain-Driven Design (5 Pilar), Exact Penny Allocation (`Money.allocate`), Template Canvas Geometri Integer `Mm10`, Apache PDFBox 3.x Renderer Server, Claymorphism UI  
> **Prasyarat**: Pemahaman dasar Kotlin, Compose Multiplatform, SQL PostgreSQL, dan prinsip clean architecture  
> **Referensi Task**: Modul INVOICING — Invoice Kustom Berkanvas + Termin Sampling / DP / Pelunasan  

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Industri Konveksi (Garment Manufacturing)
Bayangkan sebuah pabrik garmen menerima pesanan seragam korporat senilai **Rp 100.000.005** (seratus juta lima rupiah). Klien meminta pembayaran dibagi 2 termin:
1. **Down Payment (DP) 50%** sebelum kain dipotong.
2. **Pelunasan 50%** setelah proses Quality Control (QC) dan sebelum pengiriman barang.

Jika developer menghitung termin menggunakan tipe data desimal biasa (`Double` atau pembagian pembulatan biasa):
- DP: `100.000.005 / 2 = 50.000.002,5` dibulatkan jadi `Rp 50.000.002` (atau `Rp 50.000.003`).
- Pelunasan: `100.000.005 / 2 = 50.000.002,5` dibulatkan jadi `Rp 50.000.002`.
- **Total yang tertagih**: `50.000.002 + 50.000.002 = Rp 100.000.004`. **Hilang Rp 1!**

Bagi orang awam, Rp 1 tampak sepele. Namun bagi auditor keuangan dan akuntan pajak, **selisih Rp 1 pada invoice menyebabkan status pembukuan *unbalanced* (tidak seimbang)**, faktur pajak PPN tidak cocok dengan DPP, dan verifikasi rekonsiliasi bank gagal otomatis!

Masalah kedua adalah **format faktur fisik/PDF**:
Setiap pabrik konveksi memiliki identitas perusahaan dan format faktur yang berbeda: letak kop surat, logo, nomor rekening pembayaran BCA/Mandiri, kotak tanda tangan, dan jumlah baris pekerjaan (potong, jahit, bordir, sablon, kancing). Jika template dibuat kaku dengan kode HTML/CSS statis atau PDF hardcoded:
- Ketika baris item banyak, tabel menabrak kotak tanda tangan di bawahnya (*content collision*).
- Tampilan preview di layar browser berbeda ukuran dan posisinya saat dicetak ke PDF fisik (*coordinate drift*).

### Solusi Arsitektural Kita
1. **Zero-Loss Financial Engine**: Menggunakan value object `Money` murni integer sen dan metode `Money.allocate(weights)` yang mendistribusikan sisa sen (remainder) ke termin pertama secara matematis tanpa pernah kehilangan satu sen pun.
2. **Koordinat Integer Kanvas `Mm10`**: Satuan kanvas dihitung dalam per sepuluh milimeter integer (`1 mm = 10 Mm10`). Tidak ada drift floating point antara layar Compose dan lembar PDFBox.
3. **Anchor Dinamis Bawah Tabel (`anchorBelowTable`)**: Elemen seperti tanda tangan, rincian rekening bank, dan total terbilang dapat di-*anchor* untuk turun secara otomatis jika tabel baris pekerjaan bertambah panjang.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diberikan task membuat modul enterprise end-to-end dari nol, **JANGAN PERNAH** langsung mengetik antarmuka UI atau membuka IDE database. Ikuti urutan 5 pilar DDD berikut:

```
┌─────────────────────────────────────────────────────────────┐
│ 1. PURE DOMAIN LAYER (core/)                                │
│    Value Objects -> Entities -> Domain Rules -> Repositories │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 2. APPLICATION USE CASES (core/)                            │
│    CreateInvoice, Issue, RecordPayment, Void, Prefill       │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 3. PERSISTENCE & DATABASE LAYER (server/)                   │
│    Flyway Migrations (V31, V32) -> Exposed Tables -> Repos  │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 4. BACKEND API & PDF ENGINE (server/)                       │
│    PDFBox 3.x Renderer -> Ktor REST Routes -> RBAC Auth     │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│ 5. CLIENT & UI PRESENTATION (app/shared/)                   │
│    Ktor Client -> StateFlow ViewModel -> Claymorphism UI    │
└─────────────────────────────────────────────────────────────┘
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pure Domain Layer — Exact Penny Allocation

Perhatikan bagaimana `PaymentSchedule.kt` membagi nilai kontrak menjadi termin DP dan Pelunasan:

```kotlin
// File: core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/PaymentSchedule.kt

object PaymentScheduleFactory {
    fun splitDownPaymentAndSettlement(
        contractValue: Money,
        dpRatio: Ratio
    ): Pair<PaymentScheduleTerm, PaymentScheduleTerm> {
        require(dpRatio > Ratio.ZERO && dpRatio < Ratio.ONE) {
            "Rasio DP harus di antara 0% dan 100% eksklusif."
        }

        // Bobot rasio dikonversi ke skala basis integer (misal 50% vs 50%)
        val dpWeight = dpRatio.numerator.toLong() * 1000L / dpRatio.denominator.toLong()
        val settlementWeight = 1000L - dpWeight

        // KUNCI EMAS: Money.allocate mendistribusikan sen sisa ke termin pertama!
        val allocated = contractValue.allocate(longArrayOf(dpWeight, settlementWeight))

        val dpTerm = PaymentScheduleTerm(
            termNumber = 1,
            title = "Down Payment (${dpRatio.asPercentageString(0)})",
            ratio = dpRatio,
            amount = allocated[0]
        )
        val settlementTerm = PaymentScheduleTerm(
            termNumber = 2,
            title = "Pelunasan",
            ratio = Ratio.ONE - dpRatio,
            amount = allocated[1]
        )
        return Pair(dpTerm, settlementTerm)
    }
}
```

**Mengapa blok ini ditulis begini?**
- `contractValue.allocate(weights)` tidak menggunakan operator bagi (`/`) yang menghasilkan floating point. Ia membagi integer sen, menghitung sisa modulus, lalu menambahkan `+1 sen` ke ember pertama sampai sisa habis.
- **Garansi matematis**: `allocated[0] + allocated[1] == contractValue` selalu bernilai `true` 100% sepanjang masa!

---

### Blok B: Domain Geometri & Koordinat Integer `Mm10`

```kotlin
// File: core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/template/TemplateGeometry.kt

@JvmInline
value class Mm10(val value: Int) : Comparable<Mm10> {
    operator fun plus(other: Mm10): Mm10 = Mm10(value + other.value)
    operator fun minus(other: Mm10): Mm10 = Mm10(value - other.value)
    operator fun times(scalar: Int): Mm10 = Mm10(value * scalar)
    operator fun div(scalar: Int): Mm10 = Mm10(value / scalar)

    fun toMillimeters(): Double = value / 10.0
}
```

**Mengapa blok ini ditulis begini?**
- `@JvmInline value class` membungkus nilai integer primitif tanpa memakan alokasi heap object tambahan di runtime.
- Koordinat integer `Mm10` (`1 mm = 10 units`) mengeliminasi perbedaan pembulatan sub-pixel floating point ketika kode dieksekusi di lingkungan JavaScript/Wasm (Client) dibandingkan JVM (Server).

---

### Blok C: Database Schema & Row-Level Security (PostgreSQL)

```sql
-- File: server/src/main/resources/db/migration/V31__create_invoicing_schema.sql

CREATE TABLE IF NOT EXISTS invoices (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL REFERENCES tenants(id) ON DELETE CASCADE,
    number VARCHAR(64) NOT NULL,
    kind VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    subtotal_minor BIGINT NOT NULL,
    tax_amount_minor BIGINT NOT NULL,
    total_minor BIGINT NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'IDR',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- MULTI-TENANCY ISOLATION DENGAN RLS ENGINE
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_invoices ON invoices
    FOR ALL
    USING (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), ''))
    WITH CHECK (tenant_id = NULLIF(current_setting('app.current_tenant_id', true), ''));
```

**Mengapa blok ini ditulis begini?**
- Nilai uang disimpan sebagai `BIGINT` (`subtotal_minor`, `total_minor`). Dalam mata uang Rupiah (`IDR`), nilai minor unit adalah sen (`1 IDR = 100 minor units`).
- PostgreSQL Row-Level Security (RLS) mengunci baris data di level kernel database. Bahkan jika programmer lupa menambahkan klausa `WHERE tenant_id = ?` pada query SQL di Ktor, data tagihan milik tenant lain tidak akan pernah bocor.

---

### Blok D: Server-Side PDF Engine (Apache PDFBox 3.x)

```kotlin
// File: server/src/main/kotlin/com/eventverse/app/infrastructure/pdf/InvoicePdfRenderer.kt

class InvoicePdfRenderer(
    private val fontRegularBytes: ByteArray,
    private val fontBoldBytes: ByteArray
) {
    fun render(invoice: Invoice, template: InvoiceTemplate, totalPaid: Money): ByteArray {
        PDDocument().use { doc ->
            // Konversi Mm10 ke PDF Points (72 points / 25.4 mm)
            val pageW = (template.paperSize.width.value / 254f * 72f)
            val pageH = (template.paperSize.height.value / 254f * 72f)
            val page = PDPage(PDRectangle(pageW, pageH))
            doc.addPage(page)

            // Hitung pertambahan tinggi tabel dinamis
            val extraTableHeightMm10 = calculateExtraTableHeight(invoice, template)

            // Gambar elemen di kanvas
            template.elements.forEach { element ->
                val adjustedRect = if (element.anchorBelowTable) {
                    element.rect.translated(dx = Mm10.ZERO, dy = extraTableHeightMm10)
                } else element.rect

                drawElement(doc, page, element, adjustedRect, invoice, totalPaid)
            }
            // Simpan dokumen ke byte array
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- PDFBox 3.x menggunakan sistem koordinat Cartesian di mana titik `(0,0)` berada di **kiri-bawah** halaman, sedangkan desainer UI Compose kita menggunakan titik `(0,0)` di **kiri-atas**. Renderer melakukan transformasi koordinat `yPdf = pageHeight - (yTop + height)` secara akurat.
- Penyesuaian `anchorBelowTable` memastikan bahwa ketika invoice konveksi memiliki 20 baris item pekerjaan bordir & sablon, elemen di bawah tabel secara otomatis bergeser ke bawah tanpa menabrak tabel.

---

### Blok E: Presentation Layer (Compose Multiplatform Canvas & Neo-Brutalism)

Perhatikan rendering interaktif pada kanvas Compose (`TemplateCanvas.kt`):

```kotlin
// File: app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/invoicing/template/TemplateCanvas.kt

@Composable
fun TemplateCanvas(
    state: TemplateDesignerUiState,
    onEvent: (TemplateDesignerUiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val zoomFactor = state.zoomPercent / 100f
    val mmToDp = 3f * zoomFactor // 1 mm = 3 dp pada zoom normal

    Box(
        modifier = Modifier
            .size((state.template.paperSize.width.value / 10f * mmToDp).dp,
                  (state.template.paperSize.height.value / 10f * mmToDp).dp)
            .claySurface(
                shape = RoundedCornerShape(4.dp),
                background = WeMadeColors.Surface,
                outline = WeMadeColors.Outline,
                offset = ClayOffset.Rest,
                borderWidth = ClayBorder.Medium
            )
    ) {
        // Grid latar belakang 10mm + Elemen dinamis dengan drag gesture & snap
    }
}
```

**Mengapa blok ini ditulis begini?**
- Mengikuti aturan ketat **Claymorphism + Neo-Brutalism Design System**: outline tegas `ClayBorder.Medium`, hard shadow tanpa blur via `Modifier.claySurface`, nol ad-hoc hex literal di luar tema, dan palet warna WeMade (`#2563EB` & `#EA580C`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Komponen / Masalah | Pilihan Kita | Alternatif Lain | Mengapa Memilih Ini? (The Why) | Risiko Jika Memakai Alternatif |
|---|---|---|---|---|
| **Akurasi Uang** | `Money` & `Money.allocate()` | `Double` / `BigDecimal.divide()` | Presisi sen integer tanpa pembulatan liar (*exact penny distribution*). | Terjadi selisih Rp 1 pada invoice termin yang merusak balance neraca akuntansi. |
| **Geometri Kanvas** | Integer `Mm10` | Floating Point `Float` / `Double` | Nilai tetap sama di Wasm, Android, JVM. Mencegah drift rendering. | Drift posisi elemen antara browser client dan cetakan printer fisik. |
| **PDF Rendering Server** | Apache PDFBox 3.x | Headless Chromium / Puppeteer | Ringan, cold-start cepat (milidetik), konsumsi memori hemat, self-contained JVM. | Puppeteer butuh Node.js runtime, rakus RAM (500MB+ per instance), lambat di server container kecil. |
| **Desain Antarmuka UI** | Claymorphism + Neo-Brutalism | Raw Material Design 3 | Karakter visual brand WeMade ERP yang unik, ramah, kontras tinggi di tablet lantai pabrik. | Tampilan generik pabrikan Google, bayangan blur tipis sulit dilihat di layar workshop konveksi. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Membagi Nilai Tagihan dengan Operator Bagi Biasa (`/ 2`)**
   - *Kenapa bahaya*: Ketika nominal ganjil dibagi 2, terjadi selisih sen/rupiah yang hilang.
   - *Solusi kita*: Gunakan `Money.allocate(longArrayOf(dpWeight, settlementWeight))`.

2. **Jebakan 2: Mencampur Nama Enum Token Binding**
   - *Kenapa bahaya*: Menyebut `InvoiceKind.SAMPLING` padahal di domain didefinisikan sebagai `InvoiceKind.SAMPLE`.
   - *Solusi kita*: Selalu gunakan Single Source of Truth dari Domain Value Objects. Biarkan compiler Kotlin mendeteksi kesalahan secara komprehensif (`when` exhaustive).

3. **Jebakan 3: Drift Koordinat Layar vs Kertas Cetak**
   - *Kenapa bahaya*: Menggunakan satuan `dp` atau `pixel` di database template. Saat dicetak di printer ukuran A4, layout menjadi berantakan karena DPI layar berbeda dengan DPI printer (300/600 DPI).
   - *Solusi kita*: Simpan semua koordinat dalam ukuran fisik integer `Mm10` (milimeter).

4. **Jebakan 4: Token Binding Ambigu di Baris Tabel**
   - *Kenapa bahaya*: Mengambil token data klien (`billTo.name`) di dalam sel baris barang (`line.quantity`).
   - *Solusi kita*: Pisahkan katalog token menjadi `BindingScope.DOCUMENT` dan `BindingScope.LINE` di `InvoiceBindingRegistry`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian menyeluruh telah dilakukan di 3 layer:

1. **Pure Domain Tests (`core/src/commonTest/`)**:
   - `InvoiceTest.kt`: Menguji pembuatan draf, penerbitan (issue), pencatatan pembayaran cicilan, dan pembatalan (void).
   - `InvoiceTemplateTest.kt`: Menguji validasi geometri kanvas `Mm10`, parsing token binding, dan penataan kolom tabel.
   - `InvoiceBindingResolverTest.kt`: Menguji token resolver dan konversi bilangan terbilang rupiah ("Satu Juta Lima Ratus Ribu Rupiah").

2. **Integration & PDF Tests (`server/src/test/`)**:
   - `InvoicePdfRendererTest.kt`: Memastikan PDFBox 3.x berhasil merender file PDF valid (header `%PDF-1.4`, embedded TrueType font Nunito, multi-baris pekerjaan).
   - `InvoicingApiTest.kt`: Menguji endpoint REST Ktor (`/api/tenant/invoicing/*`), autentikasi JWT, multi-tenancy RLS, dan isolasi tenant.

3. **Compose UI Compilation (`app/shared/`)**:
   - Menjalankan `./gradlew :app:shared:compileKotlinJvm` untuk memastikan integrasi ViewModel, StateFlow, dan dialog Claymorphism bebas error.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk memperdalam pemahamanmu tentang arsitektur ini, coba selesaikan 2 tantangan berikut:

- [ ] **Tantangan 1 (Multi-Currency)**: Tambahkan dukungan mata uang Dollar Amerika (`USD`) pada `InvoiceBindingResolver`. Pastikan format uangnya menggunakan koma sebagai pemisah desimal cent (`$ 1,250.50`) dan buat pengubah terbilang bahasa Inggris (*English Number-to-Words*).
- [ ] **Tantangan 2 (Dynamic Barcode / QRIS)**: Buat elemen template baru `TemplateElement.QrCode` yang merender QRIS statis atau dinamis berisi kode pembayaran bank transfer pada pojok kanan bawah invoice.
