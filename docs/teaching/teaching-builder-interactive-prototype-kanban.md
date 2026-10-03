# 🎓 Modul Pembelajaran: Prototype Interaktif Berbasis Spec — Kanban yang Bisa Dipindah (`/builder/prototype`)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Spec-as-data, reducer murni, state machine, adaptor kompatibilitas, gestur drag di Compose Multiplatform
> **Prasyarat**: Kotlin data class/sealed interface, state hoisting Compose, dasar DDD repo ini
> **Referensi Task**: [TRD-PLAT-003](../trd/TRD-PLAT-003-interactive-prototype.md), tahap 1

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Prototype lama menggambar `sampleRows: List<Map<String,String>>` apa adanya. Kanban hanyalah `groupBy("Kolom")`; `onClick = {}`. Prospek melihat *gambar* aplikasi, bukan aplikasi. Selain itu baris tak bertipe dan tak ber-id, sehingga "pindah kartu" tidak punya makna yang bisa divalidasi, dan kolom kosong tidak pernah tampil (kolom hanya muncul bila punya kartu).

**Analogi.** Lovable menyerahkan kunci bengkel: AI boleh memahat komponen apa pun. Kita menyerahkan **katalog lego** dan buku aturan: AI hanya boleh merangkai blok yang sudah kita sahkan. Hasilnya lebih sempit, tetapi selalu sesuai standar dan bisa dilanjutkan jadi modul asli.

**Hasil akhir.** Kanban Sampling/Jadwal Potong/Lini Jahit di `/builder/prototype` bisa diseret antar kolom, ditolak bila melanggar alur (mis. `Baru → Selesai`), dan punya jalur ketuk-lalu-pilih tanpa drag.

---

## 🧭 2. "Start dari Mana?" — Order of Operations

1. **Model dulu, UI belakangan** (`core/domain/prototype/EntitySpec.kt`): apa itu field, entitas, state machine. UI yang ditulis sebelum model akan menyimpan aturan di Composable.
2. **Spec koheren di konstruktor** (`PrototypeSpec.kt`): spec ngawur tidak pernah terbentuk.
3. **Store immutable + seed tervalidasi** (`PrototypeStore.kt`).
4. **Reducer murni** (`PrototypeReducer.kt`): `(spec, store, aksi) → Result<store>`. Semua aturan pindah-kartu ada di sini, jadi bisa dites tanpa UI.
5. **Adaptor** (`InteractiveScreen.kt`): menurunkan spec dari baris lama supaya draf lama ikut hidup.
6. **Data pack** (`GarmentScreenSuggestions.kt`, `KanbanHints`): urutan kolom dan transisi adalah data vertikal.
7. **Kabel**: `InteractiveScreenCodec` (server → klien), `WidgetRegistry.interactiveFor`, field `interactive` di `DiscoveryRoutes`.
8. **UI** (`InteractiveKanbanState.kt`, `InteractiveKanban.kt`) dan `PrototypeRenderer` memilih interaktif vs statis.

---

## 🧱 3. Bedah Kode

### Blok A — Spec yang menolak dirinya sendiri bila salah
```kotlin
data class PrototypeSpec(val entities: List<EntitySpec>, val screens: List<ScreenSpec>) {
    init {
        screens.forEach { screen ->
            val entity = requireNotNull(entities.firstOrNull { it.id == screen.entityId }) { ... }
            screen.kanban?.let { k -> require(k.columns.all { it in group.options }) { ... } }
```
Validasi di konstruktor (aturan Value Object repo): tidak ada "spec setengah sah" beredar. Kolom kanban di luar opsi ENUM langsung gagal, bukan dibiarkan tampil salah.

### Blok B — Reducer: transisi ditolak dengan pesan, bukan dibulatkan
```kotlin
val machine = entity.stateMachine?.takeIf { it.field == a.field }
if (machine != null) require(machine.allows(from, a.value)) { "'${field.label}' tidak boleh pindah dari $from ke ${a.value}" }
```
`Result.failure` membawa pesan yang langsung layak ditampilkan. Tidak ada fallback senyap (tenant-variability Kontrak 4).

### Blok C — Adaptor: bentuk lama ditafsirkan ulang, bukan dibuang
```kotlin
val columns = hints?.columns ?: rows.map { it.getValue("Kolom") }.distinct()
```
Tanpa petunjuk pack, kolom diturunkan dari baris dan kartu bebas pindah. Dengan `KanbanHints`, kolom kosong ("Lini 3") ikut tampil dan transisi dijaga. Bila baris tak bisa jadi papan, hasilnya `null` dan renderer jatuh ke gambar statis.

### Blok D — Drag tanpa terpotong
```kotlin
.onGloballyPositioned { origin = it.positionInRoot() }
.graphicsLayer { if (dragging) { translationX = state.dragOffset.x; ... } }
```
Posisi pointer dihitung di koordinat *root* (`origin + grab + dragOffset`) lalu di-hit-test ke `boundsInRoot` tiap kolom. Kolom yang memegang kartu terseret diberi `zIndex(1f)` agar kartu tidak tenggelam di bawah kolom sebelah. Jalur ketuk → `DropdownMenu` "Pindah ke…" hanya menampilkan tujuan yang diizinkan mesin status.

---

## ⚖️ 4. Pendekatan yang Dipilih: The "Why"

| Pendekatan | Alternatif | Mengapa | Risiko alternatif |
|---|---|---|---|
| Spec data + runtime interpreter | AI menulis kode Compose bebas | Aman, sesuai Clay/RBAC/port, bisa di-handoff | Kode liar, tak teruji, tak bisa divalidasi |
| Reducer murni di `core` | Logika di ViewModel/Composable | Bisa dites tanpa UI; sesuai aturan DDD | Aturan bocor ke UI, sulit dites |
| State di memori sesi | Simpan tiap perpindahan ke DB | Ini mockup; seed deterministik | Tabel, migrasi, dan data sampah tanpa nilai |
| Adaptor `sampleRows` | Ganti semua data pack sekaligus | Draf lama tetap hidup (Strangler) | Layar lama rusak mendadak |

---

## ⚠️ 5. Jebakan Pemula

1. **Kolom hanya dari data.** `groupBy` tidak menghasilkan kolom kosong. *Solusi*: `KanbanConfig.columns` eksplisit.
2. **Menaruh aturan transisi di Composable.** *Solusi*: `StateMachine` di spec, ditegakkan reducer.
3. **Kartu terpotong saat diseret.** Menggeser dengan `offset` di dalam kolom yang meng-clip. *Solusi*: `graphicsLayer` + `zIndex` pada kolom induk.
4. **Mengandalkan drag saja.** Layar sentuh dan aksesibilitas butuh jalur ketuk.
5. **Lupa codec.** `kanbanHints` baru sampai ke pack terkodekan setelah ditambahkan di `DomainPackCodec` — test round-trip yang menangkapnya.

---

## 🧪 6. Cara Membuktikannya

- `PrototypeReducerTest`: fixture **non-garment** (tiket servis) — transisi sah, transisi terlarang ditolak, nilai di luar opsi, id kembar, spec & seed tak sah.
- `InteractiveScreenFactoryTest`: adaptor legacy, drop ke kolom kosong, transisi dari hints, round-trip codec, dan **setiap kanban pack garment interaktif** (entri baru tanpa hints sah tetap lolos; yang rusak gagal).
- `DomainPackCodecTest` (sudah ada) menangkap `kanbanHints` yang belum di-encode.
- **Belum terverifikasi dengan mata**: browser Playwright sedang dipakai proses lain saat dikerjakan. Gestur drag dan layout kolom 360dp wajib dilihat manual di `/builder/prototype` (login superadmin demo).

---

## 🏆 7. Tantangan Mandiri

- [ ] Tambahkan `WidgetKind.TABLE` interaktif: ubah status lewat dropdown, tetap lewat `PrototypeReducer`.
- [ ] Buat dasbor "Order aktif" yang menghitung jumlah kartu non-`Selesai` dari store yang sama.
- [ ] Tambahkan aksi `Create` ke UI (tombol "+ Kartu" per kolom) dan tes penolakan nilai tak sah.
