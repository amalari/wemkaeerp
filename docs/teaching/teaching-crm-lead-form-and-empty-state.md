# 🎓 Modul Pembelajaran: Validasi Nomor HP Indonesia, Brand Fleksibel, & Claymorphic Empty State CRM

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Full-Stack DDD (Domain-Driven Design), Indonesian Phone Number Validation, Flyway Migration, Compose Multiplatform Claymorphism Neo-Brutalism  
> **Prasyarat**: Pemahaman dasar Kotlin Multiplatform (KMP), Compose UI, dan arsitektur Bounded Context WeMade ERP  
> **Referensi Task**: CRM Sales Lead Capture Refinement & Kanban Empty State  

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Dalam operasional pabrik konveksi dan garment B2B:
1. **Calon pembeli sering menghubungi via chat WhatsApp tanpa langsung menyebut nama PT atau brand mereka.** Jika form CRM mewajibkan kolom *"Nama Brand/Perusahaan"*, staf sales di lapangan akan terhambat atau terpaksa mengetik teks sampah seperti `"-"` atau `"TBD"`.
2. **Format nomor telepon di Indonesia sering kacau.** Ada yang mengetik `08123456789`, `+628123456789`, atau bahkan nomor telepon rumah Jakarta `021...` yang tidak bisa di-chat WhatsApp. Panjang nomor ponsel Indonesia umumnya adalah 10 hingga 13 digit (atau 11–14 digit berawalan `628`). Jika tidak divalidasi di awal, pesan otomatis dan deep link `wa.me/` akan gagal terkirim.
3. **Peluang tender besar yang sudah matang sering datang langsung.** Jika staf sales harus selalu memasukkan lead ke kolom *New Lead* (Inquiry Masuk) baru kemudian memindahkannya ke *Qualified Lead*, ini menimbulkan friksi alur kerja (double effort). Sales butuh kemampuan untuk langsung menambahkan prospek ke kolom *Qualified Lead*.
4. **Tampilan papan kosong (Empty State) yang kaku dan membosankan membuat aplikasi terlihat mati.** Kotak abu-abu polos tanpa visual tactile merusak user experience khas Claymorphism Neo-Brutalism yang diusung WeMade ERP.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan fitur ini dari nol, ikuti urutan berikut:

```
[1. Database DDL] ──> [2. Table & ORM] ──> [3. Pure Domain Value Objects] ──> [4. Use Cases & DTOs] ──> [5. Server API] ──> [6. Presentation & UI]
```

1. **Langkah 1: Skema Database & Migrasi Flyway (`V25__...sql`)**
   - Tambahkan kolom `email` (nullable/varchar) dan buat `brand_name` memiliki default string kosong agar backward-compatible.
2. **Langkah 2: Definisi Tabel Exposed (`CrmTables.kt`) & Repository (`PostgresCrmLeadRepository.kt`)**
   - Hubungkan kolom fisik database dengan pembacaan record di server.
3. **Langkah 3: Pure Domain Layer (`core/.../domain/crm`)**
   - Perbarui `BrandName` agar menerima nilai kosong dan sediakan helper `.display()`.
   - Tambahkan aturan validasi nomor ponsel Indonesia ke `WhatsappNumber.isValidIndonesianPhone()` dan format E.164.
   - Sediakan properti cerdas `CrmLead.title` yang secara berurutan memprioritaskan: Nama Brand -> Nama Kontak -> Nomor Handphone -> ID Fallback.
4. **Langkah 4: Application Layer & Codec (`core/.../shared/crm`)**
   - Tambahkan `email` dan `stage` ke `CreateLeadRequest`, `PatchLeadRequest`, serta fungsi `encode`/`decode`.
5. **Langkah 5: Server Routing Ktor (`server/.../CrmRoutes.kt`)**
   - Ekstrak parameter `email` dan `stage` saat menerima `POST /api/tenant/crm/leads` dan `PATCH`.
6. **Langkah 6: State & ViewModel (`app/shared/.../presentation/crm`)**
   - Perbarui `CrmUiState` untuk menyimpan `createDialogInitialStage`.
   - Update event `OpenCreateDialog(stage)` dan `CreateLead(...)`.
7. **Langkah 7: Presentation UI & Claymorphism Components**
   - Modifikasi `CreateLeadDialog`: input opsional brand, input nomor handphone dengan peringatan validasi instan, input email opsional, dan tombol submit pintar.
   - Modifikasi `CrmKanbanColumn` & `CrmKanbanBoard`: tombol `+ Tambah` di kolom *New Lead* dan *Qualified Lead*, serta pembuatan kartu `CrmEmptyColumnCard` bergaya Claymorphic Neo-Brutalist.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Validasi Nomor Handphone Indonesia & Fallback Title (`core`)

```kotlin
// core/.../domain/crm/CrmLeadValueObjects.kt
fun parse(raw: String): WhatsappNumber? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null
    val digits = trimmed.filter { it.isDigit() || it == '+' }
    val normalised = when {
        digits.startsWith("+62") -> digits.removePrefix("+")
        digits.startsWith("62") -> digits
        digits.startsWith("0") -> "62" + digits.removePrefix("0")
        else -> return null
    }
    // Nomor HP Indonesia wajib diawali 628 dan panjang 11..14 digit
    if (!normalised.startsWith("628")) return null
    if (normalised.length !in 11..14) return null
    return runCatching { WhatsappNumber(normalised) }.getOrNull()
}
```

**Mengapa ditulis begini?**
- **Mental Model**: Di Indonesia, nomor ponsel berformat lokal `0812...` (10–13 digit). Saat dinormalisasi ke format internasional E.164 tanpa tanda plus, `08` menjadi `628` (11–14 digit). Angka di luar rentang ini dipastikan salah ketik atau nomor PSTN kantor/rumah (`021`, `022`, dll.) yang tidak memiliki akun WhatsApp.
- **Null Safety**: Mengembalikan `null` alih-alih melempar exception liar, sehingga UI dapat memberikan feedback visual secara reaktif tanpa menyebabkan crash.

---

### Blok B: Dialog Pembuatan Lead yang Cerdas (`CreateLeadDialog.kt`)

```kotlin
val isPhoneFilled = phoneNumber.trim().isNotBlank()
val isPhoneValid = !isPhoneFilled || WhatsappNumber.isValidIndonesianPhone(phoneNumber.trim())

val isEmailFilled = email.trim().isNotBlank()
val isEmailValid = !isEmailFilled || (email.contains("@") && email.contains(".") && email.trim().length >= 5)

// Setidaknya ada 1 identitas pengenal (Brand, Kontak, atau HP)
val hasAnyIdentifier = brandName.trim().isNotBlank() || contactPerson.trim().isNotBlank() || isPhoneFilled
val canSubmit = hasAnyIdentifier && isPhoneValid && isEmailValid
```

**Mengapa ditulis begini?**
- **Guarding Data Kualitas**: Brand nama opsional, nama kontak opsional, email opsional. Tapi kita **tidak boleh** membiarkan user menyimpan baris kosong melompong (*ghost lead*). Aturan `hasAnyIdentifier` menjamin setidaknya ada satu jejak kontak yang tersimpan.
- **Validasi Kondisional**: Validasi format hanya menyala jika kolom tersebut diisi. Jika user tidak memasukkan nomor HP, form tetap valid; namun jika diisi, nomor tersebut wajib sesuai format Indonesia.

---

### Blok C: Claymorphic Empty State Card (`CrmKanbanColumn.kt`)

```kotlin
@Composable
private fun CrmEmptyColumnCard(
    stage: LeadStage,
    canWrite: Boolean,
    onAddLead: (() -> Unit)?
) {
    val bgTint = when (stage) {
        LeadStage.NEW_LEAD -> WeMadeColors.PrimaryContainer
        LeadStage.QUALIFIED -> WeMadeColors.AccentLight
        LeadStage.UNQUALIFIED -> WeMadeColors.SurfaceMuted
    }
    val outlineTint = when (stage) {
        LeadStage.NEW_LEAD -> WeMadeColors.Primary
        LeadStage.QUALIFIED -> WeMadeColors.Accent
        LeadStage.UNQUALIFIED -> WeMadeColors.OutlineSoft
    }
    // Komponen claySurface dengan outline tebal 2dp & hard shadow tanpa blur
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .claySurface(
                shape = ClayShapes.Card,
                background = WeMadeColors.Surface,
                outline = outlineTint,
                offset = ClayOffset.Small,
                borderWidth = ClayBorder.Medium
            )
            .padding(ClaySpacing.Xl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ClaySpacing.Sm)
    ) { ... }
}
```

**Mengapa ditulis begini?**
- **Claymorphism + Neo-Brutalism Contract (§12)**: Menolak penggunaan bayangan kabur (`Modifier.shadow()`). Menggunakan `claySurface` dengan offset padat (hard shadow) dan radius besar (`ClayShapes.Card`).
- **Contextual Color Identity**: Kolom *New Lead* bernuansa biru (`PrimaryContainer` & `Primary`), kolom *Qualified Lead* bernuansa oranye garment (`AccentLight` & `Accent`), dan *Unqualified* bernuansa abu-abu slate netral.
- **Interactive Call-to-Action**: Kolom kosong tidak dibiarkan pasif. Terdapat tombol langsung `+ Tambah Inquiry` atau `+ Tambah Qualified` yang memangkas langkah user.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Keputusan | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Value Object `BrandName` & `WhatsappNumber`** | String Primitives (`var phone: String`) | Type safety murni di layer domain; normalisasi dan aturan regex terpusat di satu tempat. | Primitive Obsession: Format nomor dicek berulang-ulang di controller, UI, dan database dengan pola regex yang tidak konsisten. |
| **Pill Selector Stage di Header Dialog** | Dropdown Menu Material standar | Segmented pill instan terlihat jelas, bergaya tactile clay, dan mudah di-tap di perangkat mobile/tablet. | Dropdown Material membutuhkan klik tambahan dan default styling M3 merusak konsistensi neo-brutalism. |
| **Tombol Tambah Eksklusif di New & Qualified** | Tombol tambah di semua kolom termasuk Unqualified | Secara logika bisnis konveksi, lead "Batal" tidak pernah diciptakan secara sengaja; lead batal adalah hasil diskualifikasi dari Inquiry atau Qualified. | Menghasilkan data anomali di mana staf salah membuat entri langsung ke status batal. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Memaksa `contactPerson` atau `brandName` wajib diisi di Database.**
   - *Kenapa bahaya*: Ketika customer service menerima chat WhatsApp kilat ("Kak, mau tanya harga jaket bomber 100 pcs"), mereka belum tahu nama brand-nya. Jika form menolak, CS tidak akan mencatat data tersebut di CRM, dan prospek hilang begitu saja.
   - *Solusi*: Jadikan opsional di level DB dan Value Object, lalu gunakan `CrmLead.title` pintar untuk merender representasi terbaiknya di UI.

2. **Jebakan 2: Memakai token desain ad-hoc seperti `ClayOffset.None` atau `Color(0xFF...)`.**
   - *Kenapa bahaya*: Melanggar kontrak Design System WeMade (§12).
   - *Solusi*: Selalu gunakan token resmi yang ada di `ClayTokens.kt` (`ClayOffset.Flat`, `ClayBorder.Hairline`, `ClayBorder.Medium`) dan palet warna semantik `WeMadeColors`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian dilakukan melalui automated testing di level JVM dan WebAssembly:

```bash
# 1. Jalankan unit test Domain & Presentation
./gradlew :core:jvmTest :app:shared:jvmTest

# 2. Pastikan kompilasi WasmJS Web bundle berhasil tanpa breaking changes
./gradlew :app:webApp:wasmJsBrowserDevelopmentWebpack
```

**Unit Test Kunci di `CrmLeadTest.kt`:**
- Memvalidasi parsing nomor lokal `081234567890` menjadi `6281234567890`.
- Memvalidasi penolakan nomor tidak valid (misal: panjang kurang dari 10 digit).
- Memvalidasi fallback `CrmLead.title` saat nama brand kosong.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Tambahkan formatting otomatis pada input nomor telepon di `CreateLeadDialog` (misal secara otomatis mengelompokkan digit menjadi `0812-3456-7890` saat user mengetik).
- [ ] **Tantangan 2**: Buat unit test di `:server:test` untuk memverifikasi bahwa `POST /api/tenant/crm/leads` dapat menerima payload dengan `stage = "QUALIFIED"` dan `email` tanpa nama brand.
