# 🎓 Modul Pembelajaran: Redesign UI Deal & Kontak CRM Sales (Claymorphism)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Design System (Claymorphism + Neo-Brutalism), `LazyVerticalGrid` adaptif, KPI agregasi state, Deep-link WhatsApp (`wa.me`), Smart-cast lintas modul KMP
> **Prasyarat**: Paham dasar Compose (State hoisting, Recomposition), struktur DDD WeMade (`core/` → `app/shared/` → `server/`), dan aturan design system di `.agents/rules/design-system-rules.md`
> **Referensi**: `implementation_plan.md` — "Tampilan Modern Deal & Kontak"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah Nyata**: Pane Deal & Kontak lama hanyalah `LazyColumn` 1 kolom berisi baris tipis. Di monitor desktop 1800px, 80% layar kosong; informasi penting (nilai pipeline, win rate, siapa PIC) tidak terlihat tanpa membuka kartu satu per satu.
- **Analogi Sederhana**: Daftar lama itu seperti buku rekening yang hanya mencatat satu baris per halaman. Versi baru adalah **dashboard bank**: ringkasan di atas (KPI), lalu kartu-kartu padat yang menyaring informasi paling penting ke permukaan.
- **Hasil Akhir**: Tab `Deal` membuka dengan 4 kartu KPI, search bar, chip filter tahapan, dan grid kartu deal 3 kolom. Tab `Kontak` membuka direktori grid dengan avatar inisial, ikon vektor, ringkasan transaksi, dan tombol `Chat WhatsApp` yang langsung membuka `wa.me`.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Audit kontrak data, jangan langsung gambar UI.** Baca `Deal.kt`, `Contact.kt`, dan `DealApiClient.kt`. Temuan penting: `Deal` TIDAK punya nama employee (hanya `ownerEmployeeId`), TAPI punya `contactId` — artinya tag brand di kartu deal bisa didapat dengan **join di memori** `contacts.associateBy { it.id.value }`.
2. **Langkah 1: Hitung KPI dari state, bukan dari server.** `totalPipelineOf()`, `winRateOf()` adalah pure function di atas `List<Deal>`. Tidak ada endpoint agregasi baru — YAGNI.
3. **Langkah 2: State hoisting.** `searchQuery`, `stageFilter`, `openDealId` hidup di `DealsPane`; komponen kartu murni presentasional (`Deal`, `Contact`, lambda).
4. **Langkah 3: Susun layout atas→bawah**: header → KPI row → search → chip filter → grid.
5. **Langkah 4: Ganti `LazyColumn` → `LazyVerticalGrid(GridCells.Adaptive(340.dp))`.**
6. **Langkah 5: Kartu kontak + aksi cepat.** `openInBrowser(phone.waLink)` — `waLink` SUDAH disediakan value object `WhatsappNumber` (E.164 tanpa `+`). Jangan normalisasi manual!
7. **Langkah 6: Kompilasi 4 target** — WasmJs, Js, Jvm, Android.

---

## 🔬 3. Bedah Kode Blok per Blok

### A. Join Deal↔Contact di memori (`DealsPane.kt`)

```kotlin
val contactsById = remember(contacts) { contacts.associateBy { it.id.value } }
```
- **Mental model**: dua daftar datar dari server; peta O(1) menghubungkannya saat render. `remember(contacts)` = cache invalidasi otomatis saat data baru masuk.

### B. Grid adaptif, bukan kolom tetap

```kotlin
LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 340.dp), ...)
```
- **Mengapa `Adaptive`?** Kolom dihitung dari lebar nyata: 3 kolom di desktop, 1 kolom di ponsel — **satu baris kode, nol breakpoint manual**. Ini menjawab utang arsitektur "tidak ada adaptivitas window size".
- **Penting**: `Modifier.weight()` TIDAK ADA di dalam `LazyVerticalGrid` (bukan `RowScope`/`ColumnScope`). Gunakan `fillMaxWidth()`; grid otomatis mengisi sisa tinggi.

### C. Chip filter — Kontrak 8 (warna, bukan ketebalan)

```kotlin
ClayActionSurface(
    selected = selected,
    containerColor = if (selected) WeMadeColors.Primary.copy(alpha = 0.14f) else WeMadeColors.Surface,
    outlineColor = if (selected) WeMadeColors.Primary else WeMadeColors.Outline, ...)
```
Outline selalu `ClayBorder.Medium` — state "aktif" berbicara lewat warna. `.copy(alpha = …)` adalah **turunan token yang sah**; bukan literal warna baru.

### D. Smart-cast gagal lintas modul (`ContactsPane.kt`)

```kotlin
val phone = contact.phone      // core/ = modul terpisah → tidak bisa smart cast
if (phone != null) { ... phone.localDisplay ... }
```
- `Contact.phone` dideklarasikan di modul `core` (bisa di-override di platform lain), jadi compiler Kotlin menolak smart cast `contact.phone != null → contact.phone.localDisplay`. **Salin ke val lokal** dulu. Error aslinya: *"Smart cast to 'WhatsappNumber' is impossible"*.

### E. Avatar inisial — tidak ada gambar, tidak ada emoji

```kotlin
Box(Modifier.size(44.dp).clayFlat(shape = CircleShape, background = tint.copy(alpha = 0.15f), outline = tint, borderWidth = ClayBorder.Medium))
```
Warna avatar dipilih dari daftar **token brand** via `abs(name.hashCode()) % size` — stabil per nama, tanpa API apa pun. Inisial dua huruf dari `split(Regex("\\s+"))`.

---

## 🛠️ 4. Technology & Approach ("The Why")

| Keputusan | Alternatif ditolak | Alasan |
|---|---|---|
| KPI dihitung di client dari `List<Deal>` | Endpoint agregasi baru | Data sudah utuh di memori; menambah endpoint = menambah permukaan API yang harus diuji RBAC untuk keuntungan nol. |
| `GridCells.Adaptive` | `Fixed(3)` + manual breakpoint | Adaptive bekerja di SEMUA target (ponsel → ultra-wide) tanpa logika `if (width > …)`. |
| `openInBrowser(waLink)` expect/actual | Menulis `window.open` di composable | Contract multiplatform sudah ada (`PoFilePicker.kt`); JS/Wasm pakai `window.open`, JVM pakai `Desktop.browse`, Android/iOS no-op aman. **Reuse, bukan duplikasi.** |
| `ClayActionSurface` untuk chip | `FilterChip` Material3 | M3 membawa warna ungu default & radius yang salah; melanggar Kontrak 5 design system. |
| Muat `getDeals()` di `ContactsPane` | Endpoint ringkasan per kontak | Sama seperti KPI: data deal kecil, join lokal cukup. |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **`Modifier.weight(1f)` di dalam `LazyVerticalGrid`** → error kompilasi konyol *"Expression 'weight' cannot be invoked as a function"*. Grid bukan scope baris/kolom.
2. **Smart cast properti modul lain** — lihat blok D. Pola aman: `val x = obj.prop; if (x != null)`.
3. **Menanam warna ke `TextStyle`** atau menulis `Color(0xFF…)` baru — semua warna wajib lewat `WeMadeColors` / `tint()` (Kontrak 1 & 9). `grep -rn "Color(0xFF"` pada kedua file = **nol hasil**.
4. **Emoji sebagai ikon** (`💬 📞`) → tofu `▯` di Skiko/Wasm. Wajib `IconPhone`, `IconMail`, `IconChat`, `IconSearch` dari `ClayIcons.kt`.
5. **Ikon warna salah di tombol pekat**: default `IconChat` = `OnSurface` (gelap), tak terbaca di tombol `Success` hijau → kirim `color = Color.White` secara eksplisit.
6. **Filter mengubah KPI** — KPI harus dihitung dari `deals` penuh, filter hanya menyaring grid. Menghitung win-rate dari hasil pencarian adalah bug laporan yang sulit dilacak.

---

## ✅ 6. Verifikasi & Tantangan Mandiri

**Verifikasi yang sudah dijalankan:**
```bash
./gradlew :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJvm \
          :app:shared:compileKotlinJs :app:shared:assembleAndroidMain   # ✅ semua lolos
```
Manual: buka `http://localhost:3000/crm-sales` → tab **Deal** (4 KPI terisi, cari brand, klik chip `PO Diterima`, kartu tersusun 3 kolom) → tab **Kontak** (avatar inisial, cari nomor 08…, tombol `Chat WhatsApp` membuka `wa.me`).

**Tantangan mandiri:**
- [ ] Tambah KPI kelima "Deal Jatuh Tempo" (deal dengan `expectedCloseDate` < hari ini & belum WON) tanpa menambah dependency apa pun.
- [ ] Ubah chip filter agar multi-select (`Set<DealStage>`), bukan single-select. Perhatikan: `remember` key-nya harus ikut berubah.
- [ ] Tambahkan test unit untuk `winRateOf()` dan `initialsOf()` — keduanya pure function, nol mocking.

