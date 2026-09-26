# 🎓 Modul Pembelajaran: Bugfix Alur Proses Sampling — Tombol X Tak Terlihat, Tombol + Hilang, Penomoran Tidak Bergeser

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, kontras warna (dark-on-dark), state keyed-by-ID untuk drag-and-drop, komposisi list dinamis, design token
> **Prasyarat**: Paham dasar Compose (recomposition, slot API), pernah membaca `ProcessFlowAdjusterPanel.kt`
> **Referensi Task**: Bug report "di sampling detail alur proses, ketika ditambahkan process di antara rajut dan linking"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah Nyata**: Tiga bug muncul sekaligus begitu satu proses opsional (misal *Bordir Komputer*) disisipkan di antara dua tahap wajib (*Rajut Turun Mesin* → *Linking & Tambahan*):

1. **Tombol X di chip proses tidak terlihat** — operator tidak bisa menghapus proses yang salah sisip.
2. **Tombol `+` di antara Rajut dan Bordir menghilang** — celah penyisipan "termakan" oleh chip yang baru saja disisipkan lewat celah itu sendiri.
3. **Penomoran tidak bergeser** — Linking masih bernomor 4 padahal secara urutan ia kini tahap ke-5.

**Analogi Sederhana**: Bayangkan rangkaian kereta. Dulu antar gerbong hanya ada satu kopling (`+`). Saat menyelipkan gerbong baru, kita lupa menambah kopling di depannya — gerbong baru "menempel paksa" ke gerbong sebelumnya. Dan nomor gerbong dicat permanen di pabrik, jadi menyelipkan gerbong membuat urutannya bohong.

**Hasil Akhir**: `Rajut(3) → + → Bordir(4) → + → Linking(5) → …` — setiap sambungan punya tombol `+`, setiap simpul punya nomor urut yang benar, dan tombol X di chip proses terbaca jelas.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan

Kalau harus menulis ulang dari nol:

1. **Langkah 0: Pahami struktur render baris alur.** `ProcessFlowAdjusterPanel` merakit `StagePill` → proses berjangkar → `ProcessFlowGap` per tahap. Semua bug lahir dari struktur ini — bukan dari ViewModel maupun data.
2. **Langkah 1: Perbaiki kontras tombol X (murni token).** `ProcessFlowChips.kt` — teks `x` memakai `OnSurface` (slate-800 gelap) di atas latar `OutlineSoft` (slate-700 gelap).
3. **Langkah 2: Perbaiki kunci zona drop.** Satu tahap kini punya **lebih dari satu celah**; `ProcessFlowDragState.gapBounds` yang berkunci `SamplingPipelineStage` akan saling menimpa. Ganti kuncinya ke `slotId: String` unik.
4. **Langkah 3: Selipkan celah di antara simpul.** Di panel, render `ProcessFlowGap` **sebelum setiap** proses berjangkar (dan tetap satu setelah yang terakhir).
5. **Langkah 4: Penomoran berjalan.** Ganti `StagePill(step = index + 1)` dengan counter berjalan yang juga di-increment oleh tiap proses opsional; bubble nomor ditambahkan ke `PlacedProcessChip` lewat slot `leading` milik `ClayBadge`.
6. **Langkah 5: Kompilasi & lihat dengan mata.** Bug #1 adalah bug kontras — kompilasi hijau tidak menangkapnya.

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Bug #1 — Kontras, bukan layout

```kotlin
// ❌ SEBELUM — gelap di atas gelap: hanya lingkaran gelapnya yang terlihat
Box(Modifier.background(WeMadeColors.OutlineSoft)) {   // #475569 (slate-700)
    Text("x", color = WeMadeColors.OnSurface)          // #1E293B (slate-800)
}

// ✅ SESUDAH — teks terang di atas latar gelap, satu keluarga dengan bubble nomor StagePill
Text("x", color = WeMadeColors.Surface)                // putih
```

**Mental model**: kontras adalah *kontrak*, bukan selera. Nilai latar dan nilai teks harus dibaca berpasangan — memilih keduanya secara terpisah dari dua token berbedalah yang menciptakan bug ini. Perhatikan keduanya adalah token sah dari `WeMadeColors`; tidak ada literal warna baru, jadi lint design-system tidak akan menangkapnya. **Bug kontras yang menggunakan token tetap adalah bug.**

### 3.2 Bug #2 — Satu kunci untuk banyak celah

Struktur lama merender tepat **satu** `ProcessFlowGap` per tahap, setelah semua proses berjangkar. Saat Bordir disisipkan setelah Rajut, susunannya menjadi:

```
Rajut → [Bordir] → +     ← + pindah ke belakang Bordir; + di depan Bordir tidak pernah ada
```

Perbaikannya di panel — celah dirender **interleaved**:

```kotlin
anchored.forEachIndexed { procIndex, process ->
    ProcessFlowGap(slotId = "${stage.name}-$procIndex", …)   // celah sebelum tiap proses
    PlacedProcessChip(process, step = step++, …)
}
ProcessFlowGap(slotId = "${stage.name}-final", legs = …)      // celah terakhir: rumah bagi leg
```

Konsekuensinya: satu tahap bisa punya 2+ zona drop dengan **jangkar sama**. `ProcessFlowDragState` lama memakai `Map<SamplingPipelineStage, Rect>` — registrasi celah kedua **menimpa** yang pertama, dan hit-test drop hanya mengenali satu kotak. Kuncinya diganti ID unik, dengan jangkar disimpan sebagai nilai:

```kotlin
private val gapBounds = mutableStateMapOf<String, Pair<Rect, SamplingPipelineStage>>()

fun registerGap(slotId: String, anchor: SamplingPipelineStage, bounds: Rect) {
    gapBounds[slotId] = bounds to anchor
}

private fun updateHoveredGap() {
    hoveredGapId = gapBounds.entries
        .firstOrNull { it.value.first.contains(dragPointerWindowPos) }?.key
}

fun onDragEnd(onCommit: …) {
    val anchor = hoveredGapId?.let { gapBounds[it]?.second }  // ID → jangkar
    …

## 🏗️ 4. "The Why" — Keputusan Arsitektural

| Keputusan | Alternatif yang ditolak | Kenapa |
|---|---|---|
| Kunci zona drop = `slotId` string | Kunci `Pair<Stage, Int>` (tahap + indeks) | String `${stage.name}-$n` bisa dibaca saat debug dan tidak butuh tipe data baru; peran tetap disimpan sebagai nilai |
| Leg pengiriman tetap hanya di celah **terakhir** | Gambar leg di tiap celah | Leg berangkat dari *simpul terakhir* tahap (`lastNodeAt`); menduplikasinya ke celah perantara berarti menggambar satu perpindahan dua kali |
| Celah perantara selalu `PlainGapSlot` (tanpa leg) | Kirim `legs` per proses | Sama seperti di atas — `legsLeaving` dikunci ke simpul asal, dan asal perpindahan lintas gedung adalah ujung kelompok tahap |
| Bubble nomor via slot `leading` `ClayBadge` | Komponen baru `NumberedBadge` | Aturan Tiga Kali: baru ada 2 pemakai bubble nomor (StagePill + chip proses). Naikkan ke designsystem saat pemakai ke-3 muncul |
| Tidak menyentuh ViewModel/server | Simpan nomor di entity | Nomor adalah **turunan presentasi** dari urutan render, bukan data. Menyimpannya di `TenantOptionalProcess` akan menciptakan state ganda yang pasti drift |

**Jebakan yang dihindari**: memodelkan penomoran sebagai data. Jika nomor disimpan, setiap sisip/hapus = migrasi nomor semua baris sesudahnya, di klien *dan* server. Nomor harus selalu dihitung saat render.

---

## ⚠️ 5. Jebakan Pemula (Common Pitfalls)

1. **Kontras token-vs-token tidak dicek.** Dua token sah bisa tetap menghasilkan pasangan tak terbaca. Saat memilih warna teks, selalu lihat *latar tempat ia mendarat*.
2. **Peta dengan kunci peran.** Begitu satu entitas logis (tahap) bisa punya banyak instance fisik (celah), kunci berbasis peran langsung rusak — dan rusaknya diam-diam (overwrite, bukan crash).
3. **Lupa mengganti semua pemakai field yang di-rename.** `hoveredGap` dipakai di 3 file (drag state, ghost, gap slot). `grep -rn "hoveredGap\b"` adalah langkah wajib sebelum kompilasi.
4. **Menilai bug UI dari kompilasi.** Bug #1 dan #3 lolos kompilasi sepenuhnya. Definition of Done repo ini mewajibkan "dilihat dengan mata" justru untuk kelas bug ini.

---

## ✅ 6. Verifikasi

- **Kompilasi**: `:app:shared:compileKotlinJvm`, `compileKotlinWasmJs`, `compileKotlinJs`, `jvmTest` — hijau. (`compileAndroidMain` gagal karena `MockupCropDialog.kt` yang sudah ter-commit memakai API Skia di commonMain — utang pra-eksisting, di luar cakupan bugfix ini.)
- **Manual** (yang harus dilihat dengan mata):
  1. Sisipkan proses via `+` di antara Rajut dan Linking → `+` di depan chip baru tetap ada.
  2. Chip proses menampilkan bubble nomor; tahap sesudahnya bergeser satu nomor.
  3. Tombol `x` di chip terbaca jelas (putih di atas slate gelap) dan berfungsi menghapus.
  4. Drag chip proses / palet ke celah perantara → hanya satu tombol `+` yang menyala; drop menyisipkan ke jangkar yang benar.

---

## 🏆 7. Tantangan Mandiri

- [ ] **Tantangan 1**: Dua proses opsional berjangkar ke tahap yang sama tidak punya urutan di antara mereka sendiri (data model belum menyimpannya). Rancang bagaimana menambah urutan intra-jangkar tanpa menyimpan nomor absolut — petunjuk: indeks relatif atau timestamp.
- [ ] **Tantangan 2**: Bubble nomor kini ada di 2 tempat dengan gaya mirip. Kalau fitur ke-3 butuh bubble nomor, angkat `NumberedBubble` ke `presentation/designsystem/` — pastikan ia buta domain (menerima `Int` + `Color`, bukan entity).
- [ ] **Tantangan 3**: Tulis test JVM murni untuk fungsi penomoran — ekstrak dulu perhitungan `(stage, anchored) → List<Step(label, number)>` dari composable agar bisa diuji tanpa Compose.

}
```

**Mental model**: *identitas ≠ peran*. Kunci peta harus menjadi **identitas** (slot unik), sedangkan jangkar tahap adalah **peran** (banyak slot bisa berbagi peran sama). Menjadikan peran sebagai kunci peta adalah sumber klasik bug "yang terakhir menang".

`hoveredGap` ikut diganti `hoveredGapId` — kalau tetap berbasis tahap, dua tombol `+` pada tahap yang sama akan menyala bersamaan saat drag, padahal yang dihover hanya satu.

---

