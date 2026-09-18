# WeMade ERP — Aturan Standar Batas Ukuran File (File Size & Decomposition)

Dokumen ini adalah **aturan baku ukuran file** yang wajib ditaati setiap kali membuat atau
mengubah file Kotlin di repo ini. Statusnya sejajar dengan
[`module-integration-rules.md`](module-integration-rules.md) dan
[`design-system-rules.md`](design-system-rules.md): kalau yang pertama mengatur *apa yang
dikerjakan* dan yang kedua *bagaimana rupanya*, dokumen ini mengatur **seberapa besar satu file
boleh tumbuh sebelum ia berhenti bisa dibaca**.

**Ruang lingkup**: seluruh `*.kt` di `core/`, `app/`, dan `server/`.

---

## 1. Paradigma: Batas Baris Adalah Alarm, Bukan Target

Batas ini **bukan** soal estetika atau menghitung baris demi menghitung baris. Panjang file adalah
*proxy* paling murah untuk tiga penyakit yang sebenarnya:

1. **File melanggar Single Responsibility** — satu file mengerjakan lima hal, jadi tidak ada nama
   yang jujur untuk isinya.
2. **Pola visual/logika disalin, bukan diangkat** — gejala yang sama dengan pelanggaran
   [Aturan Tiga Kali](design-system-rules.md#kontrak-4--aturan-tiga-kali-rule-of-three).
3. **File tidak lagi bisa direview** — satu file yang melebihi batas tidak bisa dibaca sekali duduk
   maupun dinilai utuh dalam satu review, sehingga bug lolos di bagian yang tidak sempat dibaca.

> **Konsekuensinya**: melewati batas **tidak** boleh diselesaikan dengan memotong file di tengah
> secara sembarang (`FooScreenPart2.kt`). Memecah file wajib mengikuti **batas tanggung jawab**,
> bukan batas baris. Kalau tidak ada garis pisah yang jujur, itu tandanya masalahnya bukan panjang
> file — melainkan desainnya.

---

## 2. Ambang Baris per Lapisan

Satu angka global tidak masuk akal: Compose secara struktural lebih panjang dari domain murni
(median `presentation/` di repo ini 3,4× median `core/`). Karena itu ambangnya per lapisan.

| Lingkup | Soft (peringatan) | Hard (tolak merge) | Alasan ambang |
|---|---|---|---|
| `core/**` (domain murni) | **250** | **400** | p90 lapisan ini 196 baris. Entity/VO >250 hampir pasti God Entity |
| `app/shared/**/presentation/**` | **400** | **600** | median 203; Compose butuh ruang, tapi 600 adalah batas satu kali duduk |
| `server/src/main/**` | **300** | **500** | p90 lapisan ini 374; routes & repository Postgres |
| `**/commonTest/**`, `**/jvmTest/**` | **500** | **800** | test memang repetitif; memecahnya merugikan keterbacaan kasus |

**Kalau ragu atau lingkupnya tidak terdaftar: soft 400 / hard 600.**

Cara membacanya:

- **Di bawah soft** — tidak perlu berpikir, lanjut.
- **Melewati soft** — boleh lanjut, tapi wajib berhenti sebentar dan bertanya: *apakah file ini
  masih punya satu nama yang jujur?* Kalau jawabannya tidak, pecah sekarang selagi murah.
- **Melewati hard** — **berhenti**. Dilarang menambah baris ke file itu tanpa memecahnya lebih
  dulu, kecuali masuk pengecualian §3.

### Kontrak 1 — Hard limit berlaku ke *file setelah diubah*, bukan ke diff-nya

Menambah 10 baris ke file 700 baris tetap pelanggaran. Aturan ini tentang hasil akhir, bukan
ukuran perubahan.

### Kontrak 2 — Aturan Ratchet: file yang sudah melanggar tidak boleh membesar

Untuk file yang **sudah** di atas hard limit sebelum aturan ini ada (lihat §5), berlaku aturan
searah: **setiap perubahan pada file itu wajib membuatnya lebih pendek, atau minimal tidak lebih
panjang.** Tidak ada kewajiban menormalkannya dalam satu PR — tapi tidak boleh bertambah.

```
# sebelum menyentuh file yang sudah besar
wc -l <file>          # catat angkanya
# ... kerjakan perubahan ...
wc -l <file>          # wajib ≤ angka sebelumnya
```

---

## 3. Pengecualian yang Sah (dan Hanya Ini)

Ambang baris **tidak berlaku** untuk file yang isinya **data terurut, bukan logika bercabang** —
karena memecahnya tidak menambah keterbacaan sedikit pun, hanya menyebarkan satu tabel ke lima
tempat.

Pengecualian wajib **dideklarasikan eksplisit** dengan komentar di baris pertama file:

```kotlin
// FILE-SIZE-EXEMPT: katalog aset — data terurut, bukan logika. Lihat .claude/rules/file-size-rules.md §3
```

Yang memenuhi syarat:

| Kategori | Contoh di repo ini | Kenapa sah |
|---|---|---|
| Katalog ikon / vector path | `ClayIcons.kt` (1354), `PipelineIcons.kt` (679) | Deretan `Path` deklaratif; nol percabangan |
| Seed / preset template | `PipelinePresetFactory.kt` (1224) | Tabel data onboarding per archetype |
| Codec / mapper eksplisit | `SamplingOrderCodec.kt` (510) | Satu baris per field, lurus, tanpa logika |
| Kode ter-generate | — | Bukan kita yang menulis |

Yang **tidak** memenuhi syarat, betapa pun besarnya:

- Screen / Dialog / Pane Compose — panjangnya selalu gejala styling yang disalin atau komponen yang
  belum diangkat, bukan gejala data.
- ViewModel — panjangnya selalu gejala terlalu banyak tanggung jawab dalam satu state holder.
- Route handler & repository — pecah per agregat/resource.

---

## 4. Pola Pemecahan yang Disarankan

Jangan mengarang struktur baru; ikuti pola yang sudah dipakai repo ini.

### Compose Screen / Dialog yang membengkak

```
presentation/deal/components/
├── DealDetailDialog.kt          # hanya shell: state hoisting, scaffold, wiring event
├── DealDetailHeader.kt          # satu section = satu file
├── DealDetailSpecForm.kt
├── DealDetailTimelinePane.kt
└── DealDetailUiModel.kt         # mapping domain → UI model
```

Aturannya: **file shell hanya merakit, section yang merender.** Kalau setelah dipecah shell-nya
masih >400 baris, berarti dialog itu sebenarnya beberapa layar yang dipaksa jadi satu.

Sebelum memecah, cek dulu apakah bagian yang berulang seharusnya naik ke
`presentation/designsystem/` — sering kali separuh panjangnya adalah styling yang melanggar
[Kontrak 4 design system](design-system-rules.md#kontrak-4--aturan-tiga-kali-rule-of-three).

### ViewModel yang membengkak

Pindahkan logika ke Use Case di `core/` (memang tempatnya menurut
[CLAUDE.md §4](../CLAUDE.md)), lalu pisahkan per sumbu:

```
presentation/orgchart/
├── OrgChartViewModel.kt         # state holder + dispatch event
├── OrgChartUiState.kt           # state & event model
└── OrgChartLayoutCalculator.kt  # perhitungan murni, bisa diuji tanpa ViewModel
```

### Route / Repository server yang membengkak

Pecah per agregat, bukan per HTTP method:

```
routes/
├── CostingRoutes.kt             # composisi: route("/costing") { … }
├── CostingEstimateRoutes.kt
└── CostingRateCardRoutes.kt
```

### Domain file yang membengkak

Satu file = satu konsep, sesuai [CLAUDE.md §8](../CLAUDE.md). Value object kecil boleh digabung
(`EventValueObjects.kt`), tapi begitu file itu >250 baris, kelompokkan per sub-konsep.

---

## 5. Checklist Verifikasi Sebelum Merge (Definition of Done)

- [ ] Tidak ada file yang **melewati hard limit** lapisannya tanpa komentar
      `FILE-SIZE-EXEMPT` yang beralasan menurut §3:
      ```bash
      # semua file Kotlin yang disentuh, diurutkan dari terpanjang
      git diff --name-only --diff-filter=ACM main...HEAD -- '*.kt' \
        | xargs wc -l 2>/dev/null | sort -rn | head -20
      ```
- [ ] Untuk file yang **sudah** di atas hard limit (§5 tabel utang): jumlah barisnya **tidak
      bertambah** (Kontrak 2 / Ratchet)
- [ ] File yang melewati soft limit sudah ditinjau: masih punya satu nama yang jujur
- [ ] Pemecahan mengikuti **batas tanggung jawab**, bukan potongan baris —
      tidak ada `…Part2.kt` / `…Extra.kt` / `…Helpers.kt` tanpa tema
- [ ] Kalau yang dipecah adalah UI: bagian yang berulang sudah diperiksa apakah seharusnya naik ke
      `presentation/designsystem/` alih-alih hanya dipindah file
- [ ] Setelah pemecahan, **kompilasi 5 target** (bukan satu) masih hijau:
      ```bash
      ./gradlew :app:shared:compileKotlinJvm :app:shared:compileKotlinWasmJs \
                :app:shared:compileKotlinJs :app:shared:assembleAndroidMain \
                :app:shared:jvmTest
      ```
- [ ] Kalau yang dipecah adalah UI: **dijalankan dan dilihat dengan mata** — pemecahan Compose
      mudah menggeser `Modifier` chain dan merusak layout tanpa memecahkan kompilasi

Skrip audit seluruh repo (untuk mengukur kemajuan, bukan gate per-PR):

```bash
find . -name "*.kt" -not -path "*/build/*" -not -path "*/bin/*" \
  | xargs wc -l | grep -v total | awk '$1>600' | sort -rn
```

---

## 6. Utang Teknis Terdaftar (jangan ditambah, boleh dicicil)

Kondisi awal saat aturan ini ditetapkan (2026-09-18): **954 file Kotlin, median 101 baris,
p90 353 baris**. Yang di atas hard limit: **21 file** — 4 di antaranya sah sebagai pengecualian §3,
menyisakan **17 file** untuk dicicil.

| File | Baris | Status |
|---|---|---|
| `presentation/deal/components/DealDetailDialog.kt` | 3006 | **Prioritas 1.** Juga terdaftar di utang design system |
| `presentation/orgchart/OrgChartScreen.kt` | 2420 | **Prioritas 2.** Juga 108 literal warna belum disapu |
| `presentation/designsystem/ClayIcons.kt` | 1354 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `core/domain/pipeline/PipelinePresetFactory.kt` | 1224 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `presentation/orgchart/OrgChartViewModel.kt` | 1196 | Pecah: logika layout → kalkulator murni |
| `presentation/rbac/components/AssignDepartmentModal.kt` | 1057 | Juga 19 literal warna |
| `presentation/invoicing/template/TemplateCanvas.kt` | 983 | |
| `presentation/invoicing/template/DesignerPropertyInspector.kt` | 892 | |
| `presentation/deal/components/DealsPane.kt` | 861 | |
| `presentation/costing/CostingWorkspaceScreen.kt` | 825 | |
| `presentation/pipeline/components/NodeInputInspectorModal.kt` | 809 | |
| `server/routes/CostingRoutes.kt` | 754 | Pecah per agregat |
| `presentation/crm/components/LeadInspectorPane.kt` | 733 | |
| `presentation/auth/LoginScreen.kt` | 716 | Juga terdaftar di utang design system |
| `server/infrastructure/PostgresSamplingOrderRepository.kt` | 698 | |
| `server/Application.kt` | 697 | Pecah: konfigurasi plugin → file terpisah |
| `presentation/pipeline/components/PipelineIcons.kt` | 679 | Pengecualian §3 — tandai `FILE-SIZE-EXEMPT` |
| `server/routes/DealRoutes.kt` | 677 | Pecah per agregat |
| `presentation/navigation/PersonaSwitcherDropdown.kt` | 676 | |
| `server/infrastructure/PostgresModuleDevRepositories.kt` | 628 | Nama jamak = tanda sudah waktunya dipecah |
| `presentation/deal/components/ContactsPane.kt` | 625 | |

Setiap kali menyentuh file di daftar ini untuk alasan apa pun, **cicil** bagiannya (Kontrak 2) —
jangan menambah barisnya.

---

## 7. Catatan Penegakan Otomatis

Saat ini **belum ada gate otomatis**: Detekt belum terpasang di Gradle (hook
`.claude/hooks/validate-detekt.sh` ada tapi belum ada plugin maupun `detekt.yml`), jadi aturan ini
ditegakkan lewat review dan skrip di §5.

Kalau nanti Detekt dipasang, ambang di §2 dipetakan ke:

```yaml
complexity:
  LongMethod:
    threshold: 60
style:
  MaxLineLength:
    maxLineLength: 120
# batas panjang FILE tidak punya rule bawaan detekt —
# gunakan custom rule atau skrip CI di §5
```
