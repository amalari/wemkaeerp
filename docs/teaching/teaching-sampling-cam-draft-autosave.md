# 🎓 Modul Pembelajaran: Autosave Draft Program CAM & Hasil R&D

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Debounce, coroutine `Job` lifecycle, MVI state, race condition UI ↔ server
> **Prasyarat**: Kotlin coroutines (`launch`, `delay`, `cancel`), `MutableStateFlow`, Compose state dasar
> **Referensi Task**: Pertanyaan user "Simpan Program maksudnya apa / kenapa ga autosave?" di dialog Detail SPK

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: Dialog Detail SPK punya dua tombol, "Simpan Program" dan "Mulai Pembuatan →".
  User bingung bedanya. Lebih parah lagi, kalau lupa menekan "Simpan Program" lalu menutup dialog,
  isian tensi dan instruksi panah **hilang**.
- **Analogi**: Google Docs tidak punya tombol "Save". Tombol yang ada hanya yang *mengubah status*,
  misalnya "Share" atau "Submit". Menyimpan draft itu kewajiban sistem, bukan kewajiban manusia.
- **Hasil akhir**: Setiap ketikan tersimpan otomatis ±1,5 detik setelah user berhenti mengetik.
  Header menampilkan `Menyimpan…` / `Tersimpan` + ikon centang. Footer hanya menyisakan aksi yang
  **memajukan tahap** ("Mulai Pembuatan →").

---

## 🧭 2. "Start dari Mana?"

1. **Langkah 0 — Telusuri jalur lama.** Tombol memanggil `onSaveRdResult`, lalu
   `SamplingUiEvent.SaveStageInput`, lalu `saveFullOrder()`. Ternyata `saveFullOrder` menyalakan
   `isSubmitting` **dan** memunculkan toast. Kalau jalur ini dipakai apa adanya untuk autosave,
   tombol "Mulai Pembuatan" akan berkedip disabled dan toast muncul di setiap ketikan. Jadi kita
   butuh jalur simpan tersendiri.
2. **Langkah 1 — Tentukan state.** Buat `DraftSaveStatus` (Idle/Saving/Saved/Failed) yang dibungkus
   `DraftSaveState(orderId, status)`.
3. **Langkah 2 — Buat autosaver.** `SamplingDraftAutosaver` sengaja dipisah dari ViewModel, karena
   ViewModel sudah 468 baris (soft limit presentation 400). Polanya meniru `SamplingStageWorkActions`.
4. **Langkah 3 — Sambungkan ke ViewModel.** Event `SaveStageInput` kini diteruskan ke autosaver, dan
   `confirmStageAdvance` memanggil `cancelPending()` lebih dulu.
5. **Langkah 4 — Ubah UI.** `onSectionsChange` memanggil `onDraftChange`, header mendapat
   `DraftSaveIndicator`, dan kedua tombol simpan manual dihapus.

Domain (`core`) **tidak disentuh sama sekali**. `SamplingOrder.fillStageInput` sudah ada. Autosave
murni urusan *kapan* menyimpan, jadi tempatnya di presentation.

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Debounce dengan `Job`

```kotlin
pending?.cancel()
pending = scope.launch {
    delay(debounceMillis)
    setStatus(orderId, DraftSaveStatus.Saving)
    val latest = state.value.orders.firstOrNull { it.id == orderId } ?: return@launch
    remote.saveOrder(tenantSlug, latest.fillStageInput(stage, sections, Clock.System.now()))
    ...
}
```

- Setiap ketikan membatalkan job sebelumnya. Hasilnya, satu sesi mengetik hanya menghasilkan
  **satu** request.
- `latest` diambil **setelah** delay, bukan saat mengetik. Dengan begitu, perubahan order lain yang
  datang di sela waktu itu (misalnya hasil `load()`) tidak tertimpa snapshot lama.
- Job hidup di `scope` ViewModel, bukan di `LaunchedEffect`. Menutup dialog di tengah debounce
  tidak membatalkan simpan, jadi tidak perlu logika "flush saat dismiss".

### Blok B: Skip isi identik

```kotlin
if (sections.isEmpty() || current.stageInputFor(stage)?.sections == sections) return
```

- Compose kadang memancarkan ulang isi yang sama (normalisasi saat render). Karena
  `StageInputSection` adalah `data class`, perbandingan `==` sudah struktural, jadi cukup satu baris.
- `isEmpty()` wajib dicek karena `fillStageInput` punya `require(sections.isNotEmpty())`.

### Blok C: Status diikat ke `orderId`

```kotlin
fun statusFor(id: SamplingOrderId) = if (orderId == id) status else DraftSaveStatus.Idle
```

Skenarionya begini: user mengetik di SPK-0051, menutup dialog, lalu membuka SPK-0052. Respons
simpan milik 0051 datang belakangan. Tanpa `orderId`, dialog 0052 akan salah menampilkan
"Tersimpan" (+ ikon centang).

### Blok D: Membatalkan draft sebelum pindah tahap

```kotlin
private fun confirmStageAdvance(...) {
    draftAutosaver.cancelPending()
    scope.launch { ... advanceStage(..., stageInputs = listOf(StageWorkInput(inputStage, sections))) }
}
```

Payload `advanceStage` sudah membawa isi terbaru. Kalau draft yang tertunda dibiarkan jalan,
responsnya (order masih di tahap CAM) bisa **menimpa** order yang baru saja maju ke Rajut di state
lokal, dan kartu Kanban seolah "mundur".

### Blok E: UI hanya melapor, tidak memutuskan

```kotlin
onSectionsChange = {
    camSections = it
    if (isCamStage || isRdStage) onDraftChange(it)
}
```

Tahap gerbang (SPK Masuk/Penentuan Alur) sengaja dikecualikan. Lembar CAM belum resmi di tahap
itu, dan isinya baru tersimpan saat "Simpan & Masuk Program CAM".

---

## ⚖️ 4. The "Why"

| Pendekatan | Alternatif | Kenapa kita pilih ini | Risiko alternatif |
|---|---|---|---|
| Debounce di ViewModel | `LaunchedEffect(camSections) { delay(); save }` di Composable | Bisa diuji tanpa UI; tidak ikut batal saat dialog ditutup | Ketikan terakhir hilang saat dialog ditutup, dan logika timing tersebar ke UI |
| Status autosave terpisah | Pakai ulang `isSubmitting` | Tombol utama tetap aktif selama mengetik | Tombol berkedip disabled, toast spam |
| Class `SamplingDraftAutosaver` | Tambah langsung ke ViewModel | Satu tanggung jawab, ViewModel tidak lewat 500 baris | God ViewModel (§14) |
| Hapus tombol manual | Autosave + tetap ada tombol | Satu model mental: semua tersimpan, tombol = pindah tahap | User tetap bertanya "perlu klik simpan dulu?" |

---

## ⚠️ 5. Jebakan Pemula

1. **Autosave tanpa debounce.** Setiap huruf menjadi satu request PUT, dan responsnya bisa datang
   tidak berurutan, sehingga isi lama menimpa isi baru.
2. **Menyimpan snapshot saat mengetik.** Isi order diambil dari sebelum delay, sehingga perubahan
   lain di sela waktu itu hilang.
3. **Lupa membatalkan draft sebelum aksi final.** Terjadi race: respons draft menimpa hasil pindah tahap.
4. **Status global tanpa kunci identitas.** Indikator "Tersimpan" bocor ke dialog SPK lain.

---

## 🧪 6. Membuktikan Kode Bekerja

`app/shared/src/commonTest/.../SamplingDraftAutosaverTest.kt` memakai `runTest` + `advanceTimeBy`
(virtual time, sehingga tidak ada `sleep` sungguhan):

- `draft change when typing burst should save only last content once`: dua ketikan hanya menghasilkan
  satu request berisi "12", dan `statusMessage`/`isSubmitting` tidak tersentuh.
- `draft change when content equals saved input should not send request`
- `cancel pending when stage advance starts should drop debounced draft`
- `draft save when remote fails should expose failed status for that order only`

Test ini memakai fake `SamplingRemoteDataSource` yang hanya mengimplementasikan `saveOrder`.
Method lain sengaja `error(...)` supaya pemanggilan tak terduga langsung ketahuan.

---

## 🏆 7. Tantangan Mandiri

- [ ] Status `Failed` sekarang hanya bisa pulih lewat ketikan berikutnya. Tambahkan tombol "Coba lagi"
      yang mengirim ulang draft terakhir. Petunjuk: autosaver perlu mengingat argumen terakhirnya.
- [ ] Setelah beberapa detik, "Tersimpan" (+ ikon centang) bisa diubah jadi "Tersimpan 10:42". Di mana jam itu
      sebaiknya dihitung, dan kenapa bukan di Composable?
- [ ] Bagaimana kalau dua orang mengedit SPK yang sama di dua browser? Rancang deteksi konflik
      memakai `updatedAt`.

---

## ➕ Lanjutan: Kartu SPK A6 Baru Dibuka Setelah Server Mengonfirmasi

**Masalah:** `openSpkCard()` dulu dipanggil di `onClick` "Mulai Pembuatan", **sebelum** request pindah
tahap terkirim. Kalau pindah tahap gagal, kartu produksi tetap keluar untuk SPK yang masih di CAM.
PDF-nya juga diminta berbarengan dengan pindah tahap, jadi server bisa menghitung urgensi dari tahap lama.

**Solusi:** pakai pola *one-shot state* ala MVI.

```kotlin
// ViewModel — hanya di cabang onSuccess
spkCardToPrint = if (openSpkCardOnSuccess) updated.id else current.spkCardToPrint

// Screen — konsumsi, lalu reset
LaunchedEffect(state.spkCardToPrint) {
    val orderId = state.spkCardToPrint ?: return@LaunchedEffect
    spkCardPrinter.open { spkCardPdfUrl(it, TraceWorkOrderRef(TraceWorkOrderKind.SAMPLING, orderId.value)) }
    viewModel.onEvent(SamplingUiEvent.SpkCardPrintHandled)
}
```

- **Kenapa launcher pindah ke Screen?** Saat sukses, ViewModel menutup dialog (`spkDetailTarget = null`).
  Launcher di dalam dialog ikut hilang bersama `rememberCoroutineScope`-nya, sehingga coroutine
  pengambil tiket PDF dibatalkan di tengah jalan.
- **Kenapa ada flag `openSpkCardOnSuccess`?** `onSubmitCamProgram` juga dipakai tahap gerbang
  (→ Program CAM), dan di tahap itu belum ada kartu fisik. Keputusan "perlu dicetak atau tidak" dibuat
  oleh pemanggil yang tahu konteksnya.
- **Kenapa tidak takut popup blocker?** Sejak tiket PDF diperkenalkan, `PdfPrintLauncher.open` sudah
  asinkron (ambil tiket dulu, baru `openInBrowser`). Pembukaan kartu memang sudah tidak terikat
  gestur klik, jadi menundanya sampai respons server tidak mengubah perilaku itu.
- **Error tidak boleh senyap.** Dialog sudah tertutup, jadi `spkCardPrinter.error` ditampilkan
  sebagai badge di layar Kanban.
