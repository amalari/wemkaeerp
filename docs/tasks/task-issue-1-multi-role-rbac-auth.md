# Resolution Summary: Issue #1 — [Core & Auth] Fondasi Arsitektur Multi-Role RBAC & User Management

- **GitHub Issue**: [#1](https://github.com/amalari/wemade-erp/issues/1)
- **Status**: ✅ Completed
- **Date**: 2026-09-08
- **Category/Layer**: Domain Core, Ktor Backend, PostgreSQL RLS, Compose Multiplatform Presentation

---

## 🎯 1. Problem Statement & Objective
- **What was required**: Membangun fondasi autentikasi, manajemen pengguna, dan kontrol akses berbasis peran (Role-Based Access Control / RBAC) untuk sistem ERP WeMade lintas divisi (Sales, PPIC, Operator, QC Inspector, Gudang, dan Tenant Admin).
- **Why it matters**: Menjamin keamanan data multi-tenant dan konsistensi hak akses di setiap modul konveksi/garmen tanpa kebocoran data antar pabrik maupun eskalasi hak istimewa (privilege escalation).
- **Acceptance Criteria Checklist**:
  - [x] Autentikasi aman (JWT + Session) di backend Ktor dengan cryptographic signature verification.
  - [x] Definisi Role dan Permission terisolasi di Pure Kotlin Domain Layer (`PLATFORM_SUPERADMIN`, `TENANT_ADMIN`, `SALES`, `PPIC_SUPERVISOR`, `OPERATOR`, `QC_INSPECTOR`, `WAREHOUSE`).
  - [x] Authorization guard & tenant resolution plugin pada rute API Ktor.
  - [x] Shared session state & token management di `core` dan `app/shared` (Compose Multiplatform MVI).
  - [x] UI Login responsif dan navigasi dinamis dengan dual login (Google Workspace & WhatsApp OTP).

---

## 🧠 2. Architectural & Design Decisions
- **Domain-Driven Design (DDD)**:
  - Seluruh logika bisnis peran (`Role`), hak akses (`Permission`), dan user identity (`User`, `UserId`, `Username`, `EmailAddress`) ditempatkan di `core/src/commonMain/kotlin` tanpa dependensi eksternal.
- **Dual-Token & Session Strategy**:
  - Google ID Token / Access Token diverifikasi di layer integrasi.
  - Sesi kerja WeMade ERP diterbitkan dalam bentuk JWT session yang menyematkan `tenantSlug`, `userId`, `role`, dan `effectivePermissions`.
- **Database Multitenancy**:
  - PostgreSQL Row-Level Security (RLS) di-enforce di setiap transaksi database melalui `DatabaseFactory.dbQuery(tenantId)`.

---

## 🛠 3. Code Walkthrough & File Changes

### Domain Layer (`core`)
- [`User.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/auth/User.kt): Entitas immutable User dengan perhitungan `effectivePermissions` dinamis.
- [`AuthValueObjects.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/auth/AuthValueObjects.kt): Definisi enum `Role`, enum `Permission`, dan value class `UserId`, `Username`, `EmailAddress`.
- [`UserSession.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/auth/UserSession.kt): Representasi sesi login aktif dan token.
- [`AuthenticateWithGoogleUseCase.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/auth/AuthenticateWithGoogleUseCase.kt): Use case autentikasi profil Google terhadap tenant database.

### Backend Infrastructure (`server`)
- [`GoogleAuthService.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/auth/GoogleAuthService.kt): Layanan verifikasi token Google dan generator URL OAuth.
- [`JwtTokenService.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/auth/JwtTokenService.kt): Layanan penerbitan dan validasi JWT HMAC-256.
- [`EnvLoader.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/infrastructure/EnvLoader.kt): Utility pembaca environment variables dari sistem atau file `.env`.
- [`Application.kt`](file:///Volumes/amalari/Projects/wemade/server/src/main/kotlin/com/eventverse/app/Application.kt): Rute publik `/api/public/auth/google/url` dan `/api/public/auth/google`.
- [`V3__seed_demo_owner.sql`](file:///Volumes/amalari/Projects/wemade/server/src/main/resources/db/migration/V3__seed_demo_owner.sql): Seed data untuk `student.achmad@gmail.com` sebagai `TENANT_ADMIN`.

### Presentation Layer (`app/shared` & `app/webApp`)
- [`LoginScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/LoginScreen.kt): Antarmuka login Compose Multiplatform responsif dengan logo canvas vektor dan tab dual-login.
- [`AuthViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/auth/AuthViewModel.kt): MVI State machine untuk `LoginUiState`, `LoginUiEvent`, `LoginUiEffect`.
- [`index.html`](file:///Volumes/amalari/Projects/wemade/app/webApp/src/webMain/resources/index.html): Integrasi Google Identity Services SDK dan JS callback bridge ke Kotlin Wasm.
- [`webpack.config.d/devServer.js`](file:///Volumes/amalari/Projects/wemade/app/webApp/webpack.config.d/devServer.js): Reverse proxy port 3000 -> 8080 untuk rute `/api`.

---

## 🧪 4. Verification & Testing
- **Unit Testing**:
  - `AuthenticateWithGoogleUseCaseTest` lulus 100%.
- **Integration Testing**:
  - `GoogleAuthIntegrationTest` lulus 100% memvalidasi flow JWT dan verifikasi token.
- **End-to-End Browser Testing**:
  - Berhasil login via Google OAuth di browser lokal dengan akun `student.achmad@gmail.com`.
  - UI State secara instan menampilkan status aktif: `achmad_owner` dengan role `TENANT_ADMIN`.

---

## 🎓 5. Key Learnings & Educational Takeaways
- **No Framework Leakage in Domain**: Core domain layer tetap 100% murni Kotlin tanpa dependensi ke Ktor, Compose, maupun library HTTP.
- **Reverse Proxy over CORS**: Menggunakan webpack dev server proxy pada port 3000 menuju port 8080 mengeliminasi kerumitan CORS headers saat tahap pengembangan.
- **Port Allocation Hygiene**: Backend dedicated pada port 8080 dan Frontend pada port 3000, didaftarkan pada Authorized Origins Google Cloud Console.

---

## ⏭ 6. Next Related Tasks
- **Issue #2 / Modul CRM**: Manajemen Leads & Katalog Produk Konveksi.
- **Issue #3 / Modul Sampling & Costing**: SPK Sampling dan kalkulasi HPP bahan baku.
