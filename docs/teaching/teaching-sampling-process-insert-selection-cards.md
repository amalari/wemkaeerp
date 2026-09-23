# 🎓 Modul Pembelajaran: Redesain Dialog Sisip Alur Proses — Dari Tombol Aksi Menjadi Radio Selection Cards

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: UI/UX Design System, Radio Tile Cards Pattern, Cognitive Dissonance in Forms, Compose Multiplatform  
> **Prasyarat**: Pemahaman Dasar Compose UI, State Management (`mutableStateOf`), Komponen Design System (`ClayCard`, `clayFlat`)  
> **Referensi Task**: Redesain `ProcessFlowInsertDialog` (Opsi Dikerjakan Sendiri vs Vendor Makloon)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata (Cognitive Dissonance)
Sebelumnya, dialog `ProcessFlowInsertDialog` menanyakan di mana proses opsional (seperti Bordir Komputer, Sablon, Cuci) akan dikerjakan dengan menggunakan dua tombol `ClayButton`:
```
[ Dikerjakan Sendiri (Biru/Primary) ]   [ Vendor Makloon (Putih/Secondary) ]
----------------------------------------------------------------------------
[ Batal (Putih/Secondary) ]              [ Sisipkan (Biru/Primary) ]
```

**Kenapa ini aneh dan membingungkan pengguna?**
1. **Dua Pasang Tombol Kembar**: Ada dua pasang tombol Primary-Secondary yang bertumpuk vertikal dengan bentuk yang identik.
2. **Kesan Cancel vs OK**: Karena tombol "Dikerjakan Sendiri" berwarna biru dan "Vendor Makloon" berwarna putih, pengguna secara refleks mengira "Vendor Makloon" adalah tombol Batal/Cancel, padahal itu adalah **pilihan mode kerja** yang sah.
3. **Pelanggaran Pola Mental**: Pilihan konfigurasi (state selection) adalah keputusan data, bukan tindakan eksekusi (action trigger). Menggunakan tombol aksi (`Button`) untuk memilih radio opsi adalah anti-pattern UI klasik.

### Solusi Desain: Clay Radio Selection Cards (Pilihan Murni Tempat Pengerjaan)
Pilihan diubah menjadi **Interactive Radio Selection Cards** yang bersih dan ringkas:
- Pilihan murni dua opsi: **"Dikerjakan di Sini"** (In-house) vs **"Vendor Luar"** (Makloon).
- **Tanpa Input Nama Vendor**: Pada tahap penentuan alur, perancang/merchandiser tidak dibebani menentukan nama PT/CV vendor spesifik. Penunjukan vendor dan penerbitan Surat Jalan menjadi ranah admin produksi saat order sudah siap jalan.
- Memiliki ikon tematik (`IconPackage` untuk in-house, `IconTruck` untuk vendor luar).
- Memiliki radio check indicator (`IconCheckCircle` vs lingkaran radio kosong).
- Outline dan latar belakang berubah warna secara halus saat dipilih (`WeMadeColors.Primary.copy(alpha = 0.08f)` vs `WeMadeColors.Surface`).
- Tombol aksi di footer (`Batal` dan `Sisipkan ke Alur`) menjadi **satu-satunya tombol aksi** di dialog tersebut.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika menemui masalah serupa di mana pemilihan opsi tampak seperti tombol submit/cancel:

1. **Langkah 1: Bedakan Antara "State Selection" vs "Action Trigger"**
   - *State Selection*: Memilih opsi A atau B (tidak mengeksekusi apa pun, hanya mengubah variabel state lokal).
   - *Action Trigger*: Menyimpan perubahan atau menutup modal (Batal / Simpan).
2. **Langkah 2: Rancang Sub-Komponen Seleksi Kartu (`ExecutionOptionCard`)**
   - Buat kontainer yang merespons state `isSelected`:
     - Border tebal saat aktif (`ClayBorder.Thick`) dan border normal saat nonaktif (`ClayBorder.Medium`).
     - Tampilkan radio indicator di sudut kanan atas kartu.
3. **Langkah 3: Integrasikan Penjelasan Kontekstual Dinamis**
   - Jika opsi In-House dipilih, tampilkan catatan operasional workstation pabrik.
   - Jika opsi Subkontrak dipilih, munculkan input nama vendor beserta pengingat Surat Jalan 2 arah (Kirim & Kembali).
4. **Langkah 4: Rapikan Tombol Footer**
   - Jadikan tombol footer berlabel eksplisit: `"Batal"` dan `"Sisipkan ke Alur"`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Komponen Kartu Seleksi (`ExecutionOptionCard`)
```kotlin
@Composable
private fun ExecutionOptionCard(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clayFlat(
                shape = ClayShapes.Card,
                background = if (isSelected) WeMadeColors.Primary.copy(alpha = 0.08f) else WeMadeColors.Surface,
                outline = if (isSelected) WeMadeColors.Primary else WeMadeColors.Outline,
                borderWidth = if (isSelected) ClayBorder.Thick else ClayBorder.Medium
            )
            .clickable(onClick = onClick)
            .padding(ClaySpacing.Md),
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            icon()
            if (isSelected) {
                IconCheckCircle(modifier = Modifier.size(16.dp), color = WeMadeColors.Primary)
            } else {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(ClayShapes.Pill)
                        .background(WeMadeColors.SurfaceMuted)
                        .clayFlat(
                            shape = ClayShapes.Pill,
                            background = WeMadeColors.SurfaceMuted,
                            outline = WeMadeColors.Outline,
                            borderWidth = ClayBorder.Hairline
                        )
                )
            }
        }
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) WeMadeColors.Primary else WeMadeColors.OnSurface
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = WeMadeColors.OnSurfaceMuted,
            lineHeight = 13.sp
        )
    }
}
```

**Mengapa blok ini ditulis begini?**
- Penggunaan `shape = ClayShapes.Card` (radius 16dp) dengan `ClayBorder.Thick` memberikan ketegasan neo-brutalism khas WeMade.
- Latar beraksen `0.08f` alpha memberikan visual feedback yang tenang dan tidak menyilaukan.
- Indikator radio di sudut kanan atas memberi tahu pengguna bahwa ini adalah pilihan eksklusif (radio button), bukan tombol aksi.

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Menggunakan `Button` untuk Mengganti State**:
   - Tombol secara universal memiliki semantik "klik untuk menjalankan sesuatu sekarang juga".
   - Jika tujuannya hanya memilih nilai enum/boolean, gunakan Segmented Control, Chips, atau Radio Cards.
2. **Ketiadaan Radio Indicator**:
   - Jika hanya mengubah warna background kartu, pengguna yang memiliki gangguan persepsi warna mungkin kesulitan membedakan mana yang sedang aktif. Ikon centang (`IconCheckCircle`) menjamin aksesibilitas visual.
3. **Label Aksi yang Terlalu Singkat**:
   - Mengubah tombol footer dari "Sisipkan" menjadi `"Sisipkan ke Alur"` memperjelas dampak klik tombol tersebut terhadap kanban produksi.

---

## ✅ 5. Verifikasi

- **Kompilasi Multiplatform**:
  ```bash
  ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs
  ```
- **Visual Validation**:
  Buka `http://localhost:3000/sampling-order`, klik tombol `+` di garis alur proses, pilih "Bordir Komputer".
  Pastikan dialog menampilkan dua kartu radio yang elegan dan jelas, bukan dua pasang tombol kembar.
