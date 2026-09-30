# Teaching — Discovery D1: PDF blueprint ber-watermark (dan tiga hal yang hanya muncul di atas kertas)

> Plan: [`PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §5 (Fase D) ·
> Status: selesai 2026-09-30 · Pendahulu: [`teaching-discovery-a1-a7-a9-vertical-slice.md`](teaching-discovery-a1-a7-a9-vertical-slice.md),
> [`teaching-discovery-a8-koog-agent.md`](teaching-discovery-a8-koog-agent.md),
> [`teaching-discovery-c1-wizard-funnel-visual-verification.md`](teaching-discovery-c1-wizard-funnel-visual-verification.md)

Fase D menambah dua hal: tombol di wizard, dan **sebuah berkas PDF yang berpindah tangan**. Yang kedua
itu yang membuat fase ini berbeda dari semua fase sebelumnya: PDF-nya keluar dari sistem kita, difoto,
diteruskan lewat WhatsApp, dan pada titik itu tidak ada lagi kode yang bisa memperbaiki kesalahpahaman.
Isi dokumen ini adalah tentang keputusan-keputusan yang muncul karena alasan itu.

---

## Step 0 — Memilih jalur, bukan membangun jalur baru

Repo ini sudah punya empat PDF: faktur, lembar kartu telusur, kartu SPK, lembar kerja rajut. Pola yang
sudah terbukti itu punya tiga bagian:

| Bagian | Tempat | Isi |
|---|---|---|
| Isi + geometri | `core/.../domain/<fitur>/print/` | data + `Mm10`/`TemplateRect`, murni, bisa diuji tanpa PDF |
| Gambar | `server/.../infrastructure/pdf/*PdfRenderer.kt` | PDFBox, font dari `PdfFonts` |
| Antar | `server/.../routes/*PrintRoutes.kt` | `respondBytes(..., ContentType.Application.Pdf)`, `Cache-Control: private, no-store` |

Blueprint mengikuti pola yang sama persis, dengan nama yang sejajar:

```
core/.../domain/discovery/print/
├── BlueprintPdfDocument.kt    # ISI: apa yang dicetak (dari DiscoveryDraft)
├── BlueprintSheet.kt          # BENTUK: peran baris, garis, halaman, watermark
└── BlueprintSheetLayout.kt    # ALGORITMA: menempatkan baris, memenggal halaman
server/.../infrastructure/pdf/BlueprintPdfRenderer.kt
server/.../routes/DiscoveryBlueprintPdfRoutes.kt
```

**Alternatif yang ditolak: menyusun PDF langsung dari data domain di dalam renderer.** Itu jalur
tercepat untuk satu dokumen, dan jalur tercepat juga untuk bug yang tidak bisa ditangkap test: renderer
akan memakai mesin pengukur teksnya sendiri, sementara kelak pratinjau di kanvas memakai yang lain.
Faktur sudah pernah membayar pelajaran itu (`InvoiceTextLayout.kt` — "titik potongnya identik secara
konstruksi, bukan karena kebetulan dua mesin pengukur sepakat").

## Step 1 — Isi: modul yang dimatikan tetap dicetak

`BlueprintPdfDocument.of(draft, …)` dibangun dari `DiscoveryDraft` — pack (kosakata vertikal) +
blueprint (modul aktif & parameter) + layar. Satu keputusan isi yang layak dibela:

```kotlin
modules = pack.modules.map { module ->
    BlueprintModuleLine(
        moduleCode = module.id.value,
        active = blueprint.isActive(module.id.value),
        parameters = blueprint.parametersOf(module.id.value).entries.sortedBy { it.key }…
    )
}
```

Iterasinya melewati **semua** modul pack, bukan hanya `blueprint.activeModuleCodes`. Alasannya
mengikuti B4/TRD-PLAT-001 FR-2: modul non-aktif bukan modul yang tidak ada — Gudang di-bypass di CMT
tetap punya `stockOwnership = CONSIGNED_CLIENT_MATERIAL`, dan di PDF barisnya muncul dengan tanda
`[bypass]`. PDF yang hanya menampilkan modul aktif membuat prospek membandingkan penawaran dengan
sistem yang tidak akan ia terima — perbedaan yang baru terasa di bulan ketiga proyek.

Bukti ada di layar (screenshot sesi ini): dokumen CMT mencetak `Modul (7 aktif dari 15)` dengan delapan
baris `[bypass]` di antaranya.

## Step 2 — Bentuk: pemenggalan halaman di tingkat **baris**

`BlueprintSheetLayout` menerima daftar aliran (flow) item, lalu memutuskan penempatan:

```kotlin
InvoiceTextLayout.wrap(item.text, width, item.role.style).forEach { text ->
    if (y + lineHeight > bottomLimitMm10) openPage()   // halaman baru + judul "(lanjutan)"
    place(text, item.role, item.indentMm10, width, lineHeight)
}
```

Dua hal yang mudah salah di sini, dan keduanya dicegah dengan sadar:

1. **Pemenggalan per blok, bukan per baris.** Deskripsi blueprint bisa panjang. Kalau blok diperlakukan
   atomik, satu paragraf yang tidak muat akan mendorong seluruhnya ke halaman berikutnya dan menyisakan
   setengah halaman kosong di atasnya.
2. **Halaman lanjutan tanpa identitas.** Lembar kedua yang mulai di tengah daftar modul terlihat seperti
   dokumen lain begitu difotokopi. Karena itu setiap halaman baru ditulis lewat `place()` (bukan
   `emit()`) dengan judul `"Blueprint Sistem · <blueprint> (lanjutan)"` — memakai `place()` juga
   menjamin judul itu tidak bisa memicu pemenggalan lagi.

`openPage()` memakai `place()` — bukan `emit()` — supaya tidak ada rekursi tak berujung ketika sebuah
baris lebih tinggi dari satu halaman penuh (tidak mungkin di A4, tapi tidak perlu diuji ulang).

## Step 3 — Renderer: satu karakter yang menggagalkan seluruh dokumen

Ini temuan paling mahal di fase ini, dan datang dari test — bukan dari mata:

```
java.lang.IllegalStateException: could not find the glyphId for the character: →
    at org.apache.pdfbox.pdmodel.PDAbstractContentStream.applyGSUBRules
```

Baris pertama yang saya cetak adalah baris istilah vertikal:

```kotlin
add(FlowItem("$neutral → $word", BlueprintLineRole.ROW))   // perusahaan → klinik
```

`PDFBox.showText` **melempar** untuk glyph yang tidak ada di font, dan font yang dibundel
(`nunito_regular.ttf`) tidak punya panah U+2192. Efeknya bukan "panahnya jelek" — efeknya **seluruh PDF
gagal terbit**, karena pengecualian itu terjadi di tengah loop halaman.

Perbaikannya dua lapis, dan lapis kedua itu yang penting:

1. **Di domain:** baris istilah ditulis ASCII (`"$neutral -> $word"`). Alasannya bukan selera — lebar
   teks dihitung di domain dari karakter aslinya, jadi mengganti karakter di sisi renderer saja membuat
   baris tercetak lebih pendek daripada rencananya.
2. **Di renderer:** setiap teks melewati `encodeSafe()` sebelum digambar:

```kotlin
private fun encodeSafe(font: PDFont, text: String): String = buildString {
    text.forEach { ch ->
        val candidate = ASCII_FALLBACK[ch] ?: ch.toString()
        append(if (canEncode(font, candidate)) candidate else "?")
    }
}
```

Kenapa lapis kedua tidak bisa dilewati: **isi PDF ini sebagian adalah data tenant.** Kosakata pack,
nama modul, dan deskripsi blueprint ditulis prospek/AI. Satu karakter aneh di salah satunya tidak boleh
membuat prospek menerima halaman kosong.

### Pelajaran kedua: jangan memetakan karakter yang **ada**

Versi pertama `ASCII_FALLBACK` memetakan seluruh tipografi: `—` → `-`, `·` → `-`, `“` → `"`, `…` → `...`.
Terlihat aman, dan salah. Probe ke font yang sebenarnya (satu test sementara yang menanyakan
`getStringWidth` per karakter, lalu dihapus) memberi jawaban yang lebih baik daripada dugaan:

```
nunito_regular.ttf -> arrow=MISSING emdash=OK endash=OK middot=OK bullet=OK
                      rsquo=OK lsquo=OK ldquo=OK rdquo=OK hellip=OK times=OK check=MISSING e=OK u=OK
```

Hanya `→` dan `✓` yang absen. Memetakan em dash ke `-` tidak menambah keamanan apa pun, tapi menambah
**selisih lebar**: layout menghitung lebar `—` (≈1 em), renderer mencetak `-` (≈0,33 em) — baris jadi
lebih pendek dari yang direncanakan domain. Daftar fallback akhirnya: dua karakter, selebihnya `?`.

> **Kebiasaan yang layak dicuri**: sebelum memetakan/menormalisasi teks apa pun "supaya aman", tanyakan
> ke sumbernya (font, parser, API) apa yang benar-benar tidak didukung. Normalisasi yang tidak
> diperlukan selalu membayar dua kali — sekali dalam kode, sekali dalam selisih yang tidak terlihat.


## Step 4 — Watermark: mengapa test-nya mengukur tinta, bukan teks

Watermark ditempatkan di domain (`BlueprintWatermark(text, center, fontSizePt, angleDeg)`), digambar
renderer dengan `Matrix.getRotateInstance` di pusat kertas, abu-abu 0,88, **sebelum** teks lain.

Membuktikannya lewat test ternyata tidak bisa memakai ekstraksi teks. `PDFTextStripper` memotong teks
miring **di tengah kata**:

```
DRAF - B
UKAN PENAWARAN
```

Jadi `contains("DRAF - BUKAN PENAWARAN")` gagal, dan bahkan perbandingan tanpa spasi pun gagal karena
`B|UKAN` terpotong. Dua pendekatan yang akhirnya dipakai:

1. **Perbandingan tanpa spasi + penyamaan dash** untuk assert isi (`normalizedForMatch`).
2. **Uji tinta pita tengah halaman** untuk assert "ada di setiap halaman":

```kotlin
val inked = centerBandInk(renderer.render(sheet))                              // dengan watermark
val control = centerBandInk(renderer.render(layout(doc.copy(watermark = "")))) // tanpa watermark
inked.zip(control).forEachIndexed { i, (a, b) -> assertTrue(a > b + 200) }
```

Halaman yang **sama** dicetak dua kali, lalu piksel gelap di pita tengah dibandingkan. Teks isi identik
di kedua versi, jadi selisih tinta hanya bisa berasal dari watermark. Ini bukti mata versi mesin:
murah, deterministik, dan tidak bergantung pada kuirk ekstraksi.

## Step 5 — Tiket cetak: tab browser tidak bisa membawa Bearer

Tombol di wizard memanggil `POST /{id}/print-ticket`, lalu membuka `blueprint.pdf?ticket=…` di tab baru.
Tanpa tiket, tautan itu **selalu 401** — bukan hanya untuk prospek, tapi juga untuk superadmin, karena
tab yang dibuka `window.open` tidak punya header `Authorization`.

Mekanisme tiket sudah ada (`PrintTicketService`, dipakai cetakan jejak), tapi `verify()` hanya
mengembalikan **tenant**. Gerbang draf discovery adalah **kepemilikan dokumen** (T12), jadi tiket perlu
membawa pemiliknya:

```kotlin
fun verifyUser(ticket: String, requestPath: String): PrintTicketUser?   // userId + isPlatformSuperadmin
```

`mayAccessPdf()` tetap memeriksa kepemilikan draf setelah tiket diverifikasi. Tiket tidak diperlakukan
sebagai sesi: ia tidak membawa peran RBAC maupun divisi, sehingga menambahkan peran baru kelak tidak
diam-diam ikut berlaku lewat tautan cetak.

Satu perilaku yang ditemukan saat menulis test, dan lebih baik dari dugaan awal: tiket untuk **draf
lain** ditolak dengan **401, bukan 403** — cakupan path di tiket ditolak `TenantResolutionPlugin`
sebelum rute berjalan. Test-nya diubah agar mengunci perilaku itu, bukan ekspektasi awal.


## Step 6 — Verifikasi (bukan sekadar kompilasi)

| Lapis | Bukti |
|---|---|
| Isi | `BlueprintPdfDocumentTest` (5): istilah klinik, modul ganda, bypass, watermark per status, urutan fase |
| Tata letak | `BlueprintSheetLayoutTest` (8): semua baris di dalam margin, tidak menimpa, judul lanjutan, 40 modul tercetak tepat sekali, deskripsi 800 kata mengalir, deterministik |
| Renderer | `BlueprintPdfRendererTest` (4): isi sampai ke kertas, watermark di tiap halaman (uji tinta), karakter tanpa glyph tidak menggagalkan dokumen |
| Tiket | `PrintTicketServiceTest` (+3): `verifyUser`, kedaluwarsa, tiket draf tidak membuka PDF tenant |
| Rute | `DiscoveryApiTest` (+1): 401 tanpa sesi · 200 pemilik lewat tiket tanpa Bearer · 200 Bearer & superadmin · 403 pengguna lain · 401 tiket draf lain · 404 draf hantu |
| Mata | `/discovery` → narasi klinik → "Unduh Blueprint (PDF)" → tab PDF: watermark diagonal `DRAF — BUKAN PENAWARAN`, istilah `dokumen -> Kunjungan`, modul `[aktif]`; narasi konveksi → `7 aktif dari 15` dengan `[bypass]` |

Screenshot sesi ini: `50/51-d1-blueprint-pdf-klinik*.png` (klinik, sebelum & sesudah dua perbaikan) dan
`52-d1-blueprint-pdf-garment.png` (konveksi — jalur lama tidak berubah).

### Dua cacat yang hanya ketahuan dari screenshot

1. **Fase tercetak dobel nomornya**: `"1. 1. Operasi — Alur kerja harian"`. `BlueprintPdfDocument`
   menambahkan `order` sementara `PhaseDefinition.displayName` pack sudah memuat nomornya sendiri
   (`"1. Operasi"`, `"1. Komersial & Sampling"`). Tidak ada test yang gagal — hanya mata yang melihat.
   Perbaikan: pakai `displayName` apa adanya; test-nya sekarang membandingkan baris utuh terhadap pack.
2. **Em dash di watermark tercetak `-`** karena fallback yang terlalu luas (Step 3). Setelah fallback
   dipertajam, watermark kembali berbunyi `DRAF — BUKAN PENAWARAN`.

## Step 7 — Audit sebelum merge

- Ukuran file: `BlueprintPdfDocument.kt` 127, `BlueprintSheet.kt` 61, `BlueprintSheetLayout.kt` 220 —
  semuanya di bawah soft limit `core/**` (250). Pemecahan mengikuti **tanggung jawab** (isi / bentuk /
  algoritma), bukan jumlah baris.
- Rute PDF dipindah ke `DiscoveryBlueprintPdfRoutes.kt` (134 baris) supaya tanggung jawab cetak tidak
  menumpuk di `DiscoveryRoutes.kt`.
- Nol `Color(0xFF…)`, `RoundedCornerShape(N.dp)`, `Modifier.shadow()` baru di UI.
- Kompilasi 4 target hijau (JVM, WasmJs, JS, jvmTest). `assembleAndroidMain` tetap terblokir tanpa
  Android SDK — kondisi lingkungan, bukan utang kode.

## 🏆 Tantangan mandiri

1. **Watermark halaman kedua.** Jalankan wizard dengan narasi yang menghasilkan banyak modul, lalu buka
   halamannya satu per satu. Apakah watermark di halaman 2 benar-benar ada, dan apakah `(lanjutan)`
   muncul? (Petunjuk: uji tinta di `BlueprintPdfRendererTest` sudah menjalankannya untuk 45 modul
   sintetis; mata tetap perlu melihatnya sekali.)
2. **Istilah vertikal di PDF vs di chrome.** Buka `/m/klinik_antrean` **dan** PDF klinik berdampingan.
   Apakah kata yang dipakai sama? Kalau tidak, dari mana perbedaannya berasal — pack, chrome, atau
   dokumen?
3. **Nomor halaman yang hilang.** Ubah `MARGIN_BOTTOM_MM10` menjadi 10 dan lihat test mana yang merah.
   Kenapa test itu yang menangkapnya, bukan test lain?

## Utang & langkah berikutnya

- **Pratinjau PDF di dalam aplikasi**: sekarang membuka tab browser; Android/iOS masih no-op seperti
  fitur cetak lain (`openInBrowser`).
- **Harga tidak ada di PDF** — disengaja. Kalau kelak prospek minta penawaran berkop, itu dokumen
  berbeda dengan gerbang berbeda (dan angka yang harus bisa ditelusuri), bukan tambahan di PDF ini.
- **C (Studio `TemplateDesigner`)** dan **E (sesi interview, demand ledger, Rule of Three)** masih
  terbuka di plan §4/§6.

