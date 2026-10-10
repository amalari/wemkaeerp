# HANDOFF 2026-10-10 — Status Pekerjaan, Yang Sedang Berjalan, dan Sisa

Dokumen serah-terima agar pekerjaan bisa dilanjutkan di sesi baru tanpa konteks percakapan. Disusun
saat batas penggunaan sesi hampir habis. Semua klaim di sini diambil dari kondisi repo dan laporan
agen pada 2026-10-10; yang tidak terverifikasi ditandai **[TAK TERVERIFIKASI]**.

> Status: **PERENCANAAN / SERAH-TERIMA** (bukan TRD fitur). TRD yang relevan ada di `docs/trd/`
> (TRD-FIELD-004 DRAFT, TRD-PLAT-012 Track A selesai).

---

## 1. Kondisi `main` (titik awal)

- HEAD lokal: `8ae0a07c` (merge TRD-FIELD-004 DRAFT). Komit hari ini **sebagian besar sudah di
  `origin/main`** (terakhir terkonfirmasi sama: `07a9b748`). Sesudahnya ada komit lokal yang **belum
  di-push**: sapu non-Latin-1 (`5ea6491a`), dokumentasi field (`718acfcc`), perbaikan KRITIS FILE
  lintas tenant (`bf9fbe65`), TRD-FIELD-004 (`8ae0a07c`). **Langkah pertama sesi baru: periksa
  `git rev-list --count origin/main..main` lalu push (fast-forward) — termasuk perbaikan keamanan.**
- Verifikasi terakhir yang hijau penuh (worktree bersih, `--rerun-tasks`, pada `bf9fbe65`):
  server 756 tes / app:shared 465 / core 1830, 0 gagal; `scripts/audit-variability.sh` 0 temuan.
  Android **tidak** dikompilasi (SDK tidak ada di mesin ini).
- Folder utama punya perubahan **belum dikomit milik sesi lain** (field discovery:
  `InteractiveFormState.kt`, `InteractiveTableState.kt`, `FieldInput.kt`, `NumberFormatting.kt`,
  `PrototypeFieldControlParityTest.kt`, dst.). **Jangan** `git add -A`/`reset`/`stash` di folder utama.

## 2. Yang sedang berjalan / menggantung (BELUM di-merge)

TRD-FIELD-004 (`docs/trd/TRD-FIELD-004-file-relation-server-hardening.md`, DRAFT; keputusan
Q1/2/3/5/7 disetujui pengguna — lihat §5).

| Pekerjaan | Branch / worktree | Komit | Status |
|---|---|---|---|
| **Track A** (celah rute FILE/RELATION): A1 tes merah, A2 `FieldFileRecordGate`, A3 ikatan `recordId`, A4 `authorizeRelationTarget` | `worktree-agent-a2a4c73c4c9b00cb6` (`.claude/worktrees/agent-a2a4c73c4c9b00cb6`, terkunci) | `79df6847`, `ecc9975c`, `9c771f7e`, `ce44e3e1`, docs `134c0536` | **Selesai** (laporan akhir diterima; basis `8ae0a07c`). Menurut agen: server 791 tes (5 skip), core 1834, app:shared + Wasm/JS hijau, audit 0 temuan; A1 = 26 tes, 14 merah pada kode lama. Teaching doc `teaching-field-file-relation-record-gate.md` dan TRD v0.2 ada di branch. **Belum diverifikasi ulang di worktree bersih oleh integrator.** |
| **Track B (B1+B2)**: `Contribution.rows` (F0), `relationProblem` di generator, penolakan target HIERARCHICAL | `worktree-agent-a5bacf93a917b548a` | `4f687dbd`, `00ab7468` | **Selesai**; agen: core 1846 / server 762 tes hijau di basis `8ae0a07c` |

**UPDATE (integrasi sudah dikerjakan):** Track A + Track B sudah digabung di branch
`integration/field-004` (worktree `.claude/worktrees/integ-f004`, 11 komit di atas `907960bb`) dan
**terverifikasi hijau penuh** (`--rerun-tasks`, worktree bersih): server 797 tes / app:shared 465 /
core 1859, 0 gagal; audit 0 temuan. Integrasi memerlukan 3 perbaikan pasca-merge (konflik
`SpecRoutesWriter.kt`; `RelationTargets.kt` memanggil `exists` tanpa `reachableOwnerIds` yang kini wajib;
dua tes Track B memakai tanda tangan lama) — semuanya `reachableOwnerIds = null` (target modul generate
hanya ke GLOBAL_ONLY, Q3). **`main` BELUM dimajukan** karena folder utama punya 16 file belum dikomit milik
sesi lain (penyatuan kosakata field) dengan 5 file tumpang tindih
(`CustomFieldValidation.kt`, `CrmFieldTypeParityTest.kt`, `CustomFieldEvolutionTest.kt`,
`CrmRelationWriteGuard.kt`, `CrmRoutes.kt`). Langkah: sesi itu mengomit/menyimpan pekerjaannya, lalu di
folder utama `git merge --ff-only integration/field-004`; bila `main` sudah maju, merge ulang
`integration/field-004` ke `main` lalu ulangi verifikasi (resep §6). Setelah masuk: hapus worktree
`integ-f004`, `agent-a2a4c73c4c9b00cb6`, `agent-a5bacf93a917b548a` dan branch-nya.

**Cara melanjutkan (urutan yang disarankan):**
1. `cd /Volumes/amalari/Projects/wemkaeerp && git branch --show-current` (harus `main`). Cek
   `git -C .claude/worktrees/agent-a2a4c73c4c9b00cb6 status --short` dan `git log main..worktree-agent-a2a4c73c4c9b00cb6`.
   Track A sudah selesai (lihat tabel). Catatan dari agen Track A yang perlu keputusan/diketahui:
   (a) field FILE bertanda `isRequired` tidak bisa dipenuhi saat create (aturan Q1) — harus opsional atau
   diisi lewat edit; (b) target RELATION di luar pack tenant pada tulis CRM kini 404 (dulu 400) karena
   gerbangnya dibagi dengan rute opsi (tafsiran FR-3.4); (c) produksi masih tanpa sumber baris
   sehingga rute generik HIERARCHICAL menjawab 403 sampai ada modul yang menyuplai `RecordOwnerSource`.
2. Merge **Track A dulu**, lalu **Track B**. Konflik yang diperkirakan kecil di
   `server/.../SpecRoutesWriter.kt` (Track B menyisipkan 5 titik `relationProblem`; Track A A3 bisa
   menambah `fileOwnershipProblem(recordId)`). Selesaikan sekali.
3. Verifikasi gabungan **di worktree bersih** (resep §6), `--rerun-tasks`.
4. Lanjut sisa TRD-FIELD-004: **B3** (magic byte + header adapter; menyentuh `FieldFileRoutes.kt`
   dan `S3ObjectStorage` — baru boleh setelah Track A ter-merge), lalu **Track C** (C1 regenerasi
   `layanan_change_request` + migrasi aditif 2 kolom nullable; C2 `GeneratedRouteMatchesPackSpecTest`
   + `GeneratedRouteWriteGuardTest`; C3 teaching doc + `scripts/sync-agent-config.sh` bila aturan berubah).

## 3. Sisa pekerjaan (belum dimulai), urut prioritas

1. **Push `main`** (lihat §1). Perbaikan keamanan FILE dan `/demo` harus ada di remote.
2. **Selesaikan TRD-FIELD-004** (§2): Track A → B (B1/B2 siap) → B3 → Track C.
3. **Q8 TRD-FIELD-004 — konfirmasi wiring deployment**: dari `server/src/main`, `Application.kt:161`
   `fieldFileRecordRows = emptyMap()` (unduh FILE generik 404 di produksi). B1 menutupnya lewat
   `Contribution.rows`; pastikan **tidak ada konfigurasi deployment di luar repo** yang menyuntik
   parameter itu (tanyakan pemilik deployment). **[TAK TERVERIFIKASI]**
4. **Q4 TRD-FIELD-004 (tiket terpisah, belum ada TRD)**: `SpecRoutesWriter.authorized` mengabaikan
   DataScope untuk seluruh CRUD modul hasil generate yang HIERARCHICAL (`repository.list(tenantId)`).
5. **Q6 TRD-FIELD-004 (infra)**: siapa yang menegakkan header `nosniff` (bucket policy/proxy)?
6. **Cek visual layar discovery dengan field `TIME`** di tenant non-garment (form blok, tabel inline,
   kanban) — PLAN-field-component-gaps "Sisa terbuka" butir 3.
7. **`origin/feat/iv-a-wizard-interview`**: 1 komit belum ter-merge (wizard wawancara); periksa
   bentrok dengan `FieldInput.kt` sebelum merge.
8. **TRD-PLAT-012 sisa**: Q3 seragamkan bypass `TENANT_ADMIN` antara `factoryFlowDecision` dan
   `callerDecisions` (Track C TRD-PLAT-012, perlu tes paritas dulu); Q4 telemetry (biarkan, catat);
   probe generik `RouteGateTest` baru mencakup prefix Factory Flow + Org Chart — modul lain belum.
9. **Login/sesi**: pemulihan sesi — kegagalan jaringan saat verifikasi masih menghapus sesi
   (perilaku lama); `devPort`/`isDevEnvironment` di `TenantApiEndpointResolver.kt` kode mati di luar
   tes; redirect demo `localhost` → `*.lvh.me:3011` port dikodekan keras (dev).
10. **Kosmetik/celah kecil**: `DropdownMenu` Material berbayang blur (belum ada `ClayDropdownMenu`);
    usulan layar ganda → 500 (seharusnya 4xx); istilah garment bocor di overview Builder klinik
    ("Garment Platform", stepper Procurement→QC); balapan sesi 401 saat memuat `/rbac` langsung via
    URL setelah login; seed V98 memberi jabatan hanya ke `wemade-demo` (tenant demo lain: karyawan
    tanpa jabatan); sisa string non-Latin-1 di `core/discovery/{brief,handoff}`, `**/print/*`,
    `server/infrastructure/**` (~140 literal; sengaja tak disapu — bukan teks Compose).
11. **`App.kt` 598 baris** (hard limit 600): perubahan berikutnya di file itu wajib memecahnya dulu.
12. **Data FILE lama**: ref asing yang mungkin tersimpan sebelum perbaikan KRITIS-1 tidak dibersihkan
    (hampir pasti kosong; tak ada modul FILE aktif). **[TAK TERVERIFIKASI di DB mana pun]**

## 4. Yang SUDAH selesai & ter-merge hari ini (ringkas, untuk konteks)

- Irisan field 1–4 (C1–C10): dokumentasi & audit status — **semua ada di semua lapisan** (audit kode).
- Stabilisasi tes server (pagar DB scratch, `DISCOVERY_AGENT`/`INTERVIEW_AGENT` dikunci di Gradle).
- TRD-PLAT-008/009/010/011/012 (+ teaching doc 008–011); gerbang Org Chart & Factory Flow fail-closed.
- Keamanan auth: `/api/public/auth/demo` default MATI (`WEMADE_DEMO_LOGIN=on`), hanya tenant demo,
  token terikat tenant, klaim slug wajib, role tak dikenal 403, superadmin demo tanpa tenant, tanpa
  fallback senyap `wemade-demo`/`ten-default`, login demo/persona tanpa sesi offline palsu.
- UI: Org Chart/RBAC/Factory Flow layout sempit, keadaan Loading/Empty/Failed/AccessDenied,
  `FriendlyErrors`, sapu non-Latin-1 + `UiTextLatin1GuardTest`.
- Keamanan FILE: `FileRef.isValidFor` (isolasi tenant ref), batas memori unggah (`readBoundedBody`).

## 5. Keputusan yang sudah diambil pengguna (jangan ditanyakan ulang)

- TRD-FIELD-004: **Q1** FILE pada create ditolak 400 (tanpa id sementara); **Q2** modul HIERARCHICAL
  tanpa sumber pemilik di rute generik → 403 fail-closed; **Q3** target RELATION dari modul generate
  ke modul HIERARCHICAL ditolak saat registrasi/generator sampai FR-3.x selesai; **Q5**
  `ObjectStorage.downloadUrl` boleh menerima parameter opsional `fileName`; **Q7** magic byte tak
  cocok → 415.
- TRD-PLAT-012: Q1 `/locations` GET hanya `FACTORY_FLOW`; level baca VIEW, tulis MANAGE.
- "Mulai dari Kosong" di Org Chart dihapus (hanya mengosongkan state lokal).
- Sesi offline klien dihapus seluruhnya (login demo/persona), termasuk jaringan mati.
- Pengguna **mengizinkan** pengambilan password DB dari env container `wemade-postgres`
  (`POSTGRES_PASSWORD`) **hanya lewat env proses** (tidak ditulis ke file/log, tidak dicetak).

## 6. Resep & pagar kerja (WAJIB dibaca sebelum menjalankan apa pun)

**Larangan keras**
- JANGAN membaca/mengubah `.env`. JANGAN membuat role/user Postgres baru (agen sebelumnya pernah
  membuat role SUPERUSER `oc10` sebagai jalan memutar — sudah di-drop; jangan ulangi).
- Tes/server hanya ke DB ber-nama **`scratch`** (pagar `DatabaseFactory` menolak yang lain).
- Jangan menyentuh port **8081/3001** (milik Anda/sesi lain). Pakai pasangan port sendiri:
  server `PORT=8091`, web `WEMADE_WEB_PORT=3011 WEMADE_API_PORT=8091` (`localhost:3011`).
- Jangan mematikan proses yang bukan milik sesi; catat PID sendiri. Tutup browser Playwright
  (dipakai bersama; buka `browser.newContext()` sendiri). Screenshot hanya di `.playwright-mcp/`
  (path absolut).

**Dua baris `.env` yang perlu Anda ubah SENDIRI** (saldo DeepSeek habis): `DISCOVERY_AGENT=deterministic`
dan `INTERVIEW_AGENT=off`. (Gradle sudah mengunci keduanya untuk tes, tapi server lokal membaca `.env`.)

**Verifikasi gabungan (resep yang dipakai hari ini)**
```bash
cd /Volumes/amalari/Projects/wemkaeerp
git worktree add -q .claude/worktrees/verify-X HEAD
cd .claude/worktrees/verify-X
docker exec wemade-postgres psql -U postgres -c 'CREATE DATABASE wemake_erp_scratch_verifyN'   # sekali
DB_NAME=wemake_erp_scratch_verifyN ./gradlew :server:test :app:shared:jvmTest :core:jvmTest \
  :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs --console=plain -q --rerun-tasks \
  > /tmp/verify.log 2>&1; echo "exit=$?" >> /tmp/verify.log        # jalankan di LATAR BELAKANG
scripts/audit-variability.sh | tail -1                              # harus 0 temuan
cd ../.. && git worktree remove --force .claude/worktrees/verify-X
```
Hitung tes dari `*/build/test-results/*/*.xml` (jumlah `tests`, `failures`, `errors`). Verifikasi di
**worktree bersih** — Gradle sesi lain di folder `build` yang sama menyebabkan hasil palsu
(`Could not write XML test results`). Jalankan Gradle panjang di latar belakang dengan log ke file
(proses foreground pernah terbunuh, kode 137, saat IDE ikut memindai worktree).

**Server lokal untuk cek visual** (password hanya via env proses):
```bash
PW=$(docker exec wemade-postgres printenv POSTGRES_PASSWORD)
PORT=8091 DB_PASSWORD="$PW" DB_USER=postgres DB_HOST=localhost DB_PORT=5432 DB_NAME=wemake_erp_scratch_xxx \
 DB_APP_USER=wemade_app DB_APP_PASSWORD=wemade-app-dev WEMADE_DEMO_LOGIN=on \
 DISCOVERY_AGENT=deterministic INTERVIEW_AGENT=off ./gradlew :server:run
WEMADE_WEB_PORT=3011 WEMADE_API_PORT=8091 ./gradlew :app:webApp:wasmJsBrowserDevelopmentRun
```
Klik Compose-wasm: `getByText(...).click({force:true})`. Login demo: tombol "Demo Mode: Masuk Cepat"
(butuh `WEMADE_DEMO_LOGIN=on`; hanya tenant `wemade-demo` + `WEMADE_DEMO_TENANTS`).

**Pola kerja yang terbukti**: A0 (core) → Track A/B/C paralel per worktree (`isolation: worktree`) →
satu integrator merge `--no-ff` + verifikasi gabungan; TDD (tes merah dulu, catat nama tes); merge
hanya setelah persetujuan pengguna; worktree/branch dihapus setelah merge (`git worktree unlock` bila
terkunci, `git branch -d`).

## 7. Pembersihan (aman, tidak mendesak — jangan hapus tanpa memeriksa dulu)

- **DB scratch** (`wemake_erp_scratch_*`, ±28 buah): `docker exec wemade-postgres psql -U postgres -c
  'DROP DATABASE wemake_erp_scratch_xxx'`. Pertahankan `wemake_erp_scratch_test` dan satu `verify`
  untuk resep §6.
- **Branch `worktree-agent-*` lama** (a7477925…, a86f3e59…, a8c91a04…, aa052352…, ac45be0f…): periksa
  `git log main..<branch>` dan `git branch --merged main`; hapus yang sudah ter-merge/kosong.
- **`.playwright-mcp/`** (±565 berkas screenshot, sebagian tak terlacak git): arsipkan/hapus bila tak perlu.
- Worktree `.kilo/worktrees/foamy-target` bukan milik sesi ini (sesi lain); jangan disentuh.

## 8. Pertanyaan terbuka untuk pemilik produk/infra

1. Q8: apakah ada konfigurasi deployment di luar repo yang menyuntik `fieldFileRecordRows`?
2. Q6: siapa yang menegakkan `nosniff` pada objek S3 (bucket policy atau proxy)?
3. Q4: apakah DataScope CRUD modul generate HIERARCHICAL dibuat TRD sendiri sekarang atau menunggu
   ada modul generate yang HIERARCHICAL di produksi?
4. Apakah `fieldFileRecordRows`/modul generate FILE sudah dipakai di produksi (menentukan urgensi
   pembersihan data lama §3 butir 12)?
