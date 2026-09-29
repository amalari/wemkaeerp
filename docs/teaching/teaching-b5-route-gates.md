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
| Tech pack | Tech Pack · Costing | Tech Pack OPERATE |
| Work order produksi | Produksi · CRM (detail deal) | Produksi OPERATE; `launch-from-deal` = Produksi/CRM |
| Surat jalan & fulfillment | Fulfillment | Fulfillment OPERATE |
| Kanvas Factory Flow | Factory Flow | (guard lama, fail-closed) |
| `billing-preview`, `customization-requests` | admin tata kelola (RBAC MANAGE) — tidak dipanggil klien | idem |
| Jabatan, penugasan, detail/arsip divisi | RBAC / Org Chart VIEW (menu dari `/me/access`) | MANAGE |

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

- Ledger: **151 → 5** (11 RBAC/Org Chart tulis + 48 keuangan + 52 SPK/lantai + 34 Tech Pack/Produksi/Surat
  jalan/Fulfillment/Pipeline/Platform; `stage-templates` dipindah ke "sengaja terbuka" karena berisi katalog
  platform), lalu **5 → 0** setelah `GET /api/tenant/me/access`. Ledger kini kosong: setiap route tenant baru
  wajib bergerbang sejak hari pertama.
- `RbacWriteGateTest`, `FinancialGateTest`, `FloorGateTest`, `TenantRouteGatePolicyTest`; server 240 hijau.
- Server nyata `bordir-uji`: owner 200, operator tanpa jabatan 403. Satu-satunya pengguna tanpa jabatan di kedua DB
  adalah persona uji; semua pengguna nyata adalah owner.

## 🔑 6. `GET /api/tenant/me/access`

Dulu klien mengunduh jabatan & penugasan **semua orang** lalu menghitung menunya sendiri, sehingga matriks wewenang
pabrik terbaca setiap anggota tenant. Kini server menghitung keputusan pemanggil dengan **jalur yang sama** dengan
gerbang (`callerDecisions`), jadi menu dan gerbang mustahil berbeda pendapat.

- Switcher persona benar-benar login ulang, jadi persona aktif = pemilik token, dan keputusan server berlaku untuknya.
- Klien hanya meminta daftar jabatan/penugasan bila server menyatakan akses RBAC. Tanpa itu tidak ada 403 sia-sia,
  dan tidak ada jabatan contoh (`createFactoryPresets`) yang tampil seolah milik pabrik.
- Tanpa keputusan server (offline/server lama), perilaku lama dipertahankan sebagai cadangan.
- Bukti visual: menu Sales `wemade-demo` = CRM/Sampling/Invoice "Input" + 6 modul "Lihat", 0 error console; layar RBAC
  superadmin memuat 6 jabatan.

## 🧭 7. Sisa

- Baca kanvas Factory Flow **tidak lagi fail-open**: dulu terbuka untuk semua anggota tenant, kini butuh
  Factory Flow VIEW (`PipelineModuleApiTest` diperbarui dengan alasan eksplisit).
- `moduleDecision` juga melewati query penugasan untuk pengguna **tanpa divisi** (`resolveDepartmentAccess`
  tidak pernah mencocokkannya).
- `PUT /sampling/orders/{id}` untuk SPK yang tidak ada menjawab 500, bukan 404 (utang handler, bukan gerbang).
