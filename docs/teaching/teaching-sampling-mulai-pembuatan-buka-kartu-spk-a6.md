# 🎓 Modul Pembelajaran: Tombol "Mulai Pembuatan" & Kartu SPK A6 Muncul Otomatis

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, PDF print-ticket flow, UX momen kritis produksi, state hoisting
> **Prasyarat**: Paham struktur `presentation/sampling/`, alur pipeline sampling (`SamplingPipelineStage`), dan dasar coroutine `Result<T>`
> **Referensi Task**: Permintaan langsung — "kartu SPK A6 harusnya muncul ketika mulai; di Program CAM ubah Masuk Mesin Rajut jadi Mulai Pembuatan"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata.** Kartu SPK A6 bukan dokumen administratif — ia adalah *lembar kerja meja*: berisi nomor SPK, QR telusur, POM buyer, dan strip urgensi (posisi SPK di antrean pabrik hari itu). Kartu ini ditempel di meja mesin rajut pada **hari pertama pembuatan**. Sebelumnya:

1. Tombol tahap Program CAM bernama **"Masuk Mesin Rajut ->"** — padahal yang terjadi bukan "masuk mesin", melainkan *SPK mulai dibuat*. Nama yang salah membuat operator ragu menekannya.
2. Saat tombol itu ditekan, **tidak ada yang memunculkan kartu A6** — tim harus sadar sendiri untuk mencari tombol kecil "Kartu SPK A6" di header. Momen kritis yang justru paling butuh kertas justru tidak menghasilkan kertas.
3. Bonus masalah infrastruktur: URL PDF lama dibuka langsung di tab browser — dan **tab browser tidak membawa header `Authorization`**, jadi permintaannya ditolak server. Kartu "harusnya muncul" tapi tidak pernah muncul.

**Analogi Sederhana.** Bayangkan dapur restoran: saat chef menekan tombol *"Mulai Masak"* di tiket order, struk order otomatis tercetak di printer dapur. Anda tidak mendesain dapur di mana chef harus berlari ke komputer kasir untuk mencetak struknya sendiri.

**Hasil Akhir.** Klik **"Mulai Pembuatan ->"** → validasi lembar CAM lolos → SPK maju ke `MACHINE_KNITTING` **dan** tab berisi PDF Kartu SPK A6 terbuka, siap dicetak.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

1. **Langkah 0: Pahami alur data sebelum menyentuh UI.**
   Klik tombol → `SamplingUiEvent.ConfirmStageAdvance` → ViewModel → `remoteDataSource.advanceStage(..., stageInputs = [...])` → server memvalidasi gerbang → tahap berubah. PDF **bukan** bagian dari transaksi ini; ia artefak cetak yang dihitung server saat diminta.
2. **Langkah 1: Selesaikan jalur autentikasi PDF (tiket cetak).**
   `PdfPrintLauncher` (di `SpkPrintActions.kt`) menukar sesi ber-Bearer menjadi URL bertiket ±60 detik via `POST .../print-ticket`, lalu `openInBrowser(url)`. Tanpa ini, tab browser ditolak 401.
3. **Langkah 2: Satu aksi, satu nama.**
   Rename label di **dua tempat** yang memicu transisi identik `CAM_PROGRAMMING → MACHINE_KNITTING`: footer dialog (`SamplingSpkDetailDialog.kt`) dan kartu Kanban (`SamplingKanbanCard.kt`).
4. **Langkah 3: Buka kartu pada momen mulai, setelah gerbang validasi.**
   Di `onClick`, PDF hanya dibuka jika `parseCamSections` menyatakan semua tab lengkap — kalau gagal validasi, tidak ada kartu yang keluar dan tidak ada tahap yang maju.
5. **Langkah 4: Sederhanakan pemakaian ganda dengan satu helper.**
   `openSpkCard` dipakai tombol header **dan** tombol mulai — Aturan Tiga Kali versi mini di dalam satu file.

---

## 🔍 3. Bedah Kode Blok per Blok

### Blok A — Helper pembuka kartu (`SamplingSpkDetailDialog.kt`)

```kotlin
val openSpkCard = {
    printer.open {
        spkCardPdfUrl(it, TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, order.id.value))
    }
}
```

- **`printer.open { ... }`**: menerima lambda `suspend TraceabilityApiClient.(tenantSlug: String) -> Result<String>`. Receiver-nya adalah client, jadi `spkCardPdfUrl(it, ...)` memanggil metode suspend milik client tanpa import tambahan; `it` adalah slug tenant yang sudah di-resolve launcher.
- **Mengapa lambda val, bukan fungsi privat?** Ia menangkap `order` dan `printer` dari scope komposisi. Sebagai `val`, ia bisa langsung dipakai sebagai `onClick = openSpkCard` — satu referensi, dua tombol.

### Blok B — Tombol mulai yang membawa kartunya

```kotlin
ClayButton(
    text = "Mulai Pembuatan ->",
    style = ClayButtonStyle.Accent,
    ...
    onClick = {
        val (currentTabs, _) = parseCamSections(camSections)
        if (currentTabs.isEmpty() || currentTabs.any { !it.isComplete }) {
            camValidationTrigger++          // ← belum lengkap: validasi visual, buka tab yang kosong
        } else {
            openSpkCard()                   // ← kartu A6 muncul di detik mulai
            onSubmitCamProgram(camSections) // ← simpan lembar + maju ke MACHINE_KNITTING
        }
    }
)
```

- **Urutan `openSpkCard()` dulu baru submit**: `open()` hanya *menembakkan* coroutine (fire-and-forget), jadi keduanya berjalan nyaris serentak; memanggil pembuka lebih awal menjaga rantai tetap dalam konteks gesture pengguna — beberapa browser memblokir `window.open` yang bukan berasal dari gesture langsung.
- **Kartu tetap valid walau submit gagal?** Ya. Isi kartu (urgensi, antrean, kode) dihitung server **saat PDF dibuka**, bukan saat tahap berubah. SPK yang masih di CAM pun mencetak kartu yang benar — tidak ada state basi.

### Blok C — Konsistensi Kanban (`SamplingKanbanCard.kt`)

```kotlin
SamplingPipelineStage.CAM_PROGRAMMING -> {
    ClayButton(
        text = "Mulai Pembuatan ->", ...
        onClick = { onAdvanceStage(SamplingPipelineStage.MACHINE_KNITTING) }
    )
}
```

Satu transisi tidak boleh punya dua nama di dua permukaan. (Jalur Kanban ini tidak membuka kartu otomatis — ia jalur pintas tanpa lembar CAM; momen "mulai" yang utuh hidup di dialog.)

---

## 🧠 4. Technology & Approach — "The Why"

| Keputusan | Alternatif yang ditolak | Alasan |
|---|---|---|
| URL **bertiket cetak** (±60 dtk) via `print-ticket` | Mengunduh PDF ke memori lalu di-share | File PDF-nya *menuju printer*, bukan menuju layar; pratinjau browser adalah dialog cetak yang dikenal semua orang. Header `Authorization` tidak pernah ikut ke tab baru — tiket adalah jembatannya. |
| Buka kartu di **klik tombol mulai** (UI level) | Efek setelah `advanceStage` sukses di ViewModel | Menuntut plumbing efek baru (`UiState` + `LaunchedEffect`) untuk keuntungan kecil; PDF memang tidak terikat hasil submit karena dihitung live. Prinsip: jangan menambah mesin state bila fire-and-forget cukup. |
| Rename di dialog **dan** Kanban | Hanya dialog | Dua permukaan, satu aksi — istilah yang menyimpang di salah satunya menciptakan bahasa palsu untuk operator. |
| Helper `openSpkCard` | Menyalin blok `printer.open { ... }` | Duplikasi 2× dalam satu file adalah pintu masuk ke duplikasi ke-3 (Aturan Tiga Kali). |

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Membuka PDF sebelum gerbang validasi.** Kalau `openSpkCard()` dipanggil di luar cabang `else`, kartu tetap tercetak padahal lembar CAM kosong — operator menempel kartu untuk SPK yang belum punya program.
2. **Menunggu `Result` dari `openInBrowser`.** Fungsi itu `expect fun` (bukan suspend, tidak mengembalikan apa pun). Kegagalan tiket ditangani `PdfPrintLauncher.error` dan ditampilkan sebagai teks merah di dialog — jangan coba "menunggu" browser.
3. **Menyalin URL bertiket untuk dipakai nanti.** Tiket mati ±60 detik. Buka segera setelah didapat; jangan simpan ke state.
4. **Menanam warna/status baru untuk tombol mulai.** Tombol tetap `ClayButtonStyle.Accent` yang sudah ada — perubahan label ≠ perubahan bahasa visual (design-system-rules).
5. **Lupa bahwa ada dua pemanggil transisi.** Dialog *dan* kartu Kanban memicu `CAM → MACHINE_KNITTING`. Mengubah perilaku hanya di satu tempat membuat sistem berbohong pada yang lain.

---

## ✅ 6. Verifikasi

1. **Kompilasi multi-target** (JVM, WasmJS, JS, Android, jvmTest):
   ```bash
   ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
             :app:shared:compileKotlinJs :app:shared:assembleAndroidMain :app:shared:jvmTest
   ```
2. **Dengan mata** (`localhost:3000/sampling-order`):
   - Buka SPK di kolom **Program CAM** → footer kini bertuliskan **"Mulai Pembuatan ->"**.
   - Kosongkan salah satu tab bagian garmen → klik tombol → validasi memblokir, **tidak ada** tab PDF dan tidak ada perpindahan kolom.
   - Lengkapi lembar CAM → klik → tab baru berisi **Kartu SPK A6** (satu halaman per ukuran, strip urgensi di bawah) dan kartu pindah ke kolom **Rajut Turun Mesin**.
   - Kartu SPK di kolom yang sama juga bertuliskan "Mulai Pembuatan ->".
3. **Uji kegagalan tiket**: matikan server → klik tombol → pesan galat tiket tampil di dalam dialog, aplikasi tidak crash.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Jalur Kanban (`SamplingKanbanCard`) saat ini tidak membuka kartu A6. Bagaimana cara menambahkannya tanpa menempelkan `PdfPrintLauncher` ke komponen kartu? (Petunjuk: angkat ke `onAdvanceStageRequested` di `SamplingWorkspaceScreen`, satu keputusan di satu tempat.)
- [ ] **Tantangan 2**: `PdfPrintLauncher.error` saat ini hanya tampil di dekat header dialog. Bagaimana bila gagal tiket saat "Mulai Pembuatan" juga menampilkan `statusMessage` lewat `SamplingViewModel` agar konsisten dengan pola error lain?
- [ ] **Tantangan 3**: Generalisasi momen "muncul ketika mulai" ke SPK massal (`SpkPrintActions` di modul Deal) — kapan lembar kerja rajut sebaiknya otomatis terbuka, dan gerbang apa yang harus lolos sebelum itu boleh terjadi?

