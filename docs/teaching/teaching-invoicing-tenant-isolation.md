# 🎓 Modul Pembelajaran: Menutup Kebocoran Lintas-Tenant di Modul Faktur

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Multi-tenancy, PostgreSQL Row-Level Security, desain tanda tangan API, test regresi keamanan
> **Prasyarat**: Paham dasar Ktor routing, Exposed, dan apa itu tenant di WeMade ERP
> **Referensi Task**: Perbaikan isolasi tenant pada `server/.../routes/InvoicingRoutes.kt`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Faktur adalah dokumen paling sensitif di sebuah ERP konveksi: ia memuat nama klien, nominal
kontrak, dan nomor rekening pabrik. Di WeMade ERP, satu instance melayani banyak pabrik sekaligus.
Jadi pertanyaan yang menentukan aman atau tidaknya sistem ini sederhana:

> Kalau pabrik A mengetahui ID faktur milik pabrik B, apa yang menghalanginya membaca faktur itu?

Jawaban sebelum perbaikan ini: **tidak ada apa-apa.** Dan bukan hanya membaca — ia juga bisa
menerbitkannya, membatalkannya, mencatat pembayaran palsu di atasnya, dan mengarsipkan template
milik pabrik lain.

### Bagaimana bisa lolos sejauh ini

Ini bagian yang paling layak dipelajari, karena tidak ada satu pun orang yang ceroboh di sini.
Ada **tiga lapis pertahanan yang semuanya gagal secara bersamaan**, dan masing-masing gagal karena
alasan yang kelihatan masuk akal saat ditulis.

**Lapis 1 — pemeriksaan manual di rute.** Rute `GET /{id}` punya ini:

```kotlin
val invoice = invoiceRepository.findById(InvoiceId(id))
    ?: return@get call.respond(HttpStatusCode.NotFound, "Invoice tidak ditemukan")

if (invoice.tenantId != tenant.tenantId) {
    return@get call.respond(HttpStatusCode.Forbidden, "Access forbidden")
}
```

Benar, jelas, dan berfungsi. Masalahnya: pemeriksaan ini adalah **sesuatu yang harus diingat**.
Dari delapan rute ber-`{id}` di modul faktur, hanya satu yang mengingatnya. Tujuh sisanya —
`PUT /{id}`, `/issue`, `/void`, `/payments`, `/create-settlement`, `/pdf`, dan dua rute template —
tidak.

**Lapis 2 — koneksi database yang sadar tenant.** `DatabaseFactory.dbQuery` punya desain yang bagus:

```kotlin
suspend fun <T> dbQuery(tenantId: TenantId? = null, block: suspend () -> T): T {
    val database = if (tenantId != null) appDatabase ?: platformDatabase else platformDatabase
    return newSuspendedTransaction(Dispatchers.IO, db = database) {
        if (tenantId != null) {
            exec("SET LOCAL app.current_tenant_id = '${tenantId.value}';")
        }
        block()
    }
}
```

Beri `tenantId` → koneksi aplikasi + variabel sesi RLS terpasang. Jangan beri → koneksi platform.
Idenya benar. Tapi `InvoiceRepository.findById(id)` **tidak menerima `tenantId`**, jadi ia memanggil
`dbQuery { }` tanpa argumen — dan otomatis jatuh ke jalur platform tanpa `app.current_tenant_id`.

**Lapis 3 — Row-Level Security PostgreSQL.** Setiap tabel memanggil `apply_tenant_rls()`:

```sql
ALTER TABLE %I ENABLE ROW LEVEL SECURITY;
CREATE POLICY ... USING (
    current_setting('app.current_user_role', true) = 'PLATFORM_SUPERADMIN'
    OR tenant_id = current_setting('app.current_tenant_id', true)
);
```

Kelihatan seperti jaring pengaman terakhir. Tapi ada satu kata yang tidak ada di sana:
**`FORCE`**. Saya grep seluruh direktori migrasi — `FORCE ROW LEVEL SECURITY` tidak muncul satu kali
pun.

> 🔑 **Fakta PostgreSQL yang wajib kamu ingat seumur hidup:**
> `ENABLE ROW LEVEL SECURITY` **tidak berlaku bagi pemilik tabel.** Pemilik tabel selalu melewati
> RLS kecuali kamu menambahkan `FORCE ROW LEVEL SECURITY`. Dan migrasi Flyway dijalankan oleh role
> owner — jadi role itu adalah pemilik setiap tabel yang dibuatnya.

Tiga lapis, tiga kegagalan, satu akibat.

### Analogi Sederhana

Bayangkan gedung apartemen:
- **Lapis 1** = satpam yang memeriksa KTP. Ia memang ada, tapi hanya berjaga di satu dari delapan pintu.
- **Lapis 2** = kartu akses lift. Sistemnya bagus, tapi tamu yang tidak diberi kartu justru diarahkan
  ke lift servis yang tidak butuh kartu sama sekali.
- **Lapis 3** = kunci pintu tiap unit. Terpasang — tapi pemilik gedung punya kunci master, dan
  aplikasi kita **berjalan sebagai pemilik gedung**.

### Hasil Akhir yang Diharapkan

Tenant bukan lagi sesuatu yang harus diingat, melainkan **sesuatu yang tidak bisa dilupakan**:
kompiler menolak kode yang tidak menyebutkannya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

### Langkah 0: Buktikan dulu bahwa lubangnya nyata

Sebelum mengetik perbaikan apa pun, telusuri rantainya sampai tuntas dan pastikan setiap mata
rantainya benar. Saya memeriksa empat hal:

1. Berapa rute yang punya pemeriksaan tenant? (`grep` → satu dari delapan)
2. Apakah `findById` membawa tenant? (tidak)
3. Ke koneksi mana `dbQuery` tanpa tenant pergi? (platform, tanpa `SET LOCAL`)
4. Apakah RLS menutupnya? (tidak — tidak ada `FORCE`)

**Kenapa ini langkah nol?** Karena "sepertinya bocor" dan "bocor" memerlukan reaksi yang berbeda.
Kalau ternyata RLS *menutupnya*, ini hanya lapisan pertahanan yang hilang — penting, tapi tidak
mendesak. Karena ternyata **tidak**, ini kerentanan aktif, dan urutan kerja seluruh hari berubah.

### Langkah 1: Ubah kontrak lebih dulu, biarkan kompiler jadi daftar tugas

Jangan mulai dari rute. Mulai dari `core/.../InvoiceRepository.kt`. Tambahkan `tenantId` ke setiap
operasi per-ID, lalu kompilasi dan biarkan kompiler menunjukkan **setiap** tempat yang terdampak.

### Langkah 2: Rambatkan ke Command

`tenantId` masuk ke lima command mutasi.

### Langkah 3: Implementasi — dua lapis sekaligus

Di Postgres: `dbQuery(tenantId)` **dan** predikat `WHERE tenant_id = ...` eksplisit.

### Langkah 4: Fake repository ikut diperketat

Kalau fake lebih longgar dari yang asli, test isolasi akan hijau secara palsu.

### Langkah 5: Rute menyuntikkan tenant dari konteks

### Langkah 6: Test regresi — lalu **buktikan test itu bergigi**

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Tenant menjadi bagian dari tanda tangan

`core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/InvoiceRepository.kt`

```kotlin
interface InvoiceRepository {
    suspend fun findById(tenantId: TenantId, id: InvoiceId): Invoice?
    suspend fun deleteDraft(tenantId: TenantId, id: InvoiceId)
    ...
}

interface InvoiceTemplateRepository {
    suspend fun findById(tenantId: TenantId, id: InvoiceTemplateId): InvoiceTemplate?
    suspend fun archive(tenantId: TenantId, id: InvoiceTemplateId)
    ...
}

interface InvoicePaymentRepository {
    suspend fun historyFor(tenantId: TenantId, invoiceId: InvoiceId): List<InvoicePayment>
    suspend fun append(tenantId: TenantId, payment: InvoicePayment)
    suspend fun totalPaidFor(tenantId: TenantId, invoiceId: InvoiceId): Money
}
```

**Mengapa blok ini ditulis begini?**

- **Kenapa bukan menambah `if` di tujuh rute yang kurang?** Karena itu memperbaiki tujuh bug tanpa
  memperbaiki penyebabnya. Rute kedelapan yang ditulis bulan depan akan mengulanginya, dan tidak ada
  apa pun yang akan memberi tahu penulisnya. Kita sudah punya bukti empiris bahwa manusia lupa
  melakukannya: tujuh dari delapan kali.

- **Kenapa `tenantId` jadi parameter pertama?** Konvensi. Begitu semua fungsi ber-scope tenant
  menaruhnya di posisi yang sama, ketidakhadirannya terlihat saat membaca sekilas.

- **Ini adalah pola "make illegal states unrepresentable"** yang diterapkan pada keamanan, bukan
  pada model data. Pertanyaan "apakah rute ini memeriksa tenant?" berubah dari **pertanyaan audit**
  (butuh manusia membaca kode) menjadi **pertanyaan tipe** (dijawab kompiler, setiap build).

> 💡 **Mental model:** kalau sebuah pemeriksaan keamanan bisa *dilupakan*, ia akan dilupakan.
> Desain yang baik memindahkannya dari "yang harus diingat" ke "yang tidak bisa dihindari".

### Blok B: Dua lapis di implementasi, bukan satu

`server/.../PostgresInvoicingRepositories.kt`

```kotlin
override suspend fun findById(tenantId: TenantId, id: InvoiceId): Invoice? =
    DatabaseFactory.dbQuery(tenantId) {                        // ← lapis 1: RLS + koneksi aplikasi
        val row = InvoicesTable.selectAll()
            .where {
                (InvoicesTable.id eq id.value) and
                    (InvoicesTable.tenantId eq tenantId.value)  // ← lapis 2: predikat eksplisit
            }
            .singleOrNull() ?: return@dbQuery null
        loadInvoiceDetails(row)
    }
```

**Mengapa dua-duanya, bukan salah satu?**

- **`dbQuery(tenantId)` saja tidak cukup** — persis itulah yang baru saja kita pelajari. Ia
  bergantung pada RLS benar-benar berlaku, padahal pemilik tabel melewatinya.
- **Predikat saja juga tidak cukup** — ia melindungi kueri *ini*, tapi tidak memberi konteks bagi
  kueri lain di transaksi yang sama.

Ini bukan paranoia berlebihan; ini **defense in depth** dengan biaya nyaris nol: satu baris `and`.

### Blok C: Perbaikan yang muncul karena menarik satu benang

```kotlin
// SEBELUM
override suspend fun append(payment: InvoicePayment): Unit =
    DatabaseFactory.dbQuery {
        val invRow = InvoicesTable.selectAll()
            .where { InvoicesTable.id eq payment.invoiceId.value }
            .singleOrNull()
        val tenantIdStr = invRow?.get(InvoicesTable.tenantId) ?: "unknown"   // ← 😱
        ...
    }
```

`?: "unknown"` itu berarti: kalau fakturnya tidak ketemu, tulis saja baris pembayaran dengan
`tenant_id = "unknown"`. Baris itu tidak dimiliki tenant mana pun, lolos dari setiap policy RLS,
dan melanggar foreign key secara semantik walau tidak secara teknis.

```kotlin
// SESUDAH
override suspend fun append(tenantId: TenantId, payment: InvoicePayment): Unit =
    DatabaseFactory.dbQuery(tenantId) {
        val ownsInvoice = InvoicesTable.selectAll()
            .where {
                (InvoicesTable.id eq payment.invoiceId.value) and
                    (InvoicesTable.tenantId eq tenantId.value)
            }
            .singleOrNull() != null
        require(ownsInvoice) {
            "Faktur '${payment.invoiceId.value}' tidak ditemukan pada tenant '${tenantId.value}'."
        }
        ...
    }
```

> 💡 **Pelajaran:** nilai *default* untuk data yang tidak ditemukan hampir selalu salah. `"unknown"`,
> `-1`, `""`, dan `0` mengubah "aku tidak tahu" menjadi "ini jawabannya" — dan yang tidak tahu itu
> lalu tersimpan permanen di database.

### Blok D: Pemeriksaan manual yang justru dihapus

```kotlin
// SESUDAH — GET /{id}
val invoice = invoiceRepository.findById(tenant.tenantId, InvoiceId(id))
    ?: return@get call.respond(HttpStatusCode.NotFound, "Invoice tidak ditemukan")

call.respondJson(InvoiceCodec.encode(invoice).encode())
```

Blok `if (invoice.tenantId != tenant.tenantId) → 403` **dihapus**, bukan dipertahankan.

**Mengapa menghapus pemeriksaan keamanan adalah perbaikan keamanan di sini?**

1. **Ia sudah mati.** Pencarian dibatasi tenant, jadi faktur milik orang lain tidak pernah sampai
   ke baris itu. Kode mati memberi rasa aman palsu kepada pembaca berikutnya.
2. **Ia mengajarkan pola yang salah.** Selama ia ada, orang akan menyalinnya ke rute baru —
   melanjutkan pola "ingat untuk memeriksa" yang baru saja kita buang.
3. **Ia membocorkan informasi.** Ini paling halus: `403 Forbidden` berarti *"ada, tapi bukan
   milikmu"*. `404 Not Found` berarti *"tidak ada untukmu"*. Yang pertama mengubah endpoint menjadi
   alat penyerang untuk menguji apakah sebuah ID valid.

Ada satu test khusus untuk poin ketiga: `unknownInvoiceId_isIndistinguishableFromForeignOne`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan Kita | Alternatif | Mengapa Kita Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| **`tenantId` wajib di tanda tangan** | `if` per rute | Kompiler menegakkannya; mustahil dilupakan | Sudah terbukti gagal — 7 dari 8 rute lupa |
| | Middleware/interceptor global | Tidak tahu objek mana yang sedang diambil; tidak bisa memvalidasi kepemilikan baris | Rasa aman palsu |
| **Predikat eksplisit + `dbQuery(tenantId)`** | Andalkan RLS saja | Pemilik tabel melewati RLS tanpa `FORCE` | Yang baru saja terjadi |
| **404, bukan 403** | 403 Forbidden | 403 mengonfirmasi ID itu valid | Enumerasi ID |
| **Fake repo ikut diperketat** | Biarkan longgar | Fake yang longgar = test hijau palsu | Test yang menjamin sesuatu yang tidak dijaminnya |
| **`FORCE RLS` ditunda** | Sekalian sekarang | Akan mematikan setiap jalur yang masih lupa — bagus, tapi harus jadi rilis tersendiri agar radiusnya terkendali | Mati total di modul lain yang belum diaudit |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

### Jebakan 1: Mengira RLS aktif berarti RLS berlaku

`ENABLE ROW LEVEL SECURITY` tidak berlaku bagi pemilik tabel. Tanpa `FORCE`, aplikasi yang berjalan
sebagai owner (dan yang menjalankan migrasi **adalah** owner) melewatinya seluruhnya.

*Cara memastikan*: `SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class WHERE relname = 'invoices';`
Kolom kedua `true` dan ketiga `false` = persis kondisi berbahaya ini.

### Jebakan 2: Memperbaiki gejala yang dilaporkan, bukan kelasnya

Laporan awal saya sendiri hanya menyebut `GET /{id}/pdf`. Kalau saya berhenti di situ, tujuh rute
lain — termasuk yang bisa **menulis** — tetap terbuka. Ketika menemukan satu kebocoran, pertanyaan
berikutnya bukan "bagaimana memperbaikinya" melainkan **"di mana lagi pola ini muncul?"**

### Jebakan 3: Nilai default untuk data yang tidak ditemukan

Lihat Blok C. `?: "unknown"` menyembunyikan kegagalan lalu menuliskannya ke database.

### Jebakan 4: Fake repository yang lebih longgar dari aslinya

`InMemoryInvoicePaymentRepository` tidak menyimpan tenant sama sekali. Kalau dibiarkan, test isolasi
pembayaran akan hijau tanpa membuktikan apa pun. Karena `InvoicePayment` tidak punya `tenantId`,
saya menyimpannya berdampingan:

```kotlin
private val payments = mutableListOf<Pair<TenantId, InvoicePayment>>()
```

> 💡 **Aturan**: test double boleh lebih *sederhana* dari aslinya, tapi tidak boleh lebih *permisif*.

### Jebakan 5: Menulis test keamanan yang tidak pernah bisa merah

Ini yang paling berbahaya, karena hasilnya adalah **dokumen jaminan palsu**. Lihat bagian 6.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

### Satu test per rute — sengaja tidak diringkas

`server/src/test/.../InvoicingTenantIsolationTest.kt` punya 13 test. Menggabungkan delapan rute ke
dalam satu test akan berhenti di kegagalan pertama dan menyembunyikan tujuh sisanya.

### Menguji akibat, bukan implementasi

```kotlin
@Test
fun voidInvoice_acrossTenants_isDeniedAndLeavesInvoiceUntouched() = testApplication {
    val f = fixture(victimStatus = InvoiceStatus.ISSUED); install(f)
    val res = client.post("/api/tenant/invoicing/${f.victimInvoice.id.value}/void") { ... }
    assertDenied(res)
    runBlocking {
        val after = f.invoices.findById(victimId, f.victimInvoice.id)
        assertEquals(InvoiceStatus.ISSUED, after!!.status, "Faktur korban dibatalkan oleh tenant lain")
    }
}
```

Dua assertion, dua pertanyaan berbeda: **(a)** penyerang ditolak, **(b)** dokumen korban tidak
berubah. Yang kedua yang benar-benar penting — status HTTP bisa saja 400 karena alasan lain sambil
mutasinya tetap terjadi.

### Kontrol positif — tanpa ini semuanya sia-sia

```kotlin
@Test
fun ownTenant_stillReadsItsOwnInvoice() = testApplication { ... }
```

Kalau seluruh rute dibuat mengembalikan 404 selamanya, sebelas test isolasi tetap hijau dan aplikasi
rusak total. Kontrol positif adalah yang membedakan "aman" dari "mati".

### Membuktikan test itu bergigi

Ini langkah yang paling sering dilewatkan. Setelah 13/13 hijau, saya **sengaja mengembalikan
perilaku lama** pada fake repository:

```kotlin
override suspend fun findById(tenantId: TenantId, id: InvoiceId): Invoice? = invoices[id]
```

Hasilnya: **9 dari 13 merah.** Empat yang tetap hijau adalah dua kontrol positif dan dua test
pembayaran (yang dijaga mekanisme berbeda). Lalu saya kembalikan.

> 🔑 Test keamanan yang belum pernah kamu lihat merah adalah **hipotesis**, bukan jaminan.
> Sebelum menutup task, tanya: *"apakah test ini akan gagal pada kode yang lama?"* Kalau kamu tidak
> tahu jawabannya, kamu belum tahu apakah testmu menguji sesuatu.

### Hasil verifikasi

- `InvoicingTenantIsolationTest`: **13/13 lulus**, 9 terbukti merah pada perilaku lama.
- Kompilasi **enam target**: JVM, WasmJS, JS, Android, iOS Arm64, server.
- Audit sisa: `grep` untuk `findById(`/`archive(`/`historyFor(`/`totalPaidFor(`/`deleteDraft(`/`append(`
  tanpa tenant di rute dan use case → **nihil**.
- 47 kegagalan `HikariPool$PoolInitializationException` = tidak ada Postgres di mesin dev, sudah ada
  sebelum perubahan ini.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1 — Tutup lapisan ketiga.** Tulis `V35__force_rls_invoicing.sql` yang menjalankan
      `ALTER TABLE ... FORCE ROW LEVEL SECURITY` untuk tabel invoicing. Sebelum menjalankannya,
      prediksi jalur mana yang akan langsung rusak. Kenapa migrasi ini **tidak boleh** digabung ke
      rilis yang sama dengan perbaikan kode?

- [ ] **Tantangan 2 — Audit modul lain.** Pola `findById(id)` tanpa tenant tidak eksklusif milik
      faktur. Grep `DatabaseFactory.dbQuery {` (tanpa argumen) di seluruh `server/`. Mana yang
      memang pekerjaan platform, dan mana yang data tenant yang bocor?

- [ ] **Tantangan 3 — Jadikan lupa itu mustahil di seluruh sistem.** Bisakah kamu membuat wrapper
      yang secara tipe menolak kueri tabel ber-`tenant_id` tanpa `TenantId`? Rancang, lalu
      argumentasikan apakah ongkos abstraksinya sepadan dibanding disiplin per-repository.

- [ ] **Tantangan 4 — Apakah 404 selalu benar?** Untuk tenant yang memang berhak tapi kehabisan
      kuota, 404 menyesatkan. Di mana batas antara "sembunyikan keberadaannya" dan "jelaskan
      kenapa ditolak"?

---

## 📎 Berkas yang Disentuh

| Berkas | Perubahan |
|---|---|
| `core/.../invoicing/InvoiceRepository.kt` | `tenantId` pada 7 operasi per-ID + KDoc alasannya |
| `core/.../usecases/{Void,Issue,UpdateDraft,RecordPayment,CreateSettlement}*.kt` | `tenantId` di command, diteruskan ke repository |
| `core/.../usecases/{CreateInvoice,PrefillInvoiceFromSampling}UseCase.kt` | `tenantId` diteruskan ke `templateRepository.findById` |
| `server/.../PostgresInvoicingRepositories.kt` | `dbQuery(tenantId)` + predikat `tenant_id` eksplisit; `append` menolak faktur milik tenant lain |
| `server/.../InMemoryInvoicingRepositories.kt` | Fake diperketat; pembayaran menyimpan tenant |
| `server/.../routes/InvoicingRoutes.kt` | Tenant disuntikkan dari konteks di 8 rute; pemeriksaan manual mati dihapus |
| `app/shared/.../InvoiceViewModel.kt` | `tenantId` pada command sisi klien (tidak ikut ke wire) |
| `server/src/test/.../InvoicingTenantIsolationTest.kt` | **Baru** — 13 test regresi |
