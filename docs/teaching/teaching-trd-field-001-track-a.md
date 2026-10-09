# 🎓 Modul Pembelajaran: TRD-FIELD-001 Track A (sisa) — Rujukan `RELATION` di mesin usulan, codec, dan generator

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Kosakata tipe field tertutup, validasi fail-closed, codec multi-target (KMP), `when` tanpa `else` sebagai pagar kompilator, kotak-pasir pack (Kontrak 6/7 variability)
> **Prasyarat**: `FieldType`/`FieldSpec` (prototype) & `sealed FieldType` (CRM), `ScreenProposalValidator`, JSON `JsonValue`
> **Referensi Task**: [`docs/trd/TRD-FIELD-001-relation.md`](../trd/TRD-FIELD-001-relation.md) §4.6 Track A; [`docs/plannings/PLAN-field-component-gaps.md`](../plannings/PLAN-field-component-gaps.md) §2 Irisan 4a

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Sebuah SPK perlu "menunjuk" PO; sebuah lead perlu menunjuk record modul lain. Cara
naif adalah foreign key (`REFERENCES`) — tapi di WeMade, modul hidup di **schema terpisah** dan modul
boleh dipromosikan dengan **salin + ganti prefiks** (TRD-PLAT-004 P5). FK fisik akan patah begitu itu
terjadi, dan pagar J3 melarang menyentuh schema di luar daftar putih. Jadi rujukan harus **logis**:
yang disimpan cuma **id baris target sebagai string**.

**Analogi sederhana.** FK seperti menempel nomor rumah dengan *paku*: kalau jalan diganti nama, paku itu
menunjuk ke tempat yang salah. Rujukan logis seperti menulis alamat **di catatan**: peta bisa berubah,
catatan tetap satu-satunya yang tahu "alamat" itu milik siapa — dan **kita yang memeriksa** alamatnya ada
saat menulis, bukan database.

**Hasil akhir setelah slice ini.** Tipe `RELATION` mengenal **target** (`"entityId"` atau
`"moduleId:entityId"`), validator usulan **menolak target yang tidak masuk akal** sebelum dokumen
tersimpan, codec dua kosakata **round-trip penuh tanpa fallback**, dan generator SQL **tidak pernah**
memancarkan `REFERENCES` untuk kolom itu.

**Konteks penting:** A0 (kontrak tipe) sudah merge: `enum FieldType.RELATION`, `FieldSpec.target` +
validasi bentuknya, `Relation(targetResource, maxCount)` di CRM, port `RelationTargetResolver`, dan
`SpecColumn` → `VARCHAR(64)`. Sisa Track A **membangun di atasnya tanpa mengubah kontrak A0**.
Sebagian besar `when (FieldType)` sudah dibuat eksahustif oleh Track A `FILE` (TRD-FIELD-002) karena
kedua tipe berbagi enum — jadi slice ini fokus ke bagian yang **belum** ada: kosakata usulan (`FieldProposal.target`),
penegakan target, kabel codec usulan, sampel kontrak, dan fixture pack non-default.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Langkah 0: bentuk (`FieldProposal.target`).** Sebelum bisa memvalidasi, tipe data harus punya
   tempat untuk `target`. Ini lapisan `core/.../discovery/proposal/ScreenProposal.kt`.
2. **Langkah 1: aturan (`ProposalEntityRules.checkTarget`).** Validasi target ada di satu tempat bersama
   aturan field lain; ia butuh tahu modul yang bisa diresolusi (`packModuleIds`).
3. **Langkah 2: kabel (codec + konversi + pemetaan pack).** `ScreenProposalCodec` (JSON),
   `ScreenProposalConversion` (usulan → `InteractiveScreen`), `PackSuggestionMapping` (petunjuk pack →
   field usulan). Inilah titik di mana `target` paling mudah **hilang diam-diam**.
4. **Langkah 3: snapshot generator.** `SpecRoutesWriter.entityLiteral` mencetak `FieldSpec(...)` —
   arg `target` wajib ikut, kalau tidak generator memancarkan RELATION tanpa target (ditolak validasi).
5. **Langkah 4: konteks kedua (pack non-default).** Tambah RELATION ke `LayananPilotPack` agar mesin
   (SQL, codec, parity board) **benar-benar** menjalankan tipe ini di data non-garment (Kontrak 7).
6. **Langkah 5: CRM.** Sel rujukan (`CustomAttributes.relationCell/relation`) + **AI prefill menolak**
   `Relation` (jangan mengarang id).
7. **Langkah 6: tes.** Baru di sini bukti: paritas iterasi enum, 3 bentuk target tak sah, tanpa
   `REFERENCES`, fixture pack non-default.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Kontrak bentuk: `FieldProposal.target`

```kotlin
// ScreenProposal.kt
data class FieldProposal(
    val key: String, val label: String, val type: FieldType,
    /* ...field lama... */ val validation: TextValidation = TextValidation.NONE,
    /** Wajib tepat bila type == RELATION, wajib null selain itu. Format "entityId" / "moduleId:entityId". */
    val target: String? = null
)
```
**Mengapa begini?**
- `FieldProposal` sengaja **tidak melempar** di konstruktor (dokumen usulan = keluaran LLM yang harus
  bisa dikoreksi dengan galat **berpath**). Karena itu validasi bentuk tidak ditaruh di `init`.
- Menambah `target` di **ujung** dengan default `null` menjaga kompatibilitas biner/kode lama: semua
  pemanggil posisional ≤9 arg tetap kompilasi. Tapi **hati-hati**: pemanggil yang mengoper argumen
  **secara posisional** dan berhenti sebelum arg ke-10 akan **kehilangan** `target`. Dua di antaranya
  harus diperbaiki: `ScreenProposalConversion.toEntitySpec` dan `PackSuggestionMapping.FieldHint.toProposal`.

### Blok B — Aturan target (satu tempat, fail-closed)

```kotlin
// ProposalEntityRules.kt
private fun checkTarget(f: FieldProposal, at: String, sink: IssueSink, packModuleIds: Set<String>?) {
    if (f.type != FieldType.RELATION) {
        if (f.target != null) sink.add("$at.target", "…bukan RELATION…tidak boleh punya target"); return
    }
    if (f.target.isNullOrBlank()) { sink.add("$at.target", "Field RELATION wajib punya target…"); return }
    if (f.target.any { it.isWhitespace() } || f.target.count { it == ':' } > 1) { sink.add("$at.target", "…format…"); return }
    val parts = f.target.split(':')
    if (parts.any { it.isBlank() }) { sink.add("$at.target", "…bagian kosong…"); return }
    if (parts.size == 2 && packModuleIds != null && parts[0] !in packModuleIds) {
        sink.add("$at.target", "Target lintas modul '${parts[0]}' tidak dapat diresolusi pack ini…")
    }
}
```
**Mengapa begini?**
- **Tiga bentuk tolakan** (kriteria terima §5) persis: (1) tanpa target, (2) bentuk salah
  (spasi/dua `:`/bagian kosong), (3) modul lintas yang tak bisa diresolusi. Masing-masing `return` awal
  supaya tidak ada galat beruntun pada satu field.
- `packModuleIds == null` = "konteks pack tak diketahui" (mis. `toInteractiveScreen` tanpa konteks) →
  resolusi modul **tidak bisa** diperiksa, jadi dilewatkan. Ini pasangan pola yang sudah ada di
  `ProposalViewRules.checkDashboard` untuk ubin dasbor.

### Blok C — Kabel codec & konversi

```kotlin
// ScreenProposalCodec.kt (encode)
"withTime" to jsonOf(f.withTime), "validation" to jsonOf(f.validation.name),
"target" to jsonOf(f.target)              // null untuk tipe selain RELATION
// (decode)
target = f.optString("target")

// ScreenProposalConversion.kt — argumen posisional ke-10!
fields.map { FieldSpec(it.key, it.label, it.type, it.options, it.required, it.format,
                       it.currencyCode, it.withTime, it.validation, it.target) }

// PackSuggestionMapping.kt — petunjuk pack → field usulan
private fun FieldHint.toProposal() = FieldProposal(key, key, type, required, options, format,
                                                   currencyCode, withTime, validation, target)
```
**Mengapa begini?**
- Codec **tidak** memvalidasi bentuk target; ia hanya memastikan **bentuk kawat**. Aturan bisnis tetap
  milik validator (satu sumber aturan).
- `ScreenProposalConversion` adalah tempat `target` **wajib** diteruskan: tanpa itu, usulan RELATION yang
  lolos validator akan gagal saat `FieldSpec` dibangun (RELATION tanpa target) — bug yang hanya muncul
  pada jalur konversi, mudah terlewat.

### Blok D — Snapshot generator & kolom SQL

```kotlin
// SpecRoutesWriter.entityLiteral (sudah ada dari A0, dipastikan tetap benar)
(if (f.target != null) ", target = " + SpecNaming.kString(f.target) else "")

// SpecColumns.kt (A0): RELATION → VARCHAR(64), TANPA REFERENCES — pagar J3.
FieldType.RELATION -> "VARCHAR(64)$notNull"
```
**Mengapa begini?** Generator menghasilkan `FieldSpec(...)` sebagai **literal Kotlin**; menambah argumen
posisi tanpa `target` akan membuat PR kandidat **tidak kompilasi** (FieldSpec RELATION tanpa target
melempar). Karena itu snapshot keluaran generator diuji di komit yang sama.

### Blok E — Konteks kedua: `LayananPilotPack`

```kotlin
// entity & spec & boardSuggestion, konsisten satu sama lain (PilotBoardParityTest menjaga)
FieldSpec("rujukan", "Permintaan terkait", FieldType.RELATION, target = "change_request")
```
**Mengapa begini?** Kontrak 7: fitur yang membaca konsep variabel wajib teruji di **tenant kedua
non-default**. Pack pilot `layanan` sudah jadi kanal konteks kedua untuk FILE; RELATION menumpang alur
yang sama (papan Api, tanpa seed). Ikuti polanya, jangan mengarang bentuk baru.

### Blok F — CRM: sel rujukan & AI prefill

```kotlin
// CustomAttributes.kt
fun relation(fieldId: CustomFieldId): String? = rawCell(fieldId)?.string("v")
fun relationCell(targetRecordId: String): JsonValue.Obj =
    jsonObjectOf("t" to jsonTag("relation"), "v" to JsonValue.Str(targetRecordId))

// LeadDraftSanitizer.kt — when tanpa else (Kontrak 6): RELATION TIDAK didukung prefill AI
is FieldType.DateField, is FieldType.Checkbox, is FieldType.UserRef, is FieldType.Relation, is FieldType.File -> null
```
**Mengapa begini?** Parser AI tidak boleh **mengarang** id rujukan. `supportedCustomFields` sudah
menyaring `Relation`, tetapi mengganti `else` dengan cabang eksplisit membuat kompilator menuntut
keputusan saat varian baru lahir — persis pagar Kontrak 6.

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Keputusan | Alternatif | Mengapa ini | Risiko alternatif |
|---|---|---|---|
| Rujukan **logis** `VARCHAR(64)` | FK `REFERENCES` lintas schema | Promosi modul = salin + prefiks (P5); FK fisik pasti patah; pagar J3 | Migrasi patah saat promosi; melanggar fence |
| Aturan target di **validator** berpath | Lempar di `data class` init | Keluaran LLM harus dikoreksi dengan path, bukan crash | Agent tak bisa memperbaiki; dokumen lama mati |
| `target` di ujung + default | Parameter wajib | Kompatibilitas posisional & dokumen lama | Ledakan call-site; PR raksasa |
| `when` **tanpa `else`** | `else -> TEXT` | Varian baru → gagal kompilasi = daftar pendaftaran tak bisa terlewat | Fallback senyap mengubah data (Kontrak 4) |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Argumen posisional yang diam-diam membuang `target`.** `FieldHint.toProposal()` dan
   `ScreenProposalConversion` memanggil konstruktor posisional; menambah parameter di ujung membuatnya
   tetap kompilasi tetapi `target` hilang. **Aturan**: setiap kali menambah field opsional, `grep`
   seluruh pemanggil posisional.
2. **Byte-stability codec usulan.** `ScreenProposalCodec` menulis kunci parameter **selalu** (pola
   saudaranya: `format`, `currencyCode`, …). Jangan menulis `target` hanya bila non-null — nanti encode
   ulang tidak lagi identik dengan dokumen yang sama.
3. **Padding migrasi `padEnd(11)`.** Nama field >11 karakter membuat `SpecMigrationWriter` **mem-`padEnd`
   tanpa efek** (1 spasi), sedangkan ≤11 → berpadding. Menambah field panjang mengubah **perataan** semua
   baris → asersi tes yang mengecek baris persis bisa pecah. Nama pendek (`rujukan`) menghindarinya.
4. **`when (FieldType)` dengan `else`.** Dilarang (Kontrak 6). Varian yang tak didukung harus **eksplisit**
   (mis. `-> null` atau `error(...)`), bukan `else -> TEXT`.
5. **Seed RELATION wajib kosong (R2).** `ProposalEntityRules.checkValue` menolaknya; jangan "mengisi
   contoh" id target — itu mengarang rujukan.
6. **Resolver tak menggantikan validasi bentuk.** `RelationTargetResolver` memverifikasi **keberadaan**
   record saat tulis (server, Track B); ia bukan alasan untuk melonggarkan bentuk `target` di klien.

---

## 🧪 6. Cara Membuktikan Kodingan Bekerja

`./gradlew :core:jvmTest` (dan `:core:compileKotlinWasmJs`/`:core:compileKotlinJs` untuk KMP):

- **Paritas iterasi `FieldType.entries`** (`PrototypeFieldTypeSqlParityTest`, `PrototypeFieldTypeCodecParityTest`,
  `CrmFieldTypeParityTest`): tiap entri punya padanan SQL, sampel katalog, dan round-trip codec. Entri
  baru tanpa padanan **menggagalkan kompilasi** (fixture `PrototypeFieldTypeSampleFields` adalah `when`
  tanpa `else`).
- **Tiga bentuk target tak sah** (`ProposalRelationTargetTest`, baru): tanpa target, bentuk salah
  (`"dua modul"`, `"a:b:c"`, `":foo"`, `"foo:"`), dan lintas modul tak teresolusi.
- **Tanpa `REFERENCES`** (`SpecScaffoldGeneratorTest` + `PrototypeFieldTypeSqlParityTest`):
  `SpecColumn.sqlDefinition()` RELATION selalu `VARCHAR(64)`, dan baris migrasi hasil generator untuk
  RELATION tak memuat `REFERENCES`.
- **Pack non-default** (`LayananPilotPack` + `PilotBoardParityTest` + `CrmFieldTypeParityTest`):
  RELATION dijalankan di konteks layanan/bordir, bukan konveksi.
- **AI prefill** (`LeadDraftSanitizerTest`/`ExtractLeadDraftUseCaseTest`): `Relation` tak pernah masuk
  spec prefill.

Sampel test kunci:

```kotlin
@Test fun relationTarget_crossModule_mustResolveInPack() {
    assertTrue(targetIssue(relation("lain:entitas"), packModuleIds = setOf("klinik_antrean")) != null)
    assertEquals(emptyList(), issuePaths(proposalWith(relation("klinik_antrean:pasien")), setOf("klinik_antrean")))
}
```

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: tambahkan cross-module target yang menunjuk **modul bersama** platform
      (`pack.moduleReferences`) dan buat validator menerimanya. Perluas `packModuleIds` di
      `DiscoveryDraftValidator` — tapi periksa dulu dampaknya ke ubin dasbor.
- [ ] **Tantangan 2**: tambah `SpecOp.SetFieldTarget` (ubah target RELATION lewat chat) beserta
      cabang codec + `when` baru; pastikan seed yang sah diperiksa ulang seperti `FieldParamOps`.
- [ ] **Tantangan 3**: tulis tes yang membuktikan `InteractiveScreenCodec`, `SpecOpCodec`,
      `ScreenSuggestionCodec`, dan `ScreenProposalCodec` semuanya **menolak** RELATION tanpa target di
      kawat sebagai korupsi bentuk — bukan menormalkannya.

---

## 8. Catatan sisa / permintaan lintas track

- **Regenerasi scaffold server** (`server/.../tenant/layanan/LayananChangeRequestRoutes.kt`, tabel,
  repositori, `V90`) belum dilakukan di track ini (batas direktori: `core/` saja). Pack (data) dan
  scaffold (kode) boleh berbeda sejenak sebelum Track B menyentuhnya; penyelarasannya urusan Track B.
- **`GarmentScreenSuggestions` sengaja tidak disentuh**: ia pack **bawaan platform** (identitas/byte
  yang dikunci), dan tidak punya rujukan natural; konteks kedua non-default sudah dipenuhi
  `LayananPilotPack` + fixture `bordir`.
- **Track B/C menyusul**: route `relation-options` fail-closed, implementasi `RelationTargetResolver`,
  migrasi `custom_field_relation_links`, katalog/prompt agent, dan `ClayRelationPicker`.
