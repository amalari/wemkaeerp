# 🎓 Modul Pembelajaran: Strip Urgensi Kartu SPK A6 yang Terpotong

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: PDFBox text metrics, koordinat PDF, auto-fit font, test geometri
> **Prasyarat**: Dasar PDFBox (`PDPageContentStream`, `showText`), satuan point (1 pt = 1/72 inci)

---

## 💡 1. Masalahnya

Teks merah di bawah kartu terpotong di **tiga sisi** sekaligus:

| Gejala | Penyebab |
|---|---|
| Bagian bawah huruf hilang | Font 16pt ditaruh di strip setinggi 6 mm (±17pt). Baseline dihitung `top − 16 − 4`, jadi posisinya **di bawah** strip dan menembus tepi kertas |
| "URGENT · PRIORITAS" hilang di kiri | Label ±600pt di strip ±270pt. Rumus center `(strip − teks) / 2` menjadi **negatif**, sehingga teks mulai di luar halaman |
| Ekor "DL 03-…" hilang di kanan | Masalah yang sama di sisi sebaliknya |

Analogi: seperti menempel spanduk 3 meter di papan 1 meter lalu "menengahkannya". Yang terbaca
hanya bagian tengahnya.

## 🧭 2. Start dari Mana

1. **Tanya dulu isinya, baru ukurannya.** Tahap dan deadline sudah tercetak di baris identitas kartu.
   Strip hanya perlu membawa sinyal yang harus terbaca dari jauh: level, prioritas, dan sisa hari.
   Label jadi separuh panjangnya sebelum satu baris kode geometri pun ditulis.
2. **Auto-fit font** untuk kasus sisanya (angka prioritas 3 digit, dsb.).
3. **Center vertikal memakai cap height**, bukan ukuran font.

## 🧱 3. Bedah Kode

```kotlin
internal fun stripFontSize(font: PDFont, label: String, availableWidthPt: Float): Float {
    val widthAt1pt = font.getStringWidth(label) / 1000f
    return (availableWidthPt / widthAt1pt).coerceIn(STRIP_MIN_SIZE, STRIP_MAX_SIZE)
}
```
- `getStringWidth` mengembalikan lebar dalam satuan *1/1000 em*. Dibagi 1000, hasilnya lebar teks
  pada ukuran 1pt. Karena lebar berbanding lurus dengan ukuran font, ukuran yang pas cukup
  dicari dengan satu kali pembagian, tanpa perlu loop coba-coba.
- `coerceIn(7, 12)`: di bawah 7pt, printer kantor mulai menghasilkan huruf yang pecah. Di atas
  12pt, huruf sudah tidak muat secara vertikal.

```kotlin
val capHeightPt = bold.fontDescriptor.capHeight / 1000f * sizePt
val baselineY = stripBottomPt + (stripHeightPt - capHeightPt) / 2f
```
- Di PDF, sumbu Y dihitung **dari bawah**, dan `newLineAtOffset` menunjuk ke *baseline*, bukan ke
  atas huruf. Teks strip semuanya huruf kapital, jadi tinggi visualnya sama dengan *cap height*.
  Menengahkan cap height di dalam strip menghasilkan teks yang benar-benar di tengah.

**Bonus:** kode `W1SR-…` di bawah QR juga melewati margin kanan. Penyebabnya, teks 8pt lebih lebar
dari QR 30 mm tapi ditulis rata kiri. Sekarang ditulis rata kanan ke tepi QR (`textRight`).

## ⚖️ 4. The "Why"

| Pilihan | Alternatif | Alasan |
|---|---|---|
| Buang info duplikat | Perbesar strip jadi 2 baris | Lebih sedikit tinta = lebih cepat terbaca dari jauh; tinggi kartu tetap cukup untuk grid ukuran maksimum |
| Auto-fit | Font kecil tetap (mis. 8pt) | Label pendek ("AMAN · PRIORITAS 1/3") tetap besar dan mencolok |
| Test geometri via metrik font | Ekstrak teks dari PDF | Font di-embed tanpa ToUnicode, sehingga `PDFTextStripper` mengembalikan string kosong |

## ⚠️ 5. Jebakan Pemula

1. **Mengira ukuran font = tinggi huruf.** Font 16pt artinya em-box 16pt. Huruf kapitalnya sekitar
   11pt, dan descender bisa turun di bawah baseline.
2. **Menengahkan tanpa memeriksa apakah teksnya muat.** Hasilnya koordinat negatif, dan PDFBox diam
   saja (tidak ada error).
3. **Test yang hanya mengecek ukuran halaman.** Test lama lolos walaupun teksnya terpotong.

## 🧪 6. Bukti

`SpkCardPdfRendererTest` › `strip urgensi label terpanjang muat di lebar strip tanpa di bawah batas font`:
label terpanjang (prioritas 188/188, sisa -188 hari) harus muat di strip, belum menyentuh font minimum,
dan cap height-nya lebih kecil dari tinggi strip. Setelah itu PDF-nya di-render ke PNG dan dicek dengan mata.

## 🏆 7. Tantangan

- [ ] Tambahkan test serupa untuk judul `SPK-SMP-0005 (Rev 1)`: nomor SPK yang panjang bisa menabrak kolom QR.
- [ ] Judul sudah memuat "(Rev 1)" dan pojok kanan juga mencetak "REV 1". Mana yang sebaiknya dibuang, dan kenapa?

---

## 🔄 Update: Teks Strip Dihapus

Keputusan produk: strip cukup jadi **penanda warna polos**. Level, prioritas, dan sisa hari tidak lagi
dicetak di strip. Tahap dan deadline tetap ada di baris identitas.

Akibatnya, `stripText`, `stripFontSize`, dan test lebar strip ikut dihapus. Kode yang tidak lagi punya
pemanggil jangan disimpan "untuk jaga-jaga", karena ia akan tetap ikut dirawat tanpa pernah dipakai.

**Trade-off yang diterima:** kartu yang dicetak di printer hitam-putih tidak lagi menyampaikan level
urgensi. Merah, amber, dan hijau semuanya keluar sebagai abu-abu. Kalau suatu hari kartu dicetak
hitam-putih, pertimbangkan satu kata level saja (mis. "URGENT") dengan ukuran tetap.
