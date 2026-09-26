# 🎓 Modul Pembelajaran: Kanban CRM Leads bergaya Papan Tugas

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Compose Multiplatform, Clay Design System, hierarki visual kartu
> **Prasyarat**: `ClayCard`, `ClayBadge`, `Modifier.clayFlat` / `claySurface` (lihat `docs/teaching/teaching-claymorphism-design-system.md`)
> **Referensi Task**: Redesign Kanban `/crm-sales/leads` mengikuti referensi "Clayboard"

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalahnya**: kartu lead lama menaruh warna stage di *setiap* elemen — outline kartu, badge
  stage, tombol tambah — sementara kolomnya abu-abu. Akibatnya kartu ramai, dan informasi yang
  paling sering dicari sales (siapa klien, berapa nilai, sudah di-follow-up belum) tenggelam.
- **Analogi**: rak di supermarket. Warna kategori ada di **papan nama rak** (header kolom) dan
  cat raknya (tint kolom), bukan dicetak ulang di setiap kemasan. Kemasan (kartu) cukup netral
  dan fokus ke isinya.
- **Hasil akhir**: kolom punya header clay berwarna + gelembung jumlah, badan kolom ber-tint
  lembut warna stage, kartu netral dengan urutan **tag sumber → judul → deskripsi satu baris →
  footer pill**, dan aksi "+ Tambah lead" di dasar kolom.

---

## 🧭 2. "Start dari Mana?"

Ini murni perubahan presentation — tidak ada domain/use case yang disentuh.

1. **Langkah 0 — Petakan referensi ke token, bukan ke piksel.** Referensi memakai palet pastel;
   aturan kita (design-system-rules §1) mengambil *bentuknya*, bukan warnanya. Jadi: warna kolom
   = `stage.tint()` (Info/Success/Error), latar = `tint.copy(alpha = 0.07f)`.
2. **Langkah 1 — Kolom dulu (luar ke dalam).** Header, tint, footer "+ Tambah" di
   `CrmKanbanColumn.kt`. Kartu baru bisa dinilai ukurannya setelah wadahnya jadi.
3. **Langkah 2 — Kartu.** Susun ulang hierarki di `CrmKanbanCard.kt`.
4. **Langkah 3 — Pecah yang bukan tanggung jawab kartu.** Dropdown pindah-stage dipindah ke
   `LeadStageMenu.kt` (kartu turun 459 → 314 baris, di bawah soft limit 400).

---

## 🧱 3. Bedah Blok per Blok

### Blok A: Tint kolom sebagai sinyal drop-target

```kotlin
.clayFlat(
    shape = ClayShapes.Panel,
    background = tint.copy(alpha = if (isDropTarget) 0.18f else 0.07f),
    outline = if (isDropTarget) tint else tint.copy(alpha = 0.30f),
    borderWidth = ClayBorder.Medium
)
```
- State (diam vs. jadi target drop) dibedakan lewat **warna/alpha**, ketebalan tetap `Medium`
  — Kontrak 8 design system.
- Banner "Lepas kartu…" yang dulu berupa kotak terpisah kini cukup menggantikan teks subjudul,
  jadi tinggi kolom tidak meloncat saat drag dimulai.

### Blok B: Header kolom = `claySurface` berwarna

```kotlin
.claySurface(
    shape = ClayShapes.Button,
    background = tint,
    outline = WeMadeColors.Outline,
    offset = ClayOffset.Small,
    borderWidth = ClayBorder.Medium
)
```
- Header adalah satu-satunya elemen yang memakai warna stage **penuh**, sehingga mata langsung
  tahu "ini kolom apa" tanpa membaca.
- Judul pakai `MaterialTheme.typography.titleMedium` (Fredoka) — bukan `fontSize` mentah — dan
  `weight(1f, fill = false)` + ellipsis agar gelembung jumlah tidak terdorong keluar (Kontrak 13).

### Blok C: Footer kartu memakai `ClayBadge`, bukan pill buatan tangan

```kotlin
ClayBadge(
    text = if (hasActivities) "${lead.activityCount}" else "Follow-up",
    tint = activityTint,
    leading = { IconChat(Modifier.size(10.dp), color = activityTint) },
    modifier = Modifier.clickable(...) { onOpenActivities(lead) }
)
```
- Kode lama punya dua blok `Row + clayFlat(Pill) + padding + Icon + Text` yang identik (WhatsApp
  & aktivitas). `ClayBadge` sudah menerima `leading` + `modifier`, jadi tidak perlu komponen baru.
- Nilai deal dipendekkan (`Rp 125 jt`, `Rp 4,5 M`) lewat `formatRupiahCompact` karena pill di
  kolom ~300dp tidak muat `Rp 125.000.000`. Nilai lengkap tetap ada di total kolom & inspector.

---

## ⚖️ 4. Keputusan & Alasannya

| Keputusan | Alternatif | Kenapa ini |
|---|---|---|
| Kartu netral (outline `Outline`), warna di kolom | Outline kartu per stage (lama) | Warna diulang 3× per kartu = kebisingan; kolom sudah membawa konteks |
| Tombol pindah-stage bulat kecil | Badge stage besar dengan chevron | Stage sudah terbaca dari kolom; tombol tetap perlu sebagai alternatif drag (keyboard) |
| Palet WeMade + alpha | Palet pastel referensi | design-system-rules §1: sinyal hijau/amber/merah tidak boleh diredam |

---

## ⚠️ 5. Jebakan Pemula

1. **Menyalin hex pastel dari mockup.** Hasilnya literal `Color(0xFF…)` di file fitur (Kontrak 1).
   Turunkan dari token dengan `.copy(alpha = …)`.
2. **`fillMaxSize()` untuk empty state di dalam `Column` yang juga punya footer.** Footer "+
   Tambah" akan terdorong keluar. Solusinya: bungkus area isi dengan `Box(Modifier.weight(1f))`.
3. **Membiarkan dropdown hidup di dalam kartu.** Kartu jadi 450+ baris dan state menu bercampur
   dengan state drag. Pecah per tanggung jawab, bukan per baris.

---

## 🧪 6. Cara Membuktikan

- Kompilasi: `./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs :app:shared:jvmTest`.
- **Lihat dengan mata** di `/crm-sales/leads`: kolom kosong (empty state di tengah, "+ Tambah" di
  dasar), kartu dengan judul panjang (maks. 2 baris), lead tanpa WhatsApp / tanpa nilai, dan
  drag kartu antar kolom (tint kolom menebal, subjudul berganti).

---

## 🏆 7. Tantangan Mandiri

- [ ] Tampilkan avatar bertumpuk (owner + kolaborator) seperti referensi — di mana data kolaborator seharusnya berasal?
- [ ] Tambahkan progress bar tipis di kartu Qualified berdasarkan kelengkapan field wajib.
- [ ] Pola "header kolom berwarna + gelembung jumlah" kini ada di CRM; kalau Sewing Kanban butuh yang sama, itu pemakaian kedua — kapan harus diangkat ke `designsystem/`?

---

## ➕ Lanjutan: Stage `FOLLOW_UP` & Pintu Masuk Tunggal

**Masalah**: lead yang sudah dihubungi sales bercampur dengan inquiry mentah di kolom New Lead,
sehingga tidak kelihatan mana yang belum disentuh sama sekali.

**Perubahan**:
1. **Domain dulu** — `LeadStage.FOLLOW_UP("Follow Up")` disisipkan di antara `NEW_LEAD` dan
   `QUALIFIED` (`CrmLeadValueObjects.kt`). `fromCode` juga menerima alias `CONTACTED` /
   `IN_PROGRESS`. Tidak perlu migrasi: kolom `stage` bertipe teks tanpa CHECK constraint.
2. **Demosi digeneralisasi** — `DemoteQualifiedLeadUseCase` sekarang menerima
   `target = NEW_LEAD | FOLLOW_UP`. Tanpa ini, menyeret kartu Qualified → Follow Up akan lolos
   lewat `UpdateLeadStageUseCase` biasa dan **deal-nya tertinggal hidup tanpa lead Qualified**.
   Route `POST …/stage` memakai jalur demosi untuk kedua target itu.
3. **Satu pintu masuk** — "+ Tambah" hanya ada di New Lead (board, mobile, dialog). Qualified
   hanya bisa dicapai dengan memindahkan kartu, karena itulah yang memicu `QualifyLeadUseCase`
   (kontak + deal dibuat dalam satu transaksi). Dialog tambah lead kini menawarkan
   `Inquiry | Follow Up`, bukan `Qualified`.
4. **Board menjadi loop** — `LeadStage.entries.forEach { CrmKanbanColumn(...) }` menggantikan
   tiga blok salin-tempel, jadi stage berikutnya tidak perlu menyentuh board.

**Jebakan**: menambah entri enum membuat setiap `when (stage)` gagal kompilasi. Itu *fitur* —
compiler menunjukkan semua tempat yang harus memutuskan perilaku stage baru. Jangan menambal
dengan `else ->`, karena cabang itu akan diam-diam menelan stage berikutnya juga.
