# 🎓 Modul Pembelajaran: Handoff dari Spec — Modul Pilot "Permintaan Perubahan" (Jalur C)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Generator kode deterministik, SQL/Kotlin dari data tak tepercaya, RLS multi-tenant, gerbang RBAC fail-closed, uji berbasis database terpisah
> **Prasyarat**: [teaching-proto-b-contract-v1](teaching-proto-b-contract-v1.md), `.claude/rules/module-integration-rules.md` §5, `.claude/rules/tenant-variability-rules.md` Kontrak 7
> **Referensi**: [PLAN-proto-C](../plannings/parallel/PLAN-proto-C-handoff-pilot.md), [Discovery Note pilot](../plannings/discovery-PROTO-pilot-change-request.md)

---

## 1. Masalah
`HandoffScaffoldGenerator` lama menghasilkan **tabel stub** (`payload JSONB`) — developer tetap menulis tabel, repository, route, dan test dari nol. Jalur C membuktikan bahwa **spec prototype** (`PrototypeSpec`) cukup untuk menurunkan titik awal modul yang nyata: tabel berkolom bertipe, RLS, repository, route CRUD yang fail-closed, dan test 403 — sebagai **kandidat PR yang ditinjau manusia**, bukan penulisan otomatis.

## 2. Start dari mana (urutan kerja)
1. **Discovery** (skill `wemade-feature-discovery`) → keputusan arsitektur *sebelum* kode. Temuan yang mengubah desain: `BusinessModule` kini alias `ModuleId` (B6d), modul pack data wajib berprefiks kode pack dan didaftarkan lewat `DomainPackRegistry.register`; route tenant wajib di `/api/tenant/…`; tabel baru wajib masuk `ModuleSchemaMap`.
2. **Pack pilot sebagai data** (`LayananPilotPack`): satu modul, satu entitas dengan **kelima tipe field**, supaya setiap jalur generator benar-benar dikompilasi dan dites.
3. **Penamaan aman** (`SpecNaming`) — sebelum menulis SQL apa pun. Spec adalah data tak tepercaya (bisa dari LLM).
4. **Migrasi** (`SpecMigrationWriter`), lalu **persistensi** (`SpecPostgresWriter`), lalu **route** (`SpecRoutesWriter`), lalu **test gerbang** (`SpecRouteTestWriter`).
5. **Terapkan manual** ke worktree, jalankan terhadap **database uji**, perbaiki generator dari temuan, regenerasi.

## 3. Bedah keputusan

### 3.1 Spec = data tak tepercaya
Setiap identifier SQL dinormalisasi ke `[a-z][a-z0-9_]*` dan **ditolak** bila kosong/terlalu panjang/kata cadangan SQL/bertabrakan dengan kolom bawaan (`id`, `tenant_id`, …). Literal SQL dikutip dengan penggandaan `'`; literal Kotlin di-escape (`\`, `"`, `$`). Test: kunci `x'; DROP TABLE tenants;--` menjadi kolom `x_drop_table_tenants`, bukan SQL injeksi. **Tolak, jangan menebak** (Kontrak 4).

### 3.2 Urutan gerbang route (tidak boleh diubah)
```
konteks tenant → modul dikenal proses ini (403 bila tidak) → keputusan RBAC + level (403)
              → modul ada di pack tenant (404, hanya yang lolos RBAC) → BARU body dibaca
```
Tiga pelajaran dari kegagalan nyata saat pilot:
- **404 untuk orang tak berwenang = handler sudah menyentuh isi tenant.** `RouteGateTest` menolak itu; RBAC harus mendahului cek pack.
- **Modul yang pack-nya tak termuat di proses** membuat `moduleDecision` melempar error (500). Keputusan RBAC yang tak bisa dihitung = **403** (fail-closed), bukan error.
- **Owner melewati matriks** — itu alasan cek keanggotaan pack tetap ada sebagai lapis kedua.

### 3.3 Satu sumber aturan
Route memvalidasi lewat `PrototypeReducer` dengan **spec yang sama** dengan prototype (required, opsi enum, transisi status). Aturan di prototype dan di server tidak bisa berbeda. Kolom `DATE` ditambah cek ISO (spec prototype menerima teks bebas).

### 3.4 Deterministik, dan tidak menulis sendiri
Masukan sama → keluaran identik byte per byte (tak ada jam/acak). Generator **tidak pernah menulis ke pohon sumber atau DB**; `ScaffoldDumpTool` menulis ke folder atas permintaan, lalu manusia menyalin dan meninjau.

### 3.5 Nama tabel Exposed berulang → alias `T`
Keluaran pertama berbaris sangat panjang (`LayananChangeRequestChangeRequestsTable.` di setiap baris). Alias lokal `T` membuat repository terbaca tim. Kode hasil generate sengaja **lurus dan eksplisit**, bukan runtime generik.

## 4. Jebakan (semuanya pernah terjadi)
1. **Berkas `.env` tidak ikut worktree** → aplikasi memakai DB bawaan. Rujuk DB uji lewat `DB_NAME` secara eksplisit.
2. **Test "tanpa Postgres" yang diam-diam butuh Postgres**: `module()` memakai repository entitlement/pipeline bawaan. Suntikkan repository memori (pola `BuilderDraftBootstrapTest`). `VendorAccessApiTest` mengklaim hal yang sama tetapi memakai repository bawaan.
3. **Impor `SqlExpressionBuilder.eq`** dibutuhkan di `deleteWhere` — hanya ketahuan saat mengompilasi keluaran, bukan saat generator dites. *Selalu kompilasi keluaran, bukan hanya menguji teksnya.*
4. **Argumen posisi `CustomRole`**: pakai argumen bernama di kode generate.
5. **Migrasi ke DB bersama**: `DatabaseFactory.init()` menjalankan Flyway. Jangan jalankan test DB tanpa `DB_NAME` yang menunjuk database uji.
6. **Agent lain menyunting working tree yang sama** → kerjakan di `git worktree` sendiri.

## 5. Cara membuktikan (ringkasan hasil)
| Bukti | Hasil |
|---|---|
| Generator: determinisme, SQL, anti-injeksi, urutan gerbang, template kedua (tiket, bukan pilot) | `SpecScaffoldGeneratorTest` — 10 test |
| Migrasi **bersih dari nol** (V1…V90) di database kosong | `flyway_schema_history` berakhir `90 | t`; DB dev tetap V89 |
| `ModuleSchemaOwnershipTest` (tabel terdaftar, RLS, grant) | lulus |
| Gerbang semua route tenant (`RouteGateTest`) + kepemilikan route | lulus setelah memperbaiki urutan RBAC |
| Test gerbang yang ikut digenerate (401/403/404/modul tak dikenal) | 6 test, tanpa Postgres |
| CRUD nyata + validasi + state machine + isolasi tenant | `LayananChangeRequestApiIntegrationTest` — 2 test (dibuktikan benar-benar menegakkan lewat uji mutasi) |
| **RLS** sebagai role `wemade_app` | tenant A melihat 2 baris, B melihat 1, sisip lintas tenant → `violates row-level security policy` |

**Yang belum terbukti:** koneksi aplikasi di tes memakai pemilik DB (melewati RLS) sehingga isolasi lewat API hanya dibuktikan oleh filter `tenant_id`; RLS dibuktikan terpisah lewat SQL. Adaptor Koog (C6), endpoint brief (C4) menunggu jalur B.

## 6. Tantangan
- [ ] Dukung spec **multi-entitas** (v1 menolaknya dengan pesan: "tepat satu entitas").
- [ ] Tambahkan `DB_APP_USER` pada tes integrasi agar RLS ikut teruji lewat API.
- [ ] Hasilkan backfill `custom_roles` untuk preset peran (kini sengaja `TODO(review)` — keputusan bisnis).
