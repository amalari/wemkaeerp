# 🎓 Modul Pembelajaran: Desain Sistem & Interaktivitas UI Prototype (Agent A)

> **Level Target**: Junior to Mid Developer  
> **Topik Utama**: Compose Multiplatform, Neo-Brutalist Claymorphism, Generic Drag-and-Drop Coordinator, In-Memory State Pipeline, Multiplatform Accessibility  
> **Prasyarat**: Dasar Kotlin Multiplatform, Jetpack Compose / Compose Multiplatform basics, pemahaman MVI & DDD  
> **Referensi Task**: [`docs/plannings/parallel/PLAN-proto-A-ui.md`](file:///Volumes/amalari/Projects/wemkaeerp/docs/plannings/parallel/PLAN-proto-A-ui.md)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

Saat membangun *prototype previewer* atau aplikasi modular berbasis Compose Multiplatform, tantangan terbesar ada pada dua kubu:
1. **Kekakuan Komponen (Domain Coupling)**: Sering kali papan kanban atau tabel dibuat terikat sangat erat dengan satu domain (misal kartu Sampling atau pesanan Konveksi). Akibatnya, saat platform ingin dipakai lintas industri (misalnya untuk rental mobil, klinik, atau penerbitan), kanban tersebut harus di-copy-paste ulang dan dimodifikasi di mana-mana.
2. **Kekacauan Visual & Drag-and-Drop Glitches**: Komponen drag-and-drop biasa yang dihitung lokal di dalam kartu sering terpotong (`clipped`) oleh batas kolom, mengalami flickering koordinat, atau tidak bisa dimainkan di perangkat sentuh/mobile tanpa mouse.

### Analogi Sederhana
Bayangkan sebuah **Meja Permainan Lego**:
- Papan kanban dan drag coordinator kita (`ClayKanbanBoard` & `ClayKanbanDragState`) adalah **alas meja magnetik** yang tidak peduli balok apa yang ditaruh di atasnya (bisa mobil, gedung, atau pesawat).
- Meja tersebut memiliki lampu gantung di atasnya: ketika kita mengangkat satu balok, bayangan melayang (floating overlay) digambar langsung dari langit-langit (root window), sehingga tidak pernah terhalang oleh sekat-sekat kotak di atas meja.
- Jika ada pemain yang tidak bisa mengangkat balok karena tangannya gemetar (atau layar HP kecil), meja menyediakan tombol sakelar "Pindah ke..." yang memindahkan balok secara presisi tanpa perlu diseret.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan fitur interaktif ini dari scratch:

1. **Langkah 1: Abstraksi Coordinator Gerakan (`designsystem/ClayKanbanDragState.kt`)**
   - Mulai dari state coordinator yang *buta domain*.
   - Tidak ada referensi ke pesanan, sampel, atau entitas bisnis tertentu—hanya `ID : Any` dan `T : Any`.
   - Mengelola koordinat window global, bounding box kolom, dan deteksi hover drop target.

2. **Langkah 2: Generic Clay Kanban Board (`designsystem/ClayKanbanBoard.kt`)**
   - Buat composable board yang merender kolom-kolom berdesain Claymorphism + Neo-Brutalism.
   - Pasang layer floating card di root koordinat dengan `zIndex(999f)`.
   - Pasang opsi aksesibilitas menu "Pindah ke…" di setiap kartu agar operable tanpa drag.

3. **Langkah 3: Konsolidasi Paritas Drag (`SamplingDragDropState.kt`)**
   - Hapus duplikasi matematika drag yang berserakan di modul lama. Delegasikan koordinasi drag ke `ClayKanbanDragState`.
   - Ini memenuhi prinsip **Aturan Tiga Kali** dan menyusutkan baris kode (ratchet rule).

4. **Langkah 4: Blok Formulir Interaktif (`InteractiveFormState.kt` & `InteractiveForm.kt`)**
   - Buat state holder form untuk me-render input secara dinamis berdasarkan `FieldType` (`TEXT`, `NUMBER`, `BOOL`, `ENUM`).
   - Eksekusi submit via `PrototypeReducer.reduce` dengan `PrototypeAction.Create`.

5. **Langkah 5: Aksi Hapus & Konfirmasi Dialog**
   - Pasang tombol hapus pada baris tabel (`InteractiveTable.kt`) dan kartu kanban (`InteractiveKanban.kt`).
   - Pastikan selalu menggunakan dialog konfirmasi `AlertDialog` bertema Clay (`ClayCard` / `ClayButton`) agar user tidak menghapus data draf tanpa sengaja.

6. **Langkah 6: Chat Edit Panel & Undo Pipeline (`PrototypeChatEditPanel.kt` & `PrototypeSession.kt`)**
   - Sediakan panel instruksi teks di kanan layar canvas prototype.
   - Sambungkan ke endpoint API `/api/builder/propose-spec-ops` atau fallback deterministik di client.
   - Terapkan atomic mutations menggunakan `SpecOpApplier.applyAll`.
   - Simpan riwayat spec di `undoStackByScreen` di dalam `PrototypeSession` sehingga user bisa menekan tombol "Batalkan".

7. **Langkah 7: Ekspor Brief Dialog (`PrototypeExportBriefDialog.kt`)**
   - Render ringkasan kebutuhan draf ke format Markdown yang bersih dan siap salin ke clipboard.

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: Floating Overlay Koordinat Root di `ClayKanbanBoard`
```kotlin
// Root layout kanban yang menangkap koordinat window
Box(
    modifier = modifier
        .fillMaxSize()
        .onGloballyPositioned { coords ->
            if (coords.isAttached) rootWindowOffset = coords.positionInWindow()
        }
) {
    // 1. Grid kolom biasa
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        Row(modifier = rowModifier.padding(ClaySpacing.Md)) {
            // Kolom-kolom kanban...
        }
    }

    // 2. Kartu Melayang di Level Root (Jira-style)
    if (dragState.isDragging && dragState.draggedItem != null) {
        val rootRelativeOffset = dragState.floatingCardOffset(rootWindowOffset)
        val density = LocalDensity.current
        val intOffset = with(density) {
            IntOffset(rootRelativeOffset.x.roundToInt(), rootRelativeOffset.y.roundToInt())
        }
        val widthDp = with(density) { dragState.cardInitialSize.width.toDp() }

        Box(
            modifier = Modifier
                .offset { intOffset }
                .width(widthDp)
                .zIndex(999f)
                .graphicsLayer {
                    rotationZ = 3f // Efek miring neo-brutalist saat diangkat
                    alpha = 0.95f
                }
        ) {
            itemContent(dragState.draggedItem!!, true)
        }
    }
}
```
**Mengapa blok ini ditulis begini?**
- Jika kartu digambar di dalam container kolom asalnya, kartu akan terpotong (`overflow: hidden`) saat digeser ke kolom lain.
- Dengan meletakkannya langsung sebagai anak dari root `Box`, kartu bebas meluncur di atas kolom mana pun tanpa kliping.
- Penambahan rotasi `rotationZ = 3f` memberikan *tactile feedback* khas Neo-Brutalism yang terasa nyata bagi pengguna.

### Blok B: Paritas Drag & Delegasi di `SamplingDragDropState`
```kotlin
// SamplingDragDropState menyusut dari 148 baris menjadi 88 baris
class SamplingDragDropState {
    val delegate = ClayKanbanDragState<SamplingStage, SpkSummaryCard>()

    val isDragging: Boolean get() = delegate.isDragging
    val draggedCard: SpkSummaryCard? get() = delegate.draggedItem
    val hoveredStage: SamplingStage? get() = delegate.hoveredColumnId

    fun onDragStart(card: SpkSummaryCard, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        delegate.onDragStart(card, windowOffset, size, pointerOffset)
    }
    // ...
}
```
**Mengapa blok ini ditulis begini?**
- Modul konveksi `sampling` tidak perlu diubah kontrak publiknya (`SamplingPipelineKanbanBoard` dan `SamplingKanbanCard` tetap berjalan tanpa modifikasi).
- Namun di baliknya, kita menghapus duplikasi rumus perhitungan tabrakan bounding box (`columnBounds.contains(...)`), sehingga bug fixing dan optimasi hanya terpusat di `ClayKanbanDragState`.

### Blok C: State Formulir Interaktif & Sinkronisasi Sesi
```kotlin
fun submit(): Boolean {
    // Generate id otomatis yang aman dan deterministik
    val existingIds = store.rowsOf(entityId).map { it.id }.toSet()
    var newId = "$entityId-${store.rowsOf(entityId).size + autoIdCounter}"
    while (newId in existingIds) {
        autoIdCounter++
        newId = "$entityId-${store.rowsOf(entityId).size + autoIdCounter}"
    }
    
    val newRow = PrototypeRow(newId, formValues.toMap())
    val result = PrototypeReducer.reduce(spec, store, PrototypeAction.Create(entityId, newRow))

    return result.fold(
        onSuccess = { updatedStore ->
            store = updatedStore
            onRowCreated?.invoke(entityId, newRow) // Siarkan ke sesi bersama
            resetForm()
            true
        },
        onFailure = { error ->
            message = error.message
            false
        }
    )
}
```
**Mengapa blok ini ditulis begini?**
- Form tidak memodifikasi list UI secara mutasi langsung (`store.rows.add(...)`), melainkan memanggil reducer domain murni `PrototypeReducer.reduce(..., PrototypeAction.Create)`.
- Callback `onRowCreated` menyiarkan entri baru ke `PrototypeSession.broadcastRowCreated`, yang otomatis meng-update tabel dan kanban di layar lain secara *reaktif*.

---

## ⚖️ 4. Teknologi & Pendekatan yang Dipilih: The "Why"

| Pendekatan yang Dipilih | Alternatif yang Ada | Mengapa Kita Memilih Ini? | Risiko Jika Memakai Alternatif |
|---|---|---|---|
| **Root-Level Floating Overlay Canvas** | Drag lokal di dalam kolom lazy list | Tidak terpotong (`clipToBounds`), koordinat global absolut, performa rendering tinggi. | Kartu terpotong di tepi kolom kanban; flickering saat melompati scroll container. |
| **Pola Ganda: Drag + Tap Menu "Pindah ke…"** | Drag gestures only | Aksesibilitas penuh untuk pengguna mobile, touch screen kecil, atau trackpad lambat. | Pengguna di layar kecil kesulitan melakukan long-drag; melanggar standar WCAG. |
| **Token-Based Clay Components (`ClayCard`, `ClayButton`)** | Raw Material3 Card / Button | Konsistensi outline tebal (3dp), shadow solid tanpa blur, dan brand theme WeMade ERP. | UI tidak konsisten, ungu default Material3 bocor ke dialog/button, melanggar Design System Rules. |
| **In-Memory Sesi Terpusat (`PrototypeSession`)** | EventBus global / Singleton State | Aman multi-window/multi-instance; siklus hidup terisolasi per sesi tab draf (dibuang saat tab ditutup). | State bocor antar tab atau tenant, memory leak jika tidak di-clear. |

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls) & Cara Menghindarinya

1. **Jebakan 1: Meletakkan Sealed Subclass di Package Berbeda**
   - *Kenapa bahaya*: Di Kotlin, jika interface sealed berada di `package com.foo`, subclass/implementasi tidak boleh berada di `package com.foo.bar`. Kompiler akan melempar error: *"Inheritance from sealed interface in different package is not allowed"*.
   - *Solusi kita*: `InteractiveFormState` dan `InteractiveForm` ditempatkan langsung di `package com.eventverse.app.presentation.discovery` sejajar dengan `PlayableState`.

2. **Jebakan 2: Hardcoding Warna Literal (`Color(0xFF...)`)**
   - *Kenapa bahaya*: Literal warna merusak tema gelap/terang dan melanggar aturan arsitektur token tiga lapis.
   - *Solusi kita*: Gunakan selalu `WeMadeColors.*` (`Primary`, `Surface`, `OnSurface`, `Outline`, dll.).

3. **Jebakan 3: File Melewati Batas Ukuran (Soft 400 / Hard 600)**
   - *Kenapa bahaya*: File yang terlalu gemuk sulit direview dan mudah menimbulkan merge conflict.
   - *Solusi kita*: Pisahkan data model/state ke file terpisah (contoh: `ClayKanbanDragState.kt` diekstrak dari `ClayKanbanBoard.kt`), sehingga semua file tetap di bawah 350 baris!

---

## 🧪 6. Bagaimana Cara Membuktikan Kodingan Kita Bekerja?

Kami menyusun pengujian unit test menyeluruh di [`app/shared/src/jvmTest/kotlin/com/eventverse/app/presentation/discovery/PrototypeInteractiveUiTest.kt`](file:///Volumes/amalari/Projects/wemkaeerp/app/shared/src/jvmTest/kotlin/com/eventverse/app/presentation/discovery/PrototypeInteractiveUiTest.kt):

- `testFormStateSubmissionUpdatesSessionAndState`: Memastikan pengisian form menghasilkan row baru, memanggil `Create` action, dan menyiarkan hasilnya ke session.
- `testKanbanAndDeleteAction`: Memastikan kartu kanban dapat dihapus melalui aksi `Delete` dan status kartu ter-update.
- `testTableDeleteRow`: Memastikan baris tabel berkurang setelah penghapusan baris via dialog konfirmasi.
- `testSessionUndoStack`: Memverifikasi mekanisme undo/redo pada perubahan spec chat edit.
- `testClayKanbanDragStateCoordination`: Menguji siklus hidup drag, perhitungan bounding box kolom, pencegahan pemindahan ilegal (`canMoveCheck`), dan commit pemindahan kolom.

Jalankan test dengan perintah:
```bash
./gradlew :app:shared:jvmTest --tests "com.eventverse.app.presentation.discovery.PrototypeInteractiveUiTest"
```

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1: Custom Field Validation**: Tambahkan validasi regex pada `FormField` untuk tipe format seperti `EMAIL` atau `PHONE` langsung di `InteractiveFormState`.
- [ ] **Tantangan 2: Column Reordering Animation**: Pada `ClayKanbanBoard`, tambahkan animasi reordering kartu ketika item di-drop menggunakan `Modifier.animateItemPlacement()`.
