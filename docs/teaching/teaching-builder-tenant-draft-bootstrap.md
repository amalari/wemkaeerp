# Teaching — Bootstrap Draf Kerja Builder dari Tenant Existing

**Slug**: `teaching-builder-tenant-draft-bootstrap` · **Tanggal**: 2026-10-03 · **Repo**: Jalur B (wemkaeerp)

## 1. Masalah

Pane **Modules / Data Flow / Prototype** di Builder Console (`app.lvh.me:3001/builder`) membaca
`GET /api/builder/draft` → `drafts.findByTenant(tenantId)`. Tenant yang sudah berjalan lama —
misalnya `wemade-demo` (PT WeMade Garmen Ekspor) — **belum pernah punya baris draf** di
`ops.discovery_drafts`, karena draf hanya lahir dari funnel Discovery atau tombol "Terapkan" Chat AI.
Hasilnya: konsol Builder tenant existing tampil kosong ("Belum ada draf kerja") padahal modul
operasionalnya sudah aktif dan dipakai.

## 2. Keputusan Desain

Tiga opsi dipertimbangkan; dipilih **lazy bootstrap**:

| Opsi | Keputusan | Alasan |
|---|---|---|
| Seed SQL migration | ❌ | Draf beku, melewati validator domain, tidak ikut perubahan tenant |
| Fallback read-only tanpa simpan | ❌ | "Terapkan" patch Chat AI tidak konsisten — tidak ada draf kerja nyata |
| **Lazy bootstrap saat `GET /draft` kosong** | ✅ | Idempoten, satu sumber kebenaran, draf langsung bisa dipatch via Chat AI |

## 3. Implementasi

### Use case (core) — `EnsureTenantWorkingDraftUseCase.kt`
`core/.../domain/builder/EnsureTenantWorkingDraftUseCase.kt` (84 baris). Kontrak:

1. **Idempoten**: `drafts.findByTenant(tenantId)` ada → kembalikan apa adanya, **tidak pernah menimpa**.
2. Pack dari `DomainPackRegistry.find(tenant.domainPack)`; blueprint starter pertama milik pack
   (`GarmentBlueprints`) — hanya kerangka, aktif/non-aktif ditentukan data.
3. Modul aktif dari **pipeline tenant** (`TenantPipelineRepository`): node non-bypass, non-plugin,
   diiriskan dengan modul blueprint (invarian `DiscoveryDraft`: blueprint hanya boleh menyebut modul
   pack-nya sendiri). Tanpa pipeline → default blueprint.
4. **Fail-soft `null`**: pack tak dikenal / blueprint tak ada / validator menolak / save gagal →
   `null`; route menjawab tanpa draf seperti sebelum fitur ada. Bootstrap tidak pernah menciptakan
   error baru.

Id draf mengikuti konvensi `ApplyDraftPatchUseCase` (`draft-<tenantId>`) supaya bootstrap dan
"Terapkan" mengerjakan **baris yang sama**.

### Route (server) — `BuilderRoutes.kt`
`GET /api/builder/draft` kini memanggil use case setelah gate fail-closed `call.gate()`; identitas
pemanggil (`CallerPrincipal.userId` → value class `UserId`) menjadi `ownerUserId` draf. Parameter
baru `pipelines` + `bootstrapDrafts` memakai pola default-argument yang sudah ada di file itu.

### Wiring — `Application.kt`
`pipelines = pipeRepo` ditambahkan **di baris yang sudah ada** (file 311 baris — aman, tapi tetap
dijaga tidak bertambah; Kontrak 2 aturan file-size). Penting untuk test: tanpa ini, test in-memory
akan memakai `PostgresTenantPipelineRepository` dan butuh DB.

## 4. Test — `BuilderDraftBootstrapTest.kt` (5 test, semua hijau)

| Test | Membuktikan |
|---|---|
| `draft_bootstrap_fromTenantPipeline_excludesBypassedModule` | Modul di-bypass pipeline → tidak aktif di draf (9 FOB − 1 bypass = 8) |
| `draft_bootstrap_isIdempotent_sameDraftIdOnSecondCall` | GET kedua tidak membuat draf baru |
| `draft_existingDraft_isNeverReplacedByBootstrap` | Draf CMT pre-existing tetap disajikan utuh |
| `draft_unauthorizedRole_returns403_andCreatesNothing` | Fail-closed: 403 **tidak** memicu bootstrap |
| `bootstrap_unknownPack_returnsNull_withoutSaving` | Pack tak dikenal = fail-soft `null` |

## 5. Verifikasi Visual (dengan mata)

1. Server di-restart (Ktor lama masih memuat class lama) → `./gradlew :server:run` di background.
2. Login demo **Superadmin** → `:3001/builder` (tenant `wemade-demo`) → menu **Modules**.
3. Hasil: pane menampilkan **"Konveksi & Garmen — 9 modul aktif"** + peta modul
   (`ModuleMapPane`) — bukan lagi "Belum ada draf kerja".
4. Di DB, `factory_flow.tenant_pipelines` untuk `ten-wemade-demo` **kosong (0 rows)** → 9 modul
   berasal dari fallback default blueprint FOB, sesuai desain. Tenant dengan pipeline nyata akan
   mengikuti modul aktifnya sendiri.

## 6. Pitfall yang Dipelajari

- **`callerPrincipalOrNull` adalah extension `ApplicationCall`** — di dalam handler route harus
  `call.callerPrincipalOrNull`, tidak bisa dipanggil telanjang (di `gate()` bisa karena dia
  extension `ApplicationCall` juga).
- **`CallerPrincipal.userId` adalah `String`**, bukan value class `UserId` — wajib dibungkus.
- **`testApplication { }` mengembalikan `Unit`** — helper test yang mengembalikan `HttpResponse`
  harus extension `ApplicationTestBuilder` yang dipanggil di dalam blok test.
- **`StubBuilderAgent` di `BuilderRouteGateTest` itu `private`** — test lain perlu stub-nya sendiri.
- **Canvas Compose menelan klik DOM** — verifikasi Playwright pada UI wasm harus klik koordinat
  mouse (`page.mouse.click`), bukan `locator.click()`.

## 7. Konsekuensi & Utang

- `Application.kt` menyentuh batas hard utangnya sendiri — setiap perubahan berikutnya wajib tidak
  menambah baris (sudah dipatuhi di perubahan ini).
- Target `assembleAndroidMain` tidak bisa dijalankan di environment ini (ANDROID_HOME kosong);
  4 target lainnya (Jvm, WasmJs, Js, jvmTest) hijau. Perubahan tidak menyentuh kode
  Android-specific (core commonMain + server), jadi risikonya rendah.
- Bootstrap berjalan sekali per tenant (saat pertama `GET /draft`); setelah itu draf hidup sebagai
  working draft dan berubah hanya lewat "Terapkan" atau deploy.
