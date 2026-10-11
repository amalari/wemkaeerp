# TRD-PLAT-011: Gerbang Org Chart Fail-Closed (token tanpa identitas pabrik)

## 1. Konteks dan Administrasi

- **ID**: TRD-PLAT-011 — Gerbang rute Org Chart fail-closed
- **Status**: **Diusulkan; diimplementasikan di cabang worktree, menunggu merge** (keputusan arah "tutup semua, bukan hanya jalur tulis" sudah diambil pengguna, 2026-10-10). Probe terhadap kode lama membuktikan celah: `SALES` tanpa identitas menerima 200 pada `GET /employees`.
- **Rujukan**: `tenant-variability-rules.md` Kontrak 7 (menulis wajib fail-closed), `module-integration-rules.md` §5.2,
  `file-size-rules.md` Kontrak 2 (ratchet), TRD-PLAT-009/010 (pola; T2 sudah menutup `restore-presets`).

### Masalah (diverifikasi dari kode, HEAD `1fc99865`)

`orgChartDecision` (`OrgChartAccessGuard.kt`) mengembalikan `null` bila repositori wewenang null **atau** token tanpa
`customRoleId` dan tanpa `departmentId`; `requireOrgChartAccess` meloloskan `null`; `OrgChartDataReach.from` dan
`applyOrgChartScope` membaca `null` sebagai tanpa batas. Grup `/api/tenant/employees` tidak memasang `moduleGate`, dan
`GET /api/tenant/departments` dikecualikan dari `moduleGate` divisi. Akibatnya token `SALES`/`OPERATOR` tanpa kedua
identitas lolos pada: `GET /employees`, `/employees/{id}`, `/employees/{id}/t-shape`, `POST/PUT/DELETE`,
`GET /employees/archived`, `POST /employees/{id}/restore`, dan `GET /departments`.

Jaring pengaman bolong: `RouteGateTest` hanya memprobe token ber-`customRoleId` tak dikenal (hasil NONE → 403);
kasus tanpa identitas tak pernah diprobe. `OrgChartAccessApiTest.listEmployees_withTokenCarryingNoFactoryIdentity_
shouldBehaveAsBefore` mengunci perilaku permisif itu.

Bukti data (DB dev, baca-saja): user tanpa jabatan dan tanpa divisi hanya `TENANT_ADMIN` (17) dan
`PLATFORM_SUPERADMIN` (1). Menutup tidak mengunci akun lemah mana pun di DB dev. Keterbatasan: hanya DB dev.

## 2. Persyaratan

- **FR-1**: Bila repositori wewenang terpasang, pemanggil tanpa jabatan/divisi diputuskan lewat `moduleDecision`
  (jalur yang sama dengan `moduleGate`): Owner (`TENANT_ADMIN`) dan superadmin lolos, peran lain = NONE → 403.
- **FR-2**: Semua rute karyawan dan `GET /departments` tertutup untuk token tanpa identitas ber-peran non-Owner.
- **FR-3**: Probe jaring pengaman: `RouteGateTest` (atau pendamping) memprobe token tanpa kedua identitas.
- **NFR-1**: Perilaku untuk token berjabatan (VIEW/NONE/OPERATE/MANAGE) dan isolasi tenant tidak berubah.
- **NFR-2**: `EmployeeRoutes.kt` (376 baris, di atas soft) tidak boleh membesar secara berarti; logika baru di berkas bertema.

## 3. Keputusan

**K1 — Dua lapis, sama-sama fail-closed.**
(a) `orgChartDecision`: bila repositori wewenang terpasang dan pemanggil tanpa jabatan dan tanpa divisi, kembalikan
`moduleDecision(ORG_CHART, …)` alih-alih `null`. Dengan begitu `requireOrgChartAccess` menolak NONE dan
`applyOrgChartScope` memakai scope hasil perhitungan (Owner = ALL_TENANT_DATA). Pemanggil tanpa principal ikut
diputuskan `moduleDecision` (= NONE). `null` tersisa **hanya** bila repositori wewenang tidak dipasang (mode
tes/in-memory lama), supaya pemasangan tanpa RBAC tidak pecah.
(b) `moduleGate(ORG_CHART)` dipasang pada grup karyawan bersyarat repositori non-null seperti divisi (read=VIEW,
write=OPERATE agar sesuai POST/PUT; DELETE/archived/restore tetap MANAGE di handler lewat `requireOrgChartAccess`).
Pengecualian `GET /departments` dihapus. Ini sabuk pengaman untuk handler yang ditambahkan kelak.

**Opsi ditolak**: (1) hanya menutup jalur tulis — pembacaan email/telepon seluruh karyawan tetap bocor; (2) hanya
`moduleGate` — handler yang dipanggil tanpa gerbang (mis. pemasangan lain) tetap memakai `null` permisif; (3) membuat
`requireOrgChartAccess` menolak `null` tanpa syarat — mematikan tes dan pemasangan tanpa repositori wewenang;
(4) melonggarkan `AccessDecisionEngine`/`ModuleGate` — di luar batas, tidak perlu.

**K2 — Owner/superadmin tetap lolos.** `moduleDecision` memberi MANAGE bagi `TENANT_ADMIN` dan superadmin act-as
tanpa jabatan (`ModuleAccessGuard.kt`). Dampak ke Owner, superadmin, dan mode demo: nol.

**K3 — Token layanan tanpa identitas ditolak 403.** Pesan galat: `Butuh wewenang … atas modul "…"; wewenang Anda saat
ini … (…)`. Dikenali dari kode 403 + frasa "Butuh wewenang". Klien Org Chart (T1) menampilkan state `Failed`; tidak diubah.

**K4 — Tes.** `OrgChartAccessApiTest` ±296-307 dibalik: kunci "tanpa identitas tetap lolos" diganti "SALES tanpa
identitas → 403, Owner tanpa jabatan → 200". Probe baru di `RouteGateTest`/kelas pendamping untuk token tanpa identitas.

**K5 — Factory Flow `GET /api/tenant/locations`.** Sudah punya gerbang handler (`requireFactoryFlowAccess`) dan
`PUT` fail-closed; `RouteOwnership` memetakannya ke `FACTORY_FLOW`. Namun `factoryFlowDecision`
(`FactoryFlowAccessGuard.kt:80-82`) memiliki pola `null` permisif yang sama. Itu **terpisah**: dicatat sebagai
tindak lanjut (TRD berikutnya), tidak diubah di sini.

## 4. Titik pendaftaran (grep segar)

`routes/OrgChartAccessGuard.kt` (`orgChartDecision`), `routes/EmployeeRoutes.kt` (grup), `routes/DepartmentRoutes.kt`
(pengecualian GET), `Application.kt` (pemanggilan `employeeRoutes`/`departmentRoutes`). Tes: `OrgChartAccessApiTest`,
`RouteGateTest`, `StarterOrgChartRestoreApiTest`, `DepartmentApiTest`, `EmployeeApiTest`, `TenantIsolationApiTest`.

## 5. Risiko

- Klien lama berbasis token layanan tanpa identitas mendapat 403 (diterima; tidak ada di DB dev).
- Tes lain yang memakai `asTenant(slug, Role.SALES)` tanpa jabatan terhadap rute org akan berubah — dipindai lewat kelas terkait.
- Dua gerbang pada satu rute: biaya satu perhitungan keputusan tambahan, tanpa efek samping.

## 6. Rencana tes

Tiap rute yang dilaporkan: SALES/OPERATOR tanpa identitas → 403 (baca+tulis); `TENANT_ADMIN` tanpa jabatan → 200;
superadmin act-as → 200; jabatan VIEW → baca 200 tulis 403; jabatan NONE → 403; tenant garmen dan non-garmen
(klinik); isolasi tenant. Probe dijalankan lebih dulu terhadap kode lama untuk membuktikan celah.
