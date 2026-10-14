# 🎓 Modul Pembelajaran: TRD-PLAT-013 Track A — PackThemeResolver & Tema Pack di Kawat JSON

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kotlin Multiplatform, DDD, service murni, kebijakan fallback (bukan fallback senyap),
> kawat JSON ketat, kolom identitas aditif, paritas enum lewat `entries`.
> **Prasyarat**: `docs/trd/features/TRD-PLAT-013-pack-theme-customization.md` (Track 0 = kontrak tema),
> `.claude/rules/tenant-variability-rules.md` Kontrak 4/7/8, `.claude/rules/module-integration-rules.md` (konsep pack).
> **Referensi Task**: TRD-PLAT-013 §4 Track A (A.1–A.4) dan §5 acceptance.

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Track 0 sudah membekukan **kontrak**: `PackTheme` (gaya visual pack) + tiga enum (`VisualStyle`,
`ChatBubbleStyle`, `CardStyle`), field `theme: PackTheme?` di `DomainPack`, dan pack garment mengisi
tema CLAY. Tapi kontrak saja tidak cukup. Dua pertanyaan belum terjawab:

1. **Kalau sebuah pack tidak menyatakan temanya, tenant itu memakai tema apa?** Jawaban yang salah adalah
   "pinjam tema pack lain yang punya tema" — itu membuat tampilan satu vertikal bocor ke vertikal lain
   tanpa jejak. Jawaban yang benar: **tema netral milik platform** (`PackTheme()` = CLAY).
2. **Dari mana tema sebuah pack data datang?** Pack data hidup di JSON (`domain_packs.definition`,
   `GET /api/tenant/pack`). Kalau kawat JSON tidak mengenal `theme`, tema pack hilang begitu disimpan —
   fitur ini mati di jalur nyata meski resolver-nya "benar".

**Analogi sederhana.** Resolver itu seperti resepsionis yang menjawab "kamar ini bergaya apa?". Kalau
kamar belum diputuskan gayanya, dia berkata "gaya standar hotel", **bukan** "ikut gaya kamar sebelah".

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0 — Tentukan sumber kebenaran fallback.** Satu tempat yang menjawab tema: `PackThemeResolver`.
   Bukan tersebar di UI/ViewModel, supaya server dan klien tidak mungkin berbeda pendapat.
2. **Langkah 1 — Service murni.** `object` tanpa I/O; masukannya `DomainPack?` yang sudah di-resolve
   (`ResolveDomainPackUseCase`), keluarannya `PackTheme`.
3. **Langkah 2 — Buat tema bisa bertahan di penyimpanan.** Tambah `theme` ke `DomainPackCodec` (encode
   hanya bila ada; decode ketat).
4. **Langkah 3 — Jaga kompatibilitas mundur identitas pack bawaan.** `theme` = kolom **aditif** di
   `ShippedPackIdentity`, seperti `roleHints` dan kawan-kawan.
5. **Langkah 4 — Tes yang membuktikan, bukan tes yang berasumsi.** Unit perilaku + **paritas** yang
   mengiterasi `entries` enum, sehingga entri baru tanpa padanan langsung gagal.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Resolver murni — `PackThemeResolver`

```kotlin
object PackThemeResolver {
    val default: PackTheme = PackTheme()
    fun resolve(pack: DomainPack?): PackTheme = pack?.theme ?: default
}
```

**Mengapa begini?**
- **`object`, bukan `class` ber-DI.** Ia tidak punya state maupun dependensi; sejalan dengan
  `SubordinateResolver`/`InvoiceBindingResolver`. (Kontrak TRD yang membayangkan injeksi registry tidak
  perlu: `DomainPackRegistry` tidak memetakan tenant→pack; tenant menyimpan `DomainPackCode`, dan
  `ResolveDomainPackUseCase` yang mengubahnya menjadi `DomainPack`.)
- **`pack?.theme ?: default`, bukan `pack?.theme ?: someOtherPack.theme`.** `default` adalah nilai netral
  platform, jadi ini **bukan** fallback senyap ke vertikal lain. `null` (pack tak dikenal) dan `theme == null`
  (pack lama) diperlakukan sama — keduanya "tidak menyatakan tema".
- **Tidak ada `!!`.** Rantai `?.`/`?:` sudah null-safe sejak tipe.

### Blok B: Kawat JSON — menulis tema hanya bila ada

```kotlin
pack.theme?.let { put("theme", encodeTheme(it)) }
```

`encodeTheme` menulis enum dengan `.name` dan aksen dengan format `#AARRGGBB` yang sama seperti
`color` fase/seksi; `accentColor`/`cardStyle` dihilangkan bila `null`.

**Mengapa hanya bila ada?** Pack yang tidak menyatakan tema ter-encode **byte-identik** seperti sebelum
fitur ini — itu yang menjaga test round-trip pack lama tetap hijau.

### Blok C: Kawat JSON — membaca ketat

```kotlin
val theme = when (val t = root["theme"]) {
    null, JsonValue.Null -> null
    is JsonValue.Obj -> { val tr = Reader(t, "$.theme"); tr.build { PackTheme(tr.enum("style", VisualStyle.entries), …) } }
    else -> throw DomainPackDecodeException("$.theme", "harus objek")
}
```

**Mengapa begini?**
- Kunci absen/null → `theme = null` (pack lama terbaca, bukan error).
- Enum tak dikenal → `DomainPackDecodeException` ber-`path` (`$.theme.style`) — **ditolak**, tidak
  diam-diam dipetakan ke gaya lain (Kontrak 4).
- Warna aksen rusak (`"blue"`) ditolak di reader `color()` yang sama dengan fase/seksi.

### Blok D: Identitas pack bawaan — kolom aditif

```kotlin
if (!additive(theme, shipped.theme) { it == null }) return false
// … lalu copy(theme = shipped.theme) == shipped
```

**Mengapa?** Draf garment yang tersimpan **sebelum** field tema ada membawa `theme == null`. Tanpa
pengecualian ini, setiap revisi draf lama ditolak "wajib identik" — regresi yang sama persis dengan yang
pernah terjadi saat kolom aditif sebelumnya ditambahkan. Tema non-null yang **berbeda** dari pack bawaan
tetap ditolak (penulisan ulang).

### Blok E: Paritas enum lewat `entries`

```kotlin
VisualStyle.entries.forEach { style -> assertEquals(style, PackThemeResolver.resolve(pack(PackTheme(style = style))).style) }
```

**Mengapa begini?** Tes tidak menuliskan daftar gaya secara manual. Entri enum baru otomatis ikut teruji;
kalau ia tidak punya padanan (codec/rencana renderer Track C), tes gagal "dengan sendirinya"
(tenant-variability-rules Kontrak 7/8).

---

## ⚠️ 4. Jebakan yang Dihindari

1. **Fallback ke pack lain.** Mengganti `?: default` dengan meminjam tema vertikal lain = data berubah
   diam-diam. Yang benar: nilai netral platform.
2. **Melewatkan kawat JSON.** Tanpa `theme` di codec, fitur "ada" di domain tapi mati di jalur nyata — dan
   test round-trip pack bawaan ikut merah. Ini temuan nyata Track A ini (baseline 2 tes merah).
3. **Menjadikan `theme` wajib identik untuk draf lama.** Menolak draf yang sah sebelum field ini ada.
4. **Menulis `"accentColor": null` ekplisit.** Bikin pack tanpa aksen tidak lagi byte-identik; lebih baik
   kunci dihilangkan.
5. **`else ->` yang menelan nilai tak dikenal.** Nilai tema tak dikenal harus **ditolak**, bukan jadi default.

---

## ✅ 5. Cara Memverifikasi

```bash
./gradlew :core:compileKotlinJvm
./gradlew :core:jvmTest --tests '*PackTheme*'   # 14 tes (5 resolver + 9 paritas)
./gradlew :core:jvmTest                          # 2048 tes, 0 gagal
```

Bukti yang diharapkan: resolver mengembalikan tema yang dinyatakan apa adanya, fallback ke `PackTheme()`
untuk pack tanpa tema / pack tak dikenal, round-trip codec setara untuk **setiap** nilai enum, dan nilai
enum/warna tak dikenal ditolak dengan `path`.
