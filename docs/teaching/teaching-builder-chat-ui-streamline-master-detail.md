# Teaching: Builder Chat UI Streamline, Selected Module in Chat & Full-Screen Prototype

## 1. Konteks & Latar Belakang Desain
Pada antarmuka AI Builder (`/builder/chat`), evaluasi sebelumnya menunjukkan:
1. **Header Modul & Teks Fitur Memakan Ruang Vertikal**: Prototype aplikasi terdorong ke bawah dan terpotong karena harus berbagi layar dengan kartu modul dan teks fitur yang panjang.
2. **Konteks Percakapan AI Membutuhkan Modul Terpilih**: Pengguna mendiskusikan *requirements*, logika, dan klarifikasi sebuah modul di panel chat kiri. Menempatkan modul aktif di panel chat membuat konteks percakapan menjadi eksplisit dan fokus.

## 2. Arsitektur Solusi Kombinasi (Chat Selected Module + Prototype Selayar)

### A. Modul Terpilih di Sidebar Chat (`SelectedModuleChatCard`)
- Ditempatkan di bagian atas panel chat:
  - **Identitas Modul**: Ikon 3D clay / vektor, nama modul, badge divisi (`PENJUALAN & RELASI PELANGGAN`), dan status aktif.
  - **Quick Dropdown Switcher**: Tombol `[Ganti ▾]` atau `[Semua Modul ▾]` memungkinkan berpindah modul secara instan dari sidebar chat.
  - **Accordion Fitur Ringkas**: Seksi `Fitur Modul (N) ▾` dapat di-expand/collapse tepat di area chat. Pengguna dapat melirik daftar fitur modul sambil berdiskusi dengan asisten AI tanpa harus berpindah tab.

### C. Prototype Full-Width & Carousel Kiri-Kanan (`PrototypeRenderer.kt`)
Untuk memaksimalkan keterbacaan data (terutama tabel lebar dan papan Kanban multi-kolom):
1. **Full-Width Canvas**: Batasan frame perangkat sempit (360dp) dihapus; prototipe sekarang membentang `fillMaxWidth()` memenuhi lebar panel kanan.
2. **Kapsul Navigasi Carousel**:
   - Tombol panah kiri dan kanan (`ClayIconButton`) untuk berpindah layar satu per satu.
   - Indikator posisi layar: `Layar X dari Y: [Judul Layar] [BADGE WIDGET]` disertai titik-titik navigasi interaktif (dot pills).
3. **Pills Quick-Jump Horisontal**: Baris pil mini yang dapat digulir menyajikan shortcut langsung (`1. Daftar PO & Prospek`, `2. Papan SPK Sampling`, dst.) dengan state aktif yang menonjol.
4. **Isolasi Layar Aktif**: Setiap kali berpindah slide, hanya layar yang aktif yang dirender penuh tanpa tumpukan scroll vertikal yang melelahkan.

## 3. Komponen & Berkas Terkait
- [`PrototypeRenderer.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/PrototypeRenderer.kt): Menambahkan dukungan `carousel = true` dan `fullWidth = true`, kapsul kontrol prev/next, dot indicators, dan quick-jump pills.
- [`SelectedModuleChatCard.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/SelectedModuleChatCard.kt): Kartu konteks modul aktif di panel chat dengan dropdown ganti modul dan accordion fitur ringkas.
- [`BuilderChatPane.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/BuilderChatPane.kt): Mengintegrasikan `SelectedModuleChatCard` di bagian atas sidebar chat.
- [`ChatResultPanel.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/builder/chat/ChatResultPanel.kt): Menyusun 3 tab baru (`Prototype`, `Fitur & Spek`, `Alur Data`) dengan Prototype memanggil Carousel full-width.
- [`ModuleDetailPane.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/ModuleDetailPane.kt): Tampilan komprehensif spesifikasi modul dan port data untuk tab `Fitur & Spek`.

## 4. Bukti Verifikasi Visual (Playwright)
- **Layar Penuh (Full Width)**: Kartu prototype tabel dan kanban memenuhi lebar kontainer hasil request secara responsif.
- **Carousel Kiri-Kanan**: Navigasi menggunakan panah maju/mundur dan baris quick-jump pills berpindah antar layar (misal Slide 1 "Daftar PO & Prospek" ke Slide 2 "Papan SPK Sampling") dengan mulus.
- **Modul Terpilih di Chat**: Memilih modul dari dropdown chat mengisolasi layar modul tersebut, atau memilih "Semua Modul" mengaktifkan navigasi carousel 9 layar tenant.
