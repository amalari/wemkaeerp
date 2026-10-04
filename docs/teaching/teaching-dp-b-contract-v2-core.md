# 🎓 Modul Pembelajaran: Jalur B — Kontrak v2 Port Data & Blok Kaya di `core` (B0–B4)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Design Contract-First untuk tim paralel, Sealed Vocabulary, Port & Adapter, Atomic Mutation, Codec ketat (strict decode), Composable ERP (kode vs data)
> **Prasyarat**: Paham struktur repo (DDD: `core` murni tanpa framework), Kotlin `sealed interface` + `Result`, dasar Kotlin Multiplatform, dan aturan [tenant-variability-rules](../../.claude/rules/tenant-variability-rules.md)
> **Referensi Task**: [PLAN-dp-B-core.md](../plannings/parallel2/PLAN-dp-B-core.md) (butir B0–B4), [PLAN-prototype-data-port-rich-blocks.md](../plannings/PLAN-prototype-data-port-rich-blocks.md) §3

---

## 💡 1. Konsep Dasar & Masalah di Dunia Nyata

**Masalah nyata.** Tiga agent (A: UI, B: core/kontrak, C: API pilot) mengerjakan satu fitur prototype
secara paralel di satu repo. Kalau tiap agent "menerka" bentuk data agent lain, hasilnya klasik:
UI A membaca kunci `status`, core B menulis kunci `Kolom`; server C mengirim `prioritas` bertipe
angka, klien menganggapnya teks. Semua saling menunggu, semua saling salah.

**Analogi sederhana.** Anggap kontrak v2 ini seperti **colokan listrik standar**: B menerbitkan bentuk
colokan (port, binding, hints, operasi) *lebih dulu* — walau listriknya (implementasi) menyusul —
supaya A dan C bisa membuat perangkatnya masing-masing tanpa menunggu. Perubahan bentuk colokan
setelah terbit = versi baru + kabar semua agent, bukan sunting diam-diam.

**Hasil akhir.**
- `DataBinding` (memori bawaan vs API), `BlockDataPort` + `PortError`/`PortException` — satu port =
  satu entitas (§3.1), pesan galat berbahasa pengguna.
- `InMemoryBlockDataPort` penuh: create/update atomik/delete, diserialkan `Mutex`, id monotonik yang
  tak pernah dipakai ulang.
- Blok kaya: `CardStyle`/`CardElement`/`ColumnMeta`, `KanbanConfig(card, columnMeta, detailForm)`,
  `TableConfig(inlineCreate, editableFields)` — divalidasi **konstruktor**, bukan di UI.
- Hints pack: `TableHints.fields` & `KanbanHints(card, columnMeta, detailForm, groupField, fields)` —
  tipe field diturunkan dari **deklarasi pack**, bukan ditebak dari contoh.
- `SpecOp` v2 (7 operasi, sealed): `ShowFieldOnCard` & `SetFieldRequired` dilaksanakan penuh di B4.

---

## 🧭 2. "Start dari Mana?" — Alur Langkah Penulisan (Order of Operations)

1. **Langkah 0 — Terbitkan kontrak (G0) sebelum implementasi.** Kenapa bukan langsung tabel/rute?
   Karena kontrak adalah **antarmuka antar-agent**: selama bentuknya dikunci (kode + fixture di
   `PrototypeContractSamples`), A dan C bisa mulai dari fake/stub. Implementasi boleh
   `Result.failure("belum didukung")` — yang penting **bentuk pesannya sudah final** di codec dan UI.
2. **Langkah 1 — Value object + validasi konstruktor.** `KanbanConfig` menolak sendiri bentuk tak
   koheren (elemen kartu menunjuk field hantu, `wipLimit ≤ 0`). Validasi di gerbang masuk paling
   awal berarti **tidak ada lapisan mana pun yang bisa membuat spec ilegal** — UI, server, dan AI
   sama-sama terhalang kalau mencoba.
3. **Langkah 2 — Port domain murni.** `BlockDataPort` hanya antarmuka; `InMemoryBlockDataPort`
   mengimplementasikan dengan aturan *identik* `PrototypeReducer` (parity test mengiterasi kasus yang
   sama). Aturan validasi jangan pernah ditulis dua kali dengan gaya berbeda.
4. **Langkah 3 — Hints & factory.** `InteractiveScreenFactory.table/kanban` menerjemahkan petunjuk
   pack → spec. Prinsip: **hints tak koheren ditolak (null → gambar statis), bukan diabaikan**.
5. **Langkah 4 — Codec ketat.** Decode: jenis/kunci/gaya tak dikenal = `Result.failure` berpesan.
   *Tidak ada tebakan* — JSON rusak harus gagal jelas, bukan "kira-kira begini maksudnya".
6. **Langkah 5 — Operasi spec (SpecOpApplier).** Murni, tanpa jam; yang gagal tidak membatalkan yang
   sah; semuanya tercatat di `CaptureEntry` untuk brief.

## 🔍 3. Bedah Kode Blok per Blok (Mental Model)

### 3.1 `DataBinding` — dari mana data layar itu datang?

```kotlin
sealed interface DataBinding {
    data object Memory : DataBinding                       // demo di memori (bawaan, aman mundur)
    data class Api(val basePath: String) : DataBinding     // data hidup di server pilot
}
```
Mental model: **binding itu alamat, bukan implementasi.** Layar membawa "tulisanku di sini"; siapa
yang memuat/menyimpannya adalah urusan lapisan di bawahnya (A menghubungkan ke port; C menyediakan
rute). Encode `Memory` = JSON `null` — pack lama tanpa kunci binding tetap terbaca (kompatibel mundur
teruji).

### 3.2 `InMemoryBlockDataPort` — mutasi atomik

- `update` multi-field: **satu pelanggaran = tidak ada yang berubah**. Caranya bukan rollback, tapi
  hitung dulu hasilnya lewat jalur validasi yang sama, baru terapkan.
- Id baris `<entityId>-<n>` dengan `n` **monotonik, tak pernah dipakai ulang** — menghapus baris
  terakhir lalu menambah lagi tidak boleh mendaur ulang id (id adalah identitas, bukan nomor kursi).
- Semua operasi dijaga `Mutex` — pemanggil tak perlu pikirkan balapan.

### 3.3 Dua mode papan kanban (B2.1 — lahir dari temuan jalur C)

C menemukan: layar kanban berbinding Api dari modul pilot **tidak punya baris contoh berkunci
"Kolom"**, jadi factory menolak → layar jatuh ke gambar statis. Solusinya di jalur B (pemilik file):

| | Mode legacy (garment) | Mode dideklarasikan (server) |
|---|---|---|
| Sumber bentuk | baris contoh berkunci `"Kolom"` | `KanbanHints.fields: List<FieldHint>` + `groupField: String?` |
| Tipe field | semua TEXT | ENUM/DATE/BOOL/NUMBER dari deklarasi |
| Baris contoh | wajib, tak kosong | **opsional** (data dari server) |
| `titleField` | teks pertama baris | elemen kartu bergaya `TITLE` (atau deklarasi pertama) |

Pelajaran besarnya: **aturan yang mencari "kunci Kolom" adalah aturan yang mengenal satu industri.**
Ganti ke *peran* — "kunci kelompoknya adalah `groupField`" — dan garment tetap identik (bawaan
`null` → `"Kolom"`) sementara pilot bebas. Ini Kontrak 3 tenant-variability dalam praktik.

Koreksi penting saat memeriksa laporan C: **tabel punya cacat serupa yang tersembunyi** — bukan di
factory, tapi di `WidgetRegistry.sampleRowsFor` yang *mengarang marker generik* (`"Kolom"`,
`"contoh 1"`) kalau `sampleRows` kosong. Marker itu membuat seed tak sah terhadap field yang
dideklarasikan. Perbaikannya: layar berbinding Api / berdeklarasi field dikembalikan **kosong apa
adanya** — *fallback tak boleh mengarang fakta*.

### 3.4 `SpecOp` — kosakata tertutup untuk mengubah spec lewat percakapan

```kotlin
sealed interface SpecOp { /* 7 operasi */ }
```
Sealed = LLM/klien **tidak bisa menyelundupkan operasi bebas**; jenis baru = ubah kode + kontrak.
Dua operasi v2 (B4):

- `ShowFieldOnCard(entityId, field, style)` — **upsert**: field yang belum tampil ditambah di akhir
  kartu; yang sudah tampil **diganti gayanya di posisi semula** (urutan kartu terjaga, tidak ada
  duplikat). Hanya layar kanban milik entitas itu; tabel/form tak tersentuh. Ini juga jalur
  eksplisit dari keputusan B3 "`AddField` tidak mengubah layar yang ada".
- `SetFieldRequired(entityId, field, required)` — toggle; penegakannya **bukan** di applier melainkan
  di `PrototypeReducer` saat `Create` (aturan satu tempat). No-op bila nilainya sudah sama.

Keduanya ditolak dengan pesan berbahasa pengguna bila field/entitas tak ada, atau entitas tak punya
papan kanban.

### 3.5 Pemecahan `ScreenSuggestionCodec`

`DomainPackCodec` membengkak (328 baris > cap lunak 250). Pemecahan **menurut tanggung jawab**:
satu file menyandikan *screen suggestion* (bentuk layar), satu lagi sisa pack. Jangan pecah per
"baris ke-N".

---

## 🧪 4. Kebiasaan Uji yang Menyelamatkan

1. **Fixture non-garment wajib** (Kontrak 6): tiket servis/order bengkel di `PrototypeContractSamples`,
   pack "uji" di `PackBindingPassThroughTest`. Pack garment tidak membuktikan apa pun tentang tenant lain.
2. **Tes penolakan sama pentingnya dengan tes keberhasilan**: hints hantu → null; JSON gaya tak
   dikenal → failure berpesan; field wajib kosong → ditolak. *Fail-closed*, bukan fail-silent.
3. **Paritas reducer ↔ port**: iterasi kasus yang sama di kedua jalur (B1), supaya aturan tidak
   pernah menyimpang diam-diam.
4. **Mutasi ekspektasi**: sesekali ubah satu ekspektasi tes dan pastikan tesnya **gagal** — melindungi
   dari tes yang selalu hijau.

---

## ⚠️ 5. Jebakan yang Benar-Benar Terjadi (dan Cara Keluarnya)

| Jebakan | Cerita | Pelajaran |
|---|---|---|
| Kunci industri di kode | Kanban wajib berkunci `"Kolom"` — pilot mati total | Cari *peran* (`groupField`), bawaan = perilaku lama |
| Marker generik dianggap data | `sampleRowsFor` mengarang `"Kolom"`/`"contoh 1"` untuk layar Api → seed tak sah | Fallback boleh menggambar, tidak boleh **mengarang fakta** |
| Smart cast lintas modul | `InteractiveKanban.kt` (jalur A): `meta.tintHex` di-smart-cast — properti modul lain tak bisa di-smart-cast | Tampung ke `val` lokal dulu |
| Salah hitung fixture sendiri | Tes mengira kartu contoh 7 elemen (nyatanya 6 — `Target` sudah ada), lalu `rowsOf().first()` mengambil baris *seed*, bukan baris baru | Baca fixture sebelum menulis ekspektasi; baris baru dicari per `id` |
| Cache inkremental Kotlin | `DirtyData.dirtyLookupSymbols` setelah core bertambah kelas | Ulangi; bila berulang, hapus `build/` **di worktree sendiri** |
| Menyentuh milik agent lain | Usulan awal menyuruh C menyunting factory | Cek kepemilikan plan dulu; perbaikan kontrak = tiket ke pemilik jalur |

---

## 📂 6. Peta Artefak (B0–B4)

| Lapisan | File | Isi kunci |
|---|---|---|
| Kontrak | `domain/prototype/{DataBinding,BlockDataPort}.kt` | `DataBinding`, `PortError`/`PortException` |
| Port memori | `domain/prototype/InMemoryBlockDataPort.kt` | Mutex, atomik, id monotonik |
| Spec | `domain/prototype/{PrototypeSpec,PrototypeHints}.kt` | Blok kaya + `KanbanHints.groupField/fields` (v2.1) |
| Factory | `domain/prototype/InteractiveScreenFactory.kt` | Dua mode kanban; tipe dari hints |
| Registry | `domain/discovery/WidgetRegistry.kt` | Meneruskan binding; marker tak dikarang |
| Codec | `shared/pack/ScreenSuggestionCodec.kt` (+`SpecOpCodec`) | Encode/decode ketat, kompatibel mundur |
| Operasi | `domain/prototype/SpecOpApplier.kt` | 7 operasi dilaksanakan penuh |
| Fixture | `domain/prototype/PrototypeContractSamples.kt` | orderEntity/orderScreen/orderScreenApi/richOps — non-garment |

Verifikasi tiap butir: `:core:jvmTest`, `:app:shared:compile{Jvm,WasmJs,Js}`, `:app:shared:jvmTest`,
`:server:compileKotlin`, `scripts/audit-variability.sh` = 0 temuan.

---

## 🏆 7. Tantangan Mandiri untuk Kamu (Self-Study Challenge)

- [ ] **Tantangan 1**: tambah operasi `HideFieldOnCard` (kebalikan `ShowFieldOnCard`) — mulai dari
      `SpecOp.kt` + codec, lalu applier + tes. Kenapa kosakata tertutup membuat langkah ini murah?
- [ ] **Tantangan 2**: buat `ApiBlockDataPort` palsu di test yang selalu gagal (`PortError.Unavailable`)
      dan buktikan UI/pemanggil melakukan rollback, bukan menyimpan setengah data (kebijakan plan §2.1).
- [ ] **Tantangan 3**: temukan satu `when` di `presentation/**` yang bercabang atas konsep yang
      seharusnya data tenant, dan rancang deklarasinya sebagai hints (bandingkan dengan
      `groupField` di §3.3).

---

*Penulis: jalur B (Agent B) — cakupan B0–B4, terakhir diperbarui saat butir B4/B5 (2026-10-04).*


