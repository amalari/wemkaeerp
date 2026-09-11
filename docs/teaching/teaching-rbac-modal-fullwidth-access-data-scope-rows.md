# 🎓 Modul Pembelajaran: Pemisahan Baris Level Akses & Jangkauan Data pada Modal RBAC

> **Level Target**: Junior to Mid Compose Multiplatform Developer  
> **Topik Utama**: Compose Multiplatform Layouting, Claymorphism Design System, UI/UX Responsive Hierarchy, RBAC Matrix  
> **Prasyarat**: Dasar Compose Multiplatform Layout (`Row`, `Column`, `Box`), State Management, dan Konsep Dasar Token Clay WeMade  
> **Referensi Task**: Task RBAC Modal Layout Adjustment (Level Akses 1 Row, Jangkauan Data 1 Row)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat merancang formulir antarmuka kompleks seperti penugasan hak akses modul konveksi (RBAC):
- Pengguna di pabrik sering melihat opsi **Level Akses** (*Hanya Lihat*, *Input & Kerja*, *Akses Penuh*) dan **Jangkauan Data** (*Data Sendiri*, *Data Bawahan*, *Semua Data*).
- Jika kedua kelompok kontrol ini diletakkan berdampingan dalam 1 `Row` pada modal dengan batas lebar 560dp, masing-masing kolom hanya mendapatkan ruang sekitar ~240dp.
- Hasilnya: Tombol chip seperti **"Data Bawahan"** terpaksa mengalami pelipatan teks canggung (*awkward line-breaking*, kata "Data" di atas dan "Bawahan" di bawah), label terpotong, dan kontrol terasa sempit. Hal ini mengurangi tingkat kenyamanan baca (*readability*) dan keterbacaan visual operator pabrik.

### Analogi Sederhana
Bayangkan lembar formulir fisik di kantor pabrik:
- Jika kita memaksakan 2 pertanyaan bertingkat penting ke dalam 1 kolom sempit berdampingan, tulisan tangan petugas akan bertumpuk dan rentan salah conteng.
- Sebaliknya, memberikan 1 baris penuh untuk *Pertanyaan A (Tingkat Wewenang)* dan 1 baris penuh di bawahnya untuk *Pertanyaan B (Cakupan Wilayah Data)* membuat setiap pilihan bernapas lega, tidak ada teks terpotong, dan keputusan dapat diambil secara tegas dalam sekali pandang.

### Hasil Akhir yang Diharapkan
- **Level Akses**: Mengambil 1 baris penuh (`fillMaxWidth()`) berisi 3 chip wewenang yang seimbang.
- **Jangkauan Data**: Ditempatkan di baris bawahnya (`fillMaxWidth()`) berisi 3 chip lingkup data (atau badge *Seluruh Pabrik* jika modul kolektif). Teks chip tidak melipat, berjarak proporsional, dan tetap pas di dalam jendela modal tanpa *overflow*.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diminta menyempurnakan tata letak modal seperti ini dari awal:

1. **Langkah 1: Identifikasi Hierarki Konten & Ruang Pandang (Spatial Budget)**
   - Cek batas lebar modal (`ClayCard` `widthIn(max = 560.dp)`).
   - Hitung berapa banyak chip yang ada per grup (3 chip di Level Akses, 3 chip di Jangkauan Data).
   - Sadari bahwa 3 chip di ruang 240dp pasti memicu *line wrapping* jika label memiliki 2 kata seperti "Data Bawahan" atau "Akses Penuh".

2. **Langkah 2: Dekomposisi Tata Letak (Row Side-by-Side ke Stacked Columns)**
   - Hapus pembungkus `Row` horizontal yang membagi ruang dengan `Modifier.weight(1f)`.
   - Ubah menjadi dua `Column(modifier = Modifier.fillMaxWidth())` yang ditumpuk secara vertikal.

3. **Langkah 3: Atur Irama Vertikal (*Vertical Rhythm & Spacing*)**
   - Berikan `Spacer(modifier = Modifier.height(12.dp))` di atas Level Akses.
   - Berikan `Spacer(modifier = Modifier.height(10.dp))` sebagai pemisah antara Level Akses dan Jangkauan Data.
   - Sesuaikan `Spacer` sebelum tombol aksi footer agar tinggi modal tetap proporsional dan tidak memicu *scroll* yang tidak perlu.

4. **Langkah 4: Standarisasi Desain Token Clay**
   - Pastikan chip menggunakan `Modifier.clayFlat` dengan token tema `WeMadeColors` (`SurfaceMuted`, `Primary`, `PrimaryDark`, `Border`).
   - Gunakan `Modifier.weight(1f)` pada masing-masing chip di dalam row horizontal agar membagi ruang selebar `fillMaxWidth()` secara merata.

5. **Langkah 5: Verifikasi Visual dengan Browser / Hot Reload**
   - Buka browser dan lihat langsung hasil rendering canvas Compose Multiplatform.
   - Pastikan teks label chip berada dalam 1 baris utuh dan kontras warna konsisten.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah perubahan pada file [AssignDepartmentModal.kt](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/rbac/components/AssignDepartmentModal.kt):

### Blok A: Level Akses (1 Row Penuh)

```kotlin
// 3. Level Akses (Full 1 Row)
Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Level Akses",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface
    )
    Spacer(modifier = Modifier.height(5.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clayFlat(
                shape = ClayShapes.Chip,
                background = WeMadeColors.SurfaceMuted,
                outline = WeMadeColors.Border
            )
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(AccessLevel.VIEW, AccessLevel.OPERATE, AccessLevel.MANAGE).forEach { level ->
            val isSelected = level == selectedAccessLevel
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (isSelected) {
                            Modifier.clayFlat(
                                shape = RoundedCornerShape(6.dp),
                                background = WeMadeColors.Primary,
                                outline = WeMadeColors.PrimaryDark,
                                borderWidth = 1.5.dp
                            )
                        } else {
                            Modifier.clip(RoundedCornerShape(6.dp))
                        }
                    )
                    .clickable { selectedAccessLevel = level }
                    .padding(vertical = 7.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = level.displayName,
                    fontSize = 11.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                )
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- `fillMaxWidth()` pada kontainer luar dan `weight(1f)` pada masing-masing chip menjamin pembagian lebar 33.3% yang simetris di seluruh lebar modal (~500dp).
- Chip yang dipilih (`isSelected`) diberi efek `clayFlat` dengan warna brand `WeMadeColors.Primary`, outline kontras `PrimaryDark`, dan teks putih tebal `WeMadeColors.Surface`.

---

### Blok B: Jangkauan Data (1 Row Penuh di Bawah Level Akses)

```kotlin
Spacer(modifier = Modifier.height(10.dp))

// 4. Jangkauan Data (Full 1 Row di bawah Level Akses)
Column(modifier = Modifier.fillMaxWidth()) {
    Text(
        text = "Jangkauan Data",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = WeMadeColors.OnSurface
    )
    Spacer(modifier = Modifier.height(5.dp))

    if (module.isGlobalOnly) {
        // Jika modul bersifat kolektif pabrik (seperti BOM, HPP, Jadwal Mesin)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.PrimaryContainer,
                    outline = WeMadeColors.Primary
                )
                .padding(vertical = 7.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconGlobe(modifier = Modifier.size(13.dp), color = WeMadeColors.PrimaryDark)
                Text(
                    text = "Seluruh Pabrik (Shared)",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.PrimaryDark
                )
            }
        }
    } else {
        // Jika modul mendukung multi-jangkauan (Data Sendiri / Data Bawahan / Semua Data)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clayFlat(
                    shape = ClayShapes.Chip,
                    background = WeMadeColors.SurfaceMuted,
                    outline = WeMadeColors.Border
                )
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            module.supportedScopes.forEach { scope ->
                val isSelected = scope == selectedScope
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (isSelected) {
                                Modifier.clayFlat(
                                    shape = RoundedCornerShape(6.dp),
                                    background = WeMadeColors.Primary,
                                    outline = WeMadeColors.PrimaryDark,
                                    borderWidth = 1.5.dp
                                )
                            } else {
                                Modifier.clip(RoundedCornerShape(6.dp))
                            }
                        )
                        .clickable { selectedScope = scope }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = scope.shortLabel,
                        fontSize = 11.5.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) WeMadeColors.Surface else WeMadeColors.OnSurfaceMuted
                    )
                }
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Sekarang chip "Data Sendiri", "Data Bawahan", dan "Semua Data" masing-masing memiliki ruang ~160dp.
- Teks `"Data Bawahan"` tidak akan pernah mengalami patahan baris (*line break*), membuat antarmuka terlihat profesional dan rapi.
- Cabang `module.isGlobalOnly` tetap dipertahankan untuk modul sistem tunggal (seperti Inventaris Bahan Baku, Kalkulasi HPP, dan Jadwal Mesin) yang tidak membedakan kepemilikan data antar staf.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan / Keputusan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Stacked Full-Width Rows** | 2-Kolom Berdampingan (*Grid / Row*) | Memberikan ruang horizontal lega untuk label teks multi-kata; tata letak lebih stabil | Teks chip melipat (*word break*) canggung, tombol terlihat kerdil dan padat |
| **Pemisah Vertikal Terukur (`10.dp` - `12.dp`)** | Spasi besar bawaan Material (`16.dp` - `24.dp`) | Menjaga total tinggi modal tetap ringkas tanpa melampaui batas layar viewport Web / Laptop | Modal menjadi terlalu panjang dan memicu scrollbar vertikal di modal |
| **Token Warna Semantik (`WeMadeColors`)** | `Color(0xFF...)` Hardcoded | Mematuhi aturan desain WeMade (§12), konsistensi tema gelap/terang terjaga | Warna melenceng dari brand guideline dan melanggar linter tim |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Asumsi Bahwa Semua Layar Menggunakan Bahasa & Panjang Kata yang Sama**
   - *Kenapa bahaya*: Developer pemula sering menguji dengan kata pendek ("View", "Edit", "All"). Ketika sistem diterjemahkan ke Bahasa Indonesia ("Hanya Lihat", "Data Bawahan"), layout langsung rusak karena kehabisan ruang.
   - *Solusi*: Selalu desain batas komponen berdasarkan teks terpanjang dalam varian lokal (*longest string budget*).

2. **Jebakan 2: Menumpuk Konten Tanpa Memperhatikan Sisa Ruang Viewport**
   - *Kenapa bahaya*: Menambah baris baru bisa mendorong tombol footer ("Tugaskan Divisi") keluar dari layar jika tinggi modal tidak diperhitungkan.
   - *Solusi*: Kurangi sedikit padding vertikal komponen lain (`14.dp` -> `12.dp`, `16.dp` -> `14.dp`) untuk mengompensasi pertambahan baris baru, sehingga tinggi total modal tetap stabil.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

1. **Pengujian Visual Langsung (Real Browser Verification)**:
   - Akses rute `/rbac` di browser dev (`http://localhost:3000/rbac`).
   - Klik tombol `+ Tambahkan Akses` atau tombol `Edit` pada salah satu kartu modul.
   - **Perhatikan**:
     1. "Level Akses" berada di 1 baris tersendiri dengan 3 opsi: *Hanya Lihat*, *Input & Kerja*, *Akses Penuh*.
     2. Tepat di bawahnya, "Jangkauan Data" berada di 1 baris tersendiri dengan 3 opsi: *Data Sendiri*, *Data Bawahan*, *Semua Data*.
     3. Kata **"Data Bawahan"** berada dalam 1 baris lurus tanpa ada pemenggalan suku kata atau teks patah.
     4. Tombol aksi *Batal* dan *Tugaskan Divisi* di bagian bawah tetap terlihat utuh dan tidak terpotong.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Coba ubah mode penugasan ke "Pilih Jabatan Spesifik". Pastikan daftar checkbox jabatan yang muncul tetap dapat di-scroll dengan nyaman tanpa mendorong baris *Level Akses* dan *Jangkauan Data*.
- [ ] **Tantangan 2**: Uji modul yang memiliki sifat `isGlobalOnly = true` (misalnya *Kalkulasi HPP & Biaya*). Pastikan baris *Jangkauan Data* otomatis menampilkan pill badge tunggal `"Seluruh Pabrik (Shared)"` selebar baris penuh.
