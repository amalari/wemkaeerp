# PLAN-K3e: Template Selection — Perbaikan Navigasi & Penyelesaian

**Tanggal**: 2026-10-13
**Rencana induk**: [`PLAN-K3-builder-wizard-self-service.md`](PLAN-K3-builder-wizard-self-service.md) §5.1 (K3e)
**TRD**: [`TRD-K3-builder-wizard-followup-ui.md`](../../trd/mvp/TRD-K3-builder-wizard-followup-ui.md) §2 FR-1..FR-4
**Status**: Plan · **Siap kerjakan** (temuan diverifikasi ke kode 2026-10-13)

---

## 0. Ringkasan Verifikasi (jawaban atas "katanya K3e belum")

**Benar, K3e belum selesai** — bukan karena layarnya tidak ada, melainkan karena **alur
template → chat tidak pernah berjalan**. Layar `BuilderNewPackScreen` sudah ditulis lengkap
(commit `b2474066`, `03f8661a`), tetapi setiap klik template mendarat di panel **Overview**,
bukan Chat. Jadi FR-3/FR-4 TRD secara literal **gagal**.

Dokumen saat ini saling bertentangan dan itu bagian dari masalah:

| Sumber | Klaim | Kenyataan |
|---|---|---|
| `PLAN-K3` §6 tabel task | `K3e = ✅` | ❌ tidak berfungsi |
| `TASKS-SUMMARY.md:52` | `K3e = TINGGI (belum ⏳)` | ✅ ini yang benar |
| TRD-K3 §8 DoD | 4 kotak belum dicentang | ✅ konsisten dengan kenyataan |

### Bukti (As-Is)

**Layar ada** — `app/shared/.../presentation/builder/BuilderNewPackScreen.kt`:
- `BuilderNewPackScreen.kt:159` — kartu "Template-based" → `onNavigate("chat?template=wizard")`
- `BuilderNewPackScreen.kt:197` — tiap template → `onNavigate("chat?template=${template.id}")`
- `BuilderNewPackScreen.kt:49-92` — 6 template (fob, cmt, sablon, bordir, rajut, custom) lengkap

**Rantai navigasi putus** — `App.kt:337-339`:
```kotlin
BuilderShell(
    section = builderSection(shellPath),          // "/builder/chat?template=fob"
    onSectionChange = { key -> goShell("/builder/$key") })
```

**Param template dibaca dari sumber yang salah** — `BuilderChatPane.kt:93`:
```kotlin
templateParam = PlatformHost.queryParameter("template")
```
`PlatformHost.queryParameter` membaca `window.location.search` (`PlatformHost.wasmJs.kt:8,22`),
**bukan** route internal Builder. Di `androidMain`/`iosMain`/`jvmMain` ia **selalu `null`**
(`PlatformHost.android.kt:6`, `.ios.kt:6`, `.jvm.kt:6`).

---

## 1. Akar Masalah (3 titik, semuanya harus ditutup)

### RC-1 — Kunci menu tidak pernah cocok karena query ikut terbawa

`builderSection` tidak memotong query (`BuilderNav.kt:22`):

```kotlin
fun builderSection(path: String): String = path.removePrefix("/builder").trim('/').ifEmpty { "overview" }
// "/builder/chat?template=fob" -> "chat?template=fob"
```

Lalu `BuilderShell.kt:94` mencari kunci menu persis:

```kotlin
val selected = remember(section) { builderMenu().firstOrNull { it.key == section }?.key ?: "overview" }
// "chat?template=fob" tidak ada di menu -> jatuh ke "overview"
```

**Akibat**: `goShell("/builder/chat?template=fob")` menampilkan **Overview**, dan `when(selected)`
tidak pernah masuk cabang `"chat"` (`BuilderShell.kt:116`), sehingga `BuilderChatPane` — beserta
`LaunchedEffect` penyemai template — **tidak pernah berjalan**.

### RC-2 — Router Builder membaca query dari URL browser, bukan dari route internal

`onNavigate("chat?template=X")` mengubah route internal (`shellPath`), bukan `window.location.search`.
Akibatnya `PlatformHost.queryParameter("template")` tetap `null` (kecuali kebetulan URL browser
mengandung `?template=`). Di WasmJs, `history.pushState` memang menulis `search` ke URL, jadi
**hanya setelah reload manual di web** template terbaca — persis gejala "kadang jalan kadang tidak".
Di Android/iOS/Desktop tidak akan pernah jalan.

### RC-3 — "Mulai chat dengan blueprint/template" belum terwujud

FR-3 TRD menyebut template → chat **dengan blueprint**. Kenyataan: `BuilderChatPane.kt:100-146`
hanya menyemai **teks prompt** hardcoded (`"Saya ingin membuat pack untuk konveksi FOB..."`).
Tidak ada parameter blueprint di `POST /api/builder/chat/runs` (`BuilderApiClient.kt:77`,
`BuilderChatUseCases.kt:49`), dan blueprint hanya lahir dari `proposedDraft.blueprint` agent.
Ini keputusan cakupan yang harus dinyatakan, bukan dibiarkan ambigu.

### Utang bersebelahan (bukan pemblokir, ikut dicicil)

- `FollowUpModal.kt:63` menulis literal `Color(0xFFEA580C)` — melanggar `design-system-rules.md`
  Kontrak 1. Token yang benar: `WeMadeColors.Secondary` (atau token peran baru `Accent`/`Warning`).
- `FollowUpModal` dirender sebagai overlay di dalam `Box` (`BuilderChatPane.kt:323-331`) **tanpa scrim**
  — bukan modal sungguhan; klik di luar tidak menutup dan chat di belakang tetap terlihat.

---

## 2. Ruang Lingkup

**Termasuk**
- RC-1: pisahkan **kunci menu** dari **query route** Builder.
- RC-2: alirkan parameter route (`template`) sebagai **prop** dari `App` → `BuilderShell` →
  `BuilderChatPane`, tanpa bergantung platform.
- RC-3: putuskan & dokumentasikan semantik template (prompt-seed dulu, blueprint binding ditunda)
  **atau** implementasikan blueprint seed bila pemilik memilih jalur penuh.
- Cicil utang: token warna `FollowUpModal`; jadikan overlay ber-scrim (opsional, kalau murah).
- Rekonsiliasi `PLAN-K3` §6 + `TASKS-SUMMARY.md` agar tidak lagi bertentangan.

**Tidak termasuk**
- K3d (Wizard shell/stepper) — sudah dibatalkan di `TASKS-SUMMARY.md:56`.
- Perubahan pada penanya klarifikasi / `InterviewPlanner` (sudah stabil, lihat `9f17eaa2`).
- Endpoint Builder lain (billing, build queue, deployment).

---

## 3. Keputusan Desain

### D-1 — Satu sumber kebenaran route = `shellPath`, dan ia dipetakan dua kali

`shellPath` memuat dua hal: **kunci menu** dan **query route**. Pecah tanggung jawabnya:

```kotlin
// BuilderNav.kt
fun builderSection(path: String): String =
    path.substringBefore('?').removePrefix("/builder").trim('/').ifEmpty { "overview" }

/** Nilai satu parameter query route Builder dari path internal, mis. template=bordir. */
fun builderQueryParam(path: String, name: String): String? =
    path.substringAfter('?', "").split('&')
        .mapNotNull { it.split('=', limit = 2).takeIf { p -> p.size == 2 && p[0] == name } }
        .firstOrNull()?.get(1)?.takeIf { it.isNotBlank() }
```

Ini menutup **RC-1** (menu cocok lagi: `chat`) dan memberi jalur baca query yang tidak bergantung
`PlatformHost` (**RC-2** akar hilangnya di native).

> Alternatif yang ditolak: menambal `BuilderShell.kt:94` dengan `section.substringBefore('?')`.
> Itu hanya menutup RC-1 dan menyisakan RC-2 — `BuilderChatPane` tetap membaca URL browser.

### D-2 — Param template jadi argumen composable, bukan pembacaan lingkungan

```kotlin
// App.kt
BuilderShell(
    section = builderSection(shellPath),
    templateCode = builderQueryParam(shellPath, "template"),
    onSectionChange = { key -> goShell("/builder/$key") }
)

// BuilderShell.kt
"chat" -> BuilderChatPane(templateCode = templateCode)

// BuilderChatPane.kt — hapus PlatformHost.queryParameter("template")
fun BuilderChatPane(templateCode: String? = null, modifier: Modifier = Modifier)
```

`LaunchedEffect` penyemai berjalan pada `templateCode`, bukan `PlatformHost`. Efeknya:
- 5 target KMP berperilaku sama (tidak ada `null` khusus native);
- dapat diuji di `jvmTest` tanpa browser;
- `templateInitialized` tetap mencegah seed ganda saat recomposition.

### D-3 — Semantik template: prompt-seed sebagai MVP, blueprint binding sebagai tindak lanjut

MVP **mempertahankan** prompt-seed (backend belum menerima blueprint di run API), tetapi:
1. pesan awal diangkat ke **fungsi murni** `templateSeedPrompt(code): String?` (bisa diuji);
2. jika `code` tak dikenal → `null` (tanpa fallback senyap ke teks generik yang menyamar sebagai
   template — sejalan `tenant-variability-rules.md` Kontrak 4);
3. **dicatat eksplisit** di TRD bahwa FR-3 "blueprint" belum terikat; bila pemilik ingin penuh,
   itu pekerjaan terpisah (butuh field `blueprint` di `POST /api/builder/chat/runs` + seed draf).

---

## 4. Task Breakdown

| # | Task | Berkas target | Effort |
|---|---|---|---|
| **T1** | `builderSection` memotong query + tambah `builderQueryParam` | `presentation/builder/BuilderNav.kt` | 0.2 hari |
| **T2** | Alirkan `templateCode` dari `App` → `BuilderShell` | `App.kt:337-339`, `BuilderShell.kt:89-116` | 0.2 hari |
| **T3** | `BuilderChatPane(templateCode)` — buang `PlatformHost`; ekstrak `templateSeedPrompt` murni | `chat/BuilderChatPane.kt`, `chat/BuilderChatTemplates.kt` (baru) | 0.3 hari |
| **T4** | Tes: `builderSection`/`builderQueryParam` + `templateSeedPrompt` (kode dikenal & tak dikenal) | `jvmTest/.../builder/BuilderNavTest.kt` (baru) | 0.3 hari |
| **T5** | Cicil: ganti literal warna `FollowUpModal.kt:63` → token; tambah scrim overlay | `chat/FollowUpModal.kt`, `chat/BuilderChatPane.kt` | 0.2 hari |
| **T6** | Rekonsiliasi `PLAN-K3` §6 & `TASKS-SUMMARY.md` + tandai FR-3 pasca-TRD | 2 berkas `.md` | 0.1 hari |
| **T7** | Verifikasi 5 target + tes segar + cek visual (lihat §5) | — | 0.3 hari |

Total ≈ **1,5–2 hari**. Tidak ada perubahan domain/server bila D-3 memilih prompt-seed.

---

## 5. Verifikasi & Definition of Done

### Tes otomatis
- [ ] `builderSection("/builder/chat?template=bordir") == "chat"` dan tanpa query tetap `"chat"`.
- [ ] `builderSection("/builder/newpack") == "newpack"`; `builderSection("/builder") == "overview"`.
- [ ] `builderQueryParam(p, "template")`: cocok, hilang, dan multi-param (`chat?template=fob&x=1`).
- [ ] `templateSeedPrompt("fob")` non-null; `templateSeedPrompt("entah") == null` (tanpa fallback senyap).
- [ ] Tes lama `BuilderChatThreadsTest` tetap hijau (tidak ada regresi chat/SSE).

### Verifikasi manual (wajib — bug ini tidak tertangkap test kompilasi)
- [ ] Login superadmin (`/login` → "Demo Mode: Masuk Cepat (Superadmin Apps)").
- [ ] Buka `/builder/newpack` → klik **Konveksi FOB** → **mendarat di panel Chat**, bukan Overview.
- [ ] Pesan awal template terkirim otomatis (run mulai, gelembung user muncul).
- [ ] Kembali ke menu **Chat AI** (tanpa query) → **tidak** menyemai template lagi.
- [ ] Klik **From Scratch** → masuk Chat tanpa template.
- [ ] Satu template di tenant uji non-garment (`klinik-uji`) untuk memastikan tidak ada asumsi
      kode industri bocor ke UI.
- [ ] Modal follow-up: token warna benar; overlay menutup/mengunci chat di belakang.

### Gerbang teknis
- [ ] Kompilasi 5 target:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] `grep -rn "Color(0xFF" app/shared/.../presentation/builder/chat/FollowUpModal.kt` → kosong.
- [ ] Hard limit file tidak dilampaui; berkas yang disentuh tidak memanjang (Ratchet, `file-size-rules.md`).

### Definisi Selesai (selaras TRD-K3 §8)
- [ ] Tampil halaman pemilihan metode.
- [ ] Grid template garment muncul dengan benar.
- [ ] Klik template → **masuk ke chat** (bukan Overview) dengan pesan awal template.
- [ ] Klik From Scratch → masuk ke chat tanpa template.
- [ ] Berfungsi di web (Wasm) **tanpa syarat reload**.
- [ ] Dokumen (`PLAN-K3`, `TASKS-SUMMARY`, TRD) tidak lagi saling bertentangan.

---

## 6. Risiko & Mitigasi

| Risiko | Mitigasi |
|---|---|
| Nama param route lain (`modules`, `module`) ikut terpengaruh | `builderQueryParam` generik, bukan khusus `template` |
| Seed template terpanggil dua kali saat recomposition | `templateInitialized` tetap dipertahankan + tes idempotensi |
| Owner menuntut FR-3 "benar-benar blueprint" | D-3 memisahkan scope; bila ya → TRD terpisah untuk field `blueprint` di run API |
| Refactor modifier overlay FollowUpModal menggeser layout chat | Cek visual §5 + tidak mengubah urutan `claySurface` |

---

## 7. Referensi

> Defek kedua pada TRD-K3 (jawaban follow-up per-pertanyaan dibuang server) punya rencana terpisah:
> [`PLAN-K3c-followup-answers-per-question.md`](PLAN-K3c-followup-answers-per-question.md).

- [`PLAN-K3-builder-wizard-self-service.md`](PLAN-K3-builder-wizard-self-service.md) §5.1, §6
- [`TRD-K3-builder-wizard-followup-ui.md`](../../trd/mvp/TRD-K3-builder-wizard-followup-ui.md) FR-1..FR-4, §8
- `app/shared/.../presentation/builder/BuilderNewPackScreen.kt` — layar pemilihan (ada)
- `app/shared/.../presentation/builder/BuilderNav.kt:22` — RC-1
- `app/shared/.../presentation/builder/BuilderShell.kt:94,116` — RC-1
- `app/shared/.../presentation/builder/chat/BuilderChatPane.kt:93` — RC-2
- `app/shared/.../infrastructure/navigation/PlatformHost.*.kt` — RC-2 (null di native)
- `design-system-rules.md` Kontrak 1 — utang `FollowUpModal.kt:63`
- `tenant-variability-rules.md` Kontrak 4 — tanpa fallback senyap (dipakai D-3)
