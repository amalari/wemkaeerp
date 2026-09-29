# 🎓 Modul Pembelajaran: Gerbang RBAC di Setiap Route Tenant (Jalur B, B5)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Mengukur celah dulu, menutupnya dengan ratchet, dan tidak mengunci pengguna yang sah
> **Prasyarat**: `module-integration-rules.md` Kontrak 8, `tenant-variability-rules.md` Kontrak 7

---

## 💡 1. Temuan

Probe memanggil **218 route** `/api/tenant/…` sebagai anggota tenant **tanpa wewenang modul**. Hanya **65** yang
menolak. Sisanya mengembalikan data (margin HPP, invoice, harga bahan), atau bahkan **menjalankan tulis**
(`roles/restore-presets`, hapus template invoice). Penyebabnya: route hanya mensyaratkan *login ke tenant*.
Pemanggilan `callerPrincipalOrNull` di handler cuma dipakai untuk audit, bukan untuk otorisasi.

## 🧱 2. Tiga Alat

### A — `RouteGateTest` + `RouteGateLedger` (ratchet)

Test mengenumerasi semua route dan memanggilnya sebagai pengguna tanpa akses. 401/403 = menolak.
**400/404 juga dianggap bocor**: gerbang harus jalan *sebelum* body dibaca atau data dicari.

- Route tidak menolak dan tidak ada di ledger → **merah** (route baru wajib bergerbang).
- Route di ledger yang kini menolak → **merah** sampai barisnya dihapus (utang hanya boleh turun).

### B — `moduleGate` (per grup route)

```kotlin
route("/api/tenant/invoicing") {
    moduleGate(BusinessModule.INVOICING, roleRepository, moduleAssignmentRepository, write = AccessLevel.OPERATE) { method, path ->
        if (method != HttpMethod.Get && path.contains("/templates")) GateRule(AccessLevel.MANAGE, listOf(BusinessModule.INVOICING)) else null
    }
    // … semua handler di bawahnya otomatis tergerbang, termasuk yang ditambahkan kelak
}
```

### C — `TenantRouteGatePolicy` (terpusat)

`/api/tenant/sampling/orders` didefinisikan di **lima file**. Ktor menggabungkannya menjadi **satu node**, jadi
gerbang per file akan terpasang ganda. Solusinya satu kebijakan murni `(method, path) → GateRule?`, dipasang
sekali pada `/api/tenant`, dan bisa diuji tanpa server (`TenantRouteGatePolicyTest`).

## 🧭 3. Aturan: siapa boleh apa

Pembaca diturunkan dari **pemakai nyata di klien**, bukan dari nama modul:

| Data | Baca | Tulis |
|---|---|---|
| SPK sampling | Sampling · Operator · QC · CRM (detail deal) | edit SPK = Sampling OPERATE; aksi lantai = Sampling/Operator/QC OPERATE |
| Katalog bahan | Master Data · Sampling · Tech Pack (dropdown) | Master Data OPERATE |
| Harga bahan | Master Data · Costing | kebijakan harga = MANAGE |
| HPP / Invoice | modulnya | OPERATE; rate card/template/profil penerbit = MANAGE |
| Jabatan, penugasan, divisi | **belum digerbang** — klien menghitung menu dari sini | MANAGE |

## ⚠️ 4. Jebakan

1. **403 ≠ handler tidak jalan.** Buktikan lewat efek samping: `RbacWriteGateTest` memastikan `restore-presets` yang
   ditolak tidak menciptakan jabatan/divisi apa pun.
2. **Gerbang yang terlalu ketat mengunci pekerjaan sah.** Menu klien dibangun dari `/roles` & `/module-assignments`.
   Menggerbang bacanya mengosongkan menu semua operator. Sebelum menutup baca, *grep pemakai di klien*.
3. **Test lama yang lolos karena alasan yang salah.** `CostingApiTest` tetap 403, tapi yang menolak kini gerbang
   modul, bukan izin `APPROVE_COSTING`. Test diubah supaya pengguna lolos gerbang dan izin handler benar-benar diuji.
4. **Kerja DB yang tidak perlu.** `moduleDecision` mengambil penugasan divisi bahkan untuk owner yang pasti lolos,
   sehingga seluruh suite test menggantung >10 menit tanpa Postgres. Untuk owner/superadmin query dilewati:
   keputusan identik, satu query lebih sedikit per request admin.
5. **Test "bukan 403" bisa lulus karena 500.** Setiap test positif **dibuktikan merah** dengan mematikan gerbangnya.

## 🧪 5. Pembuktian

- Ledger: **151 → 39** (11 RBAC/Org Chart tulis + 48 keuangan + 52 SPK/lantai; `stage-templates` dipindah ke
  "sengaja terbuka" karena berisi katalog platform).
- `RbacWriteGateTest`, `FinancialGateTest`, `FloorGateTest`, `TenantRouteGatePolicyTest`; server 240 hijau.
- Server nyata `bordir-uji`: owner 200, operator tanpa jabatan 403. Satu-satunya pengguna tanpa jabatan di kedua DB
  adalah persona uji; semua pengguna nyata adalah owner.

## 🧭 6. Sisa

- Endpoint **"wewenang saya"** dihitung di server, lalu tutup baca RBAC/Org Chart.
- Sisa ledger: Tech Pack, Production, Fulfillment, Factory Flow, Platform.
- `PUT /sampling/orders/{id}` untuk SPK yang tidak ada menjawab 500, bukan 404 (utang handler, bukan gerbang).
