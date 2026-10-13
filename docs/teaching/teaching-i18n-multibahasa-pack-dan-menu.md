# 🎓 Modul Pembelajaran: Multibahasa (ID/EN) — dari Teks Pack sampai Judul Menu

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kotlin Multiplatform (JVM/JS/Wasm), Strangler Fig, value object, codec kompatibel mundur, data-driven UI, git worktree paralel
> **Prasyarat**: Paham `data class`, `value class`, `sealed interface`, dasar Compose, dan ide "kode vs data" (`tenant-variability-rules.md`)
> **Referensi**: [TRD-I18N-001](../trd/features/TRD-I18N-001-multibahasa-ui-dan-label-sistem.md) · [TRD-I18N-002](../trd/features/TRD-I18N-002-lokalisasi-data-pack.md) · [TRD-I18N-003](../trd/features/TRD-I18N-003-menu-label-pack-dan-fitur.md) · [PLAN menu](../plannings/features/PLAN-i18n-menu-label-in-pack.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalahnya.** Aplikasi ini 100% berbahasa Indonesia, dan teksnya tersebar di tiga tempat yang sifatnya berbeda:

| Jenis teks | Contoh | Siapa yang memilikinya |
|---|---|---|
| Teks UI | "Simpan", "Belum diterjemahkan" | Kode UI (resource string) |
| Teks **data pack** | nama fase, slot, modul, judul menu | **Data** pabrik/industri — bisa berubah tanpa rilis |
| Teks tenant | nama tahap kustom buatan admin | Tenant sendiri |

**Analogi.** Bayangkan hotel internasional. Papan "Lobby" di dinding (teks UI) diganti saat renovasi. Tapi **nama kamar** ("Suite Melati") ditulis resepsionis di buku tamu (data) — kalau mau bilingual, kamu tidak mengecat ulang dinding, kamu **menambah kolom bahasa di buku tamu**. Fase 2 dan menu di proyek ini adalah "kolom bahasa di buku tamu".

**Hasil akhir.** Pengguna menekan **ID | EN** di top bar: judul seksi, nama modul, dan judul menu berganti bahasa; tenant konveksi berbahasa Indonesia tetap **identik** dengan sebelumnya (paritas).

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0 — Kontrak terkecil dulu (`core`)**: `LanguageCode`, lalu `LocalizedText`. Kenapa? Semua lapisan lain (codec, server, UI) bergantung pada bentuk data ini; kalau berubah belakangan, semua ikut berubah.
2. **Langkah 1 — Seam kompatibel mundur**: tambah field baru **di samping** field lama (`displayName: String` tetap, `displayNameText: LocalizedText` ditambah). Strangler Fig: tidak ada pembaca lama yang patah.
3. **Langkah 2 — Codec yang ketat**: satu parser; bentuk lama (`"Potong"`) dan baru (`{"id":..,"en":..}`) sama-sama terbaca; bahasa tak dikenal **ditolak**.
4. **Langkah 3 — Validator kelengkapan**: pack yang mengklaim `en` harus *terbukti* lengkap oleh mesin.
5. **Langkah 4 — Server**: ternyata cukup *membuktikan* (lewat tes) bahwa codec yang sama dipakai — tidak ada migrasi SQL.
6. **Langkah 5 — Klien**: baca `resolve(bahasa)`, tampilkan penanda bila fallback, dan sediakan tombol ganti bahasa.
7. **Langkah 6 — Lihat dengan mata**: cek visual di browser. Dua bug lolos dari semua tes dan baru kelihatan di sini.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: `LocalizedText` — teks yang tahu bahasanya

```kotlin
class LocalizedText private constructor(private val byLanguage: Map<LanguageCode, String>) {
    val idText: String get() = byLanguage.getValue(LanguageCode.ID)

    fun resolve(requested: LanguageCode): ResolvedText {
        val exact = byLanguage[requested]
        return if (exact != null) ResolvedText(exact, requested, requested)
        else ResolvedText(idText, LanguageCode.ID, requested)   // fallback EKSPLISIT
    }
}
data class ResolvedText(val text: String, val usedLanguage: LanguageCode, val requested: LanguageCode) {
    val isFallback: Boolean get() = usedLanguage != requested
}
```

**Mengapa begini?**
- `id` **wajib** (bahasa dasar), bahasa lain opsional → pack lama (Indonesia saja) tetap sah.
- `resolve` mengembalikan `ResolvedText`, **bukan `String`**. Dengan `String`, kekurangan terjemahan menjadi tak terlihat. Dengan `ResolvedText`, UI wajib memutuskan apa yang dilakukan saat `isFallback` (kita menampilkan lencana "Belum diterjemahkan").
- Konstruktor `private` + `of(...)`/`fromMap(...)`: validasi (tidak kosong, panjang maks) ada di satu tempat.

### Blok B: Jembatan Strangler Fig di data class

```kotlin
data class ModuleDefinition(
    val id: ModuleId,
    val displayName: String,                       // LAMA: tetap = teks id
    // ...
    val displayNameText: LocalizedText = LocalizedText.of(displayName),   // BARU
    val menuLabelText: LocalizedText? = null       // BARU, opsional
) {
    init { require(displayNameText.idText == displayName) { "…harus sama dengan displayName" } }
}
```

**Mengapa begini?**
- Default-nya diturunkan dari field lama → ±100 pemanggil lama **tidak perlu diubah**.
- Invarian `idText == displayName` menjaga dua field tidak saling menyimpang.
- **Konsekuensi yang menggigit**: `copy(description = "x")` kini melanggar invarian dan melempar. Karena itu ada `withIdText(...)` yang mengganti pasangan sekaligus.

### Blok C: Codec — kompatibel mundur, tapi tidak longgar

```kotlin
fun encode(text: LocalizedText): JsonValue =
    if (text.languages.size == 1) jsonOf(text.idText)                 // id-saja → string biasa (byte-identik)
    else jsonObjectOf(*text.entries().map { (l, v) -> l.code to jsonOf(v) }.toTypedArray())

fun decode(value: JsonValue?, path: String): LocalizedText = when (value) {
    is JsonValue.Str -> LocalizedText.of(id = value.value)            // bentuk LAMA dibaca sebagai id
    is JsonValue.Obj -> /* tiap kunci wajib bahasa dikenal, else DomainPackDecodeException */
    else -> throw DomainPackDecodeException(path, "wajib string atau objek {id, en?}")
}
```

**Mengapa begini?**
- Pack tersimpan lama terbaca **tanpa migrasi data**, dan pack id-saja ter-encode **byte-identik** (diuji).
- Bahasa tak dikenal (`"fr"`) **ditolak dengan path**. Fallback senyap ke `id` = data berubah tanpa jejak (Kontrak 4).

### Blok D: `MenuPlacement` — keputusan yang dipaksa kompilator

```kotlin
sealed interface MenuPlacement {
    data object Shown : MenuPlacement
    data class Hidden(val reason: String) : MenuPlacement {
        init { require(reason.isNotBlank()) }
    }
}
// AppNavScreen(..., val menu: MenuPlacement, ...)   ← PARAMETER WAJIB, tanpa nilai bawaan
```

**Mengapa begini?** Dulu `isNavMenuItem: Boolean = true` — layar baru **otomatis tampil** tanpa ada yang memutuskan. Dengan parameter wajib, menambah layar tanpa menyatakan tampil/tidak = **kompilasi gagal**. Itu "kompilator menjaga, bukan ingatan". `Hidden` wajib beralasan, jadi 10 layar tersembunyi kini terdokumentasi.

### Blok E: `menuTitle` — urutan resolusi yang jujur

```kotlin
fun menuTitle(screen: AppNavScreen, definition: ModuleDefinition?, language: LanguageCode): ResolvedText {
    val label = screen.featureCode?.let { code -> /* label fitur terdaftar */ }
        ?: definition?.menuLabelText.takeIf { screen.featureCode == null }
    return (label ?: LocalizedText.of(screen.title)).resolve(language)   // cadangan: judul lama
}
```
Fitur → label modul → judul lama. Untuk `id`, hasilnya persis judul lama (paritas); untuk `en` tanpa label, hasilnya ditandai fallback.

### Blok F: Bahasa reaktif di klien

```kotlin
class AppLanguage private constructor(initial: LanguageCode) {
    val language: StateFlow<LanguageCode>
    fun setLanguage(code: LanguageCode) { _language.value = code; LanguagePreference.save(code) }
    // Tanpa `synchronized` / `@Volatile` — tidak ada di commonMain (JS/Wasm).
}
```

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| `LocalizedText` sebagai **data** untuk teks pack | File resource XML | Pack = data tenant/superadmin, berubah tanpa rilis | Perubahan nama modul butuh rilis aplikasi |
| Field baru di samping field lama (Strangler Fig) | Ganti tipe `String` → `LocalizedText` sekaligus | ±100 file tetap kompilasi; satu tahap per PR | PR raksasa, regresi senyap |
| `resolve` → `ResolvedText` | `resolve` → `String` | Fallback terlihat dan bisa ditandai | Terjemahan hilang tanpa ketahuan |
| `MenuPlacement` parameter wajib | Boolean dengan default | Kompilator memaksa keputusan | Layar baru tampil/hilang tanpa disengaja |
| `menuLabel` terpisah dari `displayName` | Pakai `displayName` untuk menu | Teks `id` menu tidak berubah; nama pendek untuk drawer | Paritas rusak, drawer sesak |
| Kunci label fitur = **kode fitur** | Kunci = rute layar | Konsep domain stabil; mewarisi RBAC induk | Data pack tercampur routing UI |
| Server mengirim **kode error**, klien menerjemah | Server menerjemah (`Accept-Language`) | Satu sumber teks UI | Dua tabel terjemahan yang bisa menyimpang |
| Terjemahan lewat kode dulu | Langsung pasang TMS (Weblate/Tolgee) | Cukup untuk 2 bahasa; format `strings.xml` bisa pindah kapan saja | Biaya/perawatan sebelum perlu |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **`synchronized`, `@Volatile`, `String.format` di `commonMain`.** Kompilasi JVM hijau, tapi **JS dan Wasm gagal**. Selalu kompilasi *semua* target sebelum merge. Pengganti `String.format`: hitung sendiri (kita menulis `fixedDecimal`).
2. **Fallback senyap.** Menjatuhkan bahasa tak dikenal ke `id` terasa ramah, tapi mengubah data tanpa jejak. Tolak dengan path, atau kembalikan penanda fallback.
3. **`copy()` pada tipe berpasangan.** Invarian `idText == displayName` membuat `copy(displayName = …)` melempar. Sediakan fungsi penyunting resmi (`withIdText`).
4. **Menyalin daftar field.** Server tidak boleh membuat daftar field modul sendiri — pakai `DomainPackCodec`. Itu sebabnya Track B hanya perlu **membuktikan**, bukan menulis.
5. **Identitas pack.** Pack garment bawaan kini punya terjemahan `en` dan `menuLabel`; draf lama yang tersimpan tidak. Kalau pengecekan "pack ini identik dengan bawaan?" ikut menghitung teks baru, **semua draf lama ditolak**. Bandingkan *di luar* terjemahan (`withoutTranslations()` membuang `menuLabel`).
6. **`weight(1f, fill = false)` di induk yang lebarnya mengikuti isi.** Teks mengecil jadi nol (chip fase kosong!). Di kolom berlebar tetap aman; di chip, matikan `weight`. Tes tidak menangkapnya — **lihat dengan mata**.
7. **DB tes hilang.** Postgres di mesin berganti → database scratch lenyap → tes server menunggu koneksi berulang dan tampak "menggantung 30 menit". Cek `pg_database` dulu; jangan menebak kode yang salah.
8. **Banyak agen menulis di checkout yang sama.** Perubahan pihak lain yang belum di-commit membuat tes gagal secara acak. Kerjakan di **git worktree** terpisah, dan buktikan "bukan kegagalan saya" dengan menjalankan tes yang sama di `main` murni.
9. **Aturan baru bertabrakan dengan invarian lama.** Aktivasi manual (A2) membuat `fromPreset` mulai tanpa modul aktif, sementara `SaveTenantPipelineUseCase` menolak pipeline tanpa modul aktif. Perbaikan yang benar: invarian hanya menolak **mematikan modul aktif terakhir**, bukan keadaan awal.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Kita Bekerja?

**Paritas adalah tes terpenting** — "tenant konveksi bahasa `id` berperilaku identik".
- `MenuInventoryTest`: iterasi `AppNavScreen.entries` — judul menu `id` untuk setiap layar harus sama persis dengan sebelumnya; daftar `Shown` dikunci; `featureCode` harus menunjuk fitur yang induknya cocok.
- `PackLocalizationSeamTest`: pack id-saja tidak menulis kunci `languages` dan round-trip identik.
- `GarmentMenuLabelParityTest`: `menuLabel.id` == tabel judul layar yang di-hardcode; `en` lengkap.

**Tes yang menolak:** bahasa `"fr"` → `DomainPackDecodeException`; `Hidden(" ")` → `IllegalArgumentException`; `featureCode` tak terdaftar → gagal.

**Tes dengan pack non-default** (Kontrak 6): fixture klinik tanpa `menuLabel` tetap valid dan round-trip lewat API (`DomainPackMenuLabelApiTest`) dan JSONB Postgres.

**Contoh kasus kunci:**
```kotlin
@Test fun menuTitle_englishForPackWithoutLabel_isHonestFallback() {
    val noLabel = GarmentDomainPack.pack.withoutTranslations().module(ORG_CHART)
    val r = menuTitle(AppNavScreen.ORG_CHART, noLabel, LanguageCode.EN)
    assertEquals(AppNavScreen.ORG_CHART.title, r.text)
    assertTrue(r.isFallback)
}
```

**Dan lihat dengan mata**: login demo → pilih persona Owner → buka drawer → ID lalu EN. Temuan nyata dari sini: chip fase kosong, label Inggris terpotong elipsis, dan item menu yang masih Indonesia karena memakai `screen.title`.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1 — `AuthGuardCard`**: delapan titik di `App.kt` masih memakai `screen.title` untuk layar modul. Ubah ke `menuTitle`, **tanpa menambah baris `App.kt`** (file itu sudah di atas hard limit — pindahkan logika ke file bertema).
- [ ] **Tantangan 2 — Label pendek**: "Material & Price Master D…" terpotong di drawer. Tambah aturan/ tes yang menandai label menu EN lebih panjang dari N karakter, lalu pendekkan label garment-nya.
- [ ] **Tantangan 3 — Pack ketiga**: buat pack non-garment dengan dua modul, satu `menuLabel` lengkap `id`+`en`, satu tanpa. Verifikasi di drawer bahwa yang kedua menampilkan lencana "Not translated" saat EN.
- [ ] **Tantangan 4 — Registri fitur ke pack**: `ModuleFeatureRegistry` masih di paket mesin dengan konten garment. Rancang migrasi Strangler Fig-nya menjadi data pack (mulai dari Discovery Note).
