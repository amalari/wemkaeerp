# 🎓 Modul Pembelajaran: Interactive Coach Mark — Spotlight Tembus & Langkah Beraksi (TRD-TUT-002)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform pointer/hit-test, `PointerEventPass`, state machine domain, fungsi murni untuk geometri, degradasi anggun (graceful degradation)
> **Prasyarat**: Paham dasar Compose (`Modifier`, `Canvas`, recomposition), sealed interface Kotlin, dan sudah membaca [`teaching-flow-001`](../teaching/teaching-flow-001-industry-stage-templates.md) soal "kode vs data".
> **Referensi Task**: [`TRD-TUT-002`](../trd/features/TRD-TUT-002-interactive-coach-mark.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah nyata
Coach mark lama bagus untuk **membaca** UI, tapi patah untuk **mengajarkan tindakan**. Selama tutorial berjalan,
`ClaySpotlightScrim` digambar sebagai satu `Canvas(fillMaxSize)` + `pointerInput { detectTapGestures { } }` — node
itu **menelan seluruh ketukan di layar**. Akibatnya saat tutorial menyorot tombol **Tambah** dan berkata "klik ini",
tombolnya **tidak bisa diklik**. Untuk pertanyaan "cara menambah data", pengguna justru harus menutup tutorial dulu,
lalu mengulang klik manual — langkah tutorial tidak ikut maju. Patah.

### Analogi sederhana
Bayangkan museum dengan pemandu. Versi lama: pemandu memasang **kaca gelap se-lebar ruangan** dengan satu lubang
kecil menunjuk lukisan — Anda boleh *melihat* lukisan lewat lubang, tapi tidak boleh **menyentuh** apa pun.
Versi baru (TRD ini): pemandu memasang **empat panel gelap** yang mengelilingi lukisan, bukan satu lembar penuh —
lukisan itu sendiri **tidak tertutup apa pun**, jadi Anda boleh menyentuh & menekannya. Panel gelap di sekelilingnya
tetap mencegah Anda menyentuh karya lain.

### Hasil akhir yang diharapkan
- Langkah coach mark bisa diberi mode **tembus** (`interactive = true`): klik pada elemen tersorot sampai ke aplikasi.
- Langkah bisa maju karena **pengguna beraksi** (`advanceOn = UserClick`) atau **state muncul** (`StateAppears`), bukan
  hanya tombol Lanjut (`Manual`, default).
- Klik di **luar** target tetap diblokir (fokus terjaga); **Esc** tetap menutup; **Lanjut** selalu ada (no dead-end).

---

## 🧭 2. "Start dari Mana?" — Order of Operations

Kalau kamu harus mengetik fitur ini dari layar kosong, urutannya:

1. **Langkah 0 — Bekukan kontrak domain (`core`), BUKAN UI.**
   `AdvanceTrigger` (sealed interface) + dua field baru di `TutorialStep`. Ini "kode vs data" (Uji Variabilitas):
   perilaku sistem, bukan data tenant → boleh jadi enum/sealed di `core`. Mulai dari sini karena UI cuma konsumen kontrak.
2. **Langkah 1 — State machine: `TutorialController.notifyAction`.**
   Semantik dibekukan: *hanya* memajukan bila langkah aktif menunggu `UserClick(anchorId)` yang cocok. Tanpa ini,
   UI tidak punya "otak" untuk merespons aksi.
3. **Langkah 2 — Geometri = fungsi murni `spotlightRegions`.**
   Hitung ≤4 `Rect` mengelilingi lubang. **Murni** (tanpa Compose) supaya bisa diuji tanpa emulator/browser. UI cuma menggambar hasilnya.
4. **Langkah 3 — Scrim tembus (`ClaySpotlightScrim.interactive`).**
   Render tiap region sebagai node anak tersendiri; lubang **tanpa node** → hit-test menembus.
5. **Langkah 4 — Observasi tap tanpa membajak (`Modifier.tutorialAnchor(onAction)`).**
   `PointerEventPass.Initial`, **jangan consume** — `onClick` asli tetap jalan.
6. **Langkah 5 — Wiring di `TutorialLayer`.**
   Hubungkan `UserClick`/`StateAppears` → controller; teruskan `interactive` ke overlay.
7. **Langkah 6 — A11y: Esc + fokus callout.**

> **Kenapa UI terakhir?** Karena seluruh keputusan sulit (kapan maju, ke mana tembus) sudah dibekukan di langkah 0–2
> sebagai kontrak + fungsi murni. UI tinggal "menggambar & menyambungkan", jadi bug UI tidak merusak logika.

---

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Kontrak domain — `AdvanceTrigger` + field `TutorialStep`

```kotlin
// core/.../domain/tutorial/ModuleTutorial.kt
sealed interface AdvanceTrigger {
    data object Manual : AdvanceTrigger                                  // default — perilaku lama
    data class UserClick(val anchorId: TutorialAnchorId) : AdvanceTrigger
    data class StateAppears(val anchorId: TutorialAnchorId) : AdvanceTrigger
}

data class TutorialStep(
    val title: String,
    val body: String,
    val anchor: TutorialAnchorId? = null,
    val screen: ModuleId? = null,
    val placement: CalloutPlacement = CalloutPlacement.BOTTOM,
    val interactive: Boolean = false,                       // FR-1
    val advanceOn: AdvanceTrigger = AdvanceTrigger.Manual,  // FR-2
)
```

**Mengapa begini?**
- **Default `Manual` + `interactive = false`** = kompatibel mundur. Ribuan langkah tutorial lama tidak menyebut field
  baru; karena default-nya "perilaku lama", **nol migrasi konten**. Ini keputusan disiplin: fitur opt-in per langkah.
- **`sealed interface`**, bukan enum, sesuai aturan Kotlin repo (§7) — dan agar `UserClick`/`StateAppears` bisa membawa
  payload `anchorId`.

### Blok B: State machine — `notifyAction`

```kotlin
// app/.../presentation/tutorial/TutorialController.kt
fun notifyAction(anchorId: TutorialAnchorId) {
    val current = state.value ?: return
    val advance = current.step.advanceOn
    if (advance is AdvanceTrigger.UserClick && advance.anchorId == anchorId) next()
}
```

**Mengapa begini?**
- Sinyal dari **semua** anchor masuk ke sini, tapi hanya yang **cocok** yang memajukan. Ini mencegah "klik sembarang
  elemen" memajukan langkah. `Manual`/`StateAppears` diabaikan — `notifyAction` bukan hak mereka.

### Blok C: Geometri murni — `spotlightRegions`

```kotlin
// app/.../presentation/designsystem/ClaySpotlightScrim.kt
internal fun spotlightRegions(container: IntSize, target: Rect?, padding: Float, radius: Float): List<Rect> {
    if (target == null) return emptyList()
    val hole = target.inflate(padding)
    val w = container.width.toFloat(); val h = container.height.toFloat()
    return buildList {
        val top = Rect(0f, 0f, w, hole.top);        if (top.height > 0f) add(top)
        val bottom = Rect(0f, hole.bottom, w, h);   if (bottom.height > 0f) add(bottom)
        val start = Rect(0f, hole.top, hole.left, hole.bottom); if (start.width > 0f) add(start)
        val end = Rect(hole.right, hole.top, w, hole.bottom);   if (end.width > 0f) add(end)
    }
}
```

**Mengapa begini?**
- **`target == null` → `emptyList`** = degradasi FR-5: tanpa anchor, mode tembus tidak berlaku (scrim penuh).
- **Region ≤ 0 dibuang** (`if (height>0)`/`if (width>0)`): target yang menyentuh tepi layar tidak menghasilkan region
  tipis/error — dibuang saja. Ini yang diuji `targetTouchingTopEdge_dropsEmptyTopRegion`.
- **`radius` dibekukan di signature** (Track 0) meski tidak mengubah persegi — pemanggil memakainya untuk outline lubang.

### Blok D: Scrim tembus — node per region (bukan path)

```kotlin
// ClaySpotlightScrim.kt — mode interactive
Box(modifier.fillMaxSize()) {
    spotlightRegions(containerSize, target, paddingPx, radiusPx).forEach { region ->
        Box(Modifier.offset { IntOffset(region.left.toInt(), region.top.toInt()) }
                .size(region.width.toDp(), region.height.toDp())
                .pointerInput(region) { detectTapGestures { } }) {   // region gelap menelan ketukan
            Canvas(Modifier.fillMaxSize()) { drawRect(scrim, Offset.Zero, Size(size.width, size.height)) }
        }
    }
}
// Outline lubang digambar Canvas terpisah TANPA pointerInput — tampilan tak berubah.
```

**Mengapa begini? (Ini inti FR-1.)** Compose menghitung **hit-test per node layout**, bukan per piksel path. Satu
`Canvas` penuh + `pointerInput` = node menutupi semua → menelan semua ketukan (itu bug lama). Satu `Canvas` dengan
`Path` berlubang pun **tetap** menelan semua, karena node-nya tetap full-size. Satu-satunya cara menembus: **lubang
tidak boleh punya node**. Makanya region gelap dipecah jadi node anak, lubang dibiarkan kosong → hit-test "jatuh" ke
elemen aplikasi di bawahnya.

### Blok E: Observasi tap tanpa membajak — `tutorialAnchor(onAction)`

```kotlin
// TutorialAnchor.kt
.pointerInput(id, report) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.any { it.changedToDown() }) report(id)   // report = notifyAction (via registry)
        }
    }
}
```

**Mengapa `PointerEventPass.Initial` + tidak consume?**
- Compose mengirim event pointer lewat beberapa "pass". `Initial` datang **duluan**, sebelum handler elemen (`Main`).
- Kita **mengamati** di `Initial` tapi **tidak `consume()`** — jadi saat pass `Main` tiba, `onClick` asli elemen tetap
  berjalan normal. Efek samping (`notifyAction`) + handler asli keduanya jalan. Inilah "observasi tanpa membajak".
- `report` default meneruskan ke `TutorialAnchorRegistry.notifyAction`; registry punya `isObserving` (snapshot state)
  supaya observer pointer **hanya terpasang selama langkah `UserClick` aktif** — di luar tutorial elemen bebas, tanpa
  overhead pointer observer permanen di ~30 call site.

### Blok F: Wiring trigger di `TutorialLayer`

```kotlin
// TutorialLayer.kt
LaunchedEffect(active) {
    when (advance) {
        is AdvanceTrigger.StateAppears -> {
            state.anchors.boundsOf(advance.anchorId)
                ?: withTimeoutOrNull(ANCHOR_WAIT_MS) { snapshotFlow { boundsOf(advance.anchorId) }.first { it != null } }
            if (state.anchors.boundsOf(advance.anchorId) != null) state.controller.next()
        }
        is AdvanceTrigger.UserClick -> {
            state.anchors.beginObserving(state.controller::notifyAction)
            try { snapshotFlow { state.controller.run.value?.stepIndex }.first { it != active.stepIndex } }
            finally { state.anchors.endObserving() }
        }
        AdvanceTrigger.Manual -> Unit
    }
}
```

**Mengapa begini?**
- **`StateAppears`** memakai pola `snapshotFlow { boundsOf(id) }.first { it != null }` — sama dengan cara lama
  menunggu anchor (FR-4). Maju begitu bounds ada (elemen/state muncul).
- **`UserClick`** menyalakan listener, lalu menunggu sampai `stepIndex` berubah (controller pindah/dismiss), lalu
  **pasti** mematikan listener di `finally` — jangan sampai observer bocor setelah tutorial selesai.
- **`Manual`** tidak melakukan apa-apa (perilaku lama).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pilihan | Alternatif | Mengapa ini? | Risiko alternatif |
|---|---|---|---|
| **Pecah ≤4 region (node per region)** | Scrim penuh + hit-test manual / satu `Canvas` berlubang | Hit-test Compose per-node; node tak bisa "menolak" area. Pecah region = cara paling lugas & lintas target | Satu node penuh = menelan semua ketukan (bug lama); hit-test manual rapuh & spesifik-platform |
| **Observasi tap di `Initial` pass** | Tambah `onClick` ke ~30 tombol | Menyentuh 1 modifier (`tutorialAnchor`) vs 30 layar — melanggar Aturan Tiga Kali & rapuh saat refactor | Tiap tombol harus diingat & diubah; anchor hilang tak tertangkap test |
| **`StateAppears` sebagai trigger** | Hanya `UserClick` | Tidak bergantung pada klik persis; tahan perubahan UI; bekerja untuk elemen tanpa handler klik | Hanya `UserClick` → langkah tanpa tombol jadi dead-end |
| **Opt-in per langkah (`Manual` default)** | Ubah semua tutorial sekaligus | Kompatibilitas mundur; tiap langkah memutuskan "baca" vs "beraksi" | Mengubah semua → regresi besar & migrasi konten wajib |
| **Geometri = fungsi murni** | Hitung region di dalam Composable | Bisa diuji tanpa Compose/browser; bug hit-test tak tertangkap test → geometri harus bisa diuji murni | Logika di Composable sulit diuji, bug geometri lolos |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

1. **Jebakan: `consume()` event di `Initial`.**
   - *Kenapa bahaya*: `onClick` asli elemen mati — tombol "Tambah" jadi tidak bisa diklik justru saat tutorial menyuruhnya.
   - *Solusi*: **jangan pernah consume** di observer tutorial; cuma baca sinyalnya.
2. **Jebakan: menggambar scrim tembus sebagai satu `Path` berlubang + `pointerInput`.**
   - *Kenapa bahaya*: node full-size tetap menelan semua ketukan — tembus tidak pernah terjadi (terlihat benar, tapi hit-test salah).
   - *Solusi*: lubang **tanpa node**; region gelap sebagai node anak terpisah.
3. **Jebakan: observer pointer permanen di semua anchor.**
   - *Kenapa bahaya*: overhead pointer interceptor di setiap elemen yang di-anchor, sepanjang waktu, bahkan saat tidak ada tutorial.
   - *Solusi*: `registry.isObserving` (snapshot state) — observer hanya terpasang selama langkah `UserClick` aktif.
4. **Jebakan: langkah "beraksi" tanpa jalan keluar.**
   - *Kenapa bahaya*: kalau elemen tak punya handler klik, `UserClick` tak pernah datang → pengguna terjebak.
   - *Solusi*: tombol **Lanjut** tidak pernah dihapus (fallback Manual selalu ada); degradasi FR-5 diuji eksplisit.
5. **Jebakan: menambah field `TutorialStep` tanpa default kompatibel.**
   - *Kenapa bahaya*: memaksa migrasi seluruh konten tutorial.
   - *Solusi*: default `interactive=false`/`advanceOn=Manual` — konten lama berperilaku identik.

---

## 🧪 6. Bagaimana Membuktikan Kodingan Bekerja?

Strategi: **logika diuji murni, hit-test diuji visual** (bug hit-test tidak tertangkap test mana pun — perlu mata).

- **Domain (murni, `core/commonTest`)** — `TutorialStepContractTest`: default mempertahankan perilaku lama;
  `UserClick`/`StateAppears` membawa anchor yang benar.
- **State machine (`app/shared/commonTest`)** — `TutorialControllerTest`: `notifyAction` hanya memajukan anchor yang
  cocok; `Manual` tak terpengaruh; degradasi FR-5 (anchor null ⇒ Manual+CENTER; langkah dialog ⇒ Manual).
- **Geometri murni (`app/shared/commonTest`)** — `SpotlightRegionsTest`: target di tengah (4 region), menyentuh tepi
  (region ≤0 dibuang), lebih besar dari kontainer, `target==null` (0 region), invariant area.
- **Visual (wajib, Playwright)** — login owner `wemade-demo` → Help → `crm_new_lead` (sementara ditandai interactive):
  klik **Tambah Lead** membuka form **dan** maju ke 3/4; klik **Deal** (area gelap) diblokir; **Esc** menutup.

```bash
./gradlew :core:jvmTest :app:shared:jvmTest   # semua test di atas hijau
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu

- [ ] **Tantangan 1**: tambah langkah `StateAppears` nyata di sebuah tutorial (mis. langkah yang menunggu panel muncul
      setelah klik), lalu verifikasi lewat Playwright bahwa langkah maju begitu panel tampil — **tanpa** klik Lanjut.
- [ ] **Tantangan 2**: buat skenario degradasi — sembunyikan elemen target saat langkah `UserClick` aktif — dan buktikan
      tombol Lanjut tetap bisa menyelesaikan langkah (no dead-end).
- [ ] **Tantangan 3**: `spotlightRegions` saat `padding` besar hingga `hole` keluar kontainer di dua sisi sekaligus;
      tambahkan test invariant bahwa total area region + area lubang yang **terpotong kontainer** tetap = area kontainer.
