# 🎓 Modul Pembelajaran: Resolusi Identitas Desain (Multi-Design Badging) pada Antrean Quality Control (QC) & Finishing

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Bounded Context Mapping, Multi-Design Deal Resolution, UI State Enrichment, Compose Multiplatform Clay Design System  
> **Prasyarat**: Memahami relasi CRM Deal dan Sampling Order, StateFlow di Compose Multiplatform, dan prinsip Clean Architecture / DDD  
> **Referensi Task**: Bugfix & UX Enhancement — Identifikasi Desain Multi-SPK pada Kartu QC (`http://localhost:3000/quality-control`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Di industri garmen dan rajut (knitwear), seorang klien dalam satu kesepakatan (*deal*) sering kali memesan **beberapa varian desain sekaligus**.
Misalnya:
- **Klien**: *Kanva Knit*
- **Deal**: *PO Vest Rajut Kanva*
- **Desain 1 (Navy)**: 4 pcs (`SPK-SMP-0009`)
- **Desain 2 (Cream)**: 6 pcs (`SPK-SMP-0010`)

Secara arsitektur database, setiap desain fisik wajib memiliki **nomor SPK unik** (`spk_number`) karena panel benang rajut, instruksi feeder mesin, lembar ukuran, dan kartu kerja operatornya berbeda.

**Apa kekacauan yang terjadi jika identitas desain tidak ditandai di antrean QC?**
Ketika inspektor membuka halaman antrean Quality Control (`/quality-control`), dua kartu muncul berturut-turut untuk klien yang sama dengan deskripsi gaya yang mirip (*Vest Rajut Rib Colorway Navy* dan *Vest Rajut Rib Colorway Cream*). Tanpa label pembeda yang tegas:
1. Inspektor QC mengira ada **duplikasi data / sistem bug** karena melihat 2 kartu antrean serupa.
2. Saat mengukur baju di meja QC, sampel fisik warna Cream bisa tertukar diinspeksi menggunakan lembar data Navy, menyebabkan penolakan (*reject*) keliru atau hasil ukur cacat lolos ke finishing.

### Analogi Sederhana
Bayangkan loket farmasi di rumah sakit. Seorang pasien menerima 2 resep berbeda: satu obat sirup demam, satu salep kulit.
Jika kedua kantong resep hanya ditempeli stiker nama pasien `"Budi Santoso"` tanpa tanda `"Obat 1 dari 2 (Sirup)"` dan `"Obat 2 dari 2 (Salep)"`, apoteker dan pasien akan kebingungan mengapa ada dua kantong dengan nama yang sama, dan rentan salah memberikan instruksi minum obat.

### Hasil Akhir yang Diharapkan
1. Setiap kartu antrean QC yang berasal dari kesepakatan multi-desain menampilkan badge mencolok:
   - Badge kode desain: `[ DSG-01 ]` atau `[ DSG-02 ]` dengan warna aksen brand (`WeMadeColors.Accent`).
   - Teks penjelas indeks: `"Desain 1/2"` dan `"Desain 2/2"`.
2. Pada panel detail inspeksi (header atas), inspektor langsung melihat informasi lengkap:
   `SPK-SMP-0009` `[ DSG-02 • Desain 2 dari 2 ]` dan tag `[ Desain DSG-02 ]`.
3. Filter pencarian di meja QC mendukung pencarian instan berdasarkan kode desain (misal mencari `"DSG-02"` langsung menyaring SPK terkait).
4. Operator di meja Finishing (`/finishing`) juga menerima kejelasan penandaan yang sama.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta mengerjakan fitur pembeda desain seperti ini dari awal, berikut adalah urutan berpikir dan penulisan yang benar:

```
[1. Domain Helper / Utility Pure Function]
                  ↓
[2. UI Model / Presentation DTO Enrichment]
                  ↓
[3. Presentation View Components (Cards & Headers)]
                  ↓
[4. Interaction & Filter Integration (Search & Workspace)]
                  ↓
[5. Unit Testing & Visual Verification]
```

### Langkah 1: Pure Resolution Logic (`SamplingDesignResolution.kt`)
Jangan langsung mengubah tampilan Composable! Mulailah dengan membuat pure function deterministik yang menerima satu pesanan (`order`) dan daftar seluruh pesanan (`allOrders`). Fungsi ini bertanggung jawab:
- Mengecek apakah order memiliki `dealId`.
- Mengelompokkan seluruh pesanan dengan `dealId` yang sama.
- Mengurutkannya secara stabil berdasarkan `createdAt` (atau `spkNumber` jika waktu pembuatan bersamaan).
- Mengembalikan objek `ResolvedDesignInfo(code = "DSG-01", designNumber = 1, totalDesigns = 2)`.

### Langkah 2: Perkaya Model Tampilan (`QcQueueUiModel.kt`)
Hubungkan hasil resolusi ke model antrean `QcQueueItem`:
```kotlin
data class QcQueueItem(
    ...
    val designCode: String?,
    val designNumber: Int,
    val totalDealDesigns: Int
)
```
Dengan meletakkan data ini di UI Model, Composable tidak perlu melakukan filtering atau iterasi berulang pada setiap recomposition (mencegah degradasi performa render).

### Langkah 3: Perbarui Komponen Antrean & Header QC (`QcQueuePane.kt` & `QcInspectionPane.kt`)
Sematkan komponen desain `ClayBadge` dan `ClayTag` menggunakan token warna resmi:
- Pada kartu antrean (`QcQueueCard`): tampilkan `[ DSG-01 ]` di samping nomor SPK dan info `"Desain 1/2"` di pojok kanan bawah jika `totalDealDesigns > 1`.
- Pada header inspeksi: tampilkan pill badge `"DSG-01 • Desain 1 dari 2"`.

### Langkah 4: Hubungkan ke Filter Pencarian (`QcInspectorWorkspaceScreen.kt`)
Pastikan teks pencarian `query` di kolom cari juga memeriksa `item.designCode`. Jika operator mengetik `"DSG-01"`, antrean langsung tersaring ke desain tersebut.

### Langkah 5: Tulis Unit Test & Uji Visual
Tulis test murni menggunakan `kotlin.test` untuk memverifikasi logika resolusi desain (multi-desain, single desain, order berurutan, dan fallback), jalankan test, lalu verifikasi visual melalui browser.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Logika Deterministik Resolusi Desain

File: [`SamplingDesignResolution.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/SamplingDesignResolution.kt)

```kotlin
data class ResolvedDesignInfo(
    val code: String,
    val designNumber: Int,
    val totalDesigns: Int
)

fun resolveDesignInfo(order: SamplingOrder, allOrders: List<SamplingOrder>): ResolvedDesignInfo? {
    if (!order.dealId.isNullOrBlank()) {
        val dealOrders = allOrders.filter { it.dealId == order.dealId }
            .sortedWith(compareBy<SamplingOrder> { it.createdAt }.thenBy { it.spkNumber.value })
        if (dealOrders.size > 1) {
            val idx = dealOrders.indexOfFirst { it.id == order.id }
            val num = if (idx >= 0) idx + 1 else 1
            return ResolvedDesignInfo(
                code = "DSG-" + num.toString().padStart(2, '0'),
                designNumber = num,
                totalDesigns = dealOrders.size
            )
        }
    }
    if (order.styleName.startsWith("DSG-")) {
        val candidate = order.styleName.substringBefore(' ').substringBefore(':')
        if (candidate.matches(Regex("^DSG-\\d+$"))) {
            val num = candidate.removePrefix("DSG-").toIntOrNull() ?: 1
            return ResolvedDesignInfo(
                code = candidate,
                designNumber = num,
                totalDesigns = 1
            )
        }
    }
    return null
}
```

**Mengapa blok ini ditulis begini?**
1. **Deterministik & Stabil**: Jika urutan `allOrders` dari API acak, `sortedWith(compareBy<SamplingOrder> { it.createdAt }.thenBy { it.spkNumber.value })` menjamin urutan desain selalu konsisten di mana pun dipanggil (Deal Dialog, QC Queue, Finishing, dll.).
2. **Padding 2 Digit (`DSG-01`)**: Format dua digit (`padStart(2, '0')`) menjaga lebar teks visual seragam dan rapi di dalam badge.
3. **Fallback Prefix Manual**: Jika suatu SPK lama tidak memiliki `dealId` namun diisi nama style `"DSG-03 Oversized Hoodie"`, regex mengekstraknya sebagai `DSG-03`.

---

### Blok B: Transformasi ke UI Model Antrean

File: [`QcQueueUiModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/qc/QcQueueUiModel.kt)

```kotlin
private fun SamplingOrder.toQueueItem(
    now: Instant,
    kind: QcInspectionKind,
    allOrders: List<SamplingOrder>
): QcQueueItem {
    ...
    val designInfo = resolveDesignInfo(this, allOrders)

    return QcQueueItem(
        order = this,
        ...
        designCode = designInfo?.code,
        designNumber = designInfo?.designNumber ?: 1,
        totalDealDesigns = designInfo?.totalDesigns ?: 1
    )
}
```

**Mengapa blok ini ditulis begini?**
- **Prinsip Separation of Concerns**: Logika komputasi data dipusatkan di fungsi pemetaan (*mapper*).
- **Zero Calculation in UI Recomposition**: Saat Jetpack Compose menggambar ulang item list (recomposition karena scroll atau hover), Composable hanya membaca property `item.designCode`, tanpa menjalankan filter atau sort lagi.

---

### Blok C: Penanda Visual pada Kartu Antrean

File: [`QcQueuePane.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/qc/components/QcQueuePane.kt)

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Small),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.spk,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
        )
        if (item.designCode != null) {
            ClayBadge(
                text = item.designCode,
                tint = WeMadeColors.Accent
            )
        }
    }
    WaitingBadge(item.waitingLabel, item.isStale)
}
```

**Mengapa blok ini ditulis begini?**
- **Design System Rule Compliance**: Sesuai aturan arsitektur WeMade ERP, dilarang memakai `Color(0xFF...)` sembarangan atau raw Unicode emoji. Komponen menggunakan `ClayBadge` resmi dengan token `WeMadeColors.Accent` (`#EA580C`) yang tegas dan kontras tinggi.
- **Hierarki Informasi**: Nomor SPK dan kode desain diletakkan berdampingan di baris paling atas agar mata operator langsung menangkap identitas unik lembar kerja.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif Lain | Mengapa Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Resolusi Berbasis Kesatuan `dealId`** | Menambahkan kolom `design_code` baru di tabel database `sampling_orders` | Sangat cepat diterapkan, konsisten dengan relasi deal CRM yang sudah ada, tanpa perlu migrasi DDL database baru. | Memerlukan migrasi skema Flyway, alter table, update DTO, dan sinkronisasi form input admin yang memakan waktu dan berisiko breaking change. |
| **Enrichment di Lapisan UI Model (`QcQueueItem`)** | Memanggil `resolveDesignInfo` langsung di dalam `@Composable QcQueueCard` | Memori efisien dan waktu render super cepat karena komputasi hanya terjadi sekali saat antrean disusun. | Jika dihitung di Composable, setiap kali kartu scroll atau hover, operasi filter dan sorting berjalan berulang kali (UI frame drop/lag). |
| **Token `WeMadeColors.Accent`** | Menggunakan warna ungu Material default atau hardcoded hex `Color(0xFFFF5722)` | Menjaga konsistensi brand WeMade ERP dan mematuhi aturan baku Design System (Section 12 AGENTS.md). | Pelanggaran aturan design system, risiko kebocoran warna tidak seragam di berbagai platform (Wasm, Desktop, Android). |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asumsi Nomor SPK Pasti Berurutan Sempurna**
   - *Kenapa bahaya*: Developer pemula sering berasumsi jika ada 2 desain, nomor SPK-nya pasti `0001` dan `0002`. Di dunia nyata, user bisa saja membuat order desain pertama di hari Senin (`SPK-SMP-0009`), lalu desain kedua baru dibuat hari Rabu setelah order lain masuk (`SPK-SMP-0015`).
   - *Solusi elegan kita*: Mengelompokkan berdasarkan `dealId`, lalu mengurutkan berdasarkan `createdAt` (dan `spkNumber` sebagai tie-breaker).

2. **Jebakan 2: Menaruh Komputasi Berat di Dalam Composable Loop**
   - *Kenapa bahaya*: Menulis `val designInfo = allOrders.filter { ... }.sortedBy { ... }` langsung di dalam `LazyColumn` item Composable. Hal ini menyebabkan algoritma O(N log N) dieksekusi berkali-kali setiap detik saat user melakukan scrolling.
   - *Solusi elegan kita*: Hitung di fungsi `buildQcQueue` sebelum masuk ke UI state.

3. **Jebakan 3: Menggunakan Unicode Emoji untuk Penanda**
   - *Kenapa bahaya*: Menulis icon seperti `🏷️ DSG-01` di teks. Di Compose Web (Skiko/Wasm), font bawaan OS tidak otomatis dimuat untuk glyph emoji, sehingga di layar browser klien akan merender kotak kosong (*tofu* `▯`).
   - *Solusi elegan kita*: Selalu gunakan komponen `ClayBadge` dan `ClayIcons.kt` berbasis vector canvas.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Unit Testing
Di [`SamplingDesignResolutionTest.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonTest/kotlin/com/eventverse/app/presentation/sampling/SamplingDesignResolutionTest.kt), kita menguji semua skenario bisnis:
1. Pesanan tunggal tanpa deal -> mengembalikan `null`.
2. Pesanan tunggal dalam deal (tanpa desain ganda) -> mengembalikan `null` (tidak mengotori kartu dengan badge redundan).
3. Pesanan multi-desain dalam deal yang sama -> mengembalikan `DSG-01` (1/2) dan `DSG-02` (2/2) dengan urutan pembuatan yang benar meski urutan list acak.
4. Pemecah seri (tie-breaker) menggunakan `spkNumber` jika waktu pembuatan persis sama.
5. Format nama style manual dengan prefix `DSG-xx` -> tetap terdeteksi dengan tepat.

Jalankan test dengan perintah Gradle:
```bash
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.sampling.SamplingDesignResolutionTest"
```
Hasil: **`BUILD SUCCESSFUL`** (semua test case lolos).

### Visual Verification
Gunakan screenshot Playwright atau buka browser di `http://localhost:3000/quality-control`:
- Kartu `SPK-SMP-0009` kini berlabel `[ DSG-02 ]` dengan teks `Desain 2/2`.
- Kartu `SPK-SMP-0010` kini berlabel `[ DSG-01 ]` dengan teks `Desain 1/2`.
- Saat diklik, panel inspeksi kanan menampilkan `[ DSG-02 • Desain 2 dari 2 ]`.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka halaman [`DealDetailDialog.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealDetailDialog.kt), periksa fungsi `designCodeOf(orders, order)`. Refaktorkan agar fungsi tersebut menggunakan fungsi bersama `resolveDesignInfo` dari `SamplingDesignResolution.kt` (menerapkan prinsip DRY — *Don't Repeat Yourself*).
- [ ] **Tantangan 2**: Tambahkan tooltip pada badge `ClayBadge(text = item.designCode)` yang menampilkan keterangan: *"Desain X dari Y dalam kesepakatan Klien Z"*.
