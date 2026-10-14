# Teaching: Moqups Monochrome Wireframe & Interactive Flow Canvas

## 1. Konteks & Latar Belakang Desain
Pengguna menginginkan tampilan pratinjau prototype di antarmuka AI Builder (`/builder/chat`) memiliki estetika **wireframe minimalis hitam-putih ala [Moqups](https://moqups.com/)**, namun **tetap bisa terhubung (bisa nyambung)** antar alur langkah kerja sistem dan interaktif untuk manipulasi data (bukan sekadar gambar sketsa mati).

Sebelumnya:
- Prototype menggunakan palet warna standar Claymorphism dengan aksen warna primer biru cerah (`#2563EB`), badge hijau/merah, dsb.
- Ketika sebuah modul difokuskan dari sidebar chat, prototype hanya mengisolasi layar modul tersebut sehingga pengguna kehilangan konteks alur kerja keseluruhan dan tidak bisa berpindah atau menyambung ke langkah selanjutnya di pipeline.

## 2. Arsitektur Solusi: Moqups Wireframe & Connected Pipeline Flow

### A. Dot Grid Canvas & Moqups Artboard Window Frame
- **Kanvas Bertitik (`moqupsDotGrid`)**: Latar belakang kanvas pratinjau digambar dengan kisi titik abu-abu halus berjarak 20dp (`WeMadeColors.WireframeGrid` di atas `WeMadeColors.WireframeCanvas`), mereplikasi artboard Moqups.
- **Bingkai Jendela Artboard (`MoqupsArtboardFrame`)**:
  - Tiga titik kontrol jendela ala macOS/Moqups (`● ○ ○`).
  - Baris breadcrumb judul wireframe: `wireframe / [Nama Modul] / [Judul Layar]`.
  - Lencana jenis widget stensil monokrom monospaced: `[ TABEL ]`, `[ FORM ]`, `[ KANBAN ]`, `[ DASBOR ]`.
  - Border tajam dan clean `1.5dp` (`WeMadeColors.WireframeBorder` / `#1E293B`).

### B. Rantai Alur Antar Layar Terhubung ("Bisa Nyambung")
- **Moqups Flow Navigator (`MoqupsFlowNavigator`)**:
  - Menampilkan seluruh rangkaian alur kerja sistem (`draft.screens`) secara berurutan dalam bentuk rantai langkah terhubung dengan konektor panah `──▶`.
  - Setiap node langkah menampilkan nomor urut, judul layar, dan nama modul.
  - Node aktif ditandai dengan kapsul hitam pekat (`#0F172A`) dan teks putih; node lain menggunakan kartu putih dengan outline hitam dan kursor pointer.
  - Pengguna dapat mengetuk **langkah mana saja** dalam diagram alur untuk langsung berpindah layar.
  - Tombol **`Lanjut Alur: [Layar Berikutnya] ➔`** di footer artboard memungkinkan pengguna melangkah maju menelusuri pipeline bisnis secara bertahap.
  - Toggle cakupan alur: Pengguna dapat berganti antara `[ Semua Alur (N) ]` dan `[ Modul Ini Saja ]`.

### C. Stensil Komponen Monokrom & Tetap Interaktif
- **Tabel Wireframe (`InteractiveTable`)**:
  - Tombol `+ Tambah` hitam solid dengan teks putih.
  - Header kolom dengan garis batas bawah tegas dan font berbobot tebal.
  - Lencana status baris bertransformasi menjadi pill monokrom wireframe (latar putih, border gelap, teks hitam).
  - Tetap **100% interaktif**: status dapat diklik untuk membuka dropdown perpindahan status, sel tabel dapat diklik untuk pengeditan inline, baris baru dapat ditambahkan via editor inline, dan pencarian/pengurutan kolom tetap aktif.
- **Form & Kanban Wireframe**:
  - Input field dengan border tegas dan tombol simpan hitam kontras.
  - Kolom Kanban bernuansa wireframe netral dengan kartu putih ber-outline.

## 3. Komponen & Berkas Terkait
- [`MoqupsWireframeCanvas.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/MoqupsWireframeCanvas.kt): Komponen kanvas dot grid, bilah navigasi rantai alur `MoqupsFlowNavigator`, bingkai jendela `MoqupsArtboardFrame`, dan koordinator `MoqupsWireframeRenderer`.
- [`PrototypeRenderer.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/PrototypeRenderer.kt): Menambahkan delegasi `wireframe = true` ke `MoqupsWireframeRenderer` dan mengekspos `WidgetBody` sebagai `internal`.
- [`InteractiveTable.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/InteractiveTable.kt): Menambahkan parameter `wireframe: Boolean = true` untuk styling tombol `WireframeButton`, border header, pemisah baris, dan badge status monokrom.
- [`InteractiveBlock.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/InteractiveBlock.kt): Meneruskan flag `wireframe` ke seluruh blok interaktif.
- [`InteractiveForm.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/InteractiveForm.kt) & [`InteractiveKanban.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/InteractiveKanban.kt): Mengadopsi tombol dan outline wireframe monokrom.
- [`ChatResultPanel.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/ChatResultPanel.kt): Memanggil `PrototypeRenderer` dalam mode `wireframe = true` dengan seluruh daftar layar draf (`draft.screens`) sehingga alur tersambung utuh.
- [`WeMadeTheme.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/theme/WeMadeTheme.kt): Menambahkan token warna peran wireframe (`WireframeCanvas`, `WireframeGrid`, `WireframeBorder`, `WireframeOnSurface`, dsb.).

## 4. Verifikasi & Pengujian
- **Kompilasi Multiplatform**: Lulus tanpa galat untuk target JVM (`compileKotlinJvm`) dan Web Wasm (`compileKotlinWasmJs`).
- **Unit Test Discovery**: Seluruh unit test suite `presentation.discovery.*` berjalan hijau.
- **File Size Discipline**: Seluruh berkas yang dimodifikasi mematuhi ambang batas ukuran (di bawah hard limit 600 baris).
- **Graphify**: Knowledge graph diperbarui dengan `graphify update .`.
