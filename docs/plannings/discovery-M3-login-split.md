# Discovery Note — Pemisahan Login Platform vs Tenant (PLAN-builder-console §2)

**Tanggal**: 2026-10-01 · **Penulis**: Achmad Jamaludin (dibantu Claude)

## 1. Kebutuhan
- Siapa memakai: owner/kolaborator tenant (login di `<slug>.wemakeerp.com`), calon pelanggan
  (daftar di `app.wemakeerp.com`), superadmin (login di `app.` lalu masuk tenant mana pun).
- Data milik: sesi user (token JWT) — milik user, terikat satu tenant (kecuali superadmin).
- Berubah kapan: per login.

## 2. Fitur serupa
- Perintah: `scripts/find-similar-feature.sh subdomain host login`
- Temuan:
  - Satu `LoginScreen` (716 baris, **utang file-size**, Ratchet) dengan kolom "Subdomain / Kode Pabrik"
    + tag `.wemade.id` hardcode (`LoginScreen.kt:214-232`).
  - `TenantApiEndpointResolver` — `rootDomain = "wemade.id"` hardcode.
  - Server sudah siap: `extractSubdomain` + cek JWT-vs-host + carve-out superadmin
    (`TenantResolutionPlugin.kt:208`).
  - Daftar publik: `OnboardingRoutes.kt` (`/api/public/onboarding/register`, flag `WEMADE_PUBLIC_SIGNUP`).
  - Tiket bertanda tangan berumur pendek: `PrintTicketService` (60 detik, scope path) — pola untuk
    serah-terima sesi.
- Keputusan: **Sudah ada → extend** (login, resolver, onboarding). Serah-terima sesi: **tiru pola
  `PrintTicketService`**.

## 3. Jenis
**Foundation platform (auth), bukan modul** — tidak ada `BusinessModule`, tidak di kanvas,
tidak dihitung kuota. Hanya permukaan auth + routing host.

## 4. Uji Variabilitas
| Konsep | Tenant? | Industri? | Admin ubah? | Kode/Data | Catatan |
|---|---|---|---|---|---|
| Permukaan host (`PLATFORM` vs `TENANT(slug)`) | tidak | tidak | tidak | **Kode** (sealed interface sistem) | diturunkan dari host + base domain |
| Base domain | per deployment | tidak | tidak | **Konfigurasi env** | `PLATFORM_BASE_DOMAIN`, default dev `wemade.id`/localhost |
| Judul/branding login tenant | ya | — | ya | Data (nanti, dari Pengaturan) | MVP: nama tenant dari `/check` publik |

## 5. Core & extend
- Core: `core/.../domain/tenant/` — value object `HostSurface` (parser tunggal host → PLATFORM/TENANT,
  menolak slug terlarang; dipakai klien **dan** server agar `extractSubdomain` tidak terduplikasi).
- Titik extend:
  - `PlatformNavigation` + `getCurrentHost()` (expect/actual; non-web → `null` = mode lama).
  - `TenantApiEndpointResolver` — base domain dari konfigurasi.
  - `PublicAuthRoutes` — `POST /api/public/auth/handoff` (tukar tiket → sesi di subdomain).
  - `LoginScreen` — kolom slug **disembunyikan** bila host = TENANT; di PLATFORM tetap ada
    (atau memilih tenant setelah login).
- Contoh ditiru: `PrintTicketService.kt:22-41`, `TenantResolutionPlugin.kt:290` (`extractSubdomain`).
- Jangan disentuh/ditambah: `LoginScreen.kt` (Ratchet — wajib ≤716 baris; pecah section ke file sendiri),
  `App.kt` (589, hard 600).

## 6. I/O & kanvas
- Tidak ada port, tidak di kanvas, tanpa telemetri.

## 7. Governance
| Operasi | Syarat | Ditolak (dites) |
|---|---|---|
| Login di `<slug>.` | user ber-tenant `slug` atau superadmin | user tenant lain → 403 (sudah ada) |
| Tukar tiket handoff | tiket sah, belum kedaluwarsa, **sekali pakai**, host = tenant tiket | tiket tenant A di host B → 401/403; tiket dipakai ulang → 401 |
| Daftar di `app.` | flag `WEMADE_PUBLIC_SIGNUP` | flag mati → 404/403 (sudah ada) |
- Gate: publik (pra-auth). Entitlement: tidak ada.

## 8. Ukuran → TRD?
- Agregat baru: tidak (value object + use case tiket). Migrasi: mungkin 1 tabel `used_handoff_tickets`
  (anti-replay) — atau in-memory/JTI cache. → **TRD tidak perlu**; cukup lampiran di PLAN-builder-console.

## Keputusan (2026-10-01, user)
1. **Pindah sesi `app.` → `<slug>.` = tiket sekali pakai** (localStorage per origin, sesi tidak ikut
   otomatis). `app.` menerbitkan tiket 60 detik (pola `PrintTicketService`, `jti` sekali pakai) →
   redirect `https://<slug>.<base>/auth/handoff#t=…` (fragment, tidak masuk log server/Referer) →
   klien subdomain `POST /api/public/auth/handoff` → JWT biasa.
2. **Di `app.` tenant ditentukan dari akun**, kolom "Kode Pabrik" hilang. Aman karena hari ini
   `UserRepository.findByEmail` = satu user = satu tenant. Pemilih tenant (kolaborator di banyak tenant)
   ditunda sampai model user mendukungnya.
3. Superadmin login di `app.` tetap di `app.` (tidak di-handoff); masuk tenant lewat act-as yang ada.

## Temuan sampingan
- `AuthenticateWithGoogleUseCase` mewajibkan `TenantStatus.ACTIVE` — tenant TRIAL hasil daftar publik
  tidak bisa login Google. Perlu diputuskan saat implementasi (pakai `Tenant.isAccessible`).
