# 🎓 Modul Pembelajaran: Editor Opsi Berwarna di Dialog "Tambah Kolom" CRM (ENUM & MULTI_SELECT)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kosakata Tipe Field Terpadu, Compose Multiplatform, Design Token, Fail-Closed UI, Round-Trip Codec
> **Prasyarat**: Memahami `CrmFieldType` vs `FieldType` (PLAN-unify-field-vocabulary), dasar Compose state, aturan design system clay (AGENTS.md §13)
> **Referensi Task**: Follow-up #1 `docs/plannings/PLAN-unify-field-vocabulary.md` §3a

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Setelah penyatuan kosakata (2026-10-10), CRM *sudah* mengerti tipe `ENUM` (pilihan tunggal) dan `MULTI_SELECT` (pilihan ganda) di seluruh lapisan — domain, codec JSON, validasi server, renderer sel lead. Tapi dialog "Tambah Kolom Kustom" tidak menawarkannya, jadi admin tenant tidak punya pintu masuk ke tipe itu. Fitur yang ada tapi tak bisa diakses = fitur yang tidak ada.
- **Analogi Sederhana**: Codec kita adalah *gudang serbaguna* yang sudah punya rak untuk semua jenis barang; dialog "Tambah Kolom" adalah *loket pendaftaran* yang belum menjual tiket ke dua rak itu. Tugas ini bukan membangun rak baru — hanya membuka loketnya, lengkap dengan formulir isian (label + warna per opsi).
- **Hasil Akhir**: Admin memilih "Pilihan tunggal"/"Pilihan ganda", menambah opsi berlabel dan berwarna dari palet token brand, menyimpan, dan config JSON yang terbentuk **terbaca kembali identik** oleh codec yang sama — tanpa satu baris pun berubah di lapisan domain/server.

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Cek apakah domain-nya sudah ada.** `CrmFieldType(kind, options, maxSelections)` + `CustomAttributesCodec.encodeConfig/decodeFieldType` sudah menangani ENUM & MULTI_SELECT (termasuk arsip lembut opsi dan kunci wire `options`/`maxSelections`). **Tidak ada yang perlu diubah di `core/`** — kesalahan klasik adalah menambah tipe baru padahal yang kurang hanya UI (uji variabilitas + registry tunggal membuat tipe baru otomatis mengalir ke semua lapisan).
2. **Langkah 1: Helper konversi warna dulu (`presentation/common/ColorHexSupport.kt`).** Warna domain tersimpan sebagai string `#RRGGBB` (`SelectOption.colorHex`), UI hidup di `Color` Compose. Penerjemah kecil ini dipakai dua lapisan UI, jadi naikkan ke `presentation/common/` — bukan disalin.
3. **Langkah 2: Model draf & builder murni (`SelectOptionsEditor.kt`).** Baris editor tidak boleh langsung `SelectOption` karena labelnya boleh kosong sementara (baru saja ditekan "+ Tambah pilihan") padahal `SelectOption` init `require(label.isNotBlank())`. Maka: `SelectOptionDraft` (draf bebas) → `buildSelectOptions()` (fungsi murni) → `List<SelectOption>` (domain sah) hanya saat tombol "Tambah" ditekan.
4. **Langkah 3: Komponen editor (swatch + label + hapus).** Palet warna = **token peran** `WeMadeColors` (Primary, Accent, Success, …) — bukan literal baru. Hex tersimpan diturunkan dari token lewat `Color.toColorHex()`, sehingga swatch yang dilihat admin selalu = warna yang tersimpan.
5. **Langkah 4: Wire ke dialog.** Dua entri `FieldTypeOption` baru dengan `selectKind` non-null; dialog membangun `CrmFieldType(selectKind, options = buildSelectOptions(drafts))` di klik "Tambah". Jalur simpan **sudah ada** — `CrmUiEvent.AddCustomField` → `CrmApiClient.addCustomField` → `CustomAttributesCodec.encodeConfig` → server → `PostgresCustomFieldDefinitionRepository`.
6. **Langkah 5: Test round-trip.** Draf → domain → config JSON → baca kembali identik, untuk ENUM dan MULTI_SELECT (lihat §6).

## 🧱 3. Bedah Blok per Blok

### Blok A: Draf vs Opsi Domain — dua fase kehidupan data

```kotlin
data class SelectOptionDraft(val label: String, val colorHex: String)

fun buildSelectOptions(drafts: List<SelectOptionDraft>): List<SelectOption> {
    val usedIds = mutableSetOf<String>()
    return drafts.filter { it.label.isNotBlank() }.map { draft ->
        val key = FieldKey.fromLabel(draft.label.trim(), usedIds)
        usedIds += key.value
        SelectOption(id = SelectOptionId(key.value), label = draft.label.trim(), colorHex = draft.colorHex)
    }
}
```

**Mengapa begitu?**
- **Draf dipisah dari domain**: `SelectOption` menolak label kosong di `init`. Kalau UI dipaksa membuat `SelectOption` per baris, satu baris kosong = crash. Draf adalah "masih digodok"; opsi domain adalah "sah menurut hukum domain".
- **Id di-slug lewat `FieldKey.fromLabel` + dedup**: dua opsi "Satin" dan "satin" tetap dapat id `satin` dan `satin_2`. **Jangan mengarang generator id sendiri** — `FieldKey` sudah punya aturan slug + dedup yang teruji.
- **Baris kosong dilewati, bukan ditolak keras**: ini state pra-simpan (belum ada data yang berubah), pola yang sama dengan `ClayTagInput`. Fallback senyap yang dilarang aturan adalah pada *data tersimpan*, bukan pada baris formulir yang ditinggal kosong.

### Blok B: Palet = token, hex = data

```kotlin
val SelectOptionPalette: List<Color> = listOf(
    WeMadeColors.Primary, WeMadeColors.Accent, WeMadeColors.Success, …
)

fun Color.toColorHex(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return "#" + rgb.toString(16).padStart(6, '0').uppercase()
}
```

**Mengapa begitu?**
- Kontrak 1 design system: `Color(0xFF…)` hanya boleh di `WeMadeTheme.kt`. Warna opsi **tetap boleh** berwarna karena ia **data tenant** (pengecualian sah, sama seperti `PhaseDefinition.colorHex`) — tapi palet *pilihan*nya harus dari token peran, bukan warna karangan.
- Hex diturunkan dari token (`toColorHex`), bukan diketik ulang sebagai string `"#2563EB"` di samping token: satu sumber kebenaran, nol risiko swatch dan tersimpanan beda.

### Blok C: Wire dialog — satu `when` dengan tiga cabang eksplisit

```kotlin
val type = when {
    selectedOption.needsTarget -> CrmFieldType(FieldType.RELATION, targetResource = targetResource.trim())
    selectedOption.selectKind != null -> CrmFieldType(selectedOption.selectKind, options = buildSelectOptions(optionDrafts))
    else -> selectedOption.build?.invoke()
}
```

**Mengapa begitu?** Pola lama dialog sudah punya dua jalur pembangunan (statis via `build`, dinamis via `needsTarget`). Jalur opsi mengikuti pola yang sama. Larangan `else` pada `when(FieldType)` tidak tersentuh — kita tidak mendaftar tipe baru, hanya menawarkan dua tipe yang **sudah terdaftar**.

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Mengapa dipilih | Risiko alternatif |
|---|---|---|---|
| Editor feature-local di `crm/components/` | Angkat ke `designsystem/` | Aturan Tiga Kali: pemanggilnya satu dialog hari ini (preseden: `LeadCustomField` sengaja feature-local dengan alasan sama) | Komponen designsystem prematur = abstraksi salah yang menahan dua pemanggil berikutnya |
| `maxSelections` tidak diekspos di dialog | Tambahkan input angka | Scope follow-up = *menawarkan tipe + editor opsi*; `null` = tidak dibatasi sudah sah dan round-trip | UI ekstra tanpa permintaan; validasi `1..opsi` menambah kebenaran yang belum dituntut |
| Warna dari token, hex diturunkan | Editor warna bebas (color wheel) | Palet menjaga sinyal produksi & konsistensi brand; hex `#RRGGBB` adalah format yang divalidasi `SelectOption` | Warna bebas bisa meredam sinyal hijau/amber/merah produksi |
| `colorFromHex` fail-loud (lempar pada hex tak sah) | Parse lembut → warna abu | Tanpa fallback senyap (Kontrak 4); `SelectOption` sendiri menolak hex tak sah, jadi crash di UI = bug data yang terlihat, bukan disembunyikan | Hex rusak dirender "normal" = data rusak tak terlihat |

## ⚠️ 5. Jebakan Pemula

1. **Jebakan 1: Menambah enum tipe baru "biar aman".** *Kenapa bahaya*: melanggar registry tunggal — `FieldType` sudah punya ENUM & MULTI_SELECT; menambah entri paralel membuat dua kosakata lagi. *Solusi*: audit dulu (`CrmFieldType.kt`, `CustomAttributesCodec.kt`) sebelum menulis apa pun.
2. **Jebakan 2: Membuat `SelectOption` langsung di state UI.** *Kenapa bahaya*: `require(label.isNotBlank())` melempar begitu admin menambah baris kosong. *Solusi*: draf UI terpisah + satu fungsi murni penerjemah yang bisa dites tanpa Compose.
3. **Jebakan 3: Menulis literal `Color(0xFF…)` untuk swatch.** *Kenapa bahaya*: bocor dari theme, terhitung utang teknis design system. *Solusi*: palet token + `toColorHex()`/`colorFromHex()` dua arah.
4. **Jebakan 4: Mengubah kunci wire saat menambah fitur.** *Kenapa bahaya*: `options`/`maxSelections` adalah kontrak JSONB yang dibaca server & klien; mengganti nama = data tersimpan tak terbaca. *Solusi*: codec tidak disentuh sama sekali; UI hanya memakai bentuk yang sudah dikontrak.

## 🧪 6. Membuktikan Kodingan Bekerja

`SelectOptionsEditorSupportTest` (commonTest, murni tanpa Compose runtime):

- `buildSelectOptions_skipsBlankRows_andSlugifiesIds` — baris kosong hilang, id `warna_bahan`, label ter-trim.
- `buildSelectOptions_duplicateLabels_getUniqueIds` — "Satin"/"satin" → `satin`, `satin_2`.
- `buildSelectOptions_crmFieldType_roundTripsThroughCodec` — **inti follow-up**: draf dialog → `CrmFieldType(ENUM/MULTI_SELECT)` → `encodeConfig` → `decodeFieldType` → **identik**.
- `buildSelectOptions_malformedColorHex_failsLoudNotSilent` — hex tak sah melempar, bukan dirender diam-diam.
- `palette_colorsFormatRoundTrip_andStayUnique` — token → hex → token identik; hex palet unik.
- `nextDraftColor_prefersUnusedToken_andRotatesWhenExhausted` — palet habis berputar tanpa lempar.

Plus test paritas yang mengiterasi `FieldType.entries` (`CrmFieldTypeParityTest`, 21 test) tetap hijau — pagar `when` tanpa `else` di domain tak tersentuh.

Verifikasi: `:app:shared:compileKotlinJvm/compileKotlinWasmJs/compileKotlinJs/jvmTest` hijau. `assembleAndroidMain` tidak bisa dijalankan di mesin kerja ini (tidak ada Android SDK terpasang — direktori SDK kosong), bukan karena perubahan kode: seluruh kode baru ada di `commonMain`/`commonTest` dan terkompilasi di tiga target non-Android lain.

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan input `maxSelections` (1..jumlah opsi) untuk MULTI_SELECT di dialog, dan tulis test round-trip-nya — perhatikan kunci wire `maxSelections` sudah ada, jadi ini murni UI.
- [ ] Bangun layar "Kelola pilihan" untuk field ENUM yang sudah ada (rename, arsip lembut via `archivedAt`); saat itu terjadi, evaluasi apakah `SelectOptionsEditor` layak naik ke `designsystem/` (Aturan Tiga Kali).
- [ ] Perluas `LeadCustomField` SelectEditor agar DropdownMenu-nya menampilkan swatch warna opsi (sekarang hanya label), memakai `colorFromHex` yang sama.
