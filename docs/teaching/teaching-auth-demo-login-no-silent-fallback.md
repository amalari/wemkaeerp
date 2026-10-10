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
