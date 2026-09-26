# 🎓 Modul Pembelajaran: Kontak Vendor & Penunjukan Vendor Makloon

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: DDD (Entity, Value Object, Port/Gateway), RBAC per modul, harga berversi tanggal, snapshot data transaksi, PostgreSQL RLS, Compose MVI, Aturan Tiga Kali
> **Prasyarat**: Paham `SamplingOrder` + alur proses per SPK (`customFlowProcesses`), `AccessDecisionEngine`, dan cara leg Surat Jalan diturunkan (`FlowLegDerivation`)
> **Referensi Task**: Diskusi "alur penambahan vendor saat sampling, hanya admin produksi" (branch `feat/modules`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Sebelum fitur ini, vendor makloon hanyalah **teks bebas** (`vendorRef`) yang diketik
saat menyusun alur. Akibatnya:

- Siapa pun yang bisa menyusun alur bisa menulis nama vendor apa saja, termasuk salah ketik.
  Surat Jalan lalu tercetak ke "CV Sablon Jya".
- Tidak ada daftar harga per vendor. Admin menghitung di kepala atau di WhatsApp.
- Harga yang disepakati untuk sebuah order tidak tercatat. Kalau vendor naik harga bulan depan,
  tidak ada yang tahu berapa biaya order bulan lalu.

**Analogi.** Bayangkan **buku telepon kantor + nota pesanan**:
- *Buku telepon* (Kontak Vendor) berisi nama, nomor WA, dan daftar harga yang ditempel di halamannya.
  Daftar harga boleh diganti kapan saja.
- *Nota pesanan* (Penugasan Vendor) ditulis saat memesan. Harganya **disalin** dari buku telepon hari itu.
  Mengganti daftar harga besok tidak mengubah nota yang sudah ditandatangani.

**Hasil akhir.**
1. Staf sampling cukup memilih **"Vendor Luar"** saat menyisipkan proses (misalnya Sablon).
2. Proses itu **otomatis** muncul di antrean **"Menunggu Vendor"** milik admin produksi.
3. Admin produksi memilih vendor. Harga terisi dari daftar harga dan boleh dinego.
4. Sistem menulis nama vendor ke alur SPK, sehingga **leg Surat Jalan ke vendor terbuka**.
5. Staf yang aksesnya hanya `VIEW` bisa melihat semuanya, tetapi server menolak setiap penulisan dengan **403**.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Kontrak domain di `core/domain/vendor`.**
   Kita mulai dari sini, bukan dari tabel, karena pertanyaan tersulitnya pertanyaan bisnis: *harga mana
   yang berlaku? kapan vendor boleh diganti? siapa yang boleh menunjuk?* Tabel hanya menyimpan jawabannya.
2. **Langkah 1: Value Object** (`VendorValueObjects.kt`). `VendorId`, `VendorName` (maks 150 karakter,
   karena disalin ke kolom `varchar(150)` di Surat Jalan), dan `VendorPriceUnit` beserta rumus totalnya.
3. **Langkah 2: Entity.**
   - `Vendor` beserta `VendorServiceRate`, yaitu harga berversi tanggal.
   - `VendorAssignment`, yaitu snapshot saat penunjukan.
   - `SubcontractNeed` dan `VendorAssignmentQueue`: antrean yang **diturunkan**, bukan disimpan.
4. **Langkah 3: Repository + Port.**
   - `VendorRepository` dan `VendorAssignmentRepository`.
   - **`SubcontractFlowGateway`**: pintu ke "pemilik alur" (hari ini SPK sampling, kelak PO massal).
5. **Langkah 4: Use Case.** `RegisterVendor`, `UpdateVendorProfile`, `SetVendorServiceRate`,
   `AssignVendorToProcess`, `CancelVendorAssignment`, dan `GetVendorQueue`. Masing-masing satu operasi bisnis.
6. **Langkah 5: Infrastruktur (`server`).**
   - Migrasi `V64__create_vendor_contacts.sql`: tabel, RLS, registrasi modul, backfill wewenang, seed demo.
   - Repository Postgres.
   - `SamplingSubcontractFlowGateway`.
7. **Langkah 6: Gerbang Ktor.** `ModuleAccessGuard.kt` (generik per modul) dan `VendorRoutes.kt`
   (`VIEW` untuk baca, `MANAGE` untuk tulis).
8. **Langkah 7: Client (`app/shared`).** `VendorApiClient`, lalu MVI (`VendorContactsUiState`/`ViewModel`),
   lalu layar dan komponen. Terakhir, daftarkan di `AppNavScreen`, `App.kt`, dan `ModuleWorkspaceScreen`.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Harga berversi tanggal (`Vendor.setRate`)

```kotlin
fun setRate(rate: VendorServiceRate, now: Instant): Vendor {
    val normalized = rate.copy(serviceCode = normalizeCode(rate.serviceCode), effectiveTo = null)
    val open = rates.firstOrNull { it.isOpenEnded && it.sameTrackAs(normalized) }
    val updated = when {
        open == null -> rates + normalized
        open.effectiveFrom == normalized.effectiveFrom -> rates.map { if (it === open) normalized else it }
        else -> {
            require(normalized.effectiveFrom > open.effectiveFrom) { "…harus berlaku setelah…" }
            rates.map { if (it === open) it.closedAt(normalized.effectiveFrom) else it } + normalized
        }
    }
    return copy(rates = updated, updatedAt = now)
}
```

**Mengapa blok ini ditulis begini?**
- **Tidak menimpa, tapi menutup.** Harga lama diberi `effectiveTo`, sehingga riwayatnya tetap ada.
- **"Jalur harga" = layanan + satuan.** Vendor sablon bisa punya harga *per titik* dan *per pcs* sekaligus.
  Keduanya jalur yang terpisah.
- **Tanggal mundur ditolak.** Kalau diterima, dua harga akan berlaku di hari yang sama dan tidak jelas mana yang dipakai.
- Intervalnya setengah-terbuka `[from, to)`, konvensi yang sama dengan harga bahan dan rate card HPP.

### Blok B: Snapshot + sumber harga (`AssignVendorToProcessUseCase`)

```kotlin
val listed = vendor.ratesFor(need.processCode, command.today)
    .firstOrNull { command.unit == null || it.unit == command.unit }
val price = command.pricePerUnitIdr ?: listed?.priceIdr ?: error("…isi harga manual")
val source = if (listed != null && listed.unit == unit && listed.priceIdr == price)
    VendorPriceSource.PRICE_LIST else VendorPriceSource.NEGOTIATED
```

**Mengapa blok ini ditulis begini?**
- Harga **disalin** ke `VendorAssignment`, bukan dirujuk. Laporan biaya dan tagihan vendor harus memakai
  harga saat deal.
- `PRICE_LIST` atau `NEGOTIATED` diputuskan **server**. UI hanya menampilkan tebakan yang sama.
  Kebenaran tidak boleh tinggal di layar.
- Sebelum menulis, use case memeriksa `flowGateway.isDispatched(...)`. Setelah Surat Jalan terbit,
  mengganti vendor berarti memproses retur, bukan sekadar mengganti nama.

### Blok C: Port `SubcontractFlowGateway`

```kotlin
interface SubcontractFlowGateway {
    suspend fun openNeeds(tenantId: TenantId): List<SubcontractNeed>
    suspend fun findNeed(tenantId: TenantId, subjectId: String, processCode: String): SubcontractNeed?
    suspend fun isDispatched(tenantId: TenantId, subjectId: String, processCode: String): Boolean
    suspend fun linkVendor(tenantId: TenantId, subjectId: String, processCode: String, vendorRef: String?)
}
```

**Mengapa blok ini ditulis begini?**
- Domain vendor **tidak mengimpor `SamplingOrder`**. Kalau mengimpornya, PO produksi massal kelak harus
  menyalin seluruh domain vendor.
- `linkVendor` adalah "kunci pintu" integrasi. `TenantLocationConfig.endpointFor` mengembalikan `null`
  untuk proses `SUBCONTRACTED` tanpa `vendorRef`, sehingga **tidak ada leg**. Begitu nama vendor ditulis,
  `FlowLegDerivation` otomatis menurunkan leg `SUBCONTRACT_OUTBOUND` dan `SUBCONTRACT_INBOUND`.
  Tidak ada satu baris pun kode Surat Jalan yang diubah.
- Implementasi server (`SamplingSubcontractFlowGateway`) menulis ke **salinan alur per SPK**
  (`customizeProcessFlow`), bukan ke template pabrik. Kalau ditulis ke template, seluruh SPK pabrik
  akan menunjuk vendor yang sama.

### Blok D: Migrasi, RLS, dan satu penugasan aktif

```sql
CREATE UNIQUE INDEX uq_vendor_assignment_active
    ON vendor_assignments(tenant_id, subject_id, UPPER(process_code))
    WHERE status = 'ASSIGNED';
ALTER TABLE vendor_assignments ENABLE ROW LEVEL SECURITY;
CREATE POLICY vendor_assignments_tenant_isolation ON vendor_assignments
    USING (tenant_id = CURRENT_SETTING('app.current_tenant_id', true));
```

**Mengapa blok ini ditulis begini?**
- **Partial unique index**: aturan "satu proses = satu vendor aktif" dijaga database, bukan hanya use case.
  Baris `CANCELLED` tetap tersimpan sebagai riwayat.
- **RLS**: `DatabaseFactory.dbQuery(tenantId)` menjalankan `SET LOCAL app.current_tenant_id`. Query yang
  lupa `WHERE tenant_id` tetap tidak bisa membaca data pabrik lain.
- Migrasi juga **mem-backfill** `granted_modules` dan `custom_roles`. Tanpa backfill, tenant lama tidak
  akan pernah melihat menu baru ini.

### Blok E: Gerbang wewenang generik (`ModuleAccessGuard.kt`)

```kotlin
suspend fun ApplicationCall.authorized(required: AccessLevel): TenantContext? {
    val decision = moduleDecision(BusinessModule.VENDOR_CONTACTS, tenant, roleRepository, moduleAssignmentRepository)
    return tenant.takeIf { requireModuleAccess(module, decision, required) }
}
```

**Mengapa blok ini ditulis begini?**
- Repo ini sudah punya **tiga** salinan guard yang nyaris identik (CRM, OrgChart, FactoryFlow).
  Modul keempat tidak menambah salinan keempat. Modulnya dijadikan parameter.
- Tanpa identitas, hasilnya `NONE`, jadi ditolak. Jangan menyalin fallback "boleh semua" milik guard OrgChart.
- UI memakai `decision.config.canManage`, **bukan** `canWrite`. Server menggerbang di `MANAGE`, jadi
  menampilkan tombol untuk pengguna `OPERATE` berarti menampilkan tombol yang pasti berakhir 403.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini | Risiko Alternatif |
|---|---|---|---|
| Modul sendiri `VENDOR_CONTACTS` (FOUNDATION) | Masuk ke `CRM_SALES` sebagai `Contact` bertipe vendor | CRM = sisi jual dan ber-scope hierarkis per sales; vendor = sisi produksi dan milik bersama pabrik | Sales hanya melihat vendor buatannya sendiri; admin produksi harus diberi akses CRM |
| Modul terpisah dari `MASTER_DATA` | Tab di Master Data Bahan | Pemiliknya berbeda (gudang/costing vs admin produksi) | Satu level akses untuk dua keputusan yang tidak berhubungan |
| `VendorAssignment` agregat sendiri | List di `SamplingOrder` | Satu SPK bisa punya banyak vendor; bisa dipakai PO massal; `SamplingOrder.kt` sudah 402 baris (di atas hard limit 400) | God Entity makin besar dan menempel ke sampling selamanya |
| Antrean **diturunkan** dari alur | Tombol "Minta Vendor" + status tersimpan | Tidak ada langkah yang bisa lupa ditekan | Proses subkon tanpa permintaan hilang dari radar admin |
| Rates sebagai kolom JSON di `vendors` | Tabel `vendor_rates` terpisah | Rates selalu dibaca bersama vendornya; jumlahnya kecil | (Trade-off) kueri "vendor termurah lintas pabrik" di SQL jadi sulit. Kalau kebutuhan itu datang, pecah ke tabel sendiri |
| `Long` IDR | `Money` | Konsisten dengan tetangga (`costPerPcsIdr`, `piecerateTariffIdr`) | Campuran dua tipe uang di satu alur |

---

## ⚠️ 5. Jebakan Pemula & Cara Menghindarinya

1. **Menyembunyikan tombol lalu menganggapnya aman.**
   - *Bahaya*: `curl` dengan token staf tetap bisa menunjuk vendor.
   - *Solusi*: gerbang ada di `VendorRoutes`. `VendorAccessApiTest` membuktikan `VIEW`/`OPERATE` → 403.
2. **Mencek nama jabatan (`if role == "Admin Produksi"`).**
   - *Bahaya*: jabatan dirakit per tenant, jadi di pabrik lain namanya "PPIC".
   - *Solusi*: cek **wewenang modul** lewat `AccessDecisionEngine`.
3. **Merujuk harga vendor, bukan menyalinnya.**
   - *Bahaya*: kenaikan harga ikut mengubah biaya order lama.
   - *Solusi*: snapshot di `VendorAssignment`.
4. **Menulis vendor ke template alur pabrik.**
   - *Bahaya*: semua SPK tiba-tiba memakai vendor itu.
   - *Solusi*: gateway selalu menulis ke salinan per-SPK.
5. **Menyalin pola UI kelima kalinya.**
   - Chip pilihan dan banner status sudah punya 3–4 salinan privat. Keduanya diangkat menjadi
     `ClayChoiceChip` dan `ClayStatusBanner`. Salinan lama boleh dicicil pindah.
6. **Menambah baris ke file yang sudah di atas hard limit.**
   - `OperationalModuleContract.kt` (514 baris) butuh cabang `when` baru. Dua enum digabung ke satu baris
     supaya panjang file tidak bertambah (Aturan Ratchet).
7. **Pesan error tertutup dialog.**
   - Banner ada di belakang dialog, jadi `VendorDialogFrame` menampilkan penolakan server di dalam dialog.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

- **Domain murni.** `core/src/commonTest/.../vendor/VendorTest.kt` (6 test):
  - penutupan harga lama;
  - koreksi di tanggal yang sama;
  - tolak tanggal mundur;
  - jalur satuan independen;
  - rumus total per satuan;
  - urutan antrean.
- **Use case dengan fake.** `VendorAssignmentUseCaseTest.kt` (8 test):
  - harga daftar → `PRICE_LIST` dan vendor ter-link ke alur;
  - harga beda → `NEGOTIATED`;
  - tanpa harga → gagal;
  - ganti vendor membatalkan yang lama;
  - tolak setelah Surat Jalan terbit;
  - tolak vendor nonaktif;
  - batal → unlink;
  - nama ganda ditolak.
- **Gerbang API.** `server/.../VendorAccessApiTest.kt` (5 test): semua skenario berhenti di 403 sebelum
  menyentuh database, jadi berjalan tanpa Postgres.

```kotlin
@Test
fun `assign after surat jalan issued should be rejected`() = runTest {
    seedVendor("vnd-1", "CV Sablon Jaya")
    flow.dispatched = true
    assertTrue(assign(command("vnd-1")).isFailure)
}
```

- **Lihat dengan mata.** Layar dirender di JVM memakai `ImageComposeScene` dan data palsu (antrean admin,
  antrean `VIEW`, kontak lebar/sempit, dialog penunjukan), lalu perancahnya dihapus.
  Yang belum diuji: jalur berhasil end-to-end dengan Postgres sungguhan, dan dialog di aplikasi nyata.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: pindahkan `StatusFilterChip` (TechPack), `StageFilterChip` (Deals), dan `FilterChip`
  (Production) ke `ClayChoiceChip`, lalu lihat berapa baris yang hilang.
- [ ] **Tantangan 2**: buat `BulkWorkOrderSubcontractFlowGateway` supaya PO produksi massal memakai antrean
  yang sama. Domain vendor tidak boleh berubah satu baris pun.
- [ ] **Tantangan 3**: tampilkan **biaya vendor aktual vs estimasi HPP** (`subcontractRatePerSamMinute`) di
  kartu penugasan. Tentukan: domain mana yang boleh tahu keduanya?
- [ ] **Tantangan 4**: tambahkan badge "Terlambat" bila `expectedReturnAt` < hari ini dan leg
  `SUBCONTRACT_INBOUND` belum diterima.
