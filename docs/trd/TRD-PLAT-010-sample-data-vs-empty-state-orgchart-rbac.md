# TRD-PLAT-010: Data Sampel vs Keadaan Kosong di Org Chart dan RBAC

## 1. Konteks dan Administrasi

- **ID**: TRD-PLAT-010 — Data sampel vs keadaan kosong (Org Chart + RBAC)
- **Status**: **Diusulkan** (belum ditinjau pengguna; semua keputusan di §4 diambil atas nama pengguna dan wajib ditinjau)
- **Riwayat**: 0.1 — 2026-10-09 — Claude (riset dari kode di `ec0d1937`; tanpa menjalankan aplikasi).
- **Rujukan**: `CLAUDE.md` Status Repo butir 4 (kode mesin tak menyebut satu industri), `tenant-variability-rules.md`
  Kontrak 3/4/6/7, `design-system-rules.md` §8, `file-size-rules.md` Kontrak 2 (ratchet), TRD-PLAT-008/009 (pola),
  `teaching-org-chart-t-shape-hierarchy.md:25`, `teaching-dual-path-orgchart-hierarchy-sync.md:269`.
- **Catatan penamaan**: label "B2" berasal dari permintaan; baris B2 di `PLAN-dual-track-...md` (tipe port) sudah lain
  maknanya. Dokumen ini lanjutan B7/B4 (isi tenant non-garment), bukan baris B2 PLAN.

### Masalah (diverifikasi dari kode)

1. **Org Chart selalu memulai dari sampel garment.** `OrgChartViewModel.loadInitialData()` (`OrgChartViewModel.kt:53-80`)
   mengisi `employees` dari `OrgNode.createSampleEmployees()` dan `departments` dari `Department.defaultPresets()`
   tanpa syarat. `OrgChartUiState.kt:7` juga default `departments = Department.defaultPresets()` dan
   `selectedDepartment = Department.SALES` (baris 14). `tenantSlug` default `"wemade-demo"` (baris 17) — tak ada
   hubungannya dengan sampel, tetapi memperlihatkan bahwa klien tidak tahu "mode demo".
2. **Data live hanya menimpa bila tidak kosong** (`:99` `liveDepts.isNotEmpty() || liveEmps.isNotEmpty()`).
   Respons sukses `[]` = sampel tetap. `catch` kosong (`:152-154`, "Silently fallback to presets") dan
   `isSuccess` yang salah satunya gagal juga menyisakan sampel — **sukses-kosong, galat, dan memuat tampak identik**.
   Baris 141 (`if (effectiveLiveEmps.isNotEmpty()) … else state.employees`) menahan karyawan sampel bahkan saat
   departemen live ada tetapi karyawan live kosong (mis. filter jangkauan menghasilkan kosong).
3. **RBAC sama.** `DynamicRbacViewModel.init` (`:57-58`) memanggil `loadInitialRoles()` (`:96-112`) tanpa syarat:
   `CustomRole.createFactoryPresets(tenantId, ActiveTenantPack.current)` + `Department.defaultPresets()` +
   `moduleAssignments` seed lokal. `fetchRemoteData` (`:62-92`) menimpa hanya bila `!remoteRoles.isNullOrEmpty() ||
   !remoteDepts.isNullOrEmpty()`, dan `getOrNull()` membuang pembeda galat vs kosong.
   `RbacAccessPolicyRepository.kt:177-191` menambah fallback sampel di tiga `onFailure` (roles hanya bila `server == null`,
   departments dan employees tanpa syarat itu).
4. **"Total Karyawan" bukan karyawan.** `DynamicRbacUiState.kt:80-81` `totalUsers = roles.sumOf { it.userCount }`;
   `userCount` preset di-hardcode per jabatan (`CustomRole.kt:132,143,171,197,223,249` = 1,2,1,4,3,14).
5. **Dugaan balapan pack (BELUM terbukti, tak dijalankan).** `createFactoryPresets` memberi 6 jabatan hanya untuk
   pack garment, selain itu `presets.take(1)` (`CustomRole.kt:121`). `ActiveTenantPack` bernilai pack garment sampai
   `getTenantPack(...)` selesai (`ActiveTenantPack.kt` KDoc: "Sebelum respons tiba nilainya pack bawaan"; aktivasi di
   `RbacAccessPolicyRepository.kt:168`, async). VM RBAC membaca `ActiveTenantPack.current` saat konstruksi → bila VM
   dibuat sebelum pack tenant aktif, tenant klinik melihat 6 jabatan. Konsisten dengan gejala yang dilaporkan, tetapi
   perlu dibuktikan dengan tes (§6) sebelum diklaim sebagai penyebab tunggal.
6. **Sisi server ikut menanam garment.** `POST …/restore-presets` (`EmployeeRoutes.kt:340`, `DepartmentRoutes.kt:255`)
   menulis `Department.defaultPresets(tenantId)` / `OrgNode.createSampleEmployees(tenantId)` ke **tenant mana pun**
   (`PostgresEmployeeRepository.kt:139`, `PostgresDepartmentRepository.kt:112`) — pack tenant tidak diperiksa.
   Tombol "Pulihkan preset" di tenant klinik menulis divisi garment ke database klinik. Restore peran *sudah*
   sadar-pack (`RoleRepository.restoreDefaultPresets(tenantId, pack)`).
7. **Klien memperlakukan restore secara optimistis**: `RestoreDefaultPresets` (`OrgChartViewModel.kt:675-700`) mengubah
   state lokal ke sampel *sebelum* server menjawab dan mengabaikan hasil `restoreDepartmentPresets/EmployeePresets`
   (ditolak 403/gagal pun, layar tetap menampilkan sampel).
8. **Penyebab lain di luar dua layar**: `PersonaSwitcherDropdown.kt:125` (`createFactoryPresets` bila `roles` kosong)
   dan `TestingPersona.kt:190` (`createSampleEmployees`) — alat uji persona; lihat Q6.
9. **Data sampel di DB hanya untuk tenant demo utama.** Migrasi V4 menyisipkan divisi/jabatan/karyawan untuk
   `ten-demo-001` saja; V9 membuat `ten-demo-cmt`/`ten-demo-d2c` **tanpa** data org (hanya `tenants` + `tenant_pipelines`).
   Jadi tenant demo CMT/D2C hari ini terlihat berisi *hanya* karena sampel klien — kebijakan baru mengubah itu (§5).
10. **Tak ada atribut "tenant ini mode contoh".** `Tenant` (`Tenant.kt:18-42`) tidak punya penanda demo; `TenantStatus` =
    `TRIAL|ACTIVE|DUE|PAST_DUE|SUSPENDED|ARCHIVED` (status penagihan), `tier` = paket. Kolom `tenants.settings JSONB`
    ada sejak V1 tetapi tidak dibaca domain mana pun (grep `settings` pada repository/domain tenant: nol).
11. **Tes yang bergantung pada state awal bersampel** (diverifikasi dengan membaca): `OrgChartViewModelSuperiorAutoFillTest`
    (3 tes, `OrgChartViewModel(tenantSlug="wemade-demo")` tanpa klien, mencari Hendra/Budi/Joko di `employees`),
    `OrgChartViewModelScopingTest` (5 tes, `viewerDepartmentId="dept-warehouse"`), `OrgChartViewModelDeleteTest` (3 tes,
    belum dibaca isinya). `RbacAccessPolicyRepositoryTest` memakai `TestingPersona`/peran tulis tangan (tidak membaca
    isinya; **belum diverifikasi** apakah ia bergantung pada fallback). Tak ada tes yang mengunci "respons `[]` →
    sampel tetap".

## 2. Persyaratan

- **FR-1** Org Chart dan RBAC memodelkan pemuatan sebagai keadaan eksplisit `Loading | Empty | Loaded | Failed`
  (pola `BuilderDraftState`). Respons sukses kosong = `Empty`; galat (jaringan/HTTP/isi tak terbaca) = `Failed` berpesan.
  Tidak ada jalur kode yang mengubah `Empty` atau `Failed` menjadi sampel secara otomatis.
- **FR-2** Sampel hanya muncul lewat **aksi pengguna yang sah** ("Muat contoh"), dieksekusi **server** (satu sumber
  kebenaran), dan hasilnya dibaca ulang dari server — bukan dirakit di klien.
- **FR-3** Keadaan `Empty` Org Chart: pesan "Belum ada divisi/karyawan", CTA membuat divisi pertama, dan (bila pengguna
  berwenang MANAGE dan pack menyediakan contoh) tombol "Muat contoh". Keadaan `Failed`: pesan galat + "Coba lagi";
  tidak ada sampel, tidak ada data basi yang berpura-pura segar.
- **FR-4** `restore-presets` di server **sadar-pack**: bila pack tenant tak menyediakan contoh → 409 berpesan jelas,
  tidak ada tulisan. Tetap fail-closed (MANAGE + jangkauan penuh) dan diuji 403.
- **FR-5** RBAC: VM tidak membaca `ActiveTenantPack.current` saat konstruksi untuk memutuskan daftar jabatan; daftar
  jabatan berasal dari server (sudah sadar-pack). Tanpa balapan, tanpa preset lokal.
- **FR-6** "Total Karyawan" berasal dari karyawan nyata (atau disembunyikan bila tak terbaca), bukan jumlah `userCount` preset.
- **FR-7** Kode mesin tidak menyebut slug demo atau satu industri: tidak ada `if (slug == "wemade-demo")`.
- **NFR-1** Tenant demo utama (`wemade-demo`, data di DB) berperilaku identik (tes paritas). **NFR-2** `OrgChartViewModel.kt`
  dan `OrgChartScreen.kt` tidak bertambah baris (ratchet). **NFR-3** Tanpa `!!`, tanpa `else ->` baru pada `when` domain.
  **NFR-4** Tanpa migrasi pada tahap 1–3.

## 3. Titik Pendaftaran (grep segar 2026-10-09)

`grep -rn "defaultPresets\|createSampleEmployees\|createFactoryPresets"` pada `core/commonMain`, `server/src/main`,
`app/shared/commonMain`:

| Lapisan | Titik | Tindakan |
|---|---|---|
| Klien Org Chart | `OrgChartViewModel.kt` (1196 baris), `OrgChartUiState.kt:7,14`, `OrgChartScreen.kt` (2420) | tahap 1 |
| Klien RBAC | `DynamicRbacViewModel.kt` (610) `:57,96-112,62-92`, `DynamicRbacUiState.kt:81`, `DynamicRbacScreen.kt:104,401` | tahap 3 |
| Klien kebijakan akses | `RbacAccessPolicyRepository.kt:177-191` | tahap 3 (hapus fallback sampel; **keputusan K5**) |
| Server restore | `EmployeeRoutes.kt:340`, `DepartmentRoutes.kt:255`, `PostgresEmployeeRepository.kt:139`, `PostgresDepartmentRepository.kt:112`, `InMemory*Repository` | tahap 2 |
| Domain sampel | `Department.defaultPresets` (`Department.kt:144`), `OrgNode.createSampleEmployees` (`OrgNode.kt:49`), `CustomRole.createFactoryPresets` (`CustomRole.kt:117`) | tetap; dibungkus kebijakan pack (K3) |
| Alat uji | `PersonaSwitcherDropdown.kt:125`, `TestingPersona.kt:190` | **tidak disentuh**; Q6 |
| Tes terdampak | `OrgChartViewModel{SuperiorAutoFill,Scoping,Delete}Test` | beri `seed = GarmentSample` eksplisit |
| **Tidak disentuh** | `App.kt` (default slug demo = utang lama), migrasi/`V*`, `GarmentDomainPack`, `FieldInput.kt` dan seluruh area tipe `FILE`/`discovery/fields` (sesi lain), `presentation/builder/*` (hanya dibaca sebagai pola) | — |

## 4. Keputusan dan Opsi yang Ditolak

**K1 — Model keadaan eksplisit; sampel bukan keadaan, melainkan aksi.** `OrgChartLoadState` =
`Loading | Empty | Loaded(departments, employees) | Failed(message)`; `from(deptsResult, empsResult)` menerjemahkan hasil
klien tanpa fallback. Berkas bertema baru (§5). Ditolak: (a) mempertahankan sampel sebagai "keadaan awal yang akan
diganti" — itulah bug; (b) banner "Contoh" di atas sampel yang tetap otomatis — menyembunyikan kebijakan, bukan
memperbaikinya, dan tenant kosong tetap tampak berpenghuni.

**K2 — Tak ada penanda "mode demo" di klien; sumber kebenaran = isi database tenant.** Tenant demo adalah tenant yang
*datanya diisi* (V4 sudah melakukannya untuk `ten-demo-001`); klien tak perlu tahu. Ini memenuhi FR-7 tanpa atribut baru.
Konsekuensi: demo CMT/D2C jadi kosong sampai seed ditambahkan (§5) — tindakan data, bukan kode.
Ditolak: (a) `if (tenantSlug == "wemade-demo")` di klien (melanggar kode mesin tak menyebut tenant; rapuh);
(b) kolom baru `tenants.is_demo`/`sample_data_policy` — migrasi, API baru, dan penanda yang bisa menyimpang dari isi
nyata, tanpa kebutuhan terbukti; (c) `TenantStatus.TRIAL`/`tier` sebagai pembeda — status penagihan bukan niat data,
tenant TRIAL nyata (sedang membangun di Builder) akan salah dianggap demo; (d) `tenants.settings` JSONB — tidak dibaca
domain mana pun; menghidupkannya hanya demi flag ini melebar. **Cadangan bila Q1 dijawab "ya, onboarding otomatis":**
`settings.onboarding.seedSampleOrgChart` dibaca server saat registrasi (bukan klien).

**K3 — "Muat contoh" dijalankan server dan sadar-pack lewat satu predikat transisi.** `StarterOrgChartPolicy`
(core, murni): `isAvailableFor(pack): Boolean`, kini `true` hanya untuk pack bawaan garment — jembatan Strangler
yang *dinyatakan* (KDoc: "contoh org chart saat ini bernama konveksi"), bukan tebakan. Pack non-garment → 409. Tahap
akhir opsional (T4): `DomainPack.starterOrgChart` (data pack; `null` = tak ada contoh) menggantikan predikat —
sampel menjadi data per pack (Kontrak 1). Ditolak: biarkan restore menulis garment ke tenant lain (status quo);
menyembunyikan tombol di klien saja tanpa gerbang server (fail-open); menulis sampel baru per industri sekarang (di luar cakupan).

**K4 — `RestoreDefaultPresets` menunggu server, lalu memuat ulang.** Tidak ada state sampel lokal; toast sukses hanya
setelah server menjawab sukses; 403/409/galat → toast galat, state tak berubah. Ditolak: pertahankan update optimistis
(menampilkan sampel yang tak pernah tersimpan).

**K5 — RBAC: hapus preset lokal; jabatan dari server; kegagalan = `Failed`.** Alasan: server sudah memutuskan jabatan
per pack (`createFactoryPresets(tenantId, pack)` di repository), jadi salinan klien hanyalah sumber kedua yang bisa
salah (balapan butir 5). `RbacAccessPolicyRepository` fallback `onFailure` dihapus untuk departments/employees dan
roles; menu pengguna tak terpengaruh (sudah bersumber `/me/access`, komentar `:165-168`). Ditolak: menunggu
`ActiveTenantPack` lalu memakai preset lokal (memperbaiki balapan tetapi mempertahankan "jabatan contoh seolah milik
pabrik" — komentar `:163-164` sendiri sudah menyebut ini tidak diinginkan); mulai kosong tanpa `Failed` (menyembunyikan
galat jaringan).
**Preset non-garment yang benar = 1 jabatan (Owner)**, sumber `CustomRole.createFactoryPresets` (server), selaras
TRD-PLAT-009: modul tata kelola kini disuntik ke pack hasil handoff, jadi Owner di pack itu punya `org_chart` +
`dynamic_rbac`; jabatan lain dibuat admin. Ini bukan perubahan baru — ini yang sudah dilakukan server; klien berhenti menimpanya.

**K6 — "Total Karyawan" = `employees.size` dari `OrgChartApiClient.getEmployees` bila terbaca; selain itu chip
disembunyikan.** `userCount` per kartu jabatan tetap dari server. Memuat karyawan butuh akses Org Chart
(`requireOrgChartAccess`); admin RBAC tanpa akses Org Chart → chip disembunyikan, bukan 0 (0 menyesatkan).
Ditolak: hitungan `userCount` preset (angka tulis tangan); menambah endpoint hitung baru (belum dibutuhkan).
Terbuka (Q4): apakah `userCount` preset (1/2/1/4/3/14) dinolkan di `createFactoryPresets` — menyentuh paritas
garment dan cermin backfill V19/V64.

**K7 — Strangler Fig dua penyakit dipisah: tahap dengan flag klien dihindari.** Karena kebijakan baru adalah
*menghapus* sampel otomatis (bukan menambah variasi), flag build-time ganda tidak membuat paritas lebih aman; paritas
dijaga dengan `seed` eksplisit di tes dan tenant demo berisi data DB. Tahap dipisah per lapisan (klien Org Chart →
server restore → RBAC), tiap tahap dapat di-revert sendiri. Ditolak: flag runtime "gunakan sampel lama" (dua perilaku
hidup berdampingan tanpa batas waktu).

**K8 — Parameter `seed` pada VM, bukan default sampel.** `OrgChartViewModel(seed: OrgChartSeed = OrgChartSeed.None)`;
`OrgChartSeed.GarmentSample` dipakai tes. Produksi tak pernah melewatkannya. Ditolak: default sampel (pemanggil yang
lupa mengulang bug).

## 5. Strategi Implementasi di Bawah Ratchet

`OrgChartViewModel.kt` 1196 baris (hard 600) dan `OrgChartScreen.kt` 2420 — keduanya **tidak boleh bertambah**; catat
`wc -l` sebelum/sesudah di PR. Pemecahan mengikuti tanggung jawab, bukan baris.

| Tahap | Isi | Berkas (perkiraan) | PR |
|---|---|---|---|
| **T1** Org Chart klien | `OrgChartLoadState.kt` (sealed + `from`), `OrgChartDataLoader.kt` (memindahkan blok `launch{…}` `:88-155` + `matchDepartment`/`filterByScope`/`resolveDefaultSuperior` bila bersih terpisah), `OrgChartEmptyState.kt` (composable buta-domain di `orgchart/`), `OrgChartSeed.kt`; ubah `OrgChartViewModel.kt` (turun ≥100 baris karena ekstraksi), `OrgChartUiState.kt` (default kosong, tambah `loadState`), `OrgChartScreen.kt` (cabang `when(loadState)`; tambahan baris diimbangi dengan memindahkan satu composable privat mandiri keluar — kandidat dialog arsip `:243-400`, **belum diperiksa** kerapiannya); 3 berkas tes diberi `seed` + tes baru | ±9 berkas, ±+350/−250 baris | 1 PR |
| **T2** Server restore sadar-pack | `StarterOrgChartPolicy.kt` (core), gerbang di `EmployeeRoutes`/`DepartmentRoutes` restore-presets (409), tes server: 403 (peran tak berwenang), 409 (pack non-garment), 200 garment | ±6 berkas | 1 PR |
| **T3** RBAC | `DynamicRbacViewModel` (hapus `loadInitialRoles` lokal, pakai `RbacLoadState`), `RbacAccessPolicyRepository` (hapus fallback), `DynamicRbacUiState/Screen` (chip, keadaan kosong/galat), tes | ±8 berkas | 1–2 PR |
| **T4** (opsional) | `DomainPack.starterOrgChart` + codec + pack garment mengisinya (byte-identik diuji) | besar, menyentuh `DomainPackCodec` | PR sendiri, **setelah Q1/Q5** |

Urutan T1→T2→T3 disarankan; T1 dan T2 dapat paralel (berkas tak beririsan). Data: tenant `ten-demo-cmt`/`ten-demo-d2c`
perlu skrip seed (data/migrasi V-baru) bila tetap ingin berisi (Q2) — bukan bagian T1–T3.

## 6. Rencana Tes

Org Chart (tanpa jaringan, klien palsu): respons `[]`/`[]` pada tenant non-garment → `Empty`, 0 divisi, 0 karyawan,
`employees`/`departments` bukan sampel; galat jaringan → `Failed`, bukan sampel; `getDepartments` sukses +
`getEmployees` gagal → `Failed` (bukan setengah-sampel); live sebagian (divisi ada, karyawan kosong) → `Loaded` dengan
0 karyawan, tanpa sisa sampel (menutup baris `:141`); tenant demo dengan klien palsu berisi data garment → `Loaded`
identik; `seed = GarmentSample` tanpa klien mempertahankan semua tes lama (paritas); `RestoreDefaultPresets` dengan klien
yang menolak → state tak berubah + toast galat. Template non-default: fixture pack klinik/bordir (Kontrak 6).
Server: restore-presets 403 untuk OPERATE/VIEW dan jangkauan sempit (sudah ada logikanya; tambah tes bila belum), 409
untuk pack non-garment, tidak ada baris ditulis saat 409; garment 200 identik hari ini.
RBAC: VM pada tenant klinik dengan `ActiveTenantPack` masih garment saat konstruksi → tidak pernah menampilkan 6 jabatan
(tes yang *membuktikan atau membantah* dugaan balapan butir 5 — wajib dijalankan pertama sebelum mengubah apa pun);
`getRoles` sukses-kosong → `Empty`, gagal → `Failed`; chip "Total Karyawan" = jumlah karyawan terbaca, tersembunyi bila
tak terbaca; pack non-garment → tepat 1 jabatan (Owner); `RbacAccessPolicyRepositoryTest` tetap hijau tanpa fallback.
Kompilasi 5 target + `scripts/audit-variability.sh` (tidak boleh menambah temuan).

## 7. Cek Visual yang Wajib Dilihat Mata Setelah Implementasi

(Login dulu sebagai superadmin demo bila diminta; tenant uji non-garment `bordir-uji`/klinik, plus `wemade-demo`.)
1. Org Chart tenant kosong pada **1280dp dan 360dp**: pesan kosong + CTA tak merusak tata letak, tak ada kolom divisi
   kosong yang tersisa, tak ada kartu sampel berkedip sebelum `Empty` (urutan `Loading` → `Empty`, bukan sampel → kosong).
2. Org Chart `Failed`: pesan galat terbaca, "Coba lagi" berfungsi (matikan server).
3. `wemade-demo` tetap penuh dan identik dengan sebelumnya; tenant garment baru kosong.
4. RBAC klinik: satu jabatan; "Total Karyawan" benar atau tersembunyi; header tak bergeser saat chip hilang.
5. Tombol "Muat contoh" pada tenant non-garment: toast 409 terbaca, tak ada divisi garment muncul.
6. **Utang lama (dicatat, TIDAK diperbaiki di sini):** tombol "+ Divisi Baru" pecah tiga baris di lebar sempit
   (`design-system-rules.md` Kontrak 13: elemen yang mengalah belum diberi `weight(1f, fill = false)` + `maxLines`);
   literal warna `OrgChartScreen.kt` (§8 utang design system) — jangan menambahnya di keadaan kosong baru
   (pakai token; komponen baru di `designsystem/` bila pola ≥3 kali).

## 8. Risiko

| Risiko | Mitigasi |
|---|---|
| Demo CMT/D2C mendadak kosong | Pengguna memutuskan Q2; T1 tidak digabung sebelum jawabannya |
| Tes lama diam-diam bergantung pada sampel | `seed` eksplisit; jalankan seluruh `orgchart`/`rbac` test sebelum & sesudah |
| Ratchet `OrgChartScreen.kt` (2420) sulit diimbangi | Ekstraksi composable mandiri jadi bagian T1; bila tak ada garis pisah jujur, tahan T1 dan laporkan |
| Dugaan balapan salah | Tes pembuktian dijalankan dahulu; K5 tetap benar walau dugaan salah (menghapus sumber kedua) |
| Admin RBAC tanpa akses Org Chart → karyawan tak terbaca | K6 menyembunyikan chip, bukan 0 |
| Alat uji persona masih memakai sampel | Dicatat; Q6 |
| Klien lama vs server baru (409 tak dikenal klien lama) | Klien lama menampilkan toast galat umum; tidak ada kerusakan data |

## 9. Keputusan Menunggu Pengguna

| # | Pertanyaan | Rekomendasi |
|---|---|---|
| Q1 | Tenant garment **baru** (non-demo): kosong + CTA "Muat contoh", atau otomatis berisi sekali saat registrasi? | Kosong + CTA (tidak ada data tak diminta) |
| Q2 | Demo CMT/D2C (`ten-demo-cmt/-d2c`) tetap berisi? Bila ya, seed data lewat migrasi/skrip | Ya, seed data (bukan sampel klien) |
| Q3 | `Failed` Org Chart: galat + "Coba lagi" saja, atau tambah banner bila ada data basi terakhir? | Galat saja (lebih sederhana, jujur) |
| Q4 | Nolkan `userCount` preset di `createFactoryPresets`? (menyentuh paritas garment dan cermin V19/V64) | Tunda; K6 sudah cukup untuk chip |
| Q5 | Lanjut T4 (contoh sebagai data pack) atau cukup predikat transisi K3? | Cukup K3 sekarang; T4 saat ada pack kedua yang butuh contoh |
| Q6 | `PersonaSwitcherDropdown`/`TestingPersona` (alat uji) ikut dibersihkan? | Tidak sekarang; label jelas "uji" cukup |
| Q7 | Pesan 409 dan teks keadaan kosong: bahasa Indonesia tetap, istilah pack (`term(VocabularyKey.WORKPLACE)`) dipakai? | Ya, pakai kosakata pack bila tersedia |

## 10. Tidak Bisa Dipastikan Tanpa Menjalankan Aplikasi

- Apakah balapan `ActiveTenantPack` benar penyebab "6 jabatan" di klinik (butir 5) — butuh tes/pengamatan urutan pemuatan.
- Urutan pemuatan nyata di layar (kedip sampel→kosong), tata letak keadaan kosong di 360dp.
- Apakah repositori in-memory server (profil dev) menyemai data demo seperti V4 (hanya `restoreDefaultPresets` yang
  terbaca; seed awal in-memory tidak diperiksa).
- Apakah `RbacAccessPolicyRepositoryTest` bergantung pada fallback; isi `OrgChartViewModelDeleteTest`.
- Apakah registrasi tenant non-garment sudah membuat jabatan Owner di server (diasumsikan dari TRD-PLAT-009, tidak ditelusuri).
