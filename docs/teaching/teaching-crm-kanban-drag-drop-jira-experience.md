# 🎓 Modul Pembelajaran: Arsitektur Drag & Drop CRM Kanban ala Jira di Compose Multiplatform

> **Level Target**: Junior to Mid Compose Multiplatform Developer  
> **Topik Utama**: Gesture Handling, Coordinate Spaces, Unclipped Floating Overlays, CompositionLocal, Pointer Hover Icons  
> **Prasyarat**: Pemahaman dasar Jetpack Compose / Compose Multiplatform layout, modifier chaining, dan state hoisting  
> **Referensi Task**: CRM Kanban Card Drag Pointer Grab & Jira-Like Floating Overlay

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata
Bayangkan kamu sedang memindahkan kartu tugas di papan Kanban fisik. Saat kamu mengambil kartu dari kolom "New Lead" untuk dipindahkan ke "Qualified", tanganmu mengangkat kartu tersebut ke udara. Kartu itu melayang di atas papan dan tidak terhalang oleh sekat kolom kayu di bawahnya.

Dalam UI digital, kesalahan yang sangat umum dilakukan developer pemula adalah mencoba menggerakkan kartu menggunakan `Modifier.offset(x, y)` **di dalam container kolom asalnya sendiri** (misal di dalam item `LazyColumn`).
Akibat fatalnya:
1. **Kartu Terpotong (Clipped) & Hilang**: Begitu kartu digeser melewati batas kolom asal, kartu langsung terpotong atau menghilang karena `LazyColumn` dan container kolom memiliki `clipToBounds = true`.
2. **Kursor Tetap Panah Default**: User tidak tahu bahwa kartu tersebut interaktif dan bisa di-drag karena kursor mouse tidak berubah menjadi tangan grab (`PointerIcon.Hand`).
3. **Layout Berantakan / Jitter**: Jika kartu asal langsung dihapus saat di-drag, kartu-kartu di bawahnya melompat ke atas secara mendadak.

### Analogi Sederhana
Container `LazyColumn` ibarat **laci tertutup**. Jika kamu menggeser benda di dalam laci secara horizontal ke luar dinding laci, benda itu tidak akan terlihat di luar karena terhalang dinding laci.
Solusinya: Benda tersebut harus **diangkat ke udara** (layer teratas papan / root overlay) saat dipegang tangan, sementara di dalam laci ditaruh **bayangan/placeholder** untuk menandai posisi aslinya.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

Jika kamu harus mengimplementasikan Kanban Drag & Drop ala Jira dari layar kosong di Compose Multiplatform, berikut urutan berpikir dan eksekusinya:

```
Step 0: Desain State Coordinator Papan (CrmDragDropState)
   ↓
Step 1: Definisikan CompositionLocal (LocalCrmDragDropState)
   ↓
Step 2: Pasang Root Overlay di Papan (CrmKanbanBoard)
   ↓
Step 3: Daftarkan Koordinat Kolom Penerima / Drop Target (CrmKanbanColumn)
   ↓
Step 4: Pasang Kursor Grab & Gestur Drag pada Kartu (CrmKanbanCard)
   ↓
Step 5: Render Placeholder Slot di Posisi Asal Saat Sedang Di-drag
```

---

## 🧱 3. Bedah Blok per Blok Kode (Deep Code Walkthrough)

### Blok A: State Coordinator Global Papan (`CrmDragDropState.kt`)

```kotlin
class CrmDragDropState {
    var isDragging by mutableStateOf(false)
        private set
    var draggedLead by mutableStateOf<CrmLead?>(null)
        private set
    var cardInitialWindowOffset by mutableStateOf(Offset.Zero)
        private set
    var cardInitialSize by mutableStateOf(Size.Zero)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set
    var hoveredStage by mutableStateOf<LeadStage?>(null)
        private set

    private var startPointerOffset = Offset.Zero
    private val columnBounds = mutableStateMapOf<LeadStage, Rect>()

    fun registerColumn(stage: LeadStage, bounds: Rect) {
        columnBounds[stage] = bounds
    }

    fun unregisterColumn(stage: LeadStage) {
        columnBounds.remove(stage)
    }

    fun onDragStart(lead: CrmLead, windowOffset: Offset, size: Size, pointerOffset: Offset) {
        draggedLead = lead
        cardInitialWindowOffset = windowOffset
        cardInitialSize = size
        startPointerOffset = pointerOffset
        dragOffset = Offset.Zero
        isDragging = true
        updateHoveredStage()
    }

    fun onDrag(dragAmount: Offset) {
        dragOffset += dragAmount
        updateHoveredStage()
    }

    fun onDragEnd(onCommit: (LeadStage) -> Unit) {
        val target = hoveredStage
        val lead = draggedLead
        if (target != null && lead != null && target != lead.stage) {
            onCommit(target)
        }
        reset()
    }
}
```

**Mengapa blok ini ditulis begini?**
- **Satu Sumber Kebenaran Koordinat**: Semua elemen di papan beroperasi dalam koordinat absolut layar (`windowOffset`). Ini membuat perbandingan posisi antara kursor dan kolom menjadi sangat akurat tanpa pusing memikirkan hierarki nested composables.
- **Deteksi Hit Bounding Box Kolom**: Fungsi `updateHoveredStage()` menghitung `currentPointerWindowPos = cardInitialWindowOffset + startPointerOffset + dragOffset`. Begitu posisi kursor berada di dalam `Rect` kolom tertentu, `hoveredStage` otomatis terbarui secara reaktif!

---

### Blok B: Floating Overlay Bebas Clipping di Root Board (`CrmKanbanBoard.kt`)

```kotlin
val dragDropState = rememberCrmDragDropState()
var rootWindowOffset by remember { mutableStateOf(Offset.Zero) }

CompositionLocalProvider(LocalCrmDragDropState provides dragDropState) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                if (coords.isAttached) {
                    rootWindowOffset = coords.positionInWindow()
                }
            }
    ) {
        // Konten Normal Papan (Toolbar & Row 3 Kolom)
        Column(...) { ... }

        // Floating Drag Overlay ala Jira: melayang bebas di atas seluruh board
        if (dragDropState.isDragging && dragDropState.draggedLead != null) {
            val lead = dragDropState.draggedLead!!
            val floatingOffset = dragDropState.floatingCardOffset(rootWindowOffset)
            val cardWidth = with(LocalDensity.current) {
                if (dragDropState.cardInitialSize.width > 0f) {
                    dragDropState.cardInitialSize.width.toDp()
                } else {
                    280.dp
                }
            }

            Box(
                modifier = Modifier
                    .offset { IntOffset(floatingOffset.x.toInt(), floatingOffset.y.toInt()) }
                    .width(cardWidth)
                    .zIndex(999f)
                    .graphicsLayer {
                        rotationZ = -2.5f      // Miring sedikit ala kartu Jira/Trello
                        scaleX = 1.02f
                        scaleY = 1.02f
                        alpha = 0.95f
                    }
                    .pointerHoverIcon(PointerIcon.Hand)
            ) {
                CrmKanbanCard(
                    lead = lead,
                    employees = employees,
                    selected = false,
                    canWrite = false,          // Mode baca-saja saat melayang
                    onSelectLead = {},
                    onUpdateStage = {},
                    onOpenActivities = {}
                )
            }
        }
    }
}
```

**Mengapa blok ini ditulis begini?**
- **`zIndex(999f)` di Level Root**: Karena kartu melayang diletakkan langsung sebagai anak dari `Box` terluar papan, kartu tersebut tidak lagi terikat pada batas `LazyColumn` atau kolom mana pun. Kartu dapat meluncur bebas melintasi seluruh monitor!
- **Sentuhan Mikro-Animasi Jira (`graphicsLayer`)**: Rotasi `-2.5f` derajat dan pembesaran tipis `1.02f` memberikan sensasi fisik yang kuat bahwa kartu tersebut sedang "dipegang dan diangkat".

---

### Blok C: Pointer Grab & Placeholder Slot (`CrmKanbanCard.kt`)

```kotlin
val dragDropState = LocalCrmDragDropState.current
val isBeingDragged = canWrite && dragDropState?.isDragging == true && dragDropState.draggedLead?.id == lead.id
var cardWindowOffset by remember { mutableStateOf(Offset.Zero) }
var cardSize by remember { mutableStateOf(Size.Zero) }

if (isBeingDragged) {
    // Placeholder Slot di kolom asal: mempertahankan tinggi kartu asli
    val placeholderHeight = with(LocalDensity.current) {
        if (cardSize.height > 0f) cardSize.height.toDp() else 110.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(placeholderHeight)
            .clayFlat(
                shape = ClayShapes.Card,
                background = WeMadeColors.SurfaceMuted.copy(alpha = 0.5f),
                outline = WeMadeColors.OutlineSoft,
                borderWidth = ClayBorder.Hairline
            )
            .padding(ClaySpacing.Lg),
        contentAlignment = Alignment.Center
    ) {
        Row(...) {
            IconInbox(modifier = Modifier.size(16.dp), color = WeMadeColors.OnSurfaceMuted)
            Text(
                text = "Memindahkan ${lead.title}…",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = WeMadeColors.OnSurfaceMuted
            )
        }
    }
    return
}

ClayCard(
    modifier = modifier
        .fillMaxWidth()
        .pointerHoverIcon(if (canWrite) PointerIcon.Hand else PointerIcon.Default)
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                cardWindowOffset = coords.positionInWindow()
                cardSize = coords.size.toSize()
            }
        }
        .then(
            if (canWrite) {
                Modifier.pointerInput(lead.id, lead.stage) {
                    detectDragGestures(
                        onDragStart = { pointerOffset ->
                            dragDropState?.onDragStart(lead, cardWindowOffset, cardSize, pointerOffset)
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragDropState?.onDrag(dragAmount)
                        },
                        onDragEnd = {
                            dragDropState?.onDragEnd { targetStage ->
                                onUpdateStage(targetStage)
                            }
                        },
                        onDragCancel = {
                            dragDropState?.onDragCancel()
                        }
                    )
                }
            } else Modifier
        ),
    ...
)
```

**Mengapa blok ini ditulis begini?**
- **`pointerHoverIcon(PointerIcon.Hand)`**: Saat kursor mendekati kartu yang memiliki izin tulis (`canWrite = true`), kursor desktop/web langsung berubah menjadi bentuk tangan interaktif.
- **Penyelamat Layout (`isBeingDragged`)**: Kita tidak me-remove kartu dari list saat di-drag. Sebaliknya, kartu tersebut merender kotak placeholder berukuran sama persis (`cardSize.height`). Dengan cara ini, kartu-kartu lain di dalam `LazyColumn` tetap diam di tempatnya dan tidak mengalami jumping layout.

---

### Blok D: Drop Target Indikator di Kolom (`CrmKanbanColumn.kt`)

```kotlin
val dragDropState = LocalCrmDragDropState.current
val isDropTarget = dragDropState?.isDragging == true &&
        dragDropState.hoveredStage == stage &&
        dragDropState.draggedLead?.stage != stage

DisposableEffect(stage) {
    onDispose {
        dragDropState?.unregisterColumn(stage)
    }
}

Column(
    modifier = modifier
        .onGloballyPositioned { coords ->
            if (coords.isAttached) {
                dragDropState?.registerColumn(
                    stage,
                    Rect(coords.positionInWindow(), coords.size.toSize())
                )
            }
        }
        .clayFlat(
            shape = ClayShapes.Card,
            background = if (isDropTarget) {
                when (stage) {
                    LeadStage.NEW_LEAD -> WeMadeColors.PrimaryContainer
                    LeadStage.QUALIFIED -> WeMadeColors.SuccessBg
                    LeadStage.UNQUALIFIED -> WeMadeColors.ErrorBg
                }
            } else WeMadeColors.SurfaceMuted,
            outline = columnOutline,
            borderWidth = if (isDropTarget) ClayBorder.Thick else ClayBorder.Medium
        )
) {
    if (isDropTarget) {
        // Banner indikator penjatuhan kartu
        Box(
            modifier = Modifier.fillMaxWidth().clayFlat(...),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "Lepas kartu untuk pindah ke ${stage.displayName}")
        }
    }
    ...
}
```

---

## 🔬 4. Technology & Approach ("The Why")

### Mengapa Window Coordinates vs Parent Relative Coordinates?
Dalam aplikasi Compose kompleks, kartu berada di dalam:
`Root Box -> Column -> Row -> Column (Kanban) -> LazyColumn -> Item -> ClayCard`

Jika kita menghitung koordinat relatif terhadap parent bertingkat-tingkat:
- Kode menjadi rapuh (*fragile*), rentan salah kalkulasi saat ada padding, scroll offset, atau window resize.
- Menggunakan `coords.positionInWindow()` memberikan satu titik acuan global yang absolut. Semua elemen (Board, Column, Card, Pointer) berbicara dalam bahasa koordinat yang sama.

### Mengapa CompositionLocal vs Parameter Plumbing?
`LocalCrmDragDropState` adalah contoh pemakaian `CompositionLocal` yang sangat tepat:
- State drag-drop hanya relevan untuk sub-pohon papan Kanban CRM.
- Jika menggunakan parameter passing biasa, setiap level (`Board` -> `Column` -> `Card`) harus mengalirkan 4-5 callback parameter tambahan yang mengotori API signature.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Jebakan Fatal: Meng-unmount Node Pemilik `pointerInput` (`if (isBeingDragged) return ...`)**:
   *Salah*: Saat `isBeingDragged == true`, fungsi composable melakukan `return Box(placeholder...)` dan tidak merender `ClayCard` yang ditempeli `Modifier.pointerInput`.
   *Akibat*: Begitu gestur drag dimulai (frame 1), Compose melihat node yang memiliki `pointerInput` dilepas dari tree. Coroutine gesture detector langsung di-cancel seketika, sehingga kartu sama sekali tidak bisa di-drag (*stuck* / mati).
   *Benar*: Node yang memiliki `Modifier.pointerInput` harus **selalu tetap terpasang di tree** selama drag berlangsung. Tampilkan placeholder di dalam blok `content` dari `ClayCard` tersebut (`if (isBeingDragged) { Placeholder } else { RealContent }`).

2. **Jebakan Infinite Loop Rekursif di Overlay**:
   *Salah*: Komponen kartu di dalam floating overlay juga mendengarkan `LocalCrmDragDropState` dan merender dirinya sebagai placeholder karena `lead.id == draggedLead.id`.
   *Benar*: Operasikan floating overlay dengan `canWrite = false`. Di dalam kartu, pastikan `isBeingDragged = canWrite && ...`. Dengan begitu, kartu overlay merender UI fisik penuh, sedangkan kartu di dalam kolom merender placeholder slot.

3. **Lupa `DisposableEffect` Unregister Bounding Box**:
   Saat kolom unmount (misalnya user berganti filter atau tampilan diubah), bounding box kolom lama harus dibersihkan dari map agar kursor tidak mendeteksi area hantu (*ghost drop zones*).

4. **Konsumsi Gestur Tanpa `change.consume()`**:
   Jika `change.consume()` tidak dipanggil di `onDrag`, gestur drag bisa bocor ke event handler parent atau memicu scroll yang tidak diinginkan pada browser / desktop window.

---

## 🎯 6. Verifikasi & Tantangan Mandiri

### Verifikasi yang Telah Dilakukan
1. **Kompilasi Multiplatform**:
   - Berhasil lulus `./gradlew :app:shared:compileKotlinJvm` (Desktop target)
   - Berhasil lulus `./gradlew :app:shared:compileKotlinWasmJs` (Web target)
2. **Kepatuhan Aturan Desain WeMade**:
   - Menggunakan token baku `ClayShapes`, `ClayBorder`, `ClaySpacing`, dan `WeMadeColors`.
   - Nol literal warna heksadesimal baru di luar tema.
   - Nol Unicode emoji (semua ikon menggunakan vektor Skiko `ClayIcons.kt`).

### Tantangan Mandiri untuk Developer
- Coba tambahkan animasi transisi *spring* halus (`animateOffsetAsState`) saat kartu dilepas di luar kolom target agar kartu terlihat "meluncur kembali" (*snap back*) ke posisi asalnya alih-alih langsung hilang seketika.
