# Teaching — Contact & Deal: Kualifikasi CRM Menjadi Transaksi

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD aggregate design, transaksi lintas-agregat, idempotency, S3/MinIO port-adapter, Ktor raw-body upload, Compose Multiplatform expect/actual
> **Prasyarat**: Paham struktur modul `core/` vs `server/` vs `app/shared/`, pola repository tenant-scoped, dan MVI di Compose

---

## 0. Konteks Bisnis (Kenapa Fitur Ini Ada)

Sebelumnya, alur CRM "berhenti" di label: lead dipindah ke QUALIFIED, lalu sales dilempar
ke modul Invoicing lewat `InvoicePrefillCoordinator` (in-memory, client-side, gampang hilang).
Tidak ada objek bisnis yang menyimpan "pesanan ini sedang berjalan". Tiga masalah nyata:

1. **Data kontak tidak reusable** — tercampur di dalam lead; satu brand dengan 3 leads = 3 kontak berbeda.
2. **Tidak ada wadah PO** — PO klien (fondasi produksi!) tidak punya rumah.
3. **Invoice menggantung di lead** — padahal secara bisnis yang ditagih adalah *pesanan*, bukan *prospek*.

Solusinya: dua agregat baru — **Contact** (master data pelanggan) dan **Deal** (transaksi) —
plus **PurchaseOrder** sebagai child Deal.

---

## 1. Start dari Mana? (Urutan Menulis dari Nol)

Kalau kamu menulis fitur ini dari kosong, urutannya WAJIB begini — dari dalam ke luar:

```
1. core/ domain      → Contact, Deal, PurchaseOrder, repository interfaces, QualifyLeadUseCase
2. core/ shared      → DealCodec (kontrak wire format)
3. server/ migration → V34 (tabel + RLS)                     ← butuh #1 sebagai acuan kolom
4. server/ infra     → Exposed tables, Postgres repos, S3 adapter
5. server/ routes    → DealRoutes + modifikasi CrmRoutes     ← wrapper transaksi hidup DI SINI
6. app/shared data   → DealApiClient, updateStageWithDeal
7. app/shared UI     → DealViewModel + DealDetailDialog
8. tests             → domain dulu (paling murah), server, VM
```

Jebakan umum: menulis UI dulu lalu "menggenahkan" domain-nya. Domain yang benar membuat
UI-nya mudah; sebaliknya hampir selalu berujung refaktor.

---

## 2. Bedah Kode Blok per Blok

### 2.1 Aturan Transaksi — Pertanyaan yang Jadi Alasan Fitur Ini

> "CRM yang qualified otomatis membuat contact dan deal — tapi tidak ada wrapper transaksi?"

**Keputusan arsitekturnya**: transaksi TIDAK ditaruh di domain. `core/` harus tetap zero-dependency
(tidak ada Exposed/Ktor di sana). Sebagai gantinya:

```kotlin
// CrmRoutes.kt — post("/stage")
if (newStage == LeadStage.QUALIFIED) {
    DatabaseFactory.dbQuery(tenant.tenantId) {          // ← SATU transaksi
        qualifyLeadUseCase(tenant.tenantId, leadId, newId = { newId("deal") })
    }.onSuccess { ... }
}
```

Mental model: `dbQuery` membuka transaksi tenant-scoped (dengan RLS `SET LOCAL`), dan setiap
repository di dalamnya memanggil `DatabaseFactory.dbQuery(...)` lagi — Exposed **menyambung**
panggilan bersarang ke transaksi luar, bukan membuat transaksi baru. Jadi lead + contact + deal
commit atau gagal bersama, tanpa domain tahu-tahu soal transaksi.

**Idempotency = pengaman kedua.** Lihat `QualifyLeadUseCase`:

```kotlin
val existingDeal = dealRepository.findBySourceLeadId(tenantId, leadId)
if (existingDeal != null) { ... return Qualification(..., dealAlreadyExisted = true) }
```

Kalau transaksi commit tapi klien timeout lalu retry, panggilan kedua TIDAK membuat duplikat —
ia mengembalikan pasangan yang sudah ada. Unique partial index di V34
(`uq_deals_tenant_source_lead`) adalah pengaman ketiga di level database.

### 2.2 Contact: Find-or-Create by Phone

```kotlin
private suspend fun findOrCreateContact(...): Contact {
    val phone = lead.whatsappNumber
    if (phone != null) {
        contactRepository.findByPhone(tenantId, phone)?.let { return it }
    }
    ... // buat baru
}
```

Satu nomor telepon = satu pelanggan. Lead berbeda dari brand yang sama menempel ke Contact
yang sama, tapi Deal-nya tetap berbeda. Ini dijamin index unik `uq_contacts_tenant_phone`.

### 2.3 PO: Dua Asal, Satu Invariant

`PurchaseOrder` punya `PoOrigin.UPLOADED | MANUAL`, dan invariants di `init`:

```kotlin
require(origin != PoOrigin.UPLOADED || !storageKey.isNullOrBlank())
require(origin != PoOrigin.MANUAL || lines.isNotEmpty())
```

Upload: bytes masuk ke **S3/MinIO** lewat port `PoFileStorage` (domain cuma kenal interface),
database hanya menyimpan metadata + `storage_key`. Manual: admin ketik nomor + baris item
(JSONB `manual_lines`).

Bonus invariant: deal yang masih `OPEN` otomatis naik ke `PO_RECEIVED` saat PO pertama
ditempel — ada PO berarti pesanan terkonfirmasi.

### 2.4 Upload Tanpa Multipart

Endpoint upload sengaja **tidak** memakai multipart: metadata lewat query string, bytes
sebagai raw body (`call.receive<ByteArray>()`). Satu PUT, tanpa plugin tambahan, dan client
cukup `setBody(bytes)`. Multipart bagus untuk form campuran; di sini form-nya cuma 3 string.

### 2.5 Invoicing Pindah ke Deal

`InvoiceSourceKind` bertambah `DEAL`, dan prefill punya satu penerjemah baru:
`InvoicePrefillData.fromDeal(deal, contact)` dengan `sourceRef = dealId`. Tombol Generate
Invoice di `LeadInspectorPane` diganti "Buka Deal" — penagihan kini lahir dari konteks
pesanan, bukan prospek.
### 2.6 Demosi Bersyarat: QUALIFIED Balik ke NEW_LEAD

`DemoteQualifiedLeadUseCase` menegakkan aturan: lead QUALIFIED hanya boleh balik ke
NEW_LEAD bila deal-nya **masih OPEN tanpa PO dan tanpa invoice aktif** — dan bila lolos,
deal ikut diarsipkan (event `DealArchivedByLeadDemotion`). Bila deal sudah punya PO,
invoice terbit (ISSUED/PARTIALLY_PAID/PAID — draft dan void diabaikan), atau stage-nya
sudah lewat OPEN, permintaan **ditolak dengan HTTP 409** berisi alasan eksplisit.
Pengecekan invoice lewat `InvoiceRepository.hasActiveInvoiceForSource` — method baru yang
dibuat khusus supaya use case tidak perlu memuat seluruh faktur hanya untuk satu pertanyaan ya/tidak.

### 2.7 Siklus Hidup Contact Saat Demosi (Anti-Kontak-Sampah)

Skenario yang dilindungi: **admin tidak sengaja** menarik lead ke QUALIFIED (contact + deal
tercipta), lalu menariknya balik ke NEW_LEAD. Aturannya dua sisi:

1. **Contact dibuat oleh kualifikasi lead INI** (`contact.sourceLeadId == leadId`)
   DAN tidak ada deal lain — arsip termasuk, karena FK-nya tetap menunjuk — yang
   memakainya → contact **dihapus bersih** (`ContactRepository.delete`). Ini simetri
   find-or-create: yang kita ciptakan karena keliru, kita bersihkan.
2. **Contact milik pihak lain** — dibuat oleh order pertama (`sourceLeadId` = lead lain)
   atau manual (`null`), atau masih ditunjuk deal lain (`DealRepository.existsForContact`
   dengan `excludeDealId`) → **tidak pernah disentuh**. Order kedua memakai contact yang
   sama tidak mungkin menghapusnya.

Dan bila contact sudah tidak ada sama sekali (dihapus manual), demosi **tidak crash** —
`findById` mengembalikan null dan cleanup dilewati. Pengecekan referensi dilakukan
*sebelum* delete karena `deals.contact_id` ber-ON DELETE CASCADE: menghapus contact
begitu saja akan ikut menghancurkan riwayat deal.

---

## 3. Technology & Approach ("The Why")

| Keputusan | Alternatif yang ditolak | Risiko yang dihindari |
|---|---|---|
| Transaksi di route (`dbQuery` pembungkus use case) | `UnitOfWork` port di domain | Menyelundupkan konsep infrastruktur ke `core/` (melanggar dependency rule) |
| Idempotency by `sourceLeadId` + unique index | Mengandalkan transaksi semata | Retry klien → duplikat deal; index unik = jaring pengaman di level DB |
| S3/MinIO via port `PoFileStorage` | Simpan bytea di Postgres | DB membengkak, backup lambat, tidak bisa presigned URL |
| Raw-body upload | Multipart | Plugin ekstra, parsing 2 arah, dan klien KMP harus susun multipart per platform |
| Deal tetap di `BusinessModule.CRM_SALES` | BusinessModule baru | Menyentuh kuota entitlement/pipeline + matriks RBAC baru tanpa nilai bisnis |
| `InvoicePrefillData.fromDeal` satu penerjemah | Prefill tersebar di pemanggil | Preview kanvas ≠ dokumen terbit (kesalahan yang pernah terjadi — lihat teaching palet invoice) |

---

## 4. Jebakan Pemula (Common Pitfalls)

1. **Menaruh transaksi di dalam use case domain.** `core/` tidak boleh import Exposed/Ktor.
   Transaksi hidup di route (infrastruktur). Kalau kamu merasa butuh `transaction {}` di
   `core/`, tata letaknya salah.
2. **Lupa `SET LOCAL` RLS saat menambah tabel baru.** Tanpa `SELECT apply_tenant_rls('deals')`,
   satu tenant bisa membaca deal tenant lain begitu `DB_APP_USER` aktif. Setiap tabel baru
   WAJIB dipasangkan dengan RLS di migrasinya.
3. **Menyimpan presigned URL ke database.** URL S3 kedaluwarsa (15 menit di adapter ini).
   Simpan `storage_key`; buat URL on-demand saat download.
4. **`expect/actual` tanpa actual semua target.** Satu platform terlewat = build gagal di
   target itu saja (sering tidak ketahuan di mesin dev macOS yang hanya menjalankan JVM).
   Picker PO ini punya 5 actual: jvm (FileDialog), android/ios (null yang jujur), js/wasmJs (null).
5. **Duplikasi find-or-create tanpa index unik.** `findByPhone` lalu `save` adalah race condition
   klasik di dua request bersamaan. Unique partial index membuatnya aman secara final.
6. **Menghapus tombol invoice lama tanpa jalur baru.** Invoicing "dipindah", bukan "dihapus" —
   pastikan jalur baru (Buka Deal → PO & Invoice) sudah hidup sebelum mematikan jalur lama.

---

## 5. Verifikasi & Tantangan Mandiri

Sudah diverifikasi pada implementasi ini:

```bash
# 1. Domain test (termasuk idempotency)
./gradlew :core:jvmTest --tests 'com.eventverse.app.domain.deal.usecases.QualifyLeadUseCaseTest'

# 2. Lima target kompilasi + test presentasi (Definition of Done)
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :app:shared:jvmTest

# 3. Server test (butuh DB: DB_PORT=5435 DB_PASSWORD=postgres)
./gradlew :server:test --rerun-tasks

# 4. Migrasi benar-benar terapply:
docker exec wemade-postgres psql -U postgres -d wemade_erp \
  -c "SELECT version, success FROM flyway_schema_history WHERE version='34';"
```

**Tantangan mandiri** (naikkan levelmu):

1. Tambahkan endpoint `GET /api/tenant/contacts` + pencarian by nama, lalu satu test API.
2. Buat seksi "Invoices" di `DealDetailDialog` yang membaca invoice dengan
   `sourceKind=DEAL, sourceRef=dealId` (endpoint invoicing sudah menyimpan `source_reference_id`).
3. Implementasikan picker Android sebenarnya (`ActivityResultContracts.GetContent`) dengan
   akses Activity via `LocalContext` di lapisan Compose, lalu tolak berkas >10 MB di sisi klien.
4. Pikirkan: apa yang harus terjadi pada Deal ketika invoice terakhirnya LUNAS? Stage apa yang
   tepat, dan event domain apa yang harus diterbitkan?

