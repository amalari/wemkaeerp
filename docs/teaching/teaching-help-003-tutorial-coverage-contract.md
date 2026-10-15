# 🎓 Modul Pembelajaran: Kontrak Cakupan Tutorial per Modul (PLAN-help-003)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Policy object murni, kontrak berbasis test (ratchet dua arah), frozen inventory, pola DomainPack
> **Prasyarat**: `ModuleTutorial` & `TutorialCatalog` (TRD-HELP-001), `DomainPack`/`ModuleId`, dasar `kotlin.test`
> **Referensi Task**: [`PLAN-help-003`](../../plannings/features/PLAN-help-003-tutorial-coverage-contract.md) · revisi [`TRD-HELP-001`](../../trd/features/TRD-HELP-001-module-tutorials.md) v0.5

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

- **Masalah nyata**: tutorial chat helper (`/help/ask`) hanya ada untuk 5 dari 15 modul garment.
  Tidak ada alarm apa pun — modul baru bisa lahir bertahun-tahun dan chat helper akan terus
  menjawab *"tidak menemukan tutorial"* untuknya, tanpa developer sadar. Itu **fallback senyap**
  versi fitur, seburuk codec yang jatuh ke `TEXT`.
- **Analogi**: ini seperti restoran yang punya menu 15 hidangan tapi buku resep hanya 5.
  Pelayan (chat helper) hanya bisa menjelaskan hidangan yang kebetulan ada resepnya, dan tidak
  ada daftar yang bilang hidangan mana yang belum punya resep. Kontrak ini adalah **daftar
  wajib resep**: setiap hidangan baru harus punya resep *sebelum* boleh masuk menu, atau
  tercatat resmi "resep menyusul" beserta alasannya.
- **Hasil akhir**: `TutorialCoverageTest` gagal di CI begitu ada modul tanpa tutorial dan tanpa
  pengecualian — keputusan "modul ini belum dibuatkan tutorialnya" menjadi **eksplisit dan
  tertulis**, bukan keadaan diam-diam.

## 🧭 2. "Start dari Mana?"

1. **Langkah 0: Uji Variabilitas.** Kontraknya (bukan isinya) sama untuk semua pack — ini
   konsep sistem → kode. Isi tutorial tetap data per pack (keputusan HELP-001 §0.4), tidak diubah.
2. **Langkah 1: Policy object, bukan endpoint.** Tidak ada route, tidak ada tabel. Policy
   murni di `core/.../domain/tutorial/TutorialCoverage.kt` yang bisa dipanggil siapa saja
   (test sekarang, mungkin CI report nanti) — pola persis `TutorialCatalog.violations()`.
3. **Langkah 2: Keputusan pengecualian jadi data di test.** Frozen exempt list hidup di
   `TutorialCoverageTest`, meniru `MenuInventoryTest.expectedShown` (module-integration-rules §5.7).
4. **Langkah 3: Test tenant kedua.** Fixture `ElearningPack` membuktikan kontrak menggigit di
   pack non-default (Kontrak 6 tenant-variability).

## 🧱 3. Bedah Blok per Blok Kode

### Blok A: Ratchet dua arah

```kotlin
when {
    reason != null && reason.isBlank() ->
        problems += "Modul ${module.id.value}: alasan Exempt kosong"
    covered && reason != null ->
        problems += "Modul ${module.id.value}: sudah punya tutorial, cabut Exempt(\"$reason\")"
    !covered && reason == null ->
        problems += "Modul ${module.id.value}: wajib minimal satu tutorial atau Exempt(alasan)"
}
```

- Tiga cabang ini sengaja simetris: **masuk** (modul baru wajib Covered/Exempt) dan **keluar**
  (Exempt wajib dicabut begitu Covered). Ratchet searah saja (hanya "tidak boleh bertambah")
  akan membuat exempt membusuk jadi salinan permanen dari daftar modul.
- Tidak ada `else` — semua kombinasi `covered × reason` terpetakan eksplisit
  (Kontrak 6 field-component: kompilator menjaga, bukan ingatan).

### Blok B: Exempt mati ditolak dari dua sisi

```kotlin
exempt.keys.filter { pack.module(it) == null }.forEach {
    problems += "Exempt ${it.value}: modul tidak ada di pack ${pack.code.value}"
}
```

- Daftar exempt bisa basi karena modul di-*rename* atau pindah pack. Tanpa cabang ini,
  entri basi akan mengaburkan isi daftar secara perlahan.
- Sumber kebenaran tunggal selalu `pack.modules` — exempt hanyalah opini yang harus cocok
  dengan kenyataan pack.

### Blok C: Frozen exempt list sebagai utang terdaftar

```kotlin
private val garmentExempt: Map<ModuleId, String> = listOf(
    GarmentModules.INVENTORY to "Tutorial belum ditulis (dicicil HELP-001)",
    ...
).toMap()
```

- Tiap entri adalah **utang bernama**: modul + alasan. Menambah entri baru = menambah utang
  baru, dan itu harus disengaja saat modul baru lahir — bukan kebetulan karena lupa tutorial.
- Test `coveredModuleWithExempt_mustRemoveExempt` memaksa daftar ini **menyusut**: begitu
  seseorang menulis tutorial untuk `INVENTORY`, ratchet mengeluh sampai entrinya dicabut.

## ⚖️ 4. The "Why" — Teknologi & Pendekatan

| Pilihan | Alternatif | Mengapa ini | Risiko alternatif |
|---|---|---|---|
| Policy murni di `core`, tanpa DB | Tabel `module_tutorial_coverage` + migrasi | Kontrak adalah keputusan kode; tidak perlu persistensi, RLS, atau endpoint | Migrasi + repository + route untuk data yang cuma dibaca test — biaya tak sebanding |
| Violations-list (pola `violations()`) | Exception di init / Boolean `isSatisfied()` | Satu pemanggilan mengembalikan SEMUA masalah — test bisa melaporkan lengkap, bukan satu-satu | Boolean menyembunyikan modul mana yang melanggar; exception menghentikan laporan pertama |
| Exempt di test, bukan di `ModuleDefinition` | Field `tutorialExemptReason` di pack data | HELP-001 mengunci `ModuleDefinition` dari perubahan; exempt adalah keputusan sementara developer, bukan kontrak pack | Mengubah definisi pack + codec untuk alasan yang idealnya cepat hilang |
| Tanpa endpoint baru | `GET /help/coverage-report` | Laporan coverage runtime belum dibutuhkan siapa pun; test CI sudah cukup | API yatim tanpa konsumen |

## ⚠️ 5. Jebakan Pemula

1. **Ratchet searah.** Hanya menolak modul baru tanpa tutorial → exempt lama jadi patung.
   Cabang `covered && reason != null` adalah pengikat agar daftar utang benar-benar dicicil.
2. **Membaca ulang id modul dari string pesan error.** Test `nonDefaultPack_contractBitesForEveryUncoveredModule`
   awalnya mem-*parse* id dari teks pelanggaran — rapuh. Bandingkan himpunan id modul dengan
   himpunan pelanggaran (lewat ukuran + keanggotaan), bukan mem-parse string.
3. **Menaruh kontrak di UI/runtime.** Kontrak ini milik CI. Kalau ditaruh di runtime, ia jadi
   log yang diabaikan — persis masalah yang mau dipecahkan.

## 🧪 6. Membuktikan Kodingan Bekerja

- `garmentPack_satisfiesCoverageContract` — baseline: garment bersih terhadap exempt list 10 modul.
- `uncoveredModuleWithoutExempt_isAViolation` — membuktikan cabang "wajib": kurangi satu exempt,
  harus muncul tepat satu pelanggaran.
- `coveredModuleWithExempt_mustRemoveExempt` — membuktikan ratchet keluar.
- `nonDefaultPack_contractBitesForEveryUncoveredModule` — Kontrak 6: pack non-default ikut diperiksa,
  bukan hanya garment.
- Bukti sesi ini: `:core:jvmTest` segar — **2159 tes, 0 gagal** (6 di antaranya milik kontrak ini).

## 🏆 7. Tantangan Mandiri

- [ ] Tulis satu `ModuleTutorial` untuk `INVENTORY` di `GarmentTutorials.kt`, lalu cabut entri
  exempt-nya. Test harus hijau kembali — tanpa menyentuh `TutorialCoverage.kt` sama sekali.
- [ ] Tambahkan satu modul dummy ke fixture pack test dan saksikan pesan pelanggarnya; di mana
  letak perbedaan pesannya untuk kasus "tanpa exempt" vs "alasan kosong"?
