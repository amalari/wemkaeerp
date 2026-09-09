# 🎓 Modul Pembelajaran: Implementasi Auth Guard, Session Management, dan Logout Button pada Compose Multiplatform (Wasm & Desktop)

> **Level Target**: Junior to Mid Multiplatform Developer  
> **Topik Utama**: Authentication State Machine, Navigation Guard, Client-Side Session Storage, MVI Pattern, Skiko Canvas Rendering  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform, Compose State & LaunchedEffect, serta konsep Single Source of Truth (SSOT).  
> **Referensi Task**: Auth Guard & Logout Flow (WeMade ERP)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan sebuah pabrik konveksi di mana data rahasia seperti struktur gaji, hirarki pimpinan (*Bagan Organisasi*), dan hak approval (*RBAC Matrix*) dapat diakses oleh siapa saja yang membuka URL peramban tanpa verifikasi login terlebih dahulu.

Jika sistem navigasi kita hanya mengandalkan perpindahan layar biasa tanpa **Auth Guard**:
1. Pengguna asing bisa langsung melihat atau berinteraksi dengan tombol operasional sensitif.
2. Ketika token kadaluarsa atau pengguna menekan tombol *Logout*, sisa data state di antarmuka masih tertinggal dan bisa di-inspeksi oleh orang berikutnya di workstation bersama (pabrik biasanya memiliki komputer bersama di lini produksi).
3. Terjadi ketidaksinkronan antara status login di *Application State* dengan visual navigasi di *Header Bar*.

### Analogi Sederhana: Gerbang Pintu Pabrik & Kartu ID RFID
- **Auth Guard**: Pintu putar otomatis di pos satpam pabrik. Sebelum menempelkan kartu ID (autentikasi), Anda tidak bisa masuk ke ruang produksi atau ruang direksi. Jika Anda mencoba menyusup ke pintu tersebut tanpa kartu, gerbang akan membunyikan alarm dan mengarahkan Anda kembali ke meja resepsionis (*Login Screen*).
- **Session Capsule & Logout**: Kartu ID yang Anda gantung di dada selama berada di dalam area pabrik. Saat Anda menekan *Logout*, kartu dikembalikan ke pos satpam, nama Anda diturunkan dari papan kehadiran, dan semua pintu akses otomatis terkunci kembali (*Locked Chips*).

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika Anda harus membangun sistem Auth Guard dan Logout dari nol pada aplikasi Compose Multiplatform, berikut urutan logis pengerjaannya:

```
Step 0: State & Event Contracts (Domain / Presentation Contract)
   ↓
Step 1: Session Storage & Persistence (InMemory / Platform Storage)
   ↓
Step 2: Auth ViewModel (State Machine & Single Source of Truth)
   ↓
Step 3: Auth Guard Barrier Composable (Visual Barrier)
   ↓
Step 4: Root Orchestrator (App.kt Navigation & Guard Rules)
   ↓
Step 5: Visual Indicators & Logout Action (User Capsule & Exit Trigger)
```

1. **Langkah 0: Definisikan State & Event di ViewModel**  
   Jangan langsung mendesain tombol atau layar. Tentukan dahulu: apa definisi "terotentikasi" (`UserSession != null`), apa event-nya (`LoginUiEvent.Logout`), dan efek satu-arah apa yang dipancarkan (`LoginUiEffect.NavigateToDashboard`).
2. **Langkah 1: Hubungkan Session Storage**  
   Pastikan mekanisme penyimpanan token dan penghapusan sesi (`clearSession()`) sudah tersedia agar saat logout, state di memori dan storage sinkron seketika.
3. **Langkah 2: Buat Visual Barrier (`AuthGuardCard`)**  
   Komponen presentasi yang informatif yang menjelaskan *mengapa* layar diblokir dan menyediakan jalan keluar (tombol aksi kembali ke login).
4. **Langkah 3: Terapkan Guard di Tingkat Root (`App.kt`)**  
   Gunakan `Crossfade` atau `NavHost` di mana setiap layar privat dibungkus kondisi guard:
   ```kotlin
   if (isAuthenticated) Screen() else AuthGuardCard()
   ```
5. **Langkah 4: Tambahkan Indikator Visual & Tombol Logout di Header**  
   Tampilkan identitas pengguna aktif (avatar, tenant, role) dan pasang tombol Logout dengan warna peringatan (merah/error theme).

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

Mari kita bedah implementasi yang telah dibuat di [`app/shared/src/commonMain/kotlin/com/eventverse/app/App.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/App.kt).

### Blok 1: Single Source of Truth & Session Observation

```kotlin
val authViewModel = remember { AuthViewModel() }
val authState by authViewModel.uiState.collectAsState()
val session = authState.authenticatedSession
val isAuthenticated = session != null

var currentScreen by remember {
    mutableStateOf(if (isAuthenticated) AppNavScreen.ORG_CHART else AppNavScreen.LOGIN)
}
```

**Mengapa ditulis begini?**
- `session` diambil langsung dari `authState.authenticatedSession`. Jika nilai ini berubah (misal dari null menjadi objek sesi, atau kembali ke null), Compose akan otomatis memicu rekalkulasi (*recomposition*) di seluruh subtree.
- Inisialisasi layar awal (`currentScreen`) mengecek `isAuthenticated`. Pengguna yang belum login otomatis diarahkan ke `AppNavScreen.LOGIN`, tidak langsung ke dashboard.

---

### Blok 2: Event Listener & Auto-Redirect Terkendali

```kotlin
// 1. Auto-navigate ke dashboard saat login berhasil
LaunchedEffect(authViewModel) {
    authViewModel.uiEffect.collect { effect ->
        if (effect is LoginUiEffect.NavigateToDashboard) {
            currentScreen = AppNavScreen.ORG_CHART
        }
    }
}

// 2. Redirect ke LOGIN saat pengguna logout
var wasAuthenticated by remember { mutableStateOf(isAuthenticated) }
LaunchedEffect(isAuthenticated) {
    if (wasAuthenticated && !isAuthenticated) {
        currentScreen = AppNavScreen.LOGIN
    }
    wasAuthenticated = isAuthenticated
}
```

**Mengapa teknik `wasAuthenticated` ini sangat penting?**
- **Mental Model**: Kita hanya ingin me-redirect pengguna ke `LOGIN` saat terjadi **transisi status dari login menjadi logout** (`wasAuthenticated && !isAuthenticated`).
- **Jebakan yang dihindari**: Jika kita hanya menulis `if (!isAuthenticated) currentScreen = AppNavScreen.LOGIN`, maka unauthenticated user tidak akan pernah bisa melihat layar `AuthGuardCard` saat mereka penasaran mengklik chip *Bagan Organisasi* atau *Hak Akses (RBAC)*, karena efek akan terus memaksa nilai kembali ke `LOGIN`. Dengan menyimpan state sebelumnya, transisi menjadi presisi.

---

### Blok 3: Header Navigation Chips dengan State Kunci (Lock Icons)

```kotlin
FilterChip(
    selected = currentScreen == AppNavScreen.ORG_CHART,
    onClick = { currentScreen = AppNavScreen.ORG_CHART },
    label = {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (!isAuthenticated) {
                LockIcon(modifier = Modifier.size(12.dp))
            }
            Text(
                text = "Bagan Organisasi",
                fontSize = 12.sp,
                fontWeight = if (currentScreen == AppNavScreen.ORG_CHART) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
)
```

**Mengapa ditulis begini?**
- Ketika pengguna belum login (`!isAuthenticated`), chip navigasi menampilkan gembok `LockIcon`. Ini memberikan petunjuk visual (affordance) bahwa modul tersebut adalah area privat.
- Saat diklik, kita membiarkan `currentScreen` berpindah ke `ORG_CHART`. Layar inilah yang nantinya dicegat oleh Guard.

---

### Blok 4: The Core Auth Guard Barrier

```kotlin
Crossfade(targetState = currentScreen, modifier = Modifier.weight(1f)) { screen ->
    when (screen) {
        AppNavScreen.ORG_CHART -> {
            if (isAuthenticated) {
                OrgChartScreen()
            } else {
                AuthGuardCard(
                    targetModuleName = "Bagan Struktur Organisasi & Karyawan",
                    onLoginClick = { currentScreen = AppNavScreen.LOGIN }
                )
            }
        }
        AppNavScreen.DYNAMIC_RBAC -> {
            if (isAuthenticated) {
                DynamicRbacScreen(
                    onBackToLogin = { currentScreen = AppNavScreen.LOGIN }
                )
            } else {
                AuthGuardCard(
                    targetModuleName = "Manajemen Hak Akses & Matriks RBAC",
                    onLoginClick = { currentScreen = AppNavScreen.LOGIN }
                )
            }
        }
        AppNavScreen.LOGIN -> {
            LoginScreen(
                viewModel = authViewModel,
                onNavigateToDashboard = { currentScreen = AppNavScreen.ORG_CHART }
            )
        }
    }
}
```

**Mental Model**:
- Guard diletakkan tepat di depan modul tujuan.
- Tidak ada data sensitif yang diinisialisasi atau di-render jika `isAuthenticated` bernilai `false`. Komponen `OrgChartScreen` sama sekali tidak dipanggil ke dalam komposisi Compose.

---

### Blok 5: User Profile Capsule & Tombol Logout

```kotlin
// User Profile Capsule
Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    modifier = Modifier
        .clip(RoundedCornerShape(8.dp))
        .background(Color(0xFFF1F5F9))
        .padding(horizontal = 10.dp, vertical = 5.dp)
) {
    val initial = session.user.username.value.take(2).uppercase()
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(WeMadeColors.Primary),
        contentAlignment = Alignment.Center
    ) {
        Text(text = initial, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }

    Column {
        Text(text = session.user.username.value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WeMadeColors.OnSurface)
        Text(text = session.user.role.name, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = WeMadeColors.Primary)
    }
}

// Distinct Logout Button
OutlinedButton(
    onClick = {
        authViewModel.onEvent(LoginUiEvent.Logout)
        currentScreen = AppNavScreen.LOGIN
    },
    modifier = Modifier.height(34.dp),
    shape = RoundedCornerShape(8.dp),
    colors = ButtonDefaults.outlinedButtonColors(
        contentColor = WeMadeColors.Error,
        containerColor = WeMadeColors.ErrorBg
    ),
    border = BorderStroke(1.dp, WeMadeColors.Error.copy(alpha = 0.35f)),
    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
) {
    LogoutIcon(modifier = Modifier.size(13.dp), color = WeMadeColors.Error)
    Spacer(modifier = Modifier.width(6.dp))
    Text(text = "Logout", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
}
```

---

## 🔬 4. Technology & Approach ("The Why")

### 1. Mengapa Menggunakan Custom Vector Canvas untuk Icon di Compose Wasm?
Di Compose Multiplatform Web (Wasm), menggunakan dependency berat seperti `androidx.compose.material:material-icons-extended` akan menambah ukuran bundle Wasm hingga belasan megabyte hanya untuk mengambil 2 icon.
- **Solusi**: Kita menggambar `LockIcon` dan `LogoutIcon` menggunakan `Canvas` path 2D murni.
- **Keuntungan**:
  - Nol bytes dependensi tambahan.
  - Sangat tajam di semua DPI layar (Retina, 4K, mobile).
  - Mengikuti warna tema secara dinamis (`StrokeCap.Round`, `StrokeJoin.Round`).

### 2. Mengapa Menggunakan Unidirectional Data Flow (MVI) untuk Auth State?
Ketika logout ditekan, ViewModel memproses:
1. `sessionStorage.clearSession()` (hapus token/penyimpanan lokal).
2. `_uiState.update { it.copy(authenticatedSession = null, ...) }` (reset state).
Seluruh UI yang mengamati state tersebut (header bar, guard card, screen view) secara atomik bereaksi bersamaan tanpa ada kemungkinan *stale state*.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

| Jebakan | Risiko | Solusi di Kode Kita |
|---|---|---|
| **Operator `!!` pada Session** | Crash `NullPointerException` saat logout terjadi di tengah recomposition | Hindari `!!`. Gunakan Kotlin Smart-Casting di dalam cabang `if (session != null)` |
| **Simbol Karakter Khusus di Canvas** | Simbol seperti panah unicode `➔` atau emoji `⚡` bisa gagal di-render font bawaan canvas Skiko dan muncul sebagai kotak kosong `[]` | Gunakan panah standar `→` atau teks murni yang aman bagi semua web fonts |
| **Infinite Navigation Re-trigger** | Menaruh perubahan navigasi di dalam body Composable tanpa `LaunchedEffect` | Selalu konsumsi event one-time (seperti efek navigasi setelah login) di dalam `LaunchedEffect` |
| **Menyembunyikan UI Tanpa Menghentikan Render** | Menyembunyikan tampilan dengan `modifier = Modifier.alpha(0f)` | Menggunakan struktur kontrol `if (isAuthenticated) Screen() else Guard()`, sehingga modul rahasia tidak pernah masuk ke Composition Tree |

---

## 🎯 6. Verifikasi & Tantangan Mandiri

### Checklist Verifikasi yang Berhasil Diuji:
1. [x] **Initial State**: Saat aplikasi dibuka pertama kali di `http://localhost:3000`, pengguna mendarat di layar Login, dan tab *Bagan Organisasi* serta *Hak Akses (RBAC)* memiliki icon gembok `🔒`.
2. [x] **Auth Guard Barrier**: Saat chip *Bagan Organisasi* diklik oleh pengguna anonim, layar memunculkan kartu pembatas `AuthGuardCard` bertuliskan *"Akses Terbatas: Autentikasi Diperlukan"*.
3. [x] **One-Click Return**: Tombol *"Masuk ke Akun Sekarang →"* mengembalikan pengguna ke form login secara instan.
4. [x] **Instant Login**: Tombol demo *"Masuk Cepat Demo (Owner Pabrik)"* mengotentikasi sesi sebagai `achmad_owner` dan langsung mengarahkan ke Bagan Organisasi.
5. [x] **Header Context**: Header menampilkan badge tenant `🏢 wemade-demo`, avatar `AC`, nama, role `TENANT_ADMIN`, serta tombol merah *Logout*.
6. [x] **Clean Logout**: Menekan tombol *Logout* menghapus sesi, mengunci kembali semua tab privat, dan menampilkan pesan sukses *"Berhasil keluar dari sistem."*

### 🚀 Tantangan Mandiri untuk Anda (Junior Dev Challenge):
> **Tantangan**: Tambahkan timer kadaluarsa sesi (session timeout) otomatis jika pengguna tidak melakukan aktivitas (idle) selama 15 menit, yang otomatis memicu `LoginUiEvent.Logout`!
