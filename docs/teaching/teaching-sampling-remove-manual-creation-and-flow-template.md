# 🎓 Modul Pembelajaran: Penyederhanaan Modul Sampling — Menghilangkan Pembuatan Manual SPK & Template Flow Pabrik

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Single Source of Truth (SSOT), Business Domain Alignment, Pembersihan Antarmuka (UI Decluttering)  
> **Prasyarat**: Siklus Order Garmen (CRM Deal -> Sampling -> Produksi), Jetpack Compose / Compose Multiplatform  
> **Referensi Task**: Penghapusan Tombol "Template Flow Pabrik" dan "Buat SPK Sample" di Halaman Sampling (`/sampling-order`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam ERP manufaktur garmen modern (seperti WeMade ERP), alur bisnis sampling merupakan kelanjutan langsung dari kesepakatan penjualan:
1. **Single Source of Truth (SSOT)**:
   - SPK (Surat Perintah Kerja) Sample **bukan** sesuatu yang dibuat secara acak atau mandiri dari layar sampling.
   - SPK Sample harus memiliki silsilah (*lineage*) bisnis yang jelas: berasal dari mana (`DealId`), untuk klien/brand siapa (`ContactId`), dengan desain sampling apa, dan didanai melalui invoice apa (misal Invoice Sampling 100% atau Invoice DP).
2. **Kekacauan Akibat Tombol Manual**:
   - Adanya tombol manual `+ Buat SPK Sample` di modul sampling membuka celah tim sampling membuat SPK "liar" (*rogue orders*) yang tidak terikat ke kesepakatan deal resmi atau persetujuan pembayaran.
   - Tombol `Template Flow Pabrik` di header kanban modul sampling menimbulkan kebingungan bagi operator karena alur proses seharusnya dikonfigurasi per desain di dalam detail SPK atau di menu master konfigurasi pabrik.

### Analogi Sederhana
Di sebuah restoran, koki di dapur (**Modul Sampling**) tidak menulis pesanan makanan sendiri dari meja dapur lalu memasaknya. Pesanan makanan **hanya boleh masuk** dari tablet pelayan di depan (**Modul CRM/Deal**) setelah pelanggan memesan dan tagihan diproses. Jika ada tombol "Buat Pesanan Makanan" di layar koki, koki bisa salah memasak pesanan yang belum dibayar atau tidak diinginkan pelanggan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Pembersihan (Order of Operations)

Jika diminta menghapus tombol/aksi dari antarmuka karena perubahan logika bisnis:

1. **Langkah 1: Identifikasi Titik Masuk (Entry Points)**
   - Cari di mana tombol tersebut berada (top toolbar di [`SamplingWorkspaceScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/SamplingWorkspaceScreen.kt)).
   - Cari apakah ada tombol serupa di tempat lain (misalnya tombol di *empty state* ketika daftar order kosong).
2. **Langkah 2: Bersihkan State & Dialog Terkait**
   - Hapus state penampung dialog yang kini tidak terpakai (`isDefaultFlowDialogOpen`).
   - Hapus pemanggilan dialog template alur pabrik dan dialog pembuatan order manual.
3. **Langkah 3: Perbarui Edukasi State Kosong (Empty State)**
   - Jangan tinggalkan user dalam kebingungan jika daftar kosong.
   - Ubah teks pada empty state dari ajakan membuat manual menjadi penjelasan alur sistem:  
     *"SPK Sample otomatis masuk saat pesanan sampling disetujui di modul CRM Deal."*
4. **Langkah 4: Eliminasi Unused Imports & Verifikasi Kompilasi**
   - Hapus import komponen dialog dan ikon yang sudah tidak terpakai.
   - Pastikan kompilasi JVM dan WasmJs berjalan tanpa hambatan.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Toolbar Header yang Bersih
Sebelumnya di [`SamplingWorkspaceScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/SamplingWorkspaceScreen.kt):
```kotlin
// ❌ SEBELUM: Ada tombol Template Flow Pabrik & + Buat SPK Sample
Row(
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm),
    verticalAlignment = Alignment.CenterVertically
) {
    ClayButton(
        text = "Template Flow Pabrik",
        style = ClayButtonStyle.Secondary,
        onClick = { isDefaultFlowDialogOpen = true }
    )
    ClayButton(
        text = "+ Buat SPK Sample",
        style = ClayButtonStyle.Primary,
        onClick = { viewModel.onEvent(SamplingUiEvent.OpenCreateDialog) }
    )
}
```

Diubah menjadi:
```kotlin
// ✅ SESUDAH: Toolbar hanya menampilkan identitas modul dan total SPK aktif
Row(
    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md),
    verticalAlignment = Alignment.CenterVertically
) {
    Column {
        Text(
            text = "ORDER SAMPLING",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = WeMadeColors.OnSurface
        )
        Text(
            text = "Pipeline Kanban SPK — Program CAM, Rajut, Finishing & ACC Buyer",
            fontSize = 11.sp,
            color = WeMadeColors.OnSurfaceMuted
        )
    }

    if (state.orders.isNotEmpty()) {
        ClayBadge(
            text = "${state.orders.size} SPK",
            tint = WeMadeColors.Primary
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
- Modul Order Sampling kini menjadi ruang kerja eksekusi murni (*pure operational taskboard*). Operator fokus melihat kartu Kanban, mengelola lembar kerja CAM, Rajut, Finishing, dan ACC Buyer tanpa terdistraksi tombol konfigurasi atau entri data duplikat.

---

### Blok B: Empty State Informatif
```kotlin
// ✅ Empty state yang sinkron dengan alur terintegrasi
Column(horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
        text = "Belum Ada SPK Sample",
        fontSize = 16.sp,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface
    )
    Spacer(Modifier.padding(ClaySpacing.Sm))
    Text(
        text = "SPK Sample otomatis masuk saat pesanan sampling disetujui di modul CRM Deal.",
        fontSize = 12.sp,
        color = WeMadeColors.OnSurfaceMuted
    )
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan Desain | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| **Menghapus Tombol Manual di Sampling** | Membiarkan tombol dengan peringatan | Mencegah anomali data (orphan SPK tanpa deal referensi) | SPK lahir tanpa data deal/invoice, pembukuan keuangan garmen berantakan |
| **Penyusutan File (350 -> 250 baris)** | Membiarkan dead code tersembunyi | Menjaga kode bersih dan mematuhi batas ukuran file §14 | Dead code membebani bundle dan membingungkan developer baru yang membaca repo |

---

## 🧪 5. Cara Membuktikan Kodingan Bekerja

1. **Uji Kompilasi Target**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
2. **Uji Antarmuka**:
   - Buka `http://localhost:3000/sampling-order`.
   - Header kanan kini bersih (tanpa tombol "Template Flow Pabrik" dan "+ Buat SPK Sample").
   - Papan Kanban SPK tetap berfungsi normal untuk memajukan tahap produksi sampel.
