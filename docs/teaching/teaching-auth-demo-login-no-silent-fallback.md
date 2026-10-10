# Login demo tanpa fallback senyap

## Masalah
`AuthViewModel.handleDemoLogin` menangkap galat APA PUN dari `loginDemo` (termasuk 403 "bukan tenant
demo" dan 404 "login demo dimatikan" dari gerbang server) lalu membuat sesi offline palsu
(`usr-owner-001`, token `jwt-offline-token-N`). Akibatnya penolakan server tertutup, UI mengaku
"Mode Demo Offline", dan semua endpoint kemudian menolak token palsu itu. Jalur persona sama.
Slug kosong juga diam-diam menjadi `wemade-demo`.

## Keputusan
1. **Sesi offline dihapus seluruhnya, termasuk untuk jaringan mati.** Bukti: token offline tidak pernah
   diterima server (`teaching-plat-002-m0-builder-foundation.md` butir 10 mencatatnya sebagai
   penghalang cek visual); tak ada test, skrip, atau dokumen yang bergantung pada "Mode Demo Offline"
   (grep seluruh repo); tanpa server, tak ada layar yang berguna. Jaringan mati kini tampil sebagai
   `FriendlyErrors.UNREACHABLE`, sama seperti cabang host Platform dan login Google.
2. **Galat bertipe** (`AuthApiError`): `Rejected(status, serverMessage)` (server menjawab) vs
   `Unreachable` (tak ada jawaban) vs `Malformed`. Pemanggil bisa membedakan, tidak lagi menebak dari teks.
   404 tanpa isi = "Login demo dimatikan di server ini"; 404 berisi (tenant tidak ada) dan 403 memakai
   pesan server apa adanya.
3. **Slug kosong ditolak**, bukan diganti default: "Pilih/isi kode pabrik terlebih dahulu." tanpa request
   (Kontrak 4 tenant-variability: tolak, bukan fallback senyap).

## Pelajaran
- `catch`/`onFailure` yang membuat data pengganti = fallback senyap; pesan kegagalan harus mengatakan
  apa yang terjadi, dan kegagalan tidak boleh menulis storage sesi.
- Pisahkan "server menolak" dari "server tak terjangkau" di lapisan infrastruktur; ViewModel hanya memetakan.
- Tes: `DemoLoginNoSilentFallbackTest` (slug `bordir-uji`) menegaskan tidak ada sesi dan tidak ada tulis storage.

## Sisa `wemade-demo` (belum diubah, di luar cakupan)
- `LoginUiState.tenantSlug` default (pra-isi kolom, bukan fallback; pengguna bisa mengosongkan).
- `AuthViewModel` pemulihan sesi tersimpan (`restoredSession.tenantSlug ?: "wemade-demo"`) dan
  `restorePersonaFrom` (`session.tenantSlug ?: "wemade-demo"`): sesi server selalu membawa slug, tetapi
  fallback senyapnya tetap ada.
- `LoginScreen.kt` placeholder "contoh: wemade-demo" (teks bantuan saja).
- Server `PublicDemoAuthRoutes`: `field("tenantSlug") ?: "wemade-demo"` saat param tak dikirim.

## Putaran 2: audit sisa fallback `wemade-demo` / `ten-default`

Audit `grep 'wemade-demo\|ten-default'` di `core`, `app/shared`, `server` (src/main). Tiap temuan
diklasifikasi: (1) fallback senyap berbahaya -> tolak / keadaan "tenant belum dipilih"; (2) default sah -> biarkan.

| Lokasi | Tindakan | Alasan |
|---|---|---|
| `AuthViewModel` pemulihan sesi (`?: "wemade-demo"`, `?: TenantId("ten-default")`) | (1) diganti | Sesi tersimpan tanpa slug (bukan superadmin platform) dianggap tidak valid: storage dibersihkan, kembali ke login dengan `MSG_SESSION_NO_TENANT`. Superadmin tanpa tenant tetap masuk, tanpa `TenantSession` tebakan |
| `AuthViewModel.restorePersonaFrom` | (1) diganti | Tanpa slug eksplisit tidak ada persona (sama seperti tenantId null) |
| `AuthViewModel` login demo + `applyVerifiedSession` (`ten-default`) | (1) diganti | Satu helper `storeTenantSession`: `TenantSession` hanya ditulis bila tenantId DAN slug ada; selain itu storage dikosongkan |
| `PublicDemoAuthRoutes` `field("tenantSlug") ?: "wemade-demo"` | (1) diganti | Tanpa `tenantSlug` -> 400 "Parameter tenantSlug wajib diisi". Gerbang mati tetap 404 lebih dulu |
| `App.kt` OrgChart/FactoryFlow/Traceability, dialog entitlement | (1) diganti | `TenantBound(slug)` menampilkan "Pilih tenant terlebih dahulu" alih-alih memuat data tenant demo; dialog entitlement (menulis!) tidak dibuka tanpa slug |
| `App.kt` Fulfillment (via persona) | (1) diganti | Layar menerima `tenantSlug` eksplisit dari `TenantBound` |
| `ModuleWorkspaceScreen` `resolvedSlug` | (1) diganti | Persona tanpa slug -> `TenantNotSelectedView` |
| `FactoryFlowScreen` parameter default | (1) diganti | `tenantSlug` wajib; label perusahaan memakai slug sendiri bila bukan profil demo |
| `CompanyTenantProfile.findBySlug/findByPreset` (`?: ALL.first()`) | (1) diganti | Mengembalikan null; tenant tak dikenal tidak lagi tampil sebagai "PT WeMade Garmen Ekspor" |
| `AppTopBar` (`?: "wemade-demo"`) | (1) diganti | Persona switcher tidak tampil tanpa slug; company switcher menerima string kosong = belum ada tenant terpilih |
| `LoginUiState.tenantSlug` default, `LoginScreen` placeholder | (2) dibiarkan | Pra-isi kolom dan teks bantuan; pengguna bisa mengosongkan, dan slug kosong ditolak |
| `PublicDemoAuthRoutes.DEFAULT_DEMO_TENANTS` | (2) dibiarkan | Tenant demo resmi di `DemoLoginPolicy` (gerbang) |
| `InMemoryTenantRepository`, `InMemoryTenantPipelineRepository` | (2) dibiarkan | Seed in-memory untuk dev/test, bukan jalur keputusan |
| `DynamicRbacViewModel` KDoc | (2) dibiarkan | Komentar riwayat |

Pelajaran: fallback yang "aman karena sesi server selalu punya slug" tetap berbahaya, karena
kasus tanpa slug yang sah (superadmin platform) lalu diam-diam membaca/menulis data tenant demo.
Tes: `DemoLoginNoSilentFallbackTest` (restore), `DemoAuthGateApiTest` (400 tanpa slug, `bordir-uji-gate`),
`TenantBoundTest`.

## Tambahan: tombol demo yang membungkus, dan "Sesi tidak valid" di `*.lvh.me`

**Tombol "Owner <slug>" di login Platform.** Di 360dp, label `Owner wemade-demo` membungkus jadi dua baris
sehingga tombol lebih tinggi dari "Superadmin". Perbaikannya satu parameter: `maxLines = 1` pada
`PlatformDemoButton` (`ClayButton` sudah memberi `overflow = Ellipsis`). Slug panjang kini terpotong
(`Owner wemade...`), tinggi kedua tombol seragam. Ini Kontrak 13 design system: elemen yang boleh mengalah
dinyatakan eksplisit, bukan dibiarkan membungkus.

**Penyelidikan "Sesi tidak valid" di `wemade-demo.lvh.me`.** Tidak dapat direproduksi pada kode terkini
(server `PLATFORM_BASE_DOMAIN=lvh.me`, web via proxy `/api`): login Owner dan Superadmin, reload (`/me`),
handoff act-as ke tenant host, semuanya 200. Fakta yang menjelaskan laporan lama:

- Sesi di `localStorage` per **origin termasuk port** (`wemade-demo.lvh.me:3011` != `:3012` != `app.lvh.me:3011`);
  tidak ada cookie (`Set-Cookie` kosong), jadi tidak ada masalah SameSite/domain.
- Klien memakai URL relatif `/api/...` lewat proxy webpack (`WEMADE_API_PORT`); `devPort = 8081` di
  `TenantApiEndpointResolver` tidak dipakai jalur ini (hanya tes).
- Tanpa `PLATFORM_BASE_DOMAIN` di server, host `*.lvh.me` dianggap mode lokal (layar login Platform, bukan
  `LoginScreen` tenant) - perilaku berbeda, bukan galat.
- Sebab sah penolakan setelah hardening: token lama tanpa `tenant_slug` untuk peran tenant ditolak `/me`
  (`PublicAuthRoutes.kt` "Token tidak memuat tenant"; UI: "Sesi telah kedaluwarsa"); token bertanda tangan
  `JWT_SECRET` lain ditolak `TenantResolutionPlugin` ("Sesi tidak valid atau sudah kedaluwarsa"). Keduanya
  disengaja (fail-closed); sesi lama dibersihkan dengan login ulang.
- Jebakan alat: satu browser Playwright dipakai bersama beberapa agen, sehingga halaman bisa berpindah ke
  port agen lain di tengah uji. Pakai `browser.newContext()` sendiri.

## Pesan sesi lama yang ramah (verifikasi sesi tersimpan)

`/api/public/auth/me` menjawab 401 "Token tidak memuat tenant" untuk sesi tersimpan sebelum token wajib-slug. Klien dulu
menampilkan "Sesi telah kedaluwarsa" untuk semua 401. Membedakannya lewat teks pesan rapuh, jadi server kini menambah header
`X-Auth-Reason: token_without_tenant` (konstanta `AuthRejectionReason` di core), `verifySession` mengembalikan
`AuthApiError.Rejected(reason=...)`, dan `AuthViewModel.sessionRejectedMessage` memetakan kode itu ke "Sesi lama tidak
lagi berlaku. Silakan masuk ulang."; 401 lain tetap "kedaluwarsa". Jaringan mati pada verifikasi kini bertipe
`Unreachable` (perilaku hapus-sesi tidak diubah). Tes: `SessionRestoreRejectionTest` (slug `bordir-uji`).
