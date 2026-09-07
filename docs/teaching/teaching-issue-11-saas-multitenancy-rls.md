# 🎓 Modul Pembelajaran: Arsitektur Multi-Tenancy SaaS & PostgreSQL Row-Level Security (RLS)

> **Level Target**: Junior to Mid-Level Software Engineer  
> **Topik Utama**: Domain-Driven Design (DDD), Multi-Tenancy SaaS, PostgreSQL Row-Level Security (RLS), Ktor Server Plugin, JetBrains Exposed, HikariCP, Flyway  
> **Prasyarat**: Dasar Kotlin, Konsep REST API, Pemahaman Dasar SQL (SELECT/INSERT)  
> **Referensi Task**: [GitHub Issue #11](https://github.com/amalari/wemade-erp/issues/11) — *Phase 0: SaaS Foundation*

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Industri ERP Konveksi/Garmen
Bayangkan aplikasi WeMade ERP dipakai oleh **Pabrik A (Konveksi Berkah)** dan **Pabrik B (Maklon Jaya)** dalam satu server aplikasi yang sama.
- Pabrik A punya data harga modal kain, daftar supplier rahasia, dan pesanan baju klien mereka.
- Jika sistem kita dibangun dengan cara lama (*single-tenant* biasa di mana kueri SQL hanya `SELECT * FROM orders`), maka satu kesalahan kecil developer—misalnya lupa mengetik `WHERE company_id = ...`—akan menyebabkan **Pabrik A bisa melihat seluruh pesanan dan resep kain milik Pabrik B!**
- Di industri manufaktur, insiden seperti ini disebut **Data Leakage (Kebocoran Data Antar-Tenant)** dan berakibat fatal: hilangnya kepercayaan klien, tuntutan hukum, dan bisnis SaaS hancur seketika.

### Analogi Sederhana: "Apartemen vs Rumah Pribadi"
- **Single-Tenant**: Seperti membangun 100 rumah pribadi terpisah untuk 100 keluarga. Aman, tapi biaya server dan *maintenance*-nya sangat mahal (harus deploy 100 database dan 100 server terpisah).
- **Multi-Tenant dengan RLS**: Seperti membangun 1 gedung apartemen mewah dengan 100 kamar. Semua orang berbagi fondasi, lift, dan genset yang sama (satu server & satu database), **tetapi setiap kamar memiliki kunci elektronik khusus (TenantId)**. Lebih canggih lagi: tombol lift kamar lain bahkan tidak bisa ditekan oleh penghuni yang tidak berhak (**Row-Level Security**).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diberi tugas dari nol oleh Tech Lead: *"Tolong ubah sistem ini jadi Multi-Tenant SaaS dengan isolasi data ketat"*, **JANGAN langsung membuka database atau menulis controller API!**

Berikut adalah urutan kerja (*mental order of operations*) standar industri:

```
[Langkah 1: Pure Domain Layer (core)]
  ├── Definisikan Value Objects (TenantId, TenantSlug, SubscriptionTier)
  ├── Bangun Entity (Tenant, User) dengan aturan bisnis
  └── Buat interface Repository (Kontrak) & Use Cases
         ↓
[Langkah 2: Database Schema & Migration (Flyway)]
  ├── Rancang DDL SQL dengan kolom `tenant_id`
  └── Pasang fungsi PostgreSQL Row-Level Security (RLS)
         ↓
[Langkah 3: Infrastructure & Connection Pool]
  ├── Setup HikariCP & Exposed ORM di DatabaseFactory
  └── Implementasikan PostgresTenantRepository & PostgresUserRepository
         ↓
[Langkah 4: Server Middleware (Ktor Plugin)]
  ├── Bangun TenantResolutionPlugin (ekstraksi Subdomain / Header)
  └── Guard penolakan dini (404 / 403 Forbidden untuk tenant suspended)
         ↓
[Langkah 5: Client-Side Session (app/shared)]
  └── Simpan state tenant di StateFlow & buat Endpoint Resolver
         ↓
[Langkah 6: Automated Testing]
  └── Unit Test (Domain murni) & Integration Test (PostgreSQL riil)
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah kodingan yang baru saja kita bangun baris demi baris:

### Blok 1: Value Object yang Melindungi Format Data (`TenantValueObjects.kt`)

Lokasi: [`core/src/commonMain/kotlin/.../domain/tenant/TenantValueObjects.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/tenant/TenantValueObjects.kt)

```kotlin
@JvmInline
value class TenantSlug(val value: String) {
    init {
        require(value.isNotBlank()) { "TenantSlug cannot be blank" }
        require(value.length in 3..30) { "TenantSlug must be between 3 and 30 characters" }
        require(SLUG_REGEX.matches(value)) { 
            "TenantSlug must consist of lowercase alphanumeric characters and hyphens: $value" 
        }
        require(!FORBIDDEN_SLUGS.contains(value)) {
            "TenantSlug '$value' is a reserved system keyword and cannot be used"
        }
    }

    companion object {
        private val SLUG_REGEX = Regex("^[a-z0-9]+(-[a-z0-9]+)*$")
        val FORBIDDEN_SLUGS = setOf(
            "admin", "api", "app", "auth", "billing", "dashboard", 
            "mail", "portal", "root", "superadmin", "system", "wemade", "www"
        )
    }
}
```

🔍 **Bedah Pemikiran Senior**:
1. **Mengapa `@JvmInline value class`?**
   - Menghemat memori! Di runtime JVM, Kotlin akan memperlakukannya sebagai `String` biasa (tanpa overhead alokasi object di Heap), tetapi di level kodingan kita mendapatkan *type safety* penuh. Kamu tidak akan bisa salah memasukkan `UserId` ke parameter yang butuh `TenantSlug`.
2. **Mengapa ada `FORBIDDEN_SLUGS`?**
   - Bayangkan jika ada klien jahil mendaftarkan pabrik dengan nama subdomain `admin` atau `api` sehingga URL-nya menjadi `admin.wemade.id` atau `api.wemade.id`. Ini akan merusak routing sistem dan membuka celah eksploitasi keamanan phishing. Kita cegah dari level Domain terendah!

---

### Blok 2: Entity yang Bersih & Immutable (`Tenant.kt`)

Lokasi: [`core/src/commonMain/kotlin/.../domain/tenant/Tenant.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/tenant/Tenant.kt)

```kotlin
data class Tenant(
    val id: TenantId,
    val slug: TenantSlug,
    val name: TenantName,
    val status: TenantStatus = TenantStatus.TRIAL,
    val tier: SubscriptionTier = SubscriptionTier.PRO,
    val activeMachineCount: Int = 0
) {
    val isAccessible: Boolean
        get() = status.isAccessible

    fun suspend(): Tenant = copy(status = TenantStatus.SUSPENDED)

    fun updateActiveMachineCount(count: Int): Tenant {
        require(count >= 0) { "Active machine count cannot be negative: $count" }
        require(count <= tier.maxActiveMachines) {
            "Machine count ($count) exceeds tier limit of ${tier.maxActiveMachines} for ${tier.name}"
        }
        return copy(activeMachineCount = count)
    }
}
```

🔍 **Bedah Pemikiran Senior**:
- **Aturan Immutability DDD**: Perhatikan bahwa tidak ada kata kunci `var`. Tidak ada fungsi `tenant.status = SUSPENDED`. Semua mutasi state menghasilkan salinan baru (`copy(...)`). Hal ini menjamin thread-safety di lingkungan coroutine async Ktor dan mencegah *side-effect* liar.
- **Enforce Business Rule**: Aturan kuota mesin konveksi (misal Starter maks 5 mesin, Pro maks 15 mesin) dijaga langsung oleh Entity. Database tidak perlu tahu logika ini; Domain lah yang memegang kebenaran mutlak.

---

### Blok 3: Jaring Pengaman Tingkat Database — Row-Level Security (`V1__create_multi_tenant_schema.sql`)

Lokasi: [`server/src/main/resources/db/migration/V1__create_multi_tenant_schema.sql`](file:///Volumes/amalari/Projects/wemade/server/src/main/resources/db/migration/V1__create_multi_tenant_schema.sql)

```sql
-- 1. Aktifkan fitur RLS pada tabel users
ALTER TABLE users ENABLE ROW LEVEL SECURITY;

-- 2. Pasang aturan kebijakan (Policy)
CREATE POLICY tenant_isolation_users_policy ON users
    AS PERMISSIVE
    FOR ALL
    TO PUBLIC
    USING (
        current_setting('app.current_user_role', true) = 'PLATFORM_SUPERADMIN'
        OR 
        tenant_id = current_setting('app.current_tenant_id', true)
    )
    WITH CHECK (
        current_setting('app.current_user_role', true) = 'PLATFORM_SUPERADMIN'
        OR 
        tenant_id = current_setting('app.current_tenant_id', true)
    );
```

🔍 **Bedah Pemikiran Senior**:
- **Apa itu `current_setting('app.current_tenant_id', true)`?**
  Ini adalah variabel sesi lokal di PostgreSQL.
  Ketika Ktor menerima request dari Pabrik A, Ktor mengirim perintah SQL ringan: `SET LOCAL app.current_tenant_id = 'ten-pabrik-a';`.
- **Keajaiban RLS**:
  Bahkan jika developer junior di kemudian hari menulis kueri:
  `SELECT * FROM users;` (tanpa klausul `WHERE` sama sekali), PostgreSQL **secara otomatis di level mesin database** hanya akan mengembalikan baris yang memiliki `tenant_id = 'ten-pabrik-a'`. Data tenant lain seolah-olah tidak ada di dunia nyata!

---

### Blok 4: Menjembatani Ktor & RLS di Kotlin (`DatabaseFactory.kt`)

Lokasi: [`server/src/main/kotlin/.../infrastructure/DatabaseFactory.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/DatabaseFactory.kt)

```kotlin
suspend fun <T> dbQuery(
    tenantId: TenantId? = null,
    block: suspend () -> T
): T = newSuspendedTransaction(Dispatchers.IO) {
    if (tenantId != null) {
        // Enforce PostgreSQL Row-Level Security (RLS) untuk transaksi ini
        exec("SET LOCAL app.current_tenant_id = '${tenantId.value}';")
    }
    block()
}
```

🔍 **Bedah Pemikiran Senior**:
- **Mengapa `SET LOCAL` dan bukan `SET` biasa?**
  Karena kita memakai connection pool (HikariCP). Satu koneksi database yang sama akan dipakai bergantian oleh banyak request secara bergantian.
  Jika memakai `SET`, koneksi tersebut akan tercemar tenant sebelumnya selamanya.
  Dengan `SET LOCAL`, variabel tersebut **hanya berlaku selama blok transaksi berjalan**, dan otomatis di-reset saat transaksi selesai (*auto-clean*).

---

### Blok 5: Satpam Pintu Masuk — Ktor Resolution Plugin (`TenantResolutionPlugin.kt`)

Lokasi: [`server/src/main/kotlin/.../plugins/TenantResolutionPlugin.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/plugins/TenantResolutionPlugin.kt)

```kotlin
val TenantResolutionPlugin = createApplicationPlugin(
    name = "TenantResolutionPlugin",
    createConfiguration = ::TenantResolutionConfig
) {
    val repository = pluginConfig.tenantRepository 
        ?: error("TenantRepository must be configured")
    val publicPrefixes = pluginConfig.publicRoutePrefixes

    onCall { call ->
        val path = call.request.path()
        if (path == "/" || publicPrefixes.any { path.startsWith(it) }) return@onCall

        // Ekstrak dari Header atau Subdomain URL
        val headerSlug = call.request.header("X-Tenant-Slug")
        val host = call.request.host() // misal: "pabrik-jaya.wemade.id"
        val subdomain = extractSubdomain(host)

        val resolvedTenant = // ... cari di repository ...

        if (resolvedTenant == null) {
            call.respond(HttpStatusCode.NotFound, "Tenant workspace tidak ditemukan")
            return@onCall
        }

        if (!resolvedTenant.isAccessible) {
            call.respond(HttpStatusCode.Forbidden, "Tenant sedang di-suspend")
            return@onCall
        }

        // Simpan TenantContext ke dalam ApplicationCall attributes
        call.attributes.put(TenantContextAttributeKey, TenantContext.fromTenant(resolvedTenant))
    }
}
```

🔍 **Bedah Pemikiran Senior**:
- **Fail Fast & Early Rejection**: Jangan biarkan request yang tidak sah atau akun yang sudah nunggak (*suspended*) menyentuh use case bisnis atau database. Tolak langsung di gerbang HTTP Ktor dengan kode status `404` atau `403`.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Teknologi / Pendekatan | Alternatif Populer | Mengapa Kita Memilih Ini? | Risiko Fatal Jika Memakai Alternatif |
|---|---|---|---|
| **PostgreSQL Row-Level Security (RLS)** | Filter manual kueri (`WHERE tenant_id = ?`) di kode Kotlin | **Security by Default**. Isolasi data dijamin oleh database engine, bukan bergantung pada ketelitian developer. | Satu kali developer junior lupa nulis `.where { tenantId eq current }`, data seluruh perusahaan klien bocor ke publik! |
| **HikariCP** | Default Apache DBCP / c3p0 | Connection pool tercepat di ekosistem JVM, sangat ringan, dan memiliki pemulihan koneksi mati yang tangguh. | Pool lambat menyebabkan server *hang* atau kehabisan thread saat ratusan operator tablet pabrik submit data bersamaan. |
| **Flyway Migrations** | Hibernate Auto-DDL (`hbm2ddl.auto`) | Database schema memiliki riwayat versi (*version-controlled*) yang pasti, reproducible, dan aman untuk production. | Hibernate auto-DDL bisa tiba-tiba mengubah tipe data atau menghapus tabel di production saat server restart. |
| **JetBrains Exposed DSL** | JPA / Hibernate | Type-safe murni Kotlin, tanpa sihir proxy bytecode yang membingungkan, ringan, dan ramah Coroutines. | Hibernate di KMP/Ktor sering menimbulkan isu `LazyInitializationException` dan memakan RAM sangat besar. |
| **Pure Kotlin `core`** | Model data dicampur entity framework | Kode bisnis (Domain) 100% bebas framework dan bisa di-share utuh ke Android, iOS, Desktop, dan Web (Wasm). | Jika `core` mengimpor library Ktor atau SQL, kamu tidak akan bisa menjalankan domain logic di aplikasi iOS atau Android! |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

### 🚨 Jebakan 1: Menyimpan ID sebagai `String` Primitif (*Primitive Obsession*)
- **Salah**:
  ```kotlin
  fun registerUser(tenantId: String, userId: String)
  ```
  *Kenapa bahaya?* Sangat mudah tertukar memanggil `registerUser(userId, tenantId)`. Compiler tidak akan protes karena keduanya sama-sama `String`!
- **Benar (DDD Way)**:
  ```kotlin
  fun registerUser(tenantId: TenantId, userId: UserId)
  ```
  *Hasil*: Jika parameter tertukar, compiler Kotlin langsung menolak dan memberi error merah seketika.

### 🚨 Jebakan 2: Query Tanpa Menyetel Session Context
- **Salah**: Mengeksekusi kueri langsung tanpa blok `dbQuery(tenantId)`.
- **Hasil**: Karena RLS aktif, kueri tersebut akan mengembalikan 0 hasil (kosong) dan kamu akan bingung kenapa data tidak muncul. Selalu lewatkan `tenantId` pada operasi operasional tenant.

### 🚨 Jebakan 3: Menggunakan `var` pada Entity
- **Salah**: `class Tenant { var status: String = "ACTIVE" }`
- **Kenapa bahaya?** Di aplikasi multi-threaded / coroutines, objek mutable rawan mengalami *race condition* di mana dua request bersamaan mengubah status yang sama secara bertabrakan.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Senior developer tidak pernah percaya kodenya berjalan sebelum ada pengujian otomatis:

1. **Unit Test (Kilat, Tanpa Database)**:
   Diuji di [`TenantTest.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonTest/kotlin/com/eventverse/app/domain/tenant/TenantTest.kt). Menguji apakah slug yang dilarang (misal `admin`) langsung melempar exception, dan apakah penambahan mesin melebihi kuota paket Starter langsung ditolak.
2. **Integration Test (Nyata dengan PostgreSQL 18)**:
   Diuji di [`PostgresTenantRepositoryIntegrationTest.kt`](file:///Volumes/amalari/Projects/wemade/server/src/test/kotlin/com/eventverse/app/infrastructure/PostgresTenantRepositoryIntegrationTest.kt). Menghubungkan ke instance PostgreSQL nyata di container Docker, mengeksekusi insert, kueri, dan memverifikasi data tersimpan secara fisik di hard disk.

Perintah verifikasi:
```bash
./gradlew :core:jvmTest :server:test
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

Untuk mengasah pemahamanmu setelah membaca modul ini, coba selesaikan 2 tantangan mini berikut:

- [ ] **Tantangan 1**: Buka file [docker-compose.yml](file:///Volumes/amalari/Projects/wemade/docker-compose.yml), lalu coba masuk ke terminal PostgreSQL container dengan perintah:
  ```bash
  docker exec -it wemade-postgres psql -U postgres -d wemade_erp
  ```
  Jalankan perintah `\d users` untuk melihat definisi tabel dan membuktikan keberadaan Policy RLS yang kita buat!
- [ ] **Tantangan 2**: Buat satu Value Object baru bernama `TenantPhoneNumber` di `core` dengan aturan validasi: harus diawali dengan `+` atau `08` dan memiliki panjang antara 9 s/d 15 digit. Tambahkan unit test-nya di `core/src/commonTest`!
