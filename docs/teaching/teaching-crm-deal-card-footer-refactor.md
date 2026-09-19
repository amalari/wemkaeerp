# 🎓 Modul Pembelajaran: Refaktorisasi Action Bar & Stepper Kartu Deal CRM (Option 1: Clean Action Bar)

> **Level Target**: Junior to Mid Frontend / Compose Multiplatform Developer  
> **Topik Utama**: UI/UX Heuristics, Space Economy, Neo-Brutalism & Claymorphism Design System, Visual Hierarchy, Compose Multiplatform  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform, Design Token System, Prinsip Hierarki Visual  
> **Referensi File**: [DealsPane.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/DealsPane.kt), [MiniPipelineIndicator.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/MiniPipelineIndicator.kt)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Lapangan
Di papan Kanban atau Grid Kartu CRM (lebar kolom berkisar 280–340dp), kartu sering kali menjadi "tempat pembuangan akhir" segala tombol aksi. Tanpa disadari:
1. **Dua Tombol Bersaing (Competing CTA Affordance)**: Ada dua tombol tebal mencolok berdampingan (*Amber* untuk `+ PO (Opsional)` dan *Green* untuk `Invoice DP (50%)`). Keduanya memakai outline tebal dan hard-shadow 3D, sehingga mata pengguna bingung mana langkah utama berikutnya (*next logical step*).
2. **Text Wrap Berantakan**: Karena tombol terlalu lebar, teks `"Invoice DP"` terpaksa patah menjadi 2 baris `(50%)` di baris kedua.
3. **Redundansi Visual**: Informasi Sales PIC menampilkan ikon siluet orang (`IconUser`) **sekaligus** avatar inisial (`[J]`). Ini memakan 65dp ruang horizontal secara sia-sia.
4. **Teks Terpotong pada Stepper**: Label milestone ke-4 `"Pelunasan"` terpotong menjadi `"Pelunasa"` karena batasan lebar kolom mini-stepper (`48.dp`).

### Analogi Sederhana
Bayangkan sebuah kartu boarding pass pesawat. Informasi utama (gate, boarding time, seat) harus langsung terlihat jelas. Jika di bagian bawah dicetak tombol *"Beli Makanan Ringan"* dan *"Beli Tiket Tambahan"* dengan ukuran font raksasa yang sama persis dengan *"Boarding Gate"*, penumpang akan kewalahan menyaring informasi pentingnya.

### Hasil Akhir yang Diharapkan
Kartu memiliki **hierarki visual yang tegas**:
- **1 Avatar PIC tunggal** yang rapi dan hemat tempat.
- **Secondary CTA** (`+ PO`) yang ramping dengan ikon dokumen kanvas (`IconNote`), tidak mendominasi panggung.
- **Primary Hero CTA** (`Invoice DP (50%)`) yang solid, jelas, dan muat 1 baris tanpa terpotong.
- **Stepper 4 milestone** yang bersih dengan teks `"Lunas"` (tidak terpotong).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Refaktorisasi

1. **Langkah 1: Audit Space Budgeting**
   - Hitung total lebar kartu: ~340dp.
   - Kurangi padding kartu: 16dp kiri + 16dp kanan = 32dp.
   - Sisa lebar usable: ~308dp.
   - Bagikan: PIC butuh ~60dp, Aksi Sekunder butuh ~75dp, Aksi Primer butuh ~150dp, spacing ~16dp. Total ~301dp (Muat pas 1 baris!).
2. **Langkah 2: Perbaiki Stepper Label Truncation (`MiniPipelineIndicator.kt`)**
   - Ganti label `"Pelunasan"` (9 huruf) menjadi `"Lunas"` (5 huruf).
   - Singkirkan literal `Color(0xFFF59E0B)` dan gunakan token resmi `WeMadeColors.Warning`.
3. **Langkah 3: Pangkas Redundansi Elemen PIC (`DealsPane.kt`)**
   - Hapus siluet ganda `IconUser`.
   - Gunakan 1 lingkaran avatar inisial `picInitials` (32dp) dengan label `"Sales PIC"`.
4. **Langkah 4: Terapkan Pola Primary vs Secondary CTA**
   - Buat tombol `+ PO` ringkas dengan `leadingIcon = { IconNote(...) }` dan background netral `WeMadeColors.SurfaceMuted`.
   - Tombol `Invoice DP (50%)` tetap mempertahankan warna hero `WeMadeColors.Success`.
5. **Langkah 5: Pematuhan Rule Ratchet Ukuran File**
   - Pastikan total baris file setelah diubah tidak melebihi baris awal (699 -> 698 baris).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Stepper Label & Token Konsistensi (`MiniPipelineIndicator.kt`)
```kotlin
// Step 4: Lunas (sebelumnya: Pelunasan yang terpotong menjadi "Pelunasa")
MilestoneStep(
    iconState = if (settlementDone) StepIconState.DONE else StepIconState.PENDING,
    label = "Lunas",
    labelColor = if (settlementDone) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted
)
```
**Mengapa blok ini ditulis begini?**
- `MilestoneStep` menggunakan kolom dengan lebar fix `48.dp`. Teks `"Pelunasan"` membutuhkan ~56dp sehingga huruf terakhir ter-*clip*. Menggantinya dengan `"Lunas"` menyampaikan arti bisnis yang persis sama namun pas sempurna di dalam 48dp.

### Blok B: Sales PIC Tunggal yang Bersih (`DealsPane.kt`)
```kotlin
// Sales PIC: Avatar inisial bersih & label
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(WeMadeColors.Warning)
            .border(ClayBorder.Hairline, WeMadeColors.Outline, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = picInitials,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
    }
    Column {
        Text(
            text = "Sales PIC",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = WeMadeColors.OnSurfaceMuted
        )
    }
}
```
**Mengapa blok ini ditulis begini?**
- **Single Source of Identification**: Lingkaran avatar inisial sudah memberikan identitas jelas siapa PIC-nya (misal: "JM"). Menaruh siluet generic orang di samping inisial adalah *visual noise* yang mubazir ruang.
- Memakai token `WeMadeColors.Warning` menjamin warna konsisten tanpa hardcode hexadecimal.

### Blok C: Aksi Sekunder & Primer Berdampingan
```kotlin
// Action buttons: Compact Secondary "+ PO" & Primary "Invoice DP (50%)"
Row(
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
    verticalAlignment = Alignment.CenterVertically
) {
    ClayCardButton(
        text = "+ PO",
        containerColor = WeMadeColors.SurfaceMuted,
        leadingIcon = {
            IconNote(
                modifier = Modifier.size(13.dp),
                color = WeMadeColors.OnSurface
            )
        },
        horizontalPadding = 10.dp,
        onClick = onUploadPo
    )
    val invoiceLabel = if (deal.stage == DealStage.IN_PRODUCTION || deal.stage == DealStage.WON) {
        "Invoice Pelunasan"
    } else {
        "Invoice DP (50%)"
    }
    ClayCardButton(
        text = invoiceLabel,
        containerColor = WeMadeColors.Success,
        onClick = onCreateInvoice
    )
}
```
**Mengapa blok ini ditulis begini?**
- Label dipersingkat dari `"+ PO (Opsional)"` menjadi `"+ PO"`. Status opsional adalah petunjuk form yang diletakkan di dalam dialog modal upload, bukan di judul tombol.
- `leadingIcon` memanfaatkan kanvas vektor `IconNote` yang sudah ada di design system (sesuai aturan dilarang memakai emoji Unicode seperti 📄).
- `containerColor = WeMadeColors.SurfaceMuted` memberikan kontras lembut (secondary) sehingga tidak berebut perhatian dengan tombol hijau `WeMadeColors.Success` (primary).

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Jebakan "Menambahkan Komponen Duplikat Baru"**:
   - *Kesalahan*: Membuat fungsi baru bernama `ClaySecondaryCardButton` yang 90% isinya sama dengan `ClayCardButton`.
   - *Solusi Benar*: Cukup tambahkan parameter opsional `horizontalPadding` dan `leadingIcon` pada `ClayCardButton` yang sudah ada, sehingga kode tetap DRY (Don't Repeat Yourself).
2. **Jebakan Emoji Unicode di Multiplatform**:
   - *Kesalahan*: Menulis `Text("📄 + PO")`.
   - *Akibat*: Di Compose Web/Wasm atau Linux Skiko, karakter emoji sering gagal dimuat dan dirender sebagai kotak tahu kosong (`▯`).
   - *Solusi Benar*: Selalu gunakan Canvas vector icon dari `ClayIcons.kt`.
3. **Jebakan Melanggar Ratchet Rule Baris Kode**:
   - *Kesalahan*: Menambah 20 baris kode ke file yang sudah melampaui soft/hard limit (>600 baris).
   - *Solusi Benar*: Rapikan baris-baris redundan dan padatkan struktur sehingga baris akhir tetap lebih sedikit atau sama dengan baris awal (`wc -l` 699 -> 698).

---

## 🧪 5. Verifikasi & Pengujian Mandiri

1. **Kompilasi Multiplatform**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
2. **Verifikasi Visual di Browser**:
   - Buka `http://localhost:3000/crm-sales/deals`.
   - Periksa kartu deal pada berbagai lebar layar. Pastikan:
     - Avatar inisial hanya 1 lingkaran.
     - Tombol `+ PO` tampak rapi dengan ikon dokumen di samping kirinya.
     - Tombol `Invoice DP (50%)` muat 1 baris utuh tanpa wrap.
     - Milestone terakhir pada stepper terbaca `"Lunas"` secara utuh tanpa terpotong.
