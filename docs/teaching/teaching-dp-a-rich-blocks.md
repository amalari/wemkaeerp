# 🎓 Modul Pembelajaran: Blok Prototype Interaktif Kaya — Tabel Inline, Kanban Bertipe, dan Port Data Asinkron (Jalur A)

> **Level Target**: Junior to Mid Multiplatform Engineer  
> **Topik Utama**: Compose Multiplatform, State Management MVI, Asynchronous Data Port, Claymorphism Neo-Brutalism, Cross-Worktree Parallel Development  
> **Prasyarat**: Dasar Kotlin Multiplatform (KMP), Jetpack/Compose Multiplatform State (`mutableStateOf`, `derivedStateOf`), dan Domain-Driven Design (DDD)  
> **Referensi Task**: [PLAN-dp-A-ui.md](file:///Volumes/amalari/Projects/wemkaeerp/docs/plannings/parallel2/PLAN-dp-A-ui.md) & [PLAN-prototype-data-port-rich-blocks.md](file:///Volumes/amalari/Projects/wemkaeerp/docs/plannings/PLAN-prototype-data-port-rich-blocks.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Saat kita membangun prototype cepat atau builder aplikasi, godaan terbesarnya adalah: **menulis UI yang terikat mati dengan koleksi memori lokal yang dimutasi secara sinkron**.
Di awal, aplikasi tampak cepat dan mempesona. Namun, begitu masuk fase MVP atau produksi:
1. Penyimpanan riil di server berlangsung **asinkron** dan **bisa gagal** (koneksi putus, validasi gagal, permission 403).
2. ID data baru dibuat di **server database** (bukan di browser/HP).
3. Kolom tabel dan kartu kanban menjadi kaku jika tipe data hanya dianggap sebagai teks biasa tanpa pemformatan angka, tanggal, lencana, atau tanda (flag).
4. Ketika UI ditulis tanpa port abstraksi, kita terpaksa merombak ulang 80% kode saat beralih dari mode demo ke API nyata.

### Analogi Sederhana
Bayangkan sebuah **stopkontak universal**.
- Lampu belajar (komponen UI tabel dan kanban kita) tidak perlu tahu apakah listrik dipasok dari **baterai portabel** (mode memori lokal `InMemoryBlockDataPort`) atau dari **jaringan PLN kota** (mode server HTTP `ApiBlockDataPort`).
- Selama colokan dan voltasenya seragam (kontrak interface [BlockDataPort](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/BlockDataPort.kt)), lampu dapat menyala dengan stabil tanpa harus dibongkar ulang mesin dalamnya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu diberikan canvas kosong untuk membangun sistem blok dinamis seperti ini, jangan langsung membuat tombol atau form Compose! Ikuti tahapan terstruktur berikut:

```
[Langkah 0: Spike & Analisis Layout]
               │
               ▼
[Langkah 1: Kontroller Port Asinkron (BlockDataController)]
               │
               ▼
[Langkah 2: Migrasi State Blok & Port Factory]
               │
               ▼
[Langkah 3: Komponen FieldInput Bersama (Aturan Tiga Kali)]
               │
               ▼
[Langkah 4: Tabel Inline Create & Cell Editor]
               │
               ▼
[Langkah 5: Kanban Kartu Kaya, Metadata Kolom, & Dialog Detail]
               │
               ▼
[Langkah 6: Integrasi Chat Edit & Sinkronisasi Lintas Worktree]
               │
               ▼
[Langkah 7: Verifikasi Paritas Multiplatform (JVM, Desktop, WasmJS)]
```

1. **Langkah 0: Spike & Analisis Batas Layout**
   - Periksa lebar kolom tetap (~118dp) vs overflow horizontal. Tentukan apakah editor sel inline layak atau butuh popover dialog.
   - Pahami jebakan kanban runtuh di kontainer dengan tinggi terbatas vs tak terbatas.
2. **Langkah 1: Kontroller Port Asinkron (`BlockDataController`)**
   - Bangun pengontrol Compose yang memegang `BlockDataPort` dan mematuhi kebijakan optimistik vs pesimistik:
     - *Pindah kartu/status*: **Optimistik** (ubah tampilan seketika, jika port gagal, lakukan *rollback* otomatis).
     - *Tambah/Ubah/Hapus*: **Pesimistik** (tampilkan indikator simpan, tunggu konfirmasi server sebelum menutup form).
3. **Langkah 2: Migrasi State Blok & Port Factory**
   - Hubungkan `InteractiveKanbanState`, `InteractiveTableState`, `InteractiveFormState`, dan `InteractiveChecklistState` ke `BlockDataController`.
   - Buat `BlockDataPortFactory` agar `PrototypeSession` dapat memilih port sesuai `InteractiveScreen.binding` (`Memory` vs `Api`).
4. **Langkah 3: Komponen `FieldInput` Bersama (Aturan Tiga Kali)**
   - Form pembuatan inline tabel, form detail kartu kanban, dan form blok biasa memiliki kebutuhan input identik. Ekstrak satu komponen [FieldInput](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/fields/FieldInput.kt) yang memetakan `FieldType` (`TEXT`, `NUMBER`, `DATE`, `ENUM`, `BOOL`) ke input visual Claymorphism.
5. **Langkah 4: Tabel dengan Form Inline & Editor Sel**
   - Buat baris inline create di atas header tabel jika `TableConfig.inlineCreate = true`.
   - Dukung pengeditan sel inline pada kolom yang terdaftar di `TableConfig.editableFields`.
6. **Langkah 5: Kanban Kartu Kaya & Dialog Detail**
   - Render elemen kartu sesuai [CardStyle](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/BlockDataPort.kt) (`TITLE`, `TEXT`, `BADGE`, `DATE`, `NUMBER`, `FLAG`).
   - Warnai header kolom menggunakan `ColumnMeta.tintHex` (sumber data domain) dan tampilkan indikator batas WIP.
   - Buka dialog form detail saat kartu diketuk.
7. **Langkah 6: Integrasi Chat Edit & Sinkronisasi Lintas Worktree**
   - Pastikan operasi chat edit baru ([SpecOp.ShowFieldOnCard](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/SpecOp.kt), [SpecOp.SetFieldRequired](file:///Volumes/amalari/Projects/wemkaeerp/core/src/commonMain/kotlin/com/eventverse/app/domain/prototype/SpecOp.kt)) dari Agent B langsung terefleksi di state tanpa merombak ulang controller.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Pengontrol Port Asinkron & Kebijakan Optimistik (`BlockDataController.kt`)

```kotlin
class BlockDataController(
    val port: BlockDataPort,
    initialSpec: PrototypeSpec,
    val entityId: String,
    initialRows: List<PrototypeRow> = emptyList(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) {
    var rows by mutableStateOf(initialRows)
        private set

    var phase by mutableStateOf<Phase>(Phase.Idle)
        private set

    private val mutex = Mutex()

    fun moveOrChangeStatus(
        rowId: String,
        statusField: String,
        newStatus: String,
        onValidationError: ((String) -> Unit)? = null
    ) {
        val originalRows = rows
        val targetRow = rows.firstOrNull { it.id == rowId } ?: return
        val updatedRow = targetRow.copy(values = targetRow.values + (statusField to newStatus))

        // 1. Optimistik: Update UI langsung seketika
        rows = rows.map { if (it.id == rowId) updatedRow else it }

        // 2. Kirim asinkron ke port dengan rollback jika gagal
        scope.launch {
            mutex.withLock {
                val res = port.update(rowId, mapOf(statusField to newStatus))
                if (res.isFailure) {
                    // Rollback ke state awal
                    rows = originalRows
                    val errorMsg = (res.exceptionOrNull() as? PortException)?.message 
                        ?: "Gagal memindahkan kartu."
                    phase = Phase.Error(errorMsg)
                    onValidationError?.invoke(errorMsg)
                }
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
1. **Optimistik untuk Drag & Drop**: Pengguna mengharapkan kartu berpindah instan di bawah jemari/kursor mereka tanpa jeda loading. Jika jaringan bermasalah atau status dilarang oleh server, UI membatalkan perpindahan secara visual (*rollback*) dan menyajikan pesan alasan yang jelas.
2. **Mutex Serialization**: Operasi port diserialkan dengan `Mutex` agar tidak terjadi *race condition* jika pengguna memindahkan beberapa kartu secara berurutan dengan cepat.
3. **Dispatcher Safe**: `CoroutineScope` menggunakan `SupervisorJob() + Dispatchers.Default` sehingga aman dijalankan di pengujian unit JVM tanpa error `MissingMainCoroutineDispatcher`.

---

### Blok B: Komponen Input Bersama Berbasis Tipe (`FieldInput.kt`)

```kotlin
@Composable
fun FieldInput(
    type: FieldType,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    options: List<String> = emptyList(),
    errorMessage: String? = null
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        // Label & Penanda Wajib
        Text(
            text = if (required) "$label *" else label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
        )
        // Pemetaan Input Berdasarkan FieldType
        when (type) {
            FieldType.TEXT -> ClayTextField(value = value, onValueChange = onValueChange)
            FieldType.NUMBER -> ClayTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() || ch == '-' }) }
            )
            FieldType.DATE -> ClayTextField(
                value = value,
                onValueChange = onValueChange,
                placeholder = "TTTT-BB-HH"
            )
            FieldType.ENUM -> ClayChoiceChipGroup(options = options, selected = value, onSelect = onValueChange)
            FieldType.BOOL -> ClayCheckbox(
                checked = value.equals("ya", ignoreCase = true) || value.equals("true", ignoreCase = true),
                onCheckedChange = { onValueChange(if (it) "ya" else "tidak") },
                label = label
            )
        }
        if (!errorMessage.isNullOrBlank()) {
            Text(text = errorMessage, color = WeMadeColors.Defect, style = MaterialTheme.typography.bodySmall)
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
1. **Aturan Tiga Kali (Rule of Three)**: Form pembuatan inline tabel, form detail kartu kanban, dan form blok biasa memiliki pola yang persis sama. Memusatkan logika pemetaan `FieldType` menjamin konsistensi visual dan validasi di seluruh aplikasi.
2. **Zero Color Literal**: Penanda galat menggunakan token semantik `WeMadeColors.Defect`, bukan literal warna merah sembarangan (`Color.Red` atau `Color(0xFFE11D48)`).

---

### Blok C: Rendering Kartu Bertipe pada Kanban (`KanbanCardContent.kt`)

```kotlin
@Composable
fun KanbanCardContent(
    cardElements: List<CardElement>,
    row: PrototypeRow,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(ClaySpacing.Xs)) {
        cardElements.forEach { element ->
            val rawValue = row[element.field].orEmpty()
            if (rawValue.isNotBlank()) {
                when (element.style) {
                    CardStyle.TITLE -> Text(text = rawValue, fontWeight = FontWeight.Black)
                    CardStyle.TEXT -> Text(text = rawValue, color = WeMadeColors.TextMuted)
                    CardStyle.BADGE -> ClayBadge(text = rawValue, containerColor = WeMadeColors.SurfaceMuted)
                    CardStyle.DATE -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(imageVector = Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(12.dp))
                        Text(text = rawValue, style = MaterialTheme.typography.bodySmall)
                    }
                    CardStyle.NUMBER -> Text(
                        text = rawValue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.End
                    )
                    CardStyle.FLAG -> if (rawValue.equals("ya", ignoreCase = true)) {
                        ClayTag(label = element.field, containerColor = WeMadeColors.Defect)
                    }
                }
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- Papan kanban bukan sekadar tempelan post-it berisi judul teks. Setiap informasi memiliki bobot visual tersendiri: nomor/kode pesanan tebal (`TITLE`), status berbentuk lencana (`BADGE`), batas tenggat waktu bertanda ikon kalender (`DATE`), dan status mendesak berwarna sinyal bahaya (`FLAG`).

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Port Data Asinkron (`BlockDataPort`)** | Mutasi Store Memori Sinkron | Mendukung transisi mulus dari memori lokal (demo) ke HTTP API sungguhan (MVP) tanpa ubah UI. | Perlu *total rewrite* logika UI saat menghubungkan ke backend riil. |
| **Optimistik + Rollback Otomatis** | Pesimistik Tunggu Jaringan pada Drag-and-Drop | Interaksi kanban terasa sangat cepat, halus, dan responsif. | Tampilan freeze/laggy jika menunggu round-trip HTTP setiap geser kartu. |
| **Tiga Lapis Token Desain (Clay)** | Ad-hoc CSS / Material3 mentah | Identitas visual seragam (outline 3dp, hard shadow 0-blur, font Fredoka + Nunito). | UI inkonsisten, kontras sinyal produksi rusak, bocor warna ungu default M3. |
| **Integrasi Cabang Antar-Worktree** | Menunggu Merge Formal ke `main` | Agen A dan Agen B dapat berprogres paralel tanpa mengalami *blocking* berhari-hari. | Jalur A macet menunggu kontrak B atau sebaliknya. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Dispatcher Coroutine di Unit Test JVM**
   - *Kenapa bahaya*: Menggunakan `Dispatchers.Main` di class non-Android/non-Compose murni menyebabkan `IllegalStateException: Module with the Main dispatcher had failed to initialize`.
   - *Solusi kita*: Sediakan default coroutine scope yang aman (`SupervisorJob() + Dispatchers.Default`) pada state holder / controller.
2. **Jebakan 2: Asersi Pengujian Asinkron yang Terlalu Cepat**
   - *Kenapa bahaya*: Memanggil fungsi suspend/launch lalu langsung meng-assert koleksi di baris berikutnya dapat menghasilkan *false negative* karena operasi port baru dieksekusi di tick berikutnya.
   - *Solusi kita*: Pisahkan mutasi lokal sinkron (`deleteRowLocally`, `insertRowLocally`) sebelum memicu operasi port di background, atau gunakan `runTest` dengan `advanceUntilIdle()`.
3. **Jebakan 3: Cross-Module Smart-Cast pada Kotlin Data Class**
   - *Kenapa bahaya*: Membaca properti mutable atau antar modul (`meta.tintHex`) sering kali ditolak oleh compiler: `Smart cast to 'Long' is impossible, because 'meta.tintHex' is a public property in another module`.
   - *Solusi kita*: Salin nilai ke variabel lokal terlebih dahulu (`val tintHex = meta?.tintHex`) sebelum melakukan pengecekan `if (tintHex != null)`.

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Pengujian harus mencakup verifikasi logika murni, interaksi state, hingga kompilasi multiplatform:

1. **Pengujian Controller dengan Port Palsu (`BlockDataControllerTest.kt`)**:
   - Memastikan saat port mengembalikan `PortError.Unavailable`, kartu yang digeser otomatis kembali ke kolom asalnya (*rollback*).
   - Memastikan baris input inline tetap terbuka ketika server menolak pembuatan data (`PortError.Validation`).
2. **Pengujian Chat Edit & Binding (`PrototypeInteractiveUiTest.kt`)**:
   - Menerapkan `SpecOp.ShowFieldOnCard` dan memverifikasi perubahan gaya kartu di papan kanban.
   - Menguji bahwa JSON draf dengan `DataBinding.Api` ter-decode dengan benar ke kelas sealed interface.
3. **Kompilasi Multiplatform Penuh**:
   ```bash
   ./gradlew :core:jvmTest :app:shared:jvmTest
   ./gradlew :app:desktopApp:compileKotlin
   ./gradlew :app:webApp:wasmJsMainClasses
   ```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: Buka [InlineRowEditor.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/discovery/table/InlineRowEditor.kt) dan tambahkan validasi regex lokal untuk field bertipe `DATE` (format harus tepat `TTTT-BB-HH`).
- [ ] **Tantangan 2**: Cobalah ubah `wipLimit` pada kolom "Dikerjakan" di papan sampling dan amati bagaimana outline kartu kanban berubah warna menjadi `WeMadeColors.Defect` saat jumlah kartu melampaui batas tersebut.
- [ ] **Tantangan 3**: Tambahkan satu test case di [InteractiveTableTest.kt](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/jvmTest/kotlin/com/eventverse/app/presentation/discovery/table/InteractiveTableTest.kt) yang menguji pengeditan sel inline pada kolom status dan pastikan kolom tersebut tidak dapat diedit lewat input teks bebas.
