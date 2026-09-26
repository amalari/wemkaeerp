# 🎓 Modul Pembelajaran: Mengatasi Concurrency Conflict (HTTP 409) & Premature Effect Trigger pada Compose UI Inspector

> **Level Target**: Junior to Mid Multiplatform / Frontend Engineer  
> **Topik Utama**: Jetpack / Compose Multiplatform, State Lifecycle (`LaunchedEffect` vs `remember`), Optimistic Concurrency Control (OCC), Debouncing, Error Boundary Design  
> **Prasyarat**: Dasar Compose state (`mutableStateOf`, `LaunchedEffect`), konsep REST API PATCH & OCC HTTP 409  
> **Referensi Task**: Fix Runtime Error saat klik kartu Lead di `/crm-sales`: `Gagal menyimpan lead (HTTP 409)`

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Pada halaman CRM Sales ([`/crm-sales`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/CrmWorkspaceScreen.kt)), ketika pengguna mengklik salah satu kartu lead di Kanban Board untuk melihat detailnya, halaman tiba-tiba crash dan memunculkan error berwarna merah:
```text
Gagal memuat: Gagal menyimpan lead (HTTP 409) {"id":"lead-1789374068917-838", ...}
```
Padahal pengguna **hanya mengklik untuk melihat**, sama sekali belum mengedit atau menekan tombol simpan!

### Analogi Sederhana: Form Kasir yang Otomatis Submit saat Formulir Baru Dibuka
Bayangkan kamu pergi ke bank dan teller menyerahkan lembar formulir yang sudah terisi data kamu untuk kamu tinjau. 
Namun di meja itu, setiap kolom memiliki sensor otomatis yang langsung mengirimkan SMS konfirmasi ke kantor pusat saat formulir diletakkan di atas meja. Karena ada 6 kolom (Nama, No HP, Email, dsb.), 6 SMS dikirim secara bersamaan ke server pusat.
Sistem perbankan yang memiliki proteksi keamanan (*Optimistic Concurrency*) langsung curiga: *"Kenapa ada 6 update bersamaan dengan timestamp yang sama?!"* — server menerima update pertama, lalu menolak 5 update sisanya dengan status **HTTP 409 Conflict**, lalu membunyikan alarm pembatalan.

---

## 🧭 2. "Start dari Mana?" — Alur Penelusuran Akar Masalah (Order of Operations)

1. **Langkah 1: Periksa Status Code HTTP (409 Conflict)**
   - Status `409 Conflict` adalah respons standar server untuk *Optimistic Concurrency Control (OCC)*.
   - Di backend ([`UpdateLeadUseCase.kt`](file:///Volumes/amalari/Projects/wemade/core/src/commonMain/kotlin/com/eventverse/app/domain/crm/usecases/UpdateLeadUseCase.kt)), server memeriksa:
     ```kotlin
     if (expectedUpdatedAt != null && existing.updatedAt != expectedUpdatedAt) {
         throw LeadConflictException(existing)
     }
     ```
   - Ini menandakan ada request PATCH yang dikirim dengan `expectedUpdatedAt` yang sudah kedaluwarsa.
2. **Langkah 2: Telusuri Siapa yang Mengirim PATCH saat Lead Diklik**
   - Saat lead diklik, UI memanggil `onSelectLead(lead.id)`, yang membuka [`LeadInspectorPane`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadInspectorPane.kt).
   - Di dalam pane tersebut, terdapat komponen editor input teks [`LeadCustomField`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadCustomField.kt) untuk setiap properti lead.
3. **Langkah 3: Temukan Kebocoran Lifecycle pada `LaunchedEffect`**
   - Periksa implementasi `TextEditor`:
     ```kotlin
     // ❌ KODE LAMA BERMASALAH
     var text by remember(cell) { mutableStateOf(cell?.let { ... } ?: "") }
     androidx.compose.runtime.LaunchedEffect(text) {
         onCommit(text.takeIf { it.isNotBlank() }?.let(buildCell))
     }
     ```
   - Di Jetpack Compose, **`LaunchedEffect(key)` AKAN SELALU DIJALANKAN minimal 1 kali saat composable pertama kali memasuki composition tree (initial composition)!**
   - Karena ada ~6 field teks yang di-mount bersamaan saat inspector dibuka, ke-6 field tersebut langsung menjalankan `onCommit(...)` secara paralel seketika itu juga!
   - Request pertama sampai di server dan memperbarui `lead.updatedAt`.
   - Request ke-2 sampai ke-6 sampai dengan membawa `expectedUpdatedAt` lama -> Server menolak dengan `HTTP 409 Conflict`!
4. **Langkah 4: Perbaiki Mekanisme Commit & Debounce**
   - Simpan `initialText` awal.
   - Batalkan eksekusi jika `text == initialText`.
   - Pasang jeda waktu (*debounce*) misal 600ms saat user benar-benar mengetik.
5. **Langkah 5: Isolasi Error Boundary di Layar Utama**
   - Jangan hancurkan seluruh workspace CRM jika sebuah field gagal di-patch. Pisahkan perlakuan error `load` vs error aksi inline.
6. **Langkah 6: Ubah Side Drawer Menjadi Centered Modal Dialog**
   - Pada Kanban multi-kolom, membuka panel samping (*side drawer*) akan memeras (*squish*) 3 kolom Kanban menjadi sempit dan padat.
   - Angkat detail inspector menjadi `Dialog(onDismissRequest = { ... })` dengan `ClayCard` di tengah layar, sehingga tata letak 3 kolom Kanban tetap seimbang 100% lebar layar.

---

## 🧱 3. Bedah Blok per Blok Kode Solusi

### Blok A: Guard Nilai Awal & Debounce pada Composable Editor
File: [`LeadCustomField.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/components/LeadCustomField.kt)
```kotlin
@Composable
private fun TextEditor(
    cell: JsonValue.Obj?,
    editable: Boolean,
    onCommit: ((JsonValue.Obj?) -> Unit)?,
    placeholder: String? = null,
    buildCell: (String) -> JsonValue.Obj
) {
    // 1. Catat nilai awal dari data server
    val initialText = remember(cell) { cell?.let { it.entries["v"] }?.let(::rawText) ?: "" }
    var text by remember(cell) { mutableStateOf(initialText) }

    if (editable && onCommit != null) {
        ClayTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = placeholder,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        // 2. Hanya commit jika user BENAR-BENAR mengubah text (text != initialText)
        // 3. Pasang jeda debounce 600ms agar user selesai mengetik sebelum kirim PATCH
        androidx.compose.runtime.LaunchedEffect(text) {
            if (text == initialText) return@LaunchedEffect
            kotlinx.coroutines.delay(600)
            onCommit(text.takeIf { it.isNotBlank() }?.let(buildCell))
        }
    } else {
        Text(text = text.ifBlank { "—" }, fontSize = 13.sp, color = WeMadeColors.OnSurface)
    }
}
```
**Mengapa blok ini ditulis begini?**
- `if (text == initialText) return@LaunchedEffect`: Menjamin saat pane baru pertama kali terbuka, **nol (0)** request jaringan dikirim ke server.
- `kotlinx.coroutines.delay(600)`: Jika user mengetik huruf per huruf ("B", "u", "d", "i"), coroutine sebelumnya otomatis di-cancel oleh `LaunchedEffect(text)` dan di-restart, sehingga hanya 1 request yang dikirim saat user berhenti mengetik selama 600ms.

### Blok B: Rekonsiliasi State saat Terjadi Konflik (OCC 409)
File: [`CrmViewModel.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/CrmViewModel.kt)
```kotlin
private fun commitField(fieldId: String, value: JsonValue.Obj?) {
    val state = _uiState.value
    if (!state.canWrite) return
    val lead = state.selectedLead ?: return

    scope.launch {
        val patch = ...
        // Selalu ambil instance lead terbaru dari StateFlow saat coroutine dieksekusi
        val currentLead = _uiState.value.selectedLead ?: lead
        remoteDataSource.patchLead(tenantSlug, currentLead.id, patch.copy(expectedUpdatedAt = currentLead.updatedAt))
            .onSuccess { updated -> replaceLead(updated) }
            .onFailure { error ->
                val errorMsg = error.message ?: "Gagal menyimpan field"
                // Jika terjadi 409, server mengembalikan data lead terkini di body respons
                if (errorMsg.contains("HTTP 409")) {
                    val jsonStart = errorMsg.indexOf('{')
                    if (jsonStart != -1) {
                        runCatching {
                            val obj = JsonParser.parseObject(errorMsg.substring(jsonStart))
                            val serverLead = obj?.let { CrmLeadCodec.decodeLead(it) }
                            if (serverLead != null) replaceLead(serverLead)
                        }
                    }
                }
                _uiState.update { it.copy(error = error.message) }
            }
    }
}
```

### Blok C: Non-Destructive Error Banner di Layar Workspace
File: [`CrmWorkspaceScreen.kt`](file:///Volumes/amalari/Projects/wemade/app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/crm/CrmWorkspaceScreen.kt)
```kotlin
// Hanya tampilkan full-screen error jika data awal memang benar-benar kosong/gagal dimuat
if (state.error != null && state.leads.isEmpty()) {
    Column(modifier = modifier.fillMaxSize().padding(ClaySpacing.Xxl)) {
        Text(text = "Gagal memuat: ${state.error}", color = WeMadeColors.Error)
    }
    return
}

Column(modifier = modifier.fillMaxSize()) {
    // Jika leads sudah ada, tampilkan error sebagai alert banner yang bisa ditutup
    state.error?.let { err ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = ClaySpacing.Xxl, vertical = ClaySpacing.Sm)
                .clayFlat(
                    shape = ClayShapes.Card,
                    background = WeMadeColors.ErrorBg,
                    outline = WeMadeColors.Error,
                    borderWidth = ClayBorder.Hairline
                )
                .padding(horizontal = ClaySpacing.Lg, vertical = ClaySpacing.Sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = err, color = WeMadeColors.Error, fontSize = 12.sp, modifier = Modifier.weight(1f))
            ClayButton(
                text = "Tutup",
                onClick = { viewModel.onEvent(CrmUiEvent.DismissError) },
                style = ClayButtonStyle.Ghost,
                fontSize = 12.sp,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth()) {
        // Kanban board & Inspector tetap dapat digunakan dengan aman
    }
}
```

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif yang Ada | Mengapa Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **`text == initialText` Guard** | Langsung `onCommit` di `LaunchedEffect` | Menghindari pengiriman network call saat komponen baru di-mount | Setiap kali membuka detail, server dibombardir request palsu |
| **`delay(600)` Debounce** | Kirim PATCH per keystroke | Menghemat bandwidth dan mencegah race condition antar request | Server kewalahan & rentan saling tindih dengan error 409 |
| **Inline Dismissible Banner** | Fullscreen `if (state.error != null) return` | User experience tetap terjaga; kegagalan satu field tidak merusak seluruh papan Kanban | Pengguna terkunci keluar dari layar operasional saat ada error kecil |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Lupa bahwa `LaunchedEffect` selalu jalan sekali saat mount**
   - *Kenapa bahaya*: Pemula sering berasumsi `LaunchedEffect(text)` hanya akan jalan jika variabel `text` diubah oleh ketikan keyboard. Faktanya, inisialisasi awal nilai `text` sudah dihitung sebagai state baru bagi effect yang baru lahir.
   - *Solusi*: Selalu bandingkan dengan nilai asli (`initialValue`) sebelum melakukan aksi side-effect seperti API call.
2. **Jebakan 2: Memperlakukan Semua Error sebagai Full-Screen Blocker**
   - *Kenapa bahaya*: Di aplikasi enterprise, kegagalan parsial (misal field telepon salah format) tidak boleh menghilangkan seluruh daftar pesanan atau leads dari layar user.
   - *Solusi*: Bedakan *Initial Fetch Error* (blokir layar jika tidak ada data) dengan *Mutation/Action Error* (tampilkan banner/toast sementara data tetap terlihat).

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Bekerja?

1. **Verifikasi Browser Click Interaction**:
   - Buka `http://localhost:3000/crm-sales`.
   - Klik kartu "Lead Test Baru".
   - Periksa apakah panel **Lead Inspector** di sisi kanan terbuka dengan data lengkap tanpa pesan error merah sama sekali.
2. **Verifikasi Network Traffic**:
   - Buka DevTools Network Tab.
   - Klik kartu lead: Pastikan **tidak ada** request `PATCH /api/tenant/crm/leads/...` yang terkirim saat klik terjadi.
   - Edit salah satu field (misal nama): Pastikan request `PATCH` baru dikirim setelah jeda ~600ms dari ketikan terakhir.
