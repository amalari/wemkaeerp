# 🎓 Modul Pembelajaran: Rute Serah Terima sebagai Data — Track C (Klien & UI Compose)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Kotlin Multiplatform, Compose Multiplatform, Strangler Fig Migration, UI Decomposition, Dynamic Data-Driven Forms, Fail-Closed RBAC  
> **Prasyarat**: Dasar Compose Multiplatform, arsitektur MVI/StateFlow, dan konsep dasar DDD di WeMade ERP  
> **Referensi Task**: [PLAN-handover-routes-as-data.md](file:///Volumes/amalari/Projects/wemkaeerp/docs/plannings/PLAN-handover-routes-as-data.md) · [TRD-FLOW-003](file:///Volumes/amalari/Projects/wemkaeerp/docs/trd/TRD-FLOW-003-handover-routes-as-data.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Sebelum fitur ini dimigrasikan, rute perpindahan karung antar divisi (misalnya dari QC Rajut ke Finishing) di-hardcode ke dalam sebuah enum bernama `SackRoute`. Di pabrik konveksi rajut, enum ini mencakup dua rute bawaan: `QC_RAJUT_TO_FINISHING` dan `FINISHING_TO_QC_FINISHING`.

Namun, WeMade Flow adalah platform lintas industri. Ketika tenant bordir (`bordir-uji`) atau percetakan menggunakan aplikasi ini, rute mereka sama sekali berbeda (misal: *Digitizing ke Pembidangan*, *Pembidangan ke Mesin Bordir*). Jika UI mengiterasi `SackRoute.entries`:
1. Tenant bordir dipaksa melihat rute rajut yang membingungkan operator lantai produksi.
2. Tenant tidak bisa menambah atau mematikan rute sendiri tanpa meminta programmer merilis APK/bundle baru.
3. Rute yang tidak sengaja disentuh berisiko mengalami *silent fallback* ke enum default, menyamarkan data asli.

### Analogi Sederhana
Bayangkan sebuah formulir tiket pesawat di aplikasi agen travel. Jika nama bandara di-*hardcode* di dalam dropdown (hanya CGK dan SUB), maka ketika maskapai membuka rute baru ke DPS atau KNO, aplikasi Anda langsung usang dan tidak bisa dipakai. Daftar rute harus berasal dari **data dinamis yang dikirim server**, bukan daftar enum tetap di dalam aplikasi klien.

### Hasil Akhir yang Diharapkan
1. **Daftar rute dinamis**: Form pengajuan karung membaca `effectiveRoutes` dari server, sehingga tenant bordir melihat rute bordir, dan konveksi melihat rute konveksi.
2. **Formulir cerdas berbasis mode**: Tiap rute memiliki mode (`ADMIN_HUB` vs `DIRECT`). Memilih chip rute secara langsung mengubah isian form (timbangan & foto vs jumlah pcs).
3. **Layar pengaturan rute**: Admin pabrik dapat mengatur mode rute serah terima melalui layar khusus dengan wewenang `MANAGE` (fail-closed).
4. **File decomposition bersih**: Menghindari God File Compose (memecah formulir 392 baris menjadi komponen bukti, form pengajuan, dan form aksi terfokus).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus menulis ulang fitur klien ini dari nol:

### Langkah 1: Audit dan Pemecahan File (Decomposition First — C1)
- **Aturan**: Jangan pernah menambahkan fitur baru ke file yang sudah mendekati ambang batas ukuran (Rule §14: Soft 400, Hard 600 di Compose).
- `TransferForms.kt` berukuran 392 baris. Menambah logika rute baru akan langsung melanggar batas 400 baris.
- **Tindakan**: Pecah berdasarkan tanggung jawab:
  - `TransferEvidence.kt`: Penanganan berkas foto bukti & serialisasi payload goresan tanda tangan.
  - `TransferActionSections.kt`: Aksi pasca-keberangkatan (`ApproveSection`, `ReceiveSection`, `ResubmitSection`).
  - `TransferForms.kt`: Form pengajuan awal saat karung dipindai (`SubmitSackForm`).

### Langkah 2: Abstraksi Data Source & API Client (C2)
- Perluas antarmuka `FulfillmentTransferRemoteDataSource` untuk mendukung:
  - `routeSettingsView`: Membaca rute berbasis data (`HandoverRouteSettingsView`).
  - `updateRouteModes`: Mengubah mode per rute (`PUT /route-settings`).
  - `submit`: Mengirim `routeCode: HandoverRouteCode` string alih-alih enum `SackRoute`.
- Pertahankan overload backward compatibility agar pemanggil lama tidak rusak saat masa transisi Strangler Fig.

### Langkah 3: Reaktifkan State ViewModel (C2)
- Tambahkan `routeSettingsView: HandoverRouteSettingsView?` ke `FulfillmentUiState`.
- Sediakan getter cerdas:
  - `effectiveRoutes`: Mengambil rute aktif terurut, dengan fallback yang aman saat proses memuat.
  - `routesAccepting(isClosedSack)`: Menyaring rute mana yang menerima karung tertutup vs bundel terbuka.
  - Penanganan tenant tanpa rute (`effectiveRoutes.isEmpty()`).

### Langkah 4: Bangun Layar Pengaturan Rute & Navigasi (C3)
- Buat `FulfillmentRouteSettingsScreen.kt` menggunakan Neo-Brutalist Clay design system (tanpa hex warna mentah).
- Tambahkan rute navigasi di `AppNavScreen.FULFILLMENT_ROUTE_SETTINGS`.
- Lindungi dengan gerbang RBAC `accessDecisions` di `App.kt` (fail-closed).

### Langkah 5: Unit Test dengan Fake Non-Garment (C4)
- Buat `FulfillmentViewModelTest.kt` dengan data tenant non-garment (`bordir-uji`).
- Buktikan bahwa rute non-garment terbaca dan terkirim dengan benar ke server tanpa menyentuh `SackRoute`.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengajuan Berbasis `HandoverRouteCode` yang Kompatibel Mundur
Di [TransferForms.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/fulfillment/TransferForms.kt):

```kotlin
val allowedRoutes = state.routesAccepting(isClosedSack = !isBundleCard)
val allActiveRoutes = state.effectiveRoutes

var selectedRouteCode by remember(allowedRoutes) {
    mutableStateOf(allowedRoutes.firstOrNull()?.route?.code ?: allActiveRoutes.firstOrNull()?.route?.code)
}
```

**Mengapa ditulis begini?**
- `allActiveRoutes` berasal dari `state.effectiveRoutes` (data murni dari server). Tidak ada lagi hardcode `SackRoute.entries`.
- `remember(allowedRoutes)` memastikan jika wadah yang dipindai berganti dari karung tertutup ke bundel (atau konfigurasi rute diperbarui), state pilihan rute otomatis menyesuaikan diri ke rute pertama yang diizinkan.
- Jika tenant tidak memiliki rute sama sekali (`allActiveRoutes.isEmpty()`), form menampilkan pesan edukatif dan tombol submit tidak aktif, bukan melempar crash.

### Blok B: Jembatan Overload di API Client
Di [FulfillmentTransferApiClient.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/infrastructure/api/FulfillmentTransferApiClient.kt):

```kotlin
suspend fun submit(
    tenantSlug: String,
    sackPayload: String,
    routeCode: HandoverRouteCode,
    dispatchWeightKg: String?,
    dispatchScalePhotoKey: String?,
    requestedBy: String,
    notes: String,
    declaredPcs: Int?
): Result<InternalTransfer>

suspend fun submit(
    tenantSlug: String,
    sackPayload: String,
    leg: SackRoute,
    ...
): Result<InternalTransfer> = submit(
    tenantSlug = tenantSlug,
    sackPayload = sackPayload,
    routeCode = leg.toRouteCode(),
    ...
)
```

**Mengapa ditulis begini?**
- Ini adalah implementasi pola **Strangler Fig**. Implementasi utama beroperasi pada `HandoverRouteCode`.
- Metode lama yang menerima `SackRoute` dipertahankan sebagai inline adapter yang memanggil implementasi utama lewat jembatan `leg.toRouteCode()`.
- Seluruh kode yang belum dimigrasi tetap dapat mengompilasi tanpa breaking change.

### Blok C: Layar Konfigurasi Fail-Closed RBAC
Di [FulfillmentRouteSettingsScreen.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/fulfillment/FulfillmentRouteSettingsScreen.kt):

```kotlin
val level = decision.config.level
val canManage = level.weight >= AccessLevel.MANAGE.weight || persona?.isOwnerOrSuperAdmin == true
...
// Pemilih Mode
Row(horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)) {
    ClayButton(
        text = "Lewat Meja Admin",
        style = if (selectedMode == HandoverMode.ADMIN_HUB) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
        enabled = canManage,
        onClick = { onModeSelected(HandoverMode.ADMIN_HUB) }
    )
    ClayButton(
        text = "Antar Langsung",
        style = if (selectedMode == HandoverMode.DIRECT) ClayButtonStyle.Primary else ClayButtonStyle.Secondary,
        enabled = canManage,
        onClick = { onModeSelected(HandoverMode.DIRECT) }
    )
}
```

**Mengapa ditulis begini?**
- Tombol hanya `enabled` bila `canManage == true`. Pengguna dengan akses `VIEW` atau `OPERATE` tetap bisa meninjau alur pabriknya namun tombol disabilitas mencegah perubahan tanpa izin.
- Gaya neo-brutalisme Clay membedakan state terpilih melalui warna (`Primary` vs `Secondary`), bukan dengan menebalkan garis tepi (Design System Rule §13).

---

## ⚠️ 4. Jebakan Umum & Anti-Patterns yang Dihindari

1. **Jebakan God File & Ratchet Rule**:
   - `TransferForms.kt` awalnya 392 baris. Menambah layar pengaturan dan dekomposisi di file yang sama akan melahirkan file 600+ baris.
   - Dengan memecahnya menjadi 3 file kecil (~65–200 baris), batas pemeliharaan kode tetap terjaga.
   - `App.kt` yang hampir menyentuh batas 600 baris dirampingkan melalui penggabungan branch sehingga ukurannya berkurang (ratchet rule).
2. **Jebakan Literal Hex Warna**:
   - Dilarang keras menulis `Color(0xFF2563EB)`. Seluruh warna memakai token semantik dari `WeMadeColors` (`Primary`, `Success`, `Warning`, `Error`, `OnSurface`, `OnSurfaceMuted`).
3. **Jebakan Silent Fallback**:
   - Kode lama sering kali melakukan `?: SackRoute.QC_RAJUT_TO_FINISHING` saat sebuah string rute tidak dikenal. Ini anti-pattern fatal: data tenant non-garment akan diam-diam disulap menjadi rute rajut.
   - Pola baru: String kode rute divalidasi ketat lewat `HandoverRouteCode.parse()`, dan jika tidak sah atau tidak ditemukan, ditolak secara eksplisit atau ditampilkan dalam keadaan kosong yang jelas.

---

## 🧪 5. Verifikasi & Pengujian

Verifikasi dilakukan pada 3 target kompilasi KMP dan unit test ViewModel:
1. **JVM Test**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests 'com.eventverse.app.presentation.fulfillment.FulfillmentViewModelTest'
   ```
   *Hasil*: 4 tes lolos (tenant bordir membaca 2 rute data, pembatasan wadah terbuka pada rute DIRECT, penanganan tenant kosong, dan pengiriman form dengan kode non-garment).
2. **Kompilasi Multiplatform**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinJs :app:shared:compileKotlinWasmJs
   ```
   *Hasil*: Bersih di 3 target platform (JVM, JS, WasmJS).
