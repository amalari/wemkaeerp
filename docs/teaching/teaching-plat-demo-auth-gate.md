# Modul Pembelajaran: Gerbang Login Demo (`/api/public/auth/demo`) Default Mati

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Keamanan endpoint publik, feature flag fail-closed, isolasi tenant pada penerbitan token, klaim JWT
> **Prasyarat**: Dasar Ktor routing, JWT (klaim `tenant_slug`, `tenant_id`, `role`), konsep multi-tenant
> **Referensi Task**: Perbaikan keamanan P0/P1 demo auth (tanpa nomor issue)

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: `POST /api/public/auth/demo` dibuat untuk tombol "Demo Mode" dan persona pengujian. Ia menerbitkan token **tanpa kredensial apa pun**. Di internet terbuka, siapa pun bisa meminta token `PLATFORM_SUPERADMIN`, token Owner tenant mana pun lewat `tenantSlug`, atau membuat persona. Fallback `findByEmail("student.achmad@gmail.com")` yang global bahkan menghasilkan token hibrida: `tenant_id` milik tenant A, `tenant_slug` milik tenant B.
- **Analogi**: kunci cadangan gedung yang digantung di pintu depan dengan tulisan "untuk testing". Nyaman saat renovasi, bencana saat gedung dihuni. Solusinya: kunci itu disimpan di laci terkunci (flag mati secara default) dan hanya membuka kamar contoh (tenant demo), bukan seluruh gedung.
- **Hasil akhir**: endpoint demo membalas 404 kecuali `WEMADE_DEMO_LOGIN=on`; saat menyala hanya tenant demo yang boleh dimasuki; token tidak pernah terbit untuk user yang bukan milik tenant; klaim `tenant_slug` kosong ditolak.

---

## 2. "Start dari Mana?" - Urutan Penulisan

1. **Langkah 0 - Tentukan default aman.** Flag untuk fitur berbahaya harus mati secara default. Ikuti pola yang sudah ada (`WEMADE_PUBLIC_SIGNUP`), jangan membuat konvensi baru.
2. **Langkah 1 - Value yang menjelaskan kebijakan.** `DemoLoginPolicy(enabled, demoTenantSlugs)` dengan `fromEnv()` yang bisa disuntik agar mudah dites tanpa mengubah environment proses.
3. **Langkah 2 - Gerbang di route**, sebelum ada akses repository: kalau mati, balas 404 sebelum membaca parameter, mencari tenant, atau membuat user.
4. **Langkah 3 - Invarian token**: user harus milik tenant yang diminta; hapus fallback global.
5. **Langkah 4 - Fail-closed pembaca token**: `/me` dan `TenantResolutionPlugin` menolak token tenant tanpa slug.
6. **Langkah 5 - Injeksi di `module()`** (`demoLoginPolicy: DemoLoginPolicy? = null`; null = baca env) supaya tes menyalakan gerbang lewat parameter, bukan dengan melemahkan gerbangnya.
7. **Langkah 6 - Dokumentasi env** di `.env.example` dan resep verifikasi.

---

## 3. Bedah Blok Kode

### Blok A: Kebijakan

```kotlin
data class DemoLoginPolicy(
    val enabled: Boolean = false,
    val demoTenantSlugs: Set<String> = DEFAULT_DEMO_TENANTS
) {
    fun allows(slug: String): Boolean = enabled && slug.lowercase() in demoTenantSlugs
}
```
- Konstruktor tanpa argumen = mati. Lupa mengonfigurasi berarti aman.
- Allow-list tenant (`wemade-demo` + `WEMADE_DEMO_TENANTS`) membatasi radius ledakan walau flag menyala di staging.

### Blok B: Gerbang berlapis di route

```kotlin
if (!policy.enabled) { call.respond(HttpStatusCode.NotFound); return@post }
...
if (!policy.allows(tenantSlug)) { call.respond(HttpStatusCode.Forbidden, ...); return@post }
```
- 404 saat mati: endpoint tampak tidak ada, tidak membocorkan bahwa pintu belakang pernah ada.
- 403 saat tenant bukan demo: flag menyala tetapi tenant tidak diizinkan.
- Urutan penting: gerbang sebelum `repository.findBySlug`, supaya penyerang juga tidak bisa menebak slug tenant yang ada lewat beda 404/403.

### Blok C: Token milik tenant

```kotlin
if (!isSuperAdmin && user.tenantId != tenant.id) { 403 }
```
Sebelumnya `findByEmail` global bisa mengembalikan user tenant lain. Fallback dihapus; kalau tenant demo belum punya admin, dibuatkan akun milik tenant itu sendiri. Superadmin platform dikecualikan karena memang tidak terikat tenant.

### Blok D: Klaim slug kosong

```kotlin
// TenantResolutionPlugin
if (principal.isTenantBound && principal.tenantSlug == null) -> 403
// /me
if (tenantSlug == null && role != PLATFORM_SUPERADMIN) -> 403
```
Sebelumnya `/me` jatuh ke `"wemade-demo"` dan plugin melewati pemeriksaan header/subdomain bila slug null. Fallback senyap pada klaim identitas adalah pola yang sama dengan "role tak dikenal jadi TENANT_ADMIN": identitas yang tidak terbaca justru diberi akses.

---

## 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Env flag default mati | Cek `if (isProduction)` | Tidak bergantung penamaan lingkungan; sama dengan `WEMADE_PUBLIC_SIGNUP` | Lingkungan salah-label membuka pintu |
| 404 saat mati | 403 | Tidak membocorkan keberadaan endpoint | Memberi petunjuk ke penyerang |
| Policy disuntik ke `module()` | Tes memanggil `System.setProperty` / melemahkan gerbang | Gerbang tetap utuh di produksi; tes eksplisit | Tes lolos karena gerbang longgar |
| Allow-list tenant | Hanya flag global | Staging yang menyalakan flag tetap tidak bisa membuka tenant nyata | Satu flag salah = semua tenant terbuka |

---

## 5. Jebakan Pemula

1. **Menambah `if (BuildConfig.debug)` di klien.** Klien tidak boleh menjadi penjaga; penegakan di server.
2. **Fallback "biar tetap jalan".** `findByEmail(...)` global dan `?: "wemade-demo"` terasa membantu, tetapi mengubah error menjadi akses lintas tenant. Tolak, jangan tebak.
3. **Memeriksa tenant setelah membuat user.** Gerbang harus mendahului side effect (`userRepo.save`). Tes `gateOff_...NoUserCreated` mengunci ini.
4. **Mengecualikan superadmin dari semua pemeriksaan.** Hanya invarian "user milik tenant" yang dikecualikan; gerbang flag dan allow-list tetap berlaku.

---

## 6. Pembuktian (`DemoAuthGateApiTest`)

- Gerbang mati: superadmin, Owner, persona, dan tenant non-demo semuanya 404 dan repo user kosong.
- Gerbang menyala + `wemade-demo`: Owner/superadmin/persona 200; tenant non-demo (`pabrik-bukan-demo`) 403; tenant tambahan lewat konfigurasi (`bordir-uji-gate`) 200 (fixture non-default).
- Tanpa fallback global: user bertenant lain dengan email `student.achmad@gmail.com` tidak pernah dipinjam.
- Repo "bocor" yang mengembalikan user lintas tenant: 403.
- Klaim slug null/kosong: `/me` dan `/api/tenant/info` 403; token valid tetap 200.

Menjalankan: `DB_NAME=wemake_erp_scratch_p0 ./gradlew :server:test` (hanya DB ber-nama scratch).

---

## 7. Tantangan Mandiri

- [ ] Tambahkan audit log setiap kali login demo berhasil (siapa, tenant apa, dari IP mana).
- [ ] Buat `GET /api/public/onboarding/config` ikut melaporkan `demoLoginEnabled` agar klien menyembunyikan tombol "Demo Mode" saat gerbang mati.
- [ ] Tulis tes yang memastikan `.env.example` tidak pernah berisi `WEMADE_DEMO_LOGIN=on`.

---

## Catatan Operasional

Lingkungan dev lokal dan skrip verifikasi sekarang wajib menyalakan `WEMADE_DEMO_LOGIN=on` (dan `WEMADE_DEMO_TENANTS=` untuk tenant uji selain `wemade-demo`). Tombol "Demo Mode" di `/login` akan gagal (404) tanpa itu; itu perilaku yang diinginkan di produksi.
