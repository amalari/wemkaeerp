# 🎓 Modul Pembelajaran: Pengendalian Visibilitas Kartu SPK A6 Berdasarkan Tahap Pipeline (R&D Gate)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UX Guardrails, State Lifecycle, Domain-Driven Pipeline Stage, Compose Multiplatform  
> **Prasyarat**: Paham urutan tahap di `SamplingPipelineStage`, struktur modal `SamplingSpkDetailDialog`, dan integrasi pencetakan tiket PDF  
> **Referensi Task**: "kartu SPK a6 ini muncul di http://localhost:3000/sampling-order ketika di R&D kalau masih di program cam harusnya belum ada"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Kartu SPK A6 adalah lembar kerja fisik yang memuat nomor SPK, QR traceability, POM (Point of Measure), dan strip urgensi antrean pabrik. Kartu ini dicetak untuk ditempelkan pada mesin rajut dan meja kerja operator di lantai produksi.

Sebelumnya, tombol **"Kartu SPK A6"** di header modal `SamplingSpkDetailDialog` selalu dirender tanpa memedulikan tahap SPK:
1. Ketika SPK baru masuk (`SPK Masuk / Sales Deal`), tombol sudah ada.
2. Ketika SPK sedang dalam tahap `Penentuan Alur Desain`, tombol sudah ada.
3. Ketika SPK masih di tahap `Program CAM` (di mana programmer rajut baru merancang pola garmen, instruksi panah, dan tenselity), tombol sudah ada.

Ini membingungkan pengguna pabrik:
- Kartu SPK A6 adalah artefak yang menyertai barang saat **mulai diproduksi**.
- Selama pola belum selesai diprogram di CAM, SPK belum turun ke lantai kerja nyata.
- Menyediakan tombol cetak sebelum tahap R&D selesai dipersiapkan membuka risiko kartu tercetak prematur dengan data spesifikasi garmen yang belum tervalidasi atau belum lengkap.

### Solusi & Hasil Akhir
1. Tombol header **"Kartu SPK A6"** hanya muncul ketika SPK telah resmi berada di lantai kerja **R&D** (`MACHINE_KNITTING` hingga `PENGEMASAN`) atau tahap setelahnya (`IN_DELIVERY`, `ACC_APPROVED`).
2. Saat SPK masih berada di tahap persiapan pra-produksi (`SPK Masuk`, `Penentuan Alur`, atau `Program CAM`), tombol tersebut disembunyikan.
3. Begitu programmer CAM menekan tombol **"Mulai Pembuatan ->"** di footer dialog, sistem secara atomik memvalidasi lembar kerja CAM, memajukan tahap ke `MACHINE_KNITTING`, dan otomatis membuka Kartu SPK A6 untuk pertama kalinya. Sejak detik itu dan seterusnya, tombol manual "Kartu SPK A6" di header akan selalu tersedia bila dialog dibuka kembali.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Telusuri Sumber Kebenaran Domain (`SamplingPipelineStage`)**
   Urutan tahap sampling (`order`) terdefinisi di enum `SamplingPipelineStage`:
   - `NEW_INTAKE` (1)
   - `FLOW_REVIEW` (2)
   - `CAM_PROGRAMMING` (3)
   - `MACHINE_KNITTING` (4) — Titik awal SPK masuk ke lantai R&D
   - ... hingga `PENGEMASAN` (9)
   - `IN_DELIVERY` (10) & `ACC_APPROVED` (11)

2. **Langkah 1: Identifikasi Batasan State di UI Layer (`SamplingSpkDetailDialog.kt`)**
   Hitung predikat boolean berbasis urutan tahap domain:
   ```kotlin
   val canPrintSpkCard = order.pipelineStage.order >= SamplingPipelineStage.MACHINE_KNITTING.order
   ```
   Kenapa `>= MACHINE_KNITTING.order` dan bukan hanya rentang `isRdStage`?
   Karena ketika SPK telah selesai melewati R&D (misalnya sedang di meja pengiriman `IN_DELIVERY` atau `ACC_APPROVED`), tim sampling atau supervisor tetap berhak membuka/mencetak ulang kartu riwayat A6 tersebut. Namun saat masih `< MACHINE_KNITTING.order` (termasuk `CAM_PROGRAMMING`), kartu tersebut belum boleh muncul.

3. **Langkah 2: Bungkus Komponen Tombol di Header dengan Kondisi**
   Di bagian baris header dialog, bungkus `ClayButton` dengan `if (canPrintSpkCard)`.

4. **Langkah 3: Verifikasi Kompilasi Multiplatform**
   Jalankan `./gradlew :app:shared:compileKotlinWasmJs` dan `:app:shared:compileKotlinJvm` untuk memastikan tidak ada pemutusan state atau kesalahan sintaks.

---

## 🔍 3. Bedah Kode Blok per Blok

### File: `SamplingSpkDetailDialog.kt`

#### Blok A — Evaluasi Gerbang Tahap R&D
```kotlin
// Lantai R&D (rajut s/d kemas): hasil sampel baru diketahui di sini.
val isRdStage = order.pipelineStage.order in
    SamplingPipelineStage.MACHINE_KNITTING.order..SamplingPipelineStage.PENGEMASAN.order

// Kartu SPK A6 baru boleh dibuka manual ketika SPK sudah masuk lantai R&D / produksi (rajut ke atas).
// Saat masih di tahap SPK Masuk, Penentuan Alur, atau Program CAM, kartu fisik belum dicetak.
val canPrintSpkCard = order.pipelineStage.order >= SamplingPipelineStage.MACHINE_KNITTING.order
```
- **`isRdStage`**: Menandai apakah lembar kerja yang aktif adalah input hasil riil R&D (`RdResultSection`).
- **`canPrintSpkCard`**: Menjadi gerbang visibilitas kartu A6. Evaluasi dilakukan secara deklaratif langsung dari properti entity `order.pipelineStage.order`.

#### Blok B — Kondisional Rendering di Header Modal
```kotlin
if (canPrintSpkCard) {
    ClayButton(
        text = "Kartu SPK A6",
        style = ClayButtonStyle.Secondary,
        fontSize = 11.sp,
        onClick = openSpkCard
    )
}
ClayBadge(
    text = order.pipelineStage.displayName,
    tint = samplingStageTint(order.pipelineStage)
)
```
- Jika `canPrintSpkCard` bernilai `false` (misal saat status masih `Program CAM`), tombol tidak dikomposisikan ke pohon UI. Header hanya menampilkan badge tahap dan tombol tutup dialog.
- Jika `canPrintSpkCard` bernilai `true`, tombol `ClayButton` dirender rapi di sebelah kiri badge tahap.

---

## 🛡️ 4. Jebakan Pemula (Common Pitfalls)

1. **Hardcode Pengecekan Nama Tahap (`order.pipelineStage.name == "..."`)**:
   - *Salah*: `if (order.pipelineStage != SamplingPipelineStage.CAM_PROGRAMMING)`
   - *Bahaya*: SPK baru (`NEW_INTAKE`) dan penentuan alur (`FLOW_REVIEW`) akan tetap memunculkan tombol secara salah. Gunakan perbandingan ordinal/order numerik yang mencakup seluruh tahap sebelum R&D.

2. **Memutus Integrasi Otomatis Saat "Mulai Pembuatan"**:
   - Tombol "Mulai Pembuatan ->" di footer `CAM_PROGRAMMING` tetap harus memanggil `openSpkCard()`. Jangan sampai menghapus aksi otomatis pencetakan ketika SPK baru saja dilepas ke mesin rajut.

3. **Mengabaikan Tahap Lanjutan Setelah R&D**:
   - Jika hanya mengecek `if (isRdStage)`, maka saat SPK sudah masuk `IN_DELIVERY` atau `ACC_APPROVED`, tombol cetak kartu SPK tiba-tiba menghilang kembali. Dengan `order.pipelineStage.order >= SamplingPipelineStage.MACHINE_KNITTING.order`, riwayat cetak tetap dapat diakses sepanjang siklus hidup order setelah produksi dimulai.

---

## 🧪 5. Verifikasi & Tantangan Mandiri

### Checklist Verifikasi
1. Buka http://localhost:3000/sampling-order.
2. Klik kartu SPK di kolom **1. SPK Masuk** atau **2. Penentuan Alur** → Buka Detail SPK → Pastikan tombol **"Kartu SPK A6" tidak ada**.
3. Klik kartu SPK di kolom **3. Program CAM** → Buka Detail SPK → Pastikan tombol **"Kartu SPK A6" tidak ada**.
4. Klik tombol **"Mulai Pembuatan ->"** di tahap CAM → SPK berpindah ke R&D dan tab PDF Kartu SPK A6 otomatis terbuka.
5. Klik kartu SPK di kolom **4. R&D** (atau salah satu sub-tahap rajut/linking/cuci/qc/kemas) → Buka Detail SPK → Pastikan tombol **"Kartu SPK A6" sekarang muncul di header**.
