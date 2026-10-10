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
