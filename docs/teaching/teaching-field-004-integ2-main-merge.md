# Integrasi FIELD-004 ke main setelah penyatuan tipe field

> Level: Junior–Mid. Topik: integrasi branch, kompatibilitas fixture, verifikasi KMP.
> Prasyarat: Git worktree dan Kotlin data class/enum.
> Task: periksa `integ2` pada `4c37646a`, perbaiki konflik sebelum merge ke main.

## 1. Masalah dan mental model

Git memeriksa apakah perubahan baris dapat digabung, bukan apakah nama tipe masih
sah. Dua branch dapat digabung tanpa marker konflik tetapi gagal dikompilasi karena
salah satunya sudah mengganti API. Kasus ini menyerupai dua dokumen yang bisa
disatukan, tetapi masih menggunakan kamus istilah berbeda.

`main` pada `15de18b0` sudah memuat penyatuan kosakata field. Branch integrasi
FIELD-004 masih memiliki tiga fixture test dengan tipe CRM lama. Saat pemeriksaan
dimulai, ketiga file sudah memiliki perubahan lokal yang menyesuaikan tipe tersebut.
Perubahan itu dipertahankan, ditinjau, dan dimasukkan dalam commit integrasi `09ed26d3`.

## 2. Urutan pengerjaan

0. Periksa `git worktree list`, status kedua worktree, dan perubahan lokal.
1. Coba Graphify untuk hubungan field FILE/RELATION; lanjutkan dengan diff terarah.
2. Jalankan `git merge-tree --write-tree main integration/field-004-on-main`.
   Hasil: tidak ada konflik teks. Simulasi hanya menulis objek Git, tidak memindahkan branch.
3. Gabungkan main ke `integ2` dengan `--no-commit --no-ff` agar dapat diperiksa dahulu.
4. Periksa tiga fixture terhadap `CrmFieldType` dan test gerbang FILE/RELATION.
5. Jalankan test awal, lalu commit hasil integrasi. Jalankan test ulang `--rerun-tasks`
   pada worktree yang bersih saat pengujian dimulai.
6. Setelah verifikasi selesai, majukan main dengan `--ff-only`. Jika main berubah
   lagi sebelum langkah ini, integrasi harus diperiksa ulang.

## 3. Bedah perubahan

### Fixture RELATION

```kotlin
CrmFieldType(FieldType.RELATION, targetResource = target)
```

`FieldType` berasal dari `domain.prototype`, yaitu kosakata bersama. Wrapper
`CrmFieldType` menambahkan parameter CRM seperti `targetResource`. Mengganti hanya
import tidak cukup karena constructor `FieldType.Relation(target)` sudah tidak ada.

### Fixture FILE

```kotlin
CrmFieldType(FieldType.FILE)
```

Field FILE tidak membutuhkan target relasi. Fixture tetap menguji penolakan ref
lintas record/tenant, tanpa mengubah status HTTP yang diharapkan.

### Factory fixture bersama

```kotlin
fun definition(id: String, key: String, type: CrmFieldType)
```

`FieldGateTestSupport` menerima tipe yang sama dengan `CustomFieldDefinition`.
Perubahan ini menyatukan kontrak input fixture dengan model domain, sehingga tidak
dibutuhkan overload atau adapter untuk tipe lama.

File yang dipertahankan dan dimasukkan ke commit:

- `CrmRelationTargetGateTest.kt`: RELATION memakai wrapper baru.
- `FieldFileRecordScopeTest.kt`: FILE memakai wrapper baru.
- `FieldGateTestSupport.kt`: parameter factory menerima `CrmFieldType`.

Ketiganya berukuran 131, 285, dan 136 baris setelah integrasi, di bawah hard limit.

## 4. Alasan keputusan

| Keputusan | Alasan |
|---|---|
| Integrasikan main ke branch terlebih dahulu | Verifikasi mencakup konfigurasi main terbaru tanpa memajukan main sebelum test |
| Pertahankan perubahan lokal yang relevan | Ketiga perubahan merupakan adaptasi fixture yang dibutuhkan oleh migrasi tipe |
| Pertahankan assertion keamanan | Kompatibilitas diperbaiki pada representasi tipe, bukan dengan melonggarkan gerbang |
| Fresh test setelah commit integrasi | Hasil UP-TO-DATE tidak cukup untuk membuktikan seluruh suite dijalankan ulang |
| Fast-forward main setelah verifikasi | Main menerima persis pohon kode yang sudah diuji |

## 5. Jebakan

- Merge tanpa konflik teks bukan bukti kompatibilitas API.
- Jangan memakai reset/checkout untuk menghilangkan perubahan lokal milik sesi lain.
- Jangan mengganti test 403 menjadi 200 demi membuat suite hijau.
- Build JVM tidak membuktikan keberhasilan JS/Wasm/iOS/Android.
- Commit konfigurasi main sudah teruji pada pekerjaan sebelumnya; perubahan itu
  tidak boleh hilang ketika membawa FIELD-004 masuk.

## 6. Verifikasi

Verifikasi fresh pada kode commit `09ed26d3` lulus: Gradle `BUILD SUCCESSFUL`
dalam 1m 34s, 44 task dieksekusi (menggunakan `--rerun-tasks`).

| Suite | Tests | Failures/errors | Skipped |
|---|---:|---:|---:|
| Core JVM | 1860 | 0 | 0 |
| Server | 797 | 0 | 5 |
| Shared JVM | 467 | 0 | 0 |

Total 3124 test terdaftar, 3119 lulus dan 5 skipped. Test gerbang khusus yang
fixture-nya disesuaikan ikut dijalankan: `CrmRelationTargetGateTest` 9 test dan
`FieldFileRecordScopeTest` 24 test, seluruhnya lulus tanpa skip.
Kompilasi shared JS, Wasm, iOS Simulator ARM64 dan aplikasi Desktop juga lulus.
Audit variabilitas: 0 temuan. Sinkronisasi konfigurasi: 56 output terverifikasi.
`git diff main --check` bersih. Dokumentasi ini ditambahkan setelah pengujian;
tidak ada perubahan kode setelah hasil fresh tersebut.

Perintah utama:

```bash
./gradlew :core:jvmTest :server:test :app:shared:jvmTest \
  :app:shared:compileKotlinWasmJs :app:shared:compileKotlinJs \
  :app:shared:compileKotlinIosSimulatorArm64 :app:desktopApp:compileKotlin \
  --rerun-tasks --console=plain
scripts/audit-variability.sh
scripts/sync-agent-config.sh --check
git diff main --check
```

SDK Android tidak ditemukan di lokasi standar maupun `ANDROID_HOME`/`ANDROID_SDK_ROOT`;
kompilasi Android belum diverifikasi. Perubahan integrasi tidak menyentuh UI, sehingga
tidak ada perubahan visual baru yang perlu dibandingkan. Pekerjaan FIELD-004 lanjutan
(B3 dan Track C) tetap mengikuti TRD; merge ini tidak mengklaim seluruh TRD selesai.

## 7. Latihan

- Bandingkan diff tiga fixture terhadap `4c37646a` dan jelaskan kenapa assertion
  keamanan tidak perlu diubah.
- Periksa laporan XML dan bedakan jumlah test dijalankan, skipped, serta failed.
- Di branch latihan, ubah signature sebuah interface lalu amati perbedaan antara
  keberhasilan merge Git dan hasil kompilasi Kotlin.
