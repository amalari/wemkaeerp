# 🎓 Modul Pembelajaran: Test Selektif Berbasis Perubahan (Change-Based Test Selection)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Gradle test filtering (`--tests`), pemetaan kode → test lintas modul, recall vs
> presisi dalam pemilihan test, kompatibilitas Bash 3.2 (macOS), bahaya "hijau palsu"
> **Prasyarat**: Dasar Gradle multi-modul, dasar Bash, dasar Kotlin/KMP test source set
> **Referensi File**:
> - [`tools/test-changed.sh`](file:///Volumes/amalari/Projects/wemade/tools/test-changed.sh)
> - [`dev.sh`](file:///Volumes/amalari/Projects/wemade/dev.sh)

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

### Masalah Nyata

Suite test repo ini sudah besar: **119 kelas** (66 di `core`, 23 di `app/shared`, 30 di `server`) dan
**730 test**. Menjalankan semuanya butuh **±50–70 detik**, dan itu terlalu lambat untuk sebuah loop
kerja: ubah satu baris → tunggu semenit → ubah lagi. Akibatnya yang paling sering terjadi bukan
"developer sabar menunggu", tapi **developer berhenti menjalankan test sama sekali**.

Sebaliknya, "jalankan test seadanya" juga berbahaya. Kalau kita hanya menjalankan test di folder yang
sama dengan file yang diubah, kita akan melewatkan hal seperti ini:

> Kami mengubah `core/.../TemplateGeometry.kt` (kanvas faktur). Yang ikut rusak justru **test di
> modul `server`** (`InvoiceTemplateSeedDecodeTest`), karena keduanya berbagi kontrak JSON yang sama.

Jadi tujuannya bukan "jalankan lebih sedikit test", tapi **jalankan tepat test yang punya alasan
untuk gagal** — termasuk alasan yang menyeberang modul.

### Analogi Sederhana

- **`--tests` Gradle** = menyebut nama murid tertentu, bukan memanggil seluruh kelas.
- **Pencarian referensi lintas modul** = "siapa saja yang menyebut nama orang ini?" Kalau kelas A
  berubah, siapa pun yang menyebut `A` di seluruh gedung (bukan hanya di ruangan yang sama) harus
  ikut diperiksa.
- **Daftar *foundational*** = berkas "jantung" seperti `MeasureCodec`. Bila jantung berubah, tidak
  ada gunanya menebak; periksa seluruh pasien.
- **Batas kata vs substring** = mencocokkan kata utuh `Mm10` akan melewatkan `marginMm10`. Untuk
  pemilihan test, ingatan yang lebih luas lebih baik daripada ketepatan yang buta.

### Hasil Akhir yang Diharapkan

```bash
./dev.sh test-changed            # perubahan belum di-commit → 20 detik, 5 kelas test
./dev.sh test                    # seluruh suite → bukti penuh sebelum push
```

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

1. **Langkah 0 — Ukur dulu, jangan menebak.** Buktikan filter Gradle benar-benar mempersingkat:
   `:core:jvmTest --tests '*TemplateRectMovementTest'` → **1,4 detik** vs 52 detik suite penuh.
2. **Langkah 1 — Pahami jebakan `--tests`.** Hasil test di `build/test-results/` **dibersihkan**
   setiap run terfilter, dan pola yang tidak cocok **menggagalkan build** (ini fitur, bukan bug).
3. **Langkah 2 — Tentukan peta modul → task test.** `core/src` → `:core:jvmTest`,
   `app/shared/src` → `:app:shared:jvmTest`, `server/src/test` → `:server:test`.
4. **Langkah 3 — Ekstraksi token yang aman.** Ambil nama berkas + deklarasi **top-level** saja
   (kolom 0). Kalau anggota kelas ikut diambil, token serigala seperti `x`, `y`, `width`, `align`
   akan mencocokkan hampir semua test.
5. **Langkah 4 — Pencarian referensi lintas modul** (substring, bukan batas kata).
6. **Langkah 5 — Daftar *foundational*** untuk berkas yang mustahil dipersempit dengan jujur.
7. **Langkah 6 — Jaring pengaman.** Tidak ada test yang cocok → jalankan modul penuh, jangan diam.
8. **Langkah 7 — Muat `.env`** sebelum memanggil Gradle (lihat §3.7), lalu sambungkan ke `dev.sh`.

---

## 🔬 3. Bedah Kode Blok per Blok

### 3.1 Fondasi: filter Gradle, dan buktinya

```bash
./gradlew --console=plain :core:jvmTest --tests 'com.eventverse.app.domain.invoicing.template.TemplateRectMovementTest'
# BUILD SUCCESSFUL in 1s   (5 test, 1 kelas)
```

Dua perilaku yang **wajib** diketahui sebelum membangun apa pun di atasnya:

**a. Folder hasil test dibersihkan tiap run terfilter.** Sesudah run di atas, isi
`core/build/test-results/jvmTest/` tinggal **satu** XML. Artinya: **jangan pernah** membaca folder itu
sebagai bukti "seluruh suite hijau" setelah menjalankan test selektif. Ini sumber "hijau palsu" yang
paling mudah terjadi.

**b. Pola yang tidak cocok = BUILD FAILED.** Sudah diuji:

```bash
./gradlew :core:jvmTest --tests '*TidakAdaTestSepertiIni*'   # BUILD FAILED in 816ms
```

Ini **fitur keselamatan**: salah tulis nama kelas tidak bisa diam-diam dianggap lulus.

### 3.2 Mengapa satu invokasi Gradle per modul

Gradle mengaitkan opsi `--tests` ke task yang **mendahuluinya** di baris perintah. Mencampur beberapa
task dalam satu invokasi membuat kepemilikan opsi itu tidak jelas, dan efek sampingnya terlihat pada
`:app:shared:jvmTest UP-TO-DATE` di uji kami: task kedua "dilewati" sehingga tidak mungkin dibedakan
antara "tidak ada test terpilih" dan "filter bocor ke task lain".

Karena setiap invokasi hanya **±1 detik** (configuration cache dipakai ulang), kesederhanaan itu
murah: satu invokasi per modul.

### 3.3 Ekstraksi token: pelajaran dari bug pertama

Versi pertama ekstraktor token mengambil **semua** nama deklarasi, termasuk anggota kelas:

```bash
# ❌ VERSI PERTAMA — ikut mengambil anggota kelas
grep -oE '^[[:space:]]*(modifiers)*(class|object|fun|val|var)[[:space:]]+[A-Za-z0-9_]+' "$file"
```

Hasilnya untuk `TemplateGeometry.kt`:

```
Mm10, plus, minus, times, div, compareTo, ZERO, TemplateRect, x, y, width, height,
right, bottom, translated, movedBy, PaperSize, TextAlign, ...
```

Token `x`, `y`, `width`, `height`, `align` muncul di **hampir semua** file test. Efeknya: pola
alternation `(x|y|width|height|align|…)` cocok ke seluruh isi repo, dan script melaporkan
"**66 dari 66 kelas terpilih**" untuk `core`, "23 dari 23" untuk shared, "30 dari 30" untuk server
— yaitu test selektif yang **menjalankan semuanya**. Persis jenis kegagalan yang paling menyesatkan:
terlihat bekerja, sebenarnya tidak menyaring apa pun.

Perbaikannya bukan menambah daftar blokir, tapi memakai **batas struktur bahasa**: di repo ini semua
deklarasi top-level berada di kolom 0, sedangkan anggota kelas selalu ter-indentasi.

```bash
# ✅ VERSI FINAL — hanya deklarasi top-level (kolom 0), plus saringan panjang nama
grep -oE '^(public |private |internal |expect |actual |abstract |open |sealed |data |value |inline |suspend |operator |override |tailrec |external |annotation |enum |const )*(class|object|interface|fun|val|var|typealias) +[A-Za-z0-9_]+' "$file" \
    | sed -E 's/.*[[:space:]]+([A-Za-z0-9_]+)$/\1/' \
    | grep -E '^[A-Za-z0-9_]{3,}$'
```

Hasilnya untuk file yang sama: `TemplateGeometry`, `Mm10`, `TemplateRect`, `PaperSize`, `TextAlign`,
`TextStyleSpec` — semua bermakna, dan pemilihan menyusut ke **5 kelas dari 119**.


### 3.4 Substring, bukan batas kata — dan ini disengaja

Refleks pertama setelah menemukan token "serigala" adalah memasang batas kata:

```bash
# ❌ TERLALU KETAT — recall hilang tanpa disadari
grep -rlE "(^|[^A-Za-z0-9_])(Mm10)([^A-Za-z0-9_]|$)" ...
```

Terlihat lebih "benar", tapi akibatnya ketahuan saat membandingkan daftar hasil:
`InvoiceTemplateCodecTest` **hilang** dari pilihan. Setelah ditelusuri, satu-satunya alasan test itu
relevan adalah baris:

```kotlin
assertEquals(original.marginMm10, decoded.marginMm10)
```

`marginMm10` adalah properti bertipe `Mm10`. Kalau `Mm10` berubah perilaku, test itu ikut rusak —
tetapi ia tidak pernah menulis kata `Mm10` secara utuh. Konvensi repo ini menaruh satuan sebagai
**akhiran** nama (`marginMm10`, `widthMm10`, `heightMm10`), jadi batas kata sistematis akan
melewatkannya.

**Kesimpulan yang dipakai:** pencocokan tetap substring. Dalam pemilihan test, **melewatkan satu test
yang rusak jauh lebih mahal daripada menjalankan beberapa test tambahan yang masih hijau** (biayanya
milidetik).

### 3.5 Noise "dev" diselesaikan lewat klasifikasi, bukan heuristik leksikal

Setelah token anggota kelas dibuang, muncul kebocoran lain: `dev.sh` (file yang sedang kami ubah)
menghasilkan token `dev`, dan substring `dev` cocok dengan `ModuleDev*`, `ProspectDev*`, bahkan kata
`device`. Akibatnya **11 kelas test** terpilih padahal yang berubah hanya script peluncur.

Godaan berikutnya adalah memperketat aturan pencocokan lagi. Jawaban yang benar ternyata lebih
sederhana: **`dev.sh` memang tidak boleh dianalisis sama sekali** — ia tooling pengembangan dan tidak
mengubah perilaku test apa pun.

```bash
is_ignorable() {
    case "$1" in
        docs/*|*.md|.cline/*|.agents/*|agents/*|tools/*) return 0 ;;
        *.png|*.jpg|*.txt|*.lock) return 0 ;;
        *.sh) return 0 ;;   # ← shell script tidak diuji
    esac
    return 1
}
```

**Pelajaran umum:** ketika sebuah penyaring terlalu berisik, periksa dulu apakah **masukannya** yang
salah, sebelum memperketat aturan. Memperketat aturan akan ikut memotong kasus sah (§3.4).


### 3.6 Berkas *foundational*: kapan mempersempit justru tidak jujur

Ada berkas yang **mustahil** dipetakan dengan jujur lewat pencarian referensi. Buktinya diukur, bukan
dikira-kira:

```bash
grep -rln 'MeasureCodec' core/src/commonMain app/shared/src/commonMain server/src/main | wc -l   # 17 file, 4 modul
grep -rln 'MeasureCodec' --include='*Test.kt' core/src app/shared/src server/src | wc -l         # 0 test menyebutnya
grep -rln 'DateTimeCodec' core/src/commonMain app/shared/src/commonMain server/src/main | wc -l  # 21 file
grep -rln 'DateTimeCodec' --include='*Test.kt' core/src app/shared/src server/src | wc -l        # 0 juga
```

`MeasureCodec` dipakai 17 berkas, tetapi **tidak satu pun test menyebut namanya** — test menyentuhnya
secara tidak langsung lewat codec lain. Pencarian referensi akan melaporkan "tidak ada test
terpengaruh" untuk perubahan yang berpotensi merusak modul invoicing, costing, techpack, dan
masterdata sekaligus. Untuk berkas seperti ini, **seluruh suite adalah jawaban yang benar**:

```bash
is_foundational() {
    case "$1" in
        settings.gradle.kts|build.gradle.kts|gradle.properties) return 0 ;;
        gradle/libs.versions.toml) return 0 ;;
        */build.gradle.kts) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/shared/common/*) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/shared/json/*) return 0 ;;
        core/src/commonMain/kotlin/com/eventverse/app/domain/common/*) return 0 ;;
    esac
    return 1
}
```

### 3.7 Memuat `.env` — menyembuhkan jebakan yang sudah dua kali memakan waktu

`DatabaseFactory` memakai default `localhost:5432`, sedangkan `.env` proyek ini menetapkan
`DB_PORT=5435`. Gradle **tidak** membaca `.env`, jadi `./gradlew :server:test` yang dijalankan
langsung menembak PostgreSQL project lain dan gagal dengan `HikariPool$PoolInitializationException` —
error yang tampak seperti bug kode.

Karena `dev.sh` sudah memuat `.env` di bagian atasnya, seluruh jalur test dilewatkan ke sana:

```bash
test)
    trap - SIGINT SIGTERM EXIT
    exec ./tools/test-changed.sh --full
    ;;
```

`exec` dipakai supaya shell dev **digantikan** proses test: trap `cleanup` milik `dev.sh` (yang
mematikan proses dev) tidak ikut berjalan, dan kode keluar test diteruskan apa adanya. Hasilnya
terlihat di header output:

```
🧪 Seluruh suite test — WeMade ERP
.env dimuat: DB_PORT=5435
```

### 3.8 Jaring pengaman: kalau tidak ada test yang cocok, jangan diam

```bash
elif [ "$TOTAL_SELECTED" -eq 0 ]; then
    echo -e "${YELLOW}⚠️  Tidak ada test yang menyebut file yang berubah...${RESET}"
    # lalu modul yang tersentuh dijalankan penuh
```

Kode baru yang belum punya test akan jatuh ke jalur ini. Berhenti dengan "0 test dijalankan, aman"
akan menjadi kebohongan paling berbahaya dari seluruh script ini.


---

## ⚖️ 4. Teknologi & Pendekatan: The "Why"

### Mengapa shell script, bukan custom Gradle task?

Gradle task kustom untuk tujuan ini harus membaca `git`, menghitung pemetaan, lalu menyuntikkan filter
ke task `Test` yang sudah ada — dan itu berarti menyentuh **konfigurasi build** yang dipakai bersama
lima target KMP. Setiap perubahan di sana berisiko terhadap configuration cache dan incremental
compilation, yaitu dua hal yang justru membuat build repo ini cepat.

Script shell di `tools/` berada **di luar** model build: ia hanya menyusun baris perintah Gradle yang
sudah terbukti bekerja. Risikonya terhadap build adalah nol, dan ia bisa dibaca/diubah oleh siapa pun
tanpa perlu memahami KMP.

### Mengapa bukan plugin "predictive test selection"?

Kelas fitur itu ada (mis. Predictive Test Selection milik Develocity), tapi berbayar, memerlukan
server analitik, dan mengambil keputusan dari data historis yang belum dimiliki proyek ini. Untuk 119
kelas test, pencarian referensi deterministik sudah cukup — dan lebih mudah dijelaskan ketika hasilnya
dipertanyakan.

### Mengapa pencarian referensi, bukan pemetaan "satu file → satu test"?

Pemetaan kaku (`Foo.kt` → `FooTest.kt`) akan senyap pada kasus yang justru paling berbahaya:
`MeasureCodec.kt` tidak punya `MeasureCodecTest.kt`, dan `TemplateGeometry.kt` diuji oleh empat kelas
berbeda di dua modul. Pencarian referensi menemukan **siapa pun yang menyebut nama itu**, tanpa perlu
tabel yang harus dirawat manual.

### Mengapa modul penuh kalau pilihan sudah lebih dari 20 kelas?

Karena menjalankan satu task modul penuh memakai **satu JVM** untuk seluruh suite, sedangkan 20+
`--tests` tetap satu JVM juga tetapi memaksa kita membangun baris perintah yang panjang dan rapuh.
Ambang 20 dipilih karena di atas angka itu, hampir seluruh modul memang sudah ikut terpilih — jadi
"optimasi" tambahan tidak lagi berarti.

---

## ⚠️ 5. Jebakan Pemula (Junior Pitfalls)

### 5.1 "Hijau palsu" dari folder hasil test yang sudah dibersihkan

Ini jebakan nomor satu. Setelah menjalankan test selektif, `build/test-results/` **hanya** berisi test
yang tadi dipilih, dan semuanya hijau. Kalau Anda membacanya sebagai bukti kualitas, Anda sedang
membohongi diri sendiri. Karena itu script ini selalu menutup dengan peringatan eksplisit:

```
✅ Test terpilih lulus.
   Ini BUKAN bukti seluruh suite hijau: build/test-results/ kini hanya berisi
   test yang tadi dipilih. Jalankan ./dev.sh test sebelum push.
```

### 5.2 Menjalankan dua build Gradle bersamaan

Dua build paralel menulis ke folder `build/` yang sama; yang terakhir selesai menimpa hasil yang
pertama. Kami pernah melihat hasil "merah" muncul menimpa hasil "hijau" dari eksekusi yang berbeda
tanpa ada satu baris kode pun berubah. Selalu pastikan tidak ada build berjalan:

```bash
pgrep -fl 'GradleWrapperMain'    # harus kosong
```

### 5.3 `local` dipakai di luar fungsi

```bash
while IFS= read -r file; do
    local module_name="$(modules_of_file "$file")"   # ❌ error: local hanya di dalam fungsi
done <<< "$CHANGED"
```

Di dalam loop level-atas, `local` bukan sekadar tidak berguna — ia **error** (`local: can only be used
in a function`) dan mengotori output. Pakai variabel biasa.

### 5.4 Bash 3.2 (bawaan macOS) tidak punya associative array

```bash
declare -A TESTS_PER_MODULE    # ❌ tidak ada di /bin/bash 3.2
```

`macOS` masih mengirim bash 3.2.57. Yang tidak tersedia: `declare -A`, `mapfile`/`readarray`, dan
substitusi `${var,,}`. Yang **tersedia** dan cukup: indexed array (`arr+=("x")`), `${#arr[@]}`,
perulangan `while IFS= read -r`, serta `eval` untuk trik identitas array (dipakai di
`dedupe_into_array`).

### 5.5 `set -u` + array kosong di bash 3.2

```bash
set -u
echo "${ARR[@]}"          # ❌ "unbound variable" kalau ARR kosong di bash 3.2
echo ${ARR[@]+"${ARR[@]}"}  # ✅ aman untuk kosong maupun terisi
```

Karena script ini memakai `set -uo pipefail`, semua penyebaran array memakai bentuk `+` tersebut.

### 5.6 Opsi `--tests` tidak jelas pemiliknya kalau banyak task

Sudah dibahas di §3.2: satu invokasi per modul supaya kepemilikan filter tidak ambigu dan
`UP-TO-DATE` tidak menyamarkan "tidak ada test terpilih".

### 5.7 Lupa bahwa test juga bisa menyeberang modul

Kalau Anda menulis pemilih test yang hanya melihat folder yang sama, Anda akan melewatkan
`core/` → `server/` seperti pada `InvoiceTemplateSeedDecodeTest`. Pencarian **selalu** dijalankan ke
`core/src`, `app/shared/src`, dan `server/src` sekaligus.


---

## 🧪 6. Verifikasi & Cara Membuktikannya Sendiri

Semua angka di bawah ini adalah hasil pengukuran nyata di repo ini, bukan perkiraan.

### 6.1 Skenario A — hanya dokumen/tooling yang berubah

```bash
./dev.sh test-changed --list
```

```
File berubah        : 4 (4 diabaikan: dokumen/tooling)

✅ Tidak ada file kode yang berubah — tidak ada test yang perlu dijalankan.
```

Berubah: `dev.sh`, `tools/test-changed.sh`, `AGENTS.md`, `.cline/` → **0 test**, keluar seketika.

### 6.2 Skenario B — dua berkas desainer disentuh (inti)

Disentuh: `core/.../domain/invoicing/template/TemplateGeometry.kt` dan
`app/shared/.../presentation/invoicing/template/DesignerToolbar.kt`.

```
  ▶ :core:jvmTest (4 dari 66 kelas)
      · com.eventverse.app.domain.invoicing.template.InvoiceAiMappingEngineTest
      · com.eventverse.app.domain.invoicing.template.InvoiceTemplateTest
      · com.eventverse.app.domain.invoicing.template.TemplateRectMovementTest
      · com.eventverse.app.shared.invoicing.InvoiceTemplateCodecTest
  ▶ :app:shared:jvmTest (1 dari 23 kelas)
      · com.eventverse.app.presentation.invoicing.template.TemplateDesignerViewModelTest
```

**5 kelas dari 119** — dan perhatikan bahwa 1 berkas di `core/` menyeret test dari modul
`app/shared/`, sementara modul `server` **tidak** dipanggil sama sekali (0 test terpilih).

### 6.3 Skenario C — dijalankan sungguhan

```
✅ Test terpilih lulus.
./tools/test-changed.sh  1.33s user 0.33s system 8% cpu 20.188 total
```

20 detik itu termasuk **mengompilasi ulang dua berkas yang baru disentuh**. Pada iterasi berikutnya
(tanpa perubahan kode) waktunya jatuh ke hitungan detik.

### 6.4 Skenario D — jalur *foundational*

```bash
./dev.sh test-changed HEAD~1 --list
```

```
⚠️  core/src/commonMain/kotlin/com/eventverse/app/shared/common/MeasureCodec.kt
    File ini menyentuh konfigurasi build / util lintas domain, jadi daftar
    test tidak bisa dipersempit dengan jujur. Menjalankan seluruh suite.
```

Commit terakhir memang menyentuh `MeasureCodec.kt`, dan script menolak mempersempit. Ini perilaku yang
diinginkan.

### 6.5 Skenario E — seluruh suite lewat `dev.sh`

```bash
./dev.sh test
```

```
🧪 Seluruh suite test — WeMade ERP
.env dimuat: DB_PORT=5435
...
✅ Seluruh suite lulus.
```

Hasil dari `build/test-results/` (dibaca lewat agregasi XML, bukan dari kata "BUILD SUCCESSFUL"):

| Target | Suite | Test | Failures | Errors |
|---|---|---|---|---|
| `:core:jvmTest` | 76 | 457 | 0 | 0 |
| `:app:shared:jvmTest` | 20 | 106 | 0 | 0 |
| `:server:test` | 31 | 167 | 0 | 0 |

### 6.6 Cara membuktikan sendiri bahwa penghitungannya benar

```bash
# 1. Sentuh satu berkas, lihat rencananya (tanpa menjalankan apa pun)
printf '\n// uji\n' >> core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/template/TemplateGeometry.kt
./dev.sh test-changed --list

# 2. Kembalikan
git checkout -- core/src/commonMain/kotlin/com/eventverse/app/domain/invoicing/template/TemplateGeometry.kt

# 3. Pastikan tidak ada yang tertinggal
git status --porcelain
```

Kalau rencananya menyebut **puluhan** kelas untuk satu berkas kecil, berarti ada token generik yang
bocor — periksa keluaran `tokens_of_file` untuk berkas itu (§3.3).

---

## 🏆 7. Tantangan Mandiri

**Tantangan 1 — Buktikan ambang 20 masuk akal.**
Ubah `WHOLE_MODULE_THRESHOLD` menjadi 5 dan 200, lalu jalankan skenario B. Catat waktu tiap
konfigurasi. Kapan modul penuh justru lebih cepat, dan mengapa?

**Tantangan 2 — Tangani perubahan pada berkas resource.** `server/src/main/resources/db/migration/*.sql`
saat ini tertangkap karena namanya disebut di dalam test. Tambahkan test baru yang membaca migrasi
**tanpa** menyebut namanya (mis. lewat `Flyway`), lalu buktikan pemilih test masih benar. Kalau tidak,
aturan apa yang perlu ditambahkan?

**Tantangan 3 — Mode `--since` untuk rekan setim.** Saat ini base ref diberikan manual
(`./dev.sh test-changed origin/main`). Buat default yang lebih pintar: bandingkan dengan
`git merge-base HEAD origin/main`, dengan fallback ke `HEAD` bila ref-nya tidak ada. Pastikan tetap
bekerja tanpa jaringan.

**Tantangan 4 — Ringkasan hasil test.** Setelah test jalan, cetak agregat dari XML (jumlah test,
failures, errors) seperti skrip Python di §6.5, tetapi bentuk satu baris per modul. Manfaatkan
`python3` yang sudah tersedia; jangan tambahkan dependensi baru.

**Tantangan 5 — Cegah "hijau palsu" secara struktural.** Peringatan saat ini hanya teks. Rancang cara
yang lebih kuat, mis. menulis penanda `.selective-run` di `build/` lalu menolak (exit ≠ 0) bila
`./dev.sh test` belum pernah dijalankan sejak perubahan terakhir. Diskusikan: apakah ini membantu,
atau justru jadi gangguan yang akan dilewati orang?

**Tantangan 6 — Perluas pemetaan ke target lain.** `app/shared` punya `iosTest`, `webTest`, dan
`androidHostTest`. Peta saat ini hanya menembak `:app:shared:jvmTest`. Tentukan kapan target lain
harus ikut dijalankan, dan jelaskan mengapa menjalankannya di setiap perubahan justru merugikan.

---

## 📌 Ringkasan Satu Halaman

| Prinsip | Wujudnya |
|---|---|
| Jalankan tepat test yang punya alasan gagal | pencarian referensi lintas modul |
| Ingatan luas > ketepatan buta | substring, bukan batas kata (`marginMm10`) |
| Batas struktur bahasa > daftar blokir | hanya deklarasi top-level (kolom 0) |
| Periksa masukannya sebelum memperketat aturan | `*.sh` masuk `is_ignorable` |
| Kejujuran mengalahkan kecepatan | berkas *foundational* → seluruh suite |
| Tidak ada test cocok ≠ aman | fallback modul penuh + peringatan |
| Satu sumber kebenaran untuk runner | `./dev.sh test` & `./dev.sh test-changed` |
| `.env` selalu termuat | `dev.sh` memuatnya di header |

