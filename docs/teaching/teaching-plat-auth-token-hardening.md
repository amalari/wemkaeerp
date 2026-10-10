# Teaching: Pengerasan Token Auth (P2) — fail-closed di penerbitan dan pembacaan

## Konteks
Gerbang `/demo` (P0/P1) sudah menutup pintu depan. Temuan P2 adalah lubang kecil di dalamnya: token
yang dapat terbit dalam bentuk yang tidak akan diterima gerbang, role tak dikenal yang diberi wewenang
terluas, JSON yang dirakit dari string mentah, dan id persona yang bertabrakan antar tenant.

## Step 0 — Prinsip: gagal tegas di sumber, bukan di hilir
Token tenant-bound tanpa `tenant_slug` dulu bisa diterbitkan jalur Google (slug kosong diubah jadi
`null`). Plugin menolaknya di tiap request, jadi pengguna melihat sesi "login berhasil tapi semua 401".
Sekarang `JwtTokenService.generateToken` memakai `require`: akun selain `PLATFORM_SUPERADMIN` wajib
membawa slug. Jalur Google memeriksa dulu dan menjawab 403 "Tenant akun tidak dapat ditentukan".
Semua penerbit lain (demo, persona, handoff, act-as) sudah selalu membawa slug dari tenant yang
ditemukan. `/me` juga tidak lagi mengarang `wemade-demo` bila slug hilang (401 untuk akun tenant).

## Step 1 — Role tak dikenal: tolak, jangan naikkan
`TenantResolutionPlugin` memakai `?: Role.TENANT_ADMIN`. Identitas yang tak terbaca justru mendapat
wewenang terluas. Dipilih **403**, bukan OPERATOR: token yang sah selalu membawa role valid dari
`generateToken`, jadi role asing berarti token ditempa/korup/versi lain — tidak ada alasan melayaninya
sama sekali. `/me` tetap OPERATOR karena hanya menampilkan identitas, tidak memberi akses data.

## Step 2 — JSON: serialisasi, bukan penyambungan string
`authSessionJson` kini memakai `buildJsonObject`. `departmentId` bebas diisi klien persona; satu tanda
kutip di sana dulu merusak JSON (atau menyuntikkan field). Fungsi dipindah ke `AuthSessionJson.kt`
(pemecahan per tanggung jawab; `PersonaUserResolver.kt` juga) agar `PublicAuthRoutes.kt` mengecil.

## Step 3 — Id persona unik per tenant
`users.id` adalah PK global; slug persona hanya unik per tenant (email-nya memuat slug tenant). Dua
tenant dengan persona "Budi" akan bertabrakan di `usr-persona-budi`. Id baru:
`usr-persona-<slug>-<hash8(tenantId)>` (<= 64 karakter, hash tidak pernah terpotong). Persona lama
aman karena pemanggil memakai `existing.id` yang ditemukan lewat email.

## Jebakan
- Tes lama menerbitkan token tenant tanpa slug lewat `generateToken(user)`; kini harus menyebut slug.
- Mengubah role fallback ke OPERATOR di plugin bukan "paling aman": ia tetap melayani token rusak.

## Tes
`JwtTokenServiceTenantSlugTest`, `AuthSessionJsonTest`, dan
`TenantResolutionPluginTest.tokenWithUnknownRole_shouldReturn403_notTenantAdmin`.
