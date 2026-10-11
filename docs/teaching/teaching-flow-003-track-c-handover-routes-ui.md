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
- Setelah seluruh pemanggil klien pindah, hapus overload dan pembacaan `SackRoute`. Inilah fase
  **lock-in** Strangler Fig: klien hanya memiliki satu sumber kebenaran, yaitu respons server.

### Langkah 3: Reaktifkan State ViewModel (C2)
- Tambahkan `routeSettingsView: HandoverRouteSettingsView?` ke `FulfillmentUiState`.
- Sediakan getter cerdas:
  - `effectiveRoutes`: Mengambil rute aktif terurut; sebelum data tersedia nilainya kosong, bukan
    daftar rute industri tertentu yang ditanam di aplikasi.
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

### Blok A: Pengajuan Berbasis `HandoverRouteCode`
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

### Blok B: Satu Kontrak API Tanpa Jembatan Enum
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
```

**Mengapa ditulis begini?**
- `HandoverRouteCode` adalah value object string yang menerima kode tenant non-garment tanpa
  memperluas enum aplikasi.
- Overload `SackRoute` sengaja sudah dihapus. Mempertahankannya setelah semua call site pindah akan
  menciptakan dua cara mengirim perjalanan dan mengundang kode baru kembali ke model lama.
- Server tetap menjadi pemilik validasi keanggotaan rute tenant; UI hanya mengirim kode yang dipilih
  dari daftar efektif yang server pancarkan.

### Blok C: Decoder Klien Menolak Payload Rusak

```kotlin
val code = HandoverRouteCode.parse(row.string("route"))
    .getOrElse { throw IllegalArgumentException("$at.route: ${it.message}") }
val mode = HandoverMode.entries.firstOrNull { it.name == modeName }
    ?: throw IllegalArgumentException("$at.mode: tidak dikenal '$modeName'")
```

**Mengapa ditulis begini?**
- Baris JSON yang tidak lengkap tidak boleh dilewati dengan `mapNotNull`; itu akan membuat operator
  mengira rute memang hilang dari konfigurasi tenant.
- Mode yang tidak dikenal tidak boleh jatuh diam-diam ke `ADMIN_HUB`. Fallback mengubah makna data;
  kegagalan eksplisit membuat ketidakcocokan versi terlihat dan dapat ditangani.

### Blok D: Layar Konfigurasi Fail-Closed RBAC
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
- Keputusan `NONE` dihentikan sebelum konten konfigurasi digambar; pengguna tanpa akses tidak
  memperoleh layar baca semu.
- Tombol hanya `enabled` bila `canManage == true`. Pengguna dengan akses `VIEW` atau `OPERATE` tetap bisa meninjau alur pabriknya namun tombol disabilitas mencegah perubahan tanpa izin.
- Gaya neo-brutalisme Clay membedakan state terpilih melalui warna (`Primary` vs `Secondary`), bukan dengan menebalkan garis tepi (Design System Rule §13).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Dipilih | Risiko Alternatif |
|---|---|---|---|
| `HandoverRouteCode` value object | `SackRoute` enum | Kode tetap tervalidasi tetapi daftar nilainya boleh berbeda per tenant/pack | Enum memaksa rilis aplikasi setiap industri menambah rute |
| Respons server sebagai SSOT | Fallback daftar garment di klien | UI selalu menggambar konfigurasi efektif yang sama dengan validasi server | Dua sumber kebenaran dapat berbeda dan menyamarkan kegagalan server |
| Decoder fail-fast | `mapNotNull` + nilai default | Payload rusak terlihat sebagai error operasional yang dapat ditelusuri | Rute hilang atau berubah mode tanpa jejak |
| `apiResult` yang melempar ulang `CancellationException` | `runCatching` langsung | Pembatalan lifecycle tetap mengikuti structured concurrency | Request yang dibatalkan diperlakukan sebagai error biasa dan pekerjaan induk sulit berhenti |
| `StateFlow<FulfillmentUiState>` | Mutable state tersebar di Composable | Satu keluaran state dapat diuji tanpa UI dan berlaku lintas target | Transisi loading/error/submit mudah saling bertentangan |

---

## ⚠️ 5. Jebakan Umum & Anti-Patterns yang Dihindari

1. **Jebakan God File & Ratchet Rule**:
   - `TransferForms.kt` awalnya 392 baris. Menambah layar pengaturan dan dekomposisi di file yang sama akan melahirkan file 600+ baris.
   - Dengan memecahnya menjadi 3 file kecil (~65–200 baris), batas pemeliharaan kode tetap terjaga.
   - `App.kt` yang hampir menyentuh batas 600 baris dirampingkan melalui penggabungan branch sehingga ukurannya berkurang (ratchet rule).
2. **Jebakan Literal Hex Warna**:
   - Dilarang keras menulis `Color(0xFF2563EB)`. Seluruh warna memakai token semantik dari `WeMadeColors` (`Primary`, `Success`, `Warning`, `Error`, `OnSurface`, `OnSurfaceMuted`).
3. **Jebakan Silent Fallback**:
   - Kode lama sering kali melakukan `?: SackRoute.QC_RAJUT_TO_FINISHING` saat sebuah string rute tidak dikenal. Ini anti-pattern fatal: data tenant non-garment akan diam-diam disulap menjadi rute rajut.
   - Pola baru: String kode rute divalidasi ketat lewat `HandoverRouteCode.parse()`, dan jika tidak sah atau tidak ditemukan, ditolak secara eksplisit atau ditampilkan dalam keadaan kosong yang jelas.
4. **Jebakan Jembatan yang Tidak Pernah Dicabut**:
   - Adapter lama berguna selama migrasi, tetapi menjadi utang bila dibiarkan setelah semua pemanggil pindah.
   - Pemindai `SackRoute` pada `app/shared` dijadikan bukti bahwa Track C sudah selesai, bukan sekadar
     memiliki implementasi baru di samping implementasi lama.

---

## 🧪 6. Verifikasi & Pengujian

Verifikasi dilakukan pada target KMP dan unit test ViewModel/decoder:
1. **JVM Test**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests 'com.eventverse.app.presentation.fulfillment.FulfillmentViewModelTest'
   ```
   *Cakupan*: tenant bordir membaca rute data, pembatasan wadah terbuka pada rute DIRECT,
   penanganan tenant kosong, kegagalan `route-settings` tanpa fallback, penyimpanan mode, dan
   pengiriman kode non-garment.
2. **Test kontrak decoder**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests 'com.eventverse.app.infrastructure.api.FulfillmentRouteResponseDecoderTest'
   ```
   *Cakupan*: round-trip respons rute bordir, mode tak dikenal ditolak, dan baris tanpa kode tidak
   dilewati diam-diam.
3. **Test cancellation transport**:
   ```bash
   ./gradlew :app:shared:jvmTest --tests 'com.eventverse.app.infrastructure.api.FulfillmentTransferApiClientCancellationTest'
   ```
   *Cakupan*: `CancellationException` dari HTTP engine tetap dilempar, bukan dibungkus menjadi
   `Result.failure`.
4. **Kompilasi Multiplatform dan suite shared**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinJs \
     :app:shared:compileKotlinWasmJs :app:shared:jvmTest
   ```
   *Hasil*: Bersih di 3 target platform (JVM, JS, WasmJS).

Test terfokus berjumlah **9/9 hijau** (5 ViewModel, 3 decoder, 1 cancellation). Audit variabilitas
melaporkan 0 temuan dan pemindai `SackRoute` pada `app/shared/src` kosong.

`assembleAndroidMain` belum dapat diverifikasi di worktree ini karena Android SDK tidak tersedia
(`ANDROID_HOME`/`sdk.dir` tidak ada). Cek visual dua tenant juga belum dilakukan karena sesi ini tidak
memiliki browser lokal terautentikasi; keduanya tetap menjadi item verifikasi sebelum merge.

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] Tambahkan test payload dengan dua kode rute sama dan buktikan decoder menolaknya dengan path
  `$.routes` yang jelas.
- [ ] Buat fake data source yang mengembalikan rute nonaktif, lalu buktikan `effectiveRoutes` tidak
  menawarkannya pada form submit.
- [ ] Jalankan UI sebagai `bordir-uji` pada lebar sekitar 1280dp dan dokumentasikan apakah label rute
  panjang masih memberi ruang pada badge mode tanpa pecah per huruf.
