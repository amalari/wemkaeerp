# Teaching — TRD-FIELD-001 Track C: Pemilih Rujukan (`RELATION`) di UI

> Dokumen mentoring teknis untuk junior developer. Dibuat setelah implementasi Track C (Irisan 4a)
> tipe field `RELATION`. Rujukan: `docs/trd/TRD-FIELD-001-relation.md` (FR-7, §4.3, §4.6),
> `docs/plannings/PLAN-field-component-gaps.md` §1 C7, `.claude/rules/design-system-rules.md`,
> `.claude/rules/field-component-rules.md`.

---

## Step 0 — Pahami dulu apa yang sedang dibangun

Sebelum menulis satu baris, jawab: **"nilai field `RELATION` yang tersimpan itu apa?"**

Jawabannya bukan "record target". Yang tersimpan hanyalah **id baris target sebagai string**
(`FieldType.RELATION` di kosakata prototype, `FieldType.Relation` di kosakata CRM). Itu keputusan
arkitektur inti (TRD §4.3 K1/K3/K4) dan konsekuensinya menjalar ke seluruh UI:

1. **Tidak ada JOIN lintas schema** — label target tidak datang bersama baris pemegang field.
   Label harus *dicari terpisah* lewat route `GET /api/tenant/relation-options` (Track B).
2. **Target bisa hilang** — karena tidak ada FK, record target bisa dihapus. Nilai id di sel
   pemegang tetap ada, tapi labelnya tidak ketemu. UI **wajib** menanganinya ("tidak ditemukan"),
   bukan error merah, bukan menghapus data diam-diam (FR-3).
3. **Dua kosakata, dua jalur, satu kontrol** — prototype punya enum `FieldType`, CRM punya
   `sealed interface FieldType`. Per keputusan D2 keduanya **tetap terpisah**, tapi FR-7 menuntut
   **kontrol UI yang sama** (`ClayRelationPicker`). Jadi lapisan UI harus netral dari kedua kosakata.

Mental model: pisahkan **nilai** (string id, hidup di sel) dari **tampilan** (label, hidup di cache
sementara) dari **pemilih** (komponen netral yang menulis id kembali ke sel).

---

## Step 1 — Baca batas yang tidak boleh dilanggar

Tiga pagar menentukan bentuk solusi:

1. **`design-system-rules.md` Kontrak 6** — komponen di `presentation/designsystem/` harus **buta
   domain**: tidak impor `domain/` maupun package fitur. `ClayRelationPicker` karena itu **tidak
   menerima** `FieldSpec`/`LeadFieldDescriptor`; ia menerima `String`, `Color`, lambda, dan **tipe
   UI lokal** `RelationOption`.
2. **`design-system-rules.md` Kontrak 1/5** — nol literal `Color(0xFF…)`, nol
   `Modifier.shadow()`, nol `Card`/`Button` Material. Semua lewat token Clay + `ClayCard`/
   `ClayTextField`/`clayFlat`.
3. **Batas task** — HANYA `app/shared/src/commonMain/.../presentation` dan `.../commonTest`. Kalau
   butuh kontrak baru di `core/` atau route `server/`, **kembalikan sebagai permintaan**; jangan
   pindahkan pagar sendiri.

Kenapa batas ini penting sampai level "kapan pakai tabel/label"? Karena taruhannya bukan estetika:
route opsi membuka jalur **baca lintas modul**. Kalau kontrol UI sekaligus jadi tempat menghitung
kewenangan, kewenangan itu bocor ke mana-mana. UI hanya **meminta**; server yang **memutuskan**.

---

## Step 2 — Bangun fondasi: komponen buta domain + helper murni

### 2a. `ClayRelationPicker.kt` (designsystem)

Tipe UI lokal yang menyeberang batas:

```kotlin
data class RelationOption(val id: String, val label: String) {
    init { require(id.isNotBlank()) { "RelationOption.id cannot be blank" } }
}
```

`RelationOption` sengaja hidup di design system, bukan di domain, supaya kedua kosakata bisa
memetakannya ke bentuk yang sama tanpa saling mengenal.

Tanda tangan mengikuti FR-7 persis (`query`, `onQueryChange`, `options`, `selectedId`, `onSelect`,
`enabled`, `isError`, `label`), ditambah parameter opsional ber-default (`selectedLabel`, `isLoading`,
`placeholder`) sehingga bentuk minimum FR-7 tetap utuh.

**Pelajaran penting — angkat logika ke fungsi murni.** Pencarian diangkat ke
`filterRelationOptions(options, query)` (tanpa Compose). Kenapa? Karena test Compose di
`commonTest` lintas 5 target mahal dan rapuh; fungsi murni bisa dites cepat dan justru mengunci
perilaku (studi kasus sama: `isValidIsoDate` di `ClayDatePicker`). Ini bukan sekadar demi test —
memisahkan "aturan" dari "render" membuat aturan itu bisa dipakai ulang dan tidak bisa diam-diam
berubah bersama tata letak.

**Jebakan yang dihindari:** memakai `DropdownMenu` Material demi cepat. Itu membawa gaya Material
(radius, elevasi, ripple) ke tengah bahasa clay dan melanggar Kontrak 5. Pilihannya: render daftar
opsi di permukaan `clayFlat` sendiri, dengan `IconSearch`/`IconClose`/`IconCheck` from the clay icon
set.

### 2b. `presentation/relation/RelationDisplay.kt`

Bentuk tampil nilai rujukan diringkas jadi satu fungsi murni yang **tidak memanggil jaringan**:

```kotlin
data class RelationDisplay(val text: String, val resolved: Boolean, val missing: Boolean)

fun relationDisplay(stored: String, labelFor: ((String) -> String?)?): RelationDisplay
```

Tiga kasus yang harus dibedakan — dan inilah inti FR-3:

| Kondisi | Tampilan | Warna |
|---|---|---|
| kosong | `—` | redup |
| label diketemukan | label | normal |
| resolver **ada** tapi id tak ketemu | `Tidak ditemukan (id)` | **abu** |
| resolver **tidak ada** (demo memori) | id apa adanya | normal |

**Mengapa `labelFor == null` bukan "tidak ditemukan"?** Karena "belum ada resolver" ≠ "target
hilang". Menampilkan "tidak ditemukan" saat resolver belum ada = berbohong (Kontrak 4 variability:
fallback senyap mengubah makna data). Fallback jujur = tampilkan id.

---

## Step 3 — Lapisan data: klien terhadap kontrak, bukan menunggu backend

### 3a. `RelationOptionsApi.kt`

Interface `RelationOptionsRemoteDataSource.search(tenantSlug, module, entity, query)` + implementasi
Ktor `GET /api/tenant/relation-options`. Ini **dibangun terhadap kontrak FR-4**, tidak menunggu
Track B — inilah yang dimaksud "build against the contract".

**Pelajaran:** kalau Anda menunggu backend selesai dulu, UI tidak pernah bisa dites dan kontraknya
justru tidak pernah dipakai sampai server "kira-kira begini". Menulis klien dulu memaksa kontrak
FR-4 jadi konkret (nama parameter `module`/`entity`/`q`, bentuk respons `[{"id","label"}]`).

**Kenapa ikut gate `tenantRequest`?** Karena route ini tenant-scoped dan **fail-closed**; setiap
permintaan wajib membawa token. Kita memakai `tenantRequest` (internal di modul yang sama) persis
seperti klien berkas field — satu jalur otentikasi, bukan tiga.

### 3b. `RelationFieldController.kt`

Kontroler menyimpan `query`, `options`, `isLoading`, `error`, dan **cache label** (`id → label`).
Keputusan penting: **debounce** (250 ms). Pemilih rujukan mengetik → banyak query kecil; NFR
menetapkan `p95 ≤ 300 ms`, jadi membanjiri server itu bug, bukan optimisasi.

`resolveRelationTarget(target, defaultModule)` adalah **parser tunggal** untuk notasi
`entityId` vs `moduleId:entityId` — Kontrak 4: satu parser, tanpa fallback senyap.

Dua factory dengan arti berbeda:
- `relationFieldControllerOrNull(binding, target, scope)` — prototype; `null` untuk `DataBinding.Memory`
  (demo memori tak punya server → kontrol pemilih tidak mungkin, **jangan dipalsukan**).
- `relationFieldControllerForResource(tenantSlug, resource, scope)` — CRM (`targetResource`, R4).

**Jebakan yang dihindari:** membuat `RelationFieldController` **di dalam composable tanpa `remember`**.
Setiap recompose akan membuat klien/scope baru dan memulai pemuatan ulang — persis alasan
`sharedFieldFileClient` ada di C8.

---

## Step 4 — Integrasi `FieldInput` (prototype): satu pintu, dua mode

`FieldInput` adalah satu-satunya pintu kontrol prototype (Kontrak 3 field-component-rules). Kita
menambah **satu parameter opsional** `relation: RelationFieldUi? = null` — bukan menaruh logika
pemilih di layar fitur.

```kotlin
FieldType.RELATION -> {
    if (relation != null && enabled) {
        // pemilih aktif: pengguna cari + pilih → tulis id ke sel
    } else {
        // baca-saja: label / fallback id / "tidak ditemukan" — TIDAK PERNAH kolom teks bebas
    }
}
```

Kenapa `relation == null` tetap baca-saja dan **tidak** jatuh ke `ClayTextField`? Karena memetakan
tipe yang belum bisa ditangani ke `TEXT` "mengubah data tanpa jejak" — dilarang Kontrak 8. Lebih
jujur menyatakan "belum bisa memilih" lewat tampilan baca-saja.

Tiga state prototype (`InteractiveTableState`, `InteractiveKanbanState`, `InteractiveFormState`)
mendapat metode `relationField(key)` yang mengembalikan kontroler dari binding — pola yang sama
dengan `fileFieldOps` (C8). Ini menjaga `FieldInput` tetap bodoh: ia tidak tahu dari mana opsi
datang.

---

## Step 5 — Konteks baca: tabel & kanban

Ini bagian yang sering diremehkan. **Membaca** nilai rujukan berbeda dari **menyuntingnya**:

- `TableCell` (mode baca) dan `KanbanCardContent` memakai `relationDisplay(...)` dengan resolver
  `{ id -> state.relationLabels[id] }`.
- Cache `relationLabels` default kosong → tampilan id apa adanya sampai host mengisinya.
- Bila cache tersedia tapi id tak ada → abu "Tidak ditemukan (id)".

**Mental model:** cache label adalah *data tampilan*, bukan sumber kebenaran. Kalau kosong, UI tidak
boleh menebak; ia menampilkan id (fallback jujur).

---

## Step 6 — Kosakata CRM: pemakai kedua yang sama

Ini justru yang membuktikan desainnya benar. Karena `ClayRelationPicker` buta domain, CRM memakainya
tanpa perubahan komponen:

- `LeadFieldControl` — pemeta murni tipe→kontrol; cabang `RELATION` sudah ada (kompilator memaksa
  `when` tanpa `else`). Komentarnya diperbarui: kini ada editor aktif.
- `LeadCustomField` — `RelationEditor` membangun kontroler via `relationFieldControllerForResource`
  (tenant dari sesi) dan merender `ClayRelationPicker` saat `editable`; baca-saja merender label.
- `AddCustomFieldDialog` — menambah tipe "Rujukan ke Record" **ala DateField**: tipe baru yang butuh
  parameter (`targetResource`) menampilkan input tambahan dan **memblokir** penambahan sampai terisi.
  Ini lebih baik daripada membuat `Relation(targetResource = "placeholder")` — konstruk yang
  melanggar invariant domain hanya untuk membuat tombol aktif.

**Pelajaran desain:** komponen netral + adapter per kosakata itu **lebih murah** daripada menyatukan
dua kosakata. Menyatukan menyentuh CRM yang sudah berjalan (D2) demi manfaat yang baru terasa di
tipe ke-6.

---

## Step 7 — Test: kunci perilaku, jangan kunci tata letak

Yang diuji murni (tanpa Compose, jalan di semua target):

1. `filterRelationOptions` — kosong = semua; cocok label/id tak case-sensitive; tak ada = kosong.
2. `relationDisplay` — empat kasus pada tabel Step 2b.
3. `resolveRelationTarget` — tanpa `:`, dengan modul, dan bentuk tepi (`""`, `":kain"`).
4. `RelationFieldController` — `prime()` memuat opsi + cache label; kueri kosong membersihkan tanpa
   memanggil sumber; galat permukaan.
5. `RelationFieldControlParityTest` — kolom `RELATION` di layar **memori** tidak menghasilkan
   kontroler (`null`) → jalur baca-saja, bukan pemalsuan `TEXT`.

Test paritas yang sudah ada (`PrototypeFieldControlParityTest`, `LeadFieldControlParityTest`)
mengiterasi seluruh `FieldType.entries`/`ALL_CODES` dan memastikan `RELATION` punya kontrol sendiri.

**Kenapa tidak Compose UI test?** Karena `commonTest` KMP harus jalan di JVM/JS/Wasm/Android. Menguji
aturan sebagai fungsi murni memberi sinyal yang sama dengan biaya jauh lebih kecil dan tanpa rapuh
pada detail tata letak.

---

## Step 8 — Verifikasi & ratchet

- Kompilasi lintas target: `:app:shared:compileKotlinJvm`, `:compileKotlinJs`,
  `:compileKotlinWasmJs`, `:jvmTest` hijau. (`:assembleAndroidMain` butuh Android SDK — tidak tersedia
  di environment ini; dicatat sebagai sisa verifikasi, bukan kegagalan kode.)
- Ratchet file (CLAUDE.md §14): file yang disentuh tidak melewati hard limit lapisannya.
  `LeadCustomField.kt` melewati **soft** (400) tapi di bawah **hard** (600) — boleh, ditandai untuk
  cicilan berikut.
- Nol literal warna/shadow/Material Card-Button di file baru.

---

## Step 9 — Yang belum selesai (jujur)

1. **Pemetaan `targetResource` → (module, entity) untuk CRM** diasumsikan sementara (`resource`
   tanpa `:` diperlakukan sebagai modul `resource` + entity `resource`). Track B/`RelationTargetResolver`
   yang memiliki pemetaan eksak — kembalikan sebagai permintaan bila kontraknya berbeda.
2. **Pengisian cache label pada tabel/kanban** (`relationLabels`) belum diisi otomatis (batch lookup
   / route baca label belum ada). Saat ini default kosong → fallback id. Penambahan batch resolve
   adalah lanjutan Track B/C.
3. `LeadCustomField.kt` melewati soft limit — kandidat pemecahan `RelationEditor`/`FileEditor` ke file
   sendiri berikutnya.

**Cara berpikir yang bisa dibawa ke fitur berikutnya:** jawab dulu "apa yang tersimpan vs apa yang
ditampilkan", letakkan komponen di lapisan netral, biarkan kontrak (bukan backend) memandu bentuk
klien, dan render *jujur* untuk setiap keadaan yang belum terpenuhi — jangan pernah mengubah tipe
data karena UI belum bisa menggambarnya.
