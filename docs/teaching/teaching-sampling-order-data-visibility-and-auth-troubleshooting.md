# 🎓 Modul Pembelajaran: Investigasi Visibilitas Data Sampling & Mekanisme Auth Guard

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Multi-Tenant Auth Guard, Reactive State Pipeline, Dynamic Routing, Query Filtering & Limit Removal  
> **Prasyarat**: Dasar Kotlin Multiplatform, Compose StateFlow, JWT Multi-Tenancy, dan Ktor Server Routes  
> **Referensi Task**: Troubleshooting & Normalisasi Visibilitas Data SPK Sampling (`/sampling-order`)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat membuka URL rute frontend mandiri seperti `http://localhost:3000/sampling-order`, pengguna sering kali mengira data database "hilang" atau "tidak dibuat", padahal akar masalahnya berasal dari lapisan proteksi autentikasi (*Auth Guard*) atau *slice truncation* di lapisan presentasi:
1. **Auth Guard Interception**: Aplikasi menerapkan arsitektur *Zero Trust Multi-Tenant*. Ketika URL dibuka di jendela browser baru tanpa token autentikasi aktif di local storage, sistem menampilkan modal pembatas akses (`AuthGuardCard`).
2. **Hardcoded Slicing di UI**: Di lapisan workbench presentasi, tombol filter cepat SPK sebelumnya sengaja di-slice dengan `take(5)`. Akibatnya, ketika database memiliki 8 SPK hasil migrasi seed baru (`SPK-SMP-0005` s/d `SPK-SMP-0008`), data-data baru yang berada di urutan berikutnya tidak tampak di baris tombol selektor cepat.
3. **Penyusutan Tenant Slug**: Jika persona aktif bernilai null, slug tenant berisiko jatuh ke string kosong (`""`), yang dapat memicu ketidaksinkronan data tenant pada klien API.
4. **Denormalisasi Nama Klien**: Pada pembuatan SPK dari Deal CRM, nama klien sempat tersimpan sebagai ID kontak mentah (`deal-1789...`) bukannya nama perusahaan pelanggan.

### Analogi Sederhana
Bayangkan sebuah **Brankas Dokumen Produksi di Pabrik**:
- Jika Anda belum menunjukkan tanda pengenal karyawan di pintu gerbang (*Auth Guard*), penjaga tidak akan membuka ruang arsip sama sekali.
- Ketika Anda sudah masuk ke ruang arsip, rak display hanya menyediakan 5 gantungan map pertama (*take(5)*). Lembar SPK nomor 6, 7, dan 8 sebenarnya ada di dalam lemari, namun tidak dipajang di etalase depan.
- Solusinya adalah membuka etalase seluas-luasnya dengan rel geser (*horizontal scroll*) tanpa membatasi jumlah map yang digantung.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penyelidikan (Order of Operations)

Jika menghadapi keluhan "data di halaman X tidak muncul", jangan langsung mengubah database atau membuat ulang data! Ikuti urutan investigasi sistematis:

```
Lapisan 1: DATABASE (Ground Truth)
   └── docker exec ... psql -> SELECT COUNT(*) FROM sampling_orders;
   └── Pastikan data fisik benar-benar tersimpan di PostgreSQL.
           │
Lapisan 2: BACKEND API & PROXY
   └── curl -H "Authorization: Bearer ..." http://localhost:8080/api/tenant/sampling/orders
   └── Pastikan endpoint Ktor mengembalikan JSON 200 OK dengan data lengkap.
           │
Lapisan 3: AUTH & SESSION CONTEXT
   └── Cek localStorage browser. Apakah user sudah login? Apakah token valid?
           │
Lapisan 4: PRESENTATION LOGIC & UI SLICING
   └── Periksa state mapping di ViewModel dan Composable Screen (apakah ada .take(N), filter status, atau default selection).
```

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Menghapus Pembatasan Selektor Cepat (`take(5)`)

Di file `SamplingWorkspaceScreen.kt`:

```kotlin
// ❌ SEBELUM: Hanya menampilkan maksimal 5 tombol SPK teratas
state.orders.take(5).forEach { order ->
    val isSelected = order.id == state.selectedOrderId
    ClayButton(
        text = order.spkNumber.value,
        style = if (isSelected) ClayButtonStyle.Accent else ClayButtonStyle.Secondary,
        ...
    )
}

// ✅ SESUDAH: Menampilkan seluruh SPK dalam daftar scroll horizontal
state.orders.forEach { order ->
    val isSelected = order.id == state.selectedOrderId
    ClayButton(
        text = order.spkNumber.value,
        style = if (isSelected) ClayButtonStyle.Accent else ClayButtonStyle.Secondary,
        fontSize = 10.sp,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        onClick = { viewModel.onEvent(SamplingUiEvent.SelectOrder(order.id)) }
    )
}
```

### 3.2 Resolusi Aman Tenant Slug di `ModuleWorkspaceScreen.kt`

```kotlin
// ✅ Fallback deterministik ke workspace demo tenant aktif
val resolvedSlug = persona?.tenantSlug?.takeIf { it.isNotBlank() } ?: "wemade-demo"

if (module == BusinessModule.SAMPLING_ORDER) {
    SamplingWorkspaceScreen(
        tenantSlug = resolvedSlug,
        decision = decision,
        persona = persona,
        modifier = modifier.fillMaxSize()
    )
    return
}
```

### 3.3 Normalisasi Nama Klien di Endpoint Deal Sampling

Di file `DealRoutes.kt`:

```kotlin
// ✅ Cari nama kontak/brand asli sebelum menerbitkan perintah SPK
val clientNameResolved = contactRepository.findById(tenant.tenantId, existing.contactId)?.displayName
    ?: existing.contactId.value

val command = SamplingFromDealCommand(
    tenantId = tenant.tenantId,
    dealId = dealId.value,
    clientName = clientNameResolved,
    styleName = json.string("styleName") ?: "",
    ...
)
```

---

## ⚠️ 4. Jebakan Pemula (Common Pitfalls)

1. **Mengira Data Hilang Karena Belum Login**:
   Saat mengakses URL langsung di browser (misalnya mengetik `localhost:3000/sampling-order`), jika sesi tersimpan belum ada atau kedaluwarsa, layar akan dicegat oleh `AuthGuardCard`. Selalu masuk terlebih dahulu (misalnya via tombol *Demo Mode: Masuk Cepat*) sebelum memeriksa data modul.
2. **Tidak Memeriksa Urutan Sorting Backend**:
   Backend menyortir `sampling_orders` berdasarkan `updated_at DESC`. SPK yang baru saja dibuat atau diedit akan berada di urutan pertama, sedangkan SPK awal akan bergeser ke kanan.
3. **Mengisi String ID Teknis ke Kolom Human-Readable**:
   Hindari meneruskan ID mentah seperti `deal-1789...` atau `con-001` ke field yang ditujukan untuk dibaca manusia (`clientName`). Selalu lakukan dereferensi nama master data.

---

## 🧪 5. Verifikasi & Pengujian Mandiri

1. **Verifikasi Database**:
   ```bash
   docker exec -i wemade-postgres psql -U postgres -d wemade_erp -c "SELECT id, spk_number, client_name, status FROM sampling_orders;"
   ```
   Pastikan terdapat 8 entitas SPK lengkap (`SPK-SMP-0001` s/d `SPK-SMP-0008`).

2. **Verifikasi Visual Web**:
   Buka `http://localhost:3000/sampling-order`:
   - Pastikan masuk ke akun (Owner Pabrik / Admin).
   - Periksa toolbar atas: pill badge menampilkan **8 SPK**.
   - Cek deretan tombol SPK: `SPK-SMP-0004`, `0003`, `0002`, `0001`, `0005`, `0007`, `0008`, `0006` semuanya dapat diklik.
   - Beralih ke tab **Pipeline Kanban** untuk melihat sebaran kartu SPK di setiap tahapan mesin dan finishing.
