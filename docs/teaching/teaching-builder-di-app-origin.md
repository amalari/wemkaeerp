# 🎓 Modul Pembelajaran: Builder di `app.<base>`, Subdomain Tenant untuk Aplikasi Hasil Generate

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Host surface, origin browser & storage, act-as superadmin ber-audit, fail-closed endpoint
> **Prasyarat**: Paham `HostSurface` ([HostSurface.kt](../../core/src/commonMain/kotlin/com/eventverse/app/domain/tenant/HostSurface.kt)) dan alur handoff sesi (`SessionHandoffRoutes`)
> **Referensi Task**: Keluhan "Masuk Builder dari konsol tenants lama banget / ngelag"; lanjutan PLAN-builder-console §2

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: "Masuk Builder" membuka `<slug>.lvh.me/builder`. Itu **origin lain**, jadi browser mengunduh dan mengompilasi ulang seluruh aplikasi Compose/Wasm (dua file `.wasm` ±19 MB, 59 MB resources di DevTools). Server sendiri cepat (10–60 ms per request); lag-nya ada di pemuatan bundel.
- **Analogi**: pindah dari satu gedung kantor ke gedung lain hanya untuk membuka satu ruangan, padahal ruangannya ada di gedung yang sama. Cache, localStorage, dan sesi tidak ikut pindah gedung.
- **Keputusan produk**: Builder adalah alat pemilik/operator, tempatnya di `app.<base>`. Subdomain tenant adalah **hasil generate** (aplikasi ERP tenant). Pemilik tenant boleh login ke Builder atau langsung ke aplikasinya.

## 🧭 2. "Start dari Mana?" — Urutan Penulisan

1. **Baca server dulu**: `TenantResolutionPlugin` ternyata sudah menentukan tenant dari JWT / `X-Tenant-Slug`, bukan dari host. Jadi server nyaris tidak perlu berubah — ini menentukan seluruh sisa rencana.
2. **Server**: satu endpoint baru `POST /api/admin/tenants/{slug}/act-as` ([PlatformActAsRoutes.kt](../../server/src/main/kotlin/com/eventverse/app/routes/PlatformActAsRoutes.kt)).
3. **Klien API**: `AuthApiClient.actAsSession`.
4. **ViewModel**: `actAsBuilder` (tetap di origin), `openTenantApp` (handoff ke subdomain), login tenant di `app.` tidak lagi di-handoff.
5. **UI**: tombol "Buka Aplikasi" dan "Konsol Platform" di `BuilderShell`; wiring di `App.kt`.
6. **Test** peran tak berwenang (403), lalu dokumentasi.

## 🧱 3. Bedah Kode

### Blok A — Endpoint act-as tanpa tiket

```kotlin
val principal = call.callerPrincipalOrNull
if (principal == null || !principal.isPlatformSuperadmin) { 403 }
...
auditLogRepository.record(PLATFORM_ACT_AS_STARTED).onFailure { 503; return@post }
val sessionUser = user.copy(tenantId = tenant.id)          // tidak disimpan ke DB
val token = jwtTokenService.generateToken(sessionUser, tenant.slug.value)
```

- Gerbang utama ada di plugin (`/api/admin` = hanya superadmin). Pemeriksaan **kedua** di route membuatnya tetap fail-closed bila route dipasang di tempat lain.
- **Audit dulu, token kemudian.** Gagal mencatat = masuk dibatalkan; izin act-as hanya sah karena ada jejaknya.
- Tiket handoff dipakai karena sesi harus menyeberang origin. Di origin yang sama tiket tidak berguna — server langsung mengembalikan sesi.

### Blok B — Login pemilik di `app.` tidak lagi dilempar ke subdomain

Sebelumnya `handOffToTenant` dipanggil setelah login. Sekarang `applyVerifiedSession(..., restorePersona = true)` menyimpan sesi di origin `app.`, dan `landAfterLogin` (surface bukan `Tenant`) mendaratkan ke `/builder`. `restorePersona = true` wajib, karena tanpa persona wewenang kosong.

### Blok C — `announce = false` pada act-as

`applyVerifiedSession` biasanya memancarkan `NavigateToDashboard`, yang di `App.kt` memicu `landAfterLogin` (superadmin di `app.` → `/admin`). Untuk act-as kita ingin `/builder`, jadi pemancaran dimatikan dan navigasi dilakukan pemanggil (`goShell("/builder")`).

### Blok D — Buka aplikasi dari Builder

`openTenantApp()` memakai handoff yang sudah ada. Superadmin wajib menyebut `actAs=<slug>` (server menolak tanpa itu); pemilik memakai tenant akunnya.

## ⚖️ 4. Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Kenapa | Risiko alternatif |
|---|---|---|---|
| Sesi act-as langsung di origin `app.` | Tetap handoff tiket ke subdomain | Tidak ada muat ulang 19 MB Wasm; satu origin, satu cache | Lag yang sama kembali |
| Endpoint baru di `/api/admin` | Longgarkan `redeem` agar jalan di host platform | Redeem sengaja menolak host platform; melonggarkannya melemahkan pagar tiket | Tiket bisa dipakai di permukaan yang salah |
| Audit sebelum token | Audit sesudah / best-effort | Fail-closed: tak ada jejak, tak ada masuk | Act-as tanpa jejak |

## ⚠️ 5. Jebakan Pemula

1. **Mengira lag itu soal server.** Lihat Network tab dulu: request API 10–60 ms, bundel Wasm yang berat. Ukur sebelum mengoptimasi.
2. **Origin ≠ "situs yang sama".** `app.lvh.me` dan `x.lvh.me` tidak berbagi localStorage atau cache.
3. **Efek samping `NavigateToDashboard`.** Memakai ulang fungsi login untuk alur lain membawa navigasi bawaannya.
4. **Menambah baris ke `App.kt`.** File ini sudah mendekati hard limit (aturan Ratchet): satu helper `goShell` menggantikan tiga pengulangan sehingga file tidak lebih panjang (593 → 594 → dicek ulang di akhir).
5. **Lupa peran tak berwenang di test.** Pemilik tenant yang act-as ke tenantnya sendiri harus tetap 403.

## 🧪 6. Pembuktian

[PlatformActAsRoutesTest.kt](../../server/src/test/kotlin/com/eventverse/app/routes/PlatformActAsRoutesTest.kt): superadmin → 200 + audit + sesi bertambat ke `bordir-uji` (tenant non-default); pemilik → 403 tanpa audit; tanpa sesi → 403; tenant tak dikenal → 404. `SessionHandoffRoutesTest` dan `BuilderRouteGateTest` tetap hijau.

## 🏆 7. Tantangan Mandiri

- [ ] Alihkan `<slug>.<base>/builder` ke `app.<base>/builder` (butuh handoff arah sebaliknya).
- [ ] Tampilkan penanda "Bertindak sebagai <tenant>" permanen di top bar saat sesi act-as aktif.
- [ ] Ukur ulang waktu muat dengan build production + brotli dan bandingkan dengan build dev.
