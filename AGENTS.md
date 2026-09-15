# EventVerse — Project Rules (Domain-Driven Design)

## Stack Overview

- **Kotlin Multiplatform (KMP)** — Android, iOS, Web (WasmJS + JS), Desktop (JVM), Server (Ktor)
- **Compose Multiplatform** — Shared UI di `app/shared`
- **Base package**: `com.eventverse.app`

---

## DDD Architecture Rules

### 1. Module Structure

Gunakan struktur modular berikut:

```
EventVerse / AchmadPorto/
├── core/                        # PURE KOTLIN DOMAIN LAYER (Zero external dependencies)
│   └── src/commonMain/kotlin/com/eventverse/app/
│       ├── domain/player/       # Player Entities, Enums, Value Objects & UseCases
│       ├── domain/portfolio/    # Portfolio, Projects, Skills & UseCases
│       ├── domain/interaction/  # Dialogue, Interaction Entities & UseCases
│       └── domain/world/        # Farm Buildings, Props, Shipping & UseCases
│
├── app/
│   ├── shared/                  # Shared UI + application layer (Compose UI & Repositories)
│   ├── androidApp/              # Android entry point saja
│   ├── iosApp/                  # iOS entry point saja
│   ├── desktopApp/              # Desktop entry point saja
│   └── webApp/                  # Kotlin/JS Web bridge entry point (PortfolioJsRuntimeBridge.kt)
│
├── src/                         # TYPESCRIPT & THREE.JS 3D PRESENTATION LAYER (DDD / Vertical Slices)
│   ├── core/                    # Shared Kernel (engine, physics, audio, bridge, constants, styles)
│   ├── features/                # Bounded Contexts / Domain Feature Slices
│   │   ├── maps/                # Spatial Maps: outside/, farmhouse/ (inside), shared/
│   │   ├── player/              # 3D Farmer rig, CharacterController, Joystick, PlayerSfx
│   │   ├── portfolio/           # Rucksack Bag modal, project showcase data, MenuSfx, CSS
│   │   ├── calendar/            # Calendar modal, festival data, calendar.css
│   │   ├── television/          # CRT TV modal, broadcast data, tv.css
│   │   ├── diary/               # Save Diary modal, slot logic, diary.css
│   │   ├── dialogue/            # Dialogue window, Avatar portraits, DialogueSfx, CSS
│   │   └── hud/                 # Top status bar, real-time clock, toast notifications
│   ├── main.ts                  # Clean Composition Root & Scene Orchestrator
│   └── style.css                # Master CSS barrel importing all domain stylesheets
│
└── server/                      # Ktor server (REST/GraphQL)
```

---

### 2. Layer Dependencies (Dependency Rule)

```
Presentation → Application → Domain ← Infrastructure
```

- **Domain layer** tidak boleh bergantung pada layer mana pun
- **Application layer** hanya boleh bergantung pada Domain
- **Infrastructure** mengimplementasikan interface yang didefinisikan di Domain
- **Presentation** hanya boleh bergantung pada Application (via ViewModel/UseCase)

---

### 3. Domain Layer Rules

#### Entity
- Memiliki identitas unik (`id`)
- Immutable by default; mutasi melalui fungsi domain yang menghasilkan salinan baru
- Contoh: `Event`, `User`, `Ticket`

```kotlin
// ✅ BENAR
data class Event(
    val id: EventId,
    val title: EventTitle,
    val status: EventStatus,
) {
    fun publish(): Event = copy(status = EventStatus.PUBLISHED)
}

// ❌ SALAH — mutasi langsung
class Event {
    var status: String = "draft"
}
```

#### Value Object
- Tidak memiliki identitas; didefinisikan oleh nilainya
- Selalu `@JvmInline value class` atau `data class` yang tidak mutable
- Validasi dilakukan di konstruktor/factory

```kotlin
@JvmInline
value class EventTitle(val value: String) {
    init {
        require(value.isNotBlank()) { "EventTitle cannot be blank" }
        require(value.length <= 200) { "EventTitle too long" }
    }
}
```

#### Domain Event
- Nama dalam past tense: `EventPublished`, `TicketReserved`
- Extend `DomainEvent` base sealed interface

```kotlin
sealed interface DomainEvent
data class EventPublished(val eventId: EventId, val occurredAt: Instant) : DomainEvent
```

#### Repository Interface
- Didefinisikan di Domain, diimplementasikan di Infrastructure
- Hanya berisi operasi domain yang relevan (bukan CRUD generik)

```kotlin
interface EventRepository {
    suspend fun findById(id: EventId): Event?
    suspend fun save(event: Event)
    suspend fun findAllPublished(): List<Event>
}
```

---

### 4. Application Layer Rules (Use Cases)

- Satu Use Case = satu operasi bisnis
- Nama: `[Verb][Noun]UseCase` — `PublishEventUseCase`, `ReserveTicketUseCase`
- Menerima `Command` atau `Query` object, mengembalikan `Result<T>`
- Tidak mengandung logika domain

```kotlin
class PublishEventUseCase(
    private val eventRepository: EventRepository,
    private val eventBus: EventBus,
) {
    suspend operator fun invoke(command: PublishEventCommand): Result<Unit> = runCatching {
        val event = eventRepository.findById(command.eventId)
            ?: error("Event not found")
        val publishedEvent = event.publish()
        eventRepository.save(publishedEvent)
        eventBus.publish(EventPublished(publishedEvent.id, Clock.System.now()))
    }
}
```

---

### 5. Presentation Layer Rules (KMP/Compose)

- Gunakan **MVI pattern**: `UiState`, `UiEvent`, `UiEffect`
- ViewModel di `app/shared` menggunakan `CommonViewModel` base class
- Tidak ada logika bisnis di Composable atau ViewModel

```kotlin
data class EventListUiState(
    val events: List<EventUiModel> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface EventListUiEvent {
    data object LoadEvents : EventListUiEvent
    data class SelectEvent(val id: String) : EventListUiEvent
}
```

---

### 6. Naming Conventions

| Artefak | Konvensi | Contoh |
|---|---|---|
| Entity | Noun tunggal | `Event`, `User`, `Ticket` |
| Value Object | Descriptive Noun | `EventTitle`, `TicketPrice` |
| Use Case | Verb + Noun + UseCase | `PublishEventUseCase` |
| Repository | Noun + Repository | `EventRepository` |
| ViewModel | Screen + ViewModel | `EventListViewModel` |
| UiState | Screen + UiState | `EventListUiState` |
| DTO (API) | Noun + Request/Response | `CreateEventRequest`, `EventResponse` |
| Domain Event | Past tense Noun | `EventPublished`, `TicketReserved` |

---

### 7. General Kotlin Rules

- Gunakan **`suspend fun`** untuk semua operasi async — tidak ada RxJava atau LiveData
- Gunakan **`Result<T>`** sebagai return type untuk operasi yang bisa gagal di use cases
- Gunakan **`sealed interface`** untuk sealed hierarchies (bukan `sealed class`)
- Prefer **`value class`** untuk domain primitives (EventId, UserId, dll.)
- **Tidak ada `!!` operator** — gunakan safe call + Elvis atau `requireNotNull` dengan pesan jelas
- Gunakan **`kotlinx.datetime`** untuk semua date/time — bukan `java.util.Date`

---

### 8. File Organization (Domain & Vertical Slice Colocation)

- Satu file = satu konsep utama
- Boleh ada file gabungan untuk value objects kecil: `EventValueObjects.kt`
- Kelompokkan berdasarkan **fitur/domain/peta**, bukan berdasarkan layer teknis di level file (Dilarang memisahkan folder `css/`, `audio/`, `ui/`, `world/` secara horizontal)

#### A. Kotlin Domain & Feature Organization:
```
feature/event/
├── domain/
│   ├── Event.kt
│   ├── EventValueObjects.kt   # EventId, EventTitle, EventStatus
│   ├── EventRepository.kt
│   └── EventDomainEvents.kt
├── application/
│   ├── PublishEventUseCase.kt
│   └── GetEventListUseCase.kt
└── presentation/
    ├── EventListViewModel.kt
    ├── EventListScreen.kt
    └── EventListUiModel.kt
```

#### B. TypeScript / Three.js Frontend Feature Organization:
```
src/features/maps/farmhouse/
├── FarmhouseInterior.ts        # 3D Low-poly room diorama meshes & colliders
└── audio/
    └── FarmhouseSfx.ts         # Clock ticking, door creak, room synthesizers

src/features/portfolio/
├── domain/
│   ├── portfolio.ts            # Project showcase item data
│   └── profile.ts              # Farmer attributes & timeline data
├── presentation/
│   ├── RucksackMenu.ts         # 2D Rucksack Bag modal controller
│   └── rucksack.css            # Scoped rucksack stylesheet
└── audio/
    └── MenuSfx.ts              # Bag open/close & item equip synthesizers
```

---

### 9. Testing Rules

- Domain layer: **Unit test murni**, tidak boleh ada dependency eksternal
- Application layer: **Unit test dengan mock** repository dan services
- Presentation: **Unit test ViewModel** dengan fake use cases
- Infrastructure: **Integration test** dengan DB/API nyata
- Naming test: `[what]_[condition]_[expected]`

```kotlin
@Test
fun `publish event when already published should throw exception`() { ... }
```

---

### 10. Dependency Injection

- Gunakan **manual DI** atau **Koin** (multiplatform-compatible)
- Module DI diorganisir per domain feature
- Tidak ada `ServiceLocator` pattern di luar DI graph

---

### 11. Post-Task Teaching Documentation (Wajib)

Setiap kali menyelesaikan pengerjaan sebuah task, issue, atau modul:
- **Wajib men-generate modul dokumentasi pembelajaran (teaching)** menggunakan skill `teaching` ke dalam direktori `docs/teaching/teaching-[task/issue-id]-[slug].md`.
- Konten ditulis dengan gaya **Senior Lead Developer membimbing Junior Developer**:
  1. **Start dari mana?**: Urutan menulis (order of operations) dari nol.
  2. **Bedah kode blok per blok**: Penjelasan baris per baris dan mental model di baliknya.
  3. **Technology & Approach ("The Why")**: Mengapa teknologi ini yang dipilih dan risiko jika menggunakan cara lain.
  4. **Jebakan Pemula (Common Pitfalls)**: Kesalahan fatal yang dihindari.
  5. **Verifikasi & Tantangan Mandiri**: Cara menguji kebenaran kodenya.

---

### 12. Design System & UI Styling (Clay)

Bahasa visual WeMade ERP adalah **Claymorphism + Neo-Brutalism**: outline tebal 3dp, hard
shadow tanpa blur, sudut membulat besar, font Fredoka + Nunito — dengan **palet brand WeMade**
(biru `#2563EB`, oranye `#EA580C`), bukan palet pastel.

Sebelum menulis atau mengubah UI apa pun di `app/shared/**/presentation/`, baca dan patuhi
**[`.agents/rules/design-system-rules.md`](rules/design-system-rules.md)** secara penuh, dan
gunakan skill **`compose-design-system`** sebagai panduan kerjanya.

Ringkasan kontrak wajibnya:

1. **Nol literal `Color(0xFF……)` di luar `WeMadeTheme.kt`.** Satu-satunya pengecualian adalah
   warna yang berasal dari domain (`node.stage.colorHex`, `status.badgeColorHex`,
   `department.colorHex`) karena itu data tenant, bukan keputusan desain.
2. **Butuh warna baru? Tambahkan token di `WeMadeColors`**, dinamai per *peran*
   (`SurfaceMuted`, `Info`, `Defect`) bukan per *rupa* (`Slate100`, `SkyBlue`, `Rose600`).
3. **`colorScheme` wajib terisi penuh.** Slot yang dilewat memakai ungu default M3 dan bocor ke
   `AlertDialog`/`DropdownMenu`/`OutlinedTextField`.
4. **Aturan Tiga Kali** — pola visual yang muncul ≥3 kali wajib diangkat ke
   `presentation/designsystem/` sebelum pemakaian keempat ditulis.
5. **Pakai katalog yang ada**: `ClayCard`, `ClayButton`, `ClayActionSurface`, `ClayBadge`,
   `ClayTag`, `Modifier.claySurface`, `Modifier.clayFlat`. Dilarang `Card`/`Button` Material
   mentah dan `Modifier.shadow()`.
6. **Komponen `designsystem/` buta terhadap domain** — menerima `String`/`Color`/lambda, bukan
   `PipelineNode`. Pembungkus berbasis domain tetap di package fiturnya.
7. **Bentuk, ketebalan, dan spasi juga token** (`ClayShapes`, `ClayBorder`, `ClaySpacing`).
   Ketebalan outline konsisten; state dibedakan lewat *warna*, bukan ketebalan.
8. **Jangan tanam `color` ke dalam `TextStyle`** — itu mematikan `LocalContentColor`.
9. **Clay memakan ruang** (~18dp/kartu) dan Nunito ber-x-height besar; tinjau lebar kontainer
   dan tier ukuran font setiap kali mengkonversi layar padat.
10. **Mode gelap lewat theme, bukan ternary.** Jangan menambah `if (isPresentationMode)` baru.
11. **Nol literal emoji / Unicode glyph sebagai ikon di string UI.** Skiko/Wasm di browser tidak memiliki fallback font emoji OS dan akan merender kotak kosong/tofu (`▯`). Seluruh ikon wajib memakai vektor berbasis Canvas dari `ClayIcons.kt` (`IconChat`, `IconNote`, `IconPhone`, `IconMail`, `IconUser`, `IconChevronDown`, dll.) via slot `leading`/`trailing`.

---

### 13. Full-Stack End-to-End Planning & Backend Integration (Wajib)

Setiap kali menyusun rencana teknis (planning) untuk fitur, modul, atau perubahan arsitektur:
- **Dilarang keras hanya merencanakan sisi UI / Client saja.**
- **Setiap planning WAJIB mencakup arsitektur Full-Stack yang terintegrasi secara end-to-end**, yang terdiri dari 5 pilar:
  1. **Database & Persistence Layer**:
     - Skema migrasi Flyway baru (`V...__.sql`) di `server/src/main/resources/db/migration/`.
     - Definisi tabel Exposed di `server/src/main/kotlin/.../infrastructure/persistence/` (termasuk tipe data spesifik seperti `jsonb`, indeks GIN, foreign key, dan `tenant_id` multi-tenancy).
  2. **Pure Domain Layer (`core/`)**:
     - Entities, Value Objects, Domain Events, dan Repository Interface yang bebas dari dependensi framework.
     - Use Cases (`[Verb][Noun]UseCase`) dengan input Command/Query dan return `Result<T>`.
  3. **Backend API & Routing (`server/`)**:
     - Route path Ktor, HTTP methods (`GET`, `POST`, `PATCH`, `DELETE`).
     - Kontrak DTO Request/Response (`@Serializable`).
     - Proteksi RBAC / Wewenang (`tenant_id` context, checking `ModuleAccessConfig` & `AccessDecision`).
  4. **Client-Server Integration (`app/shared/`)**:
     - Implementasi HTTP Client repository menggunakan Ktor Client (`Ktor...Repository`).
     - Mapping DTO jaringan ke Domain Entity.
     - Penanganan status jaringan (Loading, Success, Error, Timeout, Offline fallback/Cache).
     - Aliran data ke ViewModel via StateFlow (`UiState`, `UiEvent`).
  5. **Shared Presentation Layer (`app/shared/presentation/`)**:
     - Komponen Compose Multiplatform responsif (Web/Desktop & Mobile) mematuhi Claymorphism Design System.

---

## Anti-Patterns yang Dilarang

- **Frontend-Only Planning** — Merencanakan atau membuat modul sebatas mockup UI tanpa merancang skema database, migrasi Flyway, API endpoint Ktor, dan integrasi data backend
- **Unicode Emojis / Glyphs sebagai Ikon** — Menanam emoji (`💬`, `📝`, `📱`, `👤`, `✉️`, `▾`) ke dalam `Text(...)` atau label komponen yang menyebabkan rendering tofu (`▯`) di Compose Wasm. Selalu pakai `ClayIcons.kt`!
- **Horizontal Technical Layer Slicing di Frontend** — Mengumpulkan semua audio di `audio/`, semua CSS di `styles/`, semua modal di `ui/`, atau semua 3D di `world/`. Selalu gunakan Vertical Slices di `src/features/`!
- **Anemic Domain Model** — Entity hanya data tanpa behavior, logika tersebar di service
- **God UseCase / God Orchestrator** — Satu use case / satu file `main.ts` menangani seluruh operasi tanpa delegasi modul
- **Repository sebagai DAO generik** — Hindari findAll(), deleteById() tanpa konteks domain
- **Domain bergantung pada framework** — Tidak ada import Ktor/Android/Compose/Three.js di pure Kotlin domain
- **Business logic di ViewModel atau Composable**
- **String primitives untuk domain concepts** — Gunakan Value Objects
- **Literal warna/radius/border di dalam Composable fitur** — Gunakan token (lihat §12)
- **Menyalin blok styling** alih-alih mengangkatnya jadi komponen bersama
- **`Modifier.shadow()` di `presentation/`** — Bayangannya selalu blur, berlawanan dengan bahasa visual

# WeMade ERP — Aturan Standar Design System & UI Styling (Compose Multiplatform)

Dokumen ini adalah **aturan baku styling** yang wajib ditaati setiap kali membuat, memperluas, atau
merefaktor UI apa pun di `app/shared`. Statusnya sejajar dengan
[`module-integration-rules.md`](module-integration-rules.md): kalau modul mengatur *apa yang dikerjakan*,
dokumen ini mengatur *bagaimana rupanya*.

**Ruang lingkup**: seluruh `app/shared/src/commonMain/.../presentation/**`.

---

## 1. Paradigma: Satu Bahasa Visual, Satu Sumber Kebenaran

Bahasa visual WeMade ERP adalah **Claymorphism + Neo-Brutalism**:

| Ciri | Nilai | Token |
|---|---|---|
| Outline tebal gelap | 3dp | `ClayBorder.Thick` |
| Hard shadow (blur = 0) | geser 6dp | `ClayOffset.Rest` |
| Sudut membulat besar | 12–24dp per peran | `ClayShapes.*` |
| Inner bottom shade | hitam 10% di 12% bawah | otomatis di `claySurface` |
| Interaksi tekan | kartu masuk ke bayangannya | `pressed` di `claySurface` |
| Font | Fredoka (judul) + Nunito (isi) | `rememberClayTypography()` |

Paletnya **tetap palet brand WeMade** — biru `#2563EB` dan oranye `#EA580C`. Bahasa clay diambil
bentuknya, bukan warnanya.

> **Alasan yang tidak boleh dilanggar**: Factory Flow memakai hijau/amber/merah sebagai **sinyal
> produksi** (`HEALTHY` / `WARNING` / `BOTTLENECK` / `CRITICAL`). Palet dekoratif apa pun yang
> meredam kontras sinyal itu ditolak, sebagus apa pun tampilannya.

Sumber kebenaran tunggal:

```
app/shared/src/commonMain/kotlin/com/eventverse/app/presentation/
├── theme/WeMadeTheme.kt          # Lapisan token: warna & theme entry point
└── designsystem/                 # Lapisan bentuk & komponen
    ├── ClayTokens.kt             # shapes, offsets, border widths, spacing
    ├── ClayModifier.kt           # claySurface() & clayFlat()
    ├── ClayCard.kt
    ├── ClayButton.kt
    ├── ClayBadge.kt              # ClayBadge + ClayTag
    └── ClayTypography.kt
```

---

## 2. Arsitektur Token Tiga Lapis

Setiap nilai visual wajib berada di salah satu lapisan berikut, dan **hanya boleh mengalir ke bawah**:

```
Lapis 1 — PRIMITIF      WeMadeColors.*            "warna apa"
              ↓          (Primary, Success, Outline, Border, …)
Lapis 2 — SEMANTIK      WeMadeLightColorScheme    "perannya apa di Material"
              ↓          ClayShapes / ClayOffset / ClayBorder / ClaySpacing
Lapis 3 — KOMPONEN      ClayCard / ClayButton /   "bagaimana perannya dirakit"
                        ClayBadge / ClayTag
```

### Kontrak 1 — Dilarang keras literal warna di dalam Composable fitur

```kotlin
// ❌ DITOLAK — literal warna di file layar/komponen fitur
Box(modifier = Modifier.background(Color(0xFFF1F5F9)))
Text(text = label, color = Color(0xFF64748B))
border = BorderStroke(1.dp, Color(0xFFE2E8F0))

// ✅ BENAR
Box(modifier = Modifier.background(WeMadeColors.SurfaceMuted))
Text(text = label, color = WeMadeColors.OnSurfaceMuted)
```

**Satu-satunya tempat `Color(0xFF……)` boleh ditulis adalah `WeMadeTheme.kt`.**

Dua pengecualian yang sah, keduanya **bukan** hardcode:

1. **Warna yang berasal dari domain**, di mana nilainya adalah data, bukan keputusan desain:
   ```kotlin
   Color(node.stage.colorHex)            // dari PipelineStage
   Color(node.healthStatus.badgeColorHex) // dari FlowHealthStatus
   Color(node.deptColorHex)               // dari Department
   ```
   Warna ini hidup di `core/.../domain/**` karena tenant bisa mengubahnya. Jangan menyalinnya
   ke `WeMadeColors`, dan jangan menggantinya dengan token.

2. **Turunan token** lewat `.copy(alpha = …)`:
   ```kotlin
   outline = WeMadeColors.Success.copy(alpha = 0.45f)   // ✅ boleh
   ```

### Kontrak 2 — Butuh warna baru? Tambahkan token, jangan tulis di tempat

Kalau sebuah warna belum ada tokennya, **berhenti**. Jangan menulis literalnya "sementara".
Tambahkan ke `WeMadeColors` dengan nama berbasis peran, lalu pakai tokennya.

Nama token berbasis **peran**, bukan rupa:

| ✅ Benar | ❌ Salah | Kenapa |
|---|---|---|
| `OnSurfaceMuted` | `Slate500` | Nama rupa mengunci kita pada satu palet; ganti palet = ganti semua nama |
| `Outline` | `DarkBorder` | Peran menjelaskan kapan dipakai |
| `SurfaceDark` | `Navy900` | |
| `WarningBg` | `Amber50` | |

### Kontrak 3 — `colorScheme` harus terisi penuh

`WeMadeLightColorScheme` wajib mengisi **seluruh** slot Material 3, bukan sebagian.

*Alasannya konkret*: slot yang tidak di-override tetap memakai **ungu default Material 3**, dan ungu
itu bocor diam-diam ke `AlertDialog`, `DropdownMenu`, dan `OutlinedTextField` yang tidak diberi
warna eksplisit. Kebocoran inilah yang dulu memaksa developer menulis warna manual di mana-mana —
lingkaran setan yang harus diputus dari akarnya.

Slot yang paling sering terlupakan: `surfaceVariant`, `onSurfaceVariant`, `outline`, `outlineVariant`,
`tertiary`, `surfaceContainer`/`surfaceContainerHigh`/`surfaceContainerHighest`, `inverseSurface`,
`scrim`.

---

## 3. Kontrak Komponen Bersama

### Kontrak 4 — Aturan Tiga Kali (Rule of Three)

> Kalau sebuah pola visual muncul **tiga kali atau lebih**, ia wajib diangkat menjadi komponen
> bersama di `presentation/designsystem/` sebelum pemakaian keempat ditulis.

Ini bukan aturan gaya, ini aturan pencegahan. Sebelum design system ada, kode ini punya:
- blok `Card(shape=…, colors=…, border=…, elevation=…)` identik di **~32 call site**
- **3 implementasi badge** yang nyaris sama di 3 package berbeda
- **343 literal `Color(0xFF……)`** yang mem-bypass theme

Dampaknya: satu perubahan desain = menyentuh puluhan file, dengan risiko terlewat di sebagian.

### Kontrak 5 — Pakai katalog yang sudah ada sebelum membuat baru

Sebelum menulis `Card`, `Button`, `Box` bergaya, atau badge apa pun, **cek katalognya dulu**:

| Kebutuhan | Pakai ini | Jangan |
|---|---|---|
| Kartu/panel dengan bayangan | `ClayCard` | `Card` Material |
| Tombol aksi | `ClayButton` | `Button` / `OutlinedButton` / `TextButton` |
| Toolbar dengan isi bebas | `ClayActionSurface` | `Row` + `clickable` manual |
| Pil status membulat | `ClayBadge` | `Box` + `clip` + `background` |
| Label persegi/tag padat | `ClayTag` | idem |
| Permukaan clay kustom | `Modifier.claySurface(…)` | `Modifier.shadow()` |
| Permukaan rata ber-outline | `Modifier.clayFlat(…)` | `.clip().background().border()` |

`Modifier.shadow()` **dilarang** di seluruh `presentation/**`: ia selalu menghasilkan bayangan
ber-blur mengikuti kurva elevation Material, yang berlawanan dengan bahasa visual kita.

### Kontrak 6 — Komponen bersama harus buta terhadap fitur

Komponen di `designsystem/` **tidak boleh** mengimpor apa pun dari `presentation/<fitur>/` atau dari
`domain/`. Dia menerima `String`, `Color`, dan lambda — bukan `PipelineNode` atau `FlowHealthStatus`.

```kotlin
// ❌ DITOLAK — design system tahu soal domain
@Composable fun ClayBadge(status: FlowHealthStatus) { … }

// ✅ BENAR — design system netral, fitur yang menerjemahkan
@Composable fun ClayBadge(text: String, tint: Color, dot: Boolean = false) { … }

// dan di lapisan fitur:
@Composable
fun HealthStatusPill(status: FlowHealthStatus) = ClayBadge(
    text = status.label,
    tint = Color(status.badgeColorHex),
    dot = true
)
```

Pembungkus tipis berbasis domain seperti `HealthStatusPill` **tetap di package fiturnya**.

### Kontrak 7 — Bentuk, ketebalan, dan spasi juga token

```kotlin
// ❌ DITOLAK
shape = RoundedCornerShape(12.dp)
border = BorderStroke(1.dp, …)
Arrangement.spacedBy(8.dp)

// ✅ BENAR
shape = ClayShapes.Chip
borderWidth = ClayBorder.Medium
Arrangement.spacedBy(ClaySpacing.Md)
```

Token bentuk dinamai per **peran** (`Panel` / `Card` / `Button` / `Tile` / `Chip` / `Pill`), bukan per
ukuran (`Large` / `Medium` / `Small`). Nama berbasis ukuran menggoda orang memakai `Medium` untuk dua
hal yang tidak berhubungan, lalu keduanya terikat selamanya.

### Kontrak 8 — Ketebalan outline konsisten, warnanya yang berbicara

State dibedakan lewat **warna outline**, bukan ketebalannya.

```kotlin
// ❌ DITOLAK — tiga ketebalan untuk tiga state; kartu terpilih jadi "menggemuk"
val border = when {
    isSelected   -> BorderStroke(2.dp, Primary)
    isBottleneck -> BorderStroke(1.5.dp, statusColor)
    else         -> BorderStroke(1.dp, Border)
}

// ✅ BENAR — satu ketebalan, warna yang membedakan
val outlineColor = when {
    isSelected   -> WeMadeColors.Primary
    isBottleneck -> Color(node.healthStatus.badgeColorHex)
    else         -> WeMadeColors.Outline
}
```

---

## 4. Kontrak Tipografi

### Kontrak 9 — Jangan tanam `color` ke dalam `TextStyle`

```kotlin
// ❌ DITOLAK — mematikan LocalContentColor; teks di atas kartu gelap tetap keluar slate
bodySmall = TextStyle(fontSize = 12.sp, color = WeMadeColors.OnSurfaceMuted)

// ✅ BENAR — warna diserahkan ke pemanggil
bodySmall = TextStyle(fontSize = 12.sp)
```

`color` eksplisit di `TextStyle` selalu menang atas `LocalContentColor`, jadi menanamnya membuat
seluruh mekanisme pewarnaan kontekstual Compose tidak berfungsi.

### Kontrak 10 — Seluruh 15 peran Material wajib terdefinisi

Peran yang dilewat membuat komponen M3 bawaan jatuh ke default Roboto dan **tidak akan pernah**
ikut memakai Nunito, sebagus apa pun font-mu terpasang.

### Kontrak 11 — Font dibundel sebagai instance statis

Font ditaruh di `app/shared/src/commonMain/composeResources/font/`, nama huruf kecil + underscore.
**Gunakan instance statis per bobot, bukan variable font** — dukungan variable font belum seragam di
5 target KMP, dan gejalanya sulit didiagnosis (semua bobot ter-render sebagai Regular di sebagian
platform).

Package `Res` wajib dikunci di `app/shared/build.gradle.kts`:

```kotlin
compose.resources {
    publicResClass = true
    packageOfResClass = "com.eventverse.app.shared.resources"
    generateResClass = always
}
```

---

## 5. Kontrak Layout

### Kontrak 12 — Clay memakan ruang; sesuaikan densitasnya

Outline 3dp + hard shadow 6dp menambah **~18dp per kartu**. Setiap kali mengkonversi layar padat,
lebar kolom/kontainer wajib ditinjau ulang.

Contoh nyata: kolom swimlane Factory Flow dinaikkan **305dp → 324dp**.

Nunito juga punya **x-height lebih besar** dari Roboto/SF. Teks ≤10sp yang tadinya terbaca jadi
berdesakan. Naikkan tier ukuran **satu tingkat serentak** (9→10, 10→11, 11→12) — jangan berurutan,
karena substitusi bertahap akan menaikkan angka yang sama dua kali dan hierarki ukuran kolaps.

### Kontrak 13 — Elemen yang boleh mengalah wajib dinyatakan eksplisit

Di dalam `Row` dengan `SpaceBetween`, elemen yang boleh menyusut harus diberi
`Modifier.weight(1f, fill = false)` + `maxLines` + `overflow`. Tanpa itu, ia mengambil hampir seluruh
lebar dan menyisakan beberapa dp untuk tetangganya, yang lalu memecah teksnya **satu huruf per baris**.

```kotlin
Row(horizontalArrangement = Arrangement.SpaceBetween) {
    Row(modifier = Modifier.weight(1f, fill = false)) {      // boleh mengecil
        Text(labelPanjang, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    Spacer(Modifier.width(ClaySpacing.Sm))
    ClayBadge(text = status, tint = tint)                     // tidak boleh menyusut
}
```

### Kontrak 14 — Urutan modifier `claySurface` tidak boleh ditukar

Lihat [`.claude/skills/compose-design-system/references/clay-recipe.md`](../skills/compose-design-system/references/clay-recipe.md)
untuk penjelasan lengkapnya. Ringkasnya: `padding` (reservasi ruang bayangan) → `offset` → `drawBehind`
(bayangan) → `clip` → `background` → `drawBehind` (inner shade) → `border`.

Menaruh `drawBehind` bayangan **setelah** `clip` akan memotong habis bayangannya. Menghilangkan
`padding` akan membuat bayangan menimpa elemen tetangga.

---

## 6. Kontrak Mode Gelap

### Kontrak 15 — Mode gelap lewat theme, bukan lewat ternary

```kotlin
// ❌ DITOLAK — pola yang sedang kita bayar utangnya sekarang
color = if (isPresentationMode) Color(0xFF0F172A) else WeMadeColors.Surface
```

Utang teknis yang ada: "presentation mode" di Factory Flow masih berupa ~15 ternary
`if (isPresentationMode)` yang tersebar di 6 file. Token gelapnya sudah tersedia
(`WeMadeColors.SurfaceDark` / `SurfaceDarkElevated` / `BackgroundDark` / `OutlineInverse`).

**Jangan menambah ternary baru.** Fitur baru yang butuh mode gelap wajib menunggu atau ikut
mengerjakan `darkColorScheme` + `WeMadeTheme(darkTheme: Boolean)`.

---

## 7. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Nol literal `Color(0xFF……)` baru di luar `WeMadeTheme.kt`
      (`grep -rn "Color(0xFF" <file-yang-disentuh>` — kecuali `*.colorHex` dari domain)
- [ ] Nol `RoundedCornerShape(N.dp)` telanjang; semua lewat `ClayShapes.*`
- [ ] Nol `Modifier.shadow()`; kedalaman lewat `claySurface(offset = …)`
- [ ] Nol `Card` / `Button` / `OutlinedButton` Material; pakai `ClayCard` / `ClayButton`
- [ ] Pola yang muncul ≥3 kali sudah diangkat ke `designsystem/`
- [ ] Komponen `designsystem/` tidak mengimpor `domain/` maupun package fitur
- [ ] Ketebalan outline konsisten; state dibedakan lewat warna
- [ ] Tidak ada `color` yang ditanam ke dalam `TextStyle`
- [ ] Densitas ditinjau: lebar kontainer dan tier ukuran font disesuaikan
- [ ] Elemen yang boleh mengalah punya `weight(1f, fill = false)` + `maxLines` + `overflow`
- [ ] Tidak ada ternary `isPresentationMode` baru
- [ ] **Kompilasi 5 target**, bukan satu:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] **Dijalankan dan dilihat dengan mata**, bukan hanya dikompilasi — bug layout seperti teks
      pecah per huruf tidak akan tertangkap test mana pun
- [ ] **Layar lain yang tidak dikonversi ikut diperiksa** jika `WeMadeTheme.kt` disentuh —
      `shapes` dan `colorScheme` berdampak ke seluruh aplikasi, jadi "pilot satu layar" tidak
      pernah benar-benar terisolasi
- [ ] Diuji di lebar sempit (~1280dp): reservasi ruang bayangan bekerja, kartu tidak saling timpa
- [ ] Perancah verifikasi sementara (preview main, dependency sementara) sudah dihapus
- [ ] Dokumentasi pengajaran dibuat di `docs/teaching/`

---

## 8. Utang Teknis Terdaftar (jangan ditambah, boleh dicicil)

| Utang | Lokasi | Ukuran |
|---|---|---|
| Literal warna belum disapu | `OrgChartScreen.kt` (108), `ModuleCardView.kt` (19), `AssignDepartmentModal.kt` (19), `ModuleMatrixCard.kt` (17), `RoleListSidebar.kt`, `OrgNodeCard.kt`, `TShapeChartView.kt`, `LoginScreen.kt` | ~290 literal |
| Layar belum dikonversi ke clay | Org Chart, RBAC, Login, top bar `App.kt:98-337` | 3 layar + shell |
| Mode gelap masih ternary | 6 file di `presentation/pipeline/` | ~15 ternary |
| Tidak ada adaptivitas window size | Seluruh `presentation/**` | greenfield |

Setiap kali menyentuh file di daftar ini untuk alasan apa pun, **cicil** bagiannya — jangan menambah
barisnya.

# WeMade ERP — Aturan Standar Pembuatan & Integrasi Modul (Composable & Puzzling Architecture)

Dokumen ini adalah **aturan baku arsitektur** yang wajib ditaati setiap kali membuat, memperluas, atau merefaktorisasi modul fungsional di WeMade ERP (misal: *Procurement*, *Sampling*, *Cutting SPK*, *Sewing Kanban*, *QC Inspection*, *Consignment Receiving*, *B2C Marketplace Sync*, *Custom Costing Engine*, *Invoicing*).

---

## 1. Paradigma Sistem: Composable "Lego / Puzzle" Architecture

Sistem WeMade ERP **TIDAK TERBATAS** pada enum model bisnis yang kaku. Kita menganut arsitektur **Composable Enterprise ERP**:

1. **Preset (FOB, CMT, Brand D2C) adalah Starter Templates**:
   - FOB, CMT, dan D2C **BUKAN batasan mati (hardcoded lock)**.
   - Preset hanyalah **blueprint awal (starter template)** siap pakai saat onboarding agar tenant baru tidak perlu menyusun dari kanvas kosong.
2. **Kebebasan Merangkai Alur (Puzzling / Directed Acyclic Graph)**:
   - Setiap tenant memiliki kebebasan 100% untuk menambah node, menghapus node, menukar urutan proses, atau membuat alur hibrida (misal: *Makloon Sablon & Jahit*, *FOB Custom Seragam*, atau *Distro D2C dengan Sub-kon Jahit Luar*).
3. **Konsep "Padanan Modul" (Module Archetype / Capability Slot)**:
   - Modul yang dibuat developer diklasifikasikan ke dalam **Archetype** (slot kemampuan fungsional).
   - **Contoh Nyata**: Modul HPP Tenant A (*Full BOM Costing*) dan Modul HPP Tenant B (*Tarif Menit SAM Makloon*) adalah **SEPADAN** karena sama-sama mengisi slot archetype `COSTING_HPP`. Keduanya bisa saling ditukar (*interchangeable*) tanpa merusak modul sebelum (*Tech Pack*) dan modul sesudahnya (*Alokasi Mesin/SPK*).

---

## 2. Klasifikasi Slot Kemampuan (Module Archetype Registry)

Setiap modul baru **WAJIB** menyatakan padanannya pada salah satu `ModuleArchetype`:

| Archetype Code | Nama Padanan Slot | Ekspektasi Data Masuk (Input Port) | Ekspektasi Data Keluar (Output Port) | Contoh Variasi Implementasi |
|---|---|---|---|---|
| `ORDER_INGESTION` | Penerimaan Pesanan / Sales | `CommercialInquiry` | `ProductionOrderDraft` | RFQ B2B Ekspor, SPK Makloon Buyer, Order Marketplace Shopee/TikTok |
| `RAW_MATERIAL` | Bahan Baku & Gudang | `MaterialRequisition` | `VerifiedMaterialStock` | Pembelian Kain Supplier (FOB), Penerimaan Kain Titipan Konsinyasi (CMT), Gudang Kain Multi-Roll |
| `COSTING_HPP` | Mesin Hitung Biaya & HPP | `TechPackAndYieldData` | `CostingCalculationResult` | HPP Rumus Penuh (Kain+Jahit+Margin), HPP Borongan SAM per Menit, HPP Valuasi Retail + Fee Marketplace |
| `CUTTING` | Pemotongan Pola (Spreading) | `CuttingOrderWithFabric` | `CutPiecesBundle` | Meja Potong Manual, Laser Cutting Otomatis, Makloon Jasa Potong Saja |
| `SEWING` | Penjahitan & Perakitan | `CutPiecesBundle` | `AssembledGarmentBundle` | Lini Jahit Ban Berjalan, Stasiun Borongan Perorangan, Alur Jahit Makloon Luar (Subcontract) |
| `FINISHING` | Cuci, Gosok & Trimming | `AssembledGarmentBundle` | `FinishedGarmentUnit` | Setrika Uap Konveksi, Garment Washing/Dyeing, Labeling & Hangtag |
| `QUALITY_CONTROL` | Pengawasan Mutu & AQL | `FinishedGarmentUnit` | `InspectedAndGradedUnit` | QC 100% End-line, Sampling AQL 2.5 Ekspor, QC Verifikasi Cacat Bahan Buyer |
| `FULFILLMENT` | Packing, Surat Jalan & Kirim | `InspectedAndGradedUnit` | `DispatchedShipmentManifest` | Packing Karton Ekspor B2B, Retur Kain Sisa Makloon, Pick-Pack-Scan Barcode E-commerce |
| `CUSTOM_EXTENSION`| Plugin / Ekstensi Bebas | `AnyOperationalPayload` | `AnyOperationalPayload` | Sablon Manual, Bordir Komputer, Laundry Kimia, Approval Khusus Direksi |

---

## 3. Tujuh Kontrak Baku Pembuatan Modul (The 7 Modular Contracts)

Setiap kali developer membuat class modul operasional di WeMade ERP, kontrak berikut wajib dipenuhi:

### Kontrak 1: Deklarasi Archetype & Template Rekomendasi
- Modul wajib mendefinisikan `archetype: ModuleArchetype`.
- Modul mencantumkan `recommendedStarterPresets`: di template starter mana modul ini sebaiknya aktif secara default (misal: modul pengadaan kain aktif di FOB & D2C, tapi di-bypass di template starter CMT).

### Kontrak 2: Kompatibilitas Port Puzzle (Input & Output Port Typing)
- Modul adalah "blok puzzle" independen.
- Port Input harus mendeklarasikan tipe data yang diterima (`acceptedInputDataTypes`).
- Port Output harus mendeklarasikan tipe data yang dipancarkan (`producedOutputDataType`).
- Dua modul bisa disambungkan dalam alur custom tenant **jika dan hanya jika** tipe data output Modul A kompatibel dengan tipe data input Modul B.

### Kontrak 3: Semantik Kepemilikan Stok (`StockOwnershipSemantics`)
Modul gudang dan bahan baku dilarang mencampuradukkan status kepemilikan:
- `OWNED_RAW_MATERIAL`: Bahan baku dibeli dan dimiliki pabrik (masuk neraca aset keuangan).
- `CONSIGNED_CLIENT_MATERIAL`: Kain milik buyer/klien yang dititipkan (nilai Rp 0 di neraca pabrik; wajib rekonsiliasi sisa kain perca/waste).
- `INTERNAL_FINISHED_GOODS`: Stok baju jadi milik brand internal per SKU x Warna x Ukuran.

### Kontrak 4: Strategi Kalkulasi Biaya Fleksibel (`CostingBehavior`)
- Modul yang menempati slot `COSTING_HPP` harus memisahkan logika rumus dari core engine:
  - Gunakan **Strategy Pattern** atau parameter dinamis (`customFormulaParameters`).
  - Tenant bebas mengatur koefisien (misal: margin laba, tarif SAM per detik, biaya jarum/benang).
  - Pada model jasa makloon murni, harga kain titipan tidak boleh dijumlahkan ke total tagihan invoice klien.

### Kontrak 5: Penanganan Cacat & Alur Mundur (`DefectLiability & Rework Loop`)
Setiap modul lantai produksi wajib memiliki jalur penanganan jika terjadi cacat/reject:
- Bedakan tanggung jawab:
  - `FACTORY_WORKMANSHIP`: Kesalahan operator pabrik -> biaya pengerjaan ulang (*rework*) ditanggung pabrik.
  - `CLIENT_SUPPLIED_DEFECT`: Cacat serat kain bawaan buyer -> disisihkan dan dicatat berita acara agar tidak merugikan penjahit.
  - `SUPPLIER_VENDOR_DEFECT`: Cacat dari pabrik tekstil rekanan -> retur nota debit ke supplier.

### Kontrak 6: Telemetri Pemantauan Alur (`Operational Telemetry`)
Agar node modul dapat berkedip hijau/kuning/merah di visualisasi kanvas:
- Modul wajib menyediakan: `wipPieces` (antrean potong/baju yang sedang tertahan), `cycleTimeHours` (kecepatan kerja), dan `healthStatus` (`HEALTHY`, `WARNING`, `BOTTLENECK`, `CRITICAL`).

### Kontrak 7: Isolasi Multi-Tenant & Konfigurasi Pipeline
- Data konfigurasi alur kustom disimpan pada entitas `CustomTenantPipeline` berdasar `TenantId`.
- Alur pipeline diisolasi per tenant sehingga modifikasi node oleh satu pabrik tidak berdampak ke tenant lain.

### Kontrak 8: Aturan Baku Kapabilitas Jangkauan Data Modul (`ScopeCapability` & `DataScope`)
Setiap modul baru **WAJIB** mendeklarasikan salah satu dari dua kapabilitas jangkauan data (`ScopeCapability`):

#### 1. `ScopeCapability.GLOBAL_ONLY` (Kolektif / Shared Factory)
- **Karakteristik**: Digunakan untuk modul yang datanya merupakan aset bersama pabrik dan tidak logis dipartisi per individu pembuat.
- **Daftar Modul Contoh**: Bahan Baku Gudang (`INVENTORY`), Spesifikasi BOM (`TECH_PACK_BOM`), Kalkulasi HPP (`COSTING_HPP`), Jadwal Mesin (`PRODUCTION_MRP`), Inspeksi QC (`QUALITY_CONTROL`), Packing & Surat Jalan (`FULFILLMENT`).
- **Aturan Opsi Wewenang**:
  - Pilihan scope **otomatis terkunci 100% pada `DataScope.ALL_TENANT_DATA`** ("Seluruh Data Pabrik").
  - Opsi *Data Sendiri* (`OWN_DATA_ONLY`) dan *Data Bawahan* (`SUBORDINATE_DATA`) **TIDAK TERSEDIA** (disembunyikan dari UI modal wewenang) untuk mencegah kesalahan staf yang mengira sistem rusak saat data kain/mesin kosong.
  - Tampilan UI: Menampilkan badge informatif tunggal `🌐 Seluruh Pabrik (Shared)`.
- **Proteksi Domain**: Otomatis dilindungi oleh method `ModuleAccessConfig.sanitizeFor(module)` yang memaksa scope kembali ke `ALL_TENANT_DATA` jika ada request payload ilegal.

#### 2. `ScopeCapability.HIERARCHICAL` (Hirarkis / Per-Karyawan & Tim)
- **Karakteristik**: Digunakan untuk modul transaksional di mana dokumen dimiliki oleh staf pembuat (*maker*) dan diawasi oleh kepala divisinya (*checker/leader*).
- **Daftar Modul Contoh**: Pelanggan & Prospek Sales (`CRM_SALES`), Order Sampling Desain (`SAMPLING_ORDER`), Catatan Kerja Operator (`OPERATOR_EXEC`).
- **Aturan Opsi Wewenang**:
  - Tersedia **3 pilihan lengkap** yang dapat dipilih oleh admin pabrik:
    1. `DataScope.OWN_DATA_ONLY` ("Data Sendiri"): Pengguna hanya melihat dokumen miliknya sendiri.
    2. `DataScope.SUBORDINATE_DATA` ("Data Bawahan"): Pengguna melihat data miliknya dan seluruh staf bawahannya di divisi yang sama.
    3. `DataScope.ALL_TENANT_DATA` ("Semua Data"): Pengguna melihat seluruh data lintas cabang/pabrik.
  - Tampilan UI: Menampilkan segment selector 3 tab `[Data Sendiri | Data Bawahan | Semua Data]`.

---

## 4. Checklist Verifikasi Developer Sebelum Merge (Definition of Done)

- [ ] Apakah modul telah menetapkan `ModuleArchetype` yang tepat sehingga sepadan dengan modul sejenis?
- [ ] Apakah tipe data Input Port dan Output Port telah terdokumentasi dan kompatibel?
- [ ] Apakah modul mendukung parameter kustom per tenant tanpa perlu mengubah kode `core`?
- [ ] Jika memproses kain titipan, apakah status persediaan ditandai `CONSIGNED_CLIENT_MATERIAL` (Rp 0 di neraca)?
- [ ] **Apakah `scopeCapability` modul sudah ditentukan dengan benar?**
  - Jika data kolektif pabrik (kain/HPP/mesin) ➔ set `ScopeCapability.GLOBAL_ONLY` (hanya 1 opsi: Semua Data).
  - Jika data transaksi perorangan/sales/operator ➔ set `ScopeCapability.HIERARCHICAL` (tersedia 3 opsi: Sendiri / Bawahan / Semua Data).
- [ ] Apakah modul telah diuji berjalan pada alur bawaan (FOB/CMT/D2C) maupun alur custom hasil utak-atik (*puzzled*)?
- [ ] Apakah modul menyertakan dokumentasi pengajaran (*teaching*) di `docs/teaching/`?
