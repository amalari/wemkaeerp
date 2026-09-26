# Teaching — Thumbnail Mockup Kiri + Toggle Depan/Belakang di Kartu Kanban Sampling

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform Row layout, `rememberMockupBitmap` keyed recomposition, `ClayChoiceChip`, slot domain `mockupFrontKey`/`mockupBackKey`
> **Prasyarat**: Paham dasar Compose state hoisting, dan aliran data `SamplingOrder` dari server ke kanban.

---

## 1. Start dari Mana? (Order of Operations)

Kalau kamu diminta memperbaiki tampilan gambar di kartu kanban dari nol, urutannya:

1. **Temukan komponen kartunya** — `SamplingKanbanCard.kt` di
   `app/shared/src/commonMain/.../presentation/sampling/components/`. Board kanban hanya merakit
   kolom; kartulah yang merender isi.
2. **Temukan kontrak datanya di domain** — jangan pernah menebak format data dari UI.
   Di `core/.../domain/sampling/SamplingOrder.kt` sudah ada dua slot eksplisit:
   `mockupFrontKey` dan `mockupBackKey` (bentuk `front:<key>` / `back:<key>` di
   `knitSpec.mockupImageUrls`). Artinya: *toggle depan/belakang tidak butuh perubahan backend
   sama sekali* — datanya sudah ada.
3. **Temukan cara gambar dimuat** — `rememberMockupBitmap()` di
   `presentation/deal/components/MockupImageLoader.kt` menerima referensi (`data:` base64 atau
   presigned URL) dan mengembalikan `ImageBitmap?` secara async.
4. **Cek katalog design system dulu** — toggle pilihan tunggal sudah ada komponennya:
   `ClayChoiceChip`. Jangan menulis chip sendiri (Aturan Tiga Kali).
5. **Baru tulis layout**: `Row` → thumbnail kiri → kolom chip di kanan.

## 2. Bedah Kode Blok per Blok

### Blok A: State tampilan

```kotlin
var showBack by remember(order.id, backKey) { mutableStateOf(false) }
val showingBack = showBack && backKey != null
val bitmap = rememberMockupBitmap(if (showingBack) backKey else frontKey)
```

- `remember(order.id, backKey)` — state di-reset kalau kartu sekarang menampilkan order lain,
  atau kalau slot belakang tiba-tiba ada/hilang. Kunci `remember` adalah "kapan state ini
  dibuang dan dibuat ulang".
- `showingBack = showBack && backKey != null` — pertahanan: meskipun state `showBack` tertinggal
  `true` lalu foto belakang dihapus tenant, tampilan jatuh kembali ke depan, bukan gambar kosong.

### Blok B: Thumbnail di kiri

```kotlin
Box(modifier = Modifier.size(64.dp).clip(ClayShapes.Tile).background(WeMadeColors.SurfaceMuted))
```

- Urutan modifier penting: `size` → `clip` → `background` berarti warna latar ikut terpotong
  bentuk tile. Kalau `background` sebelum `clip`, sudutnya kotak.
- **Kenapa 64dp persegi tidak "kepotong"?** Mockup diunggah lewat cropper 1:1 di sisi klien
  (lihat `MockupCropDialog`), jadi rasio gambarnya sudah persegi — `ContentScale.Crop` di tile
  persegi memotong nol piksel. Masalah lama bukan `Crop`-nya, tapi **rasio kontainernya**:
  full-width × 80dp memaksa gambar persegi dipangkas atas-bawah.

### Blok C: Toggle Depan/Belakang

```kotlin
if (backKey != null) {
    Column {
        ClayChoiceChip(text = "Depan", selected = !showingBack, onClick = { showBack = false })
        ClayChoiceChip(text = "Belakang", selected = showingBack, onClick = { showBack = true })
    }
}
```

- Chip hanya muncul bila foto belakang benar-benar ada — tidak ada tombol mati yang membingungkan.
- `ClayChoiceChip` sudah mengurus state visual terpilih (isian + kedalaman; outline tetap,
  Kontrak 8 design system).

## 3. Technology & Approach ("The Why")

| Keputusan | Alternatif yang ditolak | Risiko bila pakai alternatif |
|---|---|---|
| Thumbnail persegi kiri | Tetap full-width, ganti `Crop` → `Fit` | `Fit` menampilkan gambar utuh tapi menyisakan ruang kosong; kartu kanban jadi tinggi-variabel dan board terlihat berantakan |
| `ClayChoiceChip` dari design system | Dua `ClayButton` kecil / `Text` klikable | Duplikasi pola pil pilihan (sudah 4 salinan lama sebelum komponen ini ada); melanggar Aturan Tiga Kali |
| Baca `order.mockupBackKey` dari domain | Simpan pilihan tampak di ViewModel | Slot tampak adalah **data domain** (menentukan pola depan vs belakang saat produksi), bukan state UI; domain satu sumber kebenaran |
| `rememberMockupBitmap(reference)` berganti key | Preload dua bitmap sekaligus | Dua request HTTP per kartu × 16 kartu = 32 koneksi; lazy load hanya memuat tampak yang dilihat |

## 4. Jebakan Pemula (Common Pitfalls)

1. **`remember { }` tanpa key** — pindah kartu (recycle di LazyColumn), `showBack` lama ikut
   terbawa ke order lain. Selalu kunci dengan identitas data, bukan posisi.
2. **Menaruh `clip` setelah `background`** — sudut tile jadi kotak tajam meski `ClayShapes.Tile`
   dipasang.
3. **Menampilkan chip saat `backKey == null`** — tombol tanpa fungsi adalah bug UX; domain
   menjamin foto belakang itu opsional.
4. **Menulis literal warna placeholder** — latar thumbnail wajib token
   (`WeMadeColors.SurfaceMuted`), bukan `Color(0xFF...)`. Design system mengaudit literal.
5. **Lupa `IconInbox` placeholder saat loading** — tanpa itu kartu "melompat" saat bitmap tiba;
   Box berlatar muted + ikon menjaga tinggi layout stabil sejak frame pertama.

## 5. Verifikasi & Tantangan Mandiri

**Cara menguji:**

1. Jalankan web app, buka `/sampling-order`.
2. Kartu SPK dengan satu mockup → hanya thumbnail persegi kiri, tanpa chip.
3. Kartu SPK dengan dua mockup (depan + belakang) → chip `Depan`/`Belakang` muncul; klik
   `Belakang`, gambar berganti dan chip terpilih pindah.
4. Drag kartu → placeholder "Memindahkan kartu…" tetap berfungsi (klik chip tidak boleh
   memicu `onSelectOrder` — `clickable` anak mengonsumsi event sebelum induk).

**Verifikasi kompilasi:**

```bash
./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs
```

> Catatan kondisi repo saat tugas ini: `:app:shared:compileAndroidMain` gagal karena referensi
> Skia di `MockupCropDialog.kt` (pre-existing, dikonfirmasi lewat `git stash`), dan
> `CamProgramTabMappingTest` gagal karena WIP task lain. Keduanya di luar lingkup perubahan ini.

**Tantangan mandiri:**

- Tambahkan fallback: bila `backKey` ada tapi bitmap-nya gagal dimuat, tampilkan badge kecil
  "Belakang gagal dimuat" alih-alih placeholder inbox.
- Angkat pola "thumbnail + pemilih tampak" ke `designsystem/` bila pola ini muncul ≥3 kali
  (mis. nanti di lembar detail SPK dan kartu produksi massal).
