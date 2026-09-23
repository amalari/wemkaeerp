# 🎓 Modul Pembelajaran: Perbaikan Grid Alignment Kontak & Fallback Brand/Perusahaan di WeMade ERP

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UI Grid Rhythm, Compose Adaptive Layouts, Null-Safety UI State, Decomposing Large Screen Components  
> **Prasyarat**: Dasar Jetpack Compose / Compose Multiplatform, State & Modifier, WeMade Design System Tokens  
> **Referensi Task**: Card Kontak Tidak Rapi Saat Brand/Perusahaan Kosong (`crm-sales/contacts`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat merancang tampilan katalog atau direktori kartu berbasis grid (seperti `LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 340.dp))`):
1. **Vertical Rhythm Break**: Setiap kartu mewakili satu entitas (dalam hal ini `Contact`). Jika satu kartu memiliki badge brand `[ Morfeen Studio ]`, tinggi header menjadi ~56dp. Namun jika ada entitas kontak yang belum/tidak memiliki brand (misal order perorangan non-bisnis), badge tersebut sebelumnya tidak dirender sama sekali (`brand?.let { ... }`).
2. **Cascading Row Offset**: Hilangnya baris badge ini menyebabkan seluruh elemen di bawahnya (baris info WhatsApp & Email, pill transaksi deal, dan tombol aksi "Chat WhatsApp" / "Lihat Deal") bergeser naik ke atas sekitar 24dp.
3. **Mismatched Baseline**: Akibatnya, pada baris grid yang sama, kartu yang bersebelahan memiliki tinggi berbeda, garis bawah kartu tidak sejajar, dan elemen visual seperti ikon WhatsApp melompat tidak simetris secara horizontal.

### Analogi Sederhana
Bayangkan rak display botol minuman di minimarket di mana setiap rak memiliki slot label harga di bagian leher botol. Jika ada satu botol yang tidak diberi label harga sama sekali sehingga lehernya dibiarkan kosong lalu tutup botolnya diletakkan lebih rendah, deretan botol di rak tersebut akan terlihat pincang dan berantakan. Solusi yang benar adalah memberikan label netral bertuliskan *"Non-Kemasan / Retail"* dengan bentuk dan ukuran yang identik agar ritme baris display tetap presisi.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus membenahi kartu grid yang layout-nya tidak rapi karena field opsional:

1. **Langkah 1: Identifikasi Komponen Slot Kosong**
   - Periksa di mana percabangan `null` terjadi pada Composable (`brand?.let { ... }`).
   - Tentukan apakah slot tersebut harus tetap menempati ruang (*slot reservation*) atau digantikan oleh badge fallback netral.
2. **Langkah 2: Desain Visual Fallback Sesuai Design System**
   - Jangan gunakan warna keras/kontras tinggi untuk ketiadaan data.
   - Gunakan token `WeMadeColors.SurfaceMuted` (abu-abu slate lembut), teks `WeMadeColors.OnSurfaceMuted`, dan outline `WeMadeColors.Border` agar terbaca sebagai status netral tanpa bersaing dengan warna brand aktif.
3. **Langkah 3: Sinkronisasi Elemen Kartu Lainnya (Deal Summary)**
   - Periksa apakah ada elemen kondisional lain yang berisiko membuat tinggi kartu tidak seragam (misalnya `dealSummary`).
   - Sediakan fallback baris tetap (misalnya `"0 Deals - Belum ada transaksi"` dengan warna muted) agar tinggi seluruh kartu selalu identik di setiap baris grid.
4. **Langkah 4: Penjagaan Batas Ukuran File (Decomposition)**
   - Sesuai Project Rule §14, file presentasi memiliki batas maksimal (soft limit 400, hard limit 600 baris).
   - Ekstrak dialog atau sub-panel (`ContactDealsDialog`) ke file terpisah agar file induk tetap ramping dan fokus.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Fallback Pill pada Header Kartu Kontak
Sebelumnya di [`ContactsPane.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/ContactsPane.kt):
```kotlin
// ❌ SEBELUM: Melewatkan render saat brand kosong
brand?.let {
    Spacer(Modifier.height(ClaySpacing.Xs))
    BrandPill(text = it, tint = tint)
}
```

Diubah menjadi:
```kotlin
// ✅ SESUDAH: Menjaga slot ketinggian yang identik dengan pill netral
Spacer(Modifier.height(ClaySpacing.Xs))
if (brand != null) {
    BrandPill(text = brand, tint = tint)
} else {
    BrandPill(
        text = "Tanpa Perusahaan",
        tint = WeMadeColors.SurfaceMuted,
        textColor = WeMadeColors.OnSurfaceMuted,
        outline = WeMadeColors.Border
    )
}
```

**Mengapa blok ini ditulis begini?**
- `Spacer(Modifier.height(ClaySpacing.Xs))` selalu dieksekusi secara deterministik.
- Ketika kontak tidak memiliki brand atau perusahaan, label `"Tanpa Perusahaan"` ditampilkan dengan warna lembut (`SurfaceMuted` dan `OnSurfaceMuted`).
- Tinggi kolom header tetap tepat ~56dp, sejajar sempurna dengan avatar bundar 52dp di sebelah kirinya dan sejajar dengan kartu-kartu tetangga di grid.

---

### Blok B: Standardisasi Baris Ringkasan Deal
```kotlin
Spacer(Modifier.height(ClaySpacing.Md))
val hasDeals = dealSummary != null && dealSummary.first > 0
Row(
    modifier = Modifier
        .clayFlat(
            shape = ClayShapes.Pill,
            background = WeMadeColors.SurfaceMuted,
            outline = WeMadeColors.Border,
            borderWidth = ClayBorder.Hairline
        )
        .padding(horizontal = 12.dp, vertical = 5.dp)
) {
    Text(
        text = if (hasDeals) {
            "${dealSummary.first} Deals - ${formatIdr(dealSummary.second)}"
        } else {
            "0 Deals - Belum ada transaksi"
        },
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = if (hasDeals) WeMadeColors.OnSurface else WeMadeColors.OnSurfaceMuted,
        maxLines = 1
    )
}
```

**Mengapa blok ini ditulis begini?**
- Jika sebuah kontak baru dibuat dan belum memiliki deal, kartu tersebut tidak kehilangan baris ini.
- Teks `"0 Deals - Belum ada transaksi"` dengan warna `OnSurfaceMuted` memberikan kepastian status transaksi sekaligus menjaga ritme vertikal kartu.

---

### Blok C: Pemecahan File (Dekomposisi) Sesuai Aturan Ratchet §14
Untuk mematuhi aturan batas ukuran file:
- `ContactDealsDialog` dan `DealSummaryRow` dipindahkan ke [`ContactDealsDialog.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/ContactDealsDialog.kt).
- [`ContactsPane.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/deal/components/ContactsPane.kt) turun dari 626 baris menjadi **528 baris** (di bawah hard limit 600 baris).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| **Pill Fallback "Tanpa Perusahaan"** | Menyembunyikan elemen (`if (brand != null)`) | Menjaga grid cadence dan memberikan kejelasan status pelanggan | Kartu melompat naik-turun, tombol aksi tidak sejajar di baris yang sama |
| **Warna Token `SurfaceMuted`** | Warna acak atau warna primer | Menunjukkan bahwa data ini bersifat placeholder/kosong, bukan brand korporat | Pengguna mengira "Tanpa Perusahaan" adalah nama merek asli jika diberi warna pekat |
| **File Decomposition** | Menumpuk semua dialog di 1 file | Menjaga Single Responsibility dan mematuhi batas ukuran file arsitektur | File menjadi God Composable (> 600 baris), sulit direview dan mudah konflik git |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan "Invisible Box" / Spacer Kosong**:
   - Pemula sering kali hanya menambahkan `Spacer(Modifier.height(20.dp))` saat data null.
   - *Kenapa bahaya*: Ruang kosong tanpa penjelasan membuat user merasa ada bug aset yang gagal termuat (*missing image / broken layout*). Badge dengan teks jelas `"Tanpa Perusahaan"` jauh lebih komunikatif.
2. **Jebakan Hardcoded Color**:
   - Jangan sekali-kali menulis `Color(0xFFE2E8F0)` atau `Color.Gray`. Selalu gunakan `WeMadeColors.SurfaceMuted`, `WeMadeColors.OnSurfaceMuted`, atau `WeMadeColors.Border`.

---

## 🧪 6. Bagaimana Cara Memverifikasi?

1. **Verifikasi Kompilasi Multiplatform**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
2. **Verifikasi Visual**:
   - Buka halaman `http://localhost:3000/crm-sales/contacts`.
   - Periksa kontak tanpa brand (seperti "Test").
   - Pastikan terdapat badge `[ Tanpa Perusahaan ]` dengan latar abu-abu lembut dan teks rapi.
   - Pastikan tinggi kartu "Test" sejajar sempurna dengan kartu di sebelahnya (misalnya "Ayu Lestari / Nuansa Wear").
