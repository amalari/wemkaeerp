# Teaching — Discovery C2: Studio Pola Prototipe (dan bug yang hanya muncul kalau layarnya dibuka)

> Plan: [`PLAN-discovery-blueprint-prototype-studio.md`](../plannings/PLAN-discovery-blueprint-prototype-studio.md) §4 (Fase C) ·
> Status: selesai 2026-09-30 · Pendahulu: [`teaching-discovery-c1-wizard-funnel-visual-verification.md`](teaching-discovery-c1-wizard-funnel-visual-verification.md),
> [`teaching-discovery-d1-blueprint-pdf.md`](teaching-discovery-d1-blueprint-pdf.md)

Fase C sudah setengah jalan sebelum dokumen ini ditulis: `ModuleMapPane`, `DataFlowPane`, dan
`PrototypeRenderer` sudah ada sejak Fase A–D. Yang **belum** ada adalah sisi lain dari kalimat di plan:
*"Studio internal pola `TemplateDesigner`, disimpan di `ops.prototype_patterns` (V79)"* — tabel dan
rutenya sudah hidup, tapi **nol pemakai di `app/`**. Fase C ditutup dengan membangun pemakainya, dan
dengan tiga temuan yang tidak akan pernah muncul dari kompilasi maupun test.

---

## Step 0 — Memutuskan arti "pola" sebelum menulis satu baris UI

Ini keputusan termahal di fase ini, dan mudah salah. Ada tiga arti yang kelihatannya mirip:

| Arti | Isinya | Kenapa ditolak / diterima |
|---|---|---|
| Layar jadi | seluruh deskriptor layar + data contoh | **Ditolak**: bikin sumber kebenaran kedua; draf punya deskriptornya sendiri |
| Skema widget | daftar widget + konfigurasi tipe field | **Ditolak**: itu wilayah `WidgetKind` + `WidgetRegistry` di `core`, bukan data Studio |
| **Bentuk baris** | `{"rows":[{"Blok":"Ringkasan","Lebar":"penuh"}]}` | **Diterima** |

Yang ketiga menang karena satu alasan teknis yang bisa diperiksa: `WidgetRegistry.sampleRowsFor()`
sudah mengembalikan `List<Map<String, String>>`, dan `PrototypeRenderer` sudah menggambar bentuk itu
apa adanya. Jadi pola bukan format baru — ia **nilai yang bisa diisi operator** ke slot yang sudah ada.

Konsekuensinya, pratinjau Studio memakai `PrototypeRenderer` yang **sama** dengan pratinjau draf
prospek. Ini bukan penghematan kode, ini pencegahan: dua renderer akan menyimpang, dan yang menyimpang
adalah yang diperlihatkan ke prospek (plan D2 — "satu renderer generik"; pelajaran faktur di Fase D).

```kotlin
// PrototypePatternUiModel.kt
fun toPreviewDraft(moduleId: String, moduleName: String, section: String): DiscoveryDraftUi
```

Pola dibungkus menjadi draf sekali-layar. Terdengar seperti akal-akalan, dan memang — tapi akal-akalan
yang tepat: `PrototypeRenderer` menerima `DiscoveryDraftUi`, dan memaksa ia menerima dua bentuk input
akan membuatnya bercabang. Membungkus lebih murah daripada bercabang.

## Step 1 — Kontrak HTTP lebih dulu, karena klien tidak bisa menebak

`ops.prototype_patterns` + `GET/POST /api/discovery/patterns` sudah ada sebelum fase ini. Yang perlu
dibuat hanya sisi klien di `DiscoveryApiClient`, dan di situ ada satu jebakan yang saya lewati:

```kotlin
// SEBELUM (jalur lama, dipakai semua endpoint discovery):
JsonParser.parseObject(text)   // ← wajib objek
```

`GET /api/discovery/patterns` mengembalikan **array** (`jsonArrayOf(...)`). Akibatnya, saat layar
Studio pertama kali dibuka, banner merahnya berbunyi:

```
Expected a JSON object at root (at offset 0)
```

…pada endpoint yang sehat, dengan HTTP 200, dan tanpa satu pun baris kode yang salah tulis. Ini
**temuan yang tidak bisa ditangkap test**: seluruh test rute memanggil server langsung; tidak ada yang
melewati jalur parse klien ini. Perbaikannya satu baris, tapi yang penting alasannya:

```kotlin
// SESUDAH: bentuk respons diperiksa pemanggil, tempat maknanya diketahui
JsonParser.parse(text)
```

Menegakkan "wajib objek" di lapisan transport adalah asumsi yang diselipkan ke tempat yang tidak
seharusnya tahu bentuk data: endpoint daftar selalu array, endpoint ringkasan selalu objek, dan yang
tahu perbedaannya adalah pemanggilnya.

## Step 2 — Kerangka dipanen, bukan dikarang

Pertanyaan berikutnya: operator mengetik baris dari nol, atau mulai dari sesuatu? Kalau dari nol, kita
membangun editor kosong yang jatuh ke kebiasaan masing-masing orang, dan tiap pola jadi bentuk baru
yang tidak dikenali renderer.

Jalan keluar yang dipakai repo ini sudah ada: `WidgetRegistry` (kosakata tertutup, satu sumber). Jadi
Studio meminta **pack + modul**, lalu memanen:

```kotlin
fun skeletonFrom(pack: DomainPack, moduleId: String, widgetCode: String): List<PrototypeRowUi> {
    if (WidgetKind.fromCode(widgetCode) == null) return emptyList()
    if (pack.modules.none { it.id.value == moduleId }) return emptyList()
    return WidgetRegistry.sampleRowsFor(
        PrototypeScreen("pattern-skeleton", ModuleId(moduleId), "Kerangka pola", widgetCode), pack
    ).map { row -> PrototypeRowUi(row.map { (label, value) -> PrototypeFieldUi(label, value) }) }
}
```

Dua `emptyList()` di atas adalah **jawaban, bukan kegagalan**: modul asing atau widget di luar kosakata
berarti registry tidak punya bentuk untuk pasangan itu. Layar yang menjelaskannya, bukan layar yang
diam saja saat tombolnya tidak berefek.

Yang **tidak** ikut disimpan: `moduleId`. Baris konkretnya sudah cukup, dan menyimpan modul asal akan
membuat pola mengklaim terikat pada modul yang mungkin tidak ada di pack tenant lain — klaim yang tidak
bisa dipertahankan `pack.modules.none { … }`.


## Step 3 — Satu kolom yang nilainya dibaca renderer: pil, bukan teks bebas

`CUSTOM_SCREEN` memasangkan blok dari nilai kolom `Lebar`:

```kotlin
// PrototypeRenderer.kt — aturan pasangan tinggal di renderer
if (baris["Lebar"] == "penuh") { … } else { /* dua "separuh" berdampingan */ }
```

Artinya salah ketik `"separu"` atau `"Penuh"` tidak memecahkan kompilasi, tidak menggagalkan test, dan
tidak menampilkan pesan apa pun — ia **diam-diam mengubah tata letak pratinjau**. Untuk kolom itu saja,
penyunting menukar text field dengan pil:

```kotlin
isWidthField = widgetCode == WidgetKind.CUSTOM_SCREEN.code && field.label == WIDTH_FIELD
```

Ini pengecualian yang disengaja terhadap prinsip "tidak ada bentuk baku di-hardcode": kolom `Lebar`
memang semantik bagi renderer, jadi mengunci nilainya di kosakata tertutup lebih jujur daripada
membiarkan teks bebas yang bisa salah.

## Step 4 — Wewenang menulis: server yang memutuskan, layar hanya menyembunyikan

`POST /api/discovery/patterns` menolak non-superadmin (403). Layar tidak boleh menjadi satu-satunya
gerbang, dan juga tidak boleh membuat pengguna menabrak dinding:

```kotlin
// App.kt — diputus dari sesi, ditegakkan ulang di server
PrototypeStudioScreen(canWrite = session?.user?.role == Role.PLATFORM_SUPERADMIN)
```

Di layar, `canWrite = false` menghasilkan tiga hal: tag **"Hanya baca"**, satu paragraf yang menjelaskan
bahwa server menolak peran lain (403) dan bahwa isi pola tetap bisa dibaca, dan tombol simpan yang
nonaktif. *Menyembunyikan* tombol akan lebih bersih secara visual tapi menyembunyikan juga kemampuan
yang bisa diminta pengguna ke adminnya — jadi tombolnya tetap ada, mati.

## Step 5 — Aturan Tiga Kali, dan naiknya ke design system

Layar ini butuh tiga grup pil berlabel: Widget, Pack, Modul. `ClayChoiceChip` sudah ada, tapi **grup
berlabel** (label kecil di atas, baris yang melipat, satu nilai terpilih) belum — dan itu pola yang sama
di RBAC, alur tahap sampling, dan kini Studio. Kontrak 4 design system berbunyi: pola yang muncul ≥3 kali
wajib diangkat **sebelum** pemakaian keempat. Jadi `ClayChoiceGroup` dibuat di
`presentation/designsystem/`, domain-blind (`String` masuk, `String` keluar), dan Studio memakainya tiga
kali.

Perhatikan juga yang **tidak** dilakukan: tidak ada `Card`/`Button` Material, tidak ada
`Modifier.shadow()`, tidak ada `RoundedCornerShape(12.dp)` telanjang, tidak ada `Color(0xFF……)`. Pola
terpilih dibedakan lewat **warna outline** (`ClayCard.selected`), bukan ketebalan — Kontrak 8.

## Step 7 — Verifikasi: apa yang diuji mesin, apa yang hanya terlihat mata

Test yang ditambahkan **hanya** di dua tempat, dan keduanya mengunci hal yang benar-benar bisa rusak:

| Berkas | Isi | Kenapa di situ |
|---|---|---|
| `app/shared/src/commonTest/.../PrototypePatternUiModelTest.kt` (7) | round-trip `patternJson ⇄ fromJson`, urutan kolom di `sampleRows`, panen kerangka dari `WidgetRegistry`, pola asing dibuang tanpa menyembunyikan yang sehat, koersi nilai non-string, `packLabel` | murni, tanpa jaringan — jalan di kelima target |
| `server/.../DiscoveryApiTest.kt` (+1 = 8) | payload persis yang dikirim klien tersimpan & terbaca kembali dengan urutan baris utuh | kontrak klien ↔ server, satu-satunya tempat keduanya bertemu |

Yang **tidak** bisa diuji, dan karena itu harus dilihat:

1. **Daftar pola sempat menampilkan `Expected a JSON object at root`** — bug parse di klien, tak
   terlihat oleh test rute mana pun (mereka memanggil server langsung).
2. **Pesan pratinjau yang menyesatkan** untuk pola kosong — perilaku terlihat, bukan perilaku terukur.
3. **Tata letak di lebar 1280dp**: kolom galeri tetap 380dp, perancang menyusut, tidak ada teks pecah
   per huruf, kartu tidak saling timpa (reservasi `padding` di `claySurface` bekerja).

Bukti yang diambil (tanggal 2026-09-30, web :3001, superadmin + owner pabrik):

- galeri kosong → pilih pack `garment` → pilih modul `Pola & Sampling Order` → widget `Layar Kustom` →
  "Isi kerangka dari registry" (banner hijau, 3 baris, kolom `Lebar` berupa pil) → isi nama → simpan →
  entri baru muncul di galeri **3 baris** → reload halaman → masih ada (berarti lewat Postgres, bukan
  state di memori klien);
- baris di `wemake_erp.ops.prototype_patterns`:
  ```json
  {"rows": [{"Blok": "Ringkasan Pola & Sampling Order", "Lebar": "penuh"},
            {"Blok": "Daftar Pola & Sampling Order", "Lebar": "separuh"},
            {"Blok": "Panel aksi", "Lebar": "separuh"}]}
  ```
- login sebagai **Owner Pabrik** (`TENANT_ADMIN`): tag berubah jadi "Hanya baca", tombol simpan mati,
  galeri tetap terbaca;
- `/discovery` (wizard) dibuka ulang setelah cabang `when` digabung: funnel masih utuh.

## Jebakan yang dihindari (ringkas)

1. **Membuat DSL/format kedua untuk pola.** Godaannya besar: "resep layout" terdengar seperti butuh
   skema sendiri. Yang dipilih: bentuk yang **sudah** dipakai `WidgetRegistry` dan `PrototypeRenderer`.
2. **Menambah parameter pesan ke komponen bersama** supaya bisa dipakai dua konteks. Yang benar:
   pemanggil memeriksa konteksnya, komponen tetap satu pesan.
3. **`parseObject` di lapisan transport.** Bentuk respons adalah pengetahuan pemanggil.
4. **Menyimpan `moduleId` di pola.** Klaim yang tidak bisa dipertahankan lintas pack.
5. **Menaruh `canWrite` hanya di UI.** Server tetap satu-satunya gerbang; UI hanya menjelaskan.
6. **Menguji pola dengan nama acak.** `UNIQUE(name)` + DB pengembang = run kedua gagal. Fixture harus
   idempoten (atau repositori disuntik — lihat temuan di bawah).

## Temuan yang belum ditutup

1. **Test pola menulis ke DB pengembang.** `module()` tidak menerima `PrototypePatternRepository`,
   jadi `PostgresPrototypePatternRepository()` di `Application.kt:550` yang dipakai — termasuk oleh
   test. Akibatnya fixture test "terlihat" di Studio, dan `UNIQUE(name)` menuntut test idempoten.
   Perbaikannya satu parameter, tapi `Application.kt` sudah di atas hard limit (697 > 500), sehingga
   ratchet file-size melarang menambah baris: parameter itu harus mendarat **bersamaan** dengan
   pemecahan `Application.kt` (konfigurasi plugin ke file terpisah).
2. **Jalur `id: null → server membuat id` tidak lagi diuji** end-to-end karena alasan di atas; ia hanya
   diuji per perilaku rute lain (`pola studio dibaca berlogin…`). Menyuntik repositori in-memory akan
   mengembalikan cakupan itu.
3. **Pratinjau yang benar-benar merender pola di dalam draf prospek** (pola dipilih dari galeri saat
   menyusun draf) belum ada — hari ini pola adalah alat internal; `screens` draf tetap datang dari agent.
4. Kosakata "pabrik" di layar lain (`OrgChartScreen`, `ModuleCardView`) masih terdaftar di utang design
   system; funnel discovery sudah bersih.

## Tantangan mandiri

1. **Suntik repositori pola.** Pindahkan konfigurasi plugin `Application.module()` ke file terpisah
   (memecah 697 baris jadi < 500 + file kecil), lalu tambahkan parameter
   `prototypePatternRepository` dan pakai `InMemoryPrototypePatternRepository` di test. Buktikan dengan
   `--rerun`: jumlah baris `ops.prototype_patterns` di DB pengembang **tidak** bertambah.
2. **Pakai pola di pratinjau draf.** Tambahkan satu langkah: kalau draf punya layar `CUSTOM_SCREEN`
   tanpa baris, tawarkan pola `packCode` draf itu sebagai isian. Perhatikan: `packCode` draf prospek
   (`klinik`) biasanya **tidak** punya pola — jadi pola "tanpa pack" yang berlaku umum.
3. **Uji tenant kedua.** Jalankan Studio di tenant uji non-rajut (`klinik-uji`): pastikan tidak ada satu
   pun string garment di layar, dan panen kerangka memakai modul pack klinik. Ini yang membuktikan
   Studio benar-benar platform, bukan konveksi yang kebetulan dipakai klinik.
4. **Hitung ulang ambang ukuran.** `PrototypeStudioScreen.kt` 345 baris hari ini; begitu ia menyentuh
   400 (soft), pecah per tanggung jawab — galeri perancang, bukan "Part2".

