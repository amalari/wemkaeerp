# 🎓 Modul Pembelajaran: A0 Tipe Field `RELATION` (TRD-FIELD-001)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Kosakata Tertutup (Closed Vocabulary), Logical Foreign Key, Sealed Class vs Enum, Codec Fail-Closed, Paritas Test
> **Prasyarat**: Paham struktur modul `core`/`server`/`app-shared`, dasar Kotlin `sealed interface` & `enum class`, dan pernah membaca `field-component-rules.md`
> **Referensi Task**: `docs/trd/TRD-FIELD-001-relation.md` (komit A0 — Track A tahap pertama)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Tenant ingin SPK merujuk ke PO, atau lead merujuk ke record modul lain. Cara naif yang orang langsung pikirkan: "ya bikin Foreign Key database saja!" — `REFERENCES schema_lain.tabel(id)`. Di platform multi-tenant dengan schema-per-modul, itu bom waktu:

1. **Pagar J3** (TRD-PLAT-004) melarang migrasi menyentuh schema modul lain.
2. **Promosi modul = salin + ganti prefiks schema** (P5) — satu kali promosi, semua FK fisik patah.
3. FK lintas schema membuat modul tidak lagi bisa dipindah-pindah seperti kepingan Lego.

**Analogi Sederhana**: Rujukan RELATION itu seperti **menulis alamat di catatan kamu** ("lihat nota #123 di folder Pembelian"), bukan **menyegel fotokopi nota itu di dalam catatan kamu**. Kalau folder Pembelian pindah lemari, alamatnya masih bisa diikuti; fotokopi justru jadi sampah basi yang keliru.

**Hasil Akhir A0 ini**: kedua kosakata field (prototype & CRM) punya tipe `RELATION` yang *sah menurut kompilator*, dengan kontrak: nilai sel = **string id baris target**, target dideklarasikan sebagai metadata (`target`/`targetResource`), dan **tidak ada satu pun `REFERENCES`** yang lahir dari generator SQL. Perilaku runtime (validasi keberadaan target saat tulis, route pencarian opsi, UI pemilih) menyusul di Track B & C.

---

## 🧭 2. "Start dari Mana?" — Alur Penulisan

1. **Langkah 0: Baca TRD-nya dulu.** Kontrak A0 di TRD-FIELD-001 §4.3 adalah *tanda tangan persis* — nama tipe, nama parameter, bentuk validasi. Jangan berimprovisasi sebelum membaca keputusan K1–K6.
2. **Langkah 1: Kosakata prototype** (`EntitySpec.kt`) — tambah `RELATION` ke enum + parameter `target` di `FieldSpec` + validasi `init`. Kompilator langsung menunjuk semua titik yang wajib disentuh.
3. **Langkah 2: Kosakata CRM** (`customfield/FieldType.kt`) — `data class Relation` terpisah (keputusan D2: jangan menyatukan kosakata).
4. **Langkah 3: Port domain** (`RelationTargetResolver`) — kontrak verifikasi keberadaan target, bentuk saja; implementasinya Track B.
5. **Langkah 4: Pemetaan SQL** (`SpecColumns.sqlDefinition`) — `VARCHAR(64)` tanpa `REFERENCES`.
6. **Langkah 5: Codec** — kawat JSON kedua kosakata membawa `target`/`targetResource` utuh; nilai tak dikenal tetap ditolak.
7. **Langkah 6: Ikuti kesalahan kompilator** sampai habis (ChangeWidgetOp, ProposalEdit, FieldInput, LeadFieldControl, ...). Setiap error `when must be exhaustive` = satu titik pendaftaran yang tidak bisa terlewat.
8. **Langkah 7: Perbarui fixture tes paritas** (`PrototypeFieldTypeSampleFields` dll.) — tes paritas sengaja dibuat *gagal kompilasi* saat tipe baru lahir.

**Kenapa mulai dari domain, bukan dari UI?** Karena tipe adalah *kontrak*; UI, route, dan SQL hanyalah konsekuensi dari kontrak itu. Kalau UI dulu, kamu akan memalsukan rujukan jadi kolom teks — persis anti-pola yang dilarang Kontrak 8.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Validasi `target` di `FieldSpec` (prototype)

```kotlin
if (type == FieldType.RELATION) {
    require(!target.isNullOrBlank() && !target.contains(' ') && target.count { it == ':' } <= 1) {
        "Field RELATION '$key' wajib punya target 'entityId' atau 'moduleId:entityId' ..."
    }
} else {
    require(target == null) { "Field '$key' bertipe ${type.name}, bukan RELATION, jadi tidak boleh punya target" }
}
```

**Mengapa begini?**
- Polanya **identik dengan `currencyCode`** (wajib tepat untuk CURRENCY, haram di luar itu). Satu invarian = satu gaya penulisan di seluruh file.
- Validasi `init` = *fail-fast at the gate*: spesifikasi rusak tidak mungkin terbentuk, jadi semua kode di bawahnya (codec, generator, UI) tidak perlu mengecek ulang bentuk target.
- Perhatikan apa yang **sengaja tidak** divalidasi di sini: *keberadaan* baris target. Itu pekerjaan server saat tulis nilai (Track B, lewat `RelationTargetResolver`) — konstruktor domain tidak boleh butuh database.

### Blok B: `Relation` di kosakata CRM

```kotlin
data class Relation(val targetResource: String, val maxCount: Int = 1) : FieldType {
    override val code: String = "RELATION"
    override val isReferential: Boolean = true
}
```

**Mengapa begini?**
- `isReferential = true` menandai tipe yang punya baris link (`custom_field_relation_links` — Track B), sama seperti `UserRef`.
- `targetResource` = *kunci resource* (mis. `"leads"`, `"employees"`), bukan nama tabel SQL — resource adalah konsep domain, bukan konsep storage.
- `ALL_CODES` ditambah **string literal polos** `"RELATION"`, bukan `Relation.code` — jebakan static-init JVM yang sudah terdokumentasi di KDoc companion (`ExceptionInInitializerError` jika Companion `<clinit>` jalan sebelum `Relation.<clinit>`).

### Blok C: Kolom SQL tanpa FK fisik

```kotlin
// C7: rujukan LOGIS — id baris target saja, TANPA `REFERENCES` lintas schema
FieldType.RELATION -> "VARCHAR(64)$notNull"
```

**Mengapa begini?** Ini inti K1: *logical FK*. DB tidak bisa menjaga integritas lintas schema → siapa yang menjaga? **Modul pemegang field**, saat tulis nilai (fail-closed, 400 bila target tak ada). Test `sqlDefinition_relationColumn_neverEmitsReferences` mengunci invarian ini selamanya — kalau suatu hari ada yang menambah `REFERENCES`, tesnya merah.

### Blok D: Codec CRM menolak, bukan menebak

```kotlin
"RELATION" -> config.string(KEY_TARGET_RESOURCE)?.let {
    FieldType.Relation(targetResource = it, maxCount = config.int(KEY_MAX_COUNT) ?: 1)
}
```

**Mengapa begini?** `targetResource` hilang → `null` = **data korup**, bukan sinyal untuk "pakai default". Membaca korupsi sebagai nilai default berarti **mengubah data tanpa jejak** (Kontrak 4 variability). Bandingkan dengan `maxCount` yang boleh default `1` — kunci hilang = baris lama sebelum parameter dikenal; itulah bedanya "absen" dan "tak dikenal".

---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

| Keputusan | Alternatif | Kenapa dipilih | Risiko alternatif |
|---|---|---|---|
| Logical FK (string id) | `REFERENCES` lintas schema | Pagar J3; promosi modul = salin+prefiks mematahkan FK fisik | FK patah saat promosi; migrasi ditolak fence |
| `FieldSpec.target: String?` | JSON `{moduleId, recordId}` per sel | Sel prototype = peta string murni (kontrak `PrototypeRow`); seed & codec tetap sederhana | JSON polimorfik per sel memecah codec, seed, validator |
| Tabel CRM baru (`custom_field_relation_links`) | Menyatu dengan `custom_field_links` | Tabel lama mengunci FK `employees(id)` milik `UserRef` yang berjalan | Refactor tabel berjalan = regresi CRM tanpa kebutuhan (strangler) |
| Enum + sealed terpisah per kosakata (D2) | Satu kosakata bersama | Menyatukan menyentuh CRM berjalan; manfaat baru terasa setelah tipe ke-6 | Gabungan memaksa semua pemakai prototype mengenal varian CRM |

---

## ⚠️ 5. Jebakan Pemula

1. **Memalsukan RELATION jadi kolom teks di UI.** Karena picker-nya belum ada (Track C), godaan menaruh `ClayTextField` sangat besar. Itu memungkinkan id karangan masuk — persis yang dilarang Kontrak 8. Solusi A0: render **baca-saja** (`FieldInput` menampilkan id atau "—").
2. **Menambah `else ->` untuk "menyenangkan" kompilator.** Kontrak 6: `when (FieldType)` tanpa `else`. Error kompilator adalah *daftar pendaftaran gratis* — matikan dengan cabang nyata, bukan dengan `else`.
3. **Lupa kawat codec.** Menambah parameter `FieldSpec.target` tanpa menambah `"target"` di `InteractiveScreenCodec`/`SpecOpCodec` = target hilang diam-diam saat spec lewat kawat (round-trip `assertEquals(op, decoded)` merah). Parameter baru di spec = parameter baru di codec, *di komit yang sama*.
4. **Mengubah `FieldSpec` tanpa mengubah generator literal.** `SpecRoutesWriter.entityLiteral` mencetak konstruktor `FieldSpec(...)` sebagai teks Kotlin — RELATION tanpa `target = "..."` tercetak akan ditolak saat spec hasil generate dibangun. Itulah kenapa generator menambahkan `, target = ...` hanya bila non-null.
5. **Menyentuh `DealRoutes.kt` / file utang.** Ratchet §14: file di atas hard limit tidak boleh bertambah. (Saat Track B nanti: route baru = file baru.)

---

## 🧪 6. Membuktikan Kode Bekerja

Strategi berlapis, semuanya sudah hijau setelah komit ini:

- **Paritas SQL** (`PrototypeFieldTypeSqlParityTest`): iterasi `FieldType.entries` → tiap tipe punya kolom SQL & kolom Exposed; RELATION khusus diuji **tidak pernah** memancarkan `REFERENCES`.
- **Paritas codec** (`PrototypeFieldTypeCodecParityTest`): round-trip keempat kawat untuk tiap tipe; tes baru memastikan `target` RELATION utuh melalui layar interaktif & SpecOp, dan RELATION tanpa target **ditolak** saat decode.
- **Paritas CRM** (`CrmFieldTypeParityTest`): `coverage` (when tanpa `else`) + sampel per kode `ALL_CODES`; validasi sel Str; konversi dari/ke Relation = `FORBIDDEN`; RELATION tanpa `targetResource` = `null` (korupsi).
- **Paritas kontrol UI** (`PrototypeFieldControlParityTest`, `LeadFieldControlParityTest`): tiap tipe punya kontrol; Relation punya kontrol sendiri `RELATION`, tidak dipalsukan jadi TEXT/USER_REF.
- **Kompilasi**: JVM + WasmJs + JS hijau; jvmTest core (1750+ tes) & app hijau; test server discovery/CRM hijau.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Jelaskan mengapa `FieldSpec.accepts` untuk RELATION menolak `".."` padahal id rujukan biasanya tidak mengandung titik ganda. (Petunjuk: nilai sel ini akan jadi bagian key objek/argumen baca — bentuk buruk ditolak di gerbang paling murah.)
- [ ] **Tantangan 2**: Track B akan membuat `RelationTargetResolver` di server. Draft implementasi in-memory-nya (fake) untuk test route: apa tanda tangannya, dan di mana ia disuntik?
- [ ] **Tantangan 3**: Coba tambahkan satu FieldSpec RELATION lintas modul (`target = "crm:lead"`) ke fixture `PrototypeFieldTypeSampleFields` versimu sendiri dan jalankan `PrototypeFieldTypeSqlParityTest` — amati bagaimana satu perubahan kosakata merambat ke lima lapis tanpa satu pun `else`.
