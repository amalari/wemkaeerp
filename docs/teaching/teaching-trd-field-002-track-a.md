# Teaching — TRD-FIELD-002 Track A (sisa): FILE di konteks kedua, sampel kontrak, dan tes penjaga

> Slice: **Track A sisa** dari [`TRD-FIELD-002-file.md`](../trd/TRD-FIELD-002-file.md) §4.7.
> A0 (kontrak tipe: enum `FILE`, `FileRef`, `ObjectStorage`, codec, pemetaan SQL) sudah merge di
> `b51e1ccc` — slice ini **membangun di atasnya tanpa mengubah kontraknya**.

## 1. Kenapa slice ini ada

A0 membuktikan tipe `FILE` bekerja **pada satu pack uji** (bordir). Kontrak 7
(`tenant-variability-rules.md`) menuntut lebih: fitur yang membaca konsep variabel wajib teruji di
**konteks kedua non-default** — kalau tidak, kita baru tahu rusaknya saat tenant layanan/bengkel
pertama mendaftar. Sisa Track A menjawab tiga pertanyaan kecil:

1. Apakah FILE **masuk akal di pack non-garment** dan lolos semua mesin (parity board, generator, codec)?
2. Apakah **sampel kontrak** yang dipakai lintas jalur (UI/server/handoff) ikut mengenal FILE?
3. Apakah **penjaga otomatis** mencegah FILE dipakai di tempat yang belum boleh (seed, usulan deterministik)?

## 2. Step 0 — memilih konteks kedua: `LayananPilotPack`

Pack pilot `layanan` (`core/.../domain/pack/tenant/layanan/LayananPilotPack.kt`) adalah konteks
kedua yang paling murah dan paling jujur: ia **data pack** netral industri ("Permintaan Perubahan")
yang spec-nya sengaja memuat tipe-tipe field agar generator benar-benar dikompilasi. Entitas
`change_request` juga konteks paling relevan untuk lampiran — permintaan customisasi klien membawa
berkas (mockup, spesifikasi).

```kotlin
// Tidak wajib, dan seed papan kosong (binding Api) — unggah nyata lewat Track B/C.
FieldSpec("lampiran", "Lampiran", FieldType.FILE)
```

Satu field ini mengalir otomatis ke empat lapis:

| Lapis | Yang terjadi | Penjaganya |
|---|---|---|
| Papan (hint + detailForm) | `FieldHint("lampiran", FILE)` → `toFieldSpec()` | `PilotBoardParityTest` (kunci/tipe papan = entitas server) |
| Spec generator | kolom `lampiran TEXT,` di migrasi hasil `generateFromSpec` | `SpecScaffoldGeneratorTest` (kini menegaskan kolomnya + set tipe pack) |
| Codec pack | round-trip hint FILE | `PilotPackCodecRoundTripTest` |
| Validator seed | baris contoh berisi FILE ditolak | `ScreenProposalValidatorTest` (tes baru, §4) |

**Jebakan yang dihindari**: kita **tidak** menyentuh scaffold server hasil generator yang sudah
checked-in (`LayananChangeRequestRoutes.kt`, tabel, repositori, migrasi `V90`). File itu berlabel
"KANDIDAT PR", migrasinya sudah terlanjur diterapkan Flyway (checksum), dan pembaruan scaffold
adalah urusan Track B saat endpoint unggah hadir. Pack (data) dan scaffold (kode) boleh berbeda
sejenak; yang memaksa penyelarasan nanti adalah test gerbang, bukan sunting diam-diam.

## 3. Step 1 — sampel kontrak: `PrototypeContractSamples.orderEntity`

```kotlin
// C8 (TRD-FIELD-002): nilai = FileRef; baris contoh sengaja TANPA kunci ini.
FieldSpec("Lampiran", "Lampiran", FieldType.FILE)
```

Sampel kontrak dipakai UI (`PrototypeInteractiveUiTest`), server, dan handoff. Kuncinya: baris seed
**tidak diberi kunci "Lampiran" sama sekali** — bukan diberi nilai kosong eksplisit. Kosong selalu
sah di `FieldSpec.accepts`, dan validator menolak nilai non-kosong (§4), jadi bentuk "absen" adalah
cara paling jujur menyatakan "belum diisi".

## 4. Step 2 — tes penjaga (tiga lapis, satu aturan: *tidak mengarang referensi*)

1. **Seed FILE wajib kosong** (`ScreenProposalValidatorTest`): seed berisi FileRef **berbentuk sah
   pun** (`fields/klinik/antrean/r-1/scan-…-scan.pdf`) ditolak di `$.seed[0].lampiran`. Jalurnya
   `ProposalEntityRules.checkValue` (sudah ditulis A0) — tes eksplisit ini menguncinya agar refactor
   validator tidak bisa menghilangkan aturan diam-diam. Kontras: nilai kosong tetap sah.
2. **Usulan deterministik bebas FILE** (`DeterministicScreenProposerTest`): `cardOf(FILE) == null`
   (FILE tidak punya gaya kartu — kontras dengan DATE), dan tidak ada `FieldProposal` FILE di semua
   layar kelima vertikal uji, atau petunjuk FILE di pack deterministiknya. Alasannya bukan selera:
   **kontrol unggah baru ada di Track C**, jadi usulan otomatis yang memunculkan FILE akan
   menghasilkan layar yang tak bisa diisi.
3. **Pemetaan usulan pack** (`PackSuggestionMappingFileTest`): petunjuk FILE yang **dideklarasikan
   pack** (data, bentuk persis pilot layanan: papan berbinding Api tanpa baris contoh) diteruskan
   utuh dan lolos validator; tetapi pemetaan **tidak pernah mengarang** FILE dari baris contoh.

## 5. Jebakan yang kami temui (dan polanya)

- **`ScreenSuggestion` menolak baris contoh bernilai kosong** (`DomainPack.kt`: "punya baris contoh
  kosong"). Upaya pertama menulis `"lampiran" to ""` di seed gagal di konstruktor. Pelajarannya:
  bentuk sah konteks kedua adalah **papan dideklarasikan tanpa baris contoh** — bukan baris dengan
  nilai kosong yang dipaksakan.
- **Field kelompok papan tidak boleh dideklarasikan dua kali**: `status` adalah `groupField`
  (dipaksa ENUM dari kolom papan); mendeklarasikannya lagi sebagai `FieldHint` membuat kunci kembar
  di entitas usulan. Mirror pack pilot, jangan mengarang bentuk sendiri.
- **Asersi null-safe di Kotlin test**: `p.entity?.fields?.none { … } == true` bernilai `false` untuk
  layar tanpa entitas (dasbor) — `null == true`. Pola yang benar untuk "tidak boleh ada":
  `p.entity?.fields?.any { … } != true`.

## 6. Verifikasi

- `./gradlew :core:jvmTest` — **1760 tes hijau** (termasuk 5 tes baru/terubah di slice ini).
- `./gradlew :app:shared:jvmTest` — **318 tes, 0 gagal** (sampel kontrak dipakai UI tetap hidup).
- Kompilasi `:app:shared:compileKotlinJvm` / `compileKotlinWasmJs` / `compileKotlinJs` — hijau.
- `scripts/audit-variability.sh` — **0 temuan**.
