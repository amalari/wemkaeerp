# 🎓 Modul Pembelajaran: API Sunting Kerangka & Gerbang Fail-Closed (TRD-FLOW-001 Tahap 3b)

> **Level Target**: Junior to Mid Developer
> **Topik Utama**: Menguji ulang asumsi TRD terhadap keputusan desain, invarian peran, fail-open vs fail-closed, Aturan Ratchet
> **Prasyarat**: [Tahap 3a](teaching-flow-001-tahap-3a-industry-templates.md)
> **Referensi Task**: [TRD-FLOW-001](../trd/TRD-FLOW-001-industry-stage-templates.md) — Tahap 3b (v0.8)

---

## 💡 1. Konsep Dasar

Admin pabrik kini bisa menambah, menghapus, memindah, dan mengganti nama tahap kerja, atau
mereset kerangka ke template industri lain — lewat `/api/tenant/stage-flow/**`. Superadmin bisa
memilih `industryTemplate` saat onboarding.

---

## 🧭 2. "Start dari Mana?" — Uji Dulu Asumsi TRD-nya

TRD v0.1 menulis: *hapus tahap ditolak bila ada SPK aktif di tahap itu* (`STAGE_OCCUPIED`). Sebelum
menulis kodenya, periksa terhadap keputusan yang diambil **sesudah** TRD ditulis (FR-5b, kerangka
beku per SPK):

- SPK yang beku memakai kerangkanya sendiri → menyunting kerangka pabrik tak berpengaruh padanya.
- SPK yang belum beku hanya ada di tahap masuk → tahap masuk tidak bisa disunting.
- SPK lama di lantai tanpa kerangka beku → tetap di kerangka rajut default (Tahap 3a).

Kesimpulan: `STAGE_OCCUPIED` **tidak pernah terpicu**. Menulisnya = kode mati yang memberi rasa aman
palsu. Yang benar-benar rusak saat tahap dihapus adalah **proses opsional katalog** yang berjangkar
padanya — itulah yang dijaga (`409 STAGE_HAS_PROCESSES`). TRD diperbarui, bukan diam-diam dilanggar.

---

## 🧱 3. Bedah Kode

### Blok A — Invarian peran di operasi sunting, bukan di konstruktor

```kotlin
fun remove(code: StageCode) = copy(stages = stages.filterNot { it.code == code }).also { it.requireEssentialRoles() }
```

Aturan domain mencari peran QC dan pengemasan (QC lolos → kemas). Menghapus satu-satunya tahap QC
lewat editor akan membuat **setiap** inspeksi QC tenant itu gagal. Ditaruh di operasi sunting — bukan
di `init` — supaya kerangka uji minimal di test lain tidak ikut terkena.

### Blok B — Fail-open yang diwarisi

Gerbang bersama `requireFactoryFlowAccess` sengaja **fail-open**: bila keputusan RBAC `null`, lolos.
Uji langsung ke server membuktikan akibatnya: **operator tanpa jabatan kustom berhasil menambah
tahap (200)**. Tidak ada test unit yang menangkapnya — ditemukan karena API diuji dengan tiga peran
nyata (operator, owner, superadmin).

Perbaikannya lokal dan **fail-closed**:

```kotlin
internal fun mayEditWithoutDecision(decision: AccessDecision?, role: Role?): Boolean =
    decision != null || role?.defaultPermissions?.contains(Permission.MANAGE_TENANT) == true
```

- Memakai `Permission.MANAGE_TENANT` milik sistem peran, bukan daftar peran yang dikarang.
- Gerbang bersama **tidak** diubah di PR ini: ia juga menjaga rute lokasi, dan membalik
  semantiknya butuh review tersendiri. Lubang yang sama masih terbuka di sana → dilaporkan.
- Test regresi mengiterasi `Role.entries`, jadi peran baru otomatis ikut diperiksa.

### Blok C — Ratchet dengan satu baris

`Application.kt` (di tabel utang, tidak boleh bertambah) butuh parameter onboarding baru. Default
dipindah ke use case (`industryTemplate: IndustryTemplateCode? = null`), sehingga rute cukup satu
baris yang berubah: 698 → 698.

---

## ⚠️ 4. Jebakan Pemula

1. **Menerjemahkan TRD kata per kata.** Dokumen perencanaan ditulis sebelum keputusan berikutnya.
2. **Menguji otorisasi hanya dengan pengguna yang berwenang.** Selalu uji yang *tidak* berwenang.
3. **Memperbaiki gerbang bersama diam-diam** di PR fitur — perbaiki lokal, laporkan yang bersama.
4. **Lima use case dalam satu file** melanggar CLAUDE.md §8; dipecah per operasi + helper `internal`.

---

## 🧪 5. Pembuktian

- `EditTenantStageFlowTest` (8): sisip sesudah jangkar masuk mendarat sesudah jangkar terakhir;
  sunting jangkar/sesudah tahap keluar/kode ganda ditolak; hapus QC/kemas ditolak; pindah; tambah
  (asal `OPTIONAL`, meja operator, sisa kerja mewarisi); hapus ditolak bila proses berjangkar; reset
  ditolak bila proses akan yatim.
- `StageFlowEditGuardTest`: tanpa keputusan RBAC hanya peran ber-`MANAGE_TENANT`.
- Live di `bordir-uji`: operator PATCH/DELETE **403**, GET 200; owner & superadmin 200; tambah/ganti
  nama/pindah/hapus 200; hapus QC & jangkar 400; `GET /api/tenant/stage-templates` 4 template;
  onboarding `sablon-uji` dengan `SCREEN_PRINT`.
- `core` 920, `app:shared` 152 test lulus.

---

## 🏆 6. Tantangan Mandiri

- [ ] Perbaiki fail-open di `requireFactoryFlowAccess` untuk rute lokasi — test apa yang harus ada dulu?
- [ ] Tahap kustom tenant (mis. `APPLIQUE`) belum dikenal `FlowNodeRef.parse` (hanya kode template).
      Rancang parse yang menerima kerangka tenant.
