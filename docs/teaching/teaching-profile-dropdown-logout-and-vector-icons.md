# Pembelajaran: Profile Dropdown Menu, Konsolidasi Logout, dan Vektor Canvas Tanpa Tofu Skiko

Dokumentasi ini membimbing junior developer mengenai perbaikan visual rendering pada multiplatform web (Wasm/Skiko Canvas), refactoring aksi logout ke dalam profile dropdown menu terpadu, serta pembersihan tombol logout duplikat dari navigasi samping (*ClayNavDrawer*).

---

## 1. Start dari Mana? (Order of Operations)

Jika Anda dihadapkan pada keluhan:
1. *"Icon tidak muncul / berupa kotak kosong (tofu `▯`)"*
2. *"Tombol logout dipindahkan ke dalam dropdown menu profil ber-avatar"*
3. *"Tombol logout di sidebar dibersihkan"*

Berikut urutan pengerjaan standar industri:

```
[Tahap 1: Root-Cause Analysis Tofu Glyph]
      │
      ├─ Periksa apakah icon dirender via teks unicode (mis. "▼") atau Vector Path.
      └─ Identifikasi bahwa Skiko Canvas Wasm tidak memiliki glyph fallback bawaan untuk simbol unicode tertentu.
      ▼
[Tahap 2: Canvas Vector Icon Replacement]
      │
      └─ Gantikan karakter teks "▼" dengan Composable `IconChevronDown` murni Canvas di ClayIcons.kt.
      ▼
[Tahap 3: Desain Komponen `ProfileDropdown`]
      │
      ├─ Buat kapsul `ClayActionSurface` dengan Avatar Inisial, Username, Role, dan `IconChevronDown`.
      └─ Pasang `DropdownMenu` yang memuat identitas lengkap pengguna serta tombol `ClayButton` Logout (Danger style).
      ▼
[Tahap 4: Konsolidasi & Pruning di Presentation Layer (`App.kt` & `ClayNavDrawer.kt`)]
      │
      ├─ Pasang `ProfileDropdown` di top bar; hapus tombol logout merah lama di sampingnya.
      ├─ Hapus tombol logout dari footer `ClayNavDrawer`.
      └─ Jadikan `footer` di `ClayNavDrawer` nullable agar tidak meninggalkan garis pemisah gantung (*orphaned divider*).
      ▼
[Tahap 5: Verifikasi Lintas Target & Browser]
      └─ Jalankan `compileKotlinWasmJs`, buka browser, dan verifikasi visual secara langsung.
```

---

## 2. Bedah Kode Blok per Blok

### A. Mengganti Karakter Unicode Menjadi Canvas Vector (`PersonaSwitcherDropdown.kt` & `CompanySwitcherDropdown.kt`)

**Sebelum:**
```kotlin
// ❌ BAHAYA di Skiko Wasm: Font browser tidak menjamin tersedianya glyph '▼'
Text(
    text = "▼",
    fontSize = 8.sp,
    color = WeMadeColors.OnSurfaceMuted
)
```

**Sesudah:**
```kotlin
// ✅ AMAN & KONSISTEN: Digambar langsung di Skiko Canvas melalui Path geometri
IconChevronDown(
    modifier = Modifier.size(10.dp),
    color = WeMadeColors.OnSurfaceMuted
)
```

**Implementasi di `ClayIcons.kt`:**
```kotlin
@Composable
fun IconChevronDown(modifier: Modifier = Modifier, color: Color = WeMadeColors.OnSurfaceMuted) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = 2.0f * density
        val path = Path().apply {
            moveTo(w * 0.22f, h * 0.35f)
            lineTo(w * 0.50f, h * 0.65f)
            lineTo(w * 0.78f, h * 0.35f)
        }
        drawPath(
            path = path, 
            color = color, 
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}
```

> **Mental Model:**  
> Skiko (Kotlin Multiplatform UI engine) tidak menggunakan DOM HTML (`<svg>` atau font OS standar). Skiko menggambar langsung ke WebGL/WebGPU Canvas. Jika sebuah karakter teks tidak ada di font yang dimuat oleh Skiko (`Nunito` / `Fredoka`), ia akan merender karakter pengganti missing-glyph berupa kotak `▯`. Oleh karena itu, semua elemen ikonografi antarmuka wajib berupa **Canvas vector paths**.

---

### B. Membangun Komponen `ProfileDropdown` (`ProfileDropdown.kt`)

Komponen ini membungkus aksi pengguna ke dalam pola standar Cloud Console:

```kotlin
@Composable
fun ProfileDropdown(
    session: UserSession,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // Pill profil di top bar
        ClayActionSurface(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 5.dp)
        ) {
            val initial = session.user.username.value.take(2).uppercase().ifBlank { "WM" }
            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(WeMadeColors.Primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Column {
                Text(
                    text = session.user.username.value,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = WeMadeColors.OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.user.role.name,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = WeMadeColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconChevronDown(
                modifier = Modifier.size(10.dp),
                color = WeMadeColors.OnSurfaceMuted
            )
        }

        // Dropdown menu profil
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .width(260.dp)
                .background(WeMadeColors.Surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(ClaySpacing.Lg),
                verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
            ) {
                // Info Pengguna: Avatar besar, Username, dan Email
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
                ) {
                    val initial = session.user.username.value.take(2).uppercase().ifBlank { "WM" }
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(WeMadeColors.Primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initial,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = session.user.username.value,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = WeMadeColors.OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = session.user.email.value,
                            fontSize = 11.sp,
                            color = WeMadeColors.OnSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                ClayTag(text = session.user.role.name, tint = WeMadeColors.Primary)

                HorizontalDivider(thickness = ClayBorder.Medium, color = WeMadeColors.Border)

                // Tombol Logout Utama
                ClayButton(
                    text = "Keluar (Logout)",
                    onClick = {
                        expanded = false
                        onLogout()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    style = ClayButtonStyle.Danger,
                    fontSize = 12.sp
                )
            }
        }
    }
}
```

---

### C. Menghindari Garis Pemisah Gantung di `ClayNavDrawer.kt`

Saat tombol Logout di sidebar dihapus, drawer hanya membutuhkan slot footer bila ada konten yang perlu ditampilkan (misalnya saat pengguna belum login dan butuh tombol *"Login Akun"*).

```kotlin
// ✅ Parameter footer dibuat nullable
fun ClayNavDrawer(
    open: Boolean,
    onDismiss: () -> Unit,
    title: String,
    items: List<ClayNavItem>,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    sectionLabel: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null // Default null
) {
    ...
    // ✅ Divider dan Spacer hanya digambar jika footer memang ada isinya!
    if (footer != null) {
        DrawerDivider()
        Spacer(modifier = Modifier.height(ClaySpacing.Lg))
        footer()
    }
}
```

Di `App.kt`:
```kotlin
ClayNavDrawer(
    open = drawerOpen,
    onDismiss = { drawerOpen = false },
    title = "WeMade ERP",
    subtitle = "Multi-Tenant Garment Platform",
    sectionLabel = "MODUL PABRIK",
    items = navItems,
    // Jika sudah terotentikasi, footer bernilai null sehingga tidak ada border kosong di bawah menu
    footer = if (!isAuthenticated) {
        {
            ClayButton(
                text = "Login Akun",
                onClick = { openScreen(AppNavScreen.LOGIN) },
                modifier = Modifier.fillMaxWidth(),
                style = ClayButtonStyle.Primary
            )
        }
    } else null
)
```

---

## 3. Technology & Approach ("The Why")

1. **Mengapa Tidak Memakai Karakter Emoji atau Simbol Font?**  
   Di lingkungan WebAssembly (Wasm) Skiko Canvas, mesin teks hanya menyematkan glif tipografi Latin dari font kustom yang dibundel (`Fredoka` & `Nunito`). Karakter seperti `▼`, `📁`, `⚙️`, atau `🏢` sering kali tidak ada pada bundel font biner tersebut, sehingga menghasilkan kotak error kosong (*tofu*). Dengan menggambar menggunakan Composable `Canvas` murni, aplikasi terbebas 100% dari inkonsistensi font antar-sistem operasi (macOS, Windows, Linux, Android, iOS).

2. **Mengapa Mengelompokkan Logout ke Dropdown Profil?**  
   - Menempatkan tombol logout terpisah berwarna merah di top bar memakan ruang horizontal yang berharga bagi nama tenant dan switcher persona.
   - Tombol logout di sidebar navigation drawer sering membingungkan secara hierarki navigasi (navigasi modul fungsional pabrik tercampur dengan aksi sesi akun).
   - Pola profil dropdown adalah konvensi universal SaaS kelas enterprise (seperti GCP Console, AWS Management Console, dan GitHub).

---

## 4. Jebakan Pemula (Common Pitfalls)

| Jebakan | Dampak | Pencegahan |
|---|---|---|
| **Menggunakan karakter unicode simbol (▼, ▶, dll.)** | Karakter tampil sebagai kotak tofu `▯` di Skiko Wasm. | Selalu gunakan vector Composable dari `ClayIcons.kt`. |
| **Membiarkan footer drawer non-null dengan body kosong `{}`** | Menghasilkan garis pemisah gantung (*orphaned horizontal divider*) di bagian bawah drawer. | Buat parameter `footer: (@Composable ColumnScope.() -> Unit)? = null` dan gunakan pengecekan `if (footer != null)`. |
| **Menyebarkan aksi logout di banyak tempat yang tidak sinkron** | Mengakibatkan inkonsistensi UX dan pembersihan state yang parsial. | Jadikan `ProfileDropdown` sebagai Single Source of Truth untuk manajemen sesi pengguna. |

---

## 5. Verifikasi & Pengujian Mandiri

1. **Kompilasi Wasm & JVM:**
   ```bash
   ./gradlew :app:shared:compileKotlinWasmJs :app:webApp:wasmJsBrowserDevelopmentExecutableDistribution
   ./gradlew :app:shared:compileKotlinJvm
   ```
2. **Inspeksi Visual di Browser (`http://127.0.0.1:3000/org-chart`):**
   - Periksa kapsul Persona Switcher dan Company Switcher: panah bawah wajib berbentuk segitiga/chevron vektor tajam tanpa kotak tofu.
   - Klik kapsul profil avatar di pojok kanan atas: dropdown menu terbuka, menampilkan inisial avatar, username, email, tag role, dan tombol merah `Keluar (Logout)`.
   - Buka hamburger menu di pojok kiri atas: pastikan daftar 10 modul pabrik tampil bersih dan bagian bawah drawer tidak memuat tombol logout maupun garis divider kosong.
