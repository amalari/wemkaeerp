# 🎓 Modul Pembelajaran: Perpustakaan Elemen CRM-Only & Preview Kanvas dari Lead CRM Nyata

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Domain-Driven Design, Output Port antar-modul, Ktor RBAC Guard, Compose Multiplatform ViewModel, Coroutines init-loading
> **Prasyarat**: Paham struktur modul `core/` (domain murni) vs `app/shared/` (presentation), dan kontrak modul §3 AGENTS.md (Input/Output Port)
> **Referensi Task**: Penghancuran grup "Invoicing" & "Penerbit" dari Perpustakaan Elemen + koneksi preview desainer template ke data CRM sungguhan

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Perpustakaan Elemen di desainer template faktur menawarkan empat grup modul (CRM, Invoicing, Penerbit, Baris Item). Grup "Invoicing" dan "Penerbit" menawarkan token yang nilainya justru **diproduksi oleh faktur itu sendiri** — menawarkannya sebagai elemen yang "diambil dari modul" menyesatkan pengguna. Di saat yang sama, kanvas preview menampilkan **contoh data palsu** ("PT Mitra Usaha Mandiri") ketika desainer dibuka tanpa alur CRM, sehingga pengguna tidak pernah melihat data asli sampai faktur diterbitkan.

**Analogi.** Perpustakaan Elemen itu seperti katalog merge field di Word: katalog harus hanya berisi sumber data yang benar-benar ada. Dan preview yang menampilkan data dummy itu seperti bolpoin uji di toko yang tintanya warna lain — pengguna tidak bisa menilai hasil akhir.

**Hasil akhir.**
1. Palet hanya menawarkan **CRM — Klien & Prospek** (keputusan produk sementara, mudah dibuka lagi).
2. Desainer yang dibuka dari workspace pun otomatis mengambil **lead CRM teratas dari backend** sebagai sumber preview — kanvas menampilkan data asli, dan strip sumber data berubah dari "Contoh data bawaan desainer" menjadi `CRM_LEAD · <nama klien> · <lead-id>`.
3. Akun owner pabrik (`TENANT_ADMIN` tanpa jabatan tenant) tidak lagi dikunci 403 dari seluruh API CRM.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Kalau menulis ini dari nol, urutannya WAJIB begini — dari lapisan paling dalam ke luar:

1. **Langkah 0 — Keputusan produk jadi kontrak domain (`core/InvoiceBindingRegistry.kt`)**.
   Keputusan "palet hanya CRM" adalah aturan *apa yang ditawarkan*, bukan bagaimana UI menggambar tombol. Maka dia hidup di domain: `val paletteModules: Set<BindingModuleSource> = setOf(BindingModuleSource.CRM_SALES)`. UI hanya membaca `standaloneModules()`.
2. **Langkah 1 — Kontrak data antar modul (`InvoicePrefillCoordinator.kt`)**.
   Tambahkan `InvoicePrefillData.fromCrmLead(lead)` — satu-satunya penerjemah `CrmLead` → prefill faktur. Pemetaannya **harus identik** dengan tombol "Generate Invoice" di CRM, kalau tidak preview dan hasil terbit akan menampilkan data berbeda.
3. **Langkah 2 — ViewModel (`TemplateDesignerViewModel.kt`)**.
   Suntikkan `CrmRemoteDataSource`, lalu di `init` bila tidak ada prefill: ambil lead, bangun preview, ukur ulang tinggi elemen (`measured()`).
4. **Langkah 3 — Guard server (`server/routes/CrmAccessGuard.kt`)**.
   Perluas bypass owner. Ini di server karena "jangkauan yang hanya ditegakkan klien bukan jangkauan".
5. **Langkah 4 — Test** di tiga lapis (domain, presentasi, API).

---

## 🔬 3. Bedah Kode Blok per Blok

### A. Filter palet di domain — kenapa bukan `if` di Composable?

```kotlin
val paletteModules: Set<BindingModuleSource> = setOf(BindingModuleSource.CRM_SALES)

val standaloneTokens: List<BindingDescriptor> =
    DOCUMENT.filter { it.moduleSource in paletteModules }

fun standaloneModules(): List<BindingModuleSource> =
    BindingModuleSource.entries.filter { it in paletteModules }
```

**Mental model**: registry itu *katalog lengkap*, `paletteModules` itu *etalase*. Yang dietalase boleh berubah tiap minggu; yang digambar PDF (`descriptorFor("issuer.companyName")`) tidak boleh ikut hilang. Dua test mengunci dua sisi ini — lihat `InvoiceBindingRegistryPaletteTest`.

⚠️ **Jebakan**: menghapus `issuer.*`/`invoice.*` dari `DOCUMENT`. Template seed dan `InvoicePdfRenderer` masih memakainya — menghapusnya merusak PDF **diam-diam**, hanya ketahuan saat user mencetak.

### B. Preview dari lead nyata — kenapa lewat `InvoicePrefillData`, bukan langsung `CrmLead` → `Invoice`?

```kotlin
private fun loadLiveCrmPreview() {
    scope.launch {
        crmDataSource.getLeads(tenantSlug).onSuccess { leads ->
            val lead = leads.firstOrNull { !it.isArchived } ?: return@launch
            val prefill = InvoicePrefillData.fromCrmLead(lead)
            val preview = TemplateDesignerUiState.fromPrefill(prefill, tenantSlug, now)
            _uiState.update { current ->
                current.copy(
                    prefillData = prefill,
                    previewInvoice = preview,
                    template = measured(current.template, preview)
                )
            }
        }
    }
}
```

- **Satu jalur konstruksi**: `fromPrefill` sudah punya aturan (tanggal terbit hari ini, jatuh tempo +14 hari, status `DRAFT`, fallback nama klien). Membangun `Invoice` langsung dari `CrmLead` berarti menduplikasi aturan itu — pasti melenceng.
- **`measured()` bukan hiasan**: tinggi kotak teks & baris tabel itu *turunan dari isi*. Preview ganti → jumlah baris teks bisa ganti → tinggi tersimpan basi. Lupa mengukur ulang = kotak seleksi kanvas "telat sekali persist".
- **Fallback diam**: `onFailure` sengaja tidak menampilkan error. Kanvas yang gagal ambil CRM tetap menampilkan contoh data + strip label "Contoh data bawaan desainer" — kegagalan jaringan tidak boleh tampil sebagai kanvas rusak.
- **`isSampleData` jadi benar otomatis**: karena kita mengisi `prefillData`, properti turunan `isSampleData = prefillData == null` dan `dataSourceLabel` ikut benar tanpa logika tambahan.

### C. Owner bypass — kenapa `TENANT_ADMIN` dianggap owner?

```kotlin
isOwnerOrSuperAdmin = (principal.isPlatformSuperadmin || principal.role == Role.TENANT_ADMIN) && role == null,
```

Sebelumnya, owner pabrik tanpa jabatan tenant (custom role) dan tanpa penugasan departemen jatuh ke `AccessLevel.NONE` → 403 di **seluruh** CRM, padahal dia pemilik tenant (seed `V3__seed_demo_owner.sql` menyebutnya *owner*). Syarat `role == null` tetap ada: begitu owner diberi jabatan tenant, **matriks jabatan itu yang menang** — bypass tidak menimpa keputusan RBAC tenant.

⚠️ **Jebakan**: menyalin pola nullable-permissive dari `OrgChartAccessGuard` ("null → bolehkan"). Di CRM, null **menolak** — jangan pernah menyalin kompromi migrasi ke permukaan baru.

---

## 🛠️ 4. Teknologi & Pendekatan (The "Why")

| Keputusan | Alternatif yang ditolak | Kenapa |
|---|---|---|
| `paletteModules` di domain | Flag `hideInPalette` di UI | UI tidak boleh memutuskan kontrak output port modul; dua layar (palet + inspektur) membaca sumber yang sama |
| `CrmRemoteDataSource` disuntik ke VM (interface) | Panggil `CrmApiClient()` langsung di test | Aturan test presentasi: VM diuji dengan fake, bukan HTTP nyata (`FakeCrmRemoteDataSource`) |
| Reuse `fromPrefill` | Mapper `CrmLead`→`Invoice` langsung | Satu sumber aturan draft; DRY di sini mencegah preview ≠ dokumen terbit |
| Bypass di guard server | Bypass di klien saja | Wewenang yang hanya ditegakkan klien bukan wewenang |

---

## ⚠️ 5. Jebakan Pemula

1. **Menghapus token dari registry, bukan dari etalase** — PDF & template seed ikut rusak. Kuncinya: `DOCUMENT` ≠ `standaloneTokens`.
2. **Lupa `measured()` setelah ganti preview** — tinggi elemen basi, kanvas tampak "rusak acak".
3. **Membuat `getLeads` error-blocking** — CRM down membuat desainer tak bisa dibuka. Ini data *preview*, bukan data *transaksi*; gagal = fallback, bukan gagal layar.
4. **Menebak bahwa 403 itu bug klien** — guard-nya deny-by-default; perbaikannya di server, bukan di header klien.
5. **Menyalin pemetaan lead → prefill di dua tempat** — LeadInspectorPane dan `fromCrmLead` harus identik; kalau beda, preview dan invoice terbit menampilkan klien berbeda.

---

## ✅ 6. Verifikasi

```bash
# Domain & presentasi
cd /Volumes/amalari/Projects/wemade
./gradlew :core:jvmTest --tests "com.eventverse.app.domain.invoicing.template.*"
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.invoicing.template.*"
# -> TemplateDesignerViewModelTest: 25 tests, 0 failures

# Server (guard)
./gradlew :server:test --tests "*TenantIsolationApiTest" --tests "*RbacApiTest" --tests "*InvoicingApiTest"

# Kompilasi 5 target
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :server:compileKotlin

# Live: token owner kini boleh baca CRM (dulu 403)
curl -X POST "localhost:3000/api/public/auth/demo?tenantSlug=wemade-demo&role=TENANT_ADMIN" # ambil token
curl -H "Authorization: Bearer $TOKEN" -H "X-Tenant-Slug: wemade-demo" localhost:3000/api/tenant/crm/leads  # -> 200
```

Verifikasi visual: buka `/invoicing` → **Desain Template** → Perpustakaan Elemen hanya menampilkan grup CRM, dan kanvas menampilkan data lead asli (strip sumber: `CRM_LEAD · …`).

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Tambahkan `Dropdown` di desainer untuk **memilih lead** mana yang jadi sumber preview (bukan selalu lead teratas). Di lapisan mana event barunya masuk?
- [ ] **Tantangan 2**: Buka ulang grup "Penerbit" di palet **hanya** bila profil penerbit tenant sudah diisi (cek dulu lewat `getIssuerProfile`). Petunjuk: `paletteModules` harus jadi fungsi, bukan `val`.
- [ ] **Tantangan 3**: Terapkan bypass owner yang sama di `OrgChartAccessGuard` dan `TechPackRoutes` lalu tuliskan test yang membuktikan perilaku ketiganya konsisten.
