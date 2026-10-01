# Teaching: Jam Trial Tenant (V88) — label tanpa tenggat → trial sungguhan

Rujukan: `docs/trd/TRD-PAY-001` §6 · diskusi: "trial pakai Baserow/Airtable, perlu admin dashboard?"

## 1. Masalah yang ditemukan

`TenantStatus.TRIAL` **sudah ada sejak V1** dan jadi status default tenant baru — tapi **tanpa
tenggat dan tanpa sanksi**. Efeknya: setiap tenant baru mendapat paket `PRO` penuh **selamanya**,
karena tidak ada yang menutupnya. Masalahnya bukan "butuh dashboard", melainkan trial belum punya
jam.

## 2. Yang dibangun (Lapis 1 — penegakan di core)

- **V88**: `tenants.trial_ends_at TIMESTAMPTZ` dengan **DEFAULT di DB**
  (`now() + interval '14 days'`) — tenant baru otomatis punya tenggat bahkan lewat jalur insert
  lama. NULL = tenant legacy tanpa jam (status quo, tidak dipaksa).
- **Domain** (`Tenant.kt`): `trialExpired(now)`, `extendTrial(days, now)` — basis perpanjangan
  adalah `max(now, tenggat lama)` supaya menumpuk di ujung, bukan memotong sisa; menolak di luar
  status TRIAL. Konversi ke bayar = `activate()` + alur invoice yang sudah ada.
- **Registrasi**: `RegisterTenantUseCase` memasang jam saat daftar (`clock` + `trialDays`
  di command, keduanya ber-default — **pemanggil lama dan `Application.kt` tidak tersentuh**).
- **Endpoint superadmin** (di `AdminRoutes`, prefix `/api/admin` — platform, tanpa konteks tenant):
  - `GET /api/admin/trials` — daftar + `remainingDays` + flag `expired` (**ini "dashboard"-nya**).
  - `POST /api/admin/trials/{slug}/extend?days=N` — ter-audit (`TENANT_TRIAL_EXTENDED`).

## 3. Pelajaran

1. **Penegakan tidak bisa disematkan ke spreadsheet.** Baserow/Airtable hanya bisa jadi lapisan
   keputusan/CRM; yang memblokir login dan menghitung kuota harus kode core + DB (fail-closed).
   Kalau mau papan visual: Baserow self-host sebagai tabel keputusan → memanggil endpoint
   superadmin ini — ERP tetap satu-satunya sumber kebenaran. Airtable (SaaS per-seat) hanya untuk
   CRM murni.
2. **Default di DB vs di kode**: menaruh default `trial_ends_at` di migrasi membuat aturan bertahan
   bahkan untuk jalur tulis yang lupa mengisinya; menaruhnya juga di use case (jam eksplisit)
   membuatnya bisa diuji tanpa DB. Keduanya dipakai.
3. **Param ber-default = perubahan tanpa gelombang**: menambah `clock` dan `trialDays` di *posisi
   terakhir* dengan default membuat `Application.kt` (sedang dipegang alur lain) tidak perlu
   disentuh sama sekali.
4. **Koordinasi lintas-alur**: penegakan "trial habis → tolak login" titik sambungnya di jalur
   auth yang sedang dikerjakan alur M3 — sengaja ditunda, tercatat di sini; tinggal satu kondisi
   `trialExpired(now)` di titik yang sama dengan cek `isAccessible`.
