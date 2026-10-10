# Teaching: Layout sempit layar Hak Akses (/rbac)

Mengikuti pola Putaran 2 di `teaching-orgchart-error-states-polish.md`.

## Masalah (temuan visual T3, 360dp)
1. Keadaan Empty/Failed memakai `Box(fillMaxSize)` tanpa scroll: "Coba lagi" dan "Buat jabatan" terpotong.
2. Header `Row(SpaceBetween)`: judul mengambil seluruh lebar, toolbar (chip, "Ke Halaman Login") terdesak habis.
3. Saat Failed, chip "0 Jabatan" / "15 Modul SaaS" tampil padahal angkanya tak diketahui.
4. Tab "Per Jabatan" pecah per suku kata.

## Perbaikan
- `ScrollCenter` di `RbacStatusViews.kt`: `Column(fillMaxSize().verticalScroll())` dengan isi terpusat. Urutan modifier penting: fillMaxSize dahulu, baru scroll.
- `RbacScreenHeader.kt`: `ClayFlowRow` bersarang (membungkus), tombol `maxLines = 1` (Kontrak 13).
- `RbacHeaderChips.forState`: fungsi murni; Loading/Failed tanpa chip, Total Karyawan hanya bila diketahui. Diuji di `RbacHeaderChipsTest`.
- `RbacViewModeTabs.kt`: label `maxLines = 1, softWrap = false`, bilah `horizontalScroll`.
- `DynamicRbacScreen.kt` turun 502 -> 357 baris.

## Pelajaran
Angka yang belum diketahui jangan ditampilkan sebagai 0; sembunyikan. Pisahkan keputusan tampil (murni, teruji) dari rendering.

## Belum diverifikasi
Cek visual di browser (1280 dan 360dp; Loaded/Empty/Failed) belum dijalankan pada sesi ini.

---

# Putaran 2: "9 Total Karyawan" di tenant tanpa jabatan, Empty yang berguna, galat ramah

## Temuan 2: asal chip "9 Total Karyawan" (BUKAN kebocoran)
Gejala: cv-berkah-makloon (0 jabatan) menampilkan 9 karyawan. Ditelusuri berlapis:
1. Klien: `RbacDataLoader` memanggil `client.getEmployees(tenantSlug)` per pemuatan; `DynamicRbacScreen` membuat VM `remember(tenantId, tenantSlug)`; hitungan hidup di state VM, bukan di singleton. Tidak ada cache lintas tenant (hanya `RbacAccessPolicyRepository.shared.employees`, dipakai layar CRM/Deal, bukan chip).
2. Server: `EmployeeRoutes` -> `getAll(tenant.tenantId)` -> `PostgresEmployeeRepository` menyaring `tenant_id`; `TenantIsolationApiTest` sudah ada.
3. Data: DB scratch rbac11, `org_chart.employees` = 9 baris per tenant (`ten-demo-001`, `ten-demo-cmt`, `ten-demo-d2c`) dan `departments` = 5 per tenant (seed V98). `dynamic_rbac.custom_roles` hanya berisi `ten-demo-001` (6 baris).
Kesimpulan: data nyata tenant itu (seed demo), jabatan memang nol. Tes `RbacTenantStateTest` mengunci pemisahan per slug.

## Perbaikan
- `RbacRoleListState` (murni): `NoRoles` vs `NoMatch` vs `Showing`. Tab "Per Jabatan" pada tenant tanpa jabatan kini memakai `RbacEmptyView` ("Belum Ada Jabatan" + "Buat jabatan" bila berwenang); pencarian tak cocok tetap teks "Tidak ada jabatan yang cocok". `RbacLoadState` tak berubah (Loaded bila ada divisi walau jabatan nol; Per Modul dan Per Divisi tetap berguna).
- `presentation/common/FriendlyErrors.kt`: pemetaan galat transport -> teks ramah diangkat dari `OrgChartErrorMessages` (yang kini mendelegasi). `RbacLoadState.from` memakainya sehingga teks proxy mentah tak bocor.
- `designsystem/ScrollEdgeFade.kt`: pudar tepi kanan bilah tab selama masih bisa digeser, plus tab terpilih digulirkan ke dalam pandangan.

## Pelajaran
Sebelum menyebut "kebocoran", cek data dasarnya: gejala yang sama bisa berasal dari seed yang tidak simetris. Dan jangan salin pemetaan galat ke layar kedua; angkat.
