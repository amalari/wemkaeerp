# Teaching: Jam Trial Aplikasi (V88+V89) — builder gratis, trial mulai saat go-live

Rujukan: `docs/trd/TRD-PAY-001` §6 · diskusi: "trial pakai Baserow/Airtable, perlu admin dashboard?"

## 1. Masalah & koreksi semantik (V88 → V89)

`TenantStatus.TRIAL` **sudah ada sejak V1** dan jadi status default tenant baru — tapi **tanpa
tenggat dan tanpa sanksi**: setiap tenant mendapat paket `PRO` penuh selamanya.

V88 menambalnya dengan jam `tenants.trial_ends_at` (default DB `now()+14d`, dipasang saat
registrasi) dan deploy lama langsung mempromosikan `TRIAL → ACTIVE`. **Keduanya salah moment**:
yang dibeli bukan akun Builder, melainkan **aplikasi jadinya**. Semantik yang benar:

| Fase | Status | Jam trial |
|---|---|---|
| Daftar, membangun di Builder | `TRIAL`, `trialEndsAt = NULL` | tidak berjalan — **builder gratis selamanya** |
| **Deploy pertama sukses** (go-live) | tetap `TRIAL` | **mulai**: `now + 14 hari` (deploy ulang tidak mengatur ulang) |
| Trial berjalan | `TRIAL` | `GET /api/admin/trials` → `trialStarted: true`, `remainingDays` |
| Bayar dikonfirmasi (iPaymu) | `ACTIVE` | selesai — langganan berjalan |
| Trial habis, belum bayar | `TRIAL` + `expired: true` | penegakan login menyusul (titik sambung M3) |

## 2. Yang dibangun

- **V88**: kolom `tenants.trial_ends_at`. **V89**: default DB **dihapus** (jam hanya boleh
  dipasang use case deploy) + reset NULL untuk tenant TRIAL yang keburu terisi default tapi
  belum pernah deploy (`builder.deployments` ACTIVE tidak ada).
- **Domain** (`Tenant.kt`): `trialExpired(now)`, `extendTrial(days, now)` — menumpuk di ujung
  tenggat (`max(now, lama)`), menolak di luar TRIAL; `Tenant.DEFAULT_TRIAL_DAYS = 14`;
  konversi ke bayar = `activate()`.
- **`DeployTenantUseCase`**: di path deploy ACTIVE, `trialEndsAt = trialEndsAt ?:
  (now + 14 hari)` — idempoten; deploy ulang tidak mengatur ulang. Path `BLOCKED_ON_BUILD`
  (pack kustom) **tidak** memulai jam — app belum jadi. Registrasi sengaja tidak menyentuh jam.
- **Endpoint superadmin** (`AdminRoutes`, prefix `/api/admin` — platform, tanpa konteks tenant):
  `GET /api/admin/trials` (papan pantau, kini dengan `trialStarted`) dan
  `POST /api/admin/trials/{slug}/extend?days=N` (ter-audit `TENANT_TRIAL_EXTENDED`).

## 3. Pelajaran

1. **Moment-of-truth menentukan tempat jam dipasang.** Versi pertama menaruh jam di registrasi
   karena di sanalah entitasnya lahir — padahal nilai yang dijual (aplikasi jadi) lahir di deploy.
   Tanya "kapan nilai ini mulai dikonsumsi?", bukan "kapan datanya dibuat?".
2. **Default di DB enak untuk backstop, berbahaya untuk kebijakan yang moment-nya spesifik** —
   V88 → V89 adalah harga salah memilih moment. Kalau ragu, biarkan NULL dan pasang di use case
   yang tahu konteksnya.
3. **Penegakan tidak bisa disematkan ke spreadsheet.** Baserow/Airtable hanya lapisan keputusan
   (tabel `slug | aksi | catatan` → memanggil endpoint superadmin); yang memblokir login dan
   menghitung kuota tetap kode core + DB (fail-closed). Airtable (SaaS per-seat) hanya CRM murni.
## 4. Penegakan server-side (gerbang trial, V89 lanjutan)

Titik pemasangan: **`TenantResolutionPlugin`**, sebelah cek `isAccessible` (suspended 403) —
bukan di jalur auth, supaya tidak bersengketa dengan perombakan login.

- `status == TRIAL && trialExpired(now)` pada tenant yang **di-resolve** → workspace ditolak
  **402 PaymentRequired** dengan pesan mengarah ke builder console.
- **Tiga pintu tetap terbuka dengan sengaja**:
  1. **`/api/builder/*`** (`builderRoutePrefixes`) — tenant expired harus bisa masuk melihat
     invoice & berlangganan; mengunci builder = tenant tidak punya cara bayar.
  2. **Superadmin act-as** (`isPlatformSuperadmin`) — tetap bisa mengaudit tenant mati.
  3. **Rute publik & platform** (`/api/payment`, `/api/admin`) — tidak melewati resolusi tenant.
- Konfigurasi plugin dapat `clock` beku untuk pengujian deterministik.
- Test (`TenantResolutionPluginTest`): expired → 402 di workspace; TRIAL berjalan → 200;
  expired di builder → lolos (404 dari routing, bukan 402 dari gerbang); superadmin act-as
  ke tenant expired → 200.
- Catatan verifikasi: `platformSuperadmin_shouldBeAbleToActAsAnyTenant` gagal
  (`UncompletedCoroutinesError`) **juga pada HEAD bersih** — bawaan kode M3, bukan gerbang
  trial; tercatat sebagai baseline terpisah.

