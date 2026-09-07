# 🎓 Modul Pembelajaran: Implementasi Login UI (Compose Multiplatform) & Google OAuth 2.0 di Multi-Tenant SaaS

> **Level Target**: Junior to Mid-Level Software Engineer  
> **Topik Utama**: UI/UX Design System (ui-ux-pro-max), Compose Multiplatform (MVI Pattern), Google OAuth 2.0 (OpenID Connect), Multi-Tenancy Scoped Authentication, Ktor Server Endpoints, JWT Session Generation  
> **Prasyarat**: Dasar Kotlin, Konsep MVI (Model-View-Intent), Dasar HTTP & JSON, Pemahaman Multi-Tenancy ([Issue #11](file:///Volumes/amalari/Projects/wemade/docs/teaching/teaching-issue-11-saas-multitenancy-rls.md))  
> **Referensi Task**: [GitHub Issue #1](https://github.com/amalari/wemade-erp/issues/1) — *Phase 1: Core & Auth Foundation*

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata di Industri Konveksi & Garmen
Di sebuah pabrik garmen, tipe penggunanya sangat beragam:
1. **Manajemen / Owner / Sales / PPIC**: Bekerja di depan laptop atau kantor, terbiasa dengan akun email Google Workspace perusahaan. Mengetik password panjang setiap kali membuka sistem sangat tidak efisien.
2. **Operator Mesin / Penjahit / QC / Staff Gudang**: Bekerja di lantai produksi, memegang tablet atau smartphone. Banyak dari mereka tidak mengingat kata sandi email yang rumit, namun **semua orang aktif menggunakan WhatsApp**.
3. **Bahaya Multi-Tenancy**: Jika user login dengan Google (`budi@gmail.com`), sistem **tidak boleh** langsung mengizinkannya masuk ke sembarang pabrik! Budi harus diverifikasi: *"Apakah Budi memang staf yang terdaftar di Pabrik Konveksi Berkah (`berkah-konveksi`) dan bukan penyusup dari Pabrik Konveksi Maklon Jaya?"*

### Analogi Sederhana: "Kartu Akses KTP + Daftar Karyawan Resepsionis"
- **Google OAuth**: Seperti KTP / Paspor resmi yang membuktikan bahwa identitas Anda asli (dikeluarkan oleh pihak terpercaya, yaitu Google).
- **Tenant Validation di Ktor**: Ketika Anda menunjukkan KTP Anda ke resepsionis gedung kantor ("Pabrik Berkah"), resepsionis akan memeriksa daftar karyawan internal: *"Nama Budi ada di daftar karyawan kami dan statusnya aktif sebagai Supervisor PPIC. Silakan masuk!"*
- Jika Anda membawa KTP asli tapi nama Anda tidak ada di buku tamu pabrik tersebut, Anda tetap dilarang masuk (**403 Forbidden**).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Tech Lead memberikan tugas: *"Tolong buatkan Login UI dengan Google Sign-In dan tab WhatsApp untuk WeMade ERP!"*, urutan kerja (*order of operations*) yang benar adalah:

```
[Langkah 1: Pure Domain Layer (core)]
  ├── Definisikan UserSession dan AuthToken value object
  └── Buat AuthenticateWithGoogleUseCase (validasi Tenant + Email)
         ↓
[Langkah 2: Server Infrastructure (server)]
  ├── Buat JwtTokenService (penerbitan token sesi internal HMAC256)
  ├── Bangun GoogleAuthService (panggilan verifikasi ke Google tokeninfo)
  └── Daftarkan rute API /api/public/auth/google di Application.kt
         ↓
[Langkah 3: Design Tokens & Theme (app/shared)]
  ├── Jalankan skill `ui-ux-pro-max` untuk menentukan palet SaaS B2B (#2563EB, #EA580C)
  └── Definisikan WeMadeColors, Typography, dan WeMadeTheme
         ↓
[Langkah 4: MVI Presentation State-Holder (app/shared)]
  ├── Rancang LoginUiState, LoginUiEvent, dan LoginUiEffect
  └── Bangun AuthViewModel untuk menangani perpindahan tab dan trigger autentikasi
         ↓
[Langkah 5: Compose Multiplatform UI (app/shared)]
  ├── Bangun LoginScreen (Glassmorphic card, Canvas vector logo Google, Dual tab selector)
  └── Mount LoginScreen ke dalam App.kt
         ↓
[Langkah 6: Automated Testing & Verifikasi]
  └── Unit test Domain, Integration test Ktor Server, dan Unit test AuthViewModel
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok 1: Domain Use Case Penjaga Gerbang Tenant (`AuthenticateWithGoogleUseCase.kt`)

Lokasi: [`core/src/commonMain/kotlin/.../domain/auth/AuthenticateWithGoogleUseCase.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/auth/AuthenticateWithGoogleUseCase.kt)

```kotlin
class AuthenticateWithGoogleUseCase(
    private val userRepository: UserRepository,
    private val tenantRepository: TenantRepository
) {
    suspend operator fun invoke(command: AuthenticateWithGoogleCommand): Result<User> = runCatching {
        require(command.profile.emailVerified) { "Email dari Google belum terverifikasi" }
        val emailVo = EmailAddress(command.profile.email)
        val slugVo = TenantSlug(command.tenantSlug)

        val tenant = tenantRepository.findBySlug(slugVo)
            ?: error("Perusahaan / Subdomain '${command.tenantSlug}' tidak ditemukan")

        require(tenant.status == TenantStatus.ACTIVE) {
            "Akses perusahaan '${tenant.name.value}' sedang nonaktif/ditangguhkan (${tenant.status})"
        }

        val user = userRepository.findByEmail(emailVo)
            ?: error("Akun Google (${command.profile.email}) belum terdaftar di ${tenant.name.value}. Silakan hubungi Admin Pabrik Anda.")

        if (user.role != Role.PLATFORM_SUPERADMIN) {
            require(user.tenantId == tenant.id) {
                "Akun ini tidak memiliki akses ke tenant '${tenant.name.value}'"
            }
        }

        require(user.isActive) { "Akun pengguna (${user.username.value}) sedang dinonaktifkan" }

        user
    }
}
```

🔍 **Bedah Pemikiran Senior**:
1. **Mengapa validasi tenant dilakukan di Domain murni?**
   - Aturan bisnis *"User hanya boleh masuk jika akunnya terdaftar di tenant aktif tersebut"* adalah aturan bisnis inti, bukan logika UI atau logika database. Dengan menaruhnya di use case `core`, aturan ini terlindungi dari bug di controller manapun.
2. **Kapan `Role.PLATFORM_SUPERADMIN` boleh masuk?**
   - Platform superadmin (tim internal WeMade ERP) memiliki akses lintas tenant untuk keperluan support/bantuan pabrik (*impersonation*), sedangkan user pabrik biasa (`TENANT_ADMIN`, `OPERATOR`, dll) diikat ketat ke `user.tenantId == tenant.id`.

---

### Blok 2: Verifikasi Token Google Tanpa Dependency Gemuk (`GoogleAuthService.kt`)

Lokasi: [`server/src/main/kotlin/.../infrastructure/auth/GoogleAuthService.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/auth/GoogleAuthService.kt)

```kotlin
fun verifyIdToken(idToken: String): Result<GoogleUserProfile> = runCatching {
    val request = HttpRequest.newBuilder()
        .uri(URI.create("https://oauth2.googleapis.com/tokeninfo?id_token=" + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
        .timeout(Duration.ofSeconds(10))
        .GET()
        .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    // Validasi status HTTP 200, ekstraksi email, sub, dan aud (Client ID)
    ...
}
```

🔍 **Bedah Pemikiran Senior**:
- **Mengapa tidak memasang `google-api-client` jar raksasa?**
  - Library resmi Google API Client membawa puluhan transitive dependency (gRPC, Protobuf, Guava) yang menambah ukuran build hingga belasan megabyte.
  - Google sendiri mendokumentasikan endpoint resmi `https://oauth2.googleapis.com/tokeninfo` yang memvalidasi kriptografi token secara online. Dengan `java.net.http.HttpClient` bawaan JVM 11+, server kita tetap ringan, cepat, dan 100% aman.

---

### Blok 3: State-Holder MVI Bersih di Compose Multiplatform (`AuthViewModel.kt`)

Lokasi: [`app/shared/src/commonMain/kotlin/.../presentation/auth/AuthViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/AuthViewModel.kt)

```kotlin
class AuthViewModel(
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main),
    private val sessionStorage: TenantSessionStorage = InMemoryTenantSessionStorage()
) {
    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _uiEffect = MutableSharedFlow<LoginUiEffect>()
    val uiEffect: SharedFlow<LoginUiEffect> = _uiEffect.asSharedFlow()

    fun onEvent(event: LoginUiEvent) {
        when (event) {
            is LoginUiEvent.SelectTab -> ...
            is LoginUiEvent.SubmitGoogleLogin -> handleGoogleLogin(event.idToken)
            is LoginUiEvent.SendWhatsAppOtp -> handleSendWhatsAppOtp()
            is LoginUiEvent.VerifyWhatsAppOtp -> handleVerifyWhatsAppOtp()
        }
    }
}
```

🔍 **Bedah Pemikiran Senior**:
- **Pola MVI (Model-View-Intent)**:
  - `LoginUiState` adalah *Single Source of Truth* yang menggambarkan apa yang dilihat pengguna di layar.
  - Layar (`LoginScreen`) tidak pernah mengubah variabel state secara langsung; ia hanya memancarkan `LoginUiEvent`.
  - Hal ini membuat ViewModel **sangat mudah diuji dengan unit test** (100% testable) tanpa membutuhkan emulator Android atau browser.

---

### Blok 4: Logo Vektor Google Tanpa File Bitmap (`LoginScreen.kt`)

Lokasi: [`app/shared/src/commonMain/kotlin/.../presentation/auth/LoginScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt)

```kotlin
@Composable
private fun GoogleLogoVector(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        // Menggambar 4 kuadran busur warna resmi Google:
        // Blue (0xFF4285F4), Green (0xFF34A853), Yellow (0xFFFBBC05), Red (0xFFEA4335)
        ...
    }
}
```

🔍 **Bedah Pemikiran Senior**:
- Menghindari penggunaan file `.png` bitmap untuk logo brand. Vektor canvas tajam di resolusi layar apapun (retina display, 4K monitor, mobile), tidak memakan memori resource, dan tidak menyebabkan glitch *missing asset*.

---

## ⚖️ 4. Technology & Approach ("The Why" & Trade-offs)

| Pilihan Pendekatan | Yang Kita Pilih | Alternatif Lain | Alasan Keputusan ("The Why") |
|---|---|---|---|
| **Penyedia Autentikasi** | **Google OAuth 2.0 Langsung** | Firebase Authentication | Firebase Auth secara default adalah *single-tenant*. Di KMP Desktop & Wasm, SDK Firebase memerlukan wrapper rumit. Google OAuth langsung memberi kita kendali 100% dan terintegrasi mulus dengan PostgreSQL RLS. |
| **Metode Login Alternatif** | **Nomor WhatsApp OTP** | SMS Biasa (Carrier SMS) | SMS OTP di Indonesia mahal (Rp 400-800/SMS) dan sering lambat. Operator mesin dan penjahit di pabrik selalu memiliki WhatsApp aktif di HP mereka. |
| **Arsitektur State UI** | **MVI (Model-View-Intent)** | MVVM klasik (banyak LiveData/mutable states) | MVI menjamin state layar bersifat *predictable*, bebas dari *race condition*, dan memisahkan aksi satu kali (*Effects*) dari status tampilan (*State*). |
| **Design System Tokens** | **`ui-ux-pro-max` (B2B SaaS)** | Material 3 Default (Purple/Pink) | Standar ERP membutuhkan palet yang memberi kesan *Trust & Safety* (Blue `#2563EB` dan Workwear Orange `#EA580C`) dengan kontras teks minimum 4.5:1. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls) & Cara Mengatasinya

### 1. Jebakan: "Menerima Email Begitu Saja dari Client" (Security Hole)
* **Kesalahan Fatal**: Client login ke Google, mengambil email `budi@gmail.com`, lalu mengirim JSON `{"email": "budi@gmail.com"}` ke server.
* **Bahayanya**: Hacker bisa dengan mudah menggunakan Postman/curl untuk mengirim `{"email": "boss_pabrik@gmail.com"}` dan langsung masuk sebagai Direktur tanpa password!
* **Solusi yang Benar**: Client **wajib** mengirimkan kriptografi `idToken` dari Google. Server kemudian memvalidasi tanda tangan kriptografi token tersebut ke Google untuk memastikan email tersebut memang milik orang yang bersangkutan.

### 2. Jebakan: Lupa Memvalidasi `aud` (Audience / Client ID)
* **Kesalahan**: Token Google valid, tetapi token tersebut dibuat untuk aplikasi game lain, bukan untuk WeMade ERP.
* **Solusi**: Di `GoogleAuthService`, kita memverifikasi bahwa klaim `aud` (audience) di dalam token Google cocok dengan `GOOGLE_CLIENT_ID` milik WeMade ERP.

### 3. Operator Non-Null Assertion `!!` di Kode Kotlin
* **Kesalahan**: Menulis `state.authenticatedSession!!.user.role`. Jika ada timing di mana sesi kosong, aplikasi akan langsung *Crash* (NullPointerException).
* **Solusi**: Selalu gunakan *smart cast* variabel lokal:
  ```kotlin
  val session = state.authenticatedSession
  if (session != null) {
      // session otomatis di-smart cast ke non-null
  }
  ```

---

## 🧪 6. Verifikasi & Tantangan Mandiri (Self-Challenge)

### Verifikasi yang Telah Lolos Uji:
1. **Core Domain Test**:
   ```bash
   ./gradlew :core:jvmTest
   ```
   - Berhasil memvalidasi login Google terhadap data tenant yang ada.
   - Menggagalkan login jika email tidak terdaftar di tenant yang dituju.
2. **Server Integration Test**:
   ```bash
   ./gradlew :server:test
   ```
   - Endpoint `GET /api/public/auth/google/url` berhasil mengembalikan URL OAuth Google.
   - Endpoint `POST /api/public/auth/google` berhasil menerbitkan JWT session.
3. **Compose Multiplatform UI Test**:
   ```bash
   ./gradlew :app:shared:jvmTest
   ```
   - Alur MVI tab switching, input slug normalisasi, dan WhatsApp OTP berhasil teruji.

### Tantangan Mandiri untuk Kamu:
1. **Tambahkan Timer Countdown Nyata**: Modifikasi `AuthViewModel` pada flow WhatsApp OTP agar variabel `otpCountdown` berkurang 1 setiap detik menggunakan Coroutine `ticker` atau `delay(1000)` hingga mencapai 0.
2. **Auto-Fill Subdomain dari Hostname**: Jika aplikasi dibuka di browser web dengan domain `konveksi-jaya.wemade.id`, buatlah helper yang otomatis mengisi `tenantSlug` di `LoginUiState` dengan nilai `"konveksi-jaya"`.
