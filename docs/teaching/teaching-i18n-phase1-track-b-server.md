# Teaching — i18n Phase 1, Track B (Server)

**Task**: Multibahasa Fase 1 — Track B (Server): migrasi V103, endpoint preferensi bahasa,
kode error stabil, label cetak id/en, prompt Koog berbahasa.
**Plan**: [`PLAN-i18n-phase1-ui-system.md`](../plannings/features/PLAN-i18n-phase1-ui-system.md) §4
**TRD**: [`TRD-I18N-001`](../trd/features/TRD-I18N-001-multibahasa-ui-dan-label-sistem.md)
**Tanggal**: 2026-10-13

---

## 0. Cara membaca dokumen ini

Track B adalah pekerjaan **server**: menyimpan preferensi bahasa, mengembalikannya dalam bentuk yang
mesin bisa andalkan (`code` stabil), dan membuat dokumen cetak + prompt agent mengikuti bahasa.
Kita berjalan dari lapisan paling dalam (data) ke paling luar (rute HTTP), karena setiap lapisan
hanya boleh bergantung pada lapisan di bawahnya — aturan Dependency Rule yang sama dengan DDD proyek.

Urutan bagian sengaja mencerminkan urutan kita menyentuh kode:

```
1. Domain (core)  → contract: LanguageCode (sudah ada), LanguagePreferenceRepository, ServerErrorCode, PrintLabelTable
2. Data (server)  → migrasi V103 + kolom Exposed + Postgres/InMemory repository
3. Rute (server)  → PUT /api/me/language, PUT /api/tenant/language (fail-closed)
4. Rute error     → kode stabil pada Fulfillment (B3)
5. Cetak          → renderer PDF menerima LanguageCode (B4)
6. Agent          → prompt Koog menerima bahasa keluaran (B5)
7. Tes            → 403/400/RLS tak berubah (B6)
```

---

## Step 1 — Domain: kontrak dulu, bukan implementasi

### 1.1 `LanguageCode` (sudah ada dari Track 0)

```kotlin
@JvmInline value class LanguageCode(val code: String) {
    init { require(code == "id" || code == "en") }
    companion object {
        fun parse(value: String?): LanguageCode?   // null = tak dikenal, TIDAK melempar
        fun parseOrThrow(value: String): LanguageCode
    }
}
```

Mental model: `LanguageCode` adalah **value object** — identitasnya adalah nilainya. Karena
invariant-nya dijaga di constructor, seluruh sistem boleh mengasumsikan "kalau ada `LanguageCode`,
isinya pasti `id` atau `en`". Inilah sebabnya kita tidak perlu memvalidasi ulang di setiap rute.

Perhatikan `parse` mengembalikan `null` untuk nilai tak dikenal (bukan exception, bukan fallback ke
`id`). Itu keputusan sadar: **pemanggil** yang memutuskan apa arti "tak dikenal" — 400 di endpoint
preferensi, atau jatuh ke `id` di halaman cetak. Library tidak boleh menebak kebijakan.

### 1.2 `LanguagePreferenceRepository`

```kotlin
interface LanguagePreferenceRepository {
    suspend fun findUserLanguage(userId: UserId, tenantId: TenantId?): LanguageCode?
    suspend fun setUserLanguage(userId: UserId, language: LanguageCode, tenantId: TenantId?)
    suspend fun findTenantLanguage(tenantId: TenantId): LanguageCode?
    suspend fun setTenantLanguage(tenantId: TenantId, language: LanguageCode)
}
```

Dua tingkat mengikuti tangga **pengguna menang atas tenant**. Kenapa `tenantId` ikut dikirim padahal
`userId` sudah unik?

> Karena tabel `users` ber-Row-Level-Security. Tanpa `SET LOCAL app.current_tenant_id`, UPDATE-nya
> menyentuh nol baris — **diam-diam berhasil tanpa mengubah apa pun**. Ini jebakan klasik RLS:
> kegagalan tampak seperti sukses. `tenantId = null` berarti pemanggil platform (superadmin tanpa
> workspace); implementasi memakai koneksi owner yang memang menembus RLS.

**Pelajaran**: tanda tangan repository bukan sekadar daftar data yang dibutuhkan; ia menyatakan
*konteks keamanan* operasi itu dijalankan.

### 1.3 `ServerErrorCode` — satu sumber kebenaran untuk kode error

Klien perlu menerjemahkan error server ke teks. Kalau server menulis `"FORBIDDEN"` dan klien menulis
ulang string yang sama, keduanya pasti akan berbeda pendapat suatu hari. Kita taruh kosakata di
`core` (yang bisa dibaca server **dan** klien), lalu klien (`presentation/i18n/ServerErrorCodes`)
hanya menjadi alias:

```kotlin
object ServerErrorCodes {                       // app/shared (klien)
    const val FORBIDDEN = ServerErrorCode.FORBIDDEN   // core (kanonik)
}
```

**Aturan**: `code` adalah kontrak mesin dan tidak boleh berubah tanpa migrasi klien; `message`
manusia boleh berubah kapan saja.

### 1.4 `PrintLabelTable` — terjemahan sebagai data

Dulu judul kolom PDF adalah literal Indonesia di dalam renderer. Kita ubah menjadi kunci tertutup:

```kotlin
enum class PrintLabelKey { SPK_POM_TITLE, KNIT_TOTAL, WL_OPERATOR, … }

object PrintLabelTable {
    fun label(key: PrintLabelKey, language: LanguageCode): String
    fun isTranslated(key: PrintLabelKey): Boolean   // dipakai tes paritas
}
```

Kenapa `enum` + `Map`, bukan `when` besar? Karena **kompilator** lalu menangkap kunci yang lupa
ditambahkan, dan tes paritas bisa mengiterasi `entries` — persis pola Kontrak 7 variabilitas.

`isTranslated` ada supaya tes bisa membuktikan padanan `en` **eksplisit** dan tidak diam-diam jatuh
ke `id`. Tanpa itu, label yang kebetulan sama ("REV" = "REV") akan lolos tes tanpa terjemahan.

---

## Step 2 — Data: migrasi aditif + kolom Exposed

### 2.1 Migrasi `V103`

```sql
ALTER TABLE users  ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(8) NOT NULL DEFAULT 'id';
ALTER TABLE users  ADD CONSTRAINT users_preferred_language_check CHECK (preferred_language IN ('id','en'));
-- idem untuk tenants.default_language
```

Tiga prinsip yang terlihat di sini:

1. **Aditif + idempoten** (`IF NOT EXISTS`, `DROP CONSTRAINT IF EXISTS`) — aman dijalankan di DB yang
   sudah punya kolom.
2. **Bawaan `'id'` = paritas.** Tenant/pengguna lama berperilaku persis seperti sebelum kolom ada.
   Ini menerapkan gagasan Strangler Fig: struktur baru hidup di samping perilaku lama, tes paritas
   (di sini "bawaan id") membuktikan tak ada yang berubah.
3. **CHECK di DB = net terakhir.** `LanguageCode` menolak di kode, tetapi data bisa masuk lewat jalur
   lain (SQL manual, migrasi keliru). CHECK membuat nilai rusak mustahil secara struktural.

**Yang sengaja TIDAK dilakukan**: tidak menyentuh RLS. Preferensi bahasa adalah perubahan kosmetik;
kebijakan keamanan tidak boleh ikut berubah. Itu diuji sebagai kontrak (`I18nMigrationTest`).

### 2.2 Kolom Exposed

```kotlin
val preferredLanguage = varchar("preferred_language", 8).default("id")
```

Exposed `selectAll()` akan menyertakan kolom baru; mapper `User` mengabaikannya. Konsekuensinya:
**migrasi harus jalan sebelum query apa pun**, dan Flyway memastikan itu di `DatabaseFactory.init()`.

### 2.3 Repository Postgres vs InMemory

`PostgresLanguagePreferenceRepository` memakai `DatabaseFactory.dbQuery(tenantId)` (RLS) dan
`LanguageCode.parse` untuk membaca (nilai tak dikenal → `null`, bukan ditebak).
`InMemoryLanguagePreferenceRepository` menirukan semantik yang sama untuk tes tanpa seed DB.

**Pelajaran**: dua implementasi, satu perilaku. Tes yang memakai InMemory membuktikan logika rute;
tes integrasi (booting `module()` → Flyway) membuktikan jalur Postgres benar-benar terpasang.

---

## Step 3 — Rute: `PUT /api/me/language` & `PUT /api/tenant/language`

### 3.1 Dua tingkat, dua gerbang

| Endpoint | Siapa yang boleh | Kenapa |
|---|---|---|
| `PUT /api/me/language` | setiap pemakai terautentikasi | yang diubah adalah preferensi **dirinya** |
| `PUT /api/tenant/language` | `MANAGE` atas modul tata kelola (RBAC) | yang diubah adalah **bawaan pabrik** |

### 3.2 Fail-closed, bukan fail-open

```kotlin
val decision = call.moduleDecision(GarmentModules.DYNAMIC_RBAC, tenant, roleRepository, moduleAssignmentRepository)
if (!decision.config.level.isAtLeast(AccessLevel.MANAGE)) {
    return@put call.respondError(HttpStatusCode.Forbidden, ServerErrorCode.FORBIDDEN, "…")
}
```

`moduleDecision` **selalu** mengembalikan keputusan (tanpa jabatan yang memberi akses → `NONE`),
sehingga "tidak bisa dihitung" tidak pernah berarti "izinkan". Ini pelajaran yang lebih tua dari
fitur ini (lihat `tenant-variability-rules` Kontrak 7): pencarian wewenang yang gagal harus menutup,
bukan membuka.

### 3.3 Validasi bahasa = jalan pintas 400

```kotlin
val raw = body.string("language")
if (raw.isNullOrBlank()) → 400 MISSING_LANGUAGE
LanguageCode.parse(raw) ?: → 400 UNKNOWN_LANGUAGE
```

`"fr"` ditolak **sebelum** menyentuh DB. Ini bukan hanya soal pesan yang bagus: menolak lebih awal
berarti tidak ada penulisan yang perlu di-*rollback*, dan tidak ada perubahan data yang tak sengaja.

**Jebakan yang dihindari**: memakai `requireModuleAccess` untuk 403 akan menghasilkan respons teks
biasa tanpa `code`. Untuk rute yang dimigrasi ke kode stabil, jawab 403 sendiri lewat
`respondError(..., FORBIDDEN, ...)`.

---

## Step 4 — B3: kode error stabil di rute Fulfillment

`respondError` memusatkan bentuk respons:

```json
{ "code": "VALIDATION_ERROR", "message": "Referensi produksi wajib diisi" }
```

Keuntungannya konkret: klien (Track C4) bisa `when (code)` alih-alih mencocokkan teks Indonesia.
Semua rute Fulfillment yang kita migrasikan kini memakainya.

**Jebakan yang ditemukan saat tes**: sebagian 403 ternyata dihasilkan **gerbang grup**
(`tenantRouteGate`), bukan handler. Gerbang itu masih menjawab teks biasa. Untuk menguji kode stabil,
tes harus memilih kondisi yang memang dijawab handler (mis. reopen butuh MANAGE — grup hanya butuh
OPERATE), bukan kondisi yang dicegat gerbang grup lebih dulu.

---

## Step 5 — B4: dokumen cetak mengikuti bahasa

Renderer menerima bahasa lewat parameter dengan **bawaan `id`**:

```kotlin
fun render(sheet: SpkCardSheet, language: LanguageCode = LanguageCode.ID): ByteArray
```

Kenapa parameter, bukan konstruktor? Renderer dirakit **sekali** saat wiring rute
(`TraceabilityPrintRoutes`). Menaruh bahasa di konstruktor berarti membuat ulang renderer per
permintaan. Parameter `render(...)` lebih kecil dan membuat setiap pemanggil eksplisit soal bahasa.

Rute membaca `?lang=` dan jatuh ke `id` bila tak dikenal — **bukan** 400, karena halaman PDF dibuka
tab browser (kerap lewat tiket) dan kegagalan rendering lebih merugikan daripada dokumen berbahasa
bawaan. Kontrak penolakan 400 tetap milik endpoint preferensi.

**Pelajaran**: "tolak, jangan fallback senyap" berlaku pada *penyimpanan*; pada *presentasi
best-effort* seperti cetak, jatuh ke bawaan adalah pilihan yang sengaja dan wajib ditulis alasannya.

---

## Step 6 — B5: prompt Koog menerima bahasa keluaran

`KoogDiscoveryPrompt.system` dulu `val`. Kita ubah menjadi fungsi berbahasa, dengan properti lama
dipertahankan:

```kotlin
val system: String get() = system(LanguageCode.ID)
fun system(language: LanguageCode): String = buildString {
    appendLine(languageDirective(language))   // "BAHASA KELUARAN: …" / "OUTPUT LANGUAGE: English"
    append(BASE_SYSTEM)
}
```

Direktif ditempel **di depan** agar dibaca model lebih dulu. Struktur JSON, kunci, dan kosakata
tertutup **tidak** berubah — bahasa hanya mengubah teks yang dilihat pengguna.

Bahasa mengalir dari HTTP lewat domain: `DiscoveryRequest(language = …)` → `KoogDiscoveryAgent`
memakai `request.language` saat membangun `systemPrompt`. Menambah field **berdefault** ke
`DiscoveryRequest` menjaga setiap pemanggil lama tetap kompilasi — itulah cara memperluas kontrak
tanpa memecahnya.

**Evaluasi deterministik**: tes tidak memanggil LLM. Ia menilai **string prompt** — penanda yang
wajib ada per bahasa dan penanda bahasa lain yang wajib absen. Murah, cepat, dan menangkap regresi
"bahasa keluaran hilang".

Anggaran panjang prompt dinaikkan 8.000 → 8.600 karakter karena direktif bahasa memang menambah
±330 karakter. Mengubah angka tes dibenarkan **hanya** ketika kebutuhan bertambah dan alasan
tertulis — bukan untuk membuat merah jadi hijau.

---

## Step 7 — B6: tes sebagai gerbang

| Tes | Membuktikan |
|---|---|
| `LanguageRoutesTest` | 200 menulis & menyimpan; 400 `UNKNOWN_LANGUAGE`/`MISSING_LANGUAGE`; 403 peran tak berwenang |
| `FulfillmentErrorCodeTest` | 403/400 rute Fulfillment membawa `code` stabil |
| `I18nMigrationTest` | V103 aditif, CHECK id/en, **tidak menyentuh RLS** |
| `PrintLabelTableTest` (core) | setiap `PrintLabelKey` punya padanan `en`; string `id` identik literal lama |
| `KoogDiscoveryPromptLanguageTest` | prompt id/en deterministik + struktur tetap |
| `J3MigrationFenceTest` | pagar migrasi J3 tetap hijau |

---

## 8. Jebakan umum (yang sudah kita hindari)

1. **RLS senyap**: UPDATE tanpa `SET LOCAL app.current_tenant_id` "berhasil" tanpa mengubah baris.
2. **403 dari gerbang grup ≠ 403 dari handler**: menguji kode stabil harus memilih kondisi yang
   dijawab handler.
3. **Kode error ganda**: dua definisi string (`server` & `klien`) pasti akan menyimpang; jadikan
   `core` kanonik dan aliasi sisanya.
4. **Fallback senyap ke `id`**: benar untuk cetak, salah untuk penyimpanan preferensi.
5. **Menumbuhkan file di atas hard limit**: `FulfillmentPackingRoutes.kt` tetap di bawah 500 baris
   (469 → 470); perhatikan Aturan Ratchet.
6. **Menambah route tenant tanpa pemilik**: `RouteOwnershipTest` menolak `/api/tenant/language` yang
   belum terdaftar di `RouteOwnership`.

## 9. Status verifikasi (2026-10-13)

- `:core:jvmTest` — hijau.
- `:server:test` terarah (LanguageRoutesTest, FulfillmentErrorCodeTest, I18nMigrationTest,
  KoogDiscoveryPromptLanguageTest, KoogDiscoveryPromptTest, J3MigrationFenceTest, RouteOwnershipTest,
  RouteGateTest) — hijau.
- Tes discovery/PDF/gate yang terdampak (229 tes dijalankan) — hijau kecuali
  `FulfillmentPackingRoutesTest` **yang gagal karena sebab yang sudah ada sebelum Track B**:
  tes itu menyemai karton/shipment ke Postgres dengan `tenant_id` yang hanya ada di
  `InMemoryTenantRepository` (FK `cartons_tenant_id_fk` langgar) dan memakai slug > 30 karakter
  (`TenantSlug must be between 3 and 30 characters`). Keduanya tak berkaitan dengan i18n.
- `:app:shared:compileKotlinJvm` — hijau (alias `ServerErrorCodes` → core valid).
