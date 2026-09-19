# 🎓 Modul Pembelajaran: Sinkronisasi Stage CRM Deals dengan Progres Sampling & Responsivitas TopBar Mobile

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Sinkronisasi State Bisnis (CRM vs Produksi), Flyway Migration, Compose Multiplatform Adaptive Layout, Skiko WebGL Canvas  
> **Prasyarat**: Dasar Kotlin Multiplatform, Compose Desktop/Web, PostgreSQL Flyway  
> **Referensi Task**: Konsistensi Stage Deal Sampling & Responsivitas Layout Mobile

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
1. **Inkonsistensi State Antar-Departemen (Sales vs Pabrik):**
   Di bagian penjualan (CRM Deals), kartu transaksi dilabeli **`WON` (Dimenangkan)** dengan seluruh milestone bertanda centang hijau. Namun ketika operator membuka bagian pabrik (Sampling & Lantai Produksi), baju sampelnya ternyata masih di meja rajut dan produksi massal belum boleh dimulai. Bagi pemilik pabrik dan klien, ini membingungkan dan merusak kepercayaan data ERP.
2. **Artifact Rendering Skiko WebGL di Chrome DevTools Device Mode:**
   Ketika menguji tampilan mobile via DevTools Device Toolbar, layar terdorong ke bawah 50% karena perbedaan koordinat matriks WebGL pada simulated DPR, sementara di Playwright atau window biasa tampil normal. Selain itu, TopBar desktop yang memuat 6 komponen horizontal mengalami *overflow* di layar ponsel.

### Analogi Sederhana
- **Deal `WON` Prematur**: Seperti restoran yang sudah mencetak struk *"Pesanan Selesai Disajikan"*, padahal koki di dapur baru menyalakan kompor untuk memasak hidangan kedua.
- **TopBar Responsif**: Seperti dasbor mobil — di truk besar ada puluhan tombol sakelar fisik, tapi di city car kecil tombol-tombol tersebut disederhanakan ke layar sentuh ringkas agar setir tetap leluasa digerakkan.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika menghadapi masalah konsistensi lintas modul dan UI adaptif:

1. **Langkah 1: Audit Konsistensi Domain & Data Seed (`server/src/main/resources/db/migration/`)**
   - Periksa status entitas induk (`deals`) dan bandingkan dengan status entitas anak (`sampling_orders`).
   - Buat migrasi Flyway idempotent untuk mengoreksi stage ke `PO_RECEIVED` (`Sampling`) selama ada SPK yang belum `ACC_APPROVED`.
2. **Langkah 2: Dekomposisi Komponen UI Bersama (`presentation/navigation/`)**
   - Angkat TopBar dari `App.kt` ke [`AppTopBar.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/navigation/AppTopBar.kt) untuk menjaga Single Responsibility dan mematuhi batas ukuran file (<600 baris).
3. **Langkah 3: Terapkan Adaptivitas Window Size (`BoxWithConstraints`)**
   - Deteksi `isCompact = maxWidth < ClayBreakpoints.MasterDetail`.
   - Di mobile: Tampilkan hanya menu drawer, judul ringkas (elipsis), dan avatar profil. Sembunyikan dropdown lebar multi-tenant.
4. **Langkah 4: Kunci Canvas Origin di CSS (`styles.css`)**
   - Pasang `position: absolute; top: 0; left: 0; width: 100%; height: 100%;` pada elemen `canvas` agar tidak terpengaruh flow elemen DOM lain.

---

## 🔬 3. Bedah Kode Blok per Blok

### A. Migrasi Database Flyway (`V49__sync_deal_stages_with_sampling_progress.sql`)
```sql
UPDATE deals
SET stage = 'PO_RECEIVED',
    notes = 'Sampling berjalan: DSG-01 sudah ACC (Golden Sample), DSG-02 masih proses rajut mesin.',
    updated_at = CURRENT_TIMESTAMP
WHERE id = 'deal-seed-001';
```
* **Mental Model**: Di domain WeMade ERP, `DealStage.PO_RECEIVED` memetakan label `"Sampling"` di UI kanban deals. Selama DSG-02 masih di meja rajut, deal berada pada fase sampling aktif, bukan sudah selesai (`WON`).

### B. TopBar Adaptif (`AppTopBar.kt`)
```kotlin
@Composable
fun AppTopBar(
    currentScreen: AppNavScreen,
    isCompact: Boolean,
    ...
) {
    Surface(modifier = modifier.fillMaxWidth(), ...) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, ...) {
            // Sisi Kiri
            Row(...) {
                ClayIconButton(onClick = onOpenDrawer, ...)
                if (!isCompact) {
                    Text(text = "WeMade ERP", ...)
                    Text(text = "•", ...)
                }
                Text(
                    text = currentScreen.title,
                    fontSize = if (isCompact) 14.sp else 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // Sisi Kanan: Kontrol Pengguna
            Row(...) {
                if (isAuthenticated && session != null) {
                    if (!isCompact) {
                        PersonaSwitcherDropdown(...)
                        CompanySwitcherDropdown(...)
                    }
                    ProfileDropdown(session = session, onLogout = onLogout)
                }
            }
        }
    }
}
```
* **Mental Model**: Menyesuaikan densitas informasi sesuai *real estate* layar. Menghilangkan kontrol sekunder di mobile mencegah kartu terdorong keluar batas viewport horizontal.

---

## 🛡️ 4. Jebakan Pemula (Common Pitfalls)

1. **Menaruh Status Akhir (`WON`) Terlalu Awal:**
   Jangan mengubah stage deal menjadi `WON` hanya karena buyer sudah komit verbal atau deal memiliki nilai transaksi. Syarat `WON` di manufaktur garmen adalah **seluruh sampel disetujui (ACC) dan PO resmi/DP diterima**.
2. **Hardcoding Warna / Ukuran di Komposable:**
   Selalu gunakan token `WeMadeColors` dan `ClaySpacing`. Jangan gunakan literal `Color(0xFF...)` di luar `WeMadeTheme.kt`.
3. **Mengabaikan Emulasi WebGL Canvas:**
   Saat menguji Compose Wasm di DevTools, ketahuilah bahwa simulated DPR pada DevTools Device Toolbar dapat menggeser WebGL viewport buffer. Uji responsivitas dengan mengecilkan jendela browser secara manual atau menggunakan Playwright untuk hasil pengujian cold-start yang akurat.

---

## ✅ 5. Verifikasi Mandiri

1. **Uji Data Deals:**
   Buka `http://localhost:3000/crm-sales/deals`. Pastikan kartu **BKG Apparel**, **Sejahtera Style**, **Nuansa Wear**, dan **Kanva Knit** kini berlabel **`Sampling` (Oranye)** dengan stepper Sampling aktif (panah kuning ➔), bukan `Won`.
2. **Uji Responsivitas Mobile:**
   Perkecil ukuran jendela browser ke lebar mobile (<840dp). Pastikan baris TopBar hanya menampilkan ikon drawer, nama modul, dan avatar profil secara rapi tanpa ada teks yang terpotong.
