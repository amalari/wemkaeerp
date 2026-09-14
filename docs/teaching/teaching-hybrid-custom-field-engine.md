# 🎓 Modul Pembelajaran: CRM Leads — Hybrid Core Entity + Dynamic Custom Field Engine

> **Level Target**: Mid Developer
> **Topik Utama**: Domain-Driven Design, hybrid schema design (typed columns + JSONB),
> multi-tenant RLS, RBAC DataScope enforcement, Kotlin Multiplatform shared codec,
> Compose Multiplatform adaptive layout
> **Prasyarat**: Kotlin, Exposed, Flyway, Ktor, Compose Multiplatform, konsep dasar DDD
> **Referensi Task**: Fondasi CRM Leads modul WeMade ERP (Issue #2, sebagian)

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

User ingin membangun modul CRM dengan monday.com sebagai referensi. Yang menarik
perhatiannya bukan fitur CRM-nya, melainkan **mesin kolom dinamisnya**: tenant bisa
menambah kolom sendiri (Instagram Handle, Budget Range, Sample Approved) tanpa developer
menyentuh kode.

Tantangannya: monday.com adalah *work-management tool* generik. WeMade adalah **ERP** —
ada uang, ada constraint bisnis, ada agregasi. Kalau semua field disimpan sebagai JSONB
generik ala monday.com, hal-hal berikut jadi menyakitkan:

- `SUM(estimated_value_idr)` untuk nilai total pipeline — mudah di kolom asli, susah di JSONB.
- `DataScope.OWN_DATA_ONLY` (RBAC) butuh predikat SQL berindex pada `owner_employee_id` —
  tidak bisa efisien kalau field itu terkubur di dalam blob JSON.
- Validasi transisi status (`LeadStage`) butuh enum bertipe kuat, bukan string bebas.

Solusinya: **hybrid model** (pola Salesforce/Odoo/Jira/Shopify Metafields). Field yang
punya logika bisnis jadi kolom asli; field yang murni pencatatan jadi custom field JSONB.

### Kenapa ini bukan sekadar "tambah tabel"

Salah bentuk di sini mahal dua arah:
- Terlalu banyak di custom → agregasi dan RBAC jadi lambat/rumit.
- Terlalu banyak di core → tenant tidak bisa menambah field sendiri, developer harus
  deploy tiap kali ada permintaan field baru — persis masalah yang mesin ini ingin hindari.

Aturan yang dipakai: **kalau sebuah field muncul di `WHERE`/`JOIN`/`SUM`/validasi
lintas-entity → core. Kalau hilangnya cuma merusak tampilan → custom.**

---

## 2. "Start dari Mana?" — Alur Langkah Penulisan

**Step 0 — Putuskan batas core vs custom.** Sebelum menulis satu baris SQL. Lihat tabel
keputusan di `V21__create_crm_leads.sql` — kolom apa saja yang jadi core (brand_name,
stage, owner_employee_id, estimated_value_idr, expected_close_date) dan kenapa.

**Step 1 — Migrasi SQL** (`V20`, `V21`, `V22`). Mesin custom field dulu (V20, dipakai
ulang 9 modul), baru entitas CRM (V21), baru seed data konveksi (V22, terpisah supaya
re-seed tidak mengedit migrasi yang sudah rilis).

**Step 2 — Value Object & tipe di `:core`.** `FieldType` sealed interface (7 varian:
Text, LongText, Number, SingleSelect, DateField, Checkbox, UserRef), `CustomFieldDefinition`,
`CrmLead`. Domain murni, tanpa Exposed, tanpa Ktor.

**Step 3 — Logika murni**: `FieldTypeConversion` (lattice konversi tipe),
`CustomFieldValidation` (required-on-write, bukan required-on-read), `LeadScope` (filter
`DataScope`, memakai `SubordinateResolver` yang diekstrak dari `OrgChartVisibility`).

**Step 4 — Persistensi**: tabel Exposed, repository Postgres + in-memory (test double).

**Step 5 — Route & guard**: `CrmAccessGuard` (enam titik penegakan wewenang),
`CrmRoutes` (REST surface).

**Step 6 — Codec bersama**: `CrmLeadCodec` di `core/.../shared/crm/` — dipakai server
DAN client, supaya wire format tidak pernah drift jadi dua parser berbeda.

**Step 7 — UI**: `CrmWorkspaceScreen` (adaptive breakpoint 840dp), `CrmViewModel` (MVI).

---

## 3. Pembedahan Kode Blok per Blok

### 3.1 Kenapa `CustomFieldId`, bukan nama field, jadi kunci blob?

```kotlin
// core/.../domain/customfield/CustomFieldIds.kt
@JvmInline value class CustomFieldId(val value: String)
```

Nilai custom field disimpan sebagai `{"cf-xxx": {"t":"text","v":"..."}}` — kunci blob
adalah **id**, bukan label ("Jenis Sablon"). Ini satu guardrail yang membuat rename kolom
gratis: admin ubah label dari "Jenis Sablon" jadi "Tipe Cetak", nol baris data yang perlu
disentuh. Kalau kuncinya nama, setiap rename berarti migrasi seluruh baris.

### 3.2 Kenapa nilai bertag (`{"t":"text","v":"..."}`), bukan `{"jenis_sablon": "..."}`?

```kotlin
// CustomAttributes.kt
fun textCell(value: String): JsonValue.Obj =
    jsonObjectOf("t" to jsonTag("text"), "v" to JsonValue.Str(value))
```

Bayangkan admin mengubah tipe field dari Text ke Number di tengah jalan. Tanpa tag, baris
lama (`"18.5 juta"`) dan baris baru (`18500000`) tidak bisa dibedakan cara bacanya. Dengan
tag, setiap sel tahu tipenya sendiri — baris lama tetap terbaca sebagai teks sampai
benar-benar dikonversi (lihat §3.4).

### 3.3 Seam paling penting: `LeadFieldDescriptor` + `LeadFieldProjection`

```kotlin
// core/.../domain/crm/LeadFieldDescriptor.kt
data class LeadFieldDescriptor(val fieldId: String, ...) {
    companion object {
        fun coreFieldId(name: String) = "core:$name"
        fun isCoreFieldId(fieldId: String) = fieldId.startsWith("core:")
    }
}
```

Domain tetap bertipe kuat (`CrmLead.brandName: BrandName`, bukan `Map<String, Any?>`).
Tapi UI butuh me-render core dan custom field **seragam** — kalau tidak, tiap tambah tipe
field baru berarti dua kali kerja (satu untuk core, satu untuk custom).

`LeadFieldProjection.cellsOf(lead)` (di `app/shared`) memproyeksikan kedua dunia itu ke
bentuk yang sama:

```kotlin
val core = mapOf(
    LeadFieldDescriptor.coreFieldId("brand_name") to CustomAttributes.textCell(lead.brandName.value),
    ...
)
val custom = lead.customAttributes.toJsonValue().entries.mapValues { (_, v) -> v as? JsonValue.Obj }
return core + custom
```

UI (`LeadCustomField` composable) memanggil `LeadFieldDescriptor` + sel hasil proyeksi ini
tanpa pernah tahu bedanya. Saat commit, `CrmViewModel.commitField` mem-dispatch balik
berdasar prefiks `"core:"` vs bukan — **satu-satunya tempat percabangan itu terjadi.**

### 3.4 Kenapa preview dan apply konversi tipe harus manggil fungsi yang sama?

```kotlin
// FieldTypeConversion.kt
fun coerce(cell: JsonValue.Obj?, from: FieldType, to: FieldType): CoercionResult?
```

`PreviewFieldTypeChangeUseCase` (dry-run, tidak menulis apa pun) dan `ChangeFieldTypeUseCase`
(fase 1.5, belum dibangun) **wajib** memanggil `coerce()` yang sama persis. Kalau preview
punya logika sendiri yang beda dari apply, preview yang berkata "aman" bisa saja diikuti
apply yang justru menghapus separuh data — preview yang berbohong lebih buruk daripada
tidak ada preview sama sekali.

### 3.5 Kenapa `required` divalidasi saat tulis, bukan saat baca?

```kotlin
// CustomFieldValidation.kt
fun validateForPatch(definitions, recordCreatedAt, patch): List<CustomFieldValidationError> {
    val requiredNow = def.isRequired &&
        (def.requiredSince == null || recordCreatedAt >= def.requiredSince || cell != null)
    ...
}
```

Bayangkan admin menambah field wajib "Approval Budget" bulan ini. Ada 500 lead lama yang
dibuat sebelum field itu ada. Kalau validasi jalan setiap kali lead dibaca/diedit, ke-500
lead lama itu **langsung tidak bisa diedit sama sekali** — bahkan untuk sekadar mengubah
nomor telepon — karena field wajib yang baru itu kosong. `requiredSince` menandai kapan
aturan itu mulai berlaku; hanya lead yang dibuat setelahnya (atau field itu sendiri yang
sedang disentuh) yang benar-benar diwajibkan.

### 3.6 Kenapa `SubordinateResolver` diekstrak, bukan disalin?

```kotlin
// SubordinateResolver.kt (baru)
fun reachableEmployeeIds(nodes, viewerEmployeeId, viewerDepartmentId): Set<OrgNodeId>
```

Logika "siapa saja bawahan saya" (union: sedivisi + closure `reportsTo`) sebelumnya
`private` di `OrgChartVisibility`. CRM Leads butuh logika yang **identik** untuk
`DataScope.SUBORDINATE_DATA` miliknya sendiri. Kalau disalin-tempel, ada risiko nyata dua
definisi "bawahan" perlahan menyimpang (satu diperbaiki, satu lupa) — dan penyimpangan di
soal siapa-melihat-data-siapa adalah kebocoran data, bukan bug kosmetik. Refactor ini
*behaviour-preserving*: `OrgChartVisibilityTest` yang sudah ada tetap lolos tanpa diubah.

### 3.7 Kenapa `null` = tolak di `CrmAccessGuard`, padahal `OrgChartAccessGuard` `null` = izinkan?

```kotlin
// CrmAccessGuard.kt
if (principal == null) {
    val none = ModuleAccessConfig()
    return AccessDecision(config = none, source = AccessSource.NONE, ...)
}
```

`OrgChartAccessGuard` yang lama sengaja memperlakukan "wewenang tak terhitung" sebagai
"izinkan", karena ada rute lama yang sudah jalan sebelum guard itu ada dan tidak boleh
tiba-tiba mati. **Rute CRM sama sekali baru** — tidak ada rute lama yang perlu dijaga
kompatibel — jadi tidak ada alasan untuk membuka akses secara default. Ini adalah satu
penyimpangan yang disengaja dan didokumentasikan, bukan lupa menyalin pola lama.

### 3.8 Kenapa grid spreadsheet (ala monday.com) ditolak, dipilih master-detail?

Dihitung dulu: outline 3dp + hard shadow 6dp per sel clay ≈ 18dp chrome. Grid 12 kolom
× 30 baris = 360 sel × (reservasi bayangan + 2 `drawBehind` + animasi) — tidak muat dan
tidak nyaman di HP. **Master-detail menghapus masalah itu, bukan menyelesaikannya**: daftar
380dp + panel detail di desktop (≥840dp), kartu vertikal + `ModalBottomSheet` di HP.
Breakpoint-nya ditaruh di `ClayBreakpoints.MasterDetail` (token, bukan angka telanjang)
supaya modul berikutnya yang butuh adaptivitas mewarisi nilai yang sama.

---

## 4. Jebakan Umum yang Dihindari di Sini

1. **Menyimpan angka sebagai string dalam JSONB** — akan kehilangan presisi rupiah kalau
   di-parse ulang lewat `Double`. `JsonValue.Num(raw)` menyimpan literal aslinya persis.
2. **Mengunci taksonomi tenant ke enum Kotlin** — `ClothingCategory` (Kaos, Polo, dst)
   sengaja **bukan** enum core, melainkan custom field `SingleSelect` yang di-seed. Tiap
   konveksi punya kategori sendiri; menguncinya di enum bertentangan dengan seluruh alasan
   mesin custom field ini dibangun.
3. **Escaper JSON tulisan tangan** — `EmployeeDto.escape` di kode lama membuang `\r` dan
   membiarkan karakter kontrol lain tanpa escape. `CrmLeadCodec` dibangun di atas
   `JsonValue.Str` yang otomatis lewat `JsonWriter.appendQuoted` yang benar RFC 8259.
4. **Scope `nullable`-berdefault** — `ListLeadsUseCase` menerima `DataScope` sebagai
   parameter wajib tanpa default. Scope yang bisa "lupa diisi" adalah cara paling umum
   sebuah kebocoran data terjadi tanpa disadari.

---

## 5. Verifikasi

```bash
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.crm.*" \
                         --tests "com.eventverse.app.domain.customfield.*"
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain
./gradlew :server:compileKotlin
```

**Catatan penting saat penulisan modul ini**: repo memiliki berkas lain yang sedang
dikerjakan pihak/proses lain secara bersamaan (`core/.../domain/sampling/`,
`core/.../shared/sampling/`, `SamplingTables.kt`, migrasi `V23`) yang tidak berelasi
dengan CRM Leads dan saat ini tidak kompil. Berkas-berkas itu **tidak disentuh** oleh
pekerjaan ini; jalankan ulang build penuh setelah pekerjaan tersebut selesai/stabil.

---

## 6. Yang Sengaja Belum Dibangun (Fase 1.5+)

- `ChangeFieldTypeUseCase` (apply, bukan cuma preview) dan route-nya.
- Editor opsi `SingleSelect` (tambah/rename/arsip pilihan) — field select yang di-seed
  V22 sudah bisa dipakai, belum bisa diedit lewat UI.
- Kanban pipeline per `LeadStage`, `SamplingOrder`, log aktivitas follow-up.
- `custom_field_links` untuk custom field bertipe `UserRef` (owner core sudah lewat FK asli).
