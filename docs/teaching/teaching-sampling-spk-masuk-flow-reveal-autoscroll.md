# 🎓 Modul Pembelajaran: Progressive Disclosure & Auto-Scroll Alur Proses SPK Masuk di Modul Sampling

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Progressive Disclosure UX, Compose ScrollState Animation, State-Driven UI Gating  
> **Prasyarat**: Dasar Compose State (`mutableStateOf`, `LaunchedEffect`), Coroutine Animations (`animateScrollTo`), Kanban Pipeline Lifecycle  
> **Referensi Task**: SPK Masuk Awalnya Hanya Tampilkan Detail, Alur Proses Muncul & Auto-Scroll Saat "Tentukan Alur Desain" Diklik

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada modul Order Sampling (`/sampling-order`), saat SPK baru saja masuk dari kesepakatan sales (**SPK Masuk / NEW_INTAKE**):
1. **Information Overload**:
   - Jika dialog detail SPK langsung menampilkan seluruh alur proses perakitan rajut (dari CAM, Rajut, Linking, Cuci, Setrika hingga palet opsional bordir/sablon), operator atau PIC sampling yang baru pertama kali memeriksa data teknis sampel (mockup foto, matriks ukuran POM, dan catatan klien) akan merasa kewalahan (*cognitive overload*).
2. **Kesesuaian Tahapan Logika**:
   - Pada kolom "SPK Masuk", operator pertama-tama harus mereview spesifikasi desain. Mereka belum menentukan alur khusus desain tersebut.
   - Karena itu, tombol aksi utama di footer seharusnya adalah **"Tentukan Alur Desain ->"**, bukan langsung **"Alur Siap -> Mulai CAM"**.
3. **Seamless Transition**:
   - Ketika operator memutuskan untuk mulai merancang alur dengan mengklik "Tentukan Alur Desain", section alur proses harus muncul secara dinamis dan layar harus langsung menggulir (*auto-scroll*) ke section tersebut secara otomatis tanpa memaksa pengguna mencari-cari di mana alurnya berada.

### Analogi Sederhana
Bayangkan Anda menerima formulir registrasi paspor: di tahap awal petugas hanya ingin memverifikasi KTP dan dokumen identitas Anda. Mereka tidak langsung menyodorkan lembar pemilihan tanggal sidik jari dan foto biometrik sebelum identitas awal Anda dibuka dan dibaca. Begitu dokumen identitas selesai diverifikasi dan tombol *"Lanjut ke Biometrik"* ditekan, barulah bagian biometrik terbuka dan formulir otomatis menggeser fokus Anda ke sana.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan progressive disclosure dengan auto-scroll:

1. **Langkah 1: State Gating pada Detail Dialog**
   - Tambahkan state boolean `isFlowSectionVisible` yang secara default `false` jika tahapnya masih `NEW_INTAKE`, dan `true` jika sudah di tahap selanjutnya (`FLOW_REVIEW`, `CAM_PROGRAMMING`, dst.).
2. **Langkah 2: Dynamic Footer Action Button**
   - Bila `!isFlowSectionVisible`: tombol footer utama adalah `Tentukan Alur Desain ->`.
   - Bila `isFlowSectionVisible`: tombol footer berubah menjadi `Alur Siap -> Mulai CAM`.
3. **Langkah 3: Compose Coroutine Animation Auto-Scroll**
   - Buat `scrollState = rememberScrollState()`.
   - Ketika `isFlowSectionVisible` berubah menjadi `true`, picu `LaunchedEffect(isFlowSectionVisible)` dengan sedikit delay (120ms) agar Compose sempat mengukur dimensi (*measure & layout*) komponen baru, lalu jalankan `scrollState.animateScrollTo(scrollState.maxValue)`.
4. **Langkah 4: Sinkronisasi dari Kartu Kanban Luar**
   - Berikan kemampuan kartu Kanban di kolom "SPK Masuk" untuk membuka dialog langsung ke section alur proses (`focusFlow = true`) jika tombol `Tentukan Alur Desain ->` pada kartu diklik, atau hanya membuka detail saja jika badan kartu yang diklik.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: State Gating & Auto-Scroll Trigger
Di [`SamplingSpkDetailDialog.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/sampling/components/SamplingSpkDetailDialog.kt):
```kotlin
// Pada tahap SPK Masuk (NEW_INTAKE), section alur proses awalnya belum muncul
var isFlowSectionVisible by remember(order.id, initialShowFlowSection) {
    mutableStateOf(initialShowFlowSection || order.pipelineStage != SamplingPipelineStage.NEW_INTAKE)
}

val scrollState = rememberScrollState()
val coroutineScope = rememberCoroutineScope()

LaunchedEffect(isFlowSectionVisible) {
    if (isFlowSectionVisible && (order.pipelineStage == SamplingPipelineStage.NEW_INTAKE || initialShowFlowSection)) {
        delay(120) // Beri waktu layout pass mengukur ProcessFlowAdjusterPanel
        scrollState.animateScrollTo(scrollState.maxValue)
    }
}
```

**Mengapa blok ini ditulis begini?**
- `remember(order.id, initialShowFlowSection)` menjamin bahwa ketika berganti SPK, state reset ke konfigurasi bawaan SPK tersebut.
- `delay(120)` sangat krusial: jika kamu memanggil `animateScrollTo` tepat saat `isFlowSectionVisible` disetel ke `true`, komponen anak belum selesai di-render ke tree, sehingga `scrollState.maxValue` masih bernilai lama dan auto-scroll tidak akan sampai ke dasar.

---

### Blok B: Render Kondisional Alur Proses
```kotlin
Column(
    modifier = Modifier
        .weight(1f, fill = false)
        .verticalScroll(scrollState),
    verticalArrangement = Arrangement.spacedBy(ClaySpacing.Md)
) {
    ClientSamplingReferenceCard(order)

    // Alur Proses Khusus SPK / Desain Ini (muncul saat tentukan alur desain)
    if (isFlowSectionVisible && processFlowViewModel != null) {
        LaunchedEffect(order.id) {
            processFlowViewModel.onEvent(
                ProcessFlowUiEvent.SelectScope(
                    ProcessFlowScope.Design(
                        orderId = order.id.value,
                        styleName = order.styleName,
                        spkNumber = order.spkNumber.value
                    )
                )
            )
        }
        ProcessFlowAdjusterPanel(
            viewModel = processFlowViewModel,
            hideScopeSelector = true
        )
    }
}
```

---

### Blok C: Transformasi Tombol Aksi di Footer
```kotlin
if (isGateStage) {
    if (!isFlowSectionVisible) {
        ClayButton(
            text = "Tentukan Alur Desain ->",
            style = ClayButtonStyle.Primary,
            modifier = Modifier.weight(2f),
            enabled = !isSubmitting,
            onClick = {
                isFlowSectionVisible = true
                coroutineScope.launch {
                    delay(120)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }
        )
    } else {
        ClayButton(
            text = "Alur Siap -> Mulai CAM",
            style = ClayButtonStyle.Primary,
            modifier = Modifier.weight(2f),
            enabled = !isSubmitting,
            onClick = onStartCam
        )
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa Kita Memilih Ini? | Risiko Alternatif |
|---|---|---|---|
| **Progressive Disclosure in Dialog** | Dialog bertumpuk (dialog di atas dialog) | Pengguna tetap dalam 1 konteks lembar kerja tanpa kehilangan referensi foto/POM | Dialog bertumpuk sering error di Web/Desktop (fokus keyboard & scrim tertutup) |
| **`animateScrollTo(maxValue)`** | `scrollTo` instan tanpa animasi | Memberikan orientasi spasial visual bagi pengguna bahwa konten baru ditambahkan di bawah | Tiba-tiba meloncat membuat pengguna kaget atau tidak sadar ada alur baru |
| **`delay(120)` sebelum scroll** | Scroll langsung synchronous | Menunggu Recomposition & Layout measurement pass selesai | Scroll berhenti di tengah jalan sebelum panel selesai menggambar dirinya |

---

## 🧪 5. Cara Membuktikan Kodingan Bekerja

1. **Uji Kompilasi**:
   ```bash
   ./gradlew :app:shared:compileKotlinJvm
   ./gradlew :app:shared:compileKotlinWasmJs
   ```
2. **Uji Pengalaman Pengguna (User Flow)**:
   - Di `http://localhost:3000/sampling-order`, pada kolom **1. SPK Masuk**, klik badan kartu (misal `SPK-SMP-0050`).
   - Dialog terbuka: **Hanya ada Tampak Depan/Belakang, POM table, dan Catatan Klien**. Section Alur Proses tidak tampil. Tombol footer adalah **"Tentukan Alur Desain ->"**.
   - Klik **"Tentukan Alur Desain ->"**: Section Alur Proses muncul dan dialog dengan mulus bergulir (*smooth scroll*) ke bawah menuju section tersebut. Tombol footer berubah menjadi **"Alur Siap -> Mulai CAM"**.
   - Klik langsung tombol **"Tentukan Alur Desain ->"** dari kartu di luar: Dialog langsung terbuka dengan section alur proses aktif dan ter-scroll ke bawah.
