# 🎓 Modul Pembelajaran: Pemisahan Login Platform (`app.`) vs Workspace Tenant (`<slug>.`)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Multi-tenant routing berbasis host, serah-terima sesi lintas origin, tiket sekali pakai, Strangler "mode lokal"
> **Prasyarat**: `teaching-plat-002-m0-builder-foundation.md` (aturan JWT vs host, carve-out superadmin), dasar JWT
> **Referensi Task**: [`discovery-M3-login-split.md`](../plannings/discovery-M3-login-split.md), [`PLAN-builder-console.md` §2](../plannings/PLAN-builder-console.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Dulu satu layar login melayani semua orang dan meminta user mengetik
"Subdomain / Kode Pabrik". Akibatnya:
- user harus hafal slug pabriknya;
- login di `app.wemakeerp.com` tidak terbawa ke `bordir.wemakeerp.com`, karena sesi disimpan di
  `localStorage`, dan **localStorage terikat per origin**. Dua subdomain = dua lemari yang berbeda.

**Analogi.** Bayangkan mal dengan banyak toko. Pintu utama mal (`app.`) hanya bertanya "siapa
Anda?", lalu satpam mengantar Anda ke toko Anda sendiri. Satpam tidak bisa menitipkan kunci toko
lewat pintu utama, karena kuncinya memang hanya berlaku di toko itu. Yang ia berikan adalah
**kupon sekali pakai** yang ditukar di kasir toko menjadi kunci asli. Kupon itu hangus setelah
60 detik atau setelah dipakai sekali.

**Hasil akhir.**

| Host | Yang terjadi |
|---|---|
| `app.<base>` / `<base>` | Login Google **tanpa** kolom slug. Server mencari tenant dari akun → menerbitkan tiket → redirect ke `https://<slug>.<base>/login?handoff=…` |
| `<slug>.<base>` | Tenant diambil dari host. Tag "Workspace: bordir-uji" tampil. `?handoff=` langsung ditukar jadi sesi |
| `localhost`, IP, Android/iOS/Desktop, atau `PLATFORM_BASE_DOMAIN` kosong | **Persis seperti dulu**: kolom slug tetap ada (label "Kode Pabrik (dev)") |

---

## 🧭 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 — Discovery Note dulu.** Pertanyaan termahal di fitur ini bukan soal kode, melainkan
   *bagaimana sesi pindah origin*. Pertanyaan itu diputuskan bersama user (tiket sekali pakai vs
   cookie domain vs login ulang) **sebelum** satu baris pun ditulis.
2. **Langkah 1 — Domain: `HostSurface` (`core`).** Satu parser murni `host + baseDomain →
   Local | Platform | Tenant(slug)`. Dimulai dari sini karena **klien dan server harus sepakat**
   soal arti sebuah host. Kalau masing-masing menulis parser sendiri, suatu hari keduanya akan
   berbeda pendapat.
3. **Langkah 2 — Use case: `AuthenticateWithGoogleUseCase` menerima slug `null`.** Aturan "tenant
   dari akun" adalah aturan bisnis, jadi tempatnya di domain, bukan di route.
4. **Langkah 3 — Infrastruktur: `SessionHandoffTicketService`.** Penerbit dan penukar tiket
   (JWT ber-issuer khusus, plus memori `jti`).
5. **Langkah 4 — Route: `SessionHandoffRoutes` + `/google` yang sadar host.** Fail-closed, dites
   per arah gagal.
6. **Langkah 5 — Klien: `PlatformHost` (expect/actual) → `AuthApiClient` → `AuthViewModel` →
   `LoginScreen`.** UI ditulis paling akhir dan hanya membaca `state.hostSurface`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A — Parser host tunggal (`core/.../domain/tenant/HostSurface.kt`)

```kotlin
fun parse(host: String?, baseDomain: String?): HostSurface {
    val base = baseDomain?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: return Local
    val cleanHost = host?.substringBefore(":")?.trim()?.trimEnd('.')?.lowercase()?.takeIf { it.isNotBlank() } ?: return Local
    if (cleanHost == base || cleanHost == "$PLATFORM_LABEL.$base") return Platform
    if (!cleanHost.endsWith(".$base")) return Local
    val label = cleanHost.removeSuffix(".$base")
    if (label.contains('.')) return Local
    return runCatching { Tenant(TenantSlug(label)) }.getOrElse { Platform }
}
```

**Mengapa ditulis begini?**
- **`Local` sebagai default yang aman.** Tanpa base domain (dev, test, native), perilaku lama tetap
  berlaku. Ini pola Strangler: fitur baru menyala hanya bila dikonfigurasi, dan tidak pernah
  mematahkan alur dev.
- **`endsWith(".$base")`, bukan `endsWith(base)`.** Tanpa titik, `evilwemakeerp.com` dianggap
  milik kita. Kasus ini dites.
- **Label tak sah jatuh ke `Platform`, bukan ditebak.** `www.`, `admin.`, `ab.` (terlalu pendek)
  tidak pernah menjadi "tenant lain". Validasi slug tetap di satu tempat: konstruktor `TenantSlug`.

### Blok B — Tenant dari akun (`AuthenticateWithGoogleUseCase`)

```kotlin
if (requestedSlug == null) return@runCatching authenticateOnPlatform(emailVo, command.profile.email)
...
private fun requireAccessible(tenant: Tenant) = require(tenant.isAccessible) { ... }
```

**Mengapa?**
- Ini aman **hanya** karena hari ini satu email = satu user = satu tenant (`findByEmail`). KDoc
  command menuliskannya terang-terangan: bila nanti satu email boleh ada di banyak tenant, jalur ini
  harus mengembalikan *pilihan*, bukan menebak.
- **Temuan sampingan yang diperbaiki**: dulu syaratnya `status == ACTIVE`, sehingga setiap tenant
  **TRIAL** hasil daftar publik terkunci dari login Google. Sekarang memakai `isAccessible`, aturan
  yang sama dengan `TenantResolutionPlugin`.

### Blok C — Tiket sekali pakai (`SessionHandoffTicketService`)

```kotlin
JWT.create().withIssuer("wemade-erp-handoff").withSubject(userId)
    .withJWTId(UUID.randomUUID().toString()).withClaim("tenant_slug", slug) ...

fun redeem(ticket: String, hostTenant: TenantSlug?): HandoffIdentity? {
    ...
    if (hostTenant != null && hostTenant != slug) return null
    if (redeemed.putIfAbsent(jti, expiresAt) != null) return null
}
```

**Mengapa?**
- **Issuer berbeda** dari token sesi (`wemade-erp`). Tiket yang bocor lewat URL atau log tidak
  pernah diterima sebagai sesi, dan sebaliknya token sesi tidak bisa ditukar sebagai tiket (dites).
- **Sekali pakai lewat `putIfAbsent`**: operasi atomik, jadi dua request kembar yang balapan tidak
  bisa sama-sama menang. Ini **berbeda** dari `PrintTicketService`, yang sengaja boleh dipakai ulang
  karena penampil PDF meminta ulang URL yang sama. Tiket handoff menghasilkan *sesi penuh*, jadi
  aturannya lebih ketat.
- **Batasan yang dicatat jujur**: memori `jti` per instance. Dengan beberapa instance server,
  pindahkan ke tabel.

### Blok D — Route fail-closed (`SessionHandoffRoutes.kt`)

```kotlin
post("/issue") { ... if (user.role == Role.PLATFORM_SUPERADMIN) → 400 ... }
post { if (surface is HostSurface.Platform) → 403
       identity == null → 401
       baca ulang user & tenant dari DB → nonaktif/tenant beda → 403 }
```

**Mengapa?**
- Route ini tinggal di bawah `/api/public` (penukar belum punya sesi), jadi `TenantResolutionPlugin`
  tidak menjaganya. **Setiap pemeriksaan ditulis eksplisit**: token sah, user aktif, tenant dapat
  diakses, host cocok.
- **Superadmin tidak di-handoff.** Ia tetap di platform dan masuk tenant lewat act-as (`X-Tenant-Slug`),
  sesuai carve-out F2.
- **Baca ulang DB saat penukaran.** Tiket berumur 60 detik, dan akun bisa dinonaktifkan dalam
  rentang itu.

### Blok E — Klien (`AuthViewModel`)

```kotlin
val requestedSlug = when (surface) {
    HostSurface.Platform -> null
    is HostSurface.Tenant -> surface.slug.value
    HostSurface.Local -> currentSlug
}
...
if (surface is HostSurface.Tenant && ticket != null) {
    PlatformNavigation.replacePath("/login") // tiket jangan tinggal di history
    redeemHandoff(ticket, surface.slug.value)
}
```

**Mengapa?**
- **Base domain diambil dari server** (`GET /api/public/onboarding/config` → `platformBaseDomain`),
  bukan dikompilasi ke bundle. Satu build Wasm melayani dev dan produksi.
- **Di `app.`, sesi tenant tidak disimpan.** Ia langsung dibawa ke subdomain. Origin platform tetap
  bersih.
- **Tiket di query, bukan fragment (`#`)**, karena router web memakai hash untuk path
  (`PlatformNavigation.getCurrentPath`). Risikonya tiket tercatat di access log reverse proxy.
  Risiko itu kecil karena tiket 60 detik dan sekali pakai, dan URL langsung dibersihkan dengan
  `replacePath`.
- **Persona dipulihkan hanya di jalur handoff.** Tanpa persona, `accessDecisions` kosong dan
  pendaratan tidak pernah terjadi (terlihat saat cek mata pertama). Jalur Google sengaja tidak
  diubah agar perilaku lamanya tetap.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa ini | Risiko alternatif |
|---|---|---|---|
| Tiket sekali pakai | Cookie `HttpOnly` di `.wemakeerp.com` | Model auth tetap bearer; tidak menyentuh semua API client | Cookie butuh CSRF protection dan mengubah semua klien sekaligus |
| Tiket sekali pakai | Login ulang di subdomain | Satu kali login Google | User login dua kali; terasa rusak |
| Satu `HostSurface` di `core` | Parser terpisah klien & server | Satu sumber kebenaran, dites sekali | Dua parser cepat atau lambat berbeda pendapat |
| Base domain dari `/config` | Konstanta build | Satu bundle untuk semua lingkungan | Build per lingkungan, mudah salah kirim |
| Default `Local` | Default `Platform` | Dev/test/native tidak berubah | Semua dev tiba-tiba kehilangan kolom slug |

---

## ⚠️ 5. Jebakan Pemula

1. **Mengira login di `app.` otomatis berlaku di `<slug>.`.** localStorage per origin, jadi harus
   ada mekanisme serah-terima yang eksplisit.
2. **Tiket yang boleh dipakai ulang.** Tiket yang menghasilkan sesi penuh **wajib** sekali pakai.
   Kalau tidak, siapa pun yang melihat URL di history atau log bisa masuk.
3. **`endsWith(base)` tanpa titik** membuka domain tiruan (`evilwemakeerp.com`).
4. **Percaya slug dari form ketika host sudah menyebut tenant.** `/google` menolak (403) slug yang
   bertentangan dengan host.
5. **Proxy dev menulis ulang `Host`.** `webpack devServer.proxy` memakai `changeOrigin: true`, jadi
   di dev server selalu melihat `localhost` (permukaan `Local`). Pengikatan host di server
   dibuktikan lewat **test HTTP**, bukan lewat browser dev.

---

## 🧪 6. Cara Membuktikan Kodingan Kita Bekerja

- **Domain murni**: `HostSurfaceTest` (5 test: platform, tenant non-garmen `bordir-uji`, label
  terlarang/salah, lokal, domain tiruan). `AuthenticateWithGoogleUseCaseTest` (+4: tanpa slug →
  tenant TRIAL dari akun, email asing, tenant SUSPENDED, slug tenant TRIAL).
- **HTTP**:
  - `SessionHandoffRoutesTest` (8): terbit, tanpa sesi = 401, superadmin = 400, tukar di subdomain
    sendiri = 200 lalu **replay = 401**, subdomain lain = 401, host platform = 403, akun dinonaktifkan
    = 403, token sesi bukan tiket = 401.
  - `PublicAuthHostSurfaceTest` (4): `/google` di `app.` tanpa slug, di `<slug>.` tanpa slug, slug
    bertentangan = 403, lokal tanpa slug = 400 seperti dulu.
- **Cek mata** (stack uji `PORT=8091 PLATFORM_BASE_DOMAIN=lvh.me`, web
  `WEMADE_WEB_PORT=3011 WEMADE_API_PORT=8091`):
  - `app.lvh.me:3011/login` tanpa kolom slug;
  - tiket dari `/handoff/issue` → `bordir-uji.lvh.me:3011/login?handoff=…` → mendarat di `/org-chart`
    sebagai owner bordir dengan persona aktif, dan tiket hilang dari URL;
  - tiket yang sama dibuka lagi → "Tautan masuk tidak berlaku lagi" + tag "Workspace: bordir-uji".

---

## 🏆 7. Tantangan Mandiri

- [ ] Pindahkan memori `jti` ke tabel `auth.used_handoff_tickets` (TTL 60 detik), lalu tulis test
      dua instance service yang berbagi repository.
- [ ] Rancang pemilih tenant di `app.` untuk kolaborator yang diundang ke banyak tenant. Mulai dari
      `UserRepository.findAllByEmail` dan ubah `authenticateOnPlatform` agar mengembalikan daftar.
- [ ] Ganti teks header "Sistem Manajemen Konveksi & Garmen Terpadu" di `app.` dengan teks platform
      netral industri (CLAUDE.md Jalur B poin 4). Ingat Ratchet `LoginScreen.kt` (≤711 baris).

---

## 📎 Lampiran — Pintu platform sendiri (`PlatformLoginScreen`) & pendaratan ke Builder

- `app.<base>` kini merender `PlatformLoginScreen` (brand **WeMake ERP**, netral industri). `LoginScreen`
  tetap menjadi pintu workspace tenant. Pemilihnya satu cabang di `App.kt`:
  `if (authState.hostSurface is HostSurface.Platform)`.
- Handoff mendarat di **`/builder`** (`AuthViewModel.HANDOFF_LANDING_PATH`), bukan `/login`. App membaca
  `/builder?handoff=…`, menukar tiketnya, lalu `builderRoute && isAuthenticated` merender `BuilderShell`.
  Pendaratan otomatis ke modul pertama **dilewati** bila `builderRoute`. Kalau tidak dilewati, user
  dilempar keluar dari Builder.
- Origin tujuan disusun di klien dari halaman saat ini (`tenantOriginFromHere`) supaya skema dan port
  ikut: `http://bordir.lvh.me:3001` di dev, `https://bordir.wemakeerp.com` di produksi. `origin` dari
  server hanya cadangan.
- Tombol demo Owner di `app.` juga melewati handoff. Demo Superadmin tetap di platform.
- Cek mata: `app.lvh.me:3011/login` → klik "Owner wemade-demo" → `wemade-demo.lvh.me:3011/builder`
  menampilkan Builder Overview.

---

## 📎 Lampiran 2 — Konsol platform superadmin (`app.<base>/admin`) & act-as ber-audit (discovery-M3b)

**Mental model**: superadmin = **operator platform**, bukan pengguna aplikasi hasil. Rumahnya `app./admin`;
ia masuk ke Builder **atau** aplikasi tenant lewat act-as, dan setiap act-as tercatat di audit log
**tenant tujuan** (`PLATFORM_ACT_AS_STARTED`), sehingga owner bisa melihatnya.

- **Server**
  - `GET /api/admin/tenants` (`PlatformTenantListRoutes.kt`). Gate-nya prefix `/api/admin` di
    `TenantResolutionPlugin`, tidak ada pintu baru.
  - `/handoff/issue` menerima `actAs=<slug>`:
    - owner yang mengirimnya → 403;
    - superadmin tanpa `actAs` → 400;
    - audit **gagal dicatat → 503, masuk dibatalkan** (fail-closed). Act-as tanpa jejak melanggar
      syarat yang membuatnya diizinkan.
  - Tiket membawa klaim `act_as`. Penukar mewajibkan peran superadmin, lalu menambatkan sesi ke tenant
    tujuan (`user.copy(tenantId = tenant.id)`, tidak disimpan ke DB).
- **Klien**
  - `PlatformAdminConsole` (Tenants · Antrian Pembuatan · Buku Demand). Memakai ulang
    `BuilderSidebar` (kini `internal` + `title`), `BuilderBuildQueuePane`, `DemandLedgerScreen`, dan
    `TenantModuleEntitlementDialog`.
  - `App.kt`: `shellPath` menggantikan `builderRoute`. `landAfterLogin` mengirim superadmin di permukaan
    Platform ke `/admin`.
- **Test**
  - `SessionHandoffRoutesTest` (12): audit tercatat di tenant tujuan dan sesi tertambat; owner +
    `actAs` = 403 tanpa audit; tenant tak dikenal = 403; tiket act-as di subdomain lain = 401.
  - `AdminApiTest` (+2): daftar tenant ditolak untuk owner (403) dan lengkap untuk superadmin.
- **Cek mata**:
  1. `app.lvh.me:3011` → Superadmin → `/admin`.
  2. Cari "bordir" → Masuk Builder → `bordir-uji.lvh.me:3011/builder`.
  3. Audit log `bordir-uji` memuat entri `platform_act_as_started`.
