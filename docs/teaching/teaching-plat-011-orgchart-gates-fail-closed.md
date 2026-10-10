# Modul Pembelajaran: Gerbang Org Chart Fail-Closed untuk Token Tanpa Identitas Pabrik (TRD-PLAT-011)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Fail-closed vs null-permisif, `moduleGate`, keputusan akses (`AccessDecision`), jaring pengaman tes yang bolong, balik asersi tes lama
> **Prasyarat**: Dasar Ktor routing/plugin, RBAC berbasis jabatan (`customRoleId`) dan divisi (`departmentId`), peran sistem `TENANT_ADMIN` (Owner) vs `SALES`/`OPERATOR`
> **Referensi Task**: `docs/trd/TRD-PLAT-011-orgchart-gates-fail-closed.md`. Komit: `9acef1c7` (perbaikan), `630b4761` (balik asersi tes), `3a0bbc58` (status TRD), `6c1080a6` (merge)

---

## 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: `orgChartDecision` mengembalikan `null` ("tidak diketahui") untuk pemanggil **tanpa jabatan dan tanpa divisi**, dan seluruh rantai di bawahnya membaca `null` sebagai **tanpa batas**. Hasilnya: token `SALES` atau `OPERATOR` yang tidak punya identitas pabrik menerima 200 pada `GET /api/tenant/employees` (email dan telepon seluruh karyawan), `.../t-shape`, `POST/PUT/DELETE`, arsip/restore, dan `GET /api/tenant/departments`. Probe terhadap kode lama membuktikannya.
- **Analogi**: satpam yang ditanya "orang ini terdaftar?", dan menjawab "tidak ada datanya" lalu membiarkan masuk. Kata "tidak tahu" seharusnya berarti "tolak", bukan "silakan".
- **Hasil akhir**: bila repositori wewenang terpasang, pemanggil tanpa identitas diputuskan lewat `moduleDecision` (jalur yang sama dengan `moduleGate`): Owner dan superadmin lolos, peran lain 403. Dua lapis: keputusan di handler **dan** `moduleGate` di grup rute.

---

## 2. "Start dari Mana?" — Urutan Penulisan

1. **Langkah 0 - Buktikan celahnya dulu (probe terhadap kode lama).** Jangan memperbaiki sebelum ada tes yang gagal. Probe: `SALES` tanpa jabatan/divisi -> `GET /employees` = 200.
2. **Langkah 1 - Cari kenapa jaring pengaman tidak menangkapnya.** `RouteGateTest` hanya memprobe token ber-`customRoleId` **tak dikenal** (hasil NONE, 403). Kasus "tanpa identitas" tak pernah diprobe.
3. **Langkah 2 - Perbaiki sumbernya**: `orgChartDecision` (bukan satu per satu rute).
4. **Langkah 3 - Sabuk pengaman**: pasang `moduleGate(ORG_CHART)` pada grup karyawan, dan hapus pengecualian `GET /departments`.
5. **Langkah 4 - Balik asersi tes lama** yang mengunci perilaku permisif.
6. **Langkah 5 - Tes baru**: matriks peran x rute x tenant (garment dan klinik) dan probe generik.

---

## 3. Bedah Blok Kode

### Blok A: Keputusan akses (`server/.../routes/OrgChartAccessGuard.kt`)

```kotlin
internal suspend fun ApplicationCall.orgChartDecision(...): AccessDecision? {
    if (roleRepository == null || moduleAssignmentRepository == null) return null
    val principal = callerPrincipalOrNull
    if (principal == null || (principal.customRoleId == null && principal.departmentId == null)) {
        return moduleDecision(GarmentModules.ORG_CHART, tenant, roleRepository, moduleAssignmentRepository)
    }
    ...
}
```

**Kenapa begini?**
- Sebelumnya: `val principal = callerPrincipalOrNull ?: return null` dan `if (customRoleId == null && departmentId == null) return null`. Komentar lamanya menjustifikasi "menjaga token layanan tanpa identitas tetap berperilaku seperti sebelumnya". Itulah bug-nya: kompatibilitas mundur yang berarti kebocoran.
- Kini `null` hanya tersisa untuk satu kasus sah: **repositori wewenang tidak dipasang** (pemasangan lama/tes in-memory), supaya pemasangan tanpa RBAC tidak pecah (opsi "menolak `null` tanpa syarat" ditolak TRD karena mematikan tes dan pemasangan itu).
- `moduleDecision` sudah memberi MANAGE bagi `TENANT_ADMIN` dan superadmin act-as tanpa jabatan (K2), dan NONE bagi peran lain, jadi `requireOrgChartAccess` menolak NONE dan `applyOrgChartScope` memakai scope hasil hitung (Owner = ALL_TENANT_DATA). Dampak ke Owner, superadmin, dan mode demo: nol.

### Blok B: Gerbang di grup rute (`EmployeeRoutes.kt`, `DepartmentRoutes.kt`)

```kotlin
route("/api/tenant/employees") {
    if (roleRepository != null && moduleAssignmentRepository != null) {
        moduleGate(GarmentModules.ORG_CHART, roleRepository, moduleAssignmentRepository, write = AccessLevel.OPERATE)
    }
```

```kotlin
// DepartmentRoutes: pengecualian GET dihapus
moduleGate(GarmentModules.ORG_CHART, roleRepository, moduleAssignmentRepository)
```

**Kenapa begini?**
- Baca = VIEW, tulis = OPERATE (cocok untuk POST/PUT); DELETE/archived/restore tetap MANAGE di handler lewat `requireOrgChartAccess`.
- Pengecualian `GET /departments` dulu bergantung pada guard handler yang permisif; menghapusnya membuat gerbang berlapis (handler **dan** modul). Biayanya satu perhitungan keputusan tambahan tanpa efek samping.
- Gerbang pada grup bersifat sabuk pengaman untuk handler yang ditambahkan kelak.

### Blok C: Jaring pengaman tes (`RouteGateTest.orgChartRoutes_denyMemberCarryingNoFactoryIdentity`)

Tes ini **mengenumerasi seluruh rute** bawah `/api/tenant/employees` dan `/api/tenant/departments` dari `RoutingRoot`, memanggil tiap rute dengan token `SALES` tanpa identitas, dan gagal bila ada yang bukan 401/403. Ada penjaga `assertTrue(routes.size >= 10, ...)` agar enumerasi yang kosong tidak lolos diam-diam sebagai "semua aman".

---

## 4. Teknologi & Pendekatan: The "Why"

| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Dua lapis (keputusan + `moduleGate`) | Hanya tutup jalur tulis | Pembacaan email/telepon juga bocor | Kebocoran data pribadi |
| Perbaiki `orgChartDecision` | Hanya `moduleGate` | Handler yang dipanggil tanpa gerbang tetap permisif | Celah muncul lagi di pemasangan lain |
| `null` hanya bila repo tak terpasang | `requireOrgChartAccess` menolak `null` mutlak | Pemasangan tanpa RBAC/tes lama tetap hidup | Mematikan banyak tes |
| Jangan ubah `AccessDecisionEngine`/`ModuleGate` | Longgarkan mesin | Mesin benar | Efek samping luas |

---

## 5. Jebakan Nyata yang Ditemukan

1. **Null-permisif**: "tidak diketahui" dibaca sebagai "tidak dibatasi". Pola ini diwariskan lewat tiga tempat sekaligus: `requireOrgChartAccess` meloloskan `null`, `OrgChartDataReach.from` dan `applyOrgChartScope` membaca `null` sebagai tanpa batas.
2. **Tes yang mengunci bug**: `OrgChartAccessApiTest.listEmployees_withTokenCarryingNoFactoryIdentity_shouldBehaveAsBefore` menegaskan perilaku permisif. Tes seperti ini harus **dibalik secara sadar** (komit `630b4761`): Owner tanpa jabatan tetap 200, peran lain 403.
3. **Probe yang tak pernah menguji kasus sebenarnya**: `RouteGateTest` memakai jabatan tak dikenal, sehingga lulus selamanya walau kasus tanpa identitas bocor.
4. **Pengecualian yang tampak wajar**: `GET /departments` dikecualikan dari gerbang karena "sudah punya guard sendiri", padahal guard itulah yang permisif.
5. **Cakupan terbatas**: probe baru dibatasi ke rute karyawan/divisi. Pola `null` serupa ada di `factoryFlowDecision` (K5 TRD), lalu ditangani terpisah di TRD-PLAT-012 (`docs/trd/TRD-PLAT-012-factory-flow-gates-fail-closed.md`), bukan di sini.
6. **Ukuran file (ratchet)**: `EmployeeRoutes.kt` tercatat 376 baris sebelum (di atas soft 300) dan 381 saat dibaca di HEAD; naik 5 baris tetapi masih di bawah hard 500. Catatan: angka 381 saya baca dari `wc -l` setelah merge main dan bisa mencakup perubahan lain.
7. **Batas bukti**: TRD mencatat bukti data (hanya `TENANT_ADMIN` dan `PLATFORM_SUPERADMIN` tanpa jabatan/divisi) hanya dari DB dev; lingkungan lain tidak diverifikasi. Klien berbasis token layanan tanpa identitas kini menerima 403 (risiko yang diterima).

---

## 6. Cara Memverifikasi

- `server/src/test/.../OrgChartFailClosedApiTest.kt`: `noIdentityToken_nonOwnerRoles_getForbiddenOnEveryReportedRoute_garment` dan `_klinik` (dua tenant), `tenantAdminWithoutRole_ownerStillPasses`, `superadminActingAs_passes`, `viewRole_readsOk_writesForbidden`, `noneRole_forbiddenEverywhere`, `noIdentityToken_cannotEscapeToAnotherTenant`.
- `RouteGateTest.orgChartRoutes_denyMemberCarryingNoFactoryIdentity` (probe generik); `OrgChartAccessApiTest` (asersi dibalik).
- Perintah: `./gradlew :server:test --tests '*OrgChartFailClosedApiTest*' --tests '*RouteGateTest*' --tests '*OrgChartAccessApiTest*'` (tidak dijalankan saat menulis dokumen ini).
- Cara membuktikan sendiri: checkout `9acef1c7~1` di worktree terpisah, salin tes baru ke sana, jalankan tes baru, dan lihat gagal pada kode lama.

---

## 7. Tantangan Mandiri

- [ ] Tambahkan rute karyawan baru bayangan dan pastikan probe generik gagal bila gerbangnya lupa dipasang.
- [ ] Jelaskan mengapa `moduleGate` memakai `write = OPERATE` pada karyawan tetapi DELETE tetap MANAGE di handler.
- [ ] Cari satu tempat lain di `routes/` yang memakai pola `?: return null` untuk keputusan akses dan nilai apakah aman.
