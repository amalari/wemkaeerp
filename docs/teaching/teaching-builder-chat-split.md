# 🎓 Modul Pembelajaran: Chat Builder Berlayout Chat + Panel "Hasil Request"

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Layout Compose (tinggi terbatas vs scroll), komposisi pane yang sudah ada, komponen design system buta-domain, pemisahan file per tanggung jawab
> **Prasyarat**: Dasar Compose (`Column`, `LazyColumn`, `BoxWithConstraints`), paham tokens Clay
> **Referensi Task**: Rencana `berdasarkan-hasil-garment-apps` (tanpa issue GitHub)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata
- **Masalah nyata**: `/builder/chat` menampilkan satu input di atas dan body kosong. Penyebabnya bukan chat-nya, melainkan shell: konten dibungkus `verticalScroll`, sehingga `LazyColumn` ber-`weight(1f)` di dalamnya tidak punya batas tinggi dan runtuh jadi 0. Input otomatis "naik" menempel ke judul.
- **Analogi**: seperti meja kerja yang bisa diperpanjang tanpa batas. Kamu minta "isi sisa meja" untuk riwayat, tapi tidak ada ujung meja, jadi sisanya nol.
- **Hasil akhir**: chat ala WhatsApp (riwayat tengah, composer menempel bawah), plus panel **Hasil request** berisi tab Modul / Fitur / Alur Data / Prototype.

## 🧭 2. "Start dari Mana?"
1. **Akar masalah dulu** — `BuilderShell`: untuk section `chat`, jangan `verticalScroll`, beri `fillMaxHeight()`.
2. **Komponen design system generik** — `ClayTabBar` (dari `ClayChoiceChip`) dan `maxLines` di `ClayTextField`. Keduanya buta domain.
3. **Model murni + test** — `ChatModel.kt` (`parseMessages`, `featuresOf`, `openingEntryFor`) agar logika bisa dites tanpa UI.
4. **UI per tanggung jawab** — `ChatComposer`, `ChatMessageBubble`, `ChatResultPanel`, lalu shell `BuilderChatPane` yang hanya merakit.
5. **Cek dengan mata** di tenant `wemade-demo`.

## 🧱 3. Bedah Kode
### Shell: tinggi terbatas khusus chat
```kotlin
.then(if (selected == "chat") Modifier else Modifier.verticalScroll(rememberScrollState()))
```
Pane lain tetap menggulir; chat menggulir **di dalam** `LazyColumn`-nya sendiri. Satu aturan: *list yang bisa scroll jangan dibungkus scroll lain pada sumbu yang sama*.

### Composer: Enter kirim, Shift+Enter baris baru
```kotlin
Modifier.onPreviewKeyEvent { e ->
    if (e.type == KeyEventType.KeyDown && e.key == Key.Enter && !e.isShiftPressed) { if (canSend) onSend(); true } else false
}
```
`onPreviewKeyEvent` dipasang di induk field, sehingga event dicegat sebelum field menyisipkan baris baru. `ClayTextField(singleLine=false, minLines=1, maxLines=6)` membuatnya tumbuh lalu menggulir di dalam.

### Panel hasil = komposisi ulang, bukan tulis ulang
`ChatResultPanel` memanggil `ModuleMapPane`, `DataFlowPane`, `PrototypeRenderer` dengan draf yang sama. Tab **Fitur** diturunkan dari draf (`featuresOf`): layar prototype modul + port masuk/keluar slotnya. Tidak ada entitas "fitur" baru di domain, dan kode tidak menyebut satu industri pun (aturan mesin lintas pack).

### Responsif
`BoxWithConstraints` + `ClayBreakpoints.MasterDetail` (840dp): lebar → chat | panel 440dp; sempit → panel menggantikan chat.

### Pesan pembuka lokal
Bila riwayat kosong tapi draf tenant ada (hasil `EnsureTenantWorkingDraftUseCase`), `openingEntryFor` menampilkan satu bubble lokal (tidak disimpan) berisi modul aktif. Inilah "hasil garment wemade-demo" yang langsung terlihat.

## ⚖️ 4. The "Why"
| Pendekatan | Alternatif | Alasan | Risiko alternatif |
|---|---|---|---|
| Split chat + panel | Tab penuh | Percakapan tetap terlihat saat melihat hasil | Konteks hilang tiap pindah tab |
| Pakai ulang pane Fase D | Tulis ulang | Sudah teruji, satu sumber tampilan | Dua renderer yang menyimpang |
| Pesan pembuka lokal | Simpan di server | Tanpa migrasi/endpoint, tanpa risiko tulis | Hilang saat ada pesan nyata (sengaja) |
| Muat ulang lewat `GET /chat` setelah kirim | Ubah envelope `POST` | Tidak mengubah kontrak server | Satu request tambahan |

## ⚠️ 5. Jebakan
1. **`LazyColumn` dalam `verticalScroll`** — tinggi 0 atau crash saat pesan banyak. Beri tinggi terbatas dari induk.
2. **Envelope tidak seragam** — `POST /chat` mengembalikan array, `GET` mengembalikan `{messages}`; `parseMessages` hanya membaca yang kedua (array → daftar kosong, ditest). Baca ulang via GET.
3. **Warna di atas kartu** — tidak ada token `OnPrimary`; bubble user memakai latar `SurfaceMuted` + outline biru (state lewat warna, bukan token baru yang dipaksakan).

## 🧪 6. Pembuktian
- `ChatModelTest`: envelope, array telanjang, `featuresOf` pada pack non-garment (`bordir`), pesan pembuka.
- Kompilasi JVM, WasmJs, JS hijau. **Android tidak dikompilasi** (SDK tidak ada di mesin ini).
- Cek visual `wemade-demo`: composer di bawah, panel 4 tab, tab Alur Data, composer multi-baris.

## 🏆 7. Tantangan
- [ ] Tampilkan diff patch yang sebenarnya (modul bertambah/berkurang) di panel untuk usulan yang belum diterapkan, bukan hanya ringkasan teks.
- [ ] Buat agent memakai `history` percakapan (sekarang stateless).
- [ ] Seed `screens` garment agar tab Prototype tidak kosong.

---

## 🔁 Pembaruan: menu Builder pindah ke drawer header, chat selalu dua panel
- **Navigasi**: sidebar tetap `BuilderSidebar` dicabut dari `BuilderShell` (tetap dipakai `PlatformAdminConsole`). Menu Builder kini dirender oleh `ClayNavDrawer` yang sama dengan ERP, lewat tombol hamburger di header. `BuilderNav.kt` memuat `builderDrawerSections`, `builderSection`, `builderSectionTitle`, `BuilderDrawerFooter`. Di `App.kt` hanya ada percabangan `if (builderRoute)` pada sumber section/judul/footer drawer — rute ERP tidak berubah. `App.kt` dijaga ≤ 599 baris (aturan ratchet): 597.
- **Chat**: layar lebar selalu menampilkan percakapan (42%) + Hasil request (58%); tombol tutup panel hanya ada di layar sempit. Padding konten chat dikecilkan agar ruangnya penuh.
- **Jebakan**: berbagi ruang kerja dengan sesi lain — kompilasi gagal karena file orang lain setengah jadi. Jangan "membereskan" file itu diam-diam; laporkan.
