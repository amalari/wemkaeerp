# Modul Pembelajaran: Gerbang Factory Flow Fail-Closed (TRD-PLAT-012, Track A)

> **Level**: Junior ke Mid | **Topik**: RBAC server, `null` permisif vs fail-closed, tes probe | **Referensi**: `docs/trd/TRD-PLAT-012-factory-flow-gates-fail-closed.md`

## 1. Masalah

Guard Factory Flow (`factoryFlowDecision`) mengembalikan `null` ("keputusan tak terhitung") untuk token tanpa jabatan dan tanpa
divisi, dan `requireFactoryFlowAccess(null)` **meloloskan** `null`. Bagi token `SALES`/`OPERATOR` tanpa identitas pabrik, itu
berarti "tidak diketahui" dibaca "boleh". Org Chart punya cacat sama (TRD-PLAT-011).

Audit menemukan celahnya sempit: hampir semua rute Factory Flow sudah dijaga lapis luar (`tenantRouteGate`) dan tulisnya oleh
`mayEditWithoutDecision`. **Satu rute bocor: `GET /api/tenant/locations`** — tidak punya entri `TenantRouteGatePolicy`,
satu-satunya penjaganya guard handler yang null-permisif.

## 2. Langkah (urutan penting)

1. **Probe merah dulu.** `FactoryFlowAccessApiTest` + probe generik di `RouteGateTest` dijalankan terhadap kode lama. Hasilnya
   persis satu rute: `GET /api/tenant/locations` untuk token SALES = 200. Dugaan dari membaca kode terbukti dinamis, bukan asumsi.
2. **K2 — lapis luar.** Satu baris di `TenantRouteGatePolicy`: baca `/api/tenant/locations` = `GateRule(VIEW, [FACTORY_FLOW])`;
   tulis tetap `null` (ditangani handler dengan MANAGE).
3. **K1 — lapis dalam.** `factoryFlowDecision` memakai pola `orgChartDecision`: bila repositori wewenang terpasang, token tanpa
   principal/jabatan/divisi diputuskan lewat `moduleDecision(FACTORY_FLOW)` (Owner/superadmin MANAGE, lainnya NONE). `null`
   tersisa hanya saat repositori tak terpasang. Urutan pemeriksaan dibalik: repositori dulu, baru principal.
4. **K3.** `mayEditWithoutDecision` tidak dihapus: sabuk kedua untuk tulis.

## 3. Pelajaran

- **Dua lapis bukan alasan membiarkan lapis dalam permisif.** Handler baru yang hanya memanggil `requireFactoryFlowAccess`
  mewarisi celah. Memperbaiki hanya satu lapis meninggalkan jebakan untuk rute berikutnya.
- **Tulis tes yang gagal lebih dulu.** Probe merah membuktikan tes memang menangkap celah, bukan lulus karena kebetulan.
- **Probe harus meniru token nyata yang bermasalah.** Probe lama memakai jabatan tak dikenal (selalu NONE), sehingga tidak pernah
  menangkap kelas "tanpa identitas". Probe baru memakai `TestAuth.tenantToken(slug, Role.SALES)`.
- **Karakterisasi perilaku yang disengaja.** Tes `factoryFlowDecision_withoutAuthorizationRepositories_staysNull` mengunci bahwa
  `null` saat repositori tak terpasang itu keputusan, bukan kelalaian.
- **Tes non-default.** Matriks dijalankan di tenant garmen dan `bordir-uji` (template bordir), plus isolasi lintas tenant.

## 4. Jebakan

- Tes gerbang tidak boleh memeriksa isi balasan rute yang bergantung Postgres (stage-flow 500 karena tenant uji tak ada di DB):
  asersinya "bukan 401/403".
- Perbedaan bypass `TENANT_ADMIN` antara `factoryFlowDecision` dan `callerDecisions` (token berdivisi tanpa jabatan) masih ada;
  dicatat sebagai Track C/Q3 di TRD, tidak diubah di sini.
